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

TARGET_VERSION="${1:-${YAK_OPS_VERSION:-}}"

if [[ -z "$TARGET_VERSION" ]]; then
    printf 'Usage: %s <version>\n' "$0" >&2
    exit 1
fi

if [[ ! "$TARGET_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+([.-][0-9A-Za-z]+)*)?$ ]]; then
    printf 'Invalid Yak Ops version: %s\n' "$TARGET_VERSION" >&2
    exit 1
fi

: "${DOCKERHUB_NAMESPACE:?release.env must define DOCKERHUB_NAMESPACE}"
export TARGET_VERSION DOCKERHUB_NAMESPACE

printf "Normalizing Maven reactor to Yak Ops %s...\n" "$TARGET_VERSION"
bash ./mvnw -B -ntp \
    org.codehaus.mojo:versions-maven-plugin:2.21.0:set \
    -DnewVersion="$TARGET_VERSION" \
    -DprocessAllModules=true \
    -DgenerateBackupPoms=false

node <<'NODE'
const fs = require('fs');

const version = process.env.TARGET_VERSION;
const namespace = process.env.DOCKERHUB_NAMESPACE;

function writeJson(path, update) {
  const value = JSON.parse(fs.readFileSync(path, 'utf8'));
  update(value);
  fs.writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`);
}

function replaceRequired(path, pattern, replacement) {
  const before = fs.readFileSync(path, 'utf8');
  if (!pattern.test(before)) {
    throw new Error(`Expected release-version pattern was not found in ${path}`);
  }
  const after = before.replace(pattern, replacement);
  fs.writeFileSync(path, after);
}

writeJson('yak-ops-ui/package.json', (packageJson) => {
  packageJson.version = version;
});

writeJson('yak-ops-ui/package-lock.json', (packageLock) => {
  packageLock.version = version;
  if (!packageLock.packages || !packageLock.packages['']) {
    throw new Error('package-lock.json is missing the root package entry');
  }
  packageLock.packages[''].version = version;
});

replaceRequired('release.env', /^YAK_OPS_VERSION=.*$/m, `YAK_OPS_VERSION=${version}`);

for (const path of ['compose.yaml', 'compose.without-mysql.yaml']) {
  replaceRequired(path, /(\$\{YAK_OPS_IMAGE:-)[^}]+(\})/, (_match, prefix, suffix) => `${prefix}${namespace}/yak-ops:${version}${suffix}`);
  replaceRequired(path, /(\$\{YAK_OPS_API_IMAGE:-)[^}]+(\})/, (_match, prefix, suffix) => `${prefix}${namespace}/yak-ops-api:${version}${suffix}`);
}

replaceRequired('.env.example', /^YAK_OPS_IMAGE=.*$/m, `YAK_OPS_IMAGE=${namespace}/yak-ops:${version}`);
replaceRequired('.env.example', /^YAK_OPS_API_IMAGE=.*$/m, `YAK_OPS_API_IMAGE=${namespace}/yak-ops-api:${version}`);
NODE

printf "Validating normalized release metadata...\n"
bash scripts/release/check-release-metadata.sh "$TARGET_VERSION"

printf "Release workspace is normalized to Yak Ops %s.\n" "$TARGET_VERSION"
