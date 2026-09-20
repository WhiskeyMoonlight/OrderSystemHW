package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/exec"
	"time"
)

// Значения ниже подставляются при сборке через -ldflags "-X main.xxx=...".
// Это делает build.sh.
var (
	lmsBaseURL      = "https://lms.metaclass.kts.studio/api/v2.chunk.check_callback_task"
	lmsCompanyToken = "@LMS_COMPANY_TOKEN@"
	callbackTaskID  = "@CALLBACK_TASK_ID@"
)

const (
	studentConfigFile = "config.yml"
	testsTargetDir    = "src/test/kotlin"
	wipeDir           = "src/test"
	reportGlob        = "build/test-results/test/*.xml"
	testTimeout       = 15 * time.Minute
)

func logf(format string, args ...any) {
	fmt.Printf("[checker] "+format+"\n", args...)
}

func main() {
	dryRun := flag.Bool("dry-run", false, "прогнать тесты, но не отправлять результат в LMS")
	flag.Parse()

	if err := run(*dryRun); err != nil {
		fmt.Fprintf(os.Stderr, "\n[checker] ЗАДАНИЕ НЕ ЗАСЧИТАНО: %v\n", err)
		os.Exit(1)
	}
}

func run(dryRun bool) error {
	if err := validateBuild(); err != nil {
		return err
	}

	token, err := readStudentToken()
	if err != nil {
		return err
	}
	logf("токен студента прочитан из %s", studentConfigFile)

	count, err := extractTests()
	if err != nil {
		return err
	}
	logf("подставлено эталонных тестов: %d → %s", count, testsTargetDir)

	runGradle()

	report, err := parseReports()
	if err != nil {
		return err
	}
	logf("тестов: %d, прошло: %d, упало: %d, пропущено: %d",
		report.Tests, report.passed(), report.Failures+report.Errors, report.Skipped)

	if !report.green() {
		return fmt.Errorf("есть упавшие или пропущенные тесты")
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

// runGradle запускает тесты студента. Ненулевой код возврата здесь не ошибка:
// при упавших тестах Gradle возвращает 1, но XML-отчёт уже записан, и вердикт
// выносится по нему. Настоящие проблемы (нет gradlew, не скомпилировалось)
// всплывут дальше — как отсутствие отчётов.
func runGradle() {
	if err := os.Chmod("gradlew", 0o755); err != nil {
		logf("предупреждение: не удалось сделать gradlew исполняемым: %v", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()

	logf("запуск ./gradlew --no-daemon --console=plain test")
	cmd := exec.CommandContext(ctx, "./gradlew", "--no-daemon", "--console=plain", "test")
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stdout
	if err := cmd.Run(); err != nil {
		logf("gradle завершился с ошибкой: %v", err)
	}
	if ctx.Err() == context.DeadlineExceeded {
		logf("превышен таймаут %s", testTimeout)
	}
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
