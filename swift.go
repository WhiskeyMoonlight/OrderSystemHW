package main

import (
	"context"
	"fmt"
	"io"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
)

const (
	swiftRunnerName = "OrdersTests.xctest"
	swiftCountName  = "test-count"
)

func runSwiftTests(t toolchain) error {
	if runtime.GOOS != "linux" || runtime.GOARCH != "amd64" {
		return fmt.Errorf("Swift-чекер запускается на Linux x86-64 (образ swift:6.1.3-noble)")
	}
	temp, err := os.MkdirTemp("", "orders-check-*")
	if err != nil {
		return err
	}
	defer os.RemoveAll(temp)

	runner := filepath.Join(temp, swiftRunnerName)
	expected, err := extractSwiftSuite(runner)
	if err != nil {
		return err
	}

	// Используем те же флаги ABI, что и при сборке раннера в compile.sh.
	args := []string{"-swift-version", "5", "-package-name", "Orders", "-parse-as-library", "-enable-library-evolution",
		"-emit-library", "-module-name", "Orders", "-o", filepath.Join(temp, "libOrders.so")}
	firstSource := len(args)
	const sources = "Sources/Orders"
	err = filepath.WalkDir(sources, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if entry.Type().IsRegular() && filepath.Ext(path) == ".swift" {
			args = append(args, path)
		}
		return nil
	})
	if err != nil {
		return fmt.Errorf("не удалось прочитать %s: %w", sources, err)
	}
	if len(args) == firstSource {
		return fmt.Errorf("в %s нет файлов *.swift", sources)
	}

	ctx, cancel := context.WithTimeout(context.Background(), t.timeout())
	defer cancel()
	logf("сборка решения из %s", sources)
	if err := runSwiftCommand(ctx, exec.CommandContext(ctx, "swiftc", args...)); err != nil {
		return fmt.Errorf("не удалось собрать модуль Orders: %w", err)
	}

	resultPath := filepath.Join(temp, "result")
	cmd := exec.CommandContext(ctx, runner, resultPath)
	cmd.Dir = temp
	logf("запуск вшитого XCTest")
	if err := runSwiftCommand(ctx, cmd); err != nil {
		return fmt.Errorf("XCTest завершился с ошибкой: %w", err)
	}

	result, err := os.ReadFile(resultPath)
	if err != nil {
		return fmt.Errorf("XCTest не подтвердил завершение полного прогона: %w", err)
	}
	count, err := strconv.Atoi(strings.TrimSpace(string(result)))
	if err != nil || count != expected {
		return fmt.Errorf("получен результат %q; ожидалось %d пройденных тестов", strings.TrimSpace(string(result)), expected)
	}
	logf("тестов: %d, прошло: %d", count, count)
	return nil
}

func runSwiftCommand(ctx context.Context, cmd *exec.Cmd) error {
	cmd.Stdout, cmd.Stderr = os.Stdout, os.Stderr
	err := cmd.Run()
	if ctx.Err() == context.DeadlineExceeded {
		return fmt.Errorf("превышен таймаут проверки: %w", ctx.Err())
	}
	return err
}

func extractSwiftSuite(target string) (int, error) {
	found := false
	expected := 0
	err := walkPayload(func(name string, content io.Reader) error {
		switch name {
		case swiftRunnerName:
			if found {
				return fmt.Errorf("в бинарнике несколько копий %s", swiftRunnerName)
			}
			found = true
			return writeFile(target, content)
		case swiftCountName:
			if expected != 0 {
				return fmt.Errorf("в бинарнике несколько копий %s", swiftCountName)
			}
			data, err := io.ReadAll(content)
			if err != nil {
				return err
			}
			expected, err = strconv.Atoi(strings.TrimSpace(string(data)))
			if err != nil || expected <= 0 {
				return fmt.Errorf("некорректное число эталонных тестов в бинарнике")
			}
		}
		return nil
	})
	if err != nil {
		return 0, err
	}
	if !found || expected == 0 {
		return 0, fmt.Errorf("в бинарнике нет раннера XCTest или числа тестов — пересоберите чекер")
	}
	return expected, os.Chmod(target, 0o700)
}
