# YOIP-11: Dimension Modeling — Physical Publishing and Metadata Collection

Status: Accepted

Discussion: https://github.com/gitfortian/yak-ops/pull/22

Decision: Pending

Implementation: N/A until accepted

Release: N/A until released

Related FLIPs: N/A

## Motivation

YOIP-9 and YOIP-10 made logical models the unit of modeling for `DWD`, `DIM`, `DWS` and `ADS`. The product still stops one step short of the warehouse: a model is a logical table name, ownership and field list, but no physical table is created and no persisted physical metadata exists.

The current code has the seams needed for the last mile, but not the contract:

- A model selects a layer, and a layer binds one data source. Neither the layer nor the model stores a target database or schema, while MySQL, Oracle and PostgreSQL have different namespace semantics.
- `DataSourceCatalogService` can list databases, schemas, tables and columns for an authorized data source, but those reads are transient and are not persisted.
- There is no publication record, no physical DDL generation, no publication history and no metadata snapshot.
- `JdbcMetadataApplier` already provides controlled `CREATE TABLE` behavior with verification against an existing table, but it is part of the data-sync connector boundary and has no modeling caller.

This proposal defines the smallest coherent chain from a logical model to a real physical table and a persisted physical metadata snapshot: explicit physical target, controlled create-or-verify publication, immutable publication records, and one-table metadata collection. It deliberately does not introduce scheduled full-datasource crawling, lineage, metric-driven publishing, ALTER/DROP workflows, or a full data-map product surface.

## Public Interfaces

### Product behavior

| Item | Contract |
| --- | --- |
| Publish entry | A model with at least one publishable field gains a `发布` action in the model list and editor. A model with no fields, an unbound/disabled layer, or an invalid physical type mapping is not publishable. |
| Target selection | The publish dialog lists databases and schemas through the existing Catalog endpoints. The user must choose a database and, where the namespace requires one, a schema. The target is never inferred from the data source default, table name, or layer code. |
| Table identity | The physical table name is the model's already-prefixed `name`. Publishing does not rename the logical model. |
| DDL preview | `POST .../publications/preview` returns the generated DDL and resolved target without creating a publication record or touching the database. |
| Execution | `POST .../publications` executes only after explicit confirmation. A successful execution creates one immutable publication record. A retry creates a new publication record. |
| Existing table | Publishing is create-or-verify. If the table is absent, it is created. If it is present, the publication verifies the physical columns, order, primary keys, nullability and comments; any mismatch fails without modifying the table. |
| Metadata | A successful publication immediately collects the published table and its columns into a metadata snapshot. A refresh endpoint recollects and replaces that snapshot for the same publication. |
| Publication status | The model surface shows the latest publication status and publication history. Statuses are `PENDING`, `SUCCEEDED`, `FAILED` and `UNKNOWN`; `UNKNOWN` means the DDL result could not be determined and requires manual reconciliation. |
| Deletion | Deleting a logical model does not drop the physical table. The publication and its metadata snapshot are retained; the publication's model reference becomes null. |

### REST

New endpoints under the existing dimension-modeling controller namespace:

| Method and path | Purpose |
| --- | --- |
| `POST /api/v1/data-modeling/dimension-modeling/models/{modelId}/publications/preview` | Validate the model and target, generate DDL, return the resolved target and DDL without execution. |
| `POST /api/v1/data-modeling/dimension-modeling/models/{modelId}/publications` | Execute the publication for the submitted target and return the created publication record. |
| `GET /api/v1/data-modeling/dimension-modeling/models/{modelId}/publications` | List publication records for one model, newest first. |
| `GET /api/v1/data-modeling/dimension-modeling/publications/{publicationId}` | Return one publication record, including DDL, status and error. |
| `POST /api/v1/data-modeling/dimension-modeling/publications/{publicationId}/metadata/refresh` | Recollect the published table and replace its metadata snapshot. |
| `GET /api/v1/data-modeling/dimension-modeling/publications/{publicationId}/metadata` | Return the persisted physical table and column snapshot for one publication. |

Request bodies for preview and execution carry `database` and optional `schema`. Responses reuse the existing result envelope. No existing endpoint is added, removed or renamed.

New error codes:

| Code | Use |
| --- | --- |
| `PHYSICAL_PUBLISH_FAILED` | Engine failure, unresolvable DDL result, or existing-table verification mismatch. |
| `METADATA_COLLECTION_FAILED` | Catalog collection failure after publication or refresh. |

Ownership, field and standard conflicts continue to use `INVALID_MODEL` and `INVALID_FIELD`; unknown models, layers, data sources, publications or snapshots continue to use `RESOURCE_NOT_EXISTS`.

### Persisted formats

`V4__v1_6_0.sql`, the single release migration for 1.6.0, is additive:

- `yak_ops_modeling_model_publication`
  - `id`, `workspace_id`, nullable `model_id` with `ON DELETE SET NULL`
  - `datasource_id`, `datasource_revision`
  - `physical_database`, nullable `physical_schema`, `physical_table`
  - `status`, `ddl`, nullable `error`
  - `created_time`, `finished_time`, `create_by`
- `yak_ops_modeling_metadata_collection`
  - `id`, `workspace_id`, `publication_id` with `ON DELETE CASCADE`
  - `status`, `table_count`, `field_count`, nullable `error`
  - `created_time`, `finished_time`, `create_by`
- `yak_ops_modeling_metadata_table`
  - `id`, `workspace_id`, `collection_id` with `ON DELETE CASCADE`
  - `datasource_id`, `database`, nullable `schema`, `name`, `type`, nullable `remarks`
- `yak_ops_modeling_metadata_field`
  - `id`, `workspace_id`, `table_id` with `ON DELETE CASCADE`
  - `ordinal`, `name`, `type_name`, `jdbc_type`, nullable `size`, nullable `scale`, `nullable`, `primary_key`, `primary_key_position`, nullable `remarks`

No column is added to, removed from or rewritten on `yak_ops_modeling_model` or `yak_ops_modeling_model_field`. Existing rows are untouched.

### Frontend internal contract

- The dimension-modeling service gains publication and metadata endpoints; publish target selection reuses the existing datasource Catalog service.
- The publish dialog owns target selection, DDL preview, confirmation and result presentation. It does not become a global store.
- Model list and editor show publication status without duplicating publication state outside the query cache.

## Proposed Changes

### 1. Explicit physical target

The publish request carries `database` and optional `schema`. The service validates the address through the existing Catalog seams before generating DDL: the database must be visible for the bound data source, and a non-empty schema must be visible for that database. MySQL has no independent schema, so a MySQL publication rejects a schema. Oracle and PostgreSQL publications require the selected schema to be one of the Catalog values.

The resolved address is stored on the publication record. The logical model does not gain database or schema columns; one model may be published to more than one explicit address, and each publication remains independently auditable.

### 2. Physical type mapping

The generated DDL maps the five logical types as follows:

| Logical type | Physical type |
| --- | --- |
| `STRING` | `varchar(length)`; a positive length is required |
| `BIGINT` | `bigint` |
| `DECIMAL` | `decimal(precision,scale)`; `lengthPrecision` is required and must be a complete, in-range value |
| `DATE` | `date` |
| `TIMESTAMP` | `timestamp` |

Field order follows the model field ordinal. Primary-key order, nullability and column comments are preserved where the dialect supports comments; the comment is the field Chinese name. `isPartition`, `aggregateType`, `storagePolicy` and `lifecycleDays` remain logical attributes and do not generate physical partition, storage or lifecycle clauses in this proposal.

### 3. Controlled publication execution

The publisher resolves the bound data source through the existing datasource lookup boundary, records the data source revision used, and executes through the existing controlled JDBC metadata path. Credentials are never persisted on the publication or metadata records and never enter logs or API responses.

Execution is create-or-verify:

- Absent table: create it with the generated DDL.
- Present table: verify the physical columns, order, primary keys, nullability and comments. A mismatch fails with `PHYSICAL_PUBLISH_FAILED` and leaves the table unchanged.

DDL execution is auto-commit at the engine boundary. The proposal does not claim a distributed transaction between the metadata database and the target engine. If the engine result is unknown, the publication is recorded as `UNKNOWN` with the attempted DDL and error context; automatic rollback is not attempted.

### 4. Metadata collection

After a successful create-or-verify, the publication collects exactly the published table through the existing Catalog seams and persists one collection snapshot: one metadata table row and one row per physical column. The refresh endpoint recollects the same table and replaces the snapshot transactionally in the metadata database. It never mutates the logical model.

Full-datasource crawling is out of scope for this proposal. The current Catalog table listing is bounded and lacks a stable cursor, so a whole-source crawler would need separate pagination, run identity and failure semantics before it could be safe.

### 5. Module ownership and dependency direction

Publication orchestration, physical type mapping and metadata persistence belong to `yak-ops-business-modeling`. That module gains dependencies on the datasource business boundary and the JDBC connector metadata path to reuse connection resolution, dialect selection and controlled DDL execution. No reverse dependency is introduced: datasource and connector code do not import modeling types. Credential ownership remains inside the existing datasource plugin and lookup boundary.

### 6. Frontend surface

The model list and editor gain a publish action. The publish dialog selects database and schema from Catalog results, shows generated DDL, requires explicit confirmation and displays the terminal publication result. Publication history and the latest status are visible from the model. The metadata snapshot is readable from the publication, but this proposal does not add a separate top-level data-map menu.

## Compatibility, Deprecation, and Migration Plan

- `V4__v1_6_0.sql` is additive and forward-only. Upgrade from 1.5.0 applies it after `V3__v1_5_0.sql`; a fresh install applies `V1` through `V4` in order.
- Existing logical models, REST payloads and frontend behavior are unchanged until a user publishes a model. New REST endpoints and error codes are additive.
- Publication and metadata records are Workspace-scoped and inherit the existing isolation rules.
- No downgrade path is provided, consistent with previous releases; restoring the pre-upgrade backup is the supported rollback.
- Nothing is deprecated in this proposal.

## Test Plan

- Unit tests cover the logical-to-physical type matrix, required `STRING` length and `DECIMAL` precision, primary-key order and nullability, comment generation, target validation for MySQL/Oracle/PostgreSQL, existing-table mismatch classification, publication state transitions and metadata snapshot mapping.
- Database integration tests cover `V4` application on top of `V3`, publication success and failure records, metadata refresh replacing a snapshot, Workspace isolation for publications and metadata, cross-Workspace data source access returning `RESOURCE_NOT_EXISTS`, and model deletion retaining publication and metadata rows with a null model reference.
- Frontend verification covers `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run architecture:check`, `npm run contract:check` and `npm run build`.
- Manual acceptance must exercise a real JDBC publication for at least one supported database, including an absent-table create, an existing-table verify and an intentional mismatch rejection. Browser interaction is not covered by an automated per-feature Playwright script in this proposal.

## Rejected Alternatives

1. **Infer the physical address from the data source default namespace or table name.** MySQL, Oracle and PostgreSQL do not share database/schema semantics, and Catalog visibility is authorization-dependent. Inference would publish to an address the user never confirmed.
2. **Store database and schema on the model.** That couples one logical model to one physical target and changes the model contract. A publication record is the natural place for a resolved, auditable address.
3. **Publish through the existing task scheduler as a new scheduled target type.** The current scheduler accepts only published task definitions. A metadata or DDL plugin, admission model and recovery semantics are a larger change than this proposal.
4. **Implement scheduled full-datasource metadata crawling now.** The Catalog table listing is bounded, lacks a stable cursor and has no run identity. One-table publication collection is the smallest useful snapshot and leaves crawling to a later proposal.
5. **Implement lineage in the same proposal.** There is no dependency graph and no persisted data-sync lineage yet. A physical snapshot alone cannot verify upstream and downstream relationships.
6. **Support ALTER and DROP publication now.** The existing controlled DDL path supports only limited add/alter on MySQL and PostgreSQL and is create-only on Oracle; model field replacement also makes field identity unstable. Create-or-verify is the safe first contract.
7. **Put the publisher in a new business module.** One consumer does not justify a new module boundary; reusing the modeling, datasource and JDBC dependencies avoids a reverse dependency without adding an empty layer.
8. **Bind model type to layer code again.** Layers are Workspace-configurable and custom layers carry arbitrary codes; YOIP-10 already rejected type/layer binding for the same reason.
