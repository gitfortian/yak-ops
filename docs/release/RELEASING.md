# Yak Ops Release Guide

Yak Ops uses a tag-driven release flow. The first public release baseline is `v0.1.0`.

## Release source of truth

`release.env` is the single release metadata file shared by CI, the tag release workflow and local Docker publishing scripts.

It records:

- `YAK_OPS_VERSION`: public Yak Ops release version;
- `YAK_FRAMEWORK_VERSION`: Maven coordinate expected by Yak Ops;
- `YAK_FRAMEWORK_REF`: immutable Yak Framework source commit used by CI/release builds;
- `DOCKERHUB_NAMESPACE`: namespace used for published images.

Yak Framework is currently published in source form as `1.0.0-SNAPSHOT` and does not yet have an immutable GitHub/Maven release. To make Yak Ops builds reproducible, CI checks out the exact `YAK_FRAMEWORK_REF` and installs that source revision before building Yak Ops. Once Yak Framework has a formal release, replace the snapshot coordinate and remove the source checkout step.

## Why Maven POMs are normalized during release

The existing reactor contains the same project version in many parent/module POMs. The release workflow runs `scripts/release/prepare-release-version.sh`, which uses the pinned Versions Maven Plugin to update the complete reactor in the clean release workspace before packaging. This avoids hand-editing dozens of POMs while ensuring the produced distribution is versioned with `YAK_OPS_VERSION`.

The committed frontend `package.json` and Docker example tags must already match `release.env`; `scripts/release/check-release-metadata.sh` enforces these invariants in CI.

## Required GitHub Actions secrets

Configure these repository secrets before pushing the first release tag:

- `DOCKERHUB_USERNAME`: Docker Hub account allowed to push the Yak Ops images;
- `DOCKERHUB_TOKEN`: Docker Hub access token for that account.

The account must be able to push to the namespace declared by `DOCKERHUB_NAMESPACE`.

## Release flow

1. Update `release.env` for the next release.
2. Update the committed frontend version and `.env.example` Docker tags to the same version.
3. Run `./scripts/release/check-release-metadata.sh`.
4. Merge the release preparation changes into `main` and wait for CI to pass.
5. Create and push a tag whose version exactly matches `release.env`, for example `v0.1.0`.
6. The `Release` workflow checks out the tag and pinned Yak Framework commit, builds the frontend and Maven distribution from source, publishes both Docker images, creates `SHA256SUMS`, and creates the GitHub Release.

A mismatched tag is rejected before any image is published.

## Published artifacts

For `v0.1.0`, the workflow publishes:

- `weifuwan/yak-ops:0.1.0` and `weifuwan/yak-ops:latest`;
- `weifuwan/yak-ops-api:0.1.0` and `weifuwan/yak-ops-api:latest`;
- `yak-ops-0.1.0.tar.gz` (the Maven distribution archive naming is produced by the reactor after version normalization);
- `SHA256SUMS` attached to the GitHub Release.

## Local image publishing

`build-and-push-linux.sh` and `build-and-push-windows.ps1` read the same `release.env`. They still publish an already-built local distribution and are intended as a manual fallback, not the canonical release path.

The canonical public release path is Git tag -> GitHub Actions -> Docker Hub + GitHub Release.
