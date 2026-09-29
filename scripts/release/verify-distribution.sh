#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

EXPECTED_VERSION="${1:-}"
bash scripts/release/check-release-metadata.sh "$EXPECTED_VERSION"

# shellcheck disable=SC1091
source release.env
VERSION="$YAK_OPS_VERSION"
BASE_DIR="yak-ops-${VERSION}"
ARCHIVE="yak-ops-dist/target/${BASE_DIR}.tar.gz"
TARGET_DIR="yak-ops-dist/target"
CHECKSUM_FILE="${TARGET_DIR}/SHA256SUMS"

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        printf "Required command not found: %s\n" "$1" >&2
        exit 1
    fi
}

require_command tar
require_command grep

shopt -s nullglob
ARCHIVES=("${TARGET_DIR}"/yak-ops-*.tar.gz)
shopt -u nullglob

if (( ${#ARCHIVES[@]} != 1 )); then
    printf "Expected exactly one distribution archive under %s, found %d.\n" "$TARGET_DIR" "${#ARCHIVES[@]}" >&2
    exit 1
fi

if [[ "${ARCHIVES[0]}" != "$ARCHIVE" ]]; then
    printf "Distribution version mismatch. Expected %s, found %s.\n" "$ARCHIVE" "${ARCHIVES[0]}" >&2
    exit 1
fi

LIST_FILE="$(mktemp)"
trap 'rm -f "$LIST_FILE"' EXIT

if ! tar -tzf "$ARCHIVE" > "$LIST_FILE"; then
    printf "Distribution archive cannot be read: %s\n" "$ARCHIVE" >&2
    exit 1
fi

while IFS= read -r entry; do
    if [[ "$entry" != "$BASE_DIR" && "$entry" != "$BASE_DIR/"* ]]; then
        printf "Distribution contains an unexpected top-level path: %s\n" "$entry" >&2
        exit 1
    fi
    if [[ "$entry" == *"/../"* || "$entry" == *"/.." ]]; then
        printf "Distribution contains a path traversal entry: %s\n" "$entry" >&2
        exit 1
    fi
done < "$LIST_FILE"

REQUIRED_ENTRIES=(
    "${BASE_DIR}/libs/yak-ops-api.jar"
    "${BASE_DIR}/web/index.html"
    "${BASE_DIR}/conf/application.yml"
    "${BASE_DIR}/conf/logback-spring.xml"
    "${BASE_DIR}/bin/run-yak-ops.sh"
    "${BASE_DIR}/jdbc-drivers-builtin/mysql/5/mysql-connector-java-5.1.49.jar"
    "${BASE_DIR}/jdbc-drivers-builtin/mysql/8/mysql-connector-j-8.4.0.jar"
    "${BASE_DIR}/LICENSE"
    "${BASE_DIR}/NOTICE"
    "${BASE_DIR}/README.md"
)

for entry in "${REQUIRED_ENTRIES[@]}"; do
    if grep -Fxq "$entry" "$LIST_FILE"; then
        printf "OK  %s\n" "$entry"
    else
        printf "ERR missing distribution entry: %s\n" "$entry" >&2
        exit 1
    fi
done

RUN_MODE="$(tar -tvzf "$ARCHIVE" "${BASE_DIR}/bin/run-yak-ops.sh" | awk 'NR == 1 { print $1 }')"
if [[ ${#RUN_MODE} -lt 4 || "${RUN_MODE:3:1}" != "x" ]]; then
    printf "run-yak-ops.sh is not owner-executable in the archive: %s\n" "$RUN_MODE" >&2
    exit 1
fi
printf "OK  run-yak-ops.sh executable mode: %s\n" "$RUN_MODE"

if command -v sha256sum >/dev/null 2>&1; then
    (cd "$TARGET_DIR" && sha256sum "${BASE_DIR}.tar.gz") > "$CHECKSUM_FILE"
elif command -v shasum >/dev/null 2>&1; then
    (cd "$TARGET_DIR" && shasum -a 256 "${BASE_DIR}.tar.gz") > "$CHECKSUM_FILE"
else
    printf "Neither sha256sum nor shasum is available.\n" >&2
    exit 1
fi

printf "\nDistribution verified: %s\n" "$ARCHIVE"
printf "Checksum written: %s\n" "$CHECKSUM_FILE"
cat "$CHECKSUM_FILE"
