package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"os/exec"
)

// Значения ниже подставляются при сборке через -ldflags "-X main.xxx=...".
// Это делает build.sh.
var (
	lmsBaseURL      = "https://lms.metaclass.kts.studio/api/v2.chunk.check_callback_task"
	lmsCompanyToken = "@LMS_COMPANY_TOKEN@"
	callbackTaskID  = "@CALLBACK_TASK_ID@"
)

const studentConfigFile = "config.yml"

func logf(format string, args ...any) {
	fmt.Printf("[checker] "+format+"\n", args...)
}

func main() {
	dryRun := flag.Bool("dry-run", false, "прогнать тесты, но не отправлять результат в LMS")
	printManifest := flag.Bool("print-manifest", false, "вывести вшитый toolchain.json и выйти")
	flag.Parse()

	if *printManifest {
		if err := runPrintManifest(); err != nil {
			fmt.Fprintf(os.Stderr, "[checker] %v\n", err)
			os.Exit(1)
		}
		return
	}

	if err := run(*dryRun); err != nil {
		fmt.Fprintf(os.Stderr, "\n[checker] ЗАДАНИЕ НЕ ЗАСЧИТАНО: %v\n", err)
		os.Exit(1)
	}
}

// runPrintManifest нужен build.sh: свежесобранный бинарь сразу проверяет свой
// манифест, и ошибка в toolchain.json ловится при сборке, а не в CI студента.
func runPrintManifest() error {
	t, err := loadToolchain()
	if err != nil {
		return err
	}
	encoder := json.NewEncoder(os.Stdout)
	encoder.SetIndent("", "  ")
	return encoder.Encode(t)
}

func run(dryRun bool) error {
	t, err := loadToolchain()
	if err != nil {
		return err
	}
	if err := validateBuild(); err != nil {
		return err
	}
	logf("тулчейн: %s", t.DisplayName)

	token, err := readStudentToken()
	if err != nil {
		return err
	}
	logf("токен студента прочитан из %s", studentConfigFile)

	if t.ID == "swift" {
		err = runSwiftTests(t)
	} else {
		err = runProjectTests(t)
	}
	if err != nil {
		return err
	}
	if dryRun {
		logf("режим --dry-run, результат в LMS не отправлен")
		return nil
	}
	if err := submit(token); err != nil {
		return err
	}
	logf("ЗАДАНИЕ ЗАСЧИТАНО")
	return nil
}

func runProjectTests(t toolchain) error {
	tests, pinned, err := extractPayload(t)
	if err != nil {
		return err
	}
	logf("подставлено эталонных тестов: %d → %s", tests, t.TestsTargetDir)
	if pinned > 0 {
		logf("восстановлено закреплённых файлов: %d", pinned)
	}

	if err := runTests(t); err != nil {
		return err
	}

	report, err := parseReports(t.ReportGlob)
	if err != nil {
		return err
	}
	logf("тестов: %d, прошло: %d, упало: %d, пропущено: %d",
		report.Tests, report.passed(), report.Failures+report.Errors, report.Skipped)

	if report.Tests < t.MinTests {
		return fmt.Errorf("прогнано тестов: %d, ожидалось не меньше %d — часть тестов исключена из сборки",
			report.Tests, t.MinTests)
	}
	if !report.green() {
		return fmt.Errorf("есть упавшие или пропущенные тесты")
	}

	return nil
}

// runTests запускает тесты студента. Ненулевой код возврата здесь не ошибка:
// при упавших тестах сборщик возвращает 1, но XML-отчёт уже записан, и вердикт
// выносится по нему. Настоящие проблемы (нет сборщика, не скомпилировалось)
// всплывут дальше — как отсутствие отчётов.
func runTests(t toolchain) error {
	for _, path := range t.Chmod {
		if err := os.Chmod(path, 0o755); err != nil {
			logf("предупреждение: не удалось сделать %s исполняемым: %v", path, err)
		}
	}

	// Старые отчёты сносим: иначе после неудачного запуска прочитается
	// зелёный отчёт от прошлого раза.
	if t.ReportCleanDir != "" {
		if err := os.RemoveAll(t.ReportCleanDir); err != nil {
			return fmt.Errorf("не удалось очистить %s: %w", t.ReportCleanDir, err)
		}
		if err := os.MkdirAll(t.ReportCleanDir, 0o755); err != nil {
			return err
		}
	}

	ctx, cancel := context.WithTimeout(context.Background(), t.timeout())
	defer cancel()

	logf("запуск %v", t.Command)
	cmd := exec.CommandContext(ctx, t.Command[0], t.Command[1:]...)
	cmd.Env = os.Environ()
	for key, value := range t.Env {
		cmd.Env = append(cmd.Env, key+"="+value)
	}
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stdout
	if err := cmd.Run(); err != nil {
		logf("команда завершилась с ошибкой: %v", err)
	}
	if ctx.Err() == context.DeadlineExceeded {
		logf("превышен таймаут %s", t.timeout())
	}
	return nil
}

func validateBuild() error {
	if lmsCompanyToken == "" || lmsCompanyToken[0] == '@' {
		return fmt.Errorf("бинарь собран без LMS_COMPANY_TOKEN")
	}
	if callbackTaskID == "" || callbackTaskID[0] == '@' {
		return fmt.Errorf("бинарь собран без CALLBACK_TASK_ID")
	}
	return nil
}
