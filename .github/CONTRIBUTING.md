# Contributing to Yak Ops

Yak Ops follows the Apache Flink [Contribute Code](https://flink.apache.org/how-to-contribute/contribute-code/) process and [Code Style and Quality Guide](https://flink.apache.org/how-to-contribute/code-style-and-quality-preamble/). Yak Ops records proposals, discussions and reviews through GitHub.

## Discuss, Implement, Review

1. Discuss substantial changes and agree on the scope before writing code. Routine fixes and internal refactors may be explained directly in a GitHub PR.
2. Use a YOIP for major new features, subsystems or changes to public contracts. Have the design accepted before beginning a series of implementation PRs.
3. Keep each implementation PR focused. Link the accepted YOIP when applicable; explain the motivation, solution, tests and compatibility impact.
4. Follow [DEVELOPMENT.md](../DEVELOPMENT.md), run checks for the changed scope and resolve review feedback before merge. A passing unit test must not be presented as passing runtime acceptance.

## Yak Ops Improvement Proposals (YOIPs)

Use the [official Flink FLIP process](https://cwiki.apache.org/confluence/spaces/FLINK/pages/65145551/Flink+Improvement+Proposals) and [official FLIP template](https://cwiki.apache.org/confluence/spaces/FLINK/pages/65145553/FLIP+Template) as the upstream model. Yak Ops records its own decisions as YOIPs using proposal PRs; the local [YOIP template](YOIP_TEMPLATE.md) retains all six original sections. Related Flink FLIPs may be linked as design references, not as claims of completed implementation or full compatibility.

A YOIP is appropriate for a significant new subsystem, externally supported API, plugin SPI, serialized state/checkpoint format, persisted task-definition format, user-facing configuration, monitoring interface, deployment contract or significant compatibility change. Small internal refactors, bug fixes and class/package reorganizations do **not** require a YOIP.

### Propose and Discuss

- Create a **proposal-only** PR containing `docs/yoips/YOIP-XXXX-short-title.md`, based on [YOIP_TEMPLATE.md](YOIP_TEMPLATE.md). Reserve the next unused number after checking existing proposals and PRs. A proposed YOIP is not an implemented feature.
- Title the proposal PR `[DISCUSS][YOIP-XXXX] Description`. Link the proposal to the PR discussion and record the status as `Discussion`.
- Discuss motivation, public interfaces, compatibility and rejected alternatives **on the proposal PR**, not in implementation commits. Update the proposal to reflect material design changes.
- When outstanding design questions are resolved, request an explicit maintainer **[VOTE]** / decision in the proposal PR. Record the acceptance or rejection and its rationale there; do not infer acceptance from silence or a green CI status.
- For acceptance, change the proposal status to `Accepted` and merge it after maintainer review. Link it from subsequent implementation PRs. Rejected proposals may remain as closed PR discussions; do not merge a rejected design into active requirements.
- Release notes and executable verification evidence belong with the relevant implementation/release, not in the YOIP's ongoing progress log. Major changes to an accepted public design need renewed discussion, typically a new YOIP.

**Platform adaptation:** This repository currently has GitHub Issues and Discussions disabled. Therefore proposal PRs provide the discussion and decision record; this does not claim an Apache mailing-list vote or Apache committer consensus. If those GitHub features are enabled later, the discussion venue can be changed without changing the YOIP criteria or template.

## Documentation Policy

Code, types and tests describe the implemented behavior. Update JavaDoc for contracts that callers cannot infer; update user guides when visible behavior, compatibility or operations change. Do **not** require updates to Architecture, Capability or module Rules Markdown solely because a class, method or package changed. Do not create new descriptions of existing implementation just to satisfy a PR checklist.

Use code, JavaDoc, tests, persisted data and accepted YOIP decisions for current behavior. Preserve security, CI, migration checks and release validation. Write YOIPs for significant proposed designs, not routine changes.

## Pull Requests

Use the [PR template](PULL_REQUEST_TEMPLATE.md). Assess dependencies, API/format compatibility, hot-path performance, checkpoint/recovery, migrations and security if applicable. Explain what was tested and what was **not** tested. AI-assisted contributions must be reviewed and verified by the author; disclose AI assistance in the PR.
