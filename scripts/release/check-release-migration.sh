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

if [[ ! -d "$MIGRATION_DIR" ]]; then
    printf 'Migration directory is missing: %s\n' "$MIGRATION_DIR" >&2
    exit 1
fi

mapfile -t migration_files < <(
    find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*__*.sql' -printf '%f\n' | sort -V
)

if (( ${#migration_files[@]} == 0 )); then
    printf 'No Flyway migrations found in %s\n' "$MIGRATION_DIR" >&2
    exit 1
fi

failed=0
expected_flyway_version=1
current_release_count=0
current_release_flyway_version=""
highest_flyway_version=0
previous_product_version=""

declare -A seen_product_versions=()
declare -A seen_flyway_versions=()

version_gt() {
    local left="$1"
    local right="$2"
    local highest
    highest="$(printf '%s\n%s\n' "$left" "$right" | sort -V | tail -n 1)"
    [[ "$left" != "$right" && "$highest" == "$left" ]]
}

error() {
    printf 'ERR %s\n' "$1" >&2
    failed=1
}

for file in "${migration_files[@]}"; do
    if [[ "$file" == "V1__baseline.sql" ]]; then
        flyway_version=1
        product_version=""
    elif [[ "$file" =~ ^V([0-9]+)__v([0-9]+)_([0-9]+)_([0-9]+)\.sql$ ]]; then
        flyway_version="${BASH_REMATCH[1]}"
        product_version="${BASH_REMATCH[2]}.${BASH_REMATCH[3]}.${BASH_REMATCH[4]}"

        if [[ -n "${seen_product_versions[$product_version]:-}" ]]; then
            error "Multiple Release Migrations map to Product Version ${product_version}: ${seen_product_versions[$product_version]} and ${file}"
        else
            seen_product_versions["$product_version"]="$file"
        fi

        if [[ -n "$previous_product_version" ]] && ! version_gt "$product_version" "$previous_product_version"; then
            error "Product Version order is not strictly increasing: ${previous_product_version} -> ${product_version}"
        fi
        previous_product_version="$product_version"

        if version_gt "$product_version" "$TARGET_VERSION"; then
            error "Future Release Migration ${file} targets ${product_version}, newer than release ${TARGET_VERSION}"
        fi

        if [[ "$file" == *"$TARGET_SUFFIX" ]]; then
            current_release_count=$((current_release_count + 1))
            current_release_flyway_version="$flyway_version"
        fi
    else
        error "Draft or non-release Migration remains at Release Gate: ${file}"
        continue
    fi

    if [[ -n "${seen_flyway_versions[$flyway_version]:-}" ]]; then
        error "Duplicate Flyway version V${flyway_version}: ${seen_flyway_versions[$flyway_version]} and ${file}"
    else
        seen_flyway_versions["$flyway_version"]="$file"
    fi

    if (( flyway_version != expected_flyway_version )); then
        error "Flyway version sequence must be continuous: expected V${expected_flyway_version}, found ${file}"
        expected_flyway_version=$((flyway_version + 1))
    else
        expected_flyway_version=$((expected_flyway_version + 1))
    fi

    if (( flyway_version > highest_flyway_version )); then
        highest_flyway_version="$flyway_version"
    fi
done

if [[ "${migration_files[0]}" != "V1__baseline.sql" ]]; then
    error "V1__baseline.sql must remain the first published migration"
fi

if (( current_release_count > 1 )); then
    error "Product Version ${TARGET_VERSION} has more than one Release Migration"
fi

if (( current_release_count == 1 )) && (( current_release_flyway_version != highest_flyway_version )); then
    error "Release Migration for ${TARGET_VERSION} must be the highest Flyway version"
fi

if (( failed != 0 )); then
    printf '\nRelease migration contract failed for Yak Ops %s.\n' "$RELEASE_VERSION" >&2
    exit 1
fi

if (( current_release_count == 1 )); then
    printf 'OK  Yak Ops %s uses one Release Migration: V%s%s\n' \
        "$TARGET_VERSION" "$current_release_flyway_version" "$TARGET_SUFFIX"
else
    printf 'OK  Yak Ops %s has no new Release Migration; Schema is unchanged for this Product Version.\n' "$TARGET_VERSION"
fi

printf 'OK  Flyway history contains only baseline / published Release Migrations with a continuous version sequence.\n'
