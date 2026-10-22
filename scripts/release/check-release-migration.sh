#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

MIGRATION_DIR="yak-ops-dao/src/main/resources/db/migration/yak-ops"
RELEASE_VERSION="${1:-}"
if [[ -z "$RELEASE_VERSION" ]]; then
    printf 'Usage: %s <product-version>\n' "$0" >&2
    exit 1
fi
RELEASE_VERSION="${RELEASE_VERSION#v}"
if [[ ! "$RELEASE_VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-[0-9A-Za-z]+([.-][0-9A-Za-z]+)*)?$ ]]; then
    printf 'Invalid product version: %s\n' "$RELEASE_VERSION" >&2
    exit 1
fi
TARGET_VERSION="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.${BASH_REMATCH[3]}"
TARGET_SUFFIX="__v${BASH_REMATCH[1]}_${BASH_REMATCH[2]}_${BASH_REMATCH[3]}.sql"

version_gt() {
    local left="$1" right="$2" highest
    highest="$(printf '%s\n%s\n' "$left" "$right" | sort -V | tail -n 1)"
    [[ "$left" != "$right" && "$highest" == "$left" ]]
}

if version_gt "1.3.0" "$TARGET_VERSION"; then
    printf 'ERR v1.3.0 schema reset cannot be published as %s; use the original release artifacts for older versions.\n' "$TARGET_VERSION" >&2
    exit 1
fi

mapfile -t migration_files < <(
    find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*__*.sql' -printf '%f\n' | sort -V
)
if (( ${#migration_files[@]} == 0 )) || [[ "${migration_files[0]}" != "V1__baseline.sql" ]]; then
    printf 'ERR v1.3.0 requires V1__baseline.sql as the first migration.\n' >&2
    exit 1
fi
if ! grep -Fq -- '-- Yak Ops v1.3.0 fresh-install database schema.' "$MIGRATION_DIR/V1__baseline.sql"; then
    printf 'ERR Flyway V1 must identify the v1.3.0 fresh-install schema.\n' >&2
    exit 1
fi
if grep -Eiq '^[[:space:]]*(UPDATE|INSERT|DELETE|DROP|ALTER|TRUNCATE|REPLACE)[[:space:]]' "$MIGRATION_DIR/V1__baseline.sql"; then
    printf 'ERR Flyway v1.3.0 baseline must not modify existing rows or tables.\n' >&2
    exit 1
fi

expected_flyway_version=2
previous_product_version="1.3.0"
highest_flyway_version=1
current_release_count=0
current_release_flyway_version=""

for file in "${migration_files[@]:1}"; do
    if [[ ! "$file" =~ ^V([0-9]+)__v([0-9]+)_([0-9]+)_([0-9]+)\.sql$ ]]; then
        printf 'ERR Draft or invalid release migration: %s\n' "$file" >&2
        exit 1
    fi
    flyway_version="${BASH_REMATCH[1]}"
    product_version="${BASH_REMATCH[2]}.${BASH_REMATCH[3]}.${BASH_REMATCH[4]}"
    if (( flyway_version != expected_flyway_version )); then
        printf 'ERR Expected Flyway V%d, found %s\n' "$expected_flyway_version" "$file" >&2
        exit 1
    fi
    if ! version_gt "$product_version" "$previous_product_version"; then
        printf 'ERR Release migrations must increase after v1.3.0: %s\n' "$file" >&2
        exit 1
    fi
    if version_gt "$product_version" "$TARGET_VERSION"; then
        printf 'ERR Future migration %s is newer than release %s\n' "$file" "$TARGET_VERSION" >&2
        exit 1
    fi
    previous_product_version="$product_version"
    expected_flyway_version=$((expected_flyway_version + 1))
    highest_flyway_version="$flyway_version"
    if [[ "$file" == *"$TARGET_SUFFIX" ]]; then
        current_release_count=$((current_release_count + 1))
        current_release_flyway_version="$flyway_version"
    fi
done

if (( current_release_count > 1 )); then
    printf 'ERR Release %s contains multiple migration files.\n' "$TARGET_VERSION" >&2
    exit 1
fi
if (( current_release_count == 1 && current_release_flyway_version != highest_flyway_version )); then
    printf 'ERR Release migration must have the highest Flyway version.\n' >&2
    exit 1
fi

printf 'OK  v1.3.0 fresh-install baseline (V1), no data changes.\n'
if (( current_release_count == 1 )); then
    printf 'OK  %s uses one release migration: V%s%s\n' "$TARGET_VERSION" "$current_release_flyway_version" "$TARGET_SUFFIX"
else
    printf 'OK  %s has no additional schema migration.\n' "$TARGET_VERSION"
fi
printf 'OK  Published v1.3.0 baseline and subsequent release migrations are immutable.\n'
