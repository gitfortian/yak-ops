# FYOIP-21: Semantic Model — Relationships, Time Role, Synonyms and Ossie Export

Status: Accepted

Discussion: N/A until a pull request opens

Decision: Accepted by review on 2026-11-02; the PR reference is recorded here when the implementation pull request opens

Implementation: In progress — starts from `V14__v1_16_0.sql`

Release: Tracked in [v1.16.0](../releases/v1.16.0.md); not yet released

Related FLIPs: FYOIP-7, FYOIP-9, FYOIP-10, FYOIP-11, FYOIP-13, FYOIP-14. External reference: Apache Ossie core metadata specification `0.2.0.dev0` (draft, not a storage format).

## Motivation

The platform already owns the semantic **nodes**. Field standards carry type, length, security level, business caliber, lookup and measurement unit. Concept dimensions, DWD/DIM/DWS/ADS models, and model fields that bind both a standard and a metric exist. Atomic, derived and composite metrics, modifiers and time periods exist. The data map observes every physical table.

What the platform does not own is the **edges**. Five facts describe the gap.

- `yak_ops_modeling_model` and `yak_ops_modeling_model_field` describe one model in isolation. `dao/entity/modeling/` contains no relation entity, so the product cannot state that `dwd_trade_order_detail` joins `dim_customer` on `customer_id`. Any cross-model question is answered by a human rebuilding the join from memory.
- `yak_ops_modeling_model_field` carries `standard_id`, `metric_id`, `aggregate_type`, `is_primary_key` and `is_partition`. `is_partition` is a storage decision. Nothing records whether a field is a **time role** that time filtering and trend analysis may use, so a consumer must guess between `created_at`, `paid_at` and `biz_date`.
- `yak_ops_modeling_standard_field` has exactly one `cn_name` and one `en_code`. Business synonyms ("销售额", "GMV", "成交金额") have nowhere to live, so search and asset discovery only work when the user already knows the canonical name.
- `is_primary_key` describes table structure, not the meaning of a row. Nothing declares the grain ("one row per order line"), which is the most common cause of double counting in a star schema.
- A semantic model cannot leave the product. There is no projection, so definitions cannot be reviewed in a pull request, versioned in Git, or handed to a BI tool.

The result is asymmetric: four object types with good internal quality, and no way to connect or export them. Adding more object types would not fix that.

This proposal adds four edges and one projection. It deliberately does not add a query engine, an import path, hierarchies, or automatic join inference.

| Included in this proposal | Deliberately excluded |
| --- | --- |
| Model-to-model relationships with ordered composite keys | Cardinality beyond many-to-one |
| Key pairs reference field **codes**, never field row ids | Ossie import, round-trip or storage |
| A time-role flag on model fields, independent of datatype | Metric window semantics, already owned by `MetricTimePeriod` |
| Synonyms for standards, model fields and metrics | A glossary module, approval flow or term ownership |
| An explicit grain statement per model | Unique key sets beyond the primary key |
| A read-only Apache Ossie YAML projection | Hierarchies, drill paths or a semantic query engine |
| Two governance rules over models and relations | Scoring the new rules into the health score |
| A portable-aggregate whitelist for synthesized metric export | Dimension member exposure and value-level synonyms |
| | A publish hook guarding physical tables behind relations |

Repository facts that constrain the design:

- Modeling tables are fork-owned (FYOIP-7 to FYOIP-14). Extending them does not touch the upstream DolphinScheduler tables still carried by `main`, so this change stays merge-friendly.
- **Model fields are replaced wholesale on every save.** `ModelServiceImpl.update` runs `models.deleteFields(id)` and re-inserts every field (`ModelServiceImpl.java:200`). Field row ids are therefore unstable across edits, and any reference to them dangles after the first edit of either side. This single fact drives the key-reference decision below.
- The latest migration is `V13__v1_15_0.sql` at release `1.15.0`. This proposal starts at `V14__v1_16_0.sql`, which requires the release version bump and release notes that every migration PR needs.
- The metadata database is MySQL with InnoDB and `utf8mb4_unicode_ci`. No table uses a native JSON column. Identifiers are 64-character application-generated strings, and every table carries `revision`, `create_time`, `update_time`, `create_by` and `update_by`.
- The health score has exactly two dimensions today: `GovernanceScoreCalculator` counts active data-map tables for `DEV` and recent scheduled instances for `COMPUTE`. There is no modeling dimension.
- Workspace isolation is enforced at the request boundary through `WorkspaceContext`. A relation, synonym set or export never crosses a Workspace.
- Modeling has no execution path. Nothing here touches Task definition versions, dispatch, worker slots or instance state.
- The Ossie specification is at `0.2.0.dev0`, declares itself unstable, and already made breaking changes between `0.1.1` and `0.2.0.dev0` — including the decision to reference dataset fields by name rather than source columns. It is treated as an output format only.

## Public Interfaces

### Product behavior

1. A model detail page gains a **Relations** tab listing every relation where the model is either side, rendered as one health-coded card per target dimension — dimension name as the title, join style and key mapping as secondary monospaced detail, a colored left border for health, and actions attached to the card. Cards ordered by health: the ones needing attention first. The default detail tab remains basic information; the relations tab is reached through `?tab=relations`.
2. A relation is created from the many side only. A DWD, DWS or ADS model shows the **New relation** action; a DIM model shows the list read-only, because a dimension can only be the `to` side. The relation name is auto-generated as `{from}_to_{to}` (suffixed `-2`, `-3` on collision) and stays editable.
3. The field list gains two controls per field: a **time role** switch and a **synonym** tag editor. The switch is independent of `data_type`, mirroring the Ossie `dimension.is_time` role. Synonym editing ships for model fields only; standards and metrics expose the same REST surface without a UI in this release.
4. The model's basic-information tab gains a **grain** field. Saving a DWD, DWS or ADS model without one is allowed but produces a governance finding rather than a hard block, so existing models stay editable.
5. **Export** returns a JSON envelope carrying pre-flight checks and the `.ossie.yaml` document for the model plus every model reachable through one relation hop, with `version: 0.2.0.dev0` recorded at the top. The checks summarize what the projection had to skip or flag — keys without a standard, metrics skipped for missing unique bindings, unresolvable field codes — so the download is a reviewed decision rather than a silent output. Metrics are exported only where an unambiguous field binding exists (see decision 8), and the checks list every skipped metric with its reason — no field binding, or bindings to several fields — so a skip is legible rather than silent.
6. Two findings appear in the existing governance list: a relation key without a standard, and a model without a grain statement. Both are excluded from the health score in this release.

### REST

All paths are Workspace-scoped and authenticated. Mutations require the same permission as model editing. Paths follow the existing `/api/v1/data-modeling` prefix used by the dimension-modeling, metrics and data-standards controllers.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/data-modeling/models/{modelId}/relations` | Relations where the model is `from` or `to`, with key pairs |
| POST | `/api/v1/data-modeling/models/{modelId}/relations` | Create a many-to-one relation; `modelId` must be the `from` side |
| PUT | `/api/v1/data-modeling/relations/{relationId}` | Replace name, join type and key mapping |
| DELETE | `/api/v1/data-modeling/relations/{relationId}` | Delete a relation |
| POST | `/api/v1/data-modeling/models/{modelId}/relations/proposals` | Propose key pairs for a target model |
| PUT | `/api/v1/data-modeling/models/{modelId}/grain` | Set `grainNote` |
| GET | `/api/v1/data-modeling/synonyms?targetType=&targetId=` | Read synonyms of one target |
| PUT | `/api/v1/data-modeling/synonyms` | Replace the synonym set of one target atomically |
| POST | `/api/v1/data-modeling/models/{modelId}/semantic-export` | Return the pre-flight checks and the Ossie YAML document |

Request and response shapes that are not obvious:

- `POST .../relations/proposals` takes `{ "targetModelId": "..." }` and returns a bare candidate array `[ { "fromFieldCode": "...", "toFieldCode": "...", "fromStandardEnCode": "...", "toStandardEnCode": "...", "sameStandard": true, "typesCompatible": true, "selectable": true, "reason": "..." } ]`, at most three candidates, ordered by standard match then name match. A candidate whose key field has no standard is returned with `selectable: false` and the reason, so the UI can show why it is blocked.
- `PUT .../synonyms` takes `{ "targetType": "STANDARD_FIELD", "targetId": "...", "terms": ["...", "..."] }` and replaces the whole set in one transaction. This surface accepts `STANDARD_FIELD` and `METRIC`; `MODEL_FIELD` is rejected here because field row ids are unstable, and is served by `models/{modelId}/fields/{fieldCode}/synonyms` using the composite `modelId:fieldCode` key.
- `joinType` accepts `INNER` and `LEFT`; `LEFT` is the default because a fact row may outlive a missing dimension row.

### Persisted formats

```sql
CREATE TABLE yak_ops_modeling_relation (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated relation identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning both models',
    name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Relation name, unique per Workspace',
    from_model_id VARCHAR(64) NOT NULL COMMENT 'Many side of the relation; must not be a DIM model',
    to_model_id VARCHAR(64) NOT NULL COMMENT 'One side of the relation; must be a DIM model',
    join_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'INNER or LEFT',
    description VARCHAR(500) NULL COMMENT 'Optional business description',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic version incremented after every update',
    create_time DATETIME(3) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    update_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_relation_name (workspace_id, name),
    KEY idx_modeling_relation_from (workspace_id, from_model_id),
    KEY idx_modeling_relation_to (workspace_id, to_model_id)
);

CREATE TABLE yak_ops_modeling_relation_column (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated key-pair identity',
    workspace_id VARCHAR(64) NOT NULL,
    relation_id VARCHAR(64) NOT NULL COMMENT 'Owning relation',
    ordinal INT NOT NULL COMMENT 'Zero-based position; the from and to codes share it',
    from_field_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'en_code of the many-side field',
    to_field_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'en_code of the one-side field',
    revision BIGINT NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    update_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_relation_column (relation_id, ordinal)
);

CREATE TABLE yak_ops_modeling_synonym (
    id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'STANDARD_FIELD, MODEL_FIELD or METRIC',
    target_id VARCHAR(148) NOT NULL COMMENT 'MODEL_FIELD composes modelId:fieldCode; field row ids are unstable',
    term VARCHAR(64) NOT NULL COMMENT 'Business synonym, unique per target',
    revision BIGINT NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    update_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_synonym_term (workspace_id, target_type, target_id, term),
    KEY idx_modeling_synonym_target (workspace_id, target_type, target_id)
);

ALTER TABLE yak_ops_modeling_model
    ADD COLUMN grain_note VARCHAR(200) NULL COMMENT 'Declared meaning of one row; null when undeclared';

ALTER TABLE yak_ops_modeling_model_field
    ADD COLUMN is_time TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Analysis time role, independent of data_type';
```

Two storage rules follow from the field-replacement fact stated in the motivation:

- Key pairs reference **field codes**, not field row ids. Resolution joins `yak_ops_modeling_model_field` on `(model_id, en_code)` at read time.
- No table stores a key mapping as JSON. Ordered pairs live in a child table for the same reason the codebase avoids native JSON columns: queryable validation and no silent shape drift.

### Rule contract additions

Two rules register into the existing `GovernanceRuleRegistry`.

| Rule code | Target | Severity | Fires when |
| --- | --- | --- | --- |
| `MODEL_RELATION_KEY_UNSTANDARDIZED` | `MODELING_MODEL` | HIGH | A relation touches this model and one of its key fields has `standard_id IS NULL` |
| `MODELING_GRAIN_UNDECLARED` | `MODELING_MODEL` | MEDIUM | The model type is DWD, DWS or ADS and `grain_note` is null or blank |

Both require a new `IssueTargetType.MODELING_MODEL` value and a display name projection. The governance UI resolves the target name from `yak_ops_modeling_model.cn_name`; no new detail page is required for this release.

**Both rules produce Issues only and are excluded from the health score in this release.** The score calculator reads exactly two dimensions today, both hard-wired to other object types; wiring modeling into the score needs its own dimension design and is deferred. The exclusion is explicit: `GovernanceRule.scored()` defaults to `true` and both rules override it to `false`; the scan persists that flag on `yak_ops_governance_issue.scored`, and the score aggregation adds `AND scored = 1`. Without the flag the existing dimension-only aggregation would silently absorb the new findings.

## Proposed Changes

### 1. A relation is a Workspace-owned edge, not a field on a model

Storing `relatedModelIds` on the model would make one edge appear twice and force every reader to de-duplicate. A separate edge table keeps one row per relation, lets either side list it, and makes deletion of a model fail closed when relations still reference it.

Deleting a model with relations is rejected rather than cascaded, via a new modeling error code checked in `ModelServiceImpl.delete`. A silent cascade would leave orphaned dimensions in every consumer's mental model.

One boundary deserves an explicit statement: a relation binds **logical models, not physical tables**. The publication module today syncs columns through `ALTER` statements and has no table-drop flow, and a drop performed directly in the target warehouse is outside the platform's reach, so the semantic layer adds no guard there and no publish hook — mirroring the FYOIP-16 decision that governance also refused one. Degradation is already defined: the export projects the publication record, and a stale or missing publication is a future governance finding candidate, not a write barrier inside this proposal.

### 2. Many-to-one only, from the fact side, with auto-generated names

The relation names a `from` (many) side and a `to` (one) side. There is no cardinality enum, matching the Ossie relationship shape: many-to-one is sufficient for a star, and every other cardinality is either a modelling error or a bridge table that deserves its own model.

Two validations fall out of the star shape:

- `to_model_id` must reference a DIM model; `from_model_id` must not.
- The same ordered key-pair set between the same model pair is rejected as a duplicate, even though two relations between the same pair with different keys stay legal (a slow merchant dimension may join on both a natural and a surrogate key).

The name defaults to `{from}_to_{to}` with a numeric suffix on collision, so the user never has to invent one; it remains editable for readability in exports.

### 3. Key pairs reference field codes, never field row ids

`ModelServiceImpl.update` deletes and re-inserts every field on each save. A relation column that stores `ModelFieldEntity.id` would dangle after the first edit of either model, and no migration could repair it because the old ids are gone.

`yak_ops_modeling_relation_column` therefore stores `from_field_code` and `to_field_code`, resolved against `(model_id, en_code)` at read time. This mirrors the Ossie `0.2.0` decision to reference dataset fields by name, for the same reason: names are the stable business contract, row identities are storage detail.

A code that no longer resolves is a first-class state, shown in the UI as "字段已移除" and rejected at export time with the reason attached. It is not silently dropped.

### 4. Join keys must be standardized

A relation key whose field has no bound standard is rejected at save time, and reported by `MODEL_RELATION_KEY_UNSTANDARDIZED` for relations created before this rule existed.

The reason is symmetry: the platform's claim is that a business term resolves to one field definition. A join on two fields that mean different things is exactly the failure this proposal exists to prevent. Failing closed at the edge is cheaper than discovering the mismatch in a report.

### 5. Time role is a field attribute, independent of datatype

`is_time` mirrors Ossie's `dimension.is_time`: it is a **role**, not a type. `d_year` is an Integer that is a time dimension; `created_at` is a DateTime that is an audit timestamp and not an analysis time dimension.

The default is `false` on migration, including for date-typed fields. Ossie defaults date types to `true`; this proposal does not, because existing models already contain audit timestamps and silently promoting them would change every time-based consumer without a reviewer noticing. The field editor offers "mark all date fields" as an explicit bulk action instead.

### 6. Synonyms live in a side table keyed by target

Synonyms on `yak_ops_modeling_standard_field` would require one row per term or a JSON column. A side table keyed by `(target_type, target_id)` avoids touching three core tables, keeps the unique constraint honest, and lets the same term exist on a standard and on a metric without conflict.

The REST surface covers all three target types from day one; the UI covers model fields only in this release. Standards and metrics synonyms are reachable through the API and gain editors when those pages are next touched.

A related need is **enum membership**. For a filtered question like "orders in 华东", a consumer must know whether the stored value is `华东`, `EAST` or `HD`. The binding half already exists — `StandardFieldEntity.lookupId` and `ModelFieldEntity.lookupId` both point at code tables — so this proposal adds nothing there. What is missing is a member **surface**: querying valid values, value-level synonyms, and capping. The natural projection home is `field.ai_context.examples` with a member cap; it belongs to the same future proposal as hierarchies and is recorded in the excluded list.

The same table is the natural place for a future glossary; this release does not build one.

### 7. Grain is declared, never inferred

`grain_note` is a short human statement ("one row per order line"), edited on the model's basic-information tab. It is not derived from the primary key, because the primary key describes structure and the grain describes meaning; a table can have a correct primary key and a misunderstood grain.

The rule reports rather than blocks. Blocking would make every existing model unsaveable on upgrade, which is a migration disguised as validation.

### 8. Ossie export is a projection with an explicit metric policy

The exporter reads the model, its fields, its metrics and one hop of relations, then renders YAML. It writes nothing back, and no table stores Ossie documents.

Mappings and limits that are deliberate:

- Dataset scope is one model, not one Workspace. A Workspace-wide document would exceed the Ossie rule that a document contains exactly one semantic model.
- `dataset.source` is the published physical table when the model has a publication, and the layer-prefixed model name otherwise. The exporter never invents a database or schema.
- `grain_note` is projected into `datasets[].ai_context.instructions` and preserved verbatim in `custom_extensions`, so the meaning survives even though Ossie has no grain attribute.
- **Metrics are exported only when exactly one model field binds the metric.** The exporter then synthesizes `AGG(field_code)` from `MetricAtomic.aggregateType`. Derived and composite metrics are skipped in this release, and every skipped metric is listed by code in `custom_extensions.data`. The platform owns structured modifiers and time periods; collapsing them into opaque expression text would trade auditability for portability, and this proposal refuses that trade.
- The synthesized aggregate is restricted to a **portability whitelist**: `SUM`, `COUNT`, `MIN`, `MAX`, `AVG`. `COUNT DISTINCT` joins the whitelist once `AggregateType` models it; today the enum has no distinct variant. No window functions, no dialect functions, no date-bucket expressions such as `DATE_TRUNC` — an aggregate outside the whitelist is skipped and declared exactly like any other unexportable metric, regardless of the target warehouse.
- A related dataset ships at least the fields the document references: its primary key fields, its join key fields, and its `is_time` fields. Name resolution in the specification requires referenced fields to be declared; exporting a relationship to a dataset without them produces an invalid document.
- Expressions carry `dialects: [{dialect: ANSI_SQL}]`. The platform does not yet own a SQL dialect emitter, and pretending otherwise would produce unverifiable output.

`version` is pinned to `0.2.0.dev0` and the document records the product release in `custom_extensions`. When the specification stabilizes, the exporter version is a one-line change and no persisted state moves.

### 9. Two rules, no new scanner, no score change

Both rules implement the existing `GovernanceRule` contract and are registered in `GovernanceRuleRegistry`. They read models and relations; they never write Issues themselves, never close an Issue, and never read credentials.

`MODELING_GRAIN_UNDECLARED` enumerates models of three types; `MODEL_RELATION_KEY_UNSTANDARDIZED` enumerates relations and reports the owning model once per scan even when several keys are unstandardized, so one broken relation does not flood the issue list.

Neither rule feeds the health score in this release (see the rule contract section). Adding a modeling dimension to the score is a separate decision with its own baseline question.

### 10. What an implementation must not add

- No semantic query engine, SQL generator or join resolver. This proposal makes relations expressible, not executable.
- No automatic join inference from standard bindings, even though a suggestion endpoint is added. A wrong join is accepted more often than it is questioned.
- No Ossie import. The specification is a draft with breaking changes between minor versions.
- No hierarchy or drill-path tables. They are a separate proposal with their own UI.
- No change to `ModelFieldEntity.standardId` semantics, metric expression storage, or Task execution contracts.
- No health-score wiring for the new rules.

## Compatibility, Deprecation, and Migration Plan

- `V14__v1_16_0.sql` adds three tables and two nullable-or-defaulted columns. Existing rows remain valid: `is_time` defaults to `0`, `grain_note` is null.
- Release version moves from `1.15.0` to `1.16.0` with a release notes page, as required for any migration-bearing change.
- Existing models become "grain undeclared" findings after the first governance scan. This is intentional adoption pressure, not a defect; the finding is `MEDIUM` and resolvable without editing the model if the team accepts the default.
- Editing a model's fields no longer risks semantic damage elsewhere: relations reference codes, so field replacement is transparent to them. A removed field code surfaces as an explicit broken state instead of a dangling foreign key.
- No API is deprecated. The relation, synonym and export endpoints are additive.
- Models with relations cannot be deleted until the relations are deleted. This is a behavior change for deletion and must be called out in the release notes.
- DIM model detail pages lose the "New relation" action. No existing behavior depends on it.
- The Ossie projection has no compatibility promise beyond the pinned specification version. A specification upgrade may change the emitted document.

## Test Plan

- Repository tests: relation creation rejects a blank name, a self-relation, a cross-Workspace target model, a DIM `from` side, a non-DIM `to` side, duplicate key ordinals, empty key lists and an exact duplicate key-pair set against the same model pair.
- Key-resolution tests: after replacing a model's fields through the normal update path, relations resolve by code; removing a field code surfaces the explicit broken state; re-adding the code heals it.
- Validation tests: saving a relation with an unstandardized key fails with a stable error code; the proposal endpoint returns candidates ordered by standard match, marks unstandardized candidates `selectable: false`, and returns at most three candidates.
- Synonym tests: replace is atomic; duplicate terms within a target are rejected by the unique key; the same term on two different targets is accepted.
- Scoring isolation checks: both rules return `scored() == false`; the issue insert persists that flag and the DEV aggregation filters `scored = 1`, so advisory findings never enter the score.
- Export tests: golden-file comparison for a fact model with two dimensions, including composite keys, a null publication and a metric with no dialect; the emitted `version` equals the pinned constant; skipped metrics appear in `custom_extensions`; related datasets declare every referenced field; an aggregate outside the portability whitelist is skipped and declared.
- Rule tests: both rules enumerate completely, emit one candidate per model, and produce no candidate when the relation keys are standardized or the grain is present; neither rule is referenced by the score calculator.
- Migration test: apply `V14` to a database at `V13` containing models, then assert `is_time = 0` and `grain_note IS NULL` for every pre-existing row.
- REST tests: every endpoint rejects a request that resolves to another Workspace; a DIM model rejects `POST .../relations`.

## Rejected Alternatives

- **Store key pairs as field row ids.** `ModelServiceImpl.update` deletes and re-inserts every field on each save, so row ids dangle after the first edit. Codes are the stable contract, and Ossie `0.2.0` reached the same conclusion for the same reason.
- **Store the key mapping as a JSON column on the relation.** The schema forbids native JSON columns and ordered pairs need per-row validation; a child table costs one join and removes a class of silent corruption.
- **Add a cardinality enum.** Ossie deliberately omits one, and every non-many-to-one case in practice is a bridge table that should be modelled explicitly.
- **Infer joins from shared standards.** Tempting because standards already exist, but two models can share `customer_id` and still join on different keys (`customer_id` versus `customer_sk`). A wrong suggestion is accepted more often than it is questioned.
- **Default `is_time` to true for date types, following Ossie.** It would silently reclassify existing audit timestamps and change consumer behavior without a reviewer decision.
- **Adopt Ossie as the storage format.** `0.2.0.dev0` is explicitly unstable, already broke `0.1.1` compatibility, and defines no persistence, permissions or versioning. Projection only.
- **Export every metric by collapsing modifiers into expression text.** The platform owns structured modifiers and time periods; flattening them makes two metrics with different calibers indistinguishable to any auditor. Skip-and-declare is the honest option.
- **Export dimension members now.** The lookup bindings exist, but a member surface needs capping, value-level synonyms and a sensitive-value review; it belongs with hierarchies in a future proposal, projected through `ai_context.examples`.
- **Add a publish hook guarding physical tables behind relations.** The publication module has no drop-table flow today, an out-of-platform drop cannot be blocked anyway, and FYOIP-16 explicitly refused a publish hook for governance. A stale publication is a governance finding, not a write barrier.
- **Score the new rules into the health score immediately.** The score has two hard-wired dimensions; bolting modeling in without a dimension design would change every Workspace's score silently. Issues now, scoring later with a baseline.
- **Add hierarchy tables now.** Drill paths need a member-level design, a separate editor and a value-domain source; bundling them would double the surface without a consumer.
- **Block saving a model without a grain.** Turns a data-quality nudge into a migration that fails for every existing model on upgrade.
