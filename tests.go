package main

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	_ "embed"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// build.sh готовит сжатый архив: исходники Gradle-тестов или собранный XCTest.
//
//go:embed payload.tar.gz
var payloadArchive []byte

// walkPayload обходит обычные файлы вшитого архива. name — очищенный путь
// без "./"; записи с выходом за пределы архива пропускаются.
func walkPayload(visit func(name string, content io.Reader) error) error {
	unpacked, err := gzip.NewReader(bytes.NewReader(payloadArchive))
	if err != nil {
		return fmt.Errorf("повреждён payload: %w", err)
	}
	defer unpacked.Close()

	archive := tar.NewReader(unpacked)
	for {
		header, err := archive.Next()
		if err == io.EOF {
			return nil
		}
		if err != nil {
			return fmt.Errorf("повреждён payload: %w", err)
		}
		// Каталоги, симлинки и служебные PaxHeader-записи macOS-tar не нужны.
		if header.Typeflag != tar.TypeReg {
			continue
		}
		name, ok := cleanArchivePath(header.Name)
		if !ok {
			continue
		}
		if err := visit(name, archive); err != nil {
			return err
		}
	}
}

func cleanArchivePath(raw string) (string, bool) {
	name := strings.TrimPrefix(filepath.ToSlash(filepath.Clean(raw)), "./")
	if name == "" || name == "." || name == ".." || strings.HasPrefix(name, "../") || filepath.IsAbs(name) {
		return "", false
	}
	return name, true
}

// underPrefix возвращает путь относительно prefix, если name лежит внутри него.
func underPrefix(name, prefix string) (string, bool) {
	rest, ok := strings.CutPrefix(name, prefix+"/")
	return rest, ok && rest != ""
}

// extractPayload сносит тесты студента, раскладывает на их место эталонные
// и перезаписывает закреплённые файлы из pinned/ (например, Package.swift).
func extractPayload(t toolchain) (tests int, pinned int, err error) {
	if err := os.RemoveAll(t.WipeDir); err != nil {
		return 0, 0, fmt.Errorf("не удалось очистить %s: %w", t.WipeDir, err)
	}

	err = walkPayload(func(name string, content io.Reader) error {
		if rel, ok := underPrefix(name, payloadTestsPrefix); ok {
			tests++
			return writeFile(filepath.Join(t.TestsTargetDir, rel), content)
		}
		if rel, ok := underPrefix(name, payloadPinnedPrefix); ok {
			if err := checkProjectPath(payloadPinnedPrefix, rel); err != nil {
				return err
			}
			pinned++
			return writeFile(rel, content)
		}
		return nil
	})
	if err != nil {
		return 0, 0, err
	}
	if tests == 0 {
		return 0, 0, fmt.Errorf("бинарь собран без эталонных тестов (пустой payload/%s/%s)", t.ID, payloadTestsPrefix)
	}
	return tests, pinned, nil
}

func writeFile(target string, content io.Reader) error {
	if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
		return err
	}
	file, err := os.Create(target)
	if err != nil {
		return err
	}
	_, err = io.Copy(file, content)
	if closeErr := file.Close(); err == nil {
		err = closeErr
	}
	return err
}
