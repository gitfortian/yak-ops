#!/usr/bin/env bash
# Compute PR impact once before jobs are scheduled. Release Gate remains full.
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

changed=()
full=false
if [[ "${1:-}" == "--paths" ]]; then
    shift
    changed=("$@")
else
    case "${CI_EVENT_NAME:-${GITHUB_EVENT_NAME:-}}" in
        pull_request)
            : "${CI_PR_BASE_SHA:?Missing PR base SHA}"
            : "${CI_PR_HEAD_SHA:?Missing PR head SHA}"
            before="${CI_PR_BASE_SHA}"
            after="${CI_PR_HEAD_SHA}"
            range="${before}...${after}"
            ;;
        push)
            : "${CI_CURRENT_SHA:?Missing pushed SHA}"
            if [[ -z "${CI_PUSH_BEFORE_SHA:-}" || "${CI_PUSH_BEFORE_SHA}" =~ ^0+$ ]]; then
                full=true
            else
                range="${CI_PUSH_BEFORE_SHA} ${CI_CURRENT_SHA}"
            fi
            ;;
        *)
            full=true
            ;;
    esac
    if [[ "${full}" == false ]]; then
        changed_file="$(mktemp)"
        trap 'rm -f "${changed_file}"' EXIT
        if [[ "${CI_EVENT_NAME:-${GITHUB_EVENT_NAME:-}}" == "pull_request" ]]; then
            git diff --name-only --no-renames -z "${range}" > "${changed_file}"
        else
            git diff --name-only --no-renames -z "${CI_PUSH_BEFORE_SHA}" "${CI_CURRENT_SHA}" > "${changed_file}"
        fi
        mapfile -d '' -t changed < "${changed_file}"
    fi
fi
if [[ "${full}" == true ]]; then
    result="$(python3 -c 'import sys; sys.path.insert(0, "scripts/ci"); import impact; print(impact.outputs([], full=True), end="")')"
else
    result="$(python3 scripts/ci/impact.py "${changed[@]}")"
fi
printf '%s\n' "${result}"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then printf '%s\n' "${result}" >> "${GITHUB_OUTPUT}"; fi
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    python3 - "${changed[@]}" <<'PY' >> "${GITHUB_STEP_SUMMARY}"
import sys
sys.path.insert(0, "scripts/ci")
import impact
print(impact.summary(sys.argv[1:], full=("${full}" == "true")), end="")
PY
fi
