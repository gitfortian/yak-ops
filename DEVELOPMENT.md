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

## Product Acceptance Scopes

[Task Execution Product Acceptance](.github/workflows/task-execution-product-acceptance.yml) selects work from the complete PR diff. Its [scope map](scripts/ci/product_acceptance_scope.py) is executable and covered by tests. Documentation needs no product environment. Ordinary frontend presentation runs controlled browser contracts without Java or databases. Known outbound sync parameters, preview and schedule paths select their applicable real suites; shared backend, dependency, acceptance infrastructure and unclassified changes use full coverage. Paths cannot distinguish a styling edit from a behavioral edit inside the same file: review the behavior and broaden coverage when needed.

The workflow supports `browser`, `batch`, `schedule`, `realtime`, `schema` and `full` through **Run workflow**. Direct PR runs select automatically. Reusable callers and manual runs default to `full`; the existing Backend Acceptance / Release Gate chain therefore retains complete acceptance. Focused dispatches are diagnostic evidence for their named scope, not full release acceptance.

The controlled browser job is independent of database suites. Selected real suites run on separate disposable runners, at most two in parallel; they never share containers or state directories. Full mode preserves the complete cross-scenario sequence, including restart reconciliation. Batch, realtime and schema focused runs also restart the product and compare their pinned reports. Schedule mode includes real admission, version checks, resource-busy checks and optional browser interaction.

Run the fast routing, gate and runner contract tests without Docker, Java, Playwright or credentials:

```sh
python3 -m unittest discover -s scripts/ci -p 'test_product_acceptance*.py' -v
```

For local product acceptance, use **fresh disposable** `yak-task-mysql` and `yak-task-postgres` containers with the databases, ports and MySQL binlog options shown in the workflow. Export `E2E_DB_PASSWORD` and `E2E_USER_PASSWORD` with test-only values. Never point the harness at a production or shared database. The focused runner rejects pre-existing fixture tables rather than clearing them. Run one suite per fresh environment.

```sh
bash mvnw -B -ntp -pl yak-ops-boot -am -DskipTests package
python3 -u scripts/acceptance/run_product_acceptance.py --suite schema
# Other focused choices: batch, schedule, realtime.
```

Focused local runs include their real browser tests when `E2E_BROWSER=true`. Prepare the pinned Playwright version, Chromium and the frontend on port 5173 as in [.github/actions/acceptance-browser](.github/actions/acceptance-browser/action.yml). Full mode always includes browser tests and requires that setup:

```sh
python3 -u scripts/acceptance/run_product_acceptance.py --suite full
```

The workflow additionally executes `ScheduleAdmissionIntegrationTest` on its separate MySQL database for `schedule` and `full`, in the same Maven invocation that packages Boot. Ordinary unit, style, architecture and distribution checks remain owned by Quality Check; a product-scope selection does not replace them.

Suite results and phase durations are written to `/tmp/yak-acceptance-results`, the Actions summary and per-suite artifacts. A failure remains nonzero and retains its traceback. Focused failure-state output allowlists identity, status and failure codes; it excludes credentials, task parameters, SQL and raw error messages. Timing reports are measurements, not guaranteed runtime budgets.

The aggregate check retains the name **MySQL JDBC + Multi-Table CDC Product E2E** for existing check references. It verifies the scope job and every selected child job. Its summary, not the historical name, identifies what actually ran. Unselected suites are **NOT EXECUTED**. Failed, cancelled, missing or unexpectedly skipped selected checks block the gate. New direct PR commits cancel superseded product runs; separate manual and reusable runs do not cancel one another.

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
