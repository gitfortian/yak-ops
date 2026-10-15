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

Run relevant tests before the full reactor where possible. Connector real-database ITs run in their own workflows, outside ordinary Maven `verify`. The Boot backend E2E suite is not currently configured. Current CI scopes and required checks are defined by [Quality Check](.github/workflows/quality-check.yml). An unexecuted test is not a pass.

Frontend commands and quality gates are documented in [frontend tooling](yak-ops-ui/docs/tooling.md). Do not run frontend or release builds to validate a Markdown-only change unless its scope requires them.

## Test types and execution

| Layer | Technology | Trigger | Ownership |
| --- | --- | --- | --- |
| Unit / frontend contracts | JUnit / TypeScript | Every PR (affected quality scope) | Java modules / `yak-ops-ui` |
| JDBC/CDC connector integration | JUnit + Docker | Relevant connector PR and release | `yak-flow` connector modules |
| UI automation | Playwright | Release; manual as needed | `yak-ops-ui/tests/e2e/` |
| Deployment smoke | Docker Compose | Release | `scripts/release/smoke-compose.sh` |

### Backend integration tests

`yak-ops-boot` currently has no database E2E suite. Its previous JUnit + Testcontainers
suite and runner have been removed pending redesign. The module continues to run its
ordinary JUnit and architecture tests with Maven `verify`.

Existing real-database connector tests remain available in the
[JDBC Source IT](.github/workflows/jdbc-source-acceptance.yml),
[JDBC Sink Cross-Database IT](.github/workflows/jdbc-sink-cross-database-acceptance.yml)
and MySQL CDC integration workflows. They do not replace Boot API/Task E2E coverage.
Boot E2E is **NOT RUN** until a new suite is implemented.

### Playwright UI Tests

Playwright is **not a default feature PR gate**. The standalone [Playwright UI Tests workflow](.github/workflows/ui-acceptance.yml) runs on manual dispatch and when its own tests/workflow change. Its mocked scenarios run against the **built** frontend, not the Vite development server. Ordinary UI feature PRs rely on frontend quality and contract checks; reviewers may explicitly request a browser run for high-risk interactions.

The formal [Release Gate](.github/workflows/v1-release-gate.yml) always runs the **full** UI suite against a disposable Docker Compose stack containing the exact candidate images. The suite includes mocked interaction contracts and a real authenticated login/navigation smoke. UI tests must not wait for real JDBC/CDC, checkpoints, or Cron triggering; these belong to backend integration tests.

```sh
python3 -m pip install playwright==1.57.0
python3 -m playwright install chromium
# Serve yak-ops-ui's production build on http://127.0.0.1:5173
python3 yak-ops-ui/tests/e2e/run.py --suite mocked --base-url http://127.0.0.1:5173
```

The Playwright report is `/tmp/yak-playwright/v<VERSION>/<SHA>/playwright-results.json`, with per-scenario Playwright traces and failure screenshots. Only results from the **exact release version and SHA** apply to that build. A skipped, failed, cancelled, or missing required run blocks release.

### Test and review evidence

Connector integration workflows collect their own test reports. Playwright has its own versioned artifact. Preserve failures and traceback while redacting credentials and task parameters. Use condition-based bounded waits; arbitrary sleep, hiding failed assertions through retries, or changes to production checkpoint timing are prohibited.


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
