# Developing Yak Ops

This document covers the Yak Ops development environment and verification commands. For the contribution process and YOIPs, see [CONTRIBUTING.md](.github/CONTRIBUTING.md).

## Preparation

- Git and Java 21.
- Bash (WSL or Git Bash on Windows) to run the checked-in `mvnw` shell wrapper.
- Node.js and npm for `yak-ops-ui`; use the frontend's own package and tooling files for supported versions and commands.
- Docker and database credentials only when running relevant integration or release checks.

Clone the repository and open its root `pom.xml` as a Maven project in IntelliJ IDEA. Use the command line to distinguish Maven failures from IDE configuration problems. Build with the repository's Java 21 and checked-in Maven configuration.

## Build and Test

Backend verify, including the existing Spotless check and ordinary tests:

```sh
bash mvnw -pl '!yak-ops-ui,!yak-ops-dist' verify
```

Apply/check the existing Java formatter:

```sh
bash mvnw -DskipTests -pl '!yak-ops-bom,!yak-ops-ui,!yak-ops-dist' com.diffplug.spotless:spotless-maven-plugin:3.10.2:apply
bash mvnw -DskipTests -pl '!yak-ops-bom,!yak-ops-ui,!yak-ops-dist' com.diffplug.spotless:spotless-maven-plugin:3.10.2:check
```

Run relevant tests before the full reactor where possible. Connector real-database ITs and product acceptance are separate from Maven `verify`. Current CI scopes and required checks are defined by [Quality Check](.github/workflows/quality-check.yml). Skipped acceptance is not successful acceptance.

Frontend commands and quality gates are documented in [frontend tooling](yak-ops-ui/docs/tooling.md). Do not run frontend or release builds to validate a Markdown-only change unless its scope requires them.

## Acceptance boundaries and execution

| Layer | Technology | Trigger | Ownership |
| --- | --- | --- | --- |
| Unit / frontend contracts | JUnit / TypeScript | Every PR (affected quality scope) | Java modules / `yak-ops-ui` |
| Backend integration and E2E | JUnit + disposable Docker databases | Affected backend PR, full release gate | Connector / task / runtime test modules |
| UI acceptance | Playwright | Release; manual as needed | `yak-ops-ui/tests/e2e/` |
| Deployment acceptance | Docker Compose | Release | `scripts/release/smoke-compose.sh` |

**During the backend migration**, existing Python backend product suites remain under `scripts/acceptance/`; they test real MySQL/PostgreSQL and task lifecycle without any Playwright dependency. Move them to JUnit with Docker in a follow-up PR, one suite at a time; do not discard unique assertions before they have Java replacements.

### Backend acceptance

[Task Execution Product Acceptance](.github/workflows/task-execution-product-acceptance.yml) uses [the complete PR diff and scope map](scripts/ci/product_acceptance_scope.py). Ordinary frontend-only changes select **no backend suites**. Targeted backend changes select `batch`, `schedule`, `realtime`, or `schema`; shared backend or unknown changes conservatively select `full`. A non-selected suite is **NOT EXECUTED**, not evidence of success.

Direct PR runs are risk scoped. Manual and reusable release calls default to `full`; selected suites use fresh disposable MySQL/PostgreSQL environments and isolated state. Product E2E remains separate from Maven's ordinary `verify` phase.

```sh
python3 -m unittest discover -s scripts/ci -p 'test_product_acceptance*.py' -v
bash mvnw -B -ntp -pl yak-ops-boot -am -DskipTests package
python3 -u scripts/acceptance/run_product_acceptance.py --suite schema
```

For local backend product suites, start fresh `yak-task-mysql` and `yak-task-postgres` as configured in the workflow, set test-only `E2E_DB_PASSWORD` and `E2E_USER_PASSWORD`, and never reuse production or shared databases. A failed or dirty fixture must block the run rather than be silently cleared.

### UI acceptance

Playwright is **not a default product feature PR gate**. The standalone [UI Acceptance workflow](.github/workflows/ui-acceptance.yml) runs on manual dispatch and when its own tests/workflow change. Its mocked scenarios run against the **built** frontend, not the Vite development server. Ordinary UI feature PRs rely on frontend quality and contract checks; reviewers may explicitly request a browser run for high-risk interactions.

The formal [Release Gate](.github/workflows/v1-release-gate.yml) always runs the **full** UI suite against a disposable Docker Compose stack containing the exact candidate images. The suite includes mocked interaction contracts and a real authenticated login/navigation smoke. UI tests must not wait for real JDBC/CDC, checkpoints, or Cron triggering; these belong to backend IT/E2E.

```sh
python3 -m pip install playwright==1.57.0
python3 -m playwright install chromium
# Serve yak-ops-ui's production build on http://127.0.0.1:5173
python3 yak-ops-ui/tests/e2e/run.py --suite mocked --base-url http://127.0.0.1:5173
```

The UI report is `/tmp/yak-ui-acceptance/v<VERSION>/<SHA>/ui-acceptance.json`, with per-scenario Playwright traces and failure screenshots. Only the **exact release version and SHA** can be cited as acceptance evidence. A skipped, failed, cancelled, or missing required run blocks release.

### Test and review evidence

The backend acceptance workflow records selected scopes and phase durations under `/tmp/yak-acceptance-results`, plus per-suite Actions artifacts. UI acceptance has its own versioned artifact. Preserve failures and traceback while redacting credentials and task parameters. Use condition-based bounded waits; arbitrary sleep, hiding failed assertions through retries, or changes to production checkpoint timing are prohibited.


## Incremental Review Evidence

Follow [AGENTS.md](AGENTS.md#verification-scope-and-incremental-review) for review scope, polling and evidence reuse. Record the reviewed head, base and tested merge/tree, selected suite, command or run URL, and actual result in the PR. Follow-up fixes are reviewed against the previously reviewed SHA with affected callers and tests, not by repeatedly rereading the whole repository. Changed bases or shared contracts reopen the affected review scope. A new commit needs matching test evidence; known failures cannot disappear merely because a focused run omits their suite.

## Coding Standards

Use the [Apache Flink Code Style and Quality Guide](https://flink.apache.org/how-to-contribute/code-style-and-quality-preamble/) for code review principles: small, focused changes; meaningful names; explicit ownership; readable tests; no redundant comments.

Java formatting uses **Spotless + Palantir Java Format**. Maven Checkstyle validates JavaDoc and ArchUnit tests validate module dependency boundaries during `verify`.

```sh
bash mvnw -B -ntp -pl '!yak-ops-bom,!yak-ops-ui,!yak-ops-dist' org.apache.maven.plugins:maven-checkstyle-plugin:3.6.0:check
python3 scripts/ci/test_logging_rules.py
python3 scripts/verify_logging_rules.py
```

Keep JavaDoc and implementation comments in English. Checkstyle validates the configured backend source packages; public interfaces and non-obvious lifecycle contracts require useful documentation. Logging validation enforces SLF4J structure, sensitive-data safety and English internal log templates. Core and Runtime diagnostic strings and Connector technical exception messages use English; user-facing ErrorCode and validation messages remain localized. TRACE remains supported. In CI, Checkstyle and Spotless run before `verify`; their repeated Maven executions are skipped only after both checks pass. Plain local `verify` still enforces them.

## Documentation and Design

Follow the [current-state comment and documentation rules](AGENTS.md#current-state-comments-and-documentation) when changing JavaDoc, implementation comments or active project guidance.

- Major design changes and public contract changes go through the [YOIP process](.github/CONTRIBUTING.md#yak-ops-improvement-proposals-yoips) before implementation.
- Ordinary implementation details belong in code, JavaDoc, tests and the GitHub PR; no companion descriptive Markdown is required.
- Change user documentation for new or changed user-visible functionality, compatibility or operation procedures.
- Consult current code, JavaDoc, tests and accepted YOIPs for behavior and design decisions.
- Release decisions are recorded as Release Gate artifacts; version notes and immutable asset evidence belong in GitHub Releases, not source-tree Markdown files.
