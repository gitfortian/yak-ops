#!/usr/bin/env bash
# Native Maven owns execution; Java tests own all database and application processes.
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
suite="${1:-full}"
case "$suite" in
    jdbc)
        case "${2:-}" in mysql|postgres|oracle) suite="jdbc-$2" ;; *) echo 'Usage: run.sh jdbc {mysql|postgres|oracle}' >&2; exit 2 ;; esac ;;
esac
python3 scripts/tests/backend_tests.py run "$suite"
