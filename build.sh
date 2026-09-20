#!/usr/bin/env bash
# Собирает чекер с запечёнными тестами и токенами.
#
#   LMS_COMPANY_TOKEN=xxx CALLBACK_TASK_ID=yyy ./build.sh
#
# На выходе dist/checker-<os>-<arch>. Для GitHub Actions нужен только
# linux-amd64 — остальные для ручной отладки.
#
# INSTALL_TO=<путь к репозиторию задания> положит linux-amd64 сразу
# в <путь>/.github/checker.
set -euo pipefail
cd "$(dirname "$0")"

: "${LMS_COMPANY_TOKEN:?нужно задать LMS_COMPANY_TOKEN}"
: "${CALLBACK_TASK_ID:?нужно задать CALLBACK_TASK_ID}"

PAYLOAD_DIR="payload/${TOOLCHAIN_ID:-gradle}"

if [ -z "$(find "${PAYLOAD_DIR}" -type f 2>/dev/null)" ]; then
  echo "${PAYLOAD_DIR}/ пуст — нечего запекать в бинарь" >&2
  exit 1
fi

# Сжимаем тесты, иначе их видно в бинаре обычным grep.
# COPYFILE_DISABLE не даёт macOS-tar класть в архив служебные ._-файлы.
COPYFILE_DISABLE=1 tar -czf payload.tar.gz -C "${PAYLOAD_DIR}" .
trap 'rm -f payload.tar.gz' EXIT

LDFLAGS="-s -w
  -X main.lmsCompanyToken=${LMS_COMPANY_TOKEN}
  -X main.callbackTaskID=${CALLBACK_TASK_ID}"

mkdir -p dist
for target in linux/amd64 linux/arm64 darwin/arm64 darwin/amd64; do
  GOOS="${target%/*}" GOARCH="${target#*/}" \
    go build -trimpath -ldflags "${LDFLAGS}" \
    -o "dist/checker-${target%/*}-${target#*/}" .
done

echo "готово:"
ls -lh dist

if [ -n "${INSTALL_TO:-}" ]; then
  install -m 0755 dist/checker-linux-amd64 "${INSTALL_TO}/.github/checker"
  echo "установлен в ${INSTALL_TO}/.github/checker"
fi
