# Dashboard Requirements

## Purpose

Dashboard provides persistent, versioned BI composition. It composes reusable Analysis references and optional inline analysis payloads into layouts, filters and interactions while keeping editing and published state independent.

## Supported use cases

The module supports:

- list Dashboard identities;
- read the current Dashboard detail;
- create a Dashboard and append version V1;
- append a new Dashboard version;
- list version history;
- read an immutable version snapshot;
- publish the current version;
- read the published snapshot;
- restore a historical snapshot as a new version;
- delete a Dashboard and its owned version history;
- reject Analysis deletion while any historical DashboardVersion still references it;
- project the effective Dashboard snapshot into Lineage after commit.

The existing deprecated `activateVersion` API remains a compatibility alias for restore. It must not reactivate an old version row.

## Composition contract

A Dashboard version may contain:

- name and description snapshot;
- optional `activeDatasetId` metadata;
- optional theme JSON object;
- widgets;
- global filters and widget-field bindings;
- interactions.

### Widget binding

Every widget must choose exactly one content mode:

```text
analysisId XOR inlineAnalysis
```

A reusable `analysisId` must reference an existing Analysis. An inline payload is stored as part of the immutable DashboardVersion snapshot.

### Existing validation limits

Stage 1 behavior remains the contract:

- at most 200 widgets;
- 24-column layout grid;
- `x` in 0..23;
- positive width/height within existing limits;
- widget must not overflow the 24-column grid;
- at most 20 global filters;
- at most 200 filter bindings;
- at most 100 interactions;
- existing JSON object/scalar and serialized-size validation remains unchanged.

## Version requirements

DashboardVersion is append-only for the lifetime of a Dashboard.

Saving composition must:

```text
normalize candidate composition
 -> allocate next version number using current repository strategy
 -> append immutable version snapshot
 -> move currentVersion pointer
```

Existing historical version rows must not be mutated by save or restore.

### Restore

Restoring historical V2 while current is V7 must produce V8. It must not point `currentVersionId` back to the old V2 row.

### Current and published pointers

`currentVersionId` represents the latest editable snapshot.

`publishedVersionId` represents the externally effective published snapshot.

They may intentionally differ, for example:

```text
current   = V8
published = V5
```

Saving V9 must not change `publishedVersionId`.

## Publication requirements

Publishing must move the published pointer to the current immutable version. It must not copy, rewrite or renumber that version.

Publishing when current and published already point to the same version remains idempotent from the Dashboard business perspective.

## Analysis reference requirements

Reusable Analysis definitions remain owned by `yak-ops-business-analysis`.

Dashboard owns only the reference embedded in each version snapshot. A DashboardVersion that stores `analysisId = 10` does not freeze the current Analysis definition as a Dashboard-owned copy.

Historical references matter for deletion safety: if any historical DashboardVersion references Analysis 10, Dashboard's `AnalysisDeletionGuard` integration must continue to block deletion of Analysis 10.

## Inline analysis requirements

Inline Analysis is a Dashboard-owned opaque JSON payload frozen inside the version snapshot.

Dashboard validation checks its configured JSON shape/size but does not introduce Dataset lifecycle/schema ownership.

Lineage may perform best-effort parsing of known inline fields (`datasetId`, dimensions, metrics, filters, sorts). Failure to understand an inline payload must not corrupt Dashboard truth.

## Lineage requirements

Lineage is a derived projection of committed Dashboard state.

After Dashboard mutation commits:

- use the published snapshot when a published version exists;
- otherwise use the current draft snapshot;
- synchronize in an independent transaction;
- isolate projection failure from the already committed Dashboard business operation;
- preserve `DASHBOARD_BINDING` evidence semantics and existing asset/relation keys.

Lineage never becomes Dashboard command truth.

## Persistence requirements

The existing six Dashboard tables and Flyway baseline remain authoritative persistence for this stage:

- `yak_dashboard`;
- `yak_dashboard_version`;
- `yak_dashboard_widget`;
- `yak_dashboard_filter`;
- `yak_dashboard_filter_binding`;
- `yak_dashboard_interaction`.

Repository contracts expose Dashboard domain/JDK values. DAO/PO/MyBatis details stay below repository adapters.

## HTTP compatibility

Stage 2 does not change `/api/v1/dashboards/**`, request/response JSON fields, long-ID serialization, or the deprecated activate route.

## Explicit non-goals

This architecture governance does **not** add:

- a Dashboard execution engine;
- Dataset query execution;
- automatic Dataset ONLINE/schema validation for `activeDatasetId`;
- an Analysis definition copy/version inside Dashboard;
- in-place historical version editing;
- publish-time version copying;
- a new Dashboard status state machine;
- stronger version-number concurrency/CAS semantics;
- synchronous Lineage truth.

The current `nextVersionNo -> appendVersion` strategy is a known concurrency boundary. Stronger allocation/retry/CAS behavior is a separate domain change and must not be smuggled into an architecture-only refactor.
