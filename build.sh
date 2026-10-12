#!/usr/bin/env bash
# Собирает чекер: вшивает эталонные тесты и ключи LMS в один бинарь.
#
#   ./build.sh --tests ~/MyHomework --task-id 123 --install-to ~/MyHomework-student
#
# Все параметры: ./build.sh --help. Подробная инструкция: docs/SWIFT.md.
set -euo pipefail

CHECKER_DIR="$(cd "$(dirname "$0")" && pwd)"
DEFAULT_CONFIG="${CHECKER_DIR}/lms.yml"
TARGETS="linux/amd64 linux/arm64 darwin/arm64 darwin/amd64"
CI_TARGET="linux-amd64"

usage() {
  cat <<'EOF'
Сборка чекера с вшитыми эталонными тестами.

  ./build.sh --tests <путь> [ключи] [--install-to <репозиторий>]

Тесты:
  --tests <путь>        Тесты задания. Для Swift подходит любой из вариантов:
                          • папка пакета (где лежат Package.swift и Tests/);
                          • папка Tests/;
                          • папка одного тестового таргета (Tests/MyHomeworkTests).
  --toolchain <id>      swift или gradle. Обычно не нужен: определяется по файлам.
  --min-tests <N>       Сколько тестов должно реально запуститься.
                        По умолчанию считается автоматически по тестам.

Ключи LMS (приоритет: аргумент → переменная окружения → файл):
  --lms-token <токен>   Токен компании.        Env: LMS_COMPANY_TOKEN. Файл: lms_token
  --task-id <id>        ID задания в LMS.      Env: CALLBACK_TASK_ID.  Файл: task_id
  --config <файл>       Файл с ключами. По умолчанию lms.yml рядом с build.sh,
                        если он есть. Шаблон: lms.example.yml.

Установка:
  --install-to <путь>   Положить бинарь в <путь>/.github/checker. Если в репозитории
                        ещё нет workflow и config.yml, они тоже будут созданы.

  -h, --help            Эта справка.

Пример:
  ./build.sh --tests ~/MyHomework --task-id 123 --install-to ~/MyHomework-student
EOF
}

fail() { echo "ошибка: $*" >&2; exit 1; }
info() { echo "[build] $*"; }

# Абсолютные пути вычисляем до смены каталога: пользователь передаёт их
# относительно того места, откуда запустил скрипт.
abs_dir() { (cd "$1" 2>/dev/null && pwd) || fail "нет каталога: $1"; }
abs_file() {
  [ -f "$1" ] || fail "нет файла: $1"
  echo "$(abs_dir "$(dirname "$1")")/$(basename "$1")"
}

# --- аргументы --------------------------------------------------------------

ARG_TESTS="" ARG_TOOLCHAIN="" ARG_MIN_TESTS="" ARG_TOKEN="" ARG_TASK="" ARG_CONFIG=""
ARG_INSTALL="${INSTALL_TO:-}"

need_value() { [ $# -ge 2 ] && [ -n "$2" ] || fail "у $1 нет значения"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --*=*) set -- "${1%%=*}" "${1#*=}" "${@:2}"; continue ;;
    --tests)      need_value "$@"; ARG_TESTS="$2"; shift 2 ;;
    --toolchain)  need_value "$@"; ARG_TOOLCHAIN="$2"; shift 2 ;;
    --min-tests)  need_value "$@"; ARG_MIN_TESTS="$2"; shift 2 ;;
    --lms-token)  need_value "$@"; ARG_TOKEN="$2"; shift 2 ;;
    --task-id)    need_value "$@"; ARG_TASK="$2"; shift 2 ;;
    --config)     need_value "$@"; ARG_CONFIG="$2"; shift 2 ;;
    --install-to) need_value "$@"; ARG_INSTALL="$2"; shift 2 ;;
    -h|--help)    usage; exit 0 ;;
    -*)           fail "неизвестный параметр $1 (см. ./build.sh --help)" ;;
    *)            [ -z "${ARG_TOOLCHAIN}" ] || fail "лишний аргумент: $1"
                  ARG_TOOLCHAIN="$1"; shift ;;   # совместимость: ./build.sh gradle
  esac
done

[ -z "${ARG_TESTS}" ]   || ARG_TESTS="$(abs_dir "${ARG_TESTS}")"
[ -z "${ARG_CONFIG}" ]  || ARG_CONFIG="$(abs_file "${ARG_CONFIG}")"
[ -z "${ARG_INSTALL}" ] || ARG_INSTALL="$(abs_dir "${ARG_INSTALL}")"
cd "${CHECKER_DIR}"

# --- ключи ------------------------------------------------------------------

# Простое чтение "ключ: значение" из YAML: кавычки и комментарии допустимы.
config_value() {
  sed -nE "s/^[[:space:]]*$2[[:space:]]*:[[:space:]]*[\"']?([^\"'#[:space:]]*)[\"']?.*$/\1/p" "$1" | head -n 1
}

CONFIG_FILE="${ARG_CONFIG}"
if [ -z "${CONFIG_FILE}" ] && [ -f "${DEFAULT_CONFIG}" ]; then
  CONFIG_FILE="${DEFAULT_CONFIG}"
fi
CFG_TOKEN="" CFG_TASK=""
if [ -n "${CONFIG_FILE}" ]; then
  CFG_TOKEN="$(config_value "${CONFIG_FILE}" lms_token)"
  CFG_TASK="$(config_value "${CONFIG_FILE}" task_id)"
fi

LMS_TOKEN="${ARG_TOKEN:-${LMS_COMPANY_TOKEN:-${CFG_TOKEN}}}"
TASK_ID="${ARG_TASK:-${CALLBACK_TASK_ID:-${CFG_TASK}}}"

check_key() {  # имя флага, значение
  [ -n "$2" ] || fail "не задан $1 — передайте его аргументом, через env или в lms.yml (см. --help)"
  case "$2" in PASTE_*) fail "$1: в lms.yml осталась заглушка $2" ;; esac
  [[ "$2" =~ ^[A-Za-z0-9._~+/=:-]+$ ]] || fail "$1 содержит недопустимые символы"
  [ "${2:0:1}" != "@" ] || fail "$1 не может начинаться с @"
}
check_key --lms-token "${LMS_TOKEN}"
check_key --task-id "${TASK_ID}"

# --- тулчейн и тесты --------------------------------------------------------

has_files() { [ -n "$(find "$1" -type f -name "$2" -print -quit 2>/dev/null)" ]; }

ID="${ARG_TOOLCHAIN}"
if [ -z "${ID}" ]; then
  if [ -z "${ARG_TESTS}" ]; then ID="gradle"
  elif has_files "${ARG_TESTS}" '*.swift'; then ID="swift"
  elif has_files "${ARG_TESTS}" '*.kt'; then ID="gradle"
  else fail "в ${ARG_TESTS} нет ни .swift, ни .kt файлов"
  fi
fi
[ -f "payload/${ID}/toolchain.json" ] || fail "неизвестный тулчейн ${ID}. Доступные: $(ls payload | tr '\n' ' ')"

# Откуда брать тесты, куда их положить внутри tests/ и какой Package.swift закрепить.
TESTS_ROOT="" NEST="" PACKAGE_FILE=""
resolve_tests() {
  if [ -z "${ARG_TESTS}" ]; then
    TESTS_ROOT="${CHECKER_DIR}/payload/${ID}/tests"
    [ -d "${TESTS_ROOT}" ] || fail "укажите тесты: --tests <путь> (см. --help)"
    return 0
  fi
  TESTS_ROOT="${ARG_TESTS}"
  [ "${ID}" = "swift" ] || return 0

  if [ -f "${ARG_TESTS}/Package.swift" ]; then              # папка пакета
    [ -d "${ARG_TESTS}/Tests" ] || fail "в пакете ${ARG_TESTS} нет папки Tests/"
    PACKAGE_FILE="${ARG_TESTS}/Package.swift"
    TESTS_ROOT="${ARG_TESTS}/Tests"
  elif [ -n "$(find "${ARG_TESTS}" -maxdepth 1 -type f -name '*.swift' -print -quit)" ]; then
    NEST="$(basename "${ARG_TESTS}")"                       # папка одного таргета
    PACKAGE_FILE="$(dirname "$(dirname "${ARG_TESTS}")")/Package.swift"
  else
    PACKAGE_FILE="$(dirname "${ARG_TESTS}")/Package.swift"  # папка Tests/
  fi
  [ -f "${PACKAGE_FILE}" ] || PACKAGE_FILE=""
}
resolve_tests

count_tests() {
  local pattern include
  case "${ID}" in
    swift)  include='*.swift'
            pattern='^[[:space:]]*((public|internal|open|final|@objc)[[:space:]]+)*func[[:space:]]+test[A-Za-z0-9_]*[[:space:]]*\([[:space:]]*\)' ;;
    gradle) include='*.kt'
            pattern='^[[:space:]]*@(kotlin\.test\.|org\.junit\.jupiter\.api\.)?Test([[:space:]]|\(|$)' ;;
    *)      echo 0; return ;;
  esac
  { grep -rhE --include="${include}" "${pattern}" "$1" || true; } | wc -l | tr -d ' '
}

# --- сборка архива ----------------------------------------------------------

STAGE="$(mktemp -d)"
# Старый архив удаляем до сборки нового: go:embed требует лишь наличия файла,
# и забытый архив от другого задания молча уехал бы в бинарь.
rm -f payload.tar.gz
trap 'rm -rf "${STAGE}" payload.tar.gz' EXIT

mkdir -p "${STAGE}/tests/${NEST}"
cp -R "${TESTS_ROOT}/." "${STAGE}/tests/${NEST}/"
find "${STAGE}" \( -name '.DS_Store' -o -name '._*' -o -name '.gitkeep' -o -name '.build' \) -prune -exec rm -rf {} +
[ -n "$(find "${STAGE}/tests" -type f -print -quit)" ] || fail "в ${TESTS_ROOT} нет файлов"

if [ -n "${PACKAGE_FILE}" ]; then
  mkdir -p "${STAGE}/pinned"
  cp "${PACKAGE_FILE}" "${STAGE}/pinned/Package.swift"
elif [ "${ID}" = "swift" ]; then
  info "предупреждение: Package.swift рядом с тестами не найден — он не будет закреплён,"
  info "и студент сможет исключить тесты из сборки. Передайте --tests <папка пакета>."
fi

FOUND_TESTS="$(count_tests "${STAGE}/tests")"
MIN_TESTS="${ARG_MIN_TESTS:-${FOUND_TESTS}}"
[[ "${MIN_TESTS}" =~ ^[0-9]+$ ]] || fail "--min-tests должен быть числом"
[ "${MIN_TESTS}" -gt 0 ] || fail "в тестах не найдено ни одного теста (для Swift: func testИмя() в XCTestCase)"

cp "payload/${ID}/toolchain.json" "${STAGE}/toolchain.json"
sed -i.bak -E "s/(\"minTests\"[[:space:]]*:[[:space:]]*)[0-9]+/\1${MIN_TESTS}/" "${STAGE}/toolchain.json"
rm -f "${STAGE}/toolchain.json.bak"

# Сжимаем, иначе тесты видно в бинаре обычным grep.
# COPYFILE_DISABLE не даёт macOS-tar класть в архив служебные ._-файлы.
COPYFILE_DISABLE=1 tar -czf payload.tar.gz -C "${STAGE}" .

go vet ./...
go test ./...

# --- бинари -----------------------------------------------------------------

LDFLAGS="-s -w -X main.lmsCompanyToken=${LMS_TOKEN} -X main.callbackTaskID=${TASK_ID}"
build() {
  GOOS="$1" GOARCH="$2" CGO_ENABLED=0 \
    go build -trimpath -ldflags "${LDFLAGS}" -o "dist/checker-${ID}-$1-$2" .
}

mkdir -p dist
HOST="$(go env GOOS)/$(go env GOARCH)"
HOST_BINARY="dist/checker-${ID}-${HOST%/*}-${HOST#*/}"

# Сначала бинарь под текущую машину, и сразу проверяем, что он читает свой
# манифест: ошибка ловится здесь, а не в CI у студентов.
build "${HOST%/*}" "${HOST#*/}"
MANIFEST="$("${HOST_BINARY}" -print-manifest)" || fail "вшитый toolchain.json не прошёл проверку"
grep -Eq "\"minTests\": ${MIN_TESTS},?$" <<<"${MANIFEST}" || fail "minTests не попал в бинарь"

for target in ${TARGETS}; do
  [ "${target}" = "${HOST}" ] || build "${target%/*}" "${target#*/}"
done

# --- установка --------------------------------------------------------------

copy_if_missing() {  # откуда, куда
  [ -f "$1" ] || return 0
  if [ -e "$2" ]; then
    info "  $2 уже есть — не трогаю"
  else
    mkdir -p "$(dirname "$2")" && cp "$1" "$2" && info "  создан $2"
  fi
}

install_binary() {
  local repo="$1"
  mkdir -p "${repo}/.github"
  install -m 0755 "dist/checker-${ID}-${CI_TARGET}" "${repo}/.github/checker"
  info "установлен ${repo}/.github/checker"
  copy_if_missing "templates/${ID}/check.yml" "${repo}/.github/workflows/check.yml"
  copy_if_missing "templates/${ID}/config.yml" "${repo}/config.yml"
  copy_if_missing "templates/${ID}/gitignore" "${repo}/.gitignore"

  # При проверке Package.swift студента заменится закреплённым — они должны совпадать.
  if [ -n "${PACKAGE_FILE}" ] && [ -f "${repo}/Package.swift" ] \
     && ! cmp -s "${PACKAGE_FILE}" "${repo}/Package.swift"; then
    info "ВНИМАНИЕ: ${repo}/Package.swift отличается от ${PACKAGE_FILE}."
    info "При проверке он будет заменён — студенту стоит выдать тот же Package.swift."
  fi
}

# --- итог -------------------------------------------------------------------

echo
info "готово"
info "  тулчейн:           ${ID}"
TARGET_DIR="$(sed -nE 's/.*"testsTargetDir": "([^"]*)".*/\1/p' <<<"${MANIFEST}")"
info "  тесты:             ${TESTS_ROOT} → ${TARGET_DIR}${NEST:+/${NEST}}"
info "  найдено тестов:    ${FOUND_TESTS}, minTests=${MIN_TESTS}"
info "  Package.swift:     ${PACKAGE_FILE:-не закреплён}"
info "  ключи:             task_id=${TASK_ID}, токен ${#LMS_TOKEN} симв.${CONFIG_FILE:+ (файл ${CONFIG_FILE})}"
info "  для CI:            dist/checker-${ID}-${CI_TARGET}"
info "  для этой машины:   ${HOST_BINARY}"

[ -z "${ARG_INSTALL}" ] || install_binary "${ARG_INSTALL}"
