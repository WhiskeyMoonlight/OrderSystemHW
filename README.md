# course-checker

## Быстрый старт: тесты на Swift

1. Положите файлы `*.swift` в `payload/swift/tests/OrdersTests/` — это единственная папка, которую нужно трогать.
2. Соберите бинарь:
   ```bash
   LMS_COMPANY_TOKEN=<токен> CALLBACK_TASK_ID=<id задания> ./build.sh swift
   ```
3. Готовый бинарь для GitHub Actions — `dist/checker-swift-linux-amd64`. Чтобы сразу положить его в репозиторий задания, добавьте `INSTALL_TO=<путь к репозиторию>`.

Подробно, включая правила для тестов и где взять токены: [docs/SWIFT.md](docs/SWIFT.md).

Чекер домашних заданий: один самодостаточный бинарь, который кладётся в репозиторий студента (`.github/checker`) и запускается в GitHub Actions. Он подменяет тесты студента эталонными, прогоняет их и при зелёном прогоне отправляет результат в LMS.

Написан на Go, чтобы собираться в статический бинарь под Linux и macOS одной командой, без рантайма и зависимостей.

## Тулчейны

Язык задания задаётся каталогом в `payload/`:

```
payload/<id>/
├── toolchain.json   что и как запускать, куда класть тесты, где искать отчёт
├── tests/...        эталонные тесты → копируются в testsTargetDir проекта
└── pinned/...       необязательно: файлы, перезаписываемые в корне проекта
```

Чтобы добавить язык, нужен новый каталог — код на Go менять не нужно.

| Тулчейн | Задание | Команда сборки |
|---|---|---|
| `gradle` | Kotlin + Gradle, JUnit Platform | `./build.sh gradle` |
| `swift` | Swift + SwiftPM, XCTest | `./build.sh swift` |

## Сборка

```bash
LMS_COMPANY_TOKEN=<токен> CALLBACK_TASK_ID=<id задания> ./build.sh swift
```

Бинари появятся в `dist/checker-<тулчейн>-<os>-<arch>`. Для GitHub Actions нужен `linux-amd64`; `INSTALL_TO=<путь к репозиторию задания>` положит его сразу в `.github/checker`.

## Документация

- [docs/SWIFT.md](docs/SWIFT.md) — как написать тесты на Swift и собрать бинарь.
- [templates/swift-homework/](templates/swift-homework/) — шаблон репозитория задания на Swift.
