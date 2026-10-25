# YOIP-14: Data Modeling — Metrics

Status: Discussion

Discussion: https://github.com/gitfortian/yak-ops/pull/31

Decision: Pending (link to the recorded decision after review)

Implementation: N/A until accepted

Release: N/A until released

Related FLIPs: N/A

## Motivation

YOIP-10 shipped summary (`DWS`) and application (`ADS`) models with hand-entered measures and recorded the boundary in its Motivation: measures were to be entered by hand or imported from a physical table, with the metric module deferred as a whole. Its Rejected Alternatives 6 declined to build the metric module first because the module did not exist. YOIP-13 then shipped measurement units with the explicit note that DataWorks feeds the same unit list into atomic metrics — a consumer that did not exist yet. With YOIP-7 planning, YOIP-8 standards, YOIP-9 dimension modeling, YOIP-10 summary tables, YOIP-11 publication, YOIP-12 reverse modeling and YOIP-13 complete standards all shipped, the metric module is now the only part of the 数据建模 main line without a proposal.

The product this module is measured against defines its indicator system as four building blocks plus one derived form, and none of them exists in this repository today:

| Kind | DataWorks capability | Current state |
| --- | --- | --- |
| Atomic metric (原子指标) | The measure of one business process with its statistical caliber and calculation logic, such as 支付金额 under 支付订单 | Absent |
| Modifier (修饰词) | A business-scope restriction of a statistic, such as limiting 支付金额 to 母婴类产品; created under a data domain | Absent |
| Time period (时间周期) | The time range a statistic covers, such as 最近7天 | Absent |
| Derived metric (派生指标) | An atomic metric combined with a time period and modifiers, such as 最近7天母婴支付金额 | Absent |
| Composite metric (复合指标) | A calculation over derived metrics, such as a week-over-week growth rate | Absent |

Each gap is felt by something that already shipped:

- The same quantity is redefined per table. `dws_trade_order` and `dws_trade_order_1h` both carry a hand-typed 支付金额 field with a hand-picked `SUM`, and nothing can answer whether the two calibers agree — or what 支付金额 means at all. The measure unit list of YOIP-13 annotates each copy separately and still has no system-level consumer.
- `ModelServiceImpl` validates a `DWS` model only against carrying at least one field with an aggregate. Whether that field repeats another table's caliber, drifts from it, or contradicts it is invisible.
- YOIP-10 left the "import from metric" entry point unbuilt with the metric module; the entry point and the module are one decision, and this proposal takes it.

This proposal adds the metric module — atomic metrics, modifiers, time periods, derived metrics and composite metrics, all Workspace-scoped — and wires the one consumer that makes it more than inert data: importing a derived metric into a `DWS` or `ADS` field, with the link recorded and delete-protected. It does not add metric computation or SQL generation, statistics-granularity modeling, metric approval or versioning, bulk import or export, or import of composite metrics into fields.

## Public Interfaces

### Product behavior

| Item | Contract |
| --- | --- |
| Metric kinds | Five kinds live under 数据建模 → 数据指标: atomic metrics, derived metrics, composite metrics, modifiers and time periods. A kind selector at the top switches between them; each kind keeps its own search text and page position, mirroring the data-standards page. |
| Atomic metric | Chinese name, English code, exactly one business process of the Workspace, one of the five logical types, one aggregate of the existing five (`SUM`, `COUNT`, `AVG`, `MAX`, `MIN`), an optional measure unit from YOIP-13's list, an owner and an optional description. The aggregate must match the logical type by the same matrix YOIP-10 applies to model fields: `SUM` and `AVG` accept `BIGINT` and `DECIMAL`; `COUNT`, `MAX` and `MIN` accept all five types. |
| Modifier | Chinese name, English abbreviation, an optional description, and exactly one data domain of the Workspace that anchors it. |
| Time period | Chinese name, English abbreviation, an optional description. Time periods are a flat Workspace list, like naming dictionaries. |
| Derived metric | Composed, not typed: exactly one atomic metric, exactly one time period, and zero or more modifiers in a submitted order. The Chinese name is generated as the period name followed by the modifier names followed by the atomic name, concatenated without separators (`最近7天` + `母婴` + `支付金额` → `最近7天母婴支付金额`). The English code is generated as the period abbreviation, the modifier abbreviations in order and the atomic code joined by `_` (`7d` + `muying` + `pay_amt` → `7d_muying_pay_amt`). Both generated values are read-only; the parts are the only editable input, and the dialog shows the composed values live. Like the other two metric kinds, a derived metric carries an owner and an optional description. |
| Derived uniqueness | The composed code must be unique inside the Workspace, so two derived metrics built from the same parts collide and are refused — the composition, not a free-text name, is what keeps the caliber registry free of duplicates. A composed code or name that exceeds its column limit is refused before save. |
| Composite metric | Chinese name, user-entered English code, an arithmetic expression over derived metrics of the Workspace, an owner and an optional description. Only `+`, `-`, `*`, `/`, parentheses, numeric literals, whitespace and derived-metric codes are accepted; every referenced code must resolve to an existing derived metric of the same Workspace, and at least one reference is required. The expression is design-time data: nothing evaluates it, and it produces no data type. |
| Import into summary and application tables | A `DWS` or `ADS` model field may be imported from a derived metric. Import seeds the field's English code, Chinese name, logical type, aggregate and measure unit from the metric and records the metric on the field. The link is advisory: after import the field stays editable exactly like any other field, and editing it does not touch the metric. A field bound to a data standard keeps that standard's authority (YOIP-13 §6); the metric link adds no authority. `DWD` and `DIM` fields reject a metric link. |
| References | Atomic metric → derived metrics built on it; modifier → derived metrics using it; time period → derived metrics using it; derived metric → composite metrics referencing it and model fields importing it. Each list carries `{ kind, id, name, path }` like YOIP-13 §7, and deleting a referenced record is refused with `REFERENCED`. Composite metrics expose no references endpoint in this proposal: nothing can reference them yet, so deleting one is unrestricted. |
| Uniqueness scope | Codes of all five kinds are unique inside a Workspace, not across the installation. |
| Empty and error states | Each kind offers a code-availability check inside the Workspace before submit, matching the data-standards dialogs. A composed derived code that collides, an expression that does not parse or references an unknown code, and an aggregate that does not match the logical type are all caught in the dialog; the server remains the authority and answers with the same codes. |

### REST

All endpoints live under the new namespace `/api/v1/data-modeling/metrics`. Paths below are relative to that namespace.

| Method and path | Purpose |
| --- | --- |
| `GET /atomic-metrics` | Page atomic metrics by `processId` and `keyword`. |
| `GET /atomic-metrics/{id}` | Read one atomic metric. |
| `POST /atomic-metrics` | Create an atomic metric under a business process. |
| `PUT /atomic-metrics/{id}` | Update under the observed revision. |
| `DELETE /atomic-metrics/{id}` | Delete; refused while derived metrics build on it. |
| `GET /atomic-metrics/codes/availability` | Code availability inside the Workspace. |
| `GET /atomic-metrics/{id}/references` | Reference list. |
| `GET /modifiers` | Page modifiers by `domainId` and `keyword`. |
| `GET /modifiers/{id}` | Read one modifier. |
| `POST /modifiers` | Create a modifier under a data domain. |
| `PUT /modifiers/{id}` | Update under the observed revision. |
| `DELETE /modifiers/{id}` | Delete; refused while derived metrics use it. |
| `GET /modifiers/codes/availability` | Abbreviation availability. |
| `GET /modifiers/{id}/references` | Reference list. |
| `GET /time-periods` | Page time periods by `keyword`. |
| `GET /time-periods/{id}` | Read one time period. |
| `POST /time-periods` | Create a time period. |
| `PUT /time-periods/{id}` | Update under the observed revision. |
| `DELETE /time-periods/{id}` | Delete; refused while derived metrics use it. |
| `GET /time-periods/codes/availability` | Abbreviation availability. |
| `GET /time-periods/{id}/references` | Reference list. |
| `GET /derived-metrics` | Page derived metrics by `processId` (the process of the underlying atomic metric) and `keyword`. |
| `GET /derived-metrics/{id}` | Read one derived metric with its parts. |
| `POST /derived-metrics` | Create from parts; code and name are composed by the server. |
| `PUT /derived-metrics/{id}` | Replace parts and description under the observed revision; the composition is regenerated and re-checked. |
| `DELETE /derived-metrics/{id}` | Delete; refused while composite metrics or model fields reference it. |
| `GET /derived-metrics/codes/availability` | Composed-code availability; answers whether the submitted parts would collide. |
| `GET /derived-metrics/{id}/references` | Reference list. |
| `GET /composite-metrics` | Page composite metrics by `keyword`. |
| `GET /composite-metrics/{id}` | Read one composite metric with its resolved references. |
| `POST /composite-metrics` | Create; the expression is parsed and its references resolved before save. |
| `PUT /composite-metrics/{id}` | Replace expression and description under the observed revision; references are re-resolved. |
| `DELETE /composite-metrics/{id}` | Delete; unrestricted in this proposal. |
| `GET /composite-metrics/codes/availability` | Code availability inside the Workspace. |

Existing model endpoints keep their paths. `POST` and `PUT /api/v1/data-modeling/dimension-modeling/models` gain one optional field attribute, and the reads mirror it:

| Field | Type | Rule |
| --- | --- | --- |
| `fields[].metricId` | id, nullable | A derived metric of the same Workspace; accepted for `DWS` and `ADS` fields only, rejected for `DWD` and `DIM` with the existing `INVALID_FIELD`. An unknown id fails with the existing `RESOURCE_NOT_EXISTS`. |
| `fields[].metricEnCode`, `fields[].metricCnName` | read-only | Returned by `GET /models/{id}` beside the id, mirroring `standardId` / `standardEnCode`. |

New error codes in a new `MetricErrorCode` enum, which owns the 193xx range that no existing enum uses:

| Code | Use |
| --- | --- |
| `MetricErrorCode.INVALID_CODE` (19301) | An English code or abbreviation is malformed. |
| `MetricErrorCode.DUPLICATE_CODE` (19302) | A code or abbreviation already exists in the Workspace; for a derived metric this is a parts collision. |
| `MetricErrorCode.REVISION_CONFLICT` (19303) | Observed revision mismatch. |
| `MetricErrorCode.INVALID_OWNER` (19304) | The owner is not a valid member of the Workspace. |
| `MetricErrorCode.INVALID_ATOMIC_METRIC` (19305) | The submitted aggregate does not match the atomic metric's logical type. |
| `MetricErrorCode.INVALID_DERIVED_METRIC` (19306) | The atomic metric, time period or a modifier is missing or outside the Workspace, the same modifier is submitted twice, or the composed code or name exceeds its column limit. |
| `MetricErrorCode.INVALID_METRIC_EXPRESSION` (19307) | The composite expression does not parse, uses an illegal token, or references a code that is not an existing derived metric of the Workspace. |
| `MetricErrorCode.REFERENCED` (19308) | Deletion refused while derived metrics or model fields depend on the record. |

Metrics have no containers, so `HAS_DEPENDENCIES` has no case and is not introduced. References into other modules' entities — an unknown business process, data domain or measure unit — fail with the existing `RESOURCE_NOT_EXISTS` (`CommonErrorCode` 10010), as YOIP-10 does for an unknown process, data mart or standard; references inside the metric module use the metric codes above.

### Persisted formats

`V6__v1_8_0.sql`, the single release migration for 1.8.0, is additive; the only existing table it alters is `yak_ops_modeling_model_field`. The five record tables share these column shapes: `workspace_id`; `en_code VARCHAR(64)` ASCII with the shared `^[a-z][a-z0-9_]*$` check; `cn_name VARCHAR(128)`; nullable `description VARCHAR(500)`; `revision`; the shared audit columns; and the unique key `(workspace_id, en_code)`. The two link tables carry `workspace_id` and their reference columns only. Per table:

- `yak_ops_modeling_metric_time_period`: the shared shape and nothing else.
- `yak_ops_modeling_metric_modifier`: the shared shape plus `domain_id`, a foreign key to `yak_ops_modeling_domain` with `ON DELETE RESTRICT`.
- `yak_ops_modeling_metric_atomic`: the shared shape plus `process_id` (foreign key to `yak_ops_modeling_process` with `ON DELETE RESTRICT`), `data_type` (`VARCHAR(16)` ASCII, the five logical types), `aggregate_type` (`VARCHAR(16)` ASCII constrained to the five aggregates), nullable `measure_unit_id` (foreign key to `yak_ops_standard_measure_unit` with `ON DELETE RESTRICT`) and `owner_id`.
- `yak_ops_modeling_metric_derived`: `atomic_metric_id` and `period_id`, both foreign keys with `ON DELETE RESTRICT`; the composed `en_code` and `cn_name` are persisted columns, not read-time computations; `owner_id`.
- `yak_ops_modeling_metric_derived_modifier`: `derived_id` with `ON DELETE CASCADE`, `modifier_id` with `ON DELETE RESTRICT`, `ordinal`. Unique on `(derived_id, ordinal)` and on `(derived_id, modifier_id)`.
- `yak_ops_modeling_metric_composite`: the shared shape plus `expression VARCHAR(2048)` and `owner_id`.
- `yak_ops_modeling_metric_composite_ref`: `composite_id` with `ON DELETE CASCADE`, `derived_metric_id` with `ON DELETE RESTRICT`. Unique on `(composite_id, derived_metric_id)`.
- `yak_ops_modeling_model_field` gains nullable `metric_id`, a foreign key to `yak_ops_modeling_metric_derived` with `ON DELETE RESTRICT`.

Every table carries `workspace_id` and the shared audit columns. No existing column is dropped or rewritten, and nothing is backfilled: no existing row gains a metric link.

### Frontend internal contract

- `service/data-modeling/metrics` gains clients for the five kinds; the availability check and revision-conflict handling stay shared with the other modeling pages.
- The metric page owns one kind selector and five list components, mirroring the data-standards page; switching kinds does not remount the page or lose the other kinds' query state.
- The derived-metric dialog composes code and name live from the selected parts and offers no free-text name or code input; the composite dialog validates the expression with a client-side parser that mirrors the server grammar and lists the references it resolved under the input.
- The field editor of `DWS` and `ADS` models gains a 从指标导入 action beside the existing 从数据标准导入. The picker lists derived metrics — pre-filtered to the model's own business process for `DWS`, searchable across the Workspace for `ADS` — and reports what each import seeded. A field carrying a metric shows it in the row; removing the link is a normal edit.
- The expression grammar and the derived-metric composition are pure modules with node test-runner cases, following the `field-mapping` / `ddl-parser` convention.

## Proposed Changes

### 1. Five kinds, one page

The metric page reuses the kind-selector pattern the data-standards page shipped, because the two pages answer the same question — a Workspace registry of governed definitions — with the same navigation. The five kinds fall into two shapes. Atomic, derived and composite metrics are records with an owner, a code and a caliber. Modifiers and time periods are dictionaries: small Workspace lists whose records exist to be composed into derived metrics, like naming entries. None of the five uses the standard category tree: metrics are not fields, and DataWorks organizes them by kind and filter, not by user-created directories.

Modifiers anchor to a data domain because DataWorks creates them there, and the anchor is organizational — it filters the picker when composing a derived metric and answers "whose business scope does this restriction belong to" — not a permission. A modifier may be used by a derived metric whose atomic metric lives under a different domain; restricting that would make cross-domain statistics inexpressible, and DataWorks does not restrict it. Modifiers and time periods carry no owner: they are shared vocabulary, like naming entries, while the three metric kinds carry an accountable owner like models do.

### 2. Atomic metrics carry the caliber

An atomic metric is the single place where a quantity's meaning is defined: which business process produces it, what it is called, what type it returns, how it aggregates and in which unit it is measured. The process anchor gives the metric its data-domain home for free, because a process already hangs below a domain (YOIP-7), so a metric never carries its own domain reference that could disagree with its process.

The aggregate is part of the definition — 支付金额 is a `SUM` by definition, not per table — so it lives on the atomic metric, and the type compatibility is the same matrix YOIP-10 applies to model fields. Reusing the matrix means one rule instead of two: a metric that could not seed any legal field cannot be saved. The unit reuses YOIP-13's measure-unit list, which that proposal shipped with exactly this consumer in mind; the unit stays an annotation, never a type constraint, by the same reasoning YOIP-13 records.

### 3. Modifiers and time periods are governed dictionaries

DataWorks manages time periods as maintained data, not as a fixed enum: real warehouses need 财务月, 自然周 and company-specific windows that no closed list anticipates. YOIP-13 already hit this with measurement units, where a closed DataWorks list needed two extra categories; a closed period list would hit it again on the first fiscal calendar, and a later release would need the table anyway. The dictionary is the lighter contract built once.

Derived-metric composition needs both dictionaries to be governed: the composed name and code are built from the parts' names and abbreviations, so a part edit changes every composed metric that uses it. That is the intended behavior — it is how the registry stays consistent — and it is why parts are entities with unique abbreviations rather than free text.

### 4. Derived metrics are composed, and the composition is persisted

A derived metric has no name of its own. 最近7天母婴支付金额 is exactly period 最近7天, modifier 母婴, atomic metric 支付金额 — DataWorks generates it, and so does this proposal: the server composes both the Chinese name and the English code from the submitted parts, stores them, and refuses a parts combination whose composition collides with an existing code. Free-text names would defeat the only thing a derived metric is for: two entries could claim the same caliber with different meanings, and nothing could tell them apart.

The composed values are persisted columns, not read-time derivations. Uniqueness must be enforced on something stored, the list page must filter and sort on the code without re-deriving it for every row, and a model field that cites the metric should cite the values that were checked and saved — not a value re-derived under whatever the parts say at read time. The parts remain the single source of truth for meaning: an edit re-composes and re-checks uniqueness, and the stored values move with it.

Editing the parts of a derived metric that model fields have imported does not rewrite those fields. Like YOIP-13 §6, the divergence becomes visible the next time the metric is imported; rewriting stored fields would silently change summary-table contracts through YOIP-11's publication.

### 5. Composite metrics are expressions over derived metrics, and nothing more

The grammar is arithmetic only — `+`, `-`, `*`, `/`, parentheses, numeric literals and derived-metric codes — because DataWorks documents the composite as "运算规则" over derived metrics, and every documented example (growth rate, difference, ratio) is arithmetic. The parser resolves each identifier to an existing derived metric of the same Workspace and stores the resolved references, which the delete check and the reference panel read.

References point at derived metrics only, so the reference graph is acyclic by construction: a derived metric can never reference a composite, so no cycle check exists to get wrong. The expression is never evaluated and produces no data type — that is not a deferred feature but the boundary of this proposal, and it is why composite metrics cannot be imported into fields (Proposed Changes 6) and expose no references endpoint yet (nothing can reference them).

### 6. Import seeds a field and records the link

The import is the consumer that keeps the module from being inert data. Importing a derived metric into a `DWS` or `ADS` model seeds the field's code, Chinese name, logical type, aggregate and measure unit — everything the metric already knows — and stores `metric_id` on the field. The seeds are editable afterwards. The metric is a caliber definition; the field is a table column, and tables legitimately adjust columns (a per-table `MAX` where the metric defines `SUM`, a renamed display name) without redefining the caliber. Making the link authoritative — read-only controls until unbound, as field standards do — would force users to choose between a wrong column and a broken link, so the link is advisory and delete-protected instead.

A derived metric that model fields import cannot be deleted (`REFERENCED`), so the registry cannot lose a caliber that summary tables still cite. Editing the metric leaves the fields untouched, per item 4.

`DWD` and `DIM` fields reject `metricId` with `INVALID_FIELD`, the same code YOIP-10 uses for an aggregate on a detail field: metrics are measures, and the two kinds carry no aggregate column. An unknown metric id fails with `RESOURCE_NOT_EXISTS`, like an unknown standard today. Import of atomic metrics directly is not offered: an atomic metric without a time period is a caliber definition, not a statistic, and importing one into a table would imply a period the system cannot name — the composite metric case is Proposed Changes 5's boundary, and both are recorded in Rejected Alternatives.

### 7. Reference queries

Every referenceable kind answers "what depends on me" from the tables that point at it, in the `{ kind, id, name, path }` shape YOIP-13 §7 standardized: an atomic metric's list shows each derived metric built on it under that metric's composed code, and a derived metric's list shows each importing model field under its table-and-field path, `dws_trade_order.pay_amt`. The query runs on demand from the delete dialog and the reference panel, not inside list responses, for the same page-size reason YOIP-13 records.

## Compatibility, Deprecation, and Migration Plan

- `V6__v1_8_0.sql` applies after `V5__v1_7_0.sql`. A fresh install applies the migrations in order.
- Seven tables are created and one is altered: `yak_ops_modeling_model_field` gains nullable `metric_id`. Every new column is nullable or inside a new table, so the migration needs no backfill and no existing row changes.
- No existing payload loses or changes a field. Model-field payloads gain one optional write field and two read-only fields, so every previously valid request stays valid and every previous read stays a prefix of the new one.
- No existing behavior is tightened. Unlike YOIP-13, nothing that used to be accepted starts failing: the metric module is opt-in end to end, and a Workspace that never opens 数据指标 sees no difference except two read-only fields on model-field reads.
- Deleting a business process, data domain or measure unit that a metric references is refused by the new foreign keys, and the module answers with the same `REFERENCED` code the standards use, with the reference list behind it.
- The frontend and backend of one version ship together as a single distribution; mixed-version deployments are not supported, and the server does not need to tolerate the new field from an older client.
- No downgrade path is provided, consistent with previous releases; restoring the pre-upgrade backup is the supported rollback.
- Nothing is deprecated in this proposal.

## Test Plan

- Unit tests cover derived-metric composition (period only, modifiers in submitted order, the collision of identical parts as `DUPLICATE_CODE`, and the refusal when a composed code exceeds 64 or a composed name exceeds 128 characters), the composite expression parser (valid arithmetic, each illegal token class, unknown and cross-Workspace identifiers, empty input), the atomic aggregate/type matrix as reused from YOIP-10, code availability scoped to a Workspace, and reference aggregation for all four referenceable kinds including each `path`.
- Service tests cover `metricId` acceptance on `DWS` and `ADS` fields, rejection on `DWD` and `DIM` with `INVALID_FIELD`, `RESOURCE_NOT_EXISTS` for an unknown id, and the advisory nature of the link — editing a seeded field succeeds and leaves the metric untouched.
- Database integration tests cover `V6` application on top of `V5`; every foreign key's `RESTRICT` behavior (atomic under derived, period and modifier under derived links, derived under composite references and model fields, modifier under its domain); `CASCADE` on derived-modifier links and composite references; Workspace isolation with the same codes in two Workspaces; `metric_id` round-trip on a model field; and deletion of a referenced metric refused.
- Frontend verification covers `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run architecture:check`, `npm run contract:check` and `npm run build`, plus node test-runner cases for the composition and expression modules.
- Manual acceptance must create one time period, one modifier and one atomic metric; compose a derived metric and confirm the name and code are generated and read-only; compose the same parts again and confirm the collision; create a composite metric over two derived metrics with a bad expression first and confirm the refusal; import the derived metric into a `DWS` model and confirm the five seeded attributes and the recorded link; edit the field and confirm the metric does not change; confirm a `DWD` editor offers no import; open the modifier's delete dialog while it is used and confirm the list and the refusal; and confirm two Workspaces can define the same code. Browser interaction is not covered by an automated per-feature Playwright script in this proposal.

## Rejected Alternatives

1. **Store all five kinds as rows of one generic metric table with a `kind` column.** The kinds do not share attributes: an atomic metric owns a process, type and aggregate; a derived metric owns three part references and a composition; a composite owns an expression. One table would need either a nullable column for every kind's fields or a JSON payload that cannot be indexed or reference-checked, repeating the reasoning of YOIP-13's first rejected alternative.
2. **Make time periods a fixed closed enum.** 财务月, 自然周 and company-specific windows are ordinary requirements, and no closed list anticipates them; YOIP-13 needed two extra unit categories on top of DataWorks' three for exactly this reason. A later release would need the table anyway, so the dictionary is built once, first.
3. **Let a derived metric carry free-text modifiers.** Unlisted restrictions would make the composed caliber registry unsearchable and the composition impossible; the modifier dictionary is what keeps 最近7天母婴支付金额 a computable identity instead of a label.
4. **Make the metric link on a model field authoritative, as field standards are.** A table column legitimately differs from the caliber it cites — a per-table `MAX`, a display rename — and freezing five controls would force users to break the link to adjust the column, losing the delete protection the link exists for. The link is advisory and delete-protected; the field standard stays the authoritative contract when both are bound.
5. **Let composite metrics reference other composite metrics.** Recursion would buy nothing the first release needs and would turn acyclicity from a construction guarantee into a runtime check with an error path, an evaluation order and a depth limit to define. Derived-only references keep the graph a two-level DAG by construction.
6. **Generate SQL or evaluate composite expressions now.** There is no query engine, no physical expression mapping and no engine dialect decision in this repository; an evaluator over design-time data would either be dead code or imply an execution contract this proposal cannot state. DataWorks' own composite definition stops at the expression.
7. **Model statistics granularity (统计粒度) on derived metrics.** DataWorks' own terms page defines the derived metric as atomic metric, modifiers and time period — no granularity — and granularity needs dimension binding and rollup semantics this repository does not have. It stays out of the derived-metric contract, and with it the granularity projections DataWorks mentions for application tables.
8. **Widen the aggregate vocabulary (`COUNT DISTINCT`, median) in this proposal.** The five aggregates are a shipped YOIP-10 contract with check constraints on `yak_ops_modeling_model_field`; widening them is a separate contract change to that surface, and the metric module can ship against the existing five without prejudging it.
9. **Offer import of atomic metrics directly into fields.** DataWorks' application-table page does mention organizing statistics of atomic metrics, but every such statistic still covers a period, and our contract records a period only on the derived metric. Importing an atomic metric directly would seed a field whose window the system cannot name; requiring the derived-metric step keeps every imported field carrying an explicit period.
10. **Offer import of composite metrics into fields.** Without an evaluator a composite has no decidable type or aggregate, so an imported field would have nothing correct to seed. Import arrives with the consumer that can name the type.
11. **Hang metrics under the business-process node of the planning tree.** The process node lists the tables that realize it (YOIP-10); mixing metric lists into tree nodes would change that contract for every existing consumer. The metric page filters by process instead, which answers the same question without touching the tree.
12. **Compute the derived code and name at read time instead of persisting them.** Uniqueness must be enforced on stored values, list pages must filter on them without per-row derivation, and the model-field foreign key needs a stable target. The parts stay the source of truth; the stored composition is what references see.
13. **Add bulk `.xlsx` import and export in this proposal.** Import is a separate write channel with its own template, partial-failure semantics and overwrite policy; it multiplies the surface of five kinds at once and is better added once the contracts are stable, as YOIP-13 records for the same decision.
