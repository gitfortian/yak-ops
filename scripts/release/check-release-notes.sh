#!/usr/bin/env bash
set -euo pipefail

# One reviewed, immutable-by-commit Markdown source for each stable release.
version="${1:-}"
if [[ -z "${version}" ]]; then
  # shellcheck disable=SC1091
  source release.env
  version="${YAK_OPS_VERSION}"
fi

[[ "${version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
  echo "Expected stable release version X.Y.Z, got: ${version}" >&2
  exit 1
}

required_sections=(
  "Overview"
  "Highlights"
  "New Features"
  "Improvements"
  "Bug Fixes"
  "Breaking Changes"
  "Compatibility"
  "Upgrade Guide"
  "Known Limitations"
  "Downloads"
  "References"
)

validate_file() {
  local file="$1"
  local expected_version="$2"
  test -s "${file}" || { echo "Missing release notes: ${file}" >&2; exit 1; }
  grep -Fxq "# Yak Ops v${expected_version}" "${file}" || {
    echo "Incorrect release notes title: ${file}" >&2
    exit 1
  }
  grep -Eq '^\*\*Released:\*\* [0-9]{4}-[0-9]{2}-[0-9]{2}$' "${file}" || {
    echo "Missing released date: ${file}" >&2
    exit 1
  }
  if grep -Eiq '(Release Candidate|Awaiting Formal Release Gate|TODO|TBD|待补充)' "${file}"; then
    echo "Unresolved draft content in: ${file}" >&2
    exit 1
  fi

  for section in "${required_sections[@]}"; do
    awk -v header="## ${section}" '
      $0 == header { present = 1; active = 1; next }
      active && /^## / { exit }
      active && /[^[:space:]]/ { content = 1 }
      END { if (!present || !content) exit 1 }
    ' "${file}" || {
      echo "Missing or empty section '${section}' in ${file}" >&2
      exit 1
    }
  done
}

test -s docs/releases/README.md
test -s docs/releases/TEMPLATE.md

for historical in 1.0.0 1.1.0 1.2.0 1.3.0; do
  validate_file "docs/releases/v${historical}.md" "${historical}"
done

# Also validate any later candidate notes, without altering published versions.
for file in docs/releases/v[0-9]*.[0-9]*.[0-9]*.md; do
  [[ -f "${file}" ]] || continue
  file_version="${file##*/v}"
  file_version="${file_version%.md}"
  validate_file "${file}" "${file_version}"
done

validate_file "docs/releases/v${version}.md" "${version}"
echo "Release notes verified: v${version}"
