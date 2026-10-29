# FYOIP-17: Asset Entity Registry — Unified Identity, Relations and Cross-Type Search

Status: Discussion

Discussion: Pending — record the `[DISCUSS][FYOIP-17]` proposal PR URL here before review.

Decision: Pending maintainer review of module ownership, column identity and the migration sequence.

Implementation: N/A until accepted. Phase 0 is gated on FYOIP-18 operating in production for at least two weeks after its release with no open P0 or P1 defect attributable to the registration path — the maintainer decision of 2026-10-28 forbids starting this registry on the day FYOIP-18 ships. The identity, provenance and registration states this registry carries are defined there, and this proposal's Phase 0 (stable column identity) is the precondition for `COLUMN` entities and lands before Phase 1.

Release: N/A until released

Related FLIPs: N/A. OpenMetadata's entity envelope, `fullyQualifiedName`, `EntityReference`, type registry and relation edges are design references, not claims of API compatibility.

## Motivation

Each module persists its own assets, and nothing answers "what is this thing, what relates to it, and where do I click next".

| Asset | Core table | Children | Governance today |
| --- | --- | --- | --- |
| Physical table or view | `yak_ops_data_map_table` | `yak_ops_data_map_column` | description, owner, domain, tags, favourites, presence and provenance (FYOIP-18) |
| Logical model | `yak_ops_modeling_model` | `yak_ops_modeling_model_field` | model-owned fields only |
| Metric | `yak_ops_modeling_metric_atomic` / `_derived` / `_composite` | `_modifier`, `_time_period`, `_composite_ref` | each kind owns its own identity |
| Standard field | `yak_ops_standard_field` | — | standard-owned |
| Published table | `yak_ops_modeling_model_publication` | `yak_ops_modeling_metadata_collection` / `_table` / `_field` | bound to one publication |
| API / service | none | none | nothing persists one |

Five consequences:

- **One physical table can hold two identities.** FYOIP-11 stores a per-publication snapshot in `yak_ops_modeling_metadata_*`; FYOIP-15 stores a per-physical-identity asset in `yak_ops_data_map_table`. FYOIP-18 makes the catalog the single ledger and lets a publication register into it, but the two storage shapes still coexist and no shared key relates them.
- **A field is not addressable.** `yak_ops_data_map_column` is a child row replaced per run — `DataMapCrawlWorker.persist` deletes and reinserts a table's columns — and its unique key is `(table_id, ordinal)`, so an engine-side reorder moves a column to a different key. Nothing can hold a tag, an owner, a standard mapping or an inbound relation to a column.
- **Search cannot cross kinds.** The data-map criterion matches table name, remarks and description with an `EXISTS` over column names; models, metrics and standard fields are searched by their own services with their own DTOs. There is no type facet, no shared result shape and no single entry point.
- **Drill-down is hand-built per pair.** A table detail lists its columns and nothing else. Nothing answers "which model published this", "which metric reads this field" or "which standard does this column satisfy", because there is no edge to store it in and no reference type to return.
- **Governance fields exist only for tables.** Owner, domain, tag and favourite columns live on `yak_ops_data_map_table`. Every other kind needs its own copy or has none, which is what FYOIP-15 Rejected Alternative 17 anticipated as class-table inheritance — a core row carrying identity, domain, owner, tags and favourites, joined 1:1 to a type-specific extension table.

FYOIP-15 rejected a `asset_type` discriminator on the catalog row for a specific and still-valid reason: at that time there was exactly one kind, so the column would have held one value. This proposal is the change that kind assumption anticipated. It also moves further than that precedent, because the second kind that matters — a logical model, a metric, a standard field — is not owned by 数据地图 at all, so the shared core row cannot live inside one consuming module.

## Public Interfaces

### Product behavior

| Item | Contract |
| --- | --- |
| Entity | Every registered asset has one identity row: `id`, `entity_type`, `fqn`, `name`, `display_name`, `description`, `owner_id`, `domain_id`, `lifecycle_status` together with the time it entered that state — which is what makes a retention window computable without a change log — `origin`, `origin_ref`, `origin_state`, `source_module`, `source_id`, version and audit columns. Type-specific facts stay in the owning module's table. |
| Types | First release: `TABLE`, `COLUMN`, `MODEL`, `MODEL_FIELD`. Later, each with a real source and a real page: `METRIC`, `STANDARD_FIELD`, `API`. A type with no writer and no reader is not created. |
| Identity | `id` is stable and is the only cross-reference. `fqn` is unique per Workspace and type, human-readable, and updated on rename; it is never a foreign key. Table FQN is `{datasourceId}.{database}.{schema}.{table}` using the datasource identifier, not its display name, so renaming a datasource cannot drift the FQN; the UI shows `datasourceName.database.schema.table`. |
| Status fields | Two orthogonal fields carry what FYOIP-18 defines: `lifecycle_status` (`ACTIVE`/`MISSING`) is an observation fact owned by the crawler, `origin_state` (`ACTIVE`/`ORPHANED`) is a provenance fact owned by registration, and a model-published table whose physical table was dropped is both. `origin` and `origin_ref` name the producing module and its row. |
| Visibility | No publication or approval field exists in this release. A registered entity is retrievable, subject to Workspace isolation, exactly as 数据地图 works today. The publication gate is its own proposal; when it lands it adds `publish_status` to the envelope in one forward migration, and every read path in this proposal gains one condition rather than a new model. |
| Relations | Directed edges with a type: `CONTAINS` (table → column, model → model field), `PUBLISHED_AS` (model → table), `MAPS_TO` (model field → column), later `DERIVED_FROM` and `USES_STANDARD`. Both endpoints are same-Workspace, non-deleted entities at write time. An edge whose endpoint is retired-but-retained, missing or hidden from the caller is returned as an unavailable endpoint with its reason, never repaired by deletion; an edge is destroyed only when its endpoint row is actually destroyed. |
| Search | One projection table per entity, rebuilt in the registering transaction. Search filters by type, source, owner, domain, lifecycle, origin and keyword, and returns EntityRefs only. |
| Tags | Tags and tag links move to the registry, so a tag applies to a table, a column, a model or a metric with one table. Existing table tags are re-linked during migration. |
| Detail | A generic entity page renders the envelope, the typed detail and the relation panel; type-specific pages remain the product's own surface. Every list read carries the EntityRefs its page needs in the same response: a column table, a field hover card and a relation panel are rendered from data already fetched, and none of them issues one request per row. |

### REST

| Method and path | Purpose |
| --- | --- |
| `GET /api/v1/assets?type&keyword&ownerId&domainId&lifecycleStatus&origin&page&pageSize` | Cross-type search with type and source facets; everything registered in the caller's Workspace |
| `GET /api/v1/assets/{id}` | `entity` + `detail` + `relations` + caller permissions |
| `GET /api/v1/assets/{id}/relations?direction=IN\|OUT&type&page&pageSize` | Relation traversal |
| `GET /api/v1/assets/{id}/members` | `CONTAINS` children |
| `PUT /api/v1/assets/{id}/curation` | Description, owner, domain for any type, under the owning module's rules |
| `POST /api/v1/assets/{id}/tags` | Tag assignment for any type |

Existing per-module REST and UI routes keep working and keep their current request shapes; the registry is the new cross-type surface. Whether 资产目录 becomes the entity browser and the type pages stay, or one page replaces them, is decided in this proposal's review rather than left as a permanent dual path.

### Persisted formats

New `asset_entity`, `asset_relation`, `asset_tag`, `asset_tag_link`, `asset_search_document`, all with the repository's `VARCHAR(64)` identities, InnoDB and `utf8mb4_unicode_ci`, and no native JSON where a query needs the value. `asset_relation` declares both endpoints as foreign keys to `asset_entity` with `ON DELETE CASCADE`. Type tables gain a unique `entity_id`; nothing is dropped in the same release, so the move of governance columns from `yak_ops_data_map_table` to the envelope lands together with the read path that stops using the old copy. Phase 0 carries its own forward migration on `yak_ops_data_map_column`: the sighting state the retention rule needs — a status and the time the column vanished, with the index the sweep selects through — because the retired marker has to exist before any entity can depend on it, and the envelope does not exist yet at that point.

## Proposed Changes

### 1. Registry owns identity, modules own detail

`asset_entity` is the only place a name, a description, an owner, a domain, a lifecycle or a provenance state is read from. The owning module remains the only writer of its type table, and registering an entity is one transaction with that write: a detail row without an envelope row, or the reverse, is a defect, not a state to tolerate. `source_module` and `source_id` make the mapping explicit and let a re-sync be idempotent against `uk_asset_identity (workspace_id, entity_type, source_id)`.

`properties_json` exists for low-frequency attributes only and is not queryable or constrained; any field a filter, a sort or a facet needs becomes a column. This is the same reason FYOIP-14 and FYOIP-15 refused a JSON bag as the system of record.

### 2. Module ownership

A new `business-asset` module owns the registry, the relation edge table, tags and the search projection. It depends on `common` and `dao` only; 数据地图, 数据建模 and later 服务/HID depend on it. The alternative — putting the registry inside 数据地图 — would make every module depend on a peer product module for its own identity and would magnify the single dependency direction FYOIP-18 has to respect, where 数据地图 reads modeling's publications but modeling never reads 数据地图's tables.

### 3. Column identity comes first

Phase 0 changes the crawler from delete-and-reinsert to upsert by `(table_id, name)`, with the unique key moving from `(table_id, ordinal)` to `(table_id, name)` and `ordinal` becoming an updated attribute. Until that lands, `COLUMN` entities would churn their IDs on every run and every edge attached to them would be orphaned on schedule.

**A vanished column is retired, not deleted.** The crawler marks a column it no longer observes and stamps the moment it vanished; the row, its `asset_tag_link` rows and its `asset_relation` edges — `CONTAINS` and any `MAPS_TO` — stay exactly as they are for a 30-day retention window, and nothing in the crawl path may cascade them. Absence is an observation, and a one-run absence caused by an engine hiccup, a permissions change or a metadata view is the common case, not the interesting one.

**A reappearing name revives the same identity.** When `(table_id, name)` comes back inside the window, the crawler clears the mark, refreshes the observed attributes and re-stamps the sighting on the *existing* row. The entity id does not change, so the tags, the favourites-equivalent links and the edges that were kept are simply visible again. This is also the only shape the unique key permits: the retired row still holds `(table_id, name)`, so an insert would collide with it, which makes revival a correctness requirement rather than a product preference.

**Retention is one bounded sweep.** Past 30 days the retired column row and its links are deleted, by a sweep that selects on the vanished timestamp through an index and deletes in batches outside any user transaction. The window is one number in configuration, and the sweep is the only place registry-side governance is ever destroyed.

**A rename is retirement plus creation, with no guessing.** `information_schema` reports one current snapshot per run, so a rename is physically indistinguishable from a drop plus an add, and a type-matching guess would attach one column's governance to a different column — the same refusal as Rejected Alternative 9. The consequence is surfaced where the decision is made instead: the model-field editor's 英文编码 input raises an explicit confirmation when the field being renamed is already published or already carries governance, naming what breaks — this field's own entity and its tags and `MAPS_TO` edge start over, the new name becomes a new column entity, and because FYOIP-11 publication is create-or-verify against a present table the physical column is not renamed by the platform, so the next publication of that model fails verification until a person resolves it. Confirmation is a human decision with a stated cost, which is what makes guessing unnecessary rather than merely risky.

### 4. Relations are maintained by their owners

`CONTAINS` is written by whoever writes the children, so it cannot diverge from the detail rows. `PUBLISHED_AS` and `MAPS_TO` are written by the FYOIP-18 registration path from data it already holds, and `MAPS_TO` maps a model field to a column by name at publish time with conflicts surfaced rather than guessed. Metrics and standards gain edges only when their real sources exist; `DERIVED_FROM` and `USES_STANDARD` are named here so the vocabulary is not invented later, and are not implemented in the first release.

Dangling edges are made unrepresentable rather than swept. Both endpoints carry foreign keys to `asset_entity` with `ON DELETE CASCADE`, the same device V7 uses for a table's own children, so an edge cannot outlive the row it points at and no job has to discover that case; before Phase 1 accepts, this table pair is the one place the repository's existing cascade discipline has to be re-checked rather than assumed. Reads resolve both endpoints through the caller's Workspace and return an unavailable endpoint with its reason, which is why a temporary absence is a rendering state and not a repair action: a column retired inside its 30-day window, or a table the crawler currently reports `MISSING`, keeps every edge, because deleting them would destroy the governance the retention window exists to preserve and would force the whole subgraph to be rebuilt when the object returns.

### 5. Search is a projection, not the truth

`asset_search_document` is rebuilt in the registering transaction and is droppable and rebuildable at any time. MySQL `FULLTEXT` with the list and facet queries behind one read service is the first implementation; the projection's shape, not an interface, is the seam. An external engine is adopted only if a measured corpus — 100,000 entities is the working figure — shows the projection cannot serve the page.

In-transaction rebuild is a product decision rather than an oversight. Writing one row by primary key in a second table takes no lock on the business row, and it is what makes a just-registered asset findable in the same request — the same reasoning that put FYOIP-18's inline attempt ahead of its sweep, since a publish whose table cannot be searched for one sweep interval reads as a broken platform. Bulk rebuilds and the migration backfill stay batched and outside every user transaction. If the performance measurement shows the projection write dominating a 1,000-column registration, this is the paragraph that gets revised with the number attached, and the fallback is a post-commit refresh with the rebuild sweep as the guarantee — not a switch added before the measurement exists.

### 6. Phasing

Phase 0 column identity — the upsert, the key move, the retired marker, the 30-day window and the revival branch — and it opens only after FYOIP-18 has run in production for two weeks with no open P0 or P1 defect on the registration path. Phase 1 registry, projection, tags, `TABLE`/`COLUMN`/`MODEL`/`MODEL_FIELD`, catalog and model lists reading the projection. Phase 2 relations, the entity detail route and the relation panel, with visibility enforced on every edge read. Phase 3 adds `METRIC`, `STANDARD_FIELD` and `API` on real sources, one type per change. A vertical slice of Phases 0–2 is 20–30 person-days, separate from FYOIP-18's 14–20.

## Compatibility, Deprecation, and Migration Plan

**Against accepted records.** FYOIP-15 Rejected Alternative 17 described class-table inheritance as the long-term shape and refused an `asset_type` column while only one kind existed. This proposal is the change that introduces the second kind, and it puts the core row in a shared module instead of inside `yak_ops_data_map_table`, because the second kind is not a data-map asset. FYOIP-15 Rejected Alternative 1 stays rejected: `yak_ops_modeling_metadata_*` is not a catalog and does not become an entity source. FYOIP-18's `origin`, `origin_ref` and `origin_state`, together with the presence status that lives today as `yak_ops_data_map_table.status`, move onto the envelope without changing their semantics or their REST shapes. The maintainer decision of 2026-10-28 fixes the order: FYOIP-18 is implemented, released and settled first, so those columns exist in `yak_ops_data_map_table` before this proposal writes anything, and moving them here is one forward migration. This proposal does not share a release with FYOIP-18 or absorb its work — that ordering is rejected below. The publication gate's fields are not reserved here: that proposal adds `publish_status` to the envelope as its own forward migration, and this proposal's rejection of a placeholder column is the same one FYOIP-18 records.

**Migration.** Development environment: the registry is filled from the rows that already exist — `data_map_table`, `data_map_column`, `modeling_model` and `modeling_model_field` — by a batched application job reusing each existing `id` as `asset_entity.id`. No data is cleared: FYOIP-18 no longer hides any asset behind a publication state, so the earlier decision to reset 数据地图 in development is withdrawn and every current row stays valid and resolvable. Production: the same job, batched and restartable, never one Flyway statement moving rows, because a statement that touches 100,000 rows during startup is an outage with a migration lock. Existing `id` values are reused, so bookmarks, exports and any downstream reference keep resolving.

**Retention of old columns.** Type tables keep their own name, description and owner columns only until the read paths stop using them, then those columns are dropped in a following migration; the plan states which release removes them so no permanent dual truth is created. Tag links are re-pointed to `TABLE` entities in the same change that switches the tag reads.

**Compatibility risks.** Facets and counts grow with entity volume, so the projection and its indexes are exercised at target scale before rollout. Relation, member and facet reads must resolve both endpoints through the caller's Workspace, since an edge that reaches a foreign-workspace or soft-deleted entity is a leak on the same footing as FYOIP-15's per-endpoint isolation failures; when the publication gate adds a visibility state, every one of those reads gains the same condition it does. `toString()` output and logs must never carry detail-row values.

## Test Plan

**Unit.** FQN construction and uniqueness per type; identity reuse on re-registration; envelope/detail pair rules; relation validation rejecting cross-workspace, deleted or self edges; visibility filtering of edges; ordinal change not altering a column identity; the retention window's arithmetic and the revival branch that clears the vanished mark on the same row.

**Integration.** Crawl then registry: one `TABLE` and one `COLUMN` per physical object, stable across repeated runs including a column reorder, an added column and a dropped one; a column's tags surviving a reorder and a re-run; a dropped column keeping its row, its tag links and its `CONTAINS`/`MAPS_TO` edges inside the window, the same name reappearing reviving that exact entity id with its governance visible again and no second row colliding with `(table_id, name)`, and a column still absent past the window being deleted in bounded batches by the sweep together with its links; a `MISSING` table keeping its whole column subgraph intact; publication registering `MODEL → TABLE` `PUBLISHED_AS` and `MODEL_FIELD → COLUMN` `MAPS_TO`; search facets matching row-exact counts, and foreign-workspace or soft-deleted entities absent from search, detail, relations, members and tags; an edge to a retired, missing or hidden endpoint returned as unavailable with its reason instead of vanishing; registry rebuild from detail rows producing an identical projection; the batch migration on a seeded production-scale corpus inside its stated time budget.

**Playwright.** Search one keyword, get table, column and model results with type badges; open the generic entity detail; drill from a table to a column and from a model to its published table; curate and tag a column; rename a published model field's 英文编码 and get the confirmation naming what breaks, then cancel it and see nothing change.

**Performance.** 100,000-entity list and keyword search; relation panel at fan-out; the projection write measured as its own line item inside a 1,000-column model registration, so the cost of keeping it in the registering transaction is a number rather than a belief; the sweep that deletes past-retirement columns measured on a seeded corpus; projection rebuild window measured and reported honestly, with anything unrun marked as not run.

## Rejected Alternatives

1. **Extend `yak_ops_data_map_table` into the shared core row.** It would give 数据地图 ownership of model and metric identity, force every other module to depend on a product module for its own assets, and keep the publication and catalog ledgers unmerged. Class-table inheritance is the right shape; the core row simply cannot live in one consuming product.
2. **One JSON document per entity as the system of record.** Nothing would be constrainable or indexable, `information_schema`-shaped reads and every existing query would have to be rewritten around it, and it repeats the reason FYOIP-14 and FYOIP-15 refused a JSON bag.
3. **Elasticsearch or an ngram index first.** The metadata database is MySQL and there is no corpus yet; the same reasoning FYOIP-15 applied to `FULLTEXT … WITH PARSER ngram` applies with more force to a second search system with its own mapping, sync and failure modes. A projection read through one service is the cheap version of the same product capability.
4. **Register every known kind in one release.** Metrics, standard fields and APIs have no page that needs a registry row on day one; registering them means inventing their FQN rules, edges and visibility without a caller, which is the speculative abstraction the repository's own rules forbid.
5. **Introduce `API` as a placeholder type now.** There is no API asset source in this repository. A type nothing writes holds one value, and FYOIP-15 already refused that exact shape for `asset_type`.
6. **Federated search that queries each module live and merges the pages.** No shared ranking or facet, no stable identity for a click-through, one round trip per kind per page, and it cannot answer "columns of a table this caller may not see" without each module implementing the same visibility rule again.
7. **Keep model-published snapshots addressable as entities.** It would give one physical table two entity rows with two lifecycles and two provenance states, which is the duplicate-identity problem this registry exists to remove.
8. **Do Phase 1 before fixing column identity.** The crawler currently replaces a table's columns on every run, so `COLUMN` entities and every relation to them would be deleted and recreated on schedule; stable identity is the precondition, not a parallel workstream.
9. **Auto-infer `MAPS_TO`, or infer a rename, by position or type.** An added column shifts every later ordinal, and a type match across a reorder silently mis-maps a business key. The same reasoning covers Phase 0: `information_schema` gives one current snapshot per run, so a rename is indistinguishable from a drop plus an add, and pairing the two by type would move one column's tags and edges onto a different column. Name-based mapping with surfaced conflicts, and a confirmation dialog where a person can see the cost, is the honest version.
10. **Open the registry in the same release as FYOIP-18, or start its Phase 0 first.** Carrying the envelope, relations and search projection alongside publication-time registration turns two reviewable changes into one unverifiable change of roughly 40 person-days, and the `origin` fields it would absorb are exactly the fields FYOIP-18 still needs to define and test on their own. Moving the column unique key before the envelope that owns it is settled also re-opens every 数据地图 query that reads columns today, which is a second blast radius inside one proposal. The decided order is sequential with a hard interval: FYOIP-18 releases, runs in production for at least two weeks with no open P0 or P1 defect on the registration path, and only then does Phase 0 start — starting Phase 0 on the day of the release was refused explicitly on 2026-10-28.
11. **A nightly sweep that deletes edges whose endpoints do not resolve.** It defends a state the schema already forbids — both endpoints carry `ON DELETE CASCADE` foreign keys, so an edge cannot outlive its entity the way V7's child tables cannot outlive an asset — and it is actively harmful in the case that matters: a column inside its retired-and-retained window, or a table the crawler currently reports `MISSING`, is temporarily unreachable rather than gone, and deleting its edges would erase the governance the retention window exists to protect and force the subgraph to be rebuilt on return. An unreachable endpoint is a read-side rendering state.
12. **Move the projection write out of the registering transaction now.** The stated worry — a projection update locking the business table — is not how InnoDB row locking works, and the cost the change would buy is the guarantee that a just-registered asset is searchable in the same request, which is the reason FYOIP-18 answers inline at all. A post-commit hook would still run on the request thread without a second queue to run it on, and the repository has no event infrastructure to hand it to. The write is measured as its own line item first; this is the alternative that measurement would replace, not a switch shipped in advance.
