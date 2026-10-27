# FYOIP-15: Data Map — Metadata Catalog

Status: Discussion

Discussion: https://github.com/gitfortian/yak-ops/pull/40

Decision: Pending (link to the recorded decision after review)

Implementation: N/A until accepted

Release: N/A until released

Related FLIPs: N/A

## Motivation

FYOIP-11 shipped physical publishing for logical models and, with it, one metadata snapshot per publication. That proposal closed the chain from a model to a real table and deliberately stopped there: Proposed Changes §4 records that full-datasource crawling is out of scope because "the current Catalog table listing is bounded and lacks a stable cursor, so a whole-source crawler would need separate pagination, run identity and failure semantics before it could be safe", and Rejected Alternatives 4 declines to build crawling in the same change.

The product consequence is that a Workspace sees physical metadata only for tables it published through 数据建模. A table created by data sync, by another team's DDL, or before the platform existed is invisible in the UI: `DataSourceCatalogController` can list it live, but nothing is persisted, so it cannot be searched, classified, described or assigned an owner, and browsing a database starts from an empty session every time. The only persisted metadata the repository owns is bound to a publication and cascades with it, so it cannot answer "what tables exist in this Workspace".

The product this module is measured against — DataWorks 数据地图 — is an asset catalog over physical tables. Its one part this proposal does not take is lineage: there is no dependency graph, and nothing records which task wrote which table, which is the same reason FYOIP-11's Rejected Alternatives 5 gives for delaying it.

| Capability | DataWorks 数据地图 (分析结果) | Current state |
| --- | --- | --- |
| Search by table name, field name and description | Yes | Absent |
| Category navigation for assets | Yes | Absent |
| Table detail with columns, owner and description | Yes | Only for published tables, as an immutable snapshot |
| Bounded sample of table data | Yes, permission-gated | Absent |
| Favourites, recent visits, hot tables | Yes | Absent |
| Crawl management with per-run outcomes | Yes, scheduled | Only one table per publication, triggered by publishing |
| Lineage | Yes | Out of scope for this proposal |

Five current facts shape the design; each one is a constraint this proposal works with rather than changes.

- The metadata database is MySQL. Every migration under `yak-ops-dao/src/main/resources/db/migration/yak-ops/` targets InnoDB with `utf8mb4_unicode_ci`; there is no `JSONB`, and no table uses a native `JSON` column. `V4__v1_6_0.sql` already records why `database` and `schema` become `database_name` and `schema_name`: both are reserved words on the engines the metadata database may run on.
- Identities are 64-character application-generated strings (`IdUtils.nextId()`), not snowflake `BIGINT`s. Every table `V2` through `V6` created follows that form.
- The interactive Catalog seam caps a table listing at 500 rows (`DataSourceCatalogQueryDTO.limit`, re-capped in `AbstractJdbcCatalog.listTableInfos`) and has no cursor. The connector can enumerate a namespace without a limit when `limit` is null, and the business seam never exposes that.
- Every Catalog operation opens and closes its own physical connection (`AbstractJdbcCatalog` documents this on `close()`); there is no pooled connection to a user datasource.
- The Catalog seam resolves the caller's Workspace from `WorkspaceContext`, a request-scoped `ThreadLocal`. A background thread has no such context unless it binds one.

## Public Interfaces

### Product behavior

| Item | Contract |
| --- | --- |
| Placement | A new top-level product `数据地图`, independent of `数据建模`. Its navigation is 数据总览, 资产目录, 元数据采集 and 我的数据. |
| Assets | An asset is a physical table or view observed in a datasource namespace. System schemas are not filtered by engine: the user chooses the database and schema to crawl, and whatever that namespace returns is what the map holds. |
| Crawl trigger | Manual only. The user picks a datasource, a database and, where the engine has one, a schema. Submission records a `PENDING` run and returns immediately; collection happens on a background worker. |
| Crawl scope | One run covers exactly one datasource database (and schema). A second run for the same **datasource** is refused while one is `PENDING` or `RUNNING`, by a unique index rather than by a pre-check. |
| Crawl ceiling | A namespace of 2,000 tables or more is refused before any column is read. The connector's own unbounded enumeration is reused; nothing is truncated silently. |
| Crawl outcome | `SUCCEEDED` means enumeration completed and every observed table either refreshed or recorded a per-table failure. A run-level failure (connection lost, ceiling reached, 30-minute budget, process restart) is `FAILED`. |
| Removal detection | A table absent from a **complete** run — `SUCCEEDED` with zero per-table failures — becomes `MISSING`. A run with any per-table failure marks nothing missing. |
| Classification | A table may be assigned to one **data domain** of 数仓规划, or to none. There is no separate catalog tree, and `未分类` is the absence of a domain, not a row. |
| Curated fields | Business description, accountable owner and domain are user-owned. Tags are user-owned. A crawl never writes any of them. |
| Preview | At most 100 rows and 5 seconds, both fixed in the server. Refused on a datasource declared `PROD`. Each call is logged with its caller and asset, and the response carries a fixed privacy notice. |
| Personal surfaces | Favourites and recent visits are per Workspace member. A visit is recorded by an explicit client call, not by reading the detail page. |
| Access | Every Workspace member may browse, crawl, classify and tag. There is no per-asset or per-datasource permission in this release, because datasources do not have one either; inventing a second authorization model here would disagree with the one the Catalog seam already enforces. |
| Deletion | Deleting a datasource deletes its assets, their curated text and their history. The datasource delete dialog must say so. |

### REST

New endpoints under `/api/v1/data-map`, all Workspace-scoped through the existing interceptor:

| Method and path | Purpose |
| --- | --- |
| `GET /overview` | Asset count, managed datasource count, tables first seen in the last 7 days, unclassified count and the ten most viewed tables. |
| `GET /tables` | Search and browse: `keyword`, `datasourceId`, `database`, `schema`, `domainId`, `unclassified`, `tagId`, `includeMissing`, `page`, `pageSize`. |
| `GET /tables/{tableId}` | One asset with its columns, tags, domain and datasource name. |
| `PUT /tables/{tableId}` | Replace the curated description, owner and domain. Absent or null domain clears the classification. |
| `GET /tables/{tableId}/changes` | Crawl-detected changes, newest first, capped at 100 rows per call. |
| `GET /tables/{tableId}/preview` | Bounded sample: column names, types, at most 100 rows, a `notice` warning about personal data and why the sample stopped (`ROWS` or `SIZE`). |
| `PUT /tables/{tableId}/tags` | Replace the asset's tag set with the submitted tag ids. |
| `POST` / `DELETE /tables/{tableId}/favorites` | Add or remove the caller's favourite. Both are idempotent. |
| `POST /tables/{tableId}/views` | Record one visit for the caller on this asset. |
| `GET /favorites` | The caller's favourites, newest first, paged. |
| `GET /recent` | The caller's assets, most recently viewed first, paged. |
| `GET` / `POST /tags`, `PUT` / `DELETE /tags/{tagId}` | List, create, rename and delete Workspace tags. Deleting a tag detaches it from every asset. |
| `GET /crawls` | Runs for the Workspace, newest first, filterable by datasource. |
| `POST /crawls` | Submit a run and return the `PENDING` record. |
| `GET /crawls/{crawlId}` | One run with its counters and up to 200 failed tables. |

Request bodies use the existing `Result` envelope. `GET /tables` returns `PagingData`. No existing endpoint is renamed, removed or changes shape.

New error codes, in a new `io.yak.ops.common.enums.datamap` package:

| Code | Use |
| --- | --- |
| `19701 CRAWL_SCOPE_INVALID` | The datasource, database or schema cannot be resolved for this Workspace. |
| `19702 CRAWL_BUSY` | The datasource already has a pending or running run, or the executor queue is full. |
| `19703 CRAWL_TOO_LARGE` | The namespace reaches the 2,000-table ceiling. |
| `19704 PREVIEW_FORBIDDEN` | The datasource is declared `PROD`. |
| `19705 PREVIEW_TIMEOUT` | The sample exceeded the 5-second statement timeout. |
| `19706 INVALID_DOMAIN` | The chosen domain does not belong to this Workspace. |
| `19707 INVALID_OWNER` | The chosen owner is not an active Workspace member. |
| `19708 INVALID_TAG` | The tag name is blank or longer than 64 characters. |
| `19709 DUPLICATE_TAG` | The tag name already exists in this Workspace. |
| `19710 SEARCH_TOO_FREQUENT` | The caller exceeded the search rate limit. |
| `19711 PREVIEW_BUSY` | The node already has the maximum number of preview queries in flight. |

Unknown assets, runs and tags reuse `RESOURCE_NOT_EXISTS`. Field lengths are enforced by Bean Validation, not by a coded error.

### Persisted formats

`V7`, the next release migration, is additive: nine new tables and no change to any existing table.

`yak_ops_data_map_crawl` (one row per run, moved through its state machine)

- `id`, `workspace_id`, `datasource_id`, `datasource_revision` `BIGINT`
- `database_name`, `schema_name` `VARCHAR(128) NOT NULL DEFAULT ''`
- `status` `VARCHAR(16) ascii_bin` — `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`
- `active_scope` `TINYINT NULL` — `1` while `PENDING` or `RUNNING`, `NULL` once terminal
- `table_count`, `column_count`, `added_count`, `updated_count`, `removed_count`, `failed_count` `INT NOT NULL DEFAULT 0`
- `error` `VARCHAR(1000) NULL`, `created_time`, `started_time NULL`, `finished_time NULL`, `create_by`
- keys: `PRIMARY KEY (id)`, `UNIQUE (workspace_id, id)`,
  `UNIQUE uk_data_map_crawl_active (workspace_id, datasource_id, active_scope)`,
  `KEY (workspace_id, datasource_id, created_time)`
- `FOREIGN KEY (datasource_id) → yak_ops_data_source (id) ON DELETE CASCADE`

`active_scope` exists only to make "one active run per datasource" a constraint instead of a convention. InnoDB treats `NULL`s as distinct in a unique index, so every finished run coexists with every other and exactly one active run can hold a datasource — the same NULL-distinctness that Rejected Alternatives 5 calls a bug, used deliberately.

`yak_ops_data_map_crawl_failure` (append-only per-table failure)

- `id`, `workspace_id`, `crawl_id`, `table_name` `VARCHAR(128)`, `error` `VARCHAR(1000) NOT NULL`, `created_time`
- `FOREIGN KEY (crawl_id) → yak_ops_data_map_crawl (id) ON DELETE CASCADE`

`yak_ops_data_map_table` (current state, one row per physical table)

- `id`, `workspace_id`, `datasource_id`, `database_name`, `schema_name` `VARCHAR(128) NOT NULL DEFAULT ''`, `name` `VARCHAR(128)`
- observed: `table_type` `VARCHAR(32) NULL`, `remarks` `VARCHAR(500) NULL`, `status` `VARCHAR(16) ascii_bin`, `first_seen_time`, `last_seen_time`
- curated: `description` `VARCHAR(2000) NULL`, `owner_id` `VARCHAR(64) NULL`, `domain_id` `VARCHAR(64) NULL`
- audit: `create_time`, `update_time`, `create_by`, `update_by`
- keys: `PRIMARY KEY (id)`,
  `UNIQUE uk_data_map_table_identity (workspace_id, datasource_id, database_name, schema_name, name)`,
  `UNIQUE uk_data_map_table_workspace (workspace_id, id)`,
  `KEY (workspace_id, name)`, `KEY (workspace_id, domain_id, update_time, id)`, `KEY (workspace_id, first_seen_time)`
- `FOREIGN KEY (datasource_id) → yak_ops_data_source (id) ON DELETE CASCADE`
- `FOREIGN KEY (domain_id) → yak_ops_modeling_domain (id) ON DELETE SET NULL`
- `CHECK (status IN ('ACTIVE', 'MISSING'))`

`yak_ops_data_map_column` (current state, replaced per table)

- `id`, `workspace_id`, `table_id`, `ordinal` `INT`, `name` `VARCHAR(128)`,
  `type_name` `VARCHAR(128)`, `jdbc_type` `INT`, `size NULL`, `scale NULL`,
  `nullable` `BIT(1)`, `primary_key` `BIT(1)`, `primary_key_position NULL`, `remarks` `VARCHAR(500) NULL`
- keys: `PRIMARY KEY (id)`, `UNIQUE (table_id, ordinal)`, `KEY (workspace_id, name)`
- `FOREIGN KEY (table_id) → yak_ops_data_map_table (id) ON DELETE CASCADE`, `CHECK (ordinal > 0)`

`yak_ops_data_map_table_change` (append-only crawl-detected change)

- `id`, `workspace_id`, `table_id`, `change_type` `VARCHAR(32) ascii_bin`, `detail` `VARCHAR(1000)`, `created_time`
- keys: `PRIMARY KEY (id)`, `KEY (workspace_id, table_id, created_time)`
- `FOREIGN KEY (table_id) → yak_ops_data_map_table (id) ON DELETE CASCADE`
- `CHECK (change_type IN ('COLUMN_ADDED', 'COLUMN_REMOVED', 'COLUMN_TYPE_CHANGED',
  'COLUMN_NULLABILITY_CHANGED', 'PRIMARY_KEY_CHANGED', 'TABLE_COMMENT_CHANGED'))`

`yak_ops_data_map_tag`

- `id`, `workspace_id`, `name` `VARCHAR(64)`, `create_time`, `update_time`, `create_by`, `update_by`
- `UNIQUE uk_data_map_tag_name (workspace_id, name)`, `UNIQUE (workspace_id, id)`

`yak_ops_data_map_table_tag`

- `workspace_id`, `table_id`, `tag_id`, `create_time`, `create_by`
- `PRIMARY KEY (table_id, tag_id)`, `KEY (workspace_id, tag_id)`
- `FOREIGN KEY (table_id) → yak_ops_data_map_table (id) ON DELETE CASCADE`,
  `FOREIGN KEY (tag_id) → yak_ops_data_map_tag (id) ON DELETE CASCADE`

`yak_ops_data_map_favorite`

- `workspace_id`, `user_id`, `table_id`, `created_time`
- `PRIMARY KEY (workspace_id, user_id, table_id)`, `KEY (workspace_id, user_id, created_time)`
- `FOREIGN KEY (table_id) → yak_ops_data_map_table (id) ON DELETE CASCADE`

`yak_ops_data_map_view`

- `workspace_id`, `user_id`, `table_id`, `view_count` `BIGINT NOT NULL DEFAULT 1`, `first_viewed_time`, `last_viewed_time`
- `PRIMARY KEY (workspace_id, user_id, table_id)`, `KEY (workspace_id, user_id, last_viewed_time)`,
  `KEY (workspace_id, table_id)`, `CHECK (view_count > 0)`
- `FOREIGN KEY (table_id) → yak_ops_data_map_table (id) ON DELETE CASCADE`

Every table carries `ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci` explicitly and a comment on every column, as `V4` does. No table is added to `yak_ops_modeling_*`; the `data_map` segment keeps the new domain distinguishable in the schema.

### Frontend internal contract

- `service/data-map/` owns the endpoints and types, one file per surface (`catalog.ts`, `crawl.ts`, `personal.ts`, `tags.ts`), following `service/data-modeling/warehouse-planning/`.
- `app/data-map/` owns five routes, all under the governance prefix: `/data-governance/data-map/overview`, `/data-governance/data-map/catalog`, `/data-governance/data-map/tables/:tableId`, `/data-governance/data-map/crawls`, `/data-governance/data-map/personal`. The directory stays `app/data-map/`; only the URL names the parent, so a later 数据质量 module adds a sibling prefix instead of moving this one. `/data-governance/data-map` redirects to `/overview`, as `/operations` already redirects to its first page.
- The REST paths stay `/api/v1/data-map`. An API path names a resource domain; the URL prefix names a navigation group. Renaming the API to mirror a menu would make every endpoint move the next time the menu does.
- The category tree reuses `GET /api/v1/data-modeling/warehouse-planning/tree` and renders only `DOMAIN` nodes plus a fixed `未分类` entry, the same way `planning-tree.tsx` renders the planning tree. No new tree component is introduced; Yak UI has none.
- The detail page is a route, not a dialog, so an asset is linkable. Its tabs are 基础信息, 字段, 预览 and 变更记录; columns arrive with the detail payload.
- The crawl dialog reuses the datasource list plus `DataSourceCatalogController`'s existing databases and schemas endpoints, and the owner picker reuses `GET /api/v1/data-modeling/warehouse-planning/owners`. No picker endpoint is added by this proposal.

## Proposed Changes

### 1. The catalog is current state; the publication snapshot stays an audit record

FYOIP-11's `yak_ops_modeling_metadata_table` is a child of one publication: append-only, immutable, cascading with the publication. A catalog needs the opposite properties. An asset must keep one identity across crawls so a description, an owner, a domain and tags stay attached to it; it must survive the deletion of whatever created it; and it must be updatable in place.

The two therefore stay separate, and this proposal does not merge them. A published table appears in the map when its namespace is crawled, like every other table. Merging would require either a second writer into the catalog (rejected below) or a catalog row per publication, which is the snapshot model this proposal exists to replace.

### 2. Enumeration, run identity and failure semantics

These are the three properties FYOIP-11 named as the blockers for crawling, and each is answered explicitly rather than inherited.

**Enumeration.** The 500-row cap on `DataSourceCatalogService.listTables` is a safety bound for an interactive picker, not a crawling primitive: a namespace with 800 tables would be read as 500 with no way to ask for the rest, and no stable order to page by. The proposal adds one method to that seam — `List<DataSourceCatalogTableVO> listAllTables(datasourceId, database, schema)` — which delegates to the connector's existing unbounded `listTableInfos(database, schema, null, null)` and returns the whole namespace. The connector contract is not changed. The crawler refuses a namespace of 2,000 or more tables before reading any column, so the ceiling bounds collection work, not the enumeration itself: names are small, and materialising them is bounded by what one database schema can hold.

Both methods identify a table the way the connector already does, so this module's identity is a projection of the one the rest of the platform uses rather than a second vocabulary: `database_name` holds `TableId.catalog()`, `schema_name` holds `TableId.schema()`, and `name` holds `TableId.table()`. That is not an analogy — `MySqlJdbcDialect.qualifiedTable` renders `catalog.table` and `PostgresJdbcDialect` renders `schema.table` from the same record, so the MySQL database is the catalog slot and the PostgreSQL schema is the schema slot, and the two columns together are exactly the two selectors `JdbcCatalog.listTables(database, schema)` accepts. Writing a qualified name into any one of these three columns is what makes an identity unqueryable, and section 10 refuses it. The key those three columns form is tabular by construction — `(database_name, schema_name, name)` cannot express an object-store path or a topic name — so a second asset kind needs its own identity and a rebuilt unique key, in the same way Rejected Alternatives 16 describes for a fourth `TableId` component. What it does not need is a second catalog: the core row, and with it the domain, the tags and the favourites, stays shared, which is what the extension table in Rejected Alternatives 17 buys.

Column reads reuse `listColumns` unchanged. That means one physical connection per table, opened and closed per call, which is the load this proposal is willing to place on a user datasource: at most one crawl per datasource (the scope lock below), one connection at a time inside it, and at most 2,000 tables per run. A batched column read would be a second connector contract for one caller, and is not part of this change.

**Run identity.** A run is a row, not a thread. Submission inserts `PENDING` with the datasource revision pinned, then hands the id to a dedicated executor. The worker claims the row with `UPDATE … SET status = 'RUNNING' WHERE id = ? AND status = 'PENDING'`; a zero-row update means somebody else owns it and the worker returns.

The worker binds the run's Workspace with `WorkspaceContext.bind` and clears it in a `finally`. Binding is not an authorisation decision: membership was checked when the run was submitted, and the Workspace on the row is the one that check authorised. Without the binding, the Catalog seam — which reads the Workspace from that `ThreadLocal` — would fail on the worker thread.

**Concurrency.** Two limits are needed here, and the thread pool is neither of them. The first is one active run per datasource, which is a data constraint: `active_scope` sits inside `UNIQUE (workspace_id, datasource_id, active_scope)`, so a second submission for a busy datasource is refused by the index. A pre-check would not do — two simultaneous submissions both read "no active run", both insert, and both crawl the same database — so the losing insert takes the duplicate-key violation and returns the same `CRAWL_BUSY`, only without the window.

The second limit is throughput, which is what the executor is for: a dedicated `ThreadPoolTaskExecutor` with **two** workers and a queue of 64, never a shared `@Async` pool, because a crawl must not compete with request threads. An earlier draft used one worker, and that is wrong for a reason worth recording: a target database that stops answering holds the only worker for the whole 30-minute budget and takes every other datasource down with it, which turns one slow source into a product outage. Two workers cost one quarantined worker instead. Because a datasource can hold only one active run, two workers can only ever be pointed at two **different** datasources, so widening the pool does not widen the load on any one target database — the scope lock is what makes the extra worker safe, not a per-datasource mutex in the scheduler. Both numbers are configuration with these defaults. A full queue rejects the submission with `CRAWL_BUSY` and marks the row `FAILED` in the same statement that releases `active_scope`.

The budget is in-band: it is checked between metadata calls, so a socket that has already stopped responding is not interrupted by it. Where the driver supports JDBC 4.1 `setNetworkTimeout`, the crawl connection carries the remaining budget (MySQL does; a driver that throws `SQLFeatureNotSupportedException` is logged and the run continues). Where it does not, a wedged run is bounded by the driver's own socket timeout and otherwise holds one of the two workers, which is the failure the pool is now sized to absorb.

Startup marks every `PENDING` or `RUNNING` row `FAILED` with "interrupted by application restart" — **and releases `active_scope` in the same statement**. A sweep that only changed `status` would leave the lock behind and make every datasource it touched permanently un-crawlable, with no UI path to clear it, which is why the recovery is one statement rather than two that a later edit can forget. It also has to complete before the process accepts a crawl submission: recovery running concurrently with submission turns a stale lock into a spurious `CRAWL_BUSY` for the first seconds after every restart. The sweep runs unconditionally on every start, so a process killed at any point — including between the claim and its terminal write, and including during the sweep itself — is recovered by the next start.

This is sound only because the product does not support multi-node execution — the repository makes no clustered-execution claim — and it is recorded here so that a future clustered deployment revisits it rather than silently losing runs.

The other three terminal paths follow the same rule: success, run failure and queue rejection clear `active_scope` in the statement that writes the terminal status, never in a following one. The test plan pins all four.

Each table is written in its own transaction through a `TransactionTemplate`, never one transaction per run. A 2,000-table run in a single transaction would hold a metadata-database transaction for minutes and would make every partial result invisible if the run died. Per-table transactions also make the run resumable in the only sense this proposal claims: re-running a scope converges, because every write is an upsert on the natural key.

Inside that transaction the order is fixed: read the table's current `column` rows, compare them with the metadata just read from the datasource, write the `table_change` rows for what differs, and only then delete and re-insert the columns. The comparison has to precede the delete — afterwards the baseline is gone — and the baseline read belongs inside the same transaction, so it cannot be a row set another writer changed in between. The change rows must also be written on the caller's transaction, never through a `REQUIRES_NEW` boundary or a separately proxied method: a nested transaction that commits on its own survives the rollback of the column replacement and leaves a change log describing an update that never happened. The scope lock makes a second writer impossible today; the ordering is what keeps change detection correct if that ever loosens, and the test for it is behavioural: an engine type change must produce exactly one `COLUMN_TYPE_CHANGED` row, not an `ADDED`/`REMOVED` pair, which is what a diff against the wrong baseline produces.

**Failure semantics.** A table whose columns cannot be read gets a `crawl_failure` row, increments `failed_count`, and is skipped; the run continues. This keeps one bad table from discarding the collection work of a thousand good ones, and it keeps the failure visible per table instead of collapsing it into a single run error.

The run is `FAILED` — not `SUCCEEDED` with failures — when enumeration fails, the ceiling is reached, the 30-minute wall-clock budget expires, or the process stops before the run finishes. The last case is resolved by the startup sweep rather than at shutdown, because a stopping process may not get to write anything. In those cases `removed_count` is zero and nothing is marked `MISSING`, because an incomplete run cannot distinguish "the table was dropped" from "the run never reached it".

A complete run marks `MISSING` exactly the tables in its scope whose `last_seen_time` is older than the run's `started_time`. A table that reappears in a later run returns to `ACTIVE` on the same row, keeping its curated metadata: identity is the physical name, so a drop and a recreate is a reappearance, while a rename is a new asset and the old row stays `MISSING`. That is a real limitation, stated rather than papered over.

### 3. Observed and curated metadata never share a column

The draft rule "a crawl must not clear a description the user wrote" is usually implemented as a merge: read the existing row, keep the user's value when the observed value is empty, and write back. That rule needs a test for every field, and it fails the first time someone adds a field and forgets the rule.

This proposal makes it structural instead. `yak_ops_data_map_table` splits its columns by writer: `table_type`, `remarks`, `status`, `first_seen_time` and `last_seen_time` are written only by the crawler; `description`, `owner_id` and `domain_id` are written only by `PUT /tables/{tableId}`; the tag, favourite and view rows are separate tables the crawler never touches. The crawler's `UPDATE` names `table_type`, `remarks`, `status`, `last_seen_time` and the audit `update_time`, and cannot reach a curated column, so there is no merge rule to get wrong, no ordering dependency between a crawl and an edit, and a unit test can assert the column set.

The two writes also do not conflict at the row level: a crawl and a user edit may land in either order, and neither loses the other's field. That is why the table has no `revision` column — a revision bumped by every crawl would invalidate open edit forms for a field the crawler did not touch, which is the failure mode optimistic locking is meant to prevent, not cause.

Column-level descriptions are not curated in this release. The engine's column comment is collected as `remarks`; a user-maintained column description would need its own table, because replacing a table's columns on every crawl would delete a description stored on those rows. That is a V1.1 addition, not a hidden gap.

### 4. Classification reuses the planning data domain

`数仓规划` is a fixed three-level common tree (业务分类 → 数据域 → 业务过程) plus a two-level application tree, implemented as one table per level and assembled in `PlanningServiceImpl`. The only level that classifies a physical table without overreaching is the **data domain**: a category is too coarse, and a process only exists for tables a model already claims.

So an asset carries a nullable `domain_id`, and `未分类` is `domain_id IS NULL` rather than a synthetic node. This keeps one classification source, needs no new tree, and makes "how much of this domain is mapped" answerable by a `GROUP BY`. The picker reads the existing planning tree and filters `DOMAIN` nodes; the write validates that the domain belongs to the caller's Workspace, because the foreign key alone cannot (domain ids are globally unique strings, so the constraint is safe but not Workspace-aware, exactly as `V4` records for its publication-to-model key).

The foreign key is single-column with `ON DELETE SET NULL`, not the composite `(workspace_id, id)` form the modelling tables use. A composite key would have to null `workspace_id`, which is `NOT NULL`, so the delete would fail at runtime; a single-column key with `SET NULL` instead expresses the intended behaviour — deleting a data domain unclassifies its assets — and needs no change to the modelling delete path, which would otherwise have to ask this module whether a domain is in use and would create the dependency cycle section 8 avoids.

That silence is the one place `SET NULL` could surprise someone: a user tidying up planning has no way to see that the domain they are deleting classifies forty assets. The confirmation therefore states the number. `delete-dialog.tsx` gains one sentence — "当前该数据域下约有 N 张资产，删除后它们将变为未分类" — read from `GET /api/v1/data-map/tables?domainId=…&pageSize=1` and its `total`, the same capped count the catalog page shows, so no endpoint is added for it. The word 约 is deliberate: the count is read when the dialog opens and cannot reflect tables classified while the user is deciding, so the sentence describes the moment rather than promising a total the delete will match. That count covers non-`MISSING` assets, which is what a user means by them, even though a `MISSING` one is unclassified by the delete as well. The count informs and does not block: refusing the delete would put a usage check on the modelling path, which is the coupling this section exists to avoid. If the call fails the dialog keeps its current text, so a data map outage cannot prevent a planning delete.

### 5. Preview is bounded by construction, and production is refused

The sample is `SELECT * FROM <qualified table>` executed with `Statement.setMaxRows(100)`, `setQueryTimeout(5)` and `setFetchSize(100)` on a read-only connection. There is no `LIMIT` in the SQL: Oracle does not accept it, and dialect-specific row limiting is a second type-mapping problem for no benefit, because `setMaxRows` is the JDBC contract for exactly this. The table is never taken from the request — only a `tableId` is — and its qualified name is rendered by `JdbcDialect.qualifiedTable`, so no user string reaches the SQL text.

Values are read by JDBC type, under two budgets, because two different things can inflate a sample. Binary, BLOB and CLOB columns are reported with a placeholder and never materialised: `getBytes` or `getString` on a large object loads it whole, and a preview must not turn a 1 GB BLOB into heap. Text — a narrow `VARCHAR` as much as a `LONGTEXT` — is read through `getCharacterStream`, and the first 200 characters are taken from that stream rather than from a `String` that was already allocated. Truncating after `getString` bounds what the UI shows, not what the driver loaded, which is the hole a per-cell rule alone leaves open.

The second budget is per response: 1 MB of accumulated text, checked while cells are built. When it is spent the sample stops, `truncated` reports `SIZE` instead of `ROWS`, and the UI says the sample was cut short rather than pretending it is complete. Row width belongs to the user's table, not to this proposal: a hundred rows of fifty `VARCHAR(2000)` columns is ten million characters — twenty megabytes once they are heap `String`s — which no per-cell cap prevents.

A budget alone does not bound the driver, though, and this is the part an earlier round of this proposal got wrong. A `Statement` that is not in streaming mode buffers the whole result set before the first `next()` returns, so by the time the budget sees its first cell the twenty megabytes are already resident and the budget only bounds the response. The preview therefore asks the dialect for its streaming hint and reads no further than the budget allows. The hint is per-dialect because only some drivers have one: MySQL streams on `setFetchSize(Integer.MIN_VALUE)`, PostgreSQL cursor-fetches on a positive fetch size with autocommit off, Oracle already prefetches in batches. Where a driver has no such mode, the row cap and the LOB placeholder are what remain, and the budget is counted rather than assumed. Stopping mid-stream is safe here rather than a leaked cursor: `DefaultJdbcConnectionProvider` builds a fresh `DriverManager` connection per operation, so the try-with-resources close discards whatever was unread instead of returning a half-consumed connection to a pool.

The third bound is concurrency, and it is not about memory. Each preview holds a request thread for up to five seconds and opens a connection to the user's database — again, no pool on that side — so the cost of the feature is set by how many tabs a user opens, which is not a property this module can defend. Four preview queries in flight per node is the cap, taken with a semaphore rather than a queue, and the fifth returns `PREVIEW_BUSY` immediately instead of waiting behind a five-second statement. The number is configuration.

`PROD` datasources are refused with `PREVIEW_FORBIDDEN`, and the UI explains why. `environment` is already a datasource field (`DEVELOP`, `TEST`, `PROD`), so no new configuration or flag is introduced. This is a policy guard, not a security boundary — a user who may edit a datasource can already mislabel its environment — but it is the right default: sampling production data should require a deliberate, separate decision with its own audit, not arrive as a side effect of installing a catalog. The draft's "allow for now, add a whitelist later" would ship the permissive branch first and make the safe behaviour the harder one to reach.

Refusing `PROD` does not make a `TEST` datasource safe: that is where copies of production data usually live, sometimes masked badly. So every preview writes one structured log line — Workspace, caller, asset id, datasource, outcome and duration, never a sampled value — and the response carries a fixed server-side `notice` that the sample may contain personal data, rendered above the grid rather than buried in a footer. "Who read this table" is asked after an incident and has to be answerable that day. A persisted access trail is deliberately not in this release: an audit table needs a retention and privacy policy that this proposal has no standing to define, and rows nobody is allowed to delete are a different problem from a catalog (Rejected Alternatives 13).

### 6. Search without a segmenter, and its stated ceiling

The metadata database is MySQL with `utf8mb4_unicode_ci`, so `LIKE` is case-insensitive for ASCII and matches Chinese substrings without a tokenizer. The proposal uses `name`, `remarks` and `description` plus an `EXISTS` subquery over columns, with `%`, `_` and the escape character escaped and an explicit `ESCAPE` clause so a literal `%` searches for `%`. Results carry the columns that matched, fetched in one batch for the page, because "why did this row match" is the difference between a search result and a guess.

A leading wildcard cannot use an index, so this is a scan of the Workspace's assets. It is honest up to roughly 10⁵ tables and 10⁶ columns; beyond that the answer is a real index — MySQL `FULLTEXT … WITH PARSER ngram`, or a search service — and that is a different contract with different failure modes (minimum token length, an index that can disagree with the table). Introducing it now would mean tuning an index for a corpus that does not exist yet, and the draft's `zhparser` reference is a PostgreSQL extension that this metadata database cannot install.

Counts are capped at 1,000 and the UI shows `1000+` past that, so a scan cannot be turned into a long count query by a broad keyword.

A scan is cheap once and expensive on repeat, and the amplifier is not the broad keyword — it is a search box that fires per keystroke, where typing eight characters costs eight scans of every asset and every column in the Workspace. Two things bound that. The input searches on a 500 ms debounce or on Enter, never per character (section 9). The service also caps one caller at five searches per second: the ceiling sits above what the debounce can produce, so it fires on a held Enter rather than on normal typing, and it answers `SEARCH_TOO_FREQUENT` with a retry hint instead of scanning. The counter is an in-process bucket keyed by Workspace and member, sound for the same reason the startup sweep is — the product is single-node (section 2) — where a shared counter would require the clustered deployment that does not exist.

A minimum keyword length would be the wrong instrument: in Chinese a single character is a legitimate query, so a floor would break the primary use case to save scans that the debounce already avoids.

### 7. Personal surfaces are bounded per member and asset

A visit is stored as one row per `(workspace, user, table)` with a counter and a last-seen timestamp, upserted. The draft's append-only `md_user_action` log with `action_type` would grow one row per page view and would need `GROUP BY` and `MAX` on every read to answer "recently visited"; the bounded row answers it from a single index. Favourites are their own table with the same key shape, because existence and a counter are different facts and one table with a nullable `action_type` would have to make the counter meaningful for favourites.

Recording a view is an explicit `POST`, not a side effect of reading the detail payload. A detail read is also triggered by a browser refresh, a back navigation and a prefetch; counting those would make the hot list a measure of navigation, not interest.

Hot tables are the ten assets with the highest summed `view_count` in the Workspace. There is no time window, because that would need a log or a per-window counter, and no size or row-count input, for the reasons in Rejected Alternatives 8.

### 8. Module ownership and dependency direction

A new module `yak-ops-business/yak-ops-business-datamap` owns the catalog, the crawler and the preview reader. It depends on `yak-ops-business-datasource` (Catalog seam and connection resolution), `yak-ops-business-modeling` (the planning tree behind classification), `yak-ops-platform` (active-member check for the owner field), `yak-ops-dao`, `yak-ops-common` and `yak-flow-connector-jdbc`. Every one of those edges points the same way as an existing one: modelling already depends on datasource and on the connector for publication.

Nothing depends on the new module except `yak-ops-boot` (controllers and assembly) and `yak-ops-bom`. In particular `yak-ops-business-modeling` does not import it, which is why section 4 chose `ON DELETE SET NULL` over a service-level usage check. Controllers stay in `yak-ops-boot` beside the existing ones; DTOs and VOs go to `yak-ops-common` under `bean/dto/datamap` and `bean/vo/datamap`; the error codes go to `enums/datamap`.

The crawler reaches user datasources only through `DataSourceLookupService.connectionOptions` and the connector's `DefaultJdbcConnectionProvider`, the same two seams `DataSourceCatalogServiceImpl` uses. Credentials are never copied onto a crawl, an asset or an API response, and the connector's existing redaction (`JdbcConnectionOptions.toString`, `SensitiveUtils.mask`) is unchanged.

### 9. Frontend surface

The navigation change is one product and one sidebar array, and the product is **数据治理**, not 数据地图. The shell has exactly two levels — `ProductLauncher` (`aria-label="全局产品一级菜单"`) and, inside it, `AllProductMenu` (`aria-label="全部产品二级菜单"`), whose left column is the group label and whose entries are products — so a governance module is a `DATA_GOVERNANCE_NAVIGATION` entry inside one `AppLayout` block whose `productLabel` is 数据治理, while `GLOBAL_PRODUCT_MENU` and `ALL_PRODUCT_GROUPS` gain 数据治理. No new layout and no shell change: `ProductSidebar` already renders the product name as a quiet header above a flat `nav` and highlights the active `NavLink`. Later governance modules join the same array, which is the whole point of the URL prefix (Rejected Alternatives 18 argues the alternative).

Two limits of the existing shell are stated here rather than discovered during review. The sidebar has no collapsible tree — `ProductSidebar` renders a flat list whose only grouping is the static `groupLabel` heading that 运维中心 uses for 任务运维 — so "数据治理 expanded and highlighted" is delivered as a permanently visible product header plus the active item's highlight, not as an expandable node; making the sidebar a tree would change the chrome of all five products and is not part of this proposal. And there is no front-end permission model to read: `ProtectedRoute` checks only that a user is signed in, and all four products are visible to every signed-in user. A per-product visibility flag for this one module would make 数据地图 the only product that can disappear while the others cannot, which is worse than the current uniformity; an access model belongs in its own proposal.

One line is easy to miss and breaks deep links silently. `AppRouter.tsx` keeps a hand-maintained `AUTHENTICATED_PATHS` set plus an `isAuthenticatedPath` prefix predicate, and `resolveReturnTo` rewrites any path it does not recognise to `/data-source`. A route added without its prefix therefore sends a signed-out user who opens `/data-governance/data-map/tables/42` back to the datasource list after signing in, with the destination discarded and no error. The prefix belongs in that predicate in the same commit as the routes. Product favourites need no migration: `product-favorites.ts` resolves a stored `itemKey` against `GLOBAL_PRODUCT_MENU` and drops ids it cannot resolve, so the new product is inert until someone stars it.

The five routes reuse Yak UI's `Table`, `Dialog`, `Tabs`, `Field` and `Input`; the category tree is hand-written like `planning-tree.tsx`, because Yak UI has no tree component and adding one is a separate design.

The catalog page is a left domain tree plus a right result table with a search box and datasource/database/tag filters above it. The search box submits on Enter or after a 500 ms pause and never per keystroke, which is the front half of the bound section 6 describes; changing a filter re-runs the same query without refetching the tree. It deliberately has **one** tree: a second datasource tree would answer the same question as the filters, and would need lazy loading and its own state for a dataset that the filter already narrows. The detail page is a route so it can be linked and bookmarked; 基础信息 carries the editable description, owner, domain and tags, 字段 renders the columns collected with the asset, 预览 loads on demand, and 变更记录 pages the change rows.

The tree is fetched once per mount through the app's existing `QueryClient` with a five-minute `staleTime`, so switching between 资产目录 and an asset's detail page, or changing a filter, does not refetch it. Its size is bounded by the planning taxonomy — tens of domains in one Workspace — not by the catalog, which is why it is eager; the volume that would justify lazy loading is domains in the hundreds, and that is a planning-side shape to fix in planning rather than a reason to fork the taxonomy here (Rejected Alternatives 14).

A `MISSING` asset is shown rather than hidden: it is excluded from the default list, and both the filter chip and the detail banner say what it means — the table is no longer present in its datasource, this usually means it was dropped or renamed, and after a rename the new name arrives as a **new** asset whose description and owner must be copied by hand. Saying that plainly is the whole remedy this release offers; copying it automatically would invent an identity nothing observed (section 10).

The planning delete dialog gains the asset count of section 4. It is the only file this proposal touches outside the new product, apart from the two registration files section 9 names — `navigation.ts` and `AppRouter.tsx` — where the diff is one product constant, one navigation array and one route block.

### 10. What an implementation must not add

Each item below is a decision this proposal already made, so adding it is not a refactor but a different product that needs its own proposal. They are listed here because every one of them is the obvious next thing to build:

1. **Incremental crawling or change data capture.** Every run enumerates its whole namespace and upserts on the natural key (section 2). An incremental mode would trust a watermark the target database does not offer, and the catalog would then be missing tables it cannot explain.
2. **A repair, restore or bulk-fix action for `MISSING` assets.** `MISSING` clears only when a later complete run observes the same name (section 2). A manual restore would let an asset claim a presence nothing observed.
3. **Row counts, storage sizes or any other engine statistic presented as an asset fact**, including a "total storage" card (Rejected Alternatives 8).
4. **Any condition, filter, sort or free SQL on the preview query.** The preview is `SELECT *` with a row cap (section 5); a `WHERE` box is an arbitrary-query console with a different authorisation and cost model.
5. **A second writer into the catalog.** Publication, data sync and DDL all reach the map by being crawled, never by writing to it (sections 1 and 3), because a second writer is what makes the merge rule return.
6. **A permission model per asset or per datasource, or a share link that bypasses the Workspace.** Access is the Workspace, as it already is for datasources.
7. **A crawl scheduler, or a "crawl every datasource" button** that turns one deliberate action into a fan-out nobody sized (Rejected Alternatives 6).
8. **A per-user crawl quota or a general query cost model.** Section 6's limiter is a denial-of-service guard for an unindexed scan, not an allocation mechanism; a quota system belongs beside real full-text search in a later release, and adding one now would charge the product's most-used screen against a budget nobody has defined.
9. **A qualified name folded into `database_name` — `paimon_hive.ods`, `db.table`, `catalog/schema` — or a run whose scope spans more than one namespace.** The identity is three columns because the connector's `TableId` has three components, and one of them is already the catalog (section 2). A dotted value is unsearchable by the column that holds it, unfilterable by the tree that renders it, and unrenderable by `qualifiedTable`, which quotes each component separately. One run covers one namespace: a run that walked several would have no single scope to compare `last_seen_time` against, and `MISSING` would be decided by whichever namespace finished last.
10. **A second asset kind — an `asset_type` column, Kafka topics, files, API endpoints — in this release.** The seam enumerates JDBC tables and views, `table_type` already carries what the engine reports, and an asset that no crawl can observe would be a row whose `status` means nothing. A topic has no columns and no JDBC preview, so what differs needs its own contract; what does not differ — identity, description, owner, domain, tags, favourites — is why the eventual shape is a core row plus a 1:1 extension rather than a table of its own (Rejected Alternatives 17).
11. **A collapsible navigation tree, or a per-product visibility flag, built here to make the governance hierarchy look deeper than the shell supports.** The sidebar is flat for all five products and a tree created for one of them changes the chrome of the other four; a visibility flag with one participant is a permission system whose rules nobody has written. Section 9 states what the model does support, and Rejected Alternatives 18 records the alternative that was considered.

## Compatibility, Deprecation, and Migration Plan

- `V7` is additive and forward-only. A fresh install applies `V1` through `V7`; an upgrade from 1.8.x applies `V7` after `V6`. No existing table, column or row is touched, and no backfill is needed because the catalog starts empty.
- Existing REST endpoints, task definitions, published models, publication records and metadata snapshots are unchanged. Publication collection keeps writing its own snapshot tables; nothing reads them differently.
- One existing behaviour changes: deleting a datasource now also deletes its catalog rows, including curated descriptions, owners, domains and tags. The datasource delete dialog must state this before the action. Modelling rows are unaffected, and datasource deletion already requires no dependent task.
- Deleting a data domain now unclassifies any asset assigned to it. No modelling service or endpoint changes; the planning delete dialog gains one informational sentence (section 4), which is the only edit outside the new module.
- Nothing is deprecated. No compatibility layer is introduced.
- No downgrade path is provided, consistent with previous releases; restoring the pre-upgrade backup is the supported rollback.

## Test Plan

- Unit tests cover the crawl state machine (claim by conditional update, restart sweep to `FAILED`, queue rejection to `FAILED`), the ceiling and the 30-minute budget, per-table failure recording and its effect on `MISSING`, the `MISSING`/`ACTIVE` transition on reappearance, and change detection across all six change types with first observation producing no rows.
- The scope lock is tested at the database, not through the service: a second insert for a datasource with an active run must fail on `uk_data_map_crawl_active` even when the pre-check is bypassed, and each of the four terminal paths — success, run failure, queue rejection, restart sweep — must leave `active_scope` `NULL`, because a leaked lock makes the datasource un-crawlable with no UI remedy. The sweep is tested as a restart, not as a method call: seed a `RUNNING` row with `active_scope = 1`, start the context, then assert the row is `FAILED` with the lock released **and** that a submission for that datasource is accepted immediately, which is what fails if recovery runs after submissions are admitted.
- Two workers are asserted to run two different datasources concurrently and to be incapable of running the same one twice, which is the property that lets the pool be wider than one.
- One structural unit test asserts that the crawler's update statement names only observed columns plus the audit `update_time`, and that the curated update names only the three curated columns. This is the test that keeps section 3 true after a later field is added.
- Change detection is tested on its baseline, not only on its output: a type change (`varchar(50)` to `varchar(100)`) must produce exactly one `COLUMN_TYPE_CHANGED` row and no `ADDED`/`REMOVED` pair, which is what a diff taken after the delete, or outside the write transaction, produces instead.
- Search tests cover `%` and `_` as literals, case-insensitive matching under `utf8mb4_unicode_ci`, Chinese substring matching, matched-column reporting, the 1,000 count cap, the `includeMissing` filter, and six searches inside one second from one caller returning `SEARCH_TOO_FREQUENT` on the sixth.
- Preview tests cover the 100-row cap, the per-cell truncation, the placeholder for binary and LOB columns without reading them, the 5-second timeout mapping to `PREVIEW_TIMEOUT`, `PROD` refusal, the fixed `notice` field, an audit log line carrying the caller and asset and no sampled value, and a `tableId` for a `MISSING` asset returning `RESOURCE_NOT_EXISTS`. The byte budget is tested with a fixture of wide text columns whose 100 rows exceed 1 MB: the response must stop early with `truncated = SIZE`, and the test asserts the accumulated string length rather than the row count. The streaming hint is asserted structurally — the MySQL statement must be prepared with the dialect's sentinel — because its effect is on driver buffering and does not change which rows `next()` returns. The concurrency cap is tested by holding four permits and asserting the fifth call returns `PREVIEW_BUSY` without touching the datasource, then releasing one and asserting the next call is admitted.
- Database integration tests cover `V7` applied on top of `V6`, the identity unique key with `schema_name = ''` (two MySQL tables with different names are distinct, and a duplicate name is rejected — the case a nullable `schema_name` would silently allow), the same key on each populated slot (two MySQL databases holding an identically named table are distinct through `database_name`, two PostgreSQL schemas through `schema_name`), `ON DELETE SET NULL` on domain deletion, `ON DELETE CASCADE` from datasource and table deletion across columns, tags, favourites, views and changes, and Workspace isolation on every read and write including a foreign-workspace `tableId`.
- End-to-end tests run against the packaged Boot process with a real MySQL datasource: crawl a database, assert tables and columns persisted, re-run and assert no duplicates and no change rows, drop a table and assert `MISSING` only after a complete run, and assert a preview returns at most 100 rows.
- A failing case for the ceiling and for a datasource with an unreachable host must leave the run `FAILED` with `removed_count = 0`.
- Frontend behaviour checks cover the planning delete dialog rendering the asset count and falling back to its current text when that call fails, and the `MISSING` banner and filter chip wording.
- Frontend verification covers `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run architecture:check`, `npm run contract:check` and `npm run build`, plus two navigation assertions: the 数据地图 sidebar entry reaches `/data-governance/data-map/overview` and highlights itself under the 数据治理 header, and a signed-out deep link to `/data-governance/data-map/tables/:id` returns to that page after sign-in rather than to `/data-source` — the `resolveReturnTo` case section 9 describes, which is the one that fails silently.
- Manual acceptance must crawl a real database of more than 100 tables, preview a table with a text and a binary column, confirm that a description, an owner, a domain and a tag survive a second crawl unchanged, and confirm that a table renamed outside the platform appears as a new asset while the old row reads `MISSING` with its hint.

## Rejected Alternatives

1. **Reuse the FYOIP-11 metadata tables as the catalog.** They are append-only children of one publication: a table published twice has two rows, a refreshed snapshot replaces the previous one, and deleting the publication deletes the metadata. Nothing in that shape can hold a description that outlives a publication, which is the whole point of an asset.
2. **Write publication collections into the catalog when a model is published.** That gives the catalog a second writer with different semantics — a publish would create or overwrite an asset — and it makes modelling depend on this module or this module depend on modelling's publication internals. A published table is discovered by crawling like any other, and the detail page's provenance link is a later addition, not a merge.
3. **Keep a separate curated-annotation table keyed by the natural key.** It splits one asset across two rows with two lifecycles, needs orphan cleanup when a table disappears, and turns every read into a join to answer "what is this table". Disjoint column ownership in one row gives the same protection with none of that.
4. **Use `BIGINT` snowflake primary keys and a `JSONB`/`JSON` property bag, as the draft does.** Identities in this repository are 64-character strings from `IdUtils.nextId()`, the metadata database is MySQL, and no table uses a native JSON column. A JSON bag would also be unindexable and unconstrainable, repeating FYOIP-14's first rejected alternative.
5. **Keep the draft's nullable `schema_name` inside the identity unique key.** MySQL treats `NULL`s as distinct in a unique index, so two rows for the same MySQL table would both be insertable and the crawler would duplicate every table on every run. The column is `NOT NULL DEFAULT ''` for exactly this reason, and a test pins it.
6. **Schedule crawls through the existing Quartz scheduler now.** The scheduler admits published task definitions; a crawl is not a Task and has no definition, version or execution record. Adding a second job type to that contract is a larger change than this proposal, and a manual trigger is what makes the crawl's cost visible to the person paying it.
7. **Add a full-text index or an external segmenter now.** `zhparser` is a PostgreSQL extension and this database is MySQL; MySQL `FULLTEXT … WITH PARSER ngram` brings a token-size configuration, an index that can disagree with the table and a second write path, all to accelerate a corpus that does not exist on day one. The `LIKE` ceiling is stated instead of hidden.
8. **Collect row counts and storage size from engine statistics.** `information_schema.TABLES.TABLE_ROWS`, `pg_class.reltuples` and `ALL_TABLES.NUM_ROWS` are estimates that the UI would present as facts, they differ per engine, and an accurate count is a full scan of the user's table. The catalog therefore carries no size column, and the hot list is built from visits, which the platform observes itself.
9. **Build the sample by appending `LIMIT 100` to the SQL, or accept user SQL.** Appending `LIMIT` fails on Oracle and turns a policy into string building; user SQL is an arbitrary-query console, which is a different product with its own authorisation, audit and cost model.
10. **Store one success row per table per run, as the draft's crawl-detail table does.** A successful table is already visible through `last_seen_time`; storing a row per table per run grows with tables × runs and has no reader. Only failures are kept, which is the part an operator needs.
11. **Build lineage in this proposal.** There is no dependency graph and no persisted record of which task wrote which table. A catalog of physical tables cannot infer it, and any attempt would produce a picture that looks authoritative and is not.
12. **Add a second navigation tree for datasources.** It answers the same question as the datasource and database filters over one result list, and it would need lazy loading, its own expansion state and its own empty states for a set the filter already narrows.
13. **Persist a preview audit trail now.** An access table is the right long-term answer, but it needs a retention and privacy policy — how long reads of personal data are kept, who may query them, when they are deleted — that this proposal has no standing to define, and a table nobody may prune is a new operational problem, not a smaller one. The structured log line answers "who read this table" on the day it is asked; the table is the V1.1 shape once someone owns that policy.
14. **Serve the classification tree from a data-map-owned copy, with an asset count on every node.** A copy of the taxonomy disagrees with planning the moment a domain is renamed or re-parented, and per-node counts are an aggregate query plus a UI decision, not a reason to fork the source of truth. The catalog renders the planning tree it already validates against.
15. **Refuse a preview whose declared text width exceeds a threshold, reported as `PREVIEW_TOO_LARGE`.** A declared `VARCHAR(4000)` predicts nothing about the bytes stored: fifty such columns holding ten characters each is an ordinary business table and would be refused, while a single `LONGTEXT` holding a novel would be admitted. The check can only run before the read, so it is a guess about a width the budget measures directly, and the failure mode of the guess is a product that declines to show the tables people most want to see. It also returns nothing, where a partial sample with `truncated = SIZE` returns the first rows — which is what someone opening a preview asked for. Streaming, which an earlier round of this document also rejected, is adopted instead, because it is what makes the budget bound the driver rather than only the response (section 5).
16. **Add a catalog level to the asset identity now, and crawl lakehouse catalogs through `SHOW CATALOGS`.** The three columns this proposal uses are already the connector's entire namespace: `TableId(catalog, schema, table)` in `yak-ops-core`, populated from `TABLE_CAT`, `TABLE_SCHEM` and `TABLE_NAME`, with `JdbcCatalog.listTables(database, schema)` taking exactly two selectors and each dialect rendering one of them. A fourth level has nowhere to live — a Doris external catalog name would have to occupy the slot MySQL uses for its database, leaving the database beneath it no slot at all — so supporting it is not a column here but a fourth `TableId` component, a catalog enumerator on the seam, and an impact review of every reader of `TableId`, data sync's source and sink planning included. Doris is also not a datasource type: the plugins are `MYSQL`, `ORACLE` and `POSTGRE_SQL`, and FYOIP-12 recorded that "Paimon is a lake format the JDBC Catalog cannot reach" when it refused the same expansion. A column no code path can populate is not forward compatibility; it is a field whose meaning the next implementer has to guess, and which section 10 then has to police. The migration is additive when the time comes — the new component arrives defaulted and the identity key is rebuilt in the same release — so nothing is saved by carrying it early. Preconditions for reopening: a Doris datasource plugin, a catalog enumerator, and a fourth `TableId` component with its data-sync impact assessed.
17. **Reserve an `asset_type` column for non-JDBC assets, or force non-tabular assets into disconnected tables of their own.** The table already carries `table_type`, which is the engine's own `TABLE_TYPE` — `TABLE`, `VIEW`, and whatever else a vendor reports. A column called `asset_type` would hold the same information on day one, and a second answer to one question is an ambiguity rather than a head start: the first time the two disagree, someone has to decide which one the code trusts. The opposite reflex is no better — giving a Kafka topic or an object-store file a table of its own severs it from the governance every asset here shares, the domain and the tags and the favourites and the view counter, and turns each of those joins polymorphic in the kind. The long-term shape is class-table inheritance: a non-tabular asset is a core row in `yak_ops_data_map_table` carrying its identity, domain, owner, tags and favourites, joined 1:1 to a type-specific extension table (`yak_ops_data_map_file_props`, `yak_ops_data_map_topic_props`). That shape is why the column is not reserved now, not why it is never needed. Today a core row with no extension row is simply a table; but the kind is a property of the extension, so the discriminator, the extension tables and the code that reads them arrive in the one change that introduces a second kind. Until that change the column would hold a single value, and a field whose meaning the next implementer has to guess is not forward compatibility. Preview and columns are untouched in the meantime, exactly as they are today.
18. **Make 数据地图 a product of its own, listed under a 数据治理 heading in the all-products panel, instead of a sidebar item of a 数据治理 product.** This is defensible on the code's own vocabulary — `ALL_PRODUCT_GROUPS` really is a group label over a list of products, so the panel would show 数据地图 nested under 数据治理 — but it puts the parent in a panel the user opens and closes rather than in the chrome they are looking at. The sidebar header would read 数据地图, nothing on screen would say 数据治理 while browsing, and each governance module would then need its own `AppLayout` wrapping a one-item sidebar. Adding an entry to a navigation array is the cheaper seam, and the `/data-governance` prefix already carries the grouping in the URL and in every bookmark. Reopen if a governance module needs chrome the others do not share — 安全中心 with its own approval queue, say — because that is the case a shared sidebar cannot serve.
