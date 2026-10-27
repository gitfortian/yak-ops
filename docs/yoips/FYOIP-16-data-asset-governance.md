# FYOIP-16: Data Asset Governance — Workspace Scans and Issue Lifecycle

Status: Discussion

Discussion: https://github.com/gitfortian/yak-ops/pull/43

Decision: Pending

Implementation: N/A until accepted (migration `V8__v1_10_0.sql`)

Release: N/A until released

Related FLIPs: N/A

## Motivation

FYOIP-15 gives the product a data map: a Workspace can see which physical tables exist, who owns them, and whether they have a description. That answers visibility, but not quality. The platform still cannot say which assets are unmanaged, which scheduled Tasks keep failing, or whether a Workspace is improving.

The execution side already records the facts needed for a narrow first release. `yak_ops_task_definition` owns publication state and an immutable current version, and `yak_ops_task_instance` records the pinned definition version, trigger type, terminal status, start time and end time. The data map owns the curated owner and description fields. What is missing is a governance layer that turns those facts into findings, converges those findings when they disappear, and explains a Workspace-level score.

This proposal is deliberately narrower than the full governance surface. It ships four rules over two dimensions, one current Issue per target, explicit scan completeness, and a Workspace health score. It does not ship plans, cross-Workspace governance units, a pre-publication gate, cost analysis, lineage, storage-size estimation, or custom rules.

| Included in this proposal | Deliberately excluded |
| --- | --- |
| Four code-owned rules over data map tables and scheduled Tasks | Rule enablement, per-Workspace thresholds and custom SQL |
| One live Issue per `(rule, target)` with an append-only event history | A second issue per occurrence or an append-only issue log |
| Complete, partial and skipped scan runs with per-rule outcomes | A scan that silently treats a partial result as complete |
| Daily health snapshots for `DEV` and `COMPUTE` | A five-axis radar with three unevaluated axes |
| A Workspace-scoped overview, issue list and data map detail tab | Plans, cross-Workspace units, leaderboards and a publish gate |

Five repository facts constrain the design.

- FYOIP-15 is implemented in 1.9.0 as `V7__v1_9_0.sql`. `yak_ops_data_map_table` already stores the curated `owner_id` and `description`, the observed `remarks` and the crawler-owned `status`; this proposal reads those columns and changes none of them.
- Identities are 64-character application-generated strings from `IdUtils.nextId()`. The metadata database is MySQL with InnoDB and `utf8mb4_unicode_ci`; no table uses a native JSON column. `V1` through `V7` follow those conventions.
- Task definitions have immutable parameter versions, and Task instances have a pinned `definition_version`, `trigger_type`, `status`, `start_time` and `end_time`. There is no Task dependency graph.
- Workspace isolation is enforced at the request boundary through `WorkspaceContext` and Workspace-scoped queries. A background worker has no context unless it binds one.
- The product is single-node. It makes no clustered-execution claim. A scheduler and a startup recovery sweep are therefore sound for this release only if that limitation is stated and revisited when clustering arrives.

## Public Interfaces

### Product behavior

| Item | Contract |
| --- | --- |
| Scope | Every scan, issue, score and query belongs to exactly one Workspace. There is no cross-Workspace aggregate. |
| Rules | Four rules are compiled into the product and always active in this release. The rule list is read-only; there is no enable switch, threshold editor or frontend-defined SQL. |
| Scan trigger | One scheduled scan per UTC day and a manual scan initiated by any Workspace member. A Workspace has at most one `PENDING` or `RUNNING` scan, enforced by a unique index rather than a pre-check. |
| Data map gate | A scan starts only when the Workspace has no active data map crawl. If a crawl is active, the run is terminal `SKIPPED` with `reason_code = DATA_MAP_BUSY` and changes no Issue. A crawl that begins after the check is an accepted race; the next complete scan converges. |
| Scan completeness | A run is `SUCCEEDED` only when every rule completed. `PARTIAL` means at least one rule failed; `FAILED` means no rule could run; `SKIPPED` means the run deliberately did not evaluate rules. |
| Convergence | A rule may auto-resolve an Issue only after that rule completed a full enumeration. A failed or partial rule never closes anything. |
| Issue identity | There is one row per `(workspace_id, rule_code, target_type, target_id)`. Status is not part of the identity. Re-detection reopens the same row; it does not insert a second row. |
| Issue lifecycle | `OPEN -> HANDLING` on assignment; `OPEN`, `HANDLING` or an already `IGNORED` Issue `-> IGNORED` for a required 1–90 day window; `OPEN`, `HANDLING` or `IGNORED -> RESOLVED` by a member; `RESOLVED -> OPEN` by an explicit member action or a later scan finding it again; an expired `IGNORED` returns to `OPEN` on the next scan. A complete rule scan that no longer sees the target resolves it automatically. |
| Ignore expiry | An expired ignore is reopened by the next scan before scoring. An `IGNORED` Issue that is still detected has its evidence and `last_detected_at` refreshed but remains ignored until the window expires. |
| Score | `DEV` and `COMPUTE` are scored independently from current `OPEN` and `HANDLING` findings. `IGNORED` and `RESOLVED` do not count. A dimension with no eligible base is `N/A`, not 100. `OVERALL` exists only when both dimensions are evaluated. |
| Score presentation | The first release shows two dimension cards and a trend, not a radar. A radar is introduced only when at least three dimensions exist, because a two-axis radar communicates no shape. |
| Access | Every Workspace member may read governance data, assign Issues, resolve or ignore them, and request a scan. This matches data map access. Every write is audited and scoped to the caller's Workspace. |
| Data map tab | The data map table detail page gains a `治理` tab backed by `GET /issues?targetType=DATA_MAP_TABLE&targetId=...`. The tab links to the governance Issue detail; it does not write into the data map. |
| Retention | Scan runs, rule results and health snapshots are retained for 90 days. `RESOLVED` Issues and their events are retained for 180 days after resolution. Cleanup is chunked and never touches an `OPEN`, `HANDLING` or `IGNORED` Issue. |

### Rules in this release

All rules have `rule_version = 1`. Severity weights are `HIGH = 10`, `MEDIUM = 3`, `LOW = 1`. Every rule is score-bearing; there is no warn-only action type.

| Code | Dimension | Target | Condition | Severity |
| --- | --- | --- | --- | --- |
| `DATA_MAP_TABLE_NO_OWNER` | `DEV` | Active data map table | `owner_id` is null or blank | `HIGH` |
| `DATA_MAP_TABLE_NO_DESCRIPTION` | `DEV` | Active data map table | Curated `description` is blank **and** observed `remarks` is blank | `LOW` |
| `TASK_CONSECUTIVE_FAILURES` | `COMPUTE` | Published Task definition | The latest three or more terminal **scheduled** instances of the current definition version, ordered by `scheduled_time` descending, are all `FAILED` within the last 7 days | `HIGH` |
| `TASK_SLOW_RUNS` | `COMPUTE` | Published Task definition | At least three `FINISHED` scheduled instances of the current definition version in the last 7 days, with an average `end_time - start_time` above 7,200 seconds | `MEDIUM` |

Rule semantics that an implementation must preserve:

- `MISSING` data map tables are never targets. A table that is dropped or renamed stops producing Issues after a complete rule scan.
- Manual, `WORKFLOW` and debug-only Task runs are not evaluated. Only `SCHEDULE` instances count.
- A `CANCELED` instance breaks a failure streak. It is not a failure and it is not silently skipped.
- A Task definition whose current version has fewer than three scheduled terminal instances is not accused by either Task rule.
- Evidence is numeric and stored on the Issue: observed value, threshold, sample count and window days. The API returns these values plus stable rule codes; the frontend owns localized wording. No free-text exception message is persisted or shown.

### REST

New endpoints under `/api/v1/governance`, all Workspace-scoped through the existing interceptor:

| Method and path | Purpose |
| --- | --- |
| `GET /overview` | Latest completed score, dimension breakdown, coverage, live Issue counts and last scan status. |
| `GET /issues` | Paged Issues with `status`, `severity`, `dimension`, `ruleCode`, `ownerId`, `targetType`, `targetId`, `keyword`, `page` and `pageSize`. |
| `GET /issues/{issueId}` | One Issue with its current evidence and assignment. |
| `GET /issues/{issueId}/events` | Append-only transition history, newest first, paged. |
| `POST /issues/assign` | Assign an active Workspace member to one or more Issues. |
| `POST /issues/resolve` | Resolve one or more Issues manually, with an optional note. |
| `POST /issues/reopen` | Return manually resolved Issues to `OPEN`. |
| `POST /issues/ignore` | Ignore one or more Issues until a required future timestamp. |
| `POST /issues/unignore` | Return ignored Issues to `OPEN`. |
| `GET /rules` | Read-only rule definitions, versions, severities, weights and fixed thresholds. |
| `GET /health` | Daily dimension and overall scores from `from` to `to`, capped at 90 days. |
| `GET /scans` | Paged scan runs for the Workspace, newest first. |
| `POST /scans` | Request a manual scan; returns `202` with the run record. |
| `GET /scans/{scanId}` | One run with per-rule outcomes and safe reason codes. |

The five batch writes accept at most 200 Issue ids. A batch is one transaction and is all-or-nothing: if one id is outside the Workspace or one transition is invalid, no Issue changes. `assign`, `resolve`, `reopen` and `ignore` are idempotent when the requested state already holds; an ignore on an already ignored Issue with a different window replaces the window and writes an event. `unignore` on a non-ignored Issue and `reopen` on an `IGNORED` Issue return `INVALID_ISSUE_STATE`; reopening an already `OPEN` Issue is a no-op.

Unknown Issues and scans reuse `RESOURCE_NOT_EXISTS`. New error codes belong to a new `io.yak.ops.common.enums.governance` package:

| Code | Use |
| --- | --- |
| `19801 BATCH_TOO_LARGE` | More than 200 Issue ids in one command. |
| `19802 INVALID_ISSUE_STATE` | The requested transition is not valid for the current status. |
| `19803 INVALID_OWNER` | The assignee is not an active member of the caller's Workspace. |
| `19804 INVALID_IGNORE_WINDOW` | The window is in the past, missing, or longer than 90 days. |
| `19805 SCAN_BUSY` | The Workspace already has an active scan, or the executor queue is full. |
| `19806 INVALID_DATE_RANGE` | `from` is after `to`, or the requested health range exceeds 90 days. |

### Persisted formats

The migration is `V8__v1_10_0.sql`, the first migration after `V7__v1_9_0.sql`, and it is additive. It creates five tables and changes none. All five use `InnoDB`, `utf8mb4_unicode_ci`, `VARCHAR(64)` application identities, `DATETIME(3)` timestamps and explicit audit columns where the row is user-mutated.

`yak_ops_governance_scan_run`

- `id`, `workspace_id`
- `trigger_type` `VARCHAR(16) ascii_bin` — `SCHEDULED`, `MANUAL`
- `status` `VARCHAR(16) ascii_bin` — `PENDING`, `RUNNING`, `SUCCEEDED`, `PARTIAL`, `FAILED`, `SKIPPED`
- `active_scope` `TINYINT NULL` — `1` while pending or running, `NULL` after any terminal state
- `rule_count`, `succeeded_rule_count`, `failed_rule_count`, `issue_created_count`, `issue_reopened_count`, `issue_resolved_count` `INT NOT NULL DEFAULT 0`
- `reason_code` `VARCHAR(64) NULL` — a safe code such as `DATA_MAP_BUSY`, never an exception message
- `start_time`, `finish_time` `DATETIME(3) NULL`, `create_time`, `create_by`
- keys: `PRIMARY KEY (id)`, `UNIQUE (workspace_id, id)`,
  `UNIQUE uk_governance_scan_active (workspace_id, active_scope)`,
  `KEY (workspace_id, create_time)`
- `CHECK (status IN (...))`
- `CHECK ((status IN ('PENDING', 'RUNNING') AND active_scope = 1) OR (status IN ('SUCCEEDED', 'PARTIAL', 'FAILED', 'SKIPPED') AND active_scope IS NULL))`
- `active_scope` uses InnoDB's `NULL`-distinct unique-index behavior: unlimited terminal runs coexist, and exactly one active run can hold a Workspace.

`yak_ops_governance_rule_result`

- `id`, `workspace_id`, `scan_id`, `rule_code`, `rule_version`
- `status` `VARCHAR(16) ascii_bin` — `RUNNING`, `SUCCEEDED`, `FAILED`
- `finding_count`, `issue_created_count`, `issue_reopened_count`, `issue_resolved_count` `INT NOT NULL DEFAULT 0`
- `reason_code` `VARCHAR(64) NULL`, `start_time`, `finish_time`
- keys: `PRIMARY KEY (id)`, `UNIQUE uk_governance_rule_result (scan_id, rule_code)`,
  `KEY (workspace_id, rule_code, start_time)`
- `CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED'))`
- `FOREIGN KEY (scan_id) REFERENCES yak_ops_governance_scan_run (id) ON DELETE CASCADE`

`yak_ops_governance_issue`

- identity: `id`, `workspace_id`
- rule snapshot: `rule_code`, `rule_version`, `dimension_code`, `severity`
- target: `target_type` (`DATA_MAP_TABLE`, `TASK_DEFINITION`), `target_id`, `target_name` `VARCHAR(512)`
- state: `status` (`OPEN`, `HANDLING`, `RESOLVED`, `IGNORED`), `owner_id NULL`, `ignore_until NULL`, `resolved_at NULL`, `resolved_by NULL`, `resolution NULL` (`USER`, `AUTO`)
- evidence: `observed_value DECIMAL(18,2) NULL`, `threshold_value DECIMAL(18,2) NULL`, `sample_count INT NULL`, `window_days INT NULL`
- convergence: `first_detected_at`, `last_detected_at`, `last_scan_id NULL`
- optimistic state: `revision BIGINT NOT NULL DEFAULT 1`
- audit: `create_time`, `update_time`, `create_by`, `update_by`
- keys: `PRIMARY KEY (id)`, `UNIQUE (workspace_id, id)`,
  `UNIQUE uk_governance_issue_target (workspace_id, rule_code, target_type, target_id)`,
  `KEY (workspace_id, status, severity, dimension_code)`,
  `KEY (workspace_id, owner_id, status, update_time)`,
  `KEY (workspace_id, rule_code, last_scan_id)`,
  `KEY (workspace_id, target_type, target_id)`
- `CHECK (status IN (...))`, `CHECK (severity IN (...))`, `CHECK (revision > 0)`,
  `CHECK (status <> 'IGNORED' OR ignore_until IS NOT NULL)`,
  `CHECK (status <> 'RESOLVED' OR (resolved_at IS NOT NULL AND resolution IS NOT NULL))`
- There is deliberately no foreign key to the data map or Task tables: the target is polymorphic across two modules, and referential cleanup belongs to the complete-scan convergence rule, not to a cascade that would erase governance history when a table is dropped.

`yak_ops_governance_issue_event`

- `id`, `workspace_id`, `issue_id`
- `event_type` — `CREATED`, `REOPENED`, `ASSIGNED`, `IGNORED`, `UNIGNORED`, `RESOLVED`, `AUTO_RESOLVED`
- `from_status`, `to_status`, `owner_id NULL`, `ignore_until NULL`, `note VARCHAR(500) NULL`, `actor_id`, `create_time`
- keys: `PRIMARY KEY (id)`, `KEY (workspace_id, issue_id, create_time)`
- `CHECK (event_type IN ('CREATED', 'REOPENED', 'ASSIGNED', 'IGNORED', 'UNIGNORED', 'RESOLVED', 'AUTO_RESOLVED'))`
- `FOREIGN KEY (issue_id) REFERENCES yak_ops_governance_issue (id) ON DELETE CASCADE`
- Append-only. No update path exists.

`yak_ops_governance_health_snapshot`

- `id`, `workspace_id`, `dimension_code` (`DEV`, `COMPUTE`, `OVERALL`), `snapshot_date DATE`
- `score DECIMAL(5,2) NOT NULL`, `base_count INT NOT NULL`, `weighted_issue_count DECIMAL(10,2) NOT NULL`
- `issue_count`, `high_count`, `medium_count`, `low_count` `INT NOT NULL DEFAULT 0`
- `evaluated_dimension_count TINYINT NOT NULL`, `formula_version` `VARCHAR(16) ascii_bin`, `scan_id`
- `create_time`, `update_time`
- keys: `PRIMARY KEY (id)`, `UNIQUE uk_governance_health_day (workspace_id, dimension_code, snapshot_date)`,
  `KEY (workspace_id, snapshot_date)`
- `CHECK (score >= 0 AND score <= 100)`, `CHECK (base_count > 0)`,
  `CHECK (evaluated_dimension_count IN (1, 2))`
- A dimension row has `evaluated_dimension_count = 1`; an `OVERALL` row has `2`. A snapshot is written only for an evaluated dimension. `OVERALL` is written only when both dimensions are evaluated. Rows for the same `(workspace, date)` are replaced as a set, so a later manual scan cannot leave a stale `OVERALL` row behind.

### Rule contract

The rule layer is an internal extension contract, not a public plugin SPI and not a database-configured registry. No external code is loaded, no rule is registered by writing to a table at startup, and there is no admin endpoint that changes rule enablement or thresholds.

```java
public interface GovernanceRule {
    String code();
    int version();
    GovernanceDimension dimension();
    IssueSeverity severity();
    IssueTargetType targetType();
    void evaluate(GovernanceRuleContext context, Consumer<IssueCandidate> sink);
}
```

`GovernanceRuleContext` carries the bound Workspace id, scan id, evaluation instant and read-only query ports. `IssueCandidate` carries the target identity, display name and numeric evidence. `evaluate` must be read-only, deterministic for the same metadata and instant, and must either enumerate every eligible target or throw. It must not write an Issue, close an Issue, read credentials or inspect user data values.

`GovernanceRuleRegistry` is populated from Spring beans. Startup fails fast if two beans declare the same code, or if a code is empty or a version is below 1. `GET /rules` reads the in-memory registry. There is no `gov_rule` or `gov_dimension` table, no startup `UPSERT`, and no way for a configuration write to reset a rule.

The governance module reads through two application contracts owned by the modules that own the data:

- `DataMapGovernanceQuery`: `hasActiveCrawl(workspaceId)`, and keyset-paged `activeTables(workspaceId, afterId, limit)` returning id, qualified display name, description, remarks and owner id. It never exposes credentials or user data values.
- `TaskGovernanceQuery`: `consecutiveFailureCandidates(workspaceId, since, minimum)` and `slowRunCandidates(workspaceId, since, minimumRuns, thresholdSeconds)`. It reads the current published definition version and `SCHEDULE` instances only.

Both contracts have a real caller (the governance rules) and are implemented by their owning modules. `yak-ops-business-governance` depends on `yak-ops-business-datamap`, `yak-ops-business-task`, `yak-ops-platform`, `yak-ops-dao` and `yak-ops-common`. Neither data map nor Task depends on governance. `yak-ops-boot` composes controllers and the scan executor. There is no publish hook, so this proposal adds no reverse dependency and no fail-open path.

### Frontend internal contract

- A new service folder `service/data-governance/asset-governance/` owns overview, issues, rules and scans, one file per surface.
- `app/data-governance/asset-governance/` owns `/overview`, `/issues`, `/issues/:issueId` and `/rules`. The existing `数据治理` product from FYOIP-15 gains one navigation item, `资产治理`; no new layout or shell is introduced.
- The routes must be added to the authenticated-path prefix in the same change. A deep link that is omitted from that predicate is silently redirected after sign-in.
- The overview renders two dimension cards, an overall score with coverage, live Issue counts, the last scan outcome and a 90-day trend. Missing snapshot days render as gaps, not zero.
- The Issue list uses the existing table, filter and dialog primitives. Batch actions submit at most 200 ids and use command-specific dialogs, not one overloaded action menu.
- The data map detail page gains a `治理` tab. It is the only change outside the new module and the navigation registration.

## Proposed Changes

### 1. One live Issue, not one row per occurrence

A `UNIQUE (..., status)` key is not an Issue identity. It permits an `OPEN` and a `HANDLING` row for the same target, and a second resolution of the same target can collide with the first `RESOLVED` row. It also makes status transitions an insert-or-replace decision rather than a state machine.

This proposal makes the target the identity: `(workspace_id, rule_code, target_type, target_id)`. Status, owner, evidence and timestamps are mutable columns on that row. Every transition writes an append-only event. Re-detection reopens the same row, preserves `first_detected_at` and the assignment, clears `resolved_at`, `resolved_by` and `resolution`, and records a `REOPENED` event.

`last_detected_at` is not `first_detected_at` renamed. The former answers "is this still happening"; the latter answers "when did this target first enter governance". Resetting the first on every reopen destroys the only fact that distinguishes a new problem from a recurring one.

The state machine is enforced in the service, not inferred by the UI. Every manual command locks its target rows with `SELECT ... FOR UPDATE` and validates the current state before writing, so a concurrent scan and a manual action serialise on the Issue row:

- `assign`: `OPEN` or `HANDLING -> HANDLING`, owner must be an active member.
- `resolve`: any non-resolved state `-> RESOLVED`, with `resolution = USER` and `ignore_until` cleared.
- `reopen`: `RESOLVED -> OPEN`, clearing the resolution fields and writing a `REOPENED` event.
- `ignore`: `OPEN`, `HANDLING` or `IGNORED -> IGNORED`, `ignore_until` required and within 1–90 days; an existing ignored window is replaced only when the submitted window differs.
- `unignore`: `IGNORED -> OPEN`, clearing `ignore_until`.
- scan detection: `RESOLVED -> OPEN` or expired `IGNORED -> OPEN`, with `REOPENED`.
- complete rule scan with no finding: `OPEN`, `HANDLING` or `IGNORED -> RESOLVED` with `resolution = AUTO` and `ignore_until` cleared.

A manual resolution is a statement about the current review, not a permanent suppression. If the condition is still present, the next complete rule scan reopens the Issue. That is deliberate: otherwise "resolve" becomes an invisible ignore.

### 2. Scan run identity, completeness and convergence

A scan is a persisted run, not a thread. Submission inserts `PENDING` with `active_scope = 1`; the worker claims it with a conditional update to `RUNNING`. The unique active index makes a second run for the same Workspace fail even if two requests race.

The worker binds `WorkspaceContext` and clears it in a `finally`. Binding is not an authorisation decision: the Workspace on the run was authorised at submission, and the worker only replays that scope.

Before any rule runs, the worker asks `DataMapGovernanceQuery.hasActiveCrawl(workspaceId)`. If a crawl is active, the run becomes `SKIPPED` with `DATA_MAP_BUSY`. It does not touch Issues and does not write a snapshot. A crawl that starts after this check is a stated race; the next complete scan converges. The scan does not override MySQL isolation to `READ COMMITTED`. InnoDB's default isolation already prevents dirty reads; `READ COMMITTED` would make one rule's reads less stable, not more. The correct guarantee here is complete-rule convergence, not a cross-rule transactional snapshot.

For each rule:

1. Insert a `RUNNING` rule result.
2. Evaluate the rule and write candidates in batches of 200, each batch its own transaction.
3. For each candidate, lock the existing Issue row by target and apply the transition rules.
4. After the rule has enumerated successfully, resolve Issues of that rule whose `last_scan_id` is not the current scan, including `NULL`, and whose status is not already `RESOLVED`. This is the only auto-close path.
5. Mark the rule `SUCCEEDED` with its counts.

A rule that throws, times out or exceeds the scan budget is `FAILED`. Its already-observed findings remain valid, but it does not auto-resolve anything. This is why a partial scan cannot erase a real problem.

Run status is derived from rule results:

- every rule `SUCCEEDED`: run `SUCCEEDED`.
- at least one rule `SUCCEEDED` and at least one `FAILED`: run `PARTIAL`.
- no rule completed: run `FAILED`.
- deliberate data map gate: run `SKIPPED`.

Only a `SUCCEEDED` run writes health snapshots. A `PARTIAL` run is visible to the operator, keeps the counts it actually observed, and leaves the previous score in place rather than publishing a number computed from missing rules.

Expired ignores are opened at the start of a scan. If the target is still present, the rule refreshes its evidence and it remains `OPEN`; if the complete rule no longer finds it, the same scan auto-resolves it. This can produce two events on one scan, which is honest: the ignore expired and the finding disappeared.

The scan has a 30-second per-statement query timeout and a 10-minute wall-clock budget. A query that times out marks the rule failed, not the whole database connection indeterminate. The scheduler is a dedicated `ThreadPoolTaskExecutor` with two workers and a queue of 64, never a shared `@Async` pool. A full queue rejects a manual request with `SCAN_BUSY`; a scheduled submission logs and skips. The product is single-node, so startup marks every `PENDING` or `RUNNING` run `FAILED` with `reason_code = APP_RESTART`, marks any `RUNNING` rule result `FAILED`, and clears `active_scope` before the scheduler starts. A clustered deployment is out of scope and must revisit this sweep rather than silently running two recovery sweeps.

### 3. Score the rate, not the raw count

A raw Issue count punishes a large Workspace for having more assets and lets a small Workspace game an "effective base" by creating empty rows. The score therefore uses a weighted Issue rate over an eligible base, with a floor that keeps tiny Workspaces from producing absurd density.

For each dimension:

- `weighted_issues` = sum of current `OPEN` and `HANDLING` Issue weights. `RESOLVED` and unexpired `IGNORED` do not count.
- `base_count`:
  - `DEV`: active data map tables in the Workspace.
  - `COMPUTE`: published Task definitions with at least one terminal `SCHEDULE` instance of their current version in the last 7 days.
- `effective_base = max(base_count, 10)`.
- `rate_per_100 = weighted_issues * 100 / effective_base`.
- `score = clamp(100 - C * ln(1 + rate_per_100), 0, 100)`, where `C = 40 / ln(501) ≈ 6.4344`.

The constant is calibrated so five `HIGH` findings in a Workspace with between 1 and 10 eligible assets score 60.00: `weighted_issues = 50`, `effective_base = 10`, `rate_per_100 = 500`, and `40 / ln(501) = 6.4344`. The logarithm prevents a single large backlog from driving the score through zero, while the clamp makes the lower bound explicit. Five `HIGH` findings in a 100-table Workspace score about 74.7; 100 `HIGH` findings in a 10-table Workspace score about 40.7. The full curve is documented in the test plan rather than left as an implementation detail.

A dimension with `base_count = 0` is `N/A`. Disabled rules cannot turn a dimension into 100 because there are no disabled rules in this release. `OVERALL` is the equal-weight average of `DEV` and `COMPUTE`, and exists only when both are evaluated. A Workspace with one evaluated dimension sees that dimension and `OVERALL = N/A`, not a fabricated overall number.

The score is a current-state score, not an average of historical scores. Daily snapshots make the trend. The snapshot stores `base_count`, `weighted_issue_count`, severity counts, `evaluated_dimension_count`, `formula_version` and `scan_id`, so a score can be explained after the algorithm changes. It is not enough to store a single decimal and hope the formula never moves.

### 4. Four rules, no dependency-graph fiction

No dependency rule is included. `yak_ops_task_definition` has no upstream or downstream relation, and `PublicationService` validates plugin parameters, not a Task graph. There is no legitimate way to distinguish an intentional root Task from an orphaned one. Shipping such a rule would either accuse every root Task or require a dependency model that is a separate proposal.

The two data map rules only inspect `ACTIVE` tables. `MISSING` means the table is no longer present; accusing it of having no owner would produce a permanent false positive that no user can fix. `DATA_MAP_TABLE_NO_DESCRIPTION` treats a nonblank observed `remarks` as a description; the rule is "no description at all", not "no curated business description". If the product later requires a curated description even when a database comment exists, that is a new rule with new semantics and a version bump.

The two Task rules describe observable execution, not a judgement about the user's SQL:

- Consecutive failures are counted over the current definition version and `SCHEDULE` instances only. A `CANCELED` instance breaks the streak. The rule does not parse `failure_code` or exception text, because the execution lifecycle does not expose a trusted ownership classification for failures.
- Slow runs use `FINISHED` instances only. A current version with fewer than three completed scheduled runs is not evaluated. A single manual backfill cannot make a Task "slow" because manual and workflow runs are excluded.

Evidence is stored as numbers. For the failure rule: observed consecutive count, threshold 3, sample count and 7-day window. For the slow rule: observed average seconds, threshold 7,200, sample count and 7-day window. The UI owns the sentence; the API never returns a raw exception message.

### 5. Module ownership and the absence of a publish hook

A new module `yak-ops-business/yak-ops-business-governance` owns the rules, scan runner, Issue lifecycle, score and snapshots. It depends on the data map read contract, the Task read contract, the platform active-member check, DAO and common. Controllers stay in `yak-ops-boot`; DTOs and VOs go to `io.yak.ops.common.bean.dto.governance` and `io.yak.ops.common.bean.vo.governance`. Data map and Task do not import governance, and the data map detail tab is a frontend call, not a backend dependency.

No pre-publication `Check Item` is included in this release. It would conflate a periodic scanner with a synchronous publish policy and would require a `task -> governance` edge or a Boot-level hook. A publish gate has different semantics: it needs a deterministic blocking decision, a versioned policy, an override path and a fail-open/fail-closed contract. It belongs in a separate proposal after this Issue lifecycle exists. This proposal changes neither `TaskDefinitionController.publish` nor `PublicationServiceImpl.publish`.

The data map detail tab is also read-only. Governance never writes a description, owner or tag into the data map. The two modules meet through a query contract and an HTTP read, not through a second writer.

### 6. What an implementation must not add

Each item below is a different product or a later decision, not an implementation detail:

1. A cross-Workspace governance unit, portfolio, ranking or "Top 5 teams" view. The current authorisation model is Workspace membership; a cross-Workspace reader needs a platform-level permission model that does not exist.
2. A governance plan, target, SLA, ticket or automatic remediation. The Issue lifecycle must stabilise first.
3. A pre-publication gate, a blocking Task check, or a `Fail-Open` hook in `publish`. That is a separate product contract.
4. Rule enablement, per-Workspace thresholds, a rule editor or frontend SQL. Rules are compiled product policy in this release.
5. A Task dependency rule. There is no dependency graph to read.
6. Cost, CU, storage-size, lineage, quality profiling or data preview inside governance.
7. A second writer into the data map, or a governance write back into a table's owner/description.
8. An Issue per occurrence, a second row per status, or an append-only Issue log without a current-state row.
9. A leaderboard or a cross-Workspace "health ranking". The score is Workspace-scoped and explains itself; comparison is a later product decision.
10. A public plugin SPI for customer rules. `GovernanceRule` is an internal contract; exposing it would require classloading, isolation, validation and lifecycle guarantees that this release does not provide.

### 7. Scheduler, snapshots and retention

The daily scheduler runs at 02:00 UTC and creates one run per Workspace, skipping a Workspace that already has an active run. It does not run inside a Task schedule and does not reuse the Task Quartz contract, because governance runs have no Task definition, version or execution record. It is an internal application job with the single-node assumption stated above.

After a `SUCCEEDED` run, the snapshot writer computes both dimensions and, when both exist, `OVERALL`. It replaces all snapshot rows for that `(workspace_id, UTC date)` in one transaction. A later manual scan on the same day replaces the set rather than leaving an old `OVERALL` beside a new `DEV` row.

Retention is a nightly chunked cleanup:

- scan runs and rule results older than 90 days;
- health snapshots older than 90 days;
- `RESOLVED` Issues and their events older than 180 days after `resolved_at`.

`OPEN`, `HANDLING` and `IGNORED` Issues are never deleted by cleanup. A deleted `RESOLVED` Issue can be recreated by a later scan, which is the same behaviour as a target that disappears and returns. The cleanup never runs a single unbounded `DELETE`.

### 8. Observability and failure language

The scan runner logs the Workspace, scan id, rule code, outcome, duration and counts at `INFO`; failures log the rule code, scan id and exception type at `WARN`. It never logs table data, Task parameters, credentials or raw exception messages in user-facing responses.

`reason_code` is a stable machine value. The API returns it with a safe localized message. The raw exception is preserved in the server log with its cause for operators. Execution decisions never parse exception text, and the UI never displays it.

## Compatibility, Deprecation, and Migration Plan

- The migration is the first migration after `V7__v1_9_0.sql`, numbered `V8__v1_10_0.sql`. It is additive and forward-only. A fresh install applies `V1` through `V8`; a database already on 1.9.x applies `V8` only. No existing table, column or row is changed.
- There is no backfill. The new tables start empty; the first successful scan creates Issues and the first complete scan creates snapshots for evaluated dimensions. Until then the overview reports `N/A` and the last scan as absent.
- No existing REST endpoint, Task publication path, data map endpoint or plugin SPI changes. The data map detail page gains a read-only tab after FYOIP-15's UI exists; the data map API is untouched.
- A datasource deletion cascades its data map tables but does not cascade governance Issues. The next complete data map rule scan resolves those Issues. A Task definition deletion or unpublish likewise resolves on the next complete Task rule scan. This one-scan delay is deliberate: the governance history must not vanish because a source object was deleted.
- Rule definitions are code-owned. A rule version bump is a code change; existing Issues are updated to the new version on their next observation. If a rule is removed in a later release, its open Issues must be explicitly closed by that release's migration or by a documented convergence run; this proposal does not provide a generic "delete rule" path.
- There is no downgrade path. The supported rollback is restoring the pre-upgrade backup, consistent with previous releases.
- No compatibility layer, alias endpoint or duplicate Issue model is introduced.

## Test Plan

- **Issue identity:** create an Issue, move it through `OPEN`, `HANDLING`, `RESOLVED` and back to `OPEN`, and assert there is still exactly one row for the target. A second scan must update that row, not insert another. This is the regression test for a `UNIQUE (..., status)` design.
- **State machine:** assign, resolve, manual reopen, ignore, re-ignore with a different window, unignore and scan-driven reopen from every legal source state; assert every illegal transition returns `INVALID_ISSUE_STATE` and writes no event. Assert `IGNORED` can never exist without `ignore_until`, and an expired ignore is opened on the next scan.
- **Batch atomicity:** submit 200 valid ids and one foreign-Workspace id; assert no Issue changes and the response identifies the invalid request. Submit 201 ids and assert `BATCH_TOO_LARGE`.
- **Scan lock:** two simultaneous submissions for one Workspace must result in exactly one active run; the losing insert must fail on `uk_governance_scan_active`, not on a pre-check. A second Workspace must still be admitted.
- **Startup recovery:** seed a `RUNNING` run with `active_scope = 1` and a `RUNNING` rule result, start the context, and assert the run is `FAILED` with `APP_RESTART`, the rule result is `FAILED`, the lock is released and a new submission is accepted immediately.
- **Data map gate:** seed an active data map crawl, start a scan, and assert `SKIPPED` with `DATA_MAP_BUSY`, no Issue changes and no snapshot. A crawl started after the check is not claimed as blocked; the next complete scan is asserted to converge.
- **Completeness:** a rule that throws after emitting one candidate must be `FAILED`, must leave its prior Issues open, and must not create a snapshot. A successful rule with zero candidates must resolve every prior Issue of that rule, including an ignored one.
- **Deduplication:** a rule that emits the same target twice in one scan must produce one Issue. A `MISSING` table must never be emitted by either data map rule.
- **Failure streak:** a current version with `FAILED, FAILED, CANCELED` is not a streak; `FAILED, FAILED, FAILED` is. Manual and workflow instances are excluded. A new version with two failures is not accused.
- **Slow Task:** three `FINISHED` scheduled runs at 7,201 seconds average produce a `MEDIUM` Issue; two runs do not; a single 12-hour manual run does not; canceled and failed runs do not enter the average.
- **Score curve:** zero findings = 100; five `HIGH` findings with `1 <= base_count <= 10` = 60.00; five `HIGH` in 100 assets ≈ 74.7; 100 `HIGH` in 10 assets ≈ 40.7; an enormous penalty clamps at 0; `base_count = 0` is `N/A`, not 100.
- **Overall:** `DEV` and `COMPUTE` both evaluated produces an `OVERALL` row; one missing dimension leaves `OVERALL` absent and coverage reports `1/2`; no dimension produces no snapshot.
- **Snapshot idempotency:** two complete scans on the same UTC date replace the day's rows as a set; an earlier `OVERALL` cannot survive a later one-dimension scan.
- **Retention:** seed old scan runs, snapshots and resolved Issues; run cleanup; assert old rows are deleted, open/handling/ignored rows are retained, and deletes are chunked.
- **Workspace isolation:** every read and write with a foreign-Workspace Issue, scan or owner returns `RESOURCE_NOT_EXISTS` or `INVALID_OWNER` without revealing the other Workspace's data.
- **Rules registry:** duplicate rule codes fail fast at startup; invalid versions fail fast; no startup SQL writes occur when the context starts.
- **Database integration:** apply `V8` after `V7`; assert the unique active scan key, the unique Issue target key across statuses, the rule-result foreign key cascade and the health-snapshot uniqueness. Assert the tables use `VARCHAR(64)` identities and `DATETIME(3)`.
- **End to end:** create an active table with no owner and no description, run a manual scan, assert two Issues and a dimension score; assign and resolve one; run a complete scan and assert the fixed target resolves while the unfixed one reopens; open the data map detail tab and assert it lists the same Issue.
- **Frontend:** the `资产治理` route is reachable before and after sign-in, the authenticated path prefix survives a signed-out deep link, the overview renders `N/A` rather than zero for an unevaluated dimension, and a missing snapshot day renders as a trend gap.
- **Verification commands:** backend `bash mvnw -pl '!yak-ops-ui,!yak-ops-dist' verify`; frontend checks follow `yak-ops-ui/docs/tooling.md`. Integration tests that require MySQL must use the repository's owned database resources, not a developer database.

## Rejected Alternatives

1. **Keep `UNIQUE (..., status)` and add an application-level "one open row" rule.** A constraint that does not constrain is worse than no constraint: it passes single-threaded tests and fails under a retry, a manual transition and a concurrent scan. The target is the identity; history belongs in an event table.

2. **Keep `gov_*` prefixes, `BIGINT` ids and `TIMESTAMP DEFAULT NOW()`.** The repository uses `yak_ops_*`, 64-character application ids and `DATETIME(3)` with explicit audit columns. A new domain may choose different names, but the persisted format becomes a second convention every reader must learn. The additive tables follow the existing one.

3. **Store rule definitions in `gov_rule` and upsert them at startup.** Startup writes make a rolling restart a configuration mutation, and an upsert can overwrite operator choices. The rules are compiled code; the registry belongs in the application, and the read-only endpoint reflects it.

4. **Make rule enablement and thresholds per Workspace.** It looks flexible, but it lets a Workspace improve its score by switching rules off, and it requires a threshold schema, validation, audit and a stable "disabled" semantics that this release does not have. Product policy is fixed here; configurability needs its own proposal.

5. **Keep a cross-Workspace `gov_unit` with a JSON list of Workspace ids.** There is no platform-level role that may read two Workspaces, no foreign key for the list and no clear owner of the aggregate. It would turn a convenience view into a data leak. Workspace scope is the only authorisation boundary this release can enforce.

6. **Ship governance plans in the same release.** A plan needs a baseline, a current value, a target direction, a completion rule and a stable definition of "progress". The Issue lifecycle and score must exist before those terms mean anything. A `template_code` without a template table is not a plan.

7. **Put a 2-second `Check Item` in Task publication with `Fail-Open`.** The current publication method locks the definition row inside a transaction. A governance call there would hold the lock for the check duration, and the product would need to distinguish "violation" from "checker unavailable" in a path that currently has no governance dependency. That is a separate contract with its own policy, override and audit.

8. **Show a five-axis radar with only two evaluated dimensions.** Three empty axes read as "healthy" or "unknown" depending on the renderer, and neither is true. Two dimension cards and a trend are the honest representation until a third dimension ships.

9. **Score raw Issue counts without a denominator.** Large Workspaces accumulate more findings for the same quality, and a small Workspace can satisfy an `asset_count < 10` gate with empty rows. A weighted rate over an eligible base, with a floor and a documented curve, is defensible and testable.

10. **Close Issues after any scan, including a partial one.** A failed rule has not observed its whole target set, so "not found" is indistinguishable from "not evaluated". Auto-close is permitted only after that rule completes. This is the core correctness rule of the scan design.

11. **Use `READ COMMITTED` and delay a scan by 15 minutes when a crawl is running.** InnoDB's default isolation already prevents dirty reads; `READ COMMITTED` gives a rule a less stable view, not a more stable one. A fixed delay has no retry, expiry or stale-lock semantics. The run checks for an active crawl, records `SKIPPED`, and relies on complete-scan convergence.

12. **Expose `GovernanceRule` as a customer plugin SPI or accept SQL from the UI.** That requires classloading, isolation, version negotiation, credential boundaries and a validation sandbox. A code-owned rule set is smaller, auditable and sufficient for four rules.

13. **Reuse the Task dependency table for `DEV_TASK_NO_DEPENDENCY`.** There is no such table, and a root Task is not an error. A rule that cannot distinguish a legitimate source Task from an orphan is a false-positive generator.

14. **Keep an append-only Issue log and query the latest row for current state.** Every list and score query would need a window function or a self-join, and "current" would depend on ordering. A current row plus an event log gives both cheap reads and complete history.

15. **Run governance scans through the existing Quartz Task scheduler.** Quartz admits scheduled Task definitions; a governance run has no Task definition, version or execution record. Reusing that contract would make a non-Task job look like a Task and add a second meaning to the schedule tables.

16. **Build a cross-Workspace health leaderboard now.** A leaderboard needs comparable coverage, a policy for Workspaces with one evaluated dimension, and a decision about whether a large Workspace's absolute backlog matters more than its density. None of those are defined here, and a ranking without them is a number that invites the wrong behaviour.

17. **Store an Issue per scan or per occurrence.** It grows with targets times scans, makes the current state a `MAX` query and turns every score into a deduplication problem. One current row per target plus events is the smaller and more truthful model.

18. **Persist a localized sentence in `detail`.** A sentence freezes one language into the database, cannot be re-rendered when the UI changes and is hard to query. Numeric evidence plus a stable rule code lets the owning boundary localize the message.
