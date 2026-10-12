# Contributing to Yak Ops

Yak Ops follows the Apache Flink [Contribute Code](https://flink.apache.org/how-to-contribute/contribute-code/) process and [Code Style and Quality Guide](https://flink.apache.org/how-to-contribute/code-style-and-quality-preamble/). The project uses GitHub instead of Flink's JIRA, developer mailing list and Confluence.

## Discuss, Implement, Review

1. Discuss substantial changes and agree on the scope before writing code. Routine fixes and internal refactors may be explained directly in a GitHub PR.
2. Use a FLIP for major new features, subsystems or changes to public contracts. Have the design accepted before beginning a series of implementation PRs.
3. Keep each implementation PR focused. Link the accepted FLIP when applicable; explain the motivation, solution, tests and compatibility impact.
4. Follow [DEVELOPMENT.md](../DEVELOPMENT.md), run checks for the changed scope and resolve review feedback before merge. A passing unit test must not be presented as passing runtime acceptance.

## Flink Improvement Proposals (FLIPs)

Use the [official Flink FLIP process](https://cwiki.apache.org/confluence/spaces/FLINK/pages/65145551/Flink+Improvement+Proposals) and [official FLIP template](https://cwiki.apache.org/confluence/spaces/FLINK/pages/65145553/FLIP+Template) as the standard. The local [FLIP template](FLIP_TEMPLATE.md) keeps the six original sections.

A FLIP is appropriate for a significant new subsystem, externally supported API, plugin SPI, serialized state/checkpoint format, persisted task-definition format, user-facing configuration, monitoring interface, deployment contract or significant compatibility change. Small internal refactors, bug fixes and class/package reorganizations do **not** require a FLIP.

### Propose and Discuss

- Create a **proposal-only** PR containing `docs/flips/YAK-FLIP-XXXX-short-title.md`, based on [FLIP_TEMPLATE.md](FLIP_TEMPLATE.md). Reserve the next unused number after checking existing proposals and PRs. A proposed FLIP is not an implemented feature.
- Title the proposal PR `[DISCUSS][YAK-FLIP-XXXX] Description`. Link the proposal to the PR discussion and record the status as `Discussion`.
- Discuss motivation, public interfaces, compatibility and rejected alternatives **on the proposal PR**, not in implementation commits. Update the proposal to reflect material design changes.
- When outstanding design questions are resolved, request an explicit maintainer **[VOTE]** / decision in the proposal PR. Record the acceptance or rejection and its rationale there; do not infer acceptance from silence or a green CI status.
- For acceptance, change the proposal status to `Accepted` and merge it after maintainer review. Link it from subsequent implementation PRs. Rejected proposals may remain as closed PR discussions; do not merge a rejected design into active requirements.
- Release notes and executable verification evidence belong with the relevant implementation/release, not in the FLIP's ongoing progress log. Major changes to an accepted public design need renewed discussion, typically a new FLIP.

**Platform adaptation:** This repository currently has GitHub Issues and Discussions disabled. Therefore proposal PRs provide the discussion and decision record; this does not claim an Apache mailing-list vote or Apache committer consensus. If those GitHub features are enabled later, the discussion venue can be changed without changing the FLIP criteria or template.

## Documentation Policy

Code, types and tests describe the implemented behavior. Update JavaDoc for contracts that callers cannot infer; update user guides when visible behavior, compatibility or operations change. Do **not** require updates to Architecture, Capability or module Rules Markdown solely because a class, method or package changed. Do not create new descriptions of existing implementation just to satisfy a PR checklist.

Legacy backend Capability/Architecture/Rules documents are retired. Investigate conflicts against code, JavaDoc, tests, persisted data and accepted FLIP decisions. Do not recreate legacy descriptions or weaken security, CI, migration checks or release validation. Do not retrospectively write FLIPs for completed changes.

## Pull Requests

Use the [PR template](PULL_REQUEST_TEMPLATE.md). Assess dependencies, API/format compatibility, hot-path performance, checkpoint/recovery, migrations and security if applicable. Explain what was tested and what was **not** tested. AI-assisted contributions must be reviewed and verified by the author; disclose AI assistance in the PR.
