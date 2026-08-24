# Dashboard Domain

## Aggregate truth

Dashboard is a versioned composition aggregate with two levels of truth:

```text
DashboardAsset
    = stable identity
      + current-version pointer
      + published-version pointer
      + current-list projection fields

DashboardVersionSnapshot
    = immutable composition truth for one version
```

`DashboardAsset.name` and `description` follow the current-version projection for list/read convenience. They do not replace the name/description snapshot stored on historical DashboardVersion rows.

## DashboardAsset

`DashboardAsset` owns:

- Dashboard ID;
- current version ID/no;
- published version ID/no;
- published time;
- current projected name/description;
- create/update timestamps.

It does not own mutable widget/filter/interaction collections.

## DashboardVersion

`DashboardVersion` is immutable after append.

It owns version metadata:

- version identity;
- Dashboard identity;
- monotonically selected version number under the current repository strategy;
- name/description snapshot;
- optional active Dataset ID metadata;
- create time.

`DashboardVersionSnapshot` completes that truth with:

- theme;
- widget snapshots;
- global-filter snapshots;
- interaction snapshots.

### Append-only invariant

For a live Dashboard:

```text
save candidate
    != mutate current row

save candidate
    = append next immutable version
```

Historical rows may only disappear when their owning Dashboard is deleted according to existing persistence semantics.

## DashboardDraft

`DashboardDraft` is a candidate composition used to create the next immutable DashboardVersion.

Unlike the former Analysis `Draft` terminology, Dashboard Draft has a real relationship to version creation: it is normalized input before append. It is not itself persistent lifecycle state and there is no `DRAFT` database status row.

## Current pointer

`currentVersionId` identifies the current editable Dashboard snapshot.

Moving current does not make that version published.

## Published pointer

`publishedVersionId` identifies the externally effective published snapshot.

Publishing changes the pointer only:

```text
publishedVersionId = currentVersionId
```

It does not mutate the version snapshot.

Current and published may diverge for an arbitrary number of new draft versions.

## Restore invariant

Restore is copy-forward, not pointer rewind.

```text
V1 -> V2 -> V3(current)
restore V1
        ↓
append V4(copy of V1 composition)
current -> V4
```

If published is V2, restore must leave published at V2 until an explicit publish occurs.

The deprecated `activateVersion` name is compatibility vocabulary only; its domain meaning is restore-as-new-version.

## Composition

### Widget

A widget contains layout plus exactly one content binding:

```text
reusable Analysis reference
OR
inline Analysis payload
```

A linked widget freezes the Analysis ID reference in the DashboardVersion. It does **not** freeze the referenced Analysis definition.

An inline widget freezes its inline payload directly in the DashboardVersion.

### Layout

Layout belongs to the DashboardVersion. The current contract uses a 24-column grid and the existing dimension/min-size constraints.

### Global filters

Global filters, filter keys, default values and widget-field bindings belong to the DashboardVersion snapshot.

A filter binding references a widget key and a field ID. Dashboard owns the binding relation, not the field's Dataset schema truth.

### Interactions

Interactions belong to the version snapshot and connect an existing source widget/field to an existing target filter.

## activeDatasetId

`activeDatasetId` is currently optional version metadata.

It is not proof that a Dataset is ONLINE, that a Dataset version exists, or that every field binding belongs to that Dataset. No such new invariant is introduced by this refactor.

## Analysis ownership

Analysis owns reusable analytical definitions.

Dashboard owns:

- an Analysis ID reference frozen into a DashboardVersion; or
- an inline payload frozen into a DashboardVersion.

Dashboard does not become a second Analysis-definition owner.

### Historical deletion restriction

Dashboard's historical version truth means an old Analysis reference remains meaningful even when the current Dashboard version no longer uses it.

Therefore the existing deletion invariant remains:

```text
any historical DashboardVersion references Analysis X
    => Analysis X is not deletable
```

## Lineage ownership

Lineage owns graph truth.

Dashboard Lineage owns only the deterministic/best-effort projection logic from committed Dashboard state.

The effective lineage snapshot rule is:

```text
published exists -> project published
otherwise         -> project current
```

Projection happens after commit and in an independent transaction. Projection failure does not roll back already committed Dashboard truth.

## Inline lineage parsing

Inline Analysis JSON is not promoted into a second Dashboard analysis domain model.

`DashboardInlineLineageExtractor` may interpret known fields for derived lineage evidence. Parse status such as success/partial/unresolved describes projection evidence, not Dashboard lifecycle state.

## Persistence truth

Database rows own durable Dashboard identity, pointers and immutable version snapshots.

Repository adapters translate between persistence rows/JSON columns and Dashboard domain values. PO/Mapper/MyBatis types are not domain contracts.

## Known domain gap: version allocation concurrency

The current flow obtains `nextVersionNo` and then appends. The unique `(dashboard_id, version_no)` database key protects duplicate durable numbers, but the application does not yet define a richer retry/CAS allocator contract.

This is intentionally documented as a future domain/concurrency concern. Architecture governance must not pretend the gap is solved.
