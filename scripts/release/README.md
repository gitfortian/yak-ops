# Release Tooling

Release instructions for maintainers. Current checks live in the linked workflows and scripts. Published versions are recorded in [GitHub Releases](https://github.com/weifuwan/yak-ops/releases) and immutable Git tags, not in per-version Markdown files.

## Prepare and Verify

Prepare a release with `bash scripts/release/prepare-release-version.sh <version>` to align Maven, the frontend, Compose, `.env.example`, and `release.env`.

```sh
bash scripts/release/check-release-metadata.sh vX.Y.Z
bash scripts/release/check-release-migration.sh X.Y.Z
```

Published Flyway migrations are frozen. Audit upgrade behavior; never use Flyway repair to conceal incompatible history. The release migration check validates naming, ordering and draft migration removal.

## Release Gate

Run [Release Gate](../../.github/workflows/v1-release-gate.yml) manually from `main`, with a stable `release_version` matching repository metadata. The workflow requires full Quality Check, JDBC source/sink real-database integration, versioned Playwright UI tests, distribution checks, Docker identity and Compose smoke. The Boot backend E2E suite is not configured while it is being redesigned, and release evidence explicitly reports it as **NOT RUN**, not PASS. This leaves Boot API/Task E2E coverage missing; the existing JDBC/CDC tests do not replace it. Required checks must actually run and succeed; UI evidence must match the candidate version and commit SHA.

Release Decision requires explicit confirmations and evidence:

- `manual_e2e_confirmed`, `manual_e2e_evidence`: required passing product E2E and its run, log, or traceable report.
- `migration_review_confirmed`, `migration_review_evidence`: audited migration or explicit no-migration justification.
- `blockers_cleared`: no unresolved P0/P1 blockers.
- `release_notes_confirmed`, `release_summary`, `known_limitations`: reviewed release summary and limitations (enter `None` if confirmed absent).

False confirmations and blank evidence are rejected. The Gate captures version, exact commit SHA, Gate run ID, evidence references and approved notes as an Actions **release-attestations artifact**. Its separately uploaded **release-candidate artifact** contains the verified distribution, SHA256SUMS and candidate evidence. Neither artifact is equivalent to a published release.

## Publish

After the Gate passes, run [Release Publish](../../.github/workflows/release-publish.yml) with `release_version`, the exact `release_commit`, the successful `gate_run_id`, and `publish_latest`. Configure `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` repository secrets before running.

Publish verifies the formal Gate run, successful Release Decision, exact commit and version, both Gate artifacts and distribution SHA-256. Docker images are built from the verified distribution, tested locally and from the registry, and published under immutable version tags. The GitHub Release prepends Gate-approved summary and known limitations to generated change notes, and attaches final evidence with distribution SHA-256, registry digests, release decision, Gate run and commit.

Existing Git tags and immutable image tags must match the exact release commit; mismatches block publish. `latest` is updated only after verified version images, registry smoke and GitHub Release succeed. The Gate does not publish or create a tag.

Rollback with an explicit version tag, preserve persisted state, and repair applied schema migrations forward (or restore a tested backup).
