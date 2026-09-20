package main

import (
	"encoding/json"
	"fmt"
	"io"
	"path/filepath"
	"regexp"
	"strings"
	"time"
)

// Структура payload/<toolchain>/, из которой build.sh собирает архив:
//
//	toolchain.json  — описание тулчейна (этот файл)
//	tests/...       — эталонные тесты, раскладываются в testsTargetDir
//	pinned/...      — необязательно: файлы, которые перезаписываются в корне проекта
//	                  (так закрепляется, например, Package.swift)
const (
	manifestName        = "toolchain.json"
	payloadTestsPrefix  = "tests"
	payloadPinnedPrefix = "pinned"
	defaultTimeout      = 15 * time.Minute
	maxTimeout          = 60 * time.Minute
)

// toolchain описывает, как проверять проект конкретного языка/сборщика.
// Чтобы добавить язык, достаточно нового каталога payload/<id>/ — код менять не нужно.
type toolchain struct {
	ID             string            `json:"id"`
	DisplayName    string            `json:"displayName"`
	WipeDir        string            `json:"wipeDir"`
	TestsTargetDir string            `json:"testsTargetDir"`
	Chmod          []string          `json:"chmod,omitempty"`
	ReportCleanDir string            `json:"reportCleanDir,omitempty"`
	Command        []string          `json:"command"`
	Env            map[string]string `json:"env,omitempty"`
	ReportGlob     string            `json:"reportGlob"`
	TimeoutMinutes int               `json:"timeoutMinutes,omitempty"`
	MinTests       int               `json:"minTests,omitempty"`
}

// loadToolchain — первый проход по вшитому архиву: только ищет манифест.
// Раскладка тестов (extractPayload) — отдельный второй проход, и это намеренно:
// wipeDir нужно знать до того, как что-либо удалять. Архив в памяти и маленький,
// так что два прохода ничего не стоят — не объединяйте их.
func loadToolchain() (toolchain, error) {
	var (
		result toolchain
		found  bool
	)
	err := walkPayload(func(name string, content io.Reader) error {
		if name != manifestName {
			return nil
		}
		decoder := json.NewDecoder(content)
		// Опечатка в ключе иначе молча превратится в пустое поле и сломает проверку
		// у всех студентов разом. Пусть лучше упадёт сборка.
		decoder.DisallowUnknownFields()
		if err := decoder.Decode(&result); err != nil {
			return fmt.Errorf("некорректный %s: %w", manifestName, err)
		}
		found = true
		return nil
	})
	if err != nil {
		return toolchain{}, err
	}
	if !found {
		return toolchain{}, fmt.Errorf("в бинаре нет %s — пересоберите через build.sh", manifestName)
	}
	return result, result.validate()
}

var toolchainIDPattern = regexp.MustCompile(`^[a-z0-9][a-z0-9_-]*$`)

func (t toolchain) validate() error {
	if !toolchainIDPattern.MatchString(t.ID) {
		return fmt.Errorf("%s: некорректный id %q", manifestName, t.ID)
	}
	if len(t.Command) == 0 || strings.TrimSpace(t.Command[0]) == "" {
		return fmt.Errorf("%s: пустой command", manifestName)
	}
	if strings.TrimSpace(t.ReportGlob) == "" {
		return fmt.Errorf("%s: пустой reportGlob", manifestName)
	}
	if t.TimeoutMinutes < 0 || t.MinTests < 0 {
		return fmt.Errorf("%s: timeoutMinutes и minTests не могут быть отрицательными", manifestName)
	}

	required := map[string]string{"wipeDir": t.WipeDir, "testsTargetDir": t.TestsTargetDir}
	for field, value := range required {
		if err := checkProjectPath(field, value); err != nil {
			return err
		}
	}
	if t.ReportCleanDir != "" {
		if err := checkProjectPath("reportCleanDir", t.ReportCleanDir); err != nil {
			return err
		}
	}
	for _, path := range t.Chmod {
		if err := checkProjectPath("chmod", path); err != nil {
			return err
		}
	}
	return nil
}

// checkProjectPath не даёт манифесту указать на что-то, что нельзя трогать:
// за пределы проекта, в .git, в сам чекер (.github) или в config.yml с токеном.
func checkProjectPath(field, value string) error {
	cleaned := filepath.ToSlash(filepath.Clean(value))
	switch {
	case strings.TrimSpace(value) == "":
		return fmt.Errorf("%s: пустое поле %s", manifestName, field)
	case filepath.IsAbs(value), cleaned == ".", cleaned == "..", strings.HasPrefix(cleaned, "../"):
		return fmt.Errorf("%s: %s=%q должен быть относительным путём внутри проекта", manifestName, field, value)
	}
	top := strings.SplitN(cleaned, "/", 2)[0]
	if top == ".git" || top == ".github" || cleaned == studentConfigFile {
		return fmt.Errorf("%s: %s=%q указывает на защищённый путь", manifestName, field, value)
	}
	return nil
}

func (t toolchain) timeout() time.Duration {
	if t.TimeoutMinutes == 0 {
		return defaultTimeout
	}
	return min(time.Duration(t.TimeoutMinutes)*time.Minute, maxTimeout)
}
