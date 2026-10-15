#!/usr/bin/env bash
# Real JUnit integration tests with Testcontainers-owned disposable databases.
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
suite="${1:-full}"
case "$suite" in
    batch) tests="TaskExecutionIT,MultiTableBatchIT,OfflineIncrementalIT" ;;
    schedule) tests="ScheduleIT,ScheduleAdmissionIT" ;;
    realtime) tests="DatabaseRealtimeIT,ProcessRecoveryIT" ;;
    schema) tests="SchemaMigrationIT" ;;
    full) tests="*IT" ;;
    *) printf "Unknown backend suite: %s\n" "$suite" >&2; exit 2 ;;
esac
command -v docker >/dev/null 2>&1 || { echo "Docker is required for Backend E2E" >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker daemon unavailable: tests did not run" >&2; exit 1; }
printf "Backend E2E suite=%s tests=%s\n" "$suite" "$tests"
bash mvnw -B -ntp -pl yak-ops-boot -am -Pbackend-e2e "-Dit.test=${tests}" \
    -Dspotless.check.skip=true -Dcheckstyle.skip=true verify
