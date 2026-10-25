# YOIP-13: Data Modeling — Complete Data Standards

Status: Discussion

Discussion: https://github.com/gitfortian/yak-ops/pull/26

Decision: Pending

Implementation: N/A until accepted

Release: N/A until released

Related FLIPs: N/A

## Motivation

YOIP-8 shipped data standards as exactly one kind of standard: a field standard with a Chinese name, an English code, a logical type, optional length or precision, a not-null flag, a security level, an owner and a business definition, organized in a multi-level directory. Its Motivation states that boundary explicitly, and `docs/releases/v1.4.0.md` repeats it for users: "数据标准只覆盖字段标准。标准代码、度量单位与命名词典不在本版范围。"

The product this module is measured against defines four kinds, and the three that are missing are not cosmetic:

| Kind | DataWorks capability | Current state |
| --- | --- | --- |
| Field standard | Name, code, type, length, precision, not-null, default value, parent standard, referenced lookup table | Name, code, type, length or precision, not-null, security level, owner and business definition; no default value, no parent and no lookup reference |
| Lookup table (标准代码) | An enumerated value range that fields reference; drives value-range quality rules | Absent |
| Measurement unit (度量单位) | Currency, quantifier and time units, extended here with ratio and rank; referenced by fields and by atomic metrics | Absent |
| Naming dictionary (命名词典) | Root words and morphemes that a layer's table-name checker validates against | Absent; YOIP-9 records the same exclusion — "可配置正则与命名词典仍不做" |

Each gap blocks a capability that is already planned or already shipped:

- Without a lookup table a field standard cannot constrain a value range. A `gender` field accepts any string, and when the quality module arrives there is no enumerated-value rule to derive from the standard.
- Without measurement units a model field cannot record that a quantity is measured in `件` or `元`. The metric module does not exist yet, but DataWorks feeds the same unit list into atomic metrics, so building metrics first would force a second unit vocabulary later.
- Without a naming dictionary, table-name validation stays one fixed pattern, `ModelServiceImpl.SUFFIX`, which only checks that a suffix is lowercase letters, digits and underscores. The prefix is composed by `fullName`; the governed vocabulary behind either half is not checked.
- `StandardReferencesVO` currently answers `NOT_ENABLED` for every reference query, so no standard kind can show what depends on it. YOIP-8 chose that placeholder because logical models did not exist yet; they exist now.

Two defects inside the shipped field standard also need to close before three more kinds are added on top of it:

- There is no parent standard. DataWorks lets `买家ID` and `卖家ID` both specialize `会员ID`, so the standard list is a tree instead of a flat page.
- Binding a standard only seeds a field. `yak_ops_modeling_model_field.is_not_null` is commented "never inherited forcibly", while DataWorks inherits name, display name, type and nullability in full. Two models that bind the same standard can therefore disagree on nullability.

One further defect cannot be copied onto the new tables: `yak_ops_standard_field` declares `uk_standard_field_code (en_code)` without `workspace_id`, so two Workspaces cannot both define `member_id`. Every other modeling table is Workspace-scoped, and the three new kinds must be, so the existing key is corrected rather than propagated.

This proposal adds the three missing kinds, closes the two field-standard gaps, makes binding authoritative instead of advisory, replaces the reference stub with a real cross-standard query, and connects the naming dictionary to layer-level table-name validation. It does not add bulk import or export, standard versioning or approval, lookup-table materialization, or automatic quality-rule generation.

## Public Interfaces

### Product behavior

| Item | Contract |
| --- | --- |
| Standard kinds | Four kinds live under 数据建模 → 数据标准: field standards, lookup tables, measurement units and naming dictionaries. A kind selector at the top switches between them; each kind keeps its own selected container, search text and page position. |
| Containers | Field standards and lookup tables share one Workspace directory tree. A node is either a `DIRECTORY` or a `STANDARD_SET`. A directory holds directories, standard sets and records; a standard set holds records of one kind only, declares that kind, and cannot be a parent. Measurement units use five fixed categories — `CURRENCY`, `QUANTIFIER`, `RATIO`, `RANK`, `TIME` — instead of a tree. Naming dictionaries are a flat Workspace list. |
| Field standard | Adds `defaultValue` (at most 2048 characters), `parentStandardId`, `lookupId` and `measureUnitId` to the existing attributes. `defaultValue` is descriptive and never becomes a physical `DEFAULT` clause. `lengthPrecision` keeps its current wire format. |
| Parent standard | A field standard may name one parent in the same Workspace. The chain is acyclic and at most eight levels deep. The link records specialization for navigation and grouping; it does **not** merge attributes, so a child keeps its own code, type and nullability. |
| Lookup table | Code, Chinese name, English name, optional description, and an ordered value list. A value carries `valueCode`, `cnName`, `enName` and optional `meaning`. Value codes are unique inside one table. A field that binds a lookup table takes its code as the field name and its Chinese name as the display name. |
| Measurement unit | Category, abbreviation, English name, Chinese name, optional description. Abbreviations are unique inside a Workspace. Binding a unit is an annotation: it never renames a field and never changes its type. |
| Naming dictionary | Abbreviation, English name, Chinese name, optional description. Abbreviations are unique inside a Workspace and are the token a table-name rule matches. |
| Binding a model field | A field may bind a field standard, a lookup table, a measurement unit, or any combination. A bound field standard is authoritative for English code, Chinese name, type, length or precision and not-null; those five controls are read-only until the standard is unbound. A lookup table supplies code and Chinese name only when no field standard is bound. A unit never overrides anything. |
| References | Every kind exposes a reference list: field standard → model fields; lookup table → field standards and model fields; measurement unit → field standards and model fields; naming dictionary → models named under a layer rule that uses the entry. Deleting a referenced record is refused with the existing `REFERENCED`, which is already what the standard service raises for an asset that depends on a record; `HAS_DEPENDENCIES` stays reserved for a container that still holds children. The delete dialog loads the same list before it confirms, so the operator sees which tables and fields depend on the record and can open the owning model instead of guessing. |
| Table-name checking | A layer may enable a naming rule. When enabled, the suffix of a model that carries a business process must begin with `<root word abbreviation>_<process code>`, where the root word is an existing naming-dictionary entry of the Workspace and the process is the model's own business process. An optional `_<qualifier>` may follow, so one process can carry several tables; the full name stays the layer prefix plus this suffix. The dialog composes the required head from two governed selections instead of accepting free text. Models without a business process — `DIM` and `ADS` — are exempt. Disabling the rule leaves existing names untouched. |
| Uniqueness scope | Codes of all four kinds are unique inside a Workspace, not across the installation. |
| Empty and error states | A standard set with no records shows an empty state rather than a table; creating a record still requires a selected container. A name rejected by an enabled rule is caught in the dialog, which composes the required head from the chosen root word and the model's own process; the server rejects a bypass as a fixed `NAMING_RULE_VIOLATION`. A layer whose rule is enabled while non-conforming models remain lists those model names in the layer panel until they are renamed. |

### REST

All endpoints extend the existing namespace `/api/v1/data-modeling/data-standards`, except the two layer rule endpoints, which are absolute. Paths below are relative to that namespace unless written in full.

| Method and path | Purpose |
| --- | --- |
| `GET /categories/tree` | Directory tree; existing response gains `nodeType` and `standardKind` on every node. |
| `POST /categories` | Create a node; request gains `nodeType`, defaulting to `DIRECTORY`, and `standardKind`, required for a standard set. |
| `GET /lookups` | Page lookup tables by `categoryId` and `keyword`. |
| `GET /lookups/{id}` | Read one lookup table with its ordered values. |
| `POST /categories/{categoryId}/lookups` | Create a lookup table under a directory or standard set. |
| `PUT /lookups/{id}` | Replace attributes and the whole value list under the observed revision. |
| `DELETE /lookups/{id}` | Delete a lookup table; refused while referenced. |
| `GET /lookups/codes/availability` | Code availability inside the Workspace. |
| `GET /lookups/{id}/references` | Reference list. |
| `GET /measure-units` | Page units by `category` and `keyword`. |
| `POST /measure-units` | Create a unit. |
| `PUT /measure-units/{id}` | Update a unit under the observed revision. |
| `DELETE /measure-units/{id}` | Delete a unit; refused while referenced. |
| `GET /measure-units/codes/availability` | Abbreviation availability. |
| `GET /measure-units/{id}/references` | Reference list. |
| `GET /naming-entries` | Page dictionary entries by `keyword`. |
| `POST /naming-entries` | Create an entry. |
| `PUT /naming-entries/{id}` | Update an entry under the observed revision. |
| `DELETE /naming-entries/{id}` | Delete an entry; refused while referenced. |
| `GET /naming-entries/codes/availability` | Abbreviation availability. |
| `GET /naming-entries/{id}/references` | Reference list. |
| `GET /api/v1/data-modeling/warehouse-planning/layers/{id}/naming-rule` | Read the toggle and the models of that layer that do not conform, with their names. |
| `PUT /api/v1/data-modeling/warehouse-planning/layers/{id}/naming-rule` | Enable or disable the rule under the observed revision. Enabling while non-conforming models exist requires `acknowledgeViolations`. |

Existing field-standard endpoints keep their paths. Their payloads gain the four new optional attributes, and `GET /fields/{id}/references` starts returning real data instead of `NOT_ENABLED`.

New error codes, each in the enum that already owns that surface — `StandardErrorCode` for data standards, `LayerErrorCode` for the layer and `DimensionModelingErrorCode` for model names:

| Code | Use |
| --- | --- |
| `StandardErrorCode.INVALID_PARENT_STANDARD` (19109) | The chosen parent is the record itself, is outside the Workspace, or would create a cycle or exceed eight levels. |
| `StandardErrorCode.INVALID_LOOKUP_VALUE` (19110) | A value code is empty or duplicated, or the submitted list is missing; the whole value list is rejected. |
| `LayerErrorCode.INVALID_NAMING_RULE` (19208) | Enabling a rule while models of that layer do not conform, and the request does not acknowledge them. |
| `DimensionModelingErrorCode.NAMING_RULE_VIOLATION` (19513) | A model name does not match the enabled layer rule. The fixed message is a fallback: the dialog composes the expected suffix before submitting, because the shared error type carries no per-request detail. |

Duplicate codes continue to use `DUPLICATE_CODE` and revision conflicts `REVISION_CONFLICT`, both already in `StandardErrorCode`. Refusal to delete a record that something references uses the existing `REFERENCED` (19108); refusal to delete a container that still holds children keeps `HAS_DEPENDENCIES` (19103). A rejected name under an enabled rule uses `NAMING_RULE_VIOLATION`; `INVALID_TABLE_NAME` keeps its current meaning of a malformed prefix or an over-long name.

### Persisted formats

`V5__v1_7_0.sql`, the single release migration for 1.7.0, is additive except for one index replacement:

- `yak_ops_standard_category` gains `node_type VARCHAR(16) NOT NULL DEFAULT 'DIRECTORY' CHECK (node_type IN ('DIRECTORY','STANDARD_SET'))` and nullable `standard_kind VARCHAR(16) CHECK (standard_kind IN ('FIELD','LOOKUP'))`, which the service requires on a standard set and forbids on a directory.
- `yak_ops_standard_lookup`: `id`, `workspace_id`, `category_id`, `en_code`, `cn_name`, `en_name`, nullable `description`, `revision`, audit columns. Unique on `(workspace_id, en_code)` and on `(workspace_id, id)`, the latter being the foreign-key target.
- `yak_ops_standard_lookup_value`: `id`, `workspace_id`, `lookup_id` with `ON DELETE CASCADE`, `ordinal`, `value_code`, `cn_name`, `en_name`, nullable `meaning`. Unique on `(lookup_id, value_code)` and `(lookup_id, ordinal)`. The column is `value_code` rather than `value`, which is reserved on MySQL.
- `yak_ops_standard_measure_unit`: `id`, `workspace_id`, `category`, `en_code`, `en_name`, `cn_name`, nullable `description`, `revision`, audit columns. Unique on `(workspace_id, en_code)` and on `(workspace_id, id)`; `category` is constrained to the five values.
- `yak_ops_standard_naming_entry`: `id`, `workspace_id`, `en_code`, `en_name`, `cn_name`, nullable `description`, `revision`, audit columns. Unique on `(workspace_id, en_code)` and on `(workspace_id, id)`. A naming entry has no category, so it needs no tree.
- `yak_ops_standard_field` gains `default_value VARCHAR(2048)`, `parent_standard_id`, `lookup_id` and `measure_unit_id`, all nullable and all foreign keys with `ON DELETE RESTRICT` against the Workspace-scoped key of their target. Its `en_code` comment stops claiming a global scope.
- `yak_ops_modeling_model_field` gains nullable `lookup_id` and `measure_unit_id` with the same referential rules, so a field can bind a lookup table without a field standard.
- `yak_ops_modeling_model` gains nullable `naming_root_word_id`, a foreign key to the naming entry with `ON DELETE RESTRICT`, written only when a model is named under an enabled layer rule.
- `yak_ops_modeling_layer` gains `naming_rule_enabled TINYINT NOT NULL DEFAULT 0` and nothing else: which business process a table belongs to is already a model column, and which root word it uses is recorded on the model, so a layer would only be duplicating both.
- `uk_standard_field_code (en_code)` is replaced by `uk_standard_field_code (workspace_id, en_code)`.

No existing column is dropped or rewritten. Existing rows keep their values, and every new column is nullable or has a default. The naming-root backfill described above only fills a new column on rows whose suffix already starts with a known head, and it runs only when an operator enables a rule.

### Frontend internal contract

- `service/data-modeling/data-standards` gains lookup, unit, naming-entry and reference clients; the directory tree, availability check and revision-conflict handling stay shared with field standards.
- The data-standard page owns one kind selector and four list components. Switching kinds does not remount the page or lose the other kinds' query state.
- The field-standard dialog splits `lengthPrecision` into separate length and precision inputs and reassembles the wire value; it does not change the payload shape. `DECIMAL` shows `18,2` as placeholder text rather than as a value, so nobody inherits a scale they did not choose.
- The lookup-table dialog sends the whole value list on every save and never a delta, matching the replace semantics of `PUT /lookups/{id}`.
- The delete confirmation of any of the four kinds loads the `references` endpoint first, lists what depends on the record with its `path`, links each item to the model that owns it, and keeps the confirm button disabled while the list is not empty.
- A reference panel is rendered from the same endpoint on demand, not preloaded with every row.
- The layer naming rule is edited from the warehouse-planning layer form and does not become a new top-level menu. It is a toggle plus the list of non-conforming `DWD` and `DWS` models. While it is on, the model dialog replaces the free-text suffix with a head composed from a root-word select and the model's own process, followed by an optional qualifier field the user may leave empty; the composed preview shows the full suffix and, under it, the resulting table name.

## Proposed Changes

### 1. Four kinds, two container shapes

Field standards and lookup tables are both "records under a directory", so they share the existing category tree rather than duplicating it. The tree gains a node type because DataWorks distinguishes a directory from a standard set, and the distinction changes what a node may contain: a standard set holds records only. Because both kinds share the tree, a standard set also declares which kind it holds. Without that, a set created for field standards would appear in the lookup-table tree holding a different list, and the tree could not answer what a set is for. Both constraints are enforced in the service, not in SQL, because they span rows.

Measurement units deliberately do not use the tree. Their taxonomy is closed — five fixed categories — and DataWorks presents them as a fixed category list. Storing them as user-created directories would let a typo create a sixth category that no filter expects. Naming dictionaries are flat in DataWorks as well.

### 2. Field standard: parent, default value and references

The parent link is a self-referencing foreign key on the Workspace-scoped key `(workspace_id, id)`, with `ON DELETE RESTRICT` so a parent cannot vanish under its children. The service rejects self-parenting, cross-Workspace parents, cycles and depth beyond eight before the write.

The link records specialization only. A child standard keeps its own code, type, length and nullability; nothing is merged down the chain. DataWorks documents the same relationship for `买家ID` and `卖家ID` under `会员ID` and describes the parent as identifying field associations; two children that took the parent's abbreviation would not be distinguishable, which is the reason to define two standards at all. Merging attributes would also make a child's effective contract depend on a record the reader is not looking at, and editing a parent would change fields nobody opened.

`defaultValue` is a string of at most 2048 characters, matching the DataWorks limit. It records the business assumption about what a missing value means; it is not an executed default, because this proposal does not generate `DEFAULT` clauses in physical DDL — YOIP-11 deliberately keeps physical publication free of column defaults. The dialog labels it 业务预设值 and states that it does not become a table default, so nobody expects the database to fill it in. Dropping the attribute as data without a consumer was considered and rejected: a field standard is itself a written contract, and `businessCaliber` has no machine consumer either, yet both tell a modeller how a field is meant to be used.

### 3. Lookup tables

A lookup table is a reusable value range, so it is an independent record with its own code, not a child of a field standard: two standards may reference the same `gender` table, and a model field may bind it directly without a standard in between.

The value list is replaced as a whole on update, matching how model fields are already replaced under one revision. Partial value edits would need either per-value revisions or a diff protocol; neither earns its complexity for lists that are typically under fifty rows. `ordinal` is assigned by the service from the submitted order, and both `(lookup_id, value_code)` and `(lookup_id, ordinal)` are unique so a reordered list cannot produce duplicates.

Deleting a value that a field's stored data still matches is not detectable here: value ranges are a design-time constraint, and this proposal makes no claim that existing physical data conforms. That claim belongs to the quality module.

### 4. Measurement units

DataWorks names currency, quantifier and time units. This proposal adds `RATIO` and `RANK`, which percentage shares and ranking fields need and which a closed list has to name explicitly instead of leaving to free text. `en_code` is the abbreviation (`m`), `en_name` the English word (`meter`) and `cn_name` the Chinese name (`米`); the abbreviation is what a model field displays next to its type and what an atomic metric will store later.

Units impose no constraint on a field's type or length. `BIGINT` with unit `件` and `DECIMAL` with unit `元` are both valid, and enforcing a category-to-type mapping would reject legitimate combinations such as a currency amount stored as an integer of cents.

### 5. Naming dictionary and table-name checking

A dictionary entry is only useful if something validates against it, so the proposal wires it into layer table naming in the same release. When a layer enables the rule, the suffix of a model that carries a business process must begin with `<root word abbreviation>_<process code>`: the root word is an existing naming-dictionary entry of the Workspace, and the process token is the model's own business process, whose code is already reserved across the installation. The rule reads the suffix, not the stored table name, because `fullName` composes the name as the layer prefix plus the submitted suffix: `dwd_trade_order` is the prefix `dwd_` over the suffix `trade_order`, and only the suffix is the modeller's text.

An optional `_<qualifier>` may follow the required head. A summary layer holds one table per granularity, so `dws_trade_order_1d` and `dws_trade_order_1h` share a process and must both be legal. Matching the head exactly would admit one suffix per layer per process and therefore one table per process, which is not how summary layers are built. The qualifier stays free of dictionary control, but it still has to satisfy `SUFFIX` and the 41-character limit, so a required head already near that limit leaves no room for one.

The required head consumes the same 41 characters the suffix pattern allows, and `fullName` checks that pattern before any naming rule runs. Defining a root word and a process separately cannot detect an over-long pair, because neither knows the other yet, so the check belongs where the pair first exists: enabling the rule is refused when the head for this root word exceeds 41 characters under any process in the layer, and the refusal names the process that does not fit.

The rule validates a shape against a governed vocabulary; it does not pin one process per layer. Which process a table belongs to is already a model column chosen when the model is created, so a layer that fixed it would either duplicate that column or contradict it.

The rule is intentionally narrower than a regular expression. The backend already validates the suffix shape through `ModelServiceImpl.SUFFIX` and composes the prefix in `fullName`; the value this proposal adds is a governed vocabulary, not a second pattern language. One root word plus the model's process covers the `dwd_trade_order` shape DataWorks documents; it does not cover multi-word roots, several leading segments or alternation. Those stay out, as recorded in item 6 of Rejected Alternatives.

Only the two kinds that carry a business process are checked: `DWD` and `DWS`, which `ModelType.isPublicLayer` marks and which the service already forces to name a process. `DIM` and `ADS` are anchored to a concept dimension and to a data mart instead, so they have no process token; applying the rule to them would either invent one or reduce the name to a single dictionary word. Both would be rules this proposal cannot state, so those two kinds are exempt rather than checked loosely.

A model named under the rule records the entry it used in `naming_root_word_id`. That makes the entry's reference list exact instead of a guess from the current name, and it lets the delete check refuse a root word that is still in use instead of quietly invalidating names that were valid before the delete.

Enabling a rule on a layer that already holds models is refused while any of them does not conform, unless the request acknowledges them. On enable the service matches the suffix of each `DWD` and `DWS` model against the dictionary, backfills the entry on the ones whose suffix starts with a known head, and returns the rest to the dialog by name; they stay listed in the layer panel until they are renamed. `DIM` and `ADS` models are neither matched nor reported. Acknowledged violations are visible rather than silent — grandfathering without acknowledgement is still refused, because the layer would otherwise switch to a state where the rule is on, knowingly broken, and nothing would record that the operator saw it. Disabling a rule never renames anything, and while a rule is on, changing a model's business process requires the matching rename in the same request.

### 6. Binding is authoritative

Today a bound standard seeds the field and the user may then change the seeded values, including nullability. The contract becomes: while a standard is bound, the five inherited attributes are read-only; unbinding restores editing. A lookup table used alone controls the field name and display name the same way.

Existing rows are not rewritten. A field that was bound before this release keeps its current values even if they differ from the standard, and the divergence becomes visible the next time the standard is re-bound. Rewriting stored fields during migration would silently change published-model contracts and, through YOIP-11, the DDL a later publication generates. Binding a standard to a field of an already published model does change that field's code or type, but the physical table is not altered behind the user's back: YOIP-11's create-or-verify reports the difference at the next publication, so the drift is surfaced instead of applied.

### 7. Reference queries replace the placeholder

`StandardReferencesVO` gains an `items` list of `{ kind, id, name, path }` and reports `AVAILABLE` instead of `NOT_ENABLED`. Each kind answers from the tables that point at it: model fields for standards and units, field standards plus model fields for lookup tables, and models for naming entries.

`path` is the locator an operator can act on — `dwd_trade_order.order_amt` for a model field, the model name for a naming entry — and each item carries the id needed to open the owning model, so the delete dialog can answer "where is this used" instead of only refusing.

The query is on demand, not embedded in list responses. A page of fifty standards would otherwise need fifty reference aggregates, and the panel is opened for one record at a time.

### 8. Identifier uniqueness moves to the Workspace

All four kinds use `(workspace_id, en_code)`. For `yak_ops_standard_field` this relaxes an existing global unique key; because a global unique value is also unique inside its own Workspace, the migration cannot conflict. The behavior change is that two Workspaces may now define the same code, which is the isolation every other modeling table already provides.

## Compatibility, Deprecation, and Migration Plan

- `V5__v1_7_0.sql` applies after `V4__v1_6_0.sql`, which YOIP-11 already contributes on `master`. A fresh install applies the migrations in order.
- Five tables are altered — `yak_ops_standard_category`, `yak_ops_standard_field`, `yak_ops_modeling_model`, `yak_ops_modeling_model_field` and `yak_ops_modeling_layer`; every added column is nullable or defaulted, so the migration needs no backfill to keep existing rows valid.
- Beyond normal edits, the only write this proposal makes to existing model rows is the `naming_root_word_id` backfill, and only when an operator explicitly enables a rule on that layer. It is derived from the current name, so it cannot contradict what the row already says.
- Deleting a field standard that a model field binds already fails with `REFERENCED` today — the foreign key from `yak_ops_modeling_model_field` enforces it, not the query. What changes is that the refusal becomes explainable: the same code now arrives with a list the dialog can show. The three new kinds get that constraint and query pair from the start.
- The unique-key replacement on `yak_ops_standard_field` is a relaxation and cannot fail on existing data. It is the only change in this proposal that removes a constraint.
- Existing field-standard REST payloads stay valid: the four new attributes are optional, and `lengthPrecision` is unchanged on the wire. Existing frontend callers keep working without modification.
- Binding behavior changes for newly bound fields only. Previously bound fields keep their stored values; nothing is rewritten by the migration.
- `StandardReferencesVO` gains a field and changes its `availability` value from `NOT_ENABLED` to `AVAILABLE`. Both changes are additive to the JSON shape, and the only consumer is the data-standard page shipped with this release.
- Enabling a layer naming rule is opt-in per layer and defaults to disabled, so no existing model becomes invalid on upgrade.
- No downgrade path is provided, consistent with previous releases; restoring the pre-upgrade backup is the supported rollback.
- Nothing is deprecated in this proposal.

## Test Plan

- Unit tests cover parent-chain validation (self, cross-Workspace, cycle, depth limit), lookup value ordering and duplicate detection, code availability scoped to a Workspace, unit category constraints, reference aggregation for all four kinds including the `path` of each item, deletion refusal while referenced and container deletion still using `HAS_DEPENDENCIES`, and the binding matrix where a field standard wins over a lookup table and a unit overrides nothing.
- Naming-rule tests cover a conforming head, a permitted qualifier (`trade_order_1d`), a wrong token order, an unknown root word, an exempt `DIM` model, an exempt `ADS` model, a checked `DWS` model, enablement refused without acknowledgement, enablement accepted with it, the backfill on enable, refusal of a head that cannot fit the 41-character suffix limit, and a rejected bypass of the composed head.
- Database integration tests cover `V5` application on top of `V4`, the self-referencing parent foreign key and its `RESTRICT` behavior, cascade deletion of lookup values with their table, the replaced unique key allowing the same code in two Workspaces, Workspace isolation for all four kinds, refusal to delete a record referenced by a model field or by a model's naming root word, and a standard set rejecting a record of the other kind.
- Frontend verification covers `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run architecture:check`, `npm run contract:check` and `npm run build`, plus Node unit tests for the naming-rule matcher and the length/precision reassembly.
- Manual acceptance must create one record of each kind, create a standard set and confirm it refuses a record of the other kind, bind a model field to a field standard that references a lookup table and a unit, confirm the five inherited attributes are read-only and that the default value is labelled as not producing a DDL default, reorder lookup values and confirm the value codes stay unique, enable a layer naming rule on a layer with one non-conforming model and confirm both the refusal and the acknowledged path, create a conforming `DWD` model and confirm the head is composed rather than typed, create a `DWS` model for the same process carrying a `_1d` qualifier and confirm the two coexist, confirm a `DIM` model is not checked at all, and open the delete dialog of a referenced record to confirm it lists and links its dependants. Browser interaction is not covered by an automated per-feature Playwright script in this proposal.

## Rejected Alternatives

1. **Store the three kinds as rows of one generic standard table with a `kind` column.** The kinds do not share attributes: a lookup table owns an ordered value list, a unit owns a closed category, a dictionary entry owns an abbreviation used in name matching. One table would need either a nullable column for every kind's fields or a JSON payload that cannot be indexed or reference-checked.
2. **Reuse lookup tables for measurement units.** A unit has a fixed taxonomy and is consumed by atomic metrics; a lookup table is an open set of enumerated values consumed by field ranges. Merging them would inflate every code list with unit rows and force the metric module to filter by a flag.
3. **Split `lengthPrecision` into two wire fields.** DataWorks shows length and precision as separate inputs, but the persisted form is one string and every existing caller already sends it. Splitting the field would break every payload for a UI convenience that can be solved in the dialog.
4. **Merge parent-standard attributes down the chain.** Making a child inherit its parent's type or code would mean a child's effective contract depends on another record, and editing a parent would silently change unrelated fields. The link records specialization only.
5. **Ship the naming dictionary without wiring it into table naming.** A dictionary that nothing validates against is inert data, and the layer checker is the only consumer DataWorks documents. Shipping the dictionary alone would leave the feature unusable while appearing complete.
6. **Let a layer name be checked by an arbitrary regular expression instead of a dictionary rule.** That is what `ModelServiceImpl.SUFFIX` already does: one fixed pattern every suffix must satisfy. A second pattern language adds configuration surface without adding governance, and the dictionary would then have no consumer.
7. **Add bulk `.xlsx` import and export in this proposal.** Import is a separate write channel with its own template, partial-failure semantics, size limits and overwrite policy. It multiplies the surface of four kinds at once and is better added once the four contracts are stable.
8. **Generate quality rules from lookup tables now.** The quality module does not exist: there is no rule entity, no strong or weak classification and no schedule binding. Deriving a rule before the rule model exists would fix the shape prematurely.
9. **Materialize a lookup table into a physical table or view now.** Publication semantics, refresh policy and ownership of a materialized code table are undecided, and YOIP-11 publishes logical models only. A materialized table without a refresh contract would go stale silently.
10. **Keep `en_code` globally unique and apply the same scope to the new kinds.** A global key means two Workspaces cannot both define `member_id`, which contradicts the isolation every other modeling table enforces. Relaxing the key is safe for existing data.
11. **Pin one root word and one business process on the layer.** Pinning the process would make the layer contradict the model: which process a table belongs to is chosen on the model, and `applyProcessOwnership` already rejects a model whose process is not under its own domain. Pinning the root word as well would leave every table in the layer sharing one head, which says nothing about what any single table holds.
12. **Prefill `DECIMAL` length and precision with `18,2`.** A value nobody chose is a value somebody publishes: a ratio or rate field needs more than two decimal places and would be truncated without anyone typing a wrong number. A placeholder carries the same hint without committing to it.
13. **Return the referencing or violating names inside the error message.** The shared error envelope carries one fixed `ErrorCode` and no per-request detail; putting names in the text would either bypass the structured code or change the envelope for every module. Both lists are already readable from their own endpoint, which is what the dialogs call before submitting.
14. **Enable a rule unconditionally and treat existing violators as exempt.** An exemption that nothing records is indistinguishable from a broken rule: the layer panel would look conforming while models under it are not. Acknowledgement keeps the convenience and leaves the list visible until the names are fixed.
15. **Require the suffix to equal the composed head exactly.** Exact matching admits one suffix per layer per business process, so a summary layer could not hold `dws_trade_order_1d` and `dws_trade_order_1h` side by side — which is the normal shape of a summary layer, not an edge case. Prefix matching keeps both governed halves under control and leaves only the granularity to the modeller.
