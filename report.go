package main

import (
	"encoding/xml"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

type report struct {
	Tests    int
	Failures int
	Errors   int
	Skipped  int
}

func (r report) passed() int {
	return max(0, r.Tests-r.Failures-r.Errors-r.Skipped)
}

func (r report) green() bool {
	return r.Tests > 0 && r.Failures == 0 && r.Errors == 0 && r.Skipped == 0
}

func (r *report) add(other report) {
	r.Tests += other.Tests
	r.Failures += other.Failures
	r.Errors += other.Errors
	r.Skipped += other.Skipped
}

// parseReports суммирует JUnit/xUnit XML по маске. Формат у Gradle и SwiftPM
// общий по духу, но не по деталям — см. parseReport.
func parseReports(glob string) (report, error) {
	files, err := filepath.Glob(glob)
	if err != nil {
		return report{}, err
	}
	if len(files) == 0 {
		return report{}, fmt.Errorf("отчётов о тестах нет — скорее всего проект не скомпилировался")
	}

	var total report
	for _, file := range files {
		partial, err := parseReport(file)
		if err != nil {
			return report{}, err
		}
		total.add(partial)
	}
	return total, nil
}

func parseReport(path string) (report, error) {
	handle, err := os.Open(path)
	if err != nil {
		return report{}, fmt.Errorf("не удалось открыть отчёт %s: %w", path, err)
	}
	defer handle.Close()

	result, err := parseReportXML(handle)
	if err != nil {
		return report{}, fmt.Errorf("битый XML-отчёт %s: %w", path, err)
	}
	return result, nil
}

// parseReportXML считает результат двумя способами и берёт более строгий.
//
// Атрибуты <testsuite tests failures errors skipped> Gradle заполняет честно,
// а SwiftPM — нет: errors у него всегда "0", skipped не пишется вовсе. Поэтому
// основной источник — сами <testcase> и их дочерние <failure>/<error>/<skipped>,
// а атрибуты только страхуют: если заявлено больше тестов, чем найдено,
// отчёт обрезан (упал процесс), и недостающие считаются ошибками.
//
// Ошибки разбора XML не глотаются: обрезанный файл с зелёной шапкой
// не должен читаться как зелёный прогон.
func parseReportXML(input io.Reader) (report, error) {
	var (
		attrs, cases report
		sawCase      bool
		depth        int
		caseDepth    = -1 // -1 — сейчас не внутри <testcase>
		suiteDepth   = -1 // атрибуты берём только у внешнего <testsuite>, вложенные их уже содержат
		failed       bool
		errored      bool
		skipped      bool
	)

	decoder := xml.NewDecoder(input)
	for {
		token, err := decoder.Token()
		if err == io.EOF {
			break
		}
		if err != nil {
			return report{}, err
		}

		switch element := token.(type) {
		case xml.StartElement:
			depth++
			switch element.Name.Local {
			case "testsuite":
				if suiteDepth == -1 {
					suiteDepth = depth
					attrs.add(suiteAttributes(element))
				}
			case "testcase":
				if caseDepth == -1 {
					caseDepth = depth
					failed, errored, skipped = false, false, false
					cases.Tests++
					sawCase = true
				}
			case "failure":
				failed = failed || caseDepth != -1
			case "error":
				errored = errored || caseDepth != -1
			case "skipped":
				skipped = skipped || caseDepth != -1
			}
		case xml.EndElement:
			if depth == suiteDepth && element.Name.Local == "testsuite" {
				suiteDepth = -1
			}
			if depth == caseDepth && element.Name.Local == "testcase" {
				// Каждый тест попадает ровно в одну корзину.
				switch {
				case errored:
					cases.Errors++
				case failed:
					cases.Failures++
				case skipped:
					cases.Skipped++
				}
				caseDepth = -1
			}
			depth--
		}
	}

	if !sawCase {
		return attrs, nil
	}
	// max, а не сумма: Gradle пишет обе картины согласованно, сумма удвоила бы падения.
	result := report{
		Tests:    cases.Tests,
		Failures: max(cases.Failures, attrs.Failures),
		Errors:   max(cases.Errors, attrs.Errors),
		Skipped:  max(cases.Skipped, attrs.Skipped),
	}
	if missing := attrs.Tests - cases.Tests; missing > 0 {
		result.Tests = attrs.Tests
		result.Errors += missing
	}
	return result, nil
}

func suiteAttributes(element xml.StartElement) report {
	var result report
	for _, attr := range element.Attr {
		value, err := strconv.Atoi(strings.TrimSpace(attr.Value))
		if err != nil {
			continue
		}
		switch attr.Name.Local {
		case "tests":
			result.Tests += value
		case "failures":
			result.Failures += value
		case "errors":
			result.Errors += value
		case "skipped", "disabled":
			result.Skipped += value
		}
	}
	return result
}
