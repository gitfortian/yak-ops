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
| Measurement unit (度量单位) | Currency, quantifier, ratio, rank and time units referenced by fields and by atomic metrics | Absent |
| Naming dictionary (命名词典) | Root words and morphemes that a layer's table-name checker validates against | Absent; YOIP-9 records the same exclusion — "可配置正则与命名词典仍不做" |

Each gap blocks a capability that is already planned or already shipped:

- Without a lookup table a field standard cannot constrain a value range. A `gender` field accepts any string, and when the quality module arrives there is no enumerated-value rule to derive from the standard.
- Without measurement units a model field cannot record that a quantity is measured in `件` or `元`. The metric module does not exist yet, but DataWorks feeds the same unit list into atomic metrics, so building metrics first would force a second unit vocabulary later.
- Without a naming dictionary, table-name validation stays one regular expression in `MODEL_SUFFIX_RULE`. The layer prefix is enforced; the governed vocabulary behind it is not.
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
| Containers | Field standards and lookup tables share one Workspace directory tree. A node is either a `DIRECTORY` or a `STANDARD_SET`. A directory holds directories, standard sets and records; a standard set holds records only and cannot be a parent. Measurement units use five fixed categories — `CURRENCY`, `QUANTIFIER`, `RATIO`, `RANK`, `TIME` — instead of a tree. Naming dictionaries are a flat Workspace list. |
| Field standard | Adds `defaultValue` (at most 2048 characters), `parentStandardId`, `lookupId` and `measureUnitId` to the existing attributes. `lengthPrecision` keeps its current wire format. |
| Parent standard | A field standard may name one parent in the same Workspace. The chain is acyclic and at most eight levels deep. The link records specialization for navigation and grouping; it does **not** merge attributes, so a child keeps its own code, type and nullability. |
| Lookup table | Code, Chinese name, English name, optional description, and an ordered value list. A value carries `valueCode`, `cnName`, `enName` and optional `meaning`. Value codes are unique inside one table. A field that binds a lookup table takes its code as the field name and its Chinese name as the display name. |
| Measurement unit | Category, abbreviation, English name, Chinese name, optional description. Abbreviations are unique inside a Workspace. Binding a unit is an annotation: it never renames a field and never changes its type. |
| Naming dictionary | Abbreviation, English name, Chinese name, optional description. Abbreviations are unique inside a Workspace and are the token a table-name rule matches. |
| Binding a model field | A field may bind a field standard, a lookup table, a measurement unit, or any combination. A bound field standard is authoritative for English code, Chinese name, type, length or precision and not-null; those five controls are read-only until the standard is unbound. A lookup table supplies code and Chinese name only when no field standard is bound. A unit never overrides anything. |
| References | Every kind exposes a reference list: field standard → model fields; lookup table → field standards and model fields; measurement unit → field standards and model fields; naming dictionary → layers whose table-name rule uses it. Deleting a referenced record is refused with the existing `HAS_DEPENDENCIES`. |
| Table-name checking | A layer may enable a naming rule composed of one naming-dictionary entry and one business process from the same data domain. When enabled, creating or renaming a model under that layer requires the suffix to be exactly `<root word abbreviation>_<business process code>`. Disabling the rule leaves existing names untouched. |
| Uniqueness scope | Codes of all four kinds are unique inside a Workspace, not across the installation. |
| Empty and error states | A standard set with no records shows an empty state rather than a table; creating a record still requires a selected container. A name rejected by an enabled rule is caught in the dialog, which shows the expected suffix computed from the rule; the server rejects it as a fixed `NAMING_RULE_VIOLATION` if the client check is bypassed. |

### REST

All endpoints extend the existing namespace `/api/v1/data-modeling/data-standards`, except the two layer rule endpoints, which are absolute. Paths below are relative to that namespace unless written in full.

| Method and path | Purpose |
| --- | --- |
| `GET /categories/tree` | Directory tree; existing response gains `nodeType` on every node. |
| `POST /categories` | Create a node; request gains `nodeType`, defaulting to `DIRECTORY`. |
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
| `GET /api/v1/data-modeling/warehouse-planning/layers/{id}/naming-rule` | Read the layer rule, or an explicit disabled state. |
| `PUT /api/v1/data-modeling/warehouse-planning/layers/{id}/naming-rule` | Enable, replace or disable the rule under the observed revision. |

Existing field-standard endpoints keep their paths. Their payloads gain the four new optional attributes, and `GET /fields/{id}/references` starts returning real data instead of `NOT_ENABLED`.

New error codes in `DimensionModelingErrorCode`:

| Code | Use |
| --- | --- |
| `INVALID_PARENT_STANDARD` (19515) | The chosen parent is the record itself, is outside the Workspace, or would create a cycle or exceed eight levels. |
| `INVALID_LOOKUP_VALUE` (19516) | A value code is empty or duplicated, or the submitted list is missing; the whole value list is rejected. |
| `INVALID_NAMING_RULE` (19517) | The root word or business process is missing, outside the Workspace, or belongs to a different data domain; or the layer's existing models cannot satisfy a rule being enabled. |
| `NAMING_RULE_VIOLATION` (19518) | A model name does not match the enabled layer rule. The fixed message is a fallback: the dialog renders the expected suffix before submitting, because the shared error type carries no per-request detail. |

Duplicate codes continue to use `DUPLICATE_CODE`, revision conflicts `REVISION_CONFLICT`, and refusal to delete a referenced record `HAS_DEPENDENCIES`. A rejected name under an enabled rule uses `NAMING_RULE_VIOLATION`; `INVALID_TABLE_NAME` keeps its current meaning of a malformed prefix or an over-long name.

### Persisted formats

`V5__v1_7_0.sql`, the single release migration for 1.7.0, is additive except for one index replacement:

- `yak_ops_standard_category` gains `node_type VARCHAR(16) NOT NULL DEFAULT 'DIRECTORY' CHECK (node_type IN ('DIRECTORY','STANDARD_SET'))`.
- `yak_ops_standard_lookup`: `id`, `workspace_id`, `category_id`, `en_code`, `cn_name`, `en_name`, nullable `description`, `revision`, audit columns. Unique on `(workspace_id, en_code)` and on `(workspace_id, id)`, the latter being the foreign-key target.
- `yak_ops_standard_lookup_value`: `id`, `workspace_id`, `lookup_id` with `ON DELETE CASCADE`, `ordinal`, `value_code`, `cn_name`, `en_name`, nullable `meaning`. Unique on `(lookup_id, value_code)` and `(lookup_id, ordinal)`. The column is `value_code` rather than `value`, which is reserved on MySQL.
- `yak_ops_standard_measure_unit`: `id`, `workspace_id`, `category`, `en_code`, `en_name`, `cn_name`, nullable `description`, `revision`, audit columns. Unique on `(workspace_id, en_code)` and on `(workspace_id, id)`; `category` is constrained to the five values.
- `yak_ops_standard_naming_entry`: `id`, `workspace_id`, `en_code`, `en_name`, `cn_name`, nullable `description`, `revision`, audit columns. Unique on `(workspace_id, en_code)` and on `(workspace_id, id)`. A naming entry has no category, so it needs no tree.
- `yak_ops_standard_field` gains `default_value VARCHAR(2048)`, `parent_standard_id`, `lookup_id` and `measure_unit_id`, all nullable and all foreign keys with `ON DELETE RESTRICT` against the Workspace-scoped key of their target.
- `yak_ops_modeling_model_field` gains nullable `lookup_id` and `measure_unit_id` with the same referential rules, so a field can bind a lookup table without a field standard.
- `yak_ops_modeling_layer` gains `naming_rule_enabled TINYINT NOT NULL DEFAULT 0`, nullable `naming_root_word_id` and nullable `naming_process_id`.
- `uk_standard_field_code (en_code)` is replaced by `uk_standard_field_code (workspace_id, en_code)`.

No existing column is dropped or rewritten. Existing rows keep their values, and every new column is nullable or has a default.

### Frontend internal contract

- `service/data-modeling/data-standards` gains lookup, unit, naming-entry and reference clients; the directory tree, availability check and revision-conflict handling stay shared with field standards.
- The data-standard page owns one kind selector and four list components. Switching kinds does not remount the page or lose the other kinds' query state.
- The field-standard dialog splits `lengthPrecision` into separate length and precision inputs and reassembles the wire value; it does not change the payload shape.
- A reference panel is rendered from the `references` endpoint on demand, not preloaded with every row.
- The layer naming rule is edited from the warehouse-planning layer form and does not become a new top-level menu.

## Proposed Changes

### 1. Four kinds, two container shapes

Field standards and lookup tables are both "records under a directory", so they share the existing category tree rather than duplicating it. The tree gains a node type because DataWorks distinguishes a directory from a standard set, and the distinction changes what a node may contain: a standard set holds records only. The constraint is enforced in the service, not in SQL, because it spans rows.

Measurement units deliberately do not use the tree. Their taxonomy is closed — five fixed categories — and DataWorks presents them as a fixed category list. Storing them as user-created directories would let a typo create a sixth category that no filter expects. Naming dictionaries are flat in DataWorks as well.

### 2. Field standard: parent, default value and references

The parent link is a self-referencing foreign key on the Workspace-scoped key `(workspace_id, id)`, with `ON DELETE RESTRICT` so a parent cannot vanish under its children. The service rejects self-parenting, cross-Workspace parents, cycles and depth beyond eight before the write.

The link records specialization only. A child standard keeps its own code, type, length and nullability; nothing is merged down the chain. This is what DataWorks does in practice — `买家ID` and `卖家ID` specialize `会员ID` and still carry distinct codes — and merging attributes would make a child's effective contract depend on a record the reader is not looking at.

`defaultValue` is a string of at most 2048 characters, matching the DataWorks limit. It is metadata about the field, not an executed default: this proposal does not generate `DEFAULT` clauses in physical DDL, because YOIP-11 deliberately keeps physical publication free of column defaults.

### 3. Lookup tables

A lookup table is a reusable value range, so it is an independent record with its own code, not a child of a field standard: two standards may reference the same `gender` table, and a model field may bind it directly without a standard in between.

The value list is replaced as a whole on update, matching how model fields are already replaced under one revision. Partial value edits would need either per-value revisions or a diff protocol; neither earns its complexity for lists that are typically under fifty rows. `ordinal` is assigned by the service from the submitted order, and both `(lookup_id, value_code)` and `(lookup_id, ordinal)` are unique so a reordered list cannot produce duplicates.

Deleting a value that a field's stored data still matches is not detectable here: value ranges are a design-time constraint, and this proposal makes no claim that existing physical data conforms. That claim belongs to the quality module.

### 4. Measurement units

The five categories mirror DataWorks. `en_code` is the abbreviation (`m`), `en_name` the English word (`meter`) and `cn_name` the Chinese name (`米`); the abbreviation is what a model field displays next to its type and what an atomic metric will store later.

Units impose no constraint on a field's type or length. `BIGINT` with unit `件` and `DECIMAL` with unit `元` are both valid, and enforcing a category-to-type mapping would reject legitimate combinations such as a currency amount stored as an integer of cents.

### 5. Naming dictionary and table-name checking

A dictionary entry is only useful if something validates against it, so the proposal wires it into layer table naming in the same release. A layer's rule is one root word plus one business process from the same data domain, and the expected suffix is exactly `<root word abbreviation>_<business process code>`.

The rule is intentionally narrower than a regular expression. YOIP-9 already enforces the layer prefix through `MODEL_SUFFIX_RULE`, and the value this proposal adds is a governed vocabulary, not a second pattern language. A rule of one word plus one process covers the `dwd_trade_order` shape DataWorks documents; it does not cover multi-word roots, optional segments or alternation. Those stay out, as recorded in item 6 of Rejected Alternatives.

Enabling a rule on a layer that already holds models is refused when any existing model would violate it. The dialog lists the violating model names before submitting, reading the existing model page filtered by layer; the server rejects the enablement as a fixed `INVALID_NAMING_RULE`, because the shared error type carries no per-request detail. Silently grandfathering them would leave the layer in a state where the rule is on and knowingly violated. Disabling a rule never renames anything.

### 6. Binding is authoritative

Today a bound standard seeds the field and the user may then change the seeded values, including nullability. The contract becomes: while a standard is bound, the five inherited attributes are read-only; unbinding restores editing. A lookup table used alone controls the field name and display name the same way.

Existing rows are not rewritten. A field that was bound before this release keeps its current values even if they differ from the standard, and the divergence becomes visible the next time the standard is re-bound. Rewriting stored fields during migration would silently change published-model contracts and, through YOIP-11, the DDL a later publication generates.

### 7. Reference queries replace the placeholder

`StandardReferencesVO` gains an `items` list of `{ kind, id, name, path }` and reports `AVAILABLE` instead of `NOT_ENABLED`. Each kind answers from the tables that point at it: model fields for standards and units, field standards plus model fields for lookup tables, layers for naming entries.

The query is on demand, not embedded in list responses. A page of fifty standards would otherwise need fifty reference aggregates, and the panel is opened for one record at a time.

### 8. Identifier uniqueness moves to the Workspace

All four kinds use `(workspace_id, en_code)`. For `yak_ops_standard_field` this relaxes an existing global unique key; because a global unique value is also unique inside its own Workspace, the migration cannot conflict. The behavior change is that two Workspaces may now define the same code, which is the isolation every other modeling table already provides.

## Compatibility, Deprecation, and Migration Plan

- `V5__v1_7_0.sql` applies after `V4__v1_6_0.sql` from YOIP-11. If YOIP-11 is not accepted first, the same migration is renumbered to follow `V3__v1_5_0.sql` and nothing else changes. A fresh install applies the migrations in order.
- Four tables are altered — `yak_ops_standard_category`, `yak_ops_standard_field`, `yak_ops_modeling_model_field` and `yak_ops_modeling_layer`; every added column is nullable or defaulted, so existing rows remain valid without a backfill.
- The unique-key replacement on `yak_ops_standard_field` is a relaxation and cannot fail on existing data. It is the only change in this proposal that removes a constraint.
- Existing field-standard REST payloads stay valid: the four new attributes are optional, and `lengthPrecision` is unchanged on the wire. Existing frontend callers keep working without modification.
- Binding behavior changes for newly bound fields only. Previously bound fields keep their stored values; nothing is rewritten by the migration.
- `StandardReferencesVO` gains a field and changes its `availability` value from `NOT_ENABLED` to `AVAILABLE`. Both changes are additive to the JSON shape, and the only consumer is the data-standard page shipped with this release.
- Enabling a layer naming rule is opt-in per layer and defaults to disabled, so no existing model becomes invalid on upgrade.
- No downgrade path is provided, consistent with previous releases; restoring the pre-upgrade backup is the supported rollback.
- Nothing is deprecated in this proposal.

## Test Plan

- Unit tests cover parent-chain validation (self, cross-Workspace, cycle, depth limit), lookup value ordering and duplicate detection, code availability scoped to a Workspace, unit category constraints, naming-rule matching and its rejection, reference aggregation for all four kinds, deletion refusal while referenced, and the binding matrix where a field standard wins over a lookup table and a unit overrides nothing.
- Database integration tests cover `V5` application on top of `V4`, the self-referencing parent foreign key and its `RESTRICT` behavior, cascade deletion of lookup values with their table, the replaced unique key allowing the same code in two Workspaces, Workspace isolation for all four kinds, and refusal to delete a record referenced by a model field.
- Frontend verification covers `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run architecture:check`, `npm run contract:check` and `npm run build`, plus Node unit tests for the naming-rule matcher and the length/precision reassembly.
- Manual acceptance must create one record of each kind, bind a model field to a field standard that references a lookup table and a unit, confirm the five inherited attributes are read-only, reorder lookup values and confirm the value codes stay unique, enable a layer naming rule and confirm a compliant name is accepted while a non-compliant one is rejected with the expected suffix, and delete a referenced record to confirm the refusal. Browser interaction is not covered by an automated per-feature Playwright script in this proposal.

## Rejected Alternatives

1. **Store the three kinds as rows of one generic standard table with a `kind` column.** The kinds do not share attributes: a lookup table owns an ordered value list, a unit owns a closed category, a dictionary entry owns an abbreviation used in name matching. One table would need either a nullable column for every kind's fields or a JSON payload that cannot be indexed or reference-checked.
2. **Reuse lookup tables for measurement units.** A unit has a fixed taxonomy and is consumed by atomic metrics; a lookup table is an open set of enumerated values consumed by field ranges. Merging them would inflate every code list with unit rows and force the metric module to filter by a flag.
3. **Split `lengthPrecision` into two wire fields.** DataWorks shows length and precision as separate inputs, but the persisted form is one string and every existing caller already sends it. Splitting the field would break every payload for a UI convenience that can be solved in the dialog.
4. **Merge parent-standard attributes down the chain.** Making a child inherit its parent's type or code would mean a child's effective contract depends on another record, and editing a parent would silently change unrelated fields. The link records specialization only.
5. **Ship the naming dictionary without wiring it into table naming.** A dictionary that nothing validates against is inert data, and the layer checker is the only consumer DataWorks documents. Shipping the dictionary alone would leave the feature unusable while appearing complete.
6. **Let a layer name be checked by an arbitrary regular expression instead of a dictionary rule.** That is what `MODEL_SUFFIX_RULE` already does. A second pattern language adds configuration surface without adding governance, and the dictionary would then have no consumer.
7. **Add bulk `.xlsx` import and export in this proposal.** Import is a separate write channel with its own template, partial-failure semantics, size limits and overwrite policy. It multiplies the surface of four kinds at once and is better added once the four contracts are stable.
8. **Generate quality rules from lookup tables now.** The quality module does not exist: there is no rule entity, no strong or weak classification and no schedule binding. Deriving a rule before the rule model exists would fix the shape prematurely.
9. **Materialize a lookup table into a physical table or view now.** Publication semantics, refresh policy and ownership of a materialized code table are undecided, and YOIP-11 publishes logical models only. A materialized table without a refresh contract would go stale silently.
10. **Keep `en_code` globally unique and apply the same scope to the new kinds.** A global key means two Workspaces cannot both define `member_id`, which contradicts the isolation every other modeling table enforces. Relaxing the key is safe for existing data.
