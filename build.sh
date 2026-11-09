#!/usr/bin/env bash
# Собирает чекер с запечёнными тестами и токенами.
#
#   LMS_COMPANY_TOKEN=xxx CALLBACK_TASK_ID=yyy ./build.sh swift [путь к OrdersTests]
#   LMS_COMPANY_TOKEN=xxx CALLBACK_TASK_ID=yyy ./build.sh gradle   # по умолчанию
#
# Тулчейн — имя каталога в payload/ (первый аргумент или TOOLCHAIN_ID).
# На выходе dist/checker-<тулчейн>-<os>-<arch>. Swift собирается для
# linux-amd64, Gradle — также для остальных платформ.
#
# INSTALL_TO=<путь к репозиторию задания> положит linux-amd64 сразу
# в <путь>/.github/checker.
set -euo pipefail
unset CDPATH

fail() { echo "ошибка: $*" >&2; exit 1; }

ID="${1:-${TOOLCHAIN_ID:-gradle}}"
SWIFT_TESTS_DIR=""
if [ "$#" -gt 1 ]; then
  [ "$ID" = swift ] && [ "$#" -eq 2 ] && [ -n "$2" ] \
    || fail "использование: ./build.sh swift [путь к OrdersTests] или ./build.sh gradle"
  SWIFT_TESTS_DIR="$(cd -- "$2" && pwd -P)" || fail "нет каталога тестов: $2"
fi

if [ "${ID}" = swift ] && [ -n "${INSTALL_TO:-}" ]; then
  INSTALL_TO="$(cd -- "${INSTALL_TO}" && pwd -P)" || fail "нет репозитория задания"
fi

cd "$(dirname "$0")"

: "${LMS_COMPANY_TOKEN:?нужно задать LMS_COMPANY_TOKEN}"
: "${CALLBACK_TASK_ID:?нужно задать CALLBACK_TASK_ID}"
case "${LMS_COMPANY_TOKEN}${CALLBACK_TASK_ID}" in
  *[[:space:]]*) fail "LMS_COMPANY_TOKEN и CALLBACK_TASK_ID не должны содержать пробелов" ;;
esac

PAYLOAD_DIR="payload/${ID}"

[ -d "${PAYLOAD_DIR}" ] || fail "нет каталога ${PAYLOAD_DIR}. Доступные тулчейны: $(ls payload | tr '\n' ' ')"
[ -f "${PAYLOAD_DIR}/toolchain.json" ] || fail "нет ${PAYLOAD_DIR}/toolchain.json"
if [ "${ID}" = swift ]; then
  TEST_DIR="${SWIFT_TESTS_DIR:-${PAYLOAD_DIR}/tests/OrdersTests}"
  [ -d "${TEST_DIR}" ] && [ -n "$(find "${TEST_DIR}/." -type f -name '*.swift' ! -name '._*' -print)" ] \
    || fail "в ${TEST_DIR} нет файлов *.swift — укажите папку тестового таргета OrdersTests"
  command -v docker >/dev/null || fail "для сборки XCTest под Linux нужен запущенный Docker"
elif [ -z "$(find "${PAYLOAD_DIR}/tests" -type f ! -name '.DS_Store' ! -name '.gitkeep' 2>/dev/null)" ]; then
  fail "${PAYLOAD_DIR}/tests/ пуст — положите туда эталонные тесты"
fi

# Закреплённые файлы должны совпадать с шаблоном репозитория студента,
# иначе студент получит один Package.swift, а проверка подложит другой.
if [ -d "${PAYLOAD_DIR}/pinned" ] && [ -d "templates/${ID}-homework" ]; then
  while IFS= read -r pinned; do
    rel="${pinned#"${PAYLOAD_DIR}/pinned/"}"
    cmp -s "${pinned}" "templates/${ID}-homework/${rel}" \
      || fail "${pinned} отличается от templates/${ID}-homework/${rel} — синхронизируйте их"
  done < <(find "${PAYLOAD_DIR}/pinned" -type f ! -name '.DS_Store')
fi

staging=""
locked=""
cleanup() {
  if [ -n "${locked}" ]; then
    rm -f payload.tar.gz
    rmdir .checker-build.lock
  fi
  if [ -n "${staging}" ]; then rm -rf -- "${staging}"; fi
}
trap cleanup EXIT
if [ "${ID}" = swift ]; then
  staging="$(mktemp -d "${TMPDIR:-/tmp}/orders-xctest.XXXXXX")"
  # Docker Desktop монтирует действительный путь, а не системный симлинк macOS.
  staging="$(cd "${staging}" && pwd -P)"
  mkdir -p "${staging}/Tests/OrdersTests" "${staging}/payload"
  cp -R "${TEST_DIR}/." "${staging}/Tests/OrdersTests/"
  cp -R templates/swift-homework/Sources/Orders "${staging}/Orders"
  cp "${PAYLOAD_DIR}/runner/"{Package.swift,Runner.swift,compile.sh} "${staging}/"
  cp "${PAYLOAD_DIR}/toolchain.json" "${staging}/payload/"
  COPYFILE_DISABLE=1 tar --no-xattrs -cf - --exclude '.DS_Store' --exclude '._*' \
    -C "${staging}" Tests Orders Package.swift Runner.swift compile.sh \
    | docker run --rm -i --platform linux/amd64 --network none \
        -v "${staging}/payload:/out" swift:6.1.3-noble \
        bash -c 'mkdir /tmp/runner && cd /tmp/runner && tar -xf - &&
                 bash compile.sh && cp payload/OrdersTests.xctest payload/test-count /out/'
  PAYLOAD_DIR="${staging}/payload"
fi

# Go вшивает архив с фиксированным именем. До конца сборки и установки
# другая команда в том же checkout не должна подменить архив или бинарник.
mkdir .checker-build.lock 2>/dev/null || fail "в этой рабочей копии уже идёт сборка; после аварийной остановки убедитесь, что процесс завершён, и выполните rmdir .checker-build.lock"
locked=1
rm -f payload.tar.gz
# Для Swift архив содержит готовый XCTest, для Gradle — исходники тестов.
# COPYFILE_DISABLE не даёт macOS-tar класть в архив служебные ._-файлы.
COPYFILE_DISABLE=1 tar -czf payload.tar.gz \
  --exclude '.DS_Store' --exclude '._*' --exclude '.gitkeep' \
  -C "${PAYLOAD_DIR}" .

go vet ./...
go test ./...

LDFLAGS="-s -w
  -X main.lmsCompanyToken=${LMS_COMPANY_TOKEN}
  -X main.callbackTaskID=${CALLBACK_TASK_ID}"

build() {
  GOOS="$1" GOARCH="$2" CGO_ENABLED=0 \
    go build -trimpath -ldflags "${LDFLAGS}" -o "${3:-dist/checker-${ID}-$1-$2}" .
}

mkdir -p dist
HOST_OS="$(go env GOOS)"
HOST_ARCH="$(go env GOARCH)"

# Сначала бинарь под текущую машину — и сразу проверяем, что он читает
# свой toolchain.json. Ошибка в манифесте ловится здесь, а не в CI студентов.
host_binary="dist/checker-${ID}-${HOST_OS}-${HOST_ARCH}"
if [ "${ID}" = swift ] && [ "${HOST_OS}/${HOST_ARCH}" != linux/amd64 ]; then
  host_binary="${staging}/check-manifest"
fi
build "${HOST_OS}" "${HOST_ARCH}" "${host_binary}"
"${host_binary}" -print-manifest > /dev/null \
  || fail "toolchain.json не прошёл проверку (подробности выше)"

targets=(linux/amd64)
if [ "${ID}" != swift ]; then targets+=(linux/arm64 darwin/arm64 darwin/amd64); fi
for target in "${targets[@]}"; do
  [ "${target}" = "${HOST_OS}/${HOST_ARCH}" ] && continue
  build "${target%/*}" "${target#*/}"
done

echo "готово (тулчейн ${ID}):"
for target in "${targets[@]}"; do
  ls -lh "dist/checker-${ID}-${target/\//-}"
done

if [ -n "${INSTALL_TO:-}" ]; then
  [ -d "${INSTALL_TO}/.github" ] || fail "${INSTALL_TO}/.github не найден — это точно репозиторий задания?"
  install -m 0755 "dist/checker-${ID}-linux-amd64" "${INSTALL_TO}/.github/checker"
  echo "установлен в ${INSTALL_TO}/.github/checker"
fi
