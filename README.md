# course-checker

Чекер домашних заданий. Это один самодостаточный бинарь: он кладётся в репозиторий студента (`.github/checker`) и запускается в GitHub Actions. Чекер подменяет тесты студента эталонными, прогоняет их и, если всё зелёное, отправляет результат в LMS.

Чекер не привязан к конкретному заданию: тесты и ключи передаются при сборке.

## Быстрый старт: Swift

1. Напишите задание как обычный Swift-пакет: `Package.swift`, `Sources/`, `Tests/`.
2. Один раз сохраните токен компании:
   ```bash
   cp lms.example.yml lms.yml   # и впишите lms_token
   ```
3. Соберите чекер и установите его в репозиторий для студентов:
   ```bash
   ./build.sh --tests ~/MyHomework --task-id 123 --install-to ~/MyHomework-student
   ```

Полная инструкция, включая правила для тестов, ключи и проверку перед выдачей: [docs/SWIFT.md](docs/SWIFT.md). Все параметры: `./build.sh --help`.

## Устройство

```
build.sh               сборка: тесты + ключи → dist/checker-<тулчейн>-<os>-<arch>
lms.example.yml        шаблон файла с ключами (настоящий lms.yml в .gitignore)
payload/<тулчейн>/     как проверять проект: toolchain.json (команда, папки, отчёт)
templates/<тулчейн>/   workflow, config.yml и .gitignore для репозитория студента
examples/swift-orders  пример Swift-задания
docs/SWIFT.md          инструкция для автора Swift-задания
*.go                   сам чекер (менять не нужно)
```

Чекер написан на Go, потому что так он собирается в статический бинарь под Linux и macOS одной командой, без рантайма и зависимостей.

| Тулчейн | Задание | Сборка |
|---|---|---|
| `swift` | Swift + SwiftPM, XCTest | `./build.sh --tests <пакет>` |
| `gradle` | Kotlin + Gradle, JUnit | `./build.sh --tests <src/test/kotlin>` или `./build.sh gradle` (встроенные тесты курса) |

Тулчейн определяется автоматически по файлам тестов. Чтобы добавить новый язык, достаточно папки `payload/<id>/` с `toolchain.json`, код на Go менять не нужно.
