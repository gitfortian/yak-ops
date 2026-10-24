# YOIP-12: Dimension Modeling — Reverse Modeling from Physical Tables

Status: Discussion

Discussion: N/A until proposed

Decision: Pending

Implementation: N/A until accepted

Release: N/A until released

Related FLIPs: N/A

## Motivation

YOIP-9 declared the `ReverseModelingProvider` seam and deliberately left it unimplemented. The stated reason was scope, not design: the only datasources were MySQL, Oracle and PostgreSQL over JDBC, there was no MaxCompute or EMR Hive, and Paimon is a lake format the JDBC Catalog cannot reach.

That reasoning does not apply to the three JDBC engines. Every capability the seam needs is already shipped:

| Capability | Current state |
| --- | --- |
| Address discovery | `GET /data-source/{id}/catalog/databases`, `GET .../schemas`, `POST .../tables`, `POST .../columns` |
| Logical type matrix | `yak-ops-ui/apps/web/app/data-modeling/logical-models/field-mapping.ts` maps every supported physical type, including `REVIEW` and `UNSUPPORTED` outcomes |
| Model creation | `POST /api/v1/data-modeling/dimension-modeling/models` with fields |
| Name collision | `GET /api/v1/data-modeling/dimension-modeling/models/name-availability` |

What is missing is the product surface that turns an existing physical table into a logical model. Without it, onboarding a table that already exists in the warehouse means retyping every field by hand, and the field descriptions that the table already carries in database comments are lost. That is the largest manual cost in the modelling chain today, and it stands between the shipped modelling modules and any later data-map work.

This proposal implements the YOIP-9 seam for the three JDBC engines only. It adds no REST endpoint and no migration: it is a frontend capability built on the existing Catalog, model-creation and name-availability contracts.

## Public Interfaces

### Product behavior

| Item | Contract |
| --- | --- |
| Entry | The dimension-modeling page gains a `逆向建模` action beside the existing create actions. The data-modeling navigation keeps the three entries YOIP-9 defined: 数仓规划, 数据标准, 维度建模. |
| Table selection | The wizard selects datasource → database → schema, where the engine exposes one → table. It reuses the existing Catalog endpoints and the same scoped loading, error and non-production confirmation rules as field import. |
| Column reading | The wizard reads the physical columns and maps them through the existing logical type matrix. A column that cannot be mapped keeps its row with an empty logical type and a visible reason; it is never dropped silently. |
| Draft generation | The wizard pre-fills the table Chinese name from the table comment (falling back to the table name), the table name suffix by stripping the selected layer prefix, and one field row per physical column. |
| Model type | The user chooses `DWD` or `DIM` explicitly; the wizard defaults to `DWD`. The type is never inferred from the table name. |
| Ownership | `DWD` requires category, data domain and business process. `DIM` requires either a public-layer data domain and dimension, or an application-layer data mart and category. These are exactly the existing model rules. |
| Save | Saving submits the existing model-creation payload. The wizard does not add a creation path of its own. |
| Duplicate name | The wizard checks name availability for the selected layer before saving and blocks a suffix that already exists; the server-side uniqueness rule remains the final guard. |
| Result | After a successful save the user lands in the existing model editor with the created model open, so the draft can be reviewed or corrected before any later physical publication. |

### REST

No endpoint is added, removed or renamed. The capability composes existing endpoints:

| Method and path | Use |
| --- | --- |
| `GET /api/v1/data-source/{id}/catalog/databases` | Database list for the selected datasource. |
| `GET /api/v1/data-source/{id}/catalog/schemas` | Schema list for the selected database, where the engine has schemas. |
| `POST /api/v1/data-source/{id}/catalog/tables` | Candidate tables, filtered by database, schema and keyword. |
| `POST /api/v1/data-source/{id}/catalog/columns` | Physical columns of the chosen table, including comments, nullability and primary-key position. |
| `GET /api/v1/data-modeling/dimension-modeling/models/name-availability` | Pre-save duplicate check for the derived table name. |
| `POST /api/v1/data-modeling/dimension-modeling/models` | Create the logical model and its fields. |

No new error code is introduced. Catalog failures keep their existing error contract; an invalid draft fails through the existing `INVALID_MODEL` and `INVALID_FIELD` codes.

### Persisted formats

None. No migration, no new table, no column change. A reverse-modeled model is an ordinary logical model; nothing records that it originated from a physical table.

### Frontend internal contract

- `ReverseModelingProvider` gains its first implementation: one JDBC provider, selected by the datasource `dbType`, where `discover` calls the tables endpoint and `describe` calls the columns endpoint and returns logical fields through the existing type matrix instead of physical columns.
- The datasource → database → schema → table picker is extracted into one shared component used by both the field-import dialog and the reverse-modeling wizard. The picker is not duplicated, so the scoped-loading and stale-response rules stay in one place.
- A new route `/data-modeling/dimension-modeling/reverse` is added to the router, the known-path set and the login-redirect allowlist. The wizard is a page rather than a dialog because it has selection, draft review and save steps.

## Proposed Changes

### 1. Provider implementation

The provider lives beside the declared seam in `service/data-modeling/dimension-modeling/reverse-modeling.ts` and implements the already-agreed signatures:

- `discover({datasourceId, pattern, exact})` delegates to the Catalog table listing, scoping results to the chosen database, schema and keyword. `exact` filters the returned names client-side so the caller keeps one contract across providers.
- `describe({datasourceId, schema, table})` reads the columns and returns logical fields. The physical-to-logical translation is the existing `mapPhysicalColumn` matrix, not a second copy; a `REVIEW` or `UNSUPPORTED` mapping becomes an empty `dataType` with the reason carried to the draft row.
- The provider is stateless and does not cache Catalog results. Credentials never leave the datasource boundary; the provider only sees identifiers.

The provider interface does not change. Engine-specific behavior stays inside the provider, as YOIP-9 required.

### 2. Draft generation rules

| Draft property | Rule |
| --- | --- |
| `enCode` | The existing field-code normalization applied to the physical column name. A name that cannot be normalized keeps its row with an empty code and a visible reason. |
| `cnName` | The column comment, trimmed and truncated to 128 characters; the column name when there is no comment. |
| `description` | The column comment, truncated to 500 characters, matching the editable description contract completed in 1.5.0. |
| `dataType`, `lengthPrecision` | The logical type matrix result. `STRING` keeps the physical length where the driver reports one; `DECIMAL` keeps a complete precision and scale only. |
| `isPrimaryKey` | The physical primary-key flag. Composite key order is not carried into the draft because the logical model stores no key position. |
| `isNotNull` | `nullable = false`. |
| `isPartition` | Never inferred. Partitioning is a modelling decision, and a physical partition column is not always the logical partition field. |
| `aggregateType` | Never inferred. A reverse-modeled table defaults to `DWD` or `DIM`, neither of which accepts a measure. |

Table-level values:

- `nameSuffix` is pre-filled by stripping `<layerEnCode.toLowerCase()>_` from the physical table name when that prefix matches; otherwise the whole physical name is used. The value stays editable because physical names frequently violate the logical name rule.
- `cnName` is the table comment truncated to 128 characters, or the physical table name.
- `storagePolicy`, `lifecycleDays` and `ownerId` are not inferable from a physical table. They keep the editor defaults and stay visible for confirmation before save.

### 3. Explicit type and ownership

The wizard asks for the model type and ownership in the same step as the draft review. `DWD` and `DIM` are the only offered types, matching the seam's scope and the fact that `DWS` and `ADS` are built from measures that a physical table cannot express.

Ownership is not inferred from the physical schema name, table prefix or datasource. The user picks the category and, depending on the type, the domain, process, dimension or data mart. This keeps one rule for every entry point: the same server-side ownership validation that guards the editor guards the wizard.

### 4. Save path

Save composes the existing payload: the chosen type and ownership, the editable table metadata, and the generated field rows, then calls model creation. Fields that still have no logical type block the save client-side, exactly as the field editor already does, so the server never receives a half-mapped draft.

A created model is an ordinary model. The wizard leaves no reverse-modeling marker on it, so later edits, publication and deletion are unchanged.

### 5. Frontend surface

The wizard has three steps: select the physical table, review and correct the draft, confirm ownership and save. Step one reuses the extracted picker. Step two reuses the existing field editor for correction instead of introducing a second field table. Step three shows the derived name, the duplicate check result and the ownership selectors.

The entry is a page action on the dimension-modeling page, not a new navigation menu item. The navigation contract stays as YOIP-9 defined it, and the wizard remains reachable from the module it belongs to.

### 6. Failure handling

- Catalog failures stay scoped to the area that failed and stay retryable; a failed schema read does not invalidate the chosen datasource.
- A table read that returns no columns blocks the wizard instead of creating an empty model.
- A layer whose prefix does not match the physical table leaves the suffix as the physical name and surfaces the naming rule before save.
- A duplicate name discovered at save time is reported with the same message the editor uses.

## Compatibility, Deprecation, and Migration Plan

- No REST contract, persisted format or migration changes. `V3__v1_5_0.sql` remains the latest migration.
- The `ReverseModelingProvider` interface is unchanged; this proposal only supplies an implementation. YOIP-9's non-goal for reverse modeling is narrowed, not overturned: MaxCompute, EMR Hive and Paimon remain out of scope and still require datasource support first.
- Existing models, editors and field-import behavior are unchanged. The picker extraction preserves the import dialog's current behavior.
- No downgrade path is needed because no persisted state is introduced.
- Nothing is deprecated.

## Test Plan

- Unit tests, attached to the existing frontend contract check, cover: prefix stripping (matching prefix, non-matching prefix, uppercase physical name, over-length name), comment truncation for Chinese name and description, empty-comment fallback, primary-key and nullability propagation, partition and aggregate never inferred, and unsupported columns retained with a reason.
- Provider selection is covered for the three JDBC database types through the datasource `dbType`.
- Frontend verification covers `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run architecture:check`, `npm run contract:check` and `npm run build`.
- Manual acceptance must reverse-model at least one table per supported JDBC engine, plus a table without comments, a table containing an unsupported column type, and a table whose derived name collides with an existing model. The created model must open in the editor with the expected fields and descriptions.
- Browser interaction is not covered by an automated per-feature Playwright script in this proposal; the repository has no such infrastructure.

## Rejected Alternatives

1. **Add a backend reverse-modeling endpoint.** The seam is a frontend contract and every input it needs is already exposed by the Catalog endpoints. A backend endpoint would duplicate the type matrix or move it server-side, splitting the one mapping rule the import path already owns.
2. **Infer the model type from the table name.** Name prefixes vary by team, and a wrong inference silently produces a model with the wrong type and wrong ownership rules. YOIP-10 rejected type-to-layer binding for the same reason; a single explicit selection is cheaper than a heuristic nobody can audit.
3. **Create the model without a review step.** A physical table routinely contains types the logical model cannot express. Auto-creating would produce models that cannot be saved by the editor's own rules, so the draft must be reviewable before it becomes a model.
4. **Support Paimon, MaxCompute and EMR Hive in this proposal.** None of them is an available datasource type, and the JDBC Catalog cannot reach a lake format. Adding them here would turn a frontend capability into a datasource project.
5. **Add a top-level 逆向建模 navigation entry.** The navigation contract is fixed at three entries by YOIP-9, and reverse modeling is a way to create a model inside the existing module, not a separate product surface.
6. **Duplicate the import dialog's picker in the wizard.** Two copies of the datasource → table selection would drift on scoped loading, stale responses and non-production confirmation. One extracted component keeps the rules in a single place.
7. **Mark reverse-modeled models with an origin flag.** The origin does not change any later behavior, and a persisted flag would need a migration and a new compatibility surface for no product gain.
