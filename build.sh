#!/usr/bin/env bash
# Собирает чекер с запечёнными тестами и токенами.
#
#   LMS_COMPANY_TOKEN=xxx CALLBACK_TASK_ID=yyy ./build.sh swift
#   LMS_COMPANY_TOKEN=xxx CALLBACK_TASK_ID=yyy ./build.sh gradle   # по умолчанию
#
# Тулчейн — имя каталога в payload/ (первый аргумент или TOOLCHAIN_ID).
# На выходе dist/checker-<тулчейн>-<os>-<arch>. Для GitHub Actions
# (ubuntu-latest) нужен linux-amd64, остальные — для локальной отладки.
#
# INSTALL_TO=<путь к репозиторию задания> положит linux-amd64 сразу
# в <путь>/.github/checker.
set -euo pipefail
cd "$(dirname "$0")"

fail() { echo "ошибка: $*" >&2; exit 1; }

: "${LMS_COMPANY_TOKEN:?нужно задать LMS_COMPANY_TOKEN}"
: "${CALLBACK_TASK_ID:?нужно задать CALLBACK_TASK_ID}"
case "${LMS_COMPANY_TOKEN}${CALLBACK_TASK_ID}" in
  *[[:space:]]*) fail "LMS_COMPANY_TOKEN и CALLBACK_TASK_ID не должны содержать пробелов" ;;
esac

ID="${1:-${TOOLCHAIN_ID:-gradle}}"
PAYLOAD_DIR="payload/${ID}"

[ -d "${PAYLOAD_DIR}" ] || fail "нет каталога ${PAYLOAD_DIR}. Доступные тулчейны: $(ls payload | tr '\n' ' ')"
[ -f "${PAYLOAD_DIR}/toolchain.json" ] || fail "нет ${PAYLOAD_DIR}/toolchain.json"
if [ -z "$(find "${PAYLOAD_DIR}/tests" -type f ! -name '.DS_Store' ! -name '.gitkeep' 2>/dev/null)" ]; then
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

# Старый архив удаляем до сборки нового: go:embed требует лишь наличия файла,
# и забытый архив от другого тулчейна молча уехал бы в бинарь.
rm -f payload.tar.gz
trap 'rm -f payload.tar.gz' EXIT
# Сжимаем тесты, иначе их видно в бинаре обычным grep.
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
    go build -trimpath -ldflags "${LDFLAGS}" -o "dist/checker-${ID}-$1-$2" .
}

mkdir -p dist
HOST_OS="$(go env GOOS)"
HOST_ARCH="$(go env GOARCH)"

# Сначала бинарь под текущую машину — и сразу проверяем, что он читает
# свой toolchain.json. Ошибка в манифесте ловится здесь, а не в CI студентов.
build "${HOST_OS}" "${HOST_ARCH}"
"dist/checker-${ID}-${HOST_OS}-${HOST_ARCH}" -print-manifest > /dev/null \
  || fail "toolchain.json не прошёл проверку (подробности выше)"

for target in linux/amd64 linux/arm64 darwin/arm64 darwin/amd64; do
  [ "${target}" = "${HOST_OS}/${HOST_ARCH}" ] && continue
  build "${target%/*}" "${target#*/}"
done

echo "готово (тулчейн ${ID}):"
ls -lh dist/checker-"${ID}"-*

if [ -n "${INSTALL_TO:-}" ]; then
  [ -d "${INSTALL_TO}/.github" ] || fail "${INSTALL_TO}/.github не найден — это точно репозиторий задания?"
  install -m 0755 "dist/checker-${ID}-linux-amd64" "${INSTALL_TO}/.github/checker"
  echo "установлен в ${INSTALL_TO}/.github/checker"
fi
