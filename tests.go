package main

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	_ "embed"
	"encoding/xml"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

// Эталонные тесты вшиваются в бинарь на этапе go build одним сжатым архивом:
// в несжатом виде их находил бы обычный grep по бинарю.
// Архив собирает build.sh из каталога payload/.
//
//go:embed payload.tar.gz
var payloadArchive []byte

// extractTests сносит тесты студента и раскладывает на их место эталонные.
func extractTests() (int, error) {
	if err := os.RemoveAll(wipeDir); err != nil {
		return 0, fmt.Errorf("не удалось очистить %s: %w", wipeDir, err)
	}

	unpacked, err := gzip.NewReader(bytes.NewReader(payloadArchive))
	if err != nil {
		return 0, fmt.Errorf("повреждён payload: %w", err)
	}
	defer unpacked.Close()

	count := 0
	archive := tar.NewReader(unpacked)
	for {
		header, err := archive.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return 0, fmt.Errorf("повреждён payload: %w", err)
		}
		if header.Typeflag != tar.TypeReg {
			continue
		}

		name := strings.TrimPrefix(filepath.Clean(header.Name), "./")
		if name == "" || strings.HasPrefix(name, "..") || filepath.IsAbs(name) {
			continue
		}

		target := filepath.Join(testsTargetDir, name)
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return 0, err
		}
		file, err := os.Create(target)
		if err != nil {
			return 0, err
		}
		_, err = io.Copy(file, archive)
		file.Close()
		if err != nil {
			return 0, err
		}
		count++
	}

	if count == 0 {
		return 0, fmt.Errorf("бинарь собран без эталонных тестов (пустой payload/)")
	}
	return count, nil
}

type report struct {
	Tests    int
	Failures int
	Errors   int
	Skipped  int
}

func (r report) passed() int { return r.Tests - r.Failures - r.Errors - r.Skipped }

func (r report) green() bool {
	return r.Tests > 0 && r.Failures == 0 && r.Errors == 0 && r.Skipped == 0
}

// parseReports суммирует JUnit XML, которые Gradle кладёт в build/test-results/test.
func parseReports() (report, error) {
	files, err := filepath.Glob(reportGlob)
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
		total.Tests += partial.Tests
		total.Failures += partial.Failures
		total.Errors += partial.Errors
		total.Skipped += partial.Skipped
	}
	return total, nil
}

func parseReport(path string) (report, error) {
	handle, err := os.Open(path)
	if err != nil {
		return report{}, fmt.Errorf("не удалось открыть отчёт %s: %w", path, err)
	}
	defer handle.Close()

	var result report
	decoder := xml.NewDecoder(handle)
	for {
		token, err := decoder.Token()
		if err != nil {
			break
		}
		start, ok := token.(xml.StartElement)
		if !ok || start.Name.Local != "testsuite" {
			continue
		}
		for _, attr := range start.Attr {
			value, err := strconv.Atoi(attr.Value)
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
			case "skipped":
				result.Skipped += value
			}
		}
	}
	return result, nil
}
