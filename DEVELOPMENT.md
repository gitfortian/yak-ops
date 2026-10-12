# Developing Yak Ops

This is the development entry point, following the structure of [Flink's DEVELOPMENT.md](https://github.com/apache/flink/blob/master/DEVELOPMENT.md). For the contribution process and FLIPs, see [CONTRIBUTING.md](.github/CONTRIBUTING.md).

## Preparation

- Git and Java 21.
- Bash (WSL or Git Bash on Windows) to run the checked-in `mvnw` shell wrapper.
- Node.js and npm for `yak-ops-ui`; use the frontend's own package and tooling files for supported versions and commands.
- Docker and database credentials only when running relevant integration or release checks.

Clone the repository and open its root `pom.xml` as a Maven project in IntelliJ IDEA. Use the command line to distinguish Maven failures from IDE configuration problems. Do not copy Flink's Java 11/17 profiles or formatter configuration into this Java 21 project.

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

Run relevant tests before the full reactor where possible. Connector real-database ITs, release acceptance and the published runtime product checks are separate from an ordinary Maven `verify`. The actual CI scope and job names are defined by [Quality Check](.github/workflows/quality-check.yml) and [CI Gates](.github/CI_GATES.md). Skipped acceptance is not successful acceptance.

Frontend commands and quality gates are documented in [frontend tooling](yak-ops-ui/docs/tooling.md). Do not run frontend or release builds to validate a Markdown-only change unless its scope requires them.

## Coding Standards

Use the [Apache Flink Code Style and Quality Guide](https://flink.apache.org/how-to-contribute/code-style-and-quality-preamble/) for code review principles: small, focused changes; meaningful names; explicit ownership; readable tests; no redundant comments.

The currently enforced formatter is **Spotless + Palantir Java Format**, not Flink's google-java-format. Keep its current versions and checks; changing formatting or the Java baseline requires a separate review. For additional enforcement run:

```sh
python3 scripts/ci/test_verify_java_comments.py
python3 scripts/ci/verify_java_comments.py
python3 scripts/verify_logging_rules.py
bash scripts/ci/verify-backend-boundaries.sh
```

Write concise English JavaDoc for repository-owned top-level types and non-obvious public contracts, including lifecycle, nullability, ownership and compatibility; do not restate obvious methods. The comment scanner enforces English-only Java comments and required top-level documentation for its covered modules. The logging validator enforces the existing logger shape, event language and sensitive-data restrictions. These scripts and CI are the executable style baseline, not a second prose-based rule system.

## Documentation and Design

- Major design changes and public contract changes go through the [YAK-FLIP process](.github/CONTRIBUTING.md#flink-improvement-proposals-flips) before implementation.
- Ordinary implementation details belong in code, JavaDoc, tests and the GitHub PR; no companion descriptive Markdown is required.
- Change user documentation for new or changed user-visible functionality, compatibility or operation procedures.
- Backend Capability/Architecture/Rules descriptions have been retired. Consult current source, relevant JavaDoc, tests and accepted YAK-FLIPs; do not recreate them during routine implementation changes.
- Release decisions are recorded as Release Gate artifacts; version notes and immutable asset evidence belong in GitHub Releases, not source-tree Markdown files.
