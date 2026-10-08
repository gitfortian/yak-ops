#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f release.env ]]; then
    printf 'release.env is missing\n' >&2
    exit 1
fi

set -a
# shellcheck disable=SC1091
source release.env
set +a

: "${YAK_OPS_VERSION:?release.env must define YAK_OPS_VERSION}"
: "${DOCKERHUB_NAMESPACE:?release.env must define DOCKERHUB_NAMESPACE}"

EXPECTED_VERSION="${1:-}"
EXPECTED_VERSION="${EXPECTED_VERSION#v}"

failed=0

check() {
    local description="$1"
    shift
    if "$@"; then
        printf 'OK  %s\n' "$description"
    else
        printf 'ERR %s\n' "$description" >&2
        failed=1
    fi
}

check_contains() {
    local description="$1"
    local file="$2"
    local text="$3"
    check "$description" grep -Fq "$text" "$file"
}

ROOT_MAVEN_VERSION="$(
    sed -n '/<artifactId>yak-ops<\/artifactId>/,/<packaging>pom<\/packaging>/{s#.*<version>\([^<]*\)</version>.*#\1#p;}' pom.xml | head -n 1
)"

check "Yak Ops version uses SemVer" \
    bash -c '[[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+([.-][0-9A-Za-z]+)*)?$ ]]' _ "$YAK_OPS_VERSION"

if [[ -n "$EXPECTED_VERSION" ]]; then
    check "Expected release version matches release metadata" test "$EXPECTED_VERSION" = "$YAK_OPS_VERSION"
fi

check "Root Maven project.version matches release metadata" test "$ROOT_MAVEN_VERSION" = "$YAK_OPS_VERSION"
check_contains "Distribution Maven parent version matches release metadata" yak-ops-dist/pom.xml "<version>${YAK_OPS_VERSION}</version>"
check_contains "Frontend Maven version matches release metadata" yak-ops-ui/pom.xml "<version>${YAK_OPS_VERSION}</version>"
check_contains "Frontend package version matches release metadata" yak-ops-ui/package.json "\"version\": \"${YAK_OPS_VERSION}\""

check "Frontend package-lock version matches release metadata" \
    python3 -c 'import json,sys; expected=sys.argv[1]; lock=json.load(open("yak-ops-ui/package-lock.json", encoding="utf-8")); raise SystemExit(0 if lock.get("version")==expected and lock.get("packages",{}).get("",{}).get("version")==expected else 1)' "$YAK_OPS_VERSION"

for compose_file in compose.yaml compose.without-mysql.yaml; do
    check_contains "${compose_file} frontend image matches release metadata" "$compose_file" "${DOCKERHUB_NAMESPACE}/yak-ops:${YAK_OPS_VERSION}"
    check_contains "${compose_file} backend image matches release metadata" "$compose_file" "${DOCKERHUB_NAMESPACE}/yak-ops-api:${YAK_OPS_VERSION}"
done

check_contains "Frontend Docker example tag matches release metadata" .env.example "YAK_OPS_IMAGE=${DOCKERHUB_NAMESPACE}/yak-ops:${YAK_OPS_VERSION}"
check_contains "Backend Docker example tag matches release metadata" .env.example "YAK_OPS_API_IMAGE=${DOCKERHUB_NAMESPACE}/yak-ops-api:${YAK_OPS_VERSION}"

if (( failed != 0 )); then
    printf '\nRelease metadata is inconsistent. Run scripts/release/prepare-release-version.sh <version> and review the changes.\n' >&2
    exit 1
fi

printf "\nRelease metadata is consistent for Yak Ops %s.\n" "$YAK_OPS_VERSION"
