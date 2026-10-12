# Yak Ops AI Agent Instructions

Guidance for contributors and AI coding agents working in this repository. Follow the [Apache Flink contribution process](https://flink.apache.org/how-to-contribute/contribute-code/) and [Code Style and Quality Guide](https://flink.apache.org/how-to-contribute/code-style-and-quality-preamble/) where applicable. Yak Ops build tools and product boundaries remain repository-specific.

## Prerequisites and Commands

- Java 21, the checked-in Maven wrapper, and Git. Use Bash or WSL for the repository's `mvnw` wrapper on Windows.
- Backend verification: `bash mvnw -pl '!yak-ops-ui,!yak-ops-dist' verify`.
- Java formatting: `bash mvnw -DskipTests -pl '!yak-ops-bom,!yak-ops-ui,!yak-ops-dist' com.diffplug.spotless:spotless-maven-plugin:3.10.2:apply`.
- Java format check: use the same command with `:check` instead of `:apply`.
- Frontend verification and tooling: `yak-ops-ui/docs/tooling.md`.

For environment setup and test scope, see [DEVELOPMENT.md](DEVELOPMENT.md). Only report checks that actually ran; a skipped integration test is not a pass.

## Repository Structure

- `yak-ops-core`: public, database-neutral flow contracts; must not depend on Runtime.
- `yak-flow`: local execution runtime and JDBC/CDC connectors.
- `yak-ops-business`: datasource and task-definition business operations.
- `yak-ops-plugins`: plugin APIs and implementations.
- `yak-ops-dao`: persistence, repositories and Flyway migrations.
- `yak-ops-platform`: security, workspace and user preferences.
- `yak-ops-common`: cross-module shared types and utilities.
- `yak-ops-boot`: HTTP boundary and application assembly.
- `yak-ops-ui`: frontend application and shared UI components.

Inspect the current code, direct callers, tests and Maven dependencies before changing a boundary. Do not infer current functionality from an old release document or an implementation proposal.

## Architecture Boundaries

- Do not introduce reverse dependencies from Core to Runtime or from business modules to Boot.
- Keep credentials out of task definitions and user-facing plugin parameters.
- Keep Workspace isolation, immutable task versions, migration history and existing safety checks intact.
- Do not claim exactly-once processing, distributed execution or product runtime integration without executable evidence.
- Do not add interfaces, factories, managers or compatibility layers without a real owner and caller.

## Common Change Patterns

- **Bug fix or internal refactor:** implement the smallest coherent change, update relevant tests and JavaDoc as needed. No routine Markdown synchronization.
- **New major subsystem or public contract change:** discuss and accept a YAK-FLIP before implementation. See [CONTRIBUTING.md](.github/CONTRIBUTING.md) and the [FLIP template](.github/FLIP_TEMPLATE.md).
- **Public interface:** assess REST, plugin SPI, persisted formats, checkpoint/state compatibility, configuration and monitoring consumers.
- **Database migration:** add a forward-only Flyway migration, check upgrades and test the affected compatibility boundary.
- **Frontend change:** follow `yak-ops-ui/FRONTEND_RULES.md` and the affected frontend code/tests; this documentation migration does not rewrite frontend rules.

## Code Quality and Review

- Follow Flink's emphasis on clear ownership, readable code, explicit contracts and targeted tests; avoid redundant comments that repeat the code.
- Use the repository's existing Spotless + Palantir Java Format baseline until a separately reviewed tooling change is accepted. Do not switch formatters as a documentation side effect.
- Preserve current static checks, CI and real-database acceptance workflows. Never weaken or skip them simply to make a PR green.
- Read the applicable existing module `*_RULES.md` for implementation constraints **while those files remain in the repository**. Their descriptions are not a requirement to edit Markdown after ordinary code changes.
- For documentation, change published user guidance when behavior changes, not when an implementation detail changes. Do not create descriptive Capability/Contract/Architecture copies for a routine PR.
- Keep one issue or coherent change per PR and explain purpose, tests, public impact and any FLIP in the PR body.

## References

- [Development](DEVELOPMENT.md) — local setup and verification.
- [Contribution process](.github/CONTRIBUTING.md) — discuss, implement, review.
- [PR template](.github/PULL_REQUEST_TEMPLATE.md) — review expectations.
- [FLIP template](.github/FLIP_TEMPLATE.md) — major design proposals.
- [Flink Code Style and Quality Guide](https://flink.apache.org/how-to-contribute/code-style-and-quality-preamble/).
- [Existing backend formatter rules](JAVA_RULES.md) and [frontend rules](yak-ops-ui/FRONTEND_RULES.md) — retained until the planned cleanup.
