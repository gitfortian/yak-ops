# YOIP-10: Data Modeling — Summary and Application Models

Status: Accepted

Discussion: https://github.com/gitfortian/yak-ops/pull/19

Decision: [VOTE] Accepted — https://github.com/gitfortian/yak-ops/pull/19#issuecomment-6382279985

Implementation: Planned — 1.5.0 batch

Release: N/A until released

Related FLIPs: N/A

## Motivation

YOIP-9 shipped logical models for detail tables (`DWD`) and dimension tables (`DIM`) and deferred summary tables (`DWS`) and application tables (`ADS`) to P1, because those two need a measure concept — a value plus an aggregate function — and application-layer ownership that the P0 contract did not cover.

That deferral leaves three concrete defects in the shipped product:

| Observed state | Why it matters |
| --- | --- |
| Layer presets already contain `DWS` and `ADS`, and any enabled layer can be selected for a model, but `ModelType` accepts only `DWD` and `DIM` | A model built on the `DWS` layer must be declared as `DWD`, so a table named `dws_order_amount` is stored as a detail table and every later consumer reads the wrong type |
| Fields have no aggregate function | Pre-aggregated data cannot be described at all: the model can only express row-level detail, so the layer that actually serves BI and API traffic has no modelling surface |
| The application layer can only hold `DIM` tables | The modelling chain stops at the boundary where the warehouse starts to serve consumers |

The persisted contract already anticipated the widening: `yak_ops_modeling_model.type` is `VARCHAR(16)` without a value check (YOIP-9 §Proposed Changes 3), and the ownership columns already encode exactly one of data domain or data mart. What is genuinely missing is the measure attribute on fields, which needs one additive migration.

This proposal closes the gap for P1 without pulling in the metric module: measures are entered by hand or imported from a physical table, and there is still no "import from metric" entry point.

## Public Interfaces

### Product behavior

| Item | Contract |
| --- | --- |
| Model types | `DWS` (summary table) and `ADS` (application table) join `DWD` and `DIM`; the editor type selector lists all four kinds |
| Placement | `DWS` hangs below a business process in the public layer, exactly like `DWD`. `ADS` hangs below a data mart in the application layer, exactly like an application-layer `DIM` |
| Process node | Lists `DWD` and `DWS` tables; offers `新建明细表` and `新建汇总表` |
| Data mart node | Lists `DIM` and `ADS` tables; offers `新建维度表` and `新建应用表` |
| Fields | `DWS` / `ADS` field rows gain an aggregate selector with `SUM`, `COUNT`, `AVG`, `MAX`, `MIN` and an empty value; `DWD` / `DIM` rows never show the column |
| Search | The model-type filter gains `DWS` and `ADS`; the other five filters are unchanged |
| Tree shape | Unchanged. Tables are still grouped by `processId` / `datamartId`; the type only decides which node may hold them |

### REST

No endpoint is added, removed or renamed. `POST` and `PUT /api/v1/data-modeling/dimension-modeling/models` accept the widened type and the new field attribute:

| Field | Type | Rule |
| --- | --- | --- |
| `type` | enum | `DWD`, `DIM`, `DWS` or `ADS` |
| `fields[].aggregateType` | enum, nullable | `SUM`, `COUNT`, `AVG`, `MAX`, `MIN`, or absent |

The `type` query parameter of `GET /models` accepts the two new values, so the existing server-side search filter needs no new parameter. `GET /models` returns the widened `type` in its list projection, `GET /models/{id}` adds `fields[].aggregateType`, and the planning tree keeps returning its `id` / `name` / `code` summaries. An unknown persisted `type` is still surfaced as unreadable and cannot be edited, as in YOIP-9.

Error semantics reuse the existing codes: `INVALID_MODEL` for an ownership or type/attribute conflict, `INVALID_FIELD` for an aggregate incompatible with the logical type, `RESOURCE_NOT_EXISTS` for an unknown process, data mart or standard. No new error code is introduced.

### Persisted formats

`V3__v1_5_0.sql`, the single release migration for 1.5.0:

- Add `aggregate_type VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL` to `yak_ops_modeling_model_field`, with `CHECK (aggregate_type IS NULL OR aggregate_type IN ('SUM','COUNT','AVG','MAX','MIN'))`.
- Add `ck_modeling_model_dws` and `ck_modeling_model_ads`, mirroring the existing `ck_modeling_model_dwd`: a `DWS` row requires `process_id` and `domain_id`, and forbids `datamart_id` and `dimension_id`; an `ADS` row requires `datamart_id` and forbids `process_id` and `dimension_id`.
- Keep `model.type` as `VARCHAR(16)` without a value check, so a later kind still costs no `ALTER`.

Existing rows are untouched: the new column is nullable, and every `DWD` / `DIM` row satisfies the new check constraints trivially because each constraint starts with `type <> ...`.

### Frontend internal contract

- `ModelType` widens to four values; `AGGREGATE_TYPES` sits next to the existing `DATA_TYPES` constant so the page, the payload builder and the tests share one definition.
- The field draft gains `aggregateType: AggregateType | ""`; the empty string is serialized as an absent property, never as `null` on the wire.
- Batch entry leaves `aggregateType` empty: a `CREATE TABLE` statement carries no aggregate function, so measures are filled in by hand after parsing.
- Importing fields from a physical table keeps the same behaviour: the parsed columns arrive without an aggregate and the user assigns measures explicitly.

## Proposed Changes

### 1. Type dispatch

`ModelServiceImpl.applyModel` currently dispatches on `DWD` versus "everything else is `DIM`", which would silently treat a `DWS` row as a dimension. The dispatch becomes exhaustive over the four kinds:

- `DWD` and `DWS` share `applyDetailOwnership` — category, data domain and business process are resolved with the same parent checks.
- `DIM` keeps `applyDimensionOwnership` unchanged.
- `ADS` reuses the data-mart half of that method without `dimensionId`: the data mart must exist in the Workspace, and `domainId`, `processId`, `dimensionId` must all be absent.

An unknown or null type keeps failing with `INVALID_MODEL` before any row is written.

### 2. Measure validation

`aggregateType` is the only new field attribute. It is accepted for `DWS` and `ADS` only, and is validated against the logical type:

| Aggregate | Allowed logical types |
| --- | --- |
| `SUM`, `AVG` | `BIGINT`, `DECIMAL` |
| `COUNT`, `MAX`, `MIN` | `STRING`, `BIGINT`, `DECIMAL`, `DATE`, `TIMESTAMP` |

Additional rules:

- A `DWD` or `DIM` model rejects any field carrying `aggregateType`, rather than dropping it silently.
- A `DWS` model must carry at least one field with an aggregate, so a summary table cannot be saved as an unaggregated copy of its source.
- An `ADS` model may be saved with no aggregate at all: an application table that only projects dimensions is legitimate.
- The aggregate is validated after standard inheritance, because a bound standard overwrites the field's code, type and length before validation. Binding a `STRING` standard to a field that carries `SUM` therefore fails the same type check as a hand-entered `STRING`, with no separate conflict path.
- `isPrimaryKey`, `isNotNull`, `isPartition` and the per-model single-partition rule are unchanged.

### 3. Table naming and layers

Table names keep following the selected layer: the prefix is the layer `enCode` lowercased plus `_`. No layer/type binding is enforced — see Rejected Alternatives — so a user may still build a `DWS` model on a custom layer, and the type remains an explicit declaration rather than a derived one.

### 4. Frontend

- The editor type selector lists the four kinds and explains placement in one line per kind.
- The field table gains an aggregate column that is rendered only for `DWS` and `ADS`; its options follow table 2 above, so an incompatible choice is unreachable rather than rejected after submit.
- The process panel and the data mart panel gain the second create action described in the product behaviour table.
- The model list shows the type badge per row; the search filter is the existing enum-driven select and picks the two values up automatically once the shared constant widens.

### 5. Validation, error codes and concurrency

`revision` optimistic locking, Workspace scoping and delete semantics are unchanged. `DWS` and `ADS` models are leaves in the reference graph, so they add no new `409` protection case: nothing references a model.

## Compatibility, Deprecation, and Migration Plan

- The migration is additive: one nullable column plus three check constraints, no `ALTER` of existing columns and no data rewrite. Upgrade from 1.4.0 applies `V3__v1_5_0.sql` after `V2__v1_4_0.sql`; a fresh install applies `V1`, `V2`, `V3` in order.
- Existing `DWD` / `DIM` rows and their API payloads are unaffected: `aggregateType` is absent on read for those kinds, and a request that omits it behaves exactly as before.
- Nothing is deprecated. The type enum stays open-ended in the database, so a future kind costs another migration only when it needs new attributes.
- No downgrade path is provided, consistent with previous releases: restoring the pre-upgrade backup is the supported rollback.
- The frontend and backend of one version ship together as a single distribution; mixed-version deployments are not supported, and the server does not need to tolerate the new field from an older client.

## Test Plan

- `ModelServiceTest`: ownership matrix for the four kinds, including a `DWS` without `processId`, an `ADS` carrying `processId`, and a `DIM` regression to prove the dispatch split did not move existing behaviour; aggregate/type matrix for all five aggregates against all five logical types; `DWS` without a measure rejected; aggregate on `DWD` rejected; standard binding to `STRING` with `SUM` rejected.
- `ModelDatabaseIT`: `V3` applies on top of `V2`, the new check constraints reject an `ADS` row with `process_id`, `aggregate_type` round-trips, and deleting a model still deletes its fields.
- Frontend: Node test-runner cases for the field draft to payload mapping, including the empty aggregate serialized as absent, and for the aggregate/data-type compatibility table; `npm run contract:check`, `npm run typecheck`, `npm run lint` and `npm run architecture:check`.
- Manual: create a `DWS` under a business process and an `ADS` under a data mart, confirm both appear under the right tree node and under the type filter, and confirm a `DWD` editor shows no aggregate column. This manual pass is the acceptance evidence; the frontend still has no automated browser interaction test, and the Test Plan does not claim one.

## Rejected Alternatives

1. **Store the aggregate in an existing free-text column** (`length_precision` or `description`). It avoids a migration but turns a typed attribute into parsed text, breaks filtering and validation, and makes the measure unqueryable by the database.
2. **Put measures in a JSON column on the model.** The field table is already the unit of ordering, uniqueness and standard binding; a JSON bag would duplicate those rules and lose the per-field check constraints.
3. **Bind model type to the layer `enCode`** (`DWS` only on the `dws` layer). Layers are Workspace-configurable and custom layers carry arbitrary codes, so the binding would either forbid legitimate custom layers or require a new type attribute on layers — a larger contract change than this proposal.
4. **Require at least one measure on `ADS` as well.** Application tables that only project dimensions onto a data mart are common; forcing a measure would push users to invent dummy sums.
5. **Require a source model (table-level lineage) for `DWS` / `ADS`.** Aggregation sources are worth modelling, but they need a dependency graph and impact analysis that the product does not have; recording a single "source model" pointer would imply lineage the system cannot verify.
6. **Build the metric module first and offer "import from metric".** YOIP-9 already rejected importing the metric centre for one dropdown; the same reasoning holds here, and measures entered by hand cover the P1 need.
