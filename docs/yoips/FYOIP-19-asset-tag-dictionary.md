# FYOIP-19: Asset Tag Dictionary and Value Bindings

Status: Accepted

Discussion: https://github.com/gitfortian/yak-ops/pull/44

Decision: [VOTE] Accepted — https://github.com/gitfortian/yak-ops/pull/44#issuecomment-6514062166

Implementation: Opens with migration `V11__v1_13_0.sql`. The proposal expected `V10`, but the v1.12.0 governance migration (FYOIP-16 implementation) took that number before implementation opened, so the first unused number at implementation time is `V11`.

Release: N/A until released

Related FLIPs: N/A

## Motivation

FYOIP-15 shipped tags as one flat table. `yak_ops_data_map_tag` holds a Workspace-unique name and `yak_ops_data_map_table_tag` holds `(table_id, tag_id)`. The catalog filters on one `tagId`, the asset detail edits the whole set, and any member may create, rename or delete a tag from the asset dialog. That model has four limits the product already feels.

- **No vocabulary.** `电商`, `电商业务` and `E-commerce` are three unrelated tags. Nothing states that they mean the same thing, and nothing stops a fourth spelling next week.
- **No dimension.** The filter accepts one tag id. A user cannot ask for `department = 电商` while excluding `sensitivity = PII`, because there is no key to group values under and no way to express two conditions.
- **Deletion is silent.** `fk_data_map_table_tag_tag` is `ON DELETE CASCADE`, so deleting a tag removes it from every asset that used it, with no count, no warning and no audit trail.
- **Rename is global and unowned.** A tag id is stable, so renaming is safe, but one member renaming `电商` to `电商业务` silently changes every asset for everyone.

This proposal replaces the flat tag with a two-level dictionary: a **key** names a dimension, a **value** names a term inside it, and a **binding** attaches a value to a catalog asset. It keeps the parts of FYOIP-15 that work (Workspace isolation, id-based renames, replace-set editing) and fixes the parts that do not (no vocabulary, no protection).

The proposal is deliberately smaller than a tag platform.

| Included in this proposal | Deliberately excluded |
| --- | --- |
| Key and value dictionaries, managed per Workspace | Preset or system tags, tag groups and tag hierarchies |
| One binding row per `(asset, value)`, with a real foreign key | Polymorphic `target_type`/`target_id` targets and non-table assets |
| Multi-value per key, with facet filter semantics | Per-key cardinality flags, typed values, value ranges |
| Database-enforced delete protection | Merge tooling and an approval flow |
| Migration of the existing flat tags into one `legacy` key | Automatic re-tagging, lineage propagation and AI suggestions |
| A tag console, a new asset dialog and a multi-value filter | Ranking, usage analytics beyond a count, and tag-based scoring |

Five repository facts shape the design.

- The current model is defined in `V7__v1_9_0.sql`: `yak_ops_data_map_tag` (`id`, `workspace_id`, `name`, audit columns) and `yak_ops_data_map_table_tag` (`table_id`, `tag_id`, `workspace_id`, `create_time`, `create_by`), with `ON DELETE CASCADE` on both foreign keys.
- `DataMapTagService` exposes list, create, rename, delete and a replace-set `assign`. `DataMapController` maps them under `/api/v1/data-map/tags`. `DataMapTableQueryDTO` carries `tagId`, and `DataMapTableSummaryVO` returns `List<DataMapTagVO>` of `{id, name}`.
- `WorkspaceService` exposes `isMember` and `isActiveMember`; the manager check (`OWNER` or `ADMIN`) exists only as a private `requireManager` inside `WorkspaceServiceImpl`.
- `yak-ops-business-datamap` depends on `yak-ops-common`, `yak-ops-dao`, datasource, modeling and the JDBC connector. It does not depend on `yak-ops-platform`.
- No migration in this repository inserts or updates business rows. Every identity is a 64-character application-generated string, timestamps are `DATETIME(3)`, and the metadata database is MySQL with InnoDB and `utf8mb4_unicode_ci`.

## Public Interfaces

### Product behavior

| Item | Contract |
| --- | --- |
| Scope | Keys, values and bindings belong to exactly one Workspace. No tag is shared across Workspaces. |
| Target scope | A binding attaches a value to a data map table. No endpoint accepts a target type, and no other asset type is taggable in this release. |
| Dictionary writes | Creating, renaming or deleting a key or value requires Workspace `OWNER` or `ADMIN`. |
| Binding writes | Any active Workspace member may replace an asset's tags or run a batch binding. |
| Key | A key is a dimension name, unique per Workspace, trimmed, 1–64 characters, compared case-insensitively by the collation. |
| Value | A value is a term inside exactly one key, unique per key, trimmed, 1–128 characters, compared case-insensitively. |
| Cardinality | An asset may hold **several values of the same key**. The binding is unique per `(workspace, table, value)`, not per key. |
| Filter semantics | Values of the same key are OR-ed; different keys are AND-ed. `department IN (电商, 零售) AND sensitivity = PII` is two filter clauses, not four. |
| Binding limit | One asset holds at most 20 values in total, across all keys. Exceeding it returns `TOO_MANY_TAGS_PER_ASSET`. |
| Value limit | One key holds at most 200 values. Exceeding it returns `TOO_MANY_TAG_VALUES_PER_KEY`. |
| Delete protection | A key or value that is still bound cannot be deleted. The database rejects the delete, and the API returns `TAG_IN_USE`. |
| Rename | Renaming a key or value changes every display because bindings reference ids. Duplicate names return `DUPLICATE_TAG_KEY` or `DUPLICATE_TAG_VALUE`. |
| Asset lifecycle | Deleting a catalog asset deletes its bindings. A table marked `MISSING` keeps its bindings until the row itself is deleted. |
| Display | The asset list shows at most three values and collapses the rest into `+N`. The asset detail shows every value grouped by key. |
| Navigation | The tag console is a first-level entry of 数据治理: `数据治理 > 标签管理`, path `/data-governance/tags`. It is not a child of 数据地图. |
| Audit | `create_by` and `update_by` are written from the authenticated actor. Deletions are logged with actor, key id and value id at `INFO`; there is no tag event table. |

### Recorded scope decisions (2026-10-27)

Two questions the first draft left open were decided before the proposal pull request. They are scope decisions, not acceptance: `Status` stays `Discussion` and the `Decision` line stays pending until a maintainer vote.

| Id | Decision | Rationale | Consequence |
| --- | --- | --- | --- |
| D1 | Only a Workspace `OWNER` or `ADMIN` may create, rename or delete a key or a value. Every active member may bind and unbind. | A key is a Workspace-level contract. A member deleting one breaks every saved filter in the Workspace at once, and a member renaming one silently rewrites what every asset means. A platform call is cheaper than repairing that. | `WorkspaceService.isManager(workspaceId, userId)` becomes a public method and `yak-ops-business-datamap` adds a `yak-ops-platform` dependency. This tightens today's behavior, where any member can delete a tag, so the release notes must state it. |
| D2 | Bindings attach to data map tables only. No `target_type`, no second asset type, no generic binding endpoint. | There is no Task-side tag display or filter to consume the data, so a generic binding would add a registry, lose the foreign key and have no test surface. An abstraction without a consumer is a liability, not a head start. | The binding table keeps its composite foreign key to `yak_ops_data_map_table` and gains no target columns. Tagging a second asset type requires its own proposal and migration; the dictionary tables are target-agnostic and can be reused unchanged. |

Both decisions remove an option rather than add one. An implementation that adds a target-type parameter, a target registry or a member-writable dictionary is not this proposal.
### Numbering and coordination with the Asset Entity Registry

This document was drafted as FYOIP-17, but master's index has since assigned FYOIP-17 to the Asset Entity Registry and FYOIP-18 to Model-Published Asset Registration; per the index rule, the next unused number, this document is renumbered to FYOIP-19.

The overlap with the Asset Entity Registry is real and needs a maintainer sequencing decision before either proposal is accepted. The registry plans `asset_tag` and `asset_tag_link` applied polymorphically to tables, columns, models and metrics, with existing table tags re-linked during its migration — while the target-scope decision above forbids a polymorphic target in this release. If the registry is accepted first, this proposal's dictionary (keys, values, limits, delete protection, the permission split) stays valid, but the binding target and the migration must be rewritten against `asset_tag_link` instead of a dedicated binding table. If this proposal is accepted first, the registry's tag move must adopt the dictionary as its vocabulary rather than reintroducing flat names. Either order works; accepting both unchanged does not.

### REST

Dictionary endpoints, new controller mapped at `/api/v1/tags`:

| Method and path | Purpose |
| --- | --- |
| `GET /keys` | Every key with its values, each value carrying `usageCount`, ordered by key name then value name. |
| `POST /keys` | Create a key with an optional initial value list, in one transaction. |
| `PUT /keys/{keyId}` | Rename a key. |
| `DELETE /keys/{keyId}` | Delete a key and its values; `TAG_IN_USE` while any value is bound. |
| `POST /keys/{keyId}/values` | Add a value to an existing key. |
| `PUT /values/{valueId}` | Rename a value. |
| `DELETE /values/{valueId}` | Delete a value; `TAG_IN_USE` while bound. |

Binding endpoints:

| Method and path | Purpose |
| --- | --- |
| `PUT /tables/{tableId}` | Replace the asset's whole tag set from `{ "tagValueIds": [...] }`; at most 20, all-or-nothing. |
| `POST /bindings/batch` | `{ "tableIds": [...], "tagValueIds": [...], "operation": "ADD" \| "REMOVE" }`; at most 100 tables, 20 values and 1,000 affected bindings. |

Catalog change:

| Method and path | Purpose |
| --- | --- |
| `GET /api/v1/data-map/tables?tagValueIds=a&tagValueIds=b` | Replaces the single `tagId` parameter. The catalog groups the ids by key and applies the filter semantics above. |

The catalog response stops returning `DataMapTagVO`. `DataMapTableSummaryVO.tags` and `DataMapTableDetailVO.tags` become `List<DataMapTagRefVO>` of `{keyId, keyName, valueId, valueName}`.

Unknown keys, values and tables reuse `RESOURCE_NOT_EXISTS`. Blank or over-length names are rejected by Bean Validation, not by a coded error. New codes belong to `io.yak.ops.common.enums.tag.TagErrorCode`, in the first free block after the data map range:

| Code | Use |
| --- | --- |
| `19901 DUPLICATE_TAG_KEY` | The key name already exists in this Workspace. |
| `19902 DUPLICATE_TAG_VALUE` | The value name already exists in this key. |
| `19903 TAG_IN_USE` | The key or value is still bound to at least one asset. |
| `19904 TOO_MANY_TAGS_PER_ASSET` | The request would leave an asset with more than 20 values. |
| `19905 TOO_MANY_TAG_VALUES_PER_KEY` | The key already holds 200 values. |
| `19906 TAG_WRITE_FORBIDDEN` | The caller is not a Workspace `OWNER` or `ADMIN`. |
| `19907 BATCH_TOO_LARGE` | The batch exceeds 100 tables, 20 values or 1,000 bindings. |

`TAG_IN_USE` carries a fixed localized message, not a count. The console already has `usageCount` from `GET /keys`, so a dynamic sentence would duplicate data the client holds and freeze a number into an error code.

### Persisted formats

The migration takes the first unused number at implementation. FYOIP-16 has since been accepted and shipped as `V9__v1_11_0.sql` (metadata collection tasks), with `V8__v1_10_0.sql` taken by the v1.10.0 crawl upgrade; the proposal therefore expected `V10`, but the v1.12.0 governance migration took that number first, so implementation writes `V11__v1_13_0.sql`. The release segment is taken from `release.env` when the migration is written. Three tables are created and two are dropped; no other table is altered.

`yak_ops_tag_key`

- `id VARCHAR(64)`, `workspace_id VARCHAR(64)`
- `name VARCHAR(64)` — trimmed dimension name
- `create_time`, `update_time` `DATETIME(3)`, `create_by`, `update_by` `VARCHAR(64)`
- keys: `PRIMARY KEY (id)`, `UNIQUE uk_tag_key_name (workspace_id, name)`, `UNIQUE uk_tag_key_workspace (workspace_id, id)`

`yak_ops_tag_value`

- `id VARCHAR(64)`, `workspace_id VARCHAR(64)`, `key_id VARCHAR(64)`
- `name VARCHAR(128)` — trimmed term
- `create_time`, `update_time` `DATETIME(3)`, `create_by`, `update_by` `VARCHAR(64)`
- keys: `PRIMARY KEY (id)`, `UNIQUE uk_tag_value_name (key_id, name)`, `UNIQUE uk_tag_value_workspace (workspace_id, id)`, `KEY idx_tag_value_key (workspace_id, key_id)`
- `FOREIGN KEY (workspace_id, key_id) REFERENCES yak_ops_tag_key (workspace_id, id) ON DELETE CASCADE`

`yak_ops_data_map_table_tag_value`

- `workspace_id VARCHAR(64)`, `table_id VARCHAR(64)`, `tag_value_id VARCHAR(64)`
- `create_time DATETIME(3)`, `create_by VARCHAR(64)`
- keys: `PRIMARY KEY (workspace_id, table_id, tag_value_id)`, `KEY idx_table_tag_value_usage (workspace_id, tag_value_id, create_time)`
- `FOREIGN KEY (workspace_id, table_id) REFERENCES yak_ops_data_map_table (workspace_id, id) ON DELETE CASCADE`
- `FOREIGN KEY (workspace_id, tag_value_id) REFERENCES yak_ops_tag_value (workspace_id, id) ON DELETE RESTRICT`

Both foreign keys are composite on `workspace_id`. A Workspace can therefore never bind another Workspace's asset or value, even if a service check is wrong. `yak_ops_data_map_table` already has `UNIQUE (workspace_id, id)`, and the new tables add the matching unique keys so both references are valid.

## Proposed Changes

### 1. A key is a dimension, a value is a term, a binding references the value

The dictionary is two tables because a value cannot exist without its key, and a binding cannot name a key without naming a value. The binding stores `tag_value_id` only. Storing `tag_key_id` beside it would be derivable data that can drift, and it would force a second unique key that constrains nothing new.

The binding keeps `workspace_id` because every query is Workspace-scoped, and because the composite foreign keys above turn isolation into a database guarantee rather than a service convention.

### 2. A key is multi-valued

`UNIQUE (workspace, table, tag_value_id)` deliberately does **not** include `tag_key_id`. One asset may carry `subject = 订单` and `subject = 支付` at the same time. This is the facet model users expect from a filter panel, and it is the only model that survives the migration in section 8 without inventing a key per old tag.

A per-key `single`/`multi` flag was rejected: it needs a column, a UI that switches between radio and checkbox, validation on write, and a migration decision for every existing key, to express a constraint that no current user asked for.

Filter semantics follow from the same choice. The catalog resolves each requested value to its key, builds one `EXISTS` clause per key, and ANDs the clauses. Values under one key become an `IN` list. This is expressible in one SQL statement and does not grow with the number of selected values.

### 3. The binding references the catalog table, not a polymorphic target

The binding is `yak_ops_data_map_table_tag_value` with a foreign key to `yak_ops_data_map_table`, not a generic `(target_type, target_id)` pair.

A polymorphic table cannot have a foreign key. Every asset deletion would leave orphan bindings, every read would need a `JOIN` that filters them out, every write would need a target-type registry whose only entry is `DATA_MAP_TABLE`, and every usage count would include rows pointing at objects that no longer exist. The repository rule is to add an abstraction when a second caller exists, not before.

The dictionary itself is not table-specific. When a second asset type has a page that displays and filters tags, the binding table is generalized in its own migration, with a real consumer to test against. Until then, the only honest target is the catalog table.

### 4. Delete protection is a constraint, not a pre-check

The draft this proposal replaces kept `ON DELETE CASCADE` and added `SELECT COUNT(1)` before the delete. That is a time-of-check/time-of-use gap: a binding inserted between the count and the delete is removed silently by the cascade.

The constraint chain here is:

- deleting an asset cascades its bindings, which is correct because the asset is gone;
- deleting a value is `RESTRICT`-ed by any binding that still references it;
- deleting a key cascades its values, and that cascade is itself blocked by the `RESTRICT` on the bound value.

So a key with one bound value cannot be deleted, and neither can that value, without any application check. The service catches `DataIntegrityViolationException` and maps it to `TAG_IN_USE`. The count query is not needed for correctness; the console reads it from `GET /keys`.

### 5. Dictionary writes require a manager, bindings do not

Today any member can delete a tag, which is the silent-data-loss problem in the motivation. The dictionary is shared vocabulary: one member's rename changes every asset for everyone. Bindings are per asset and reversible: a wrong tag on one table is a one-row fix.

The permission split is therefore: `OWNER` and `ADMIN` write the dictionary, any active member writes bindings. This needs one platform capability that does not exist yet: `WorkspaceService.isManager(String workspaceId, String userId)`, implemented beside the existing private `requireManager`. `yak-ops-business-datamap` adds a direct `yak-ops-platform` dependency for that call, matching `yak-ops-business-modeling` and `yak-ops-business-task`.

The asset dialog loses its create, rename and delete actions. It becomes a selector over existing values, with a link to the console for managers.

### 6. Limits and normalization

- Key names are trimmed, must contain a non-whitespace character, and are at most 64 characters. Value names are the same but at most 128.
- Uniqueness is enforced by the index and by the collation, so `Dept` and `dept` collide. The service normalizes only whitespace; it does not change case, because the collation already makes case variants duplicates.
- 20 values per asset is a product limit that keeps the list readable and the replace-set request small. It is enforced on the final state of a replace or batch, not per request element.
- 200 values per key bounds `GET /keys`, which returns the whole dictionary in one response. Paginating values is out of scope until a key legitimately exceeds it.

### 7. Concurrency

- A replace-set is `SELECT ... FOR UPDATE` on the asset row, then delete and insert in one transaction. The existing `assign` does the delete and insert without locking the asset, so two concurrent callers can interleave into a set neither of them asked for.
- Two callers creating the same key or value race on `uk_tag_key_name` or `uk_tag_value_name`. The loser catches `DuplicateKeyException` and returns `DUPLICATE_TAG_KEY` or `DUPLICATE_TAG_VALUE`, the same pattern the data map already uses.
- A batch is one transaction. If one table would exceed 20 values or belongs to another Workspace, nothing changes and the response names the offending table id.

### 8. Migration keeps every existing tag

The old model cannot be mapped one-to-one: a flat tag has a name and no key. Three mappings are possible, and only one preserves meaning:

1. one key per old tag, with a value such as `true` — the dictionary degenerates into the old tag list with a meaningless value on every row;
2. one key named `legacy` per Workspace that has tags, with the old tag names as its values — every binding survives, every label stays readable, and the old list becomes one dimension;
3. discard the old tags — loses user data.

This proposal takes mapping 2. The migration:

1. creates one `legacy` key per Workspace that has at least one tag, with a deterministic id derived from the Workspace id;
2. creates one value per old tag, with a deterministic id derived from the Workspace id and the old tag id, keeping the old `create_by` and `update_by`;
3. copies every row of `yak_ops_data_map_table_tag` into the new binding table;
4. drops `yak_ops_data_map_table_tag` and `yak_ops_data_map_tag`.

A Workspace with no tags gets no `legacy` key, so this is not a preset tag. The key is a migration artifact with a visible name and a defined exit: members unbind what they no longer need, re-tag with real keys, and a manager deletes `legacy`. This proposal provides no automatic merge, so `legacy` may coexist with real keys indefinitely.

Three inserts and two drops are the whole migration. Deterministic ids keep the mapping reproducible and never collide with `IdUtils.nextId()`, which emits digits only:

```sql
INSERT INTO yak_ops_tag_key (id, workspace_id, name, create_time, update_time, create_by, update_by)
SELECT CONCAT('tklg', LEFT(SHA2(t.workspace_id, 256), 32)), t.workspace_id, 'legacy',
       MIN(t.create_time), MAX(t.update_time), 'system', 'system'
FROM yak_ops_data_map_tag t
GROUP BY t.workspace_id;

INSERT INTO yak_ops_tag_value (id, workspace_id, key_id, name, create_time, update_time, create_by, update_by)
SELECT CONCAT('tvlgb', LEFT(SHA2(CONCAT(t.workspace_id, ':', t.id), 256), 32)),
       t.workspace_id,
       CONCAT('tklg', LEFT(SHA2(t.workspace_id, 256), 32)),
       t.name, t.create_time, t.update_time, t.create_by, t.update_by
FROM yak_ops_data_map_tag t;

INSERT INTO yak_ops_data_map_table_tag_value (workspace_id, table_id, tag_value_id, create_time, create_by)
SELECT b.workspace_id, b.table_id,
       CONCAT('tvlgb', LEFT(SHA2(CONCAT(b.workspace_id, ':', b.tag_id), 256), 32)),
       b.create_time, b.create_by
FROM yak_ops_data_map_table_tag b;

DROP TABLE yak_ops_data_map_table_tag;
DROP TABLE yak_ops_data_map_tag;
```

`'system'` is `CommonConstants.SYSTEM_USER`: the key is created by the migration, not by a person. Values keep the original `create_by` and `update_by` of the tag they came from.

The old tables are dropped in the same migration, because the release that ships the new endpoints is the same release that stops reading them. Keeping them would create two sources of truth with no reader.

### 9. Navigation and the two screens

`DATA_GOVERNANCE_NAVIGATION` currently contains one item, 数据地图, with four children. The tag console becomes a second item at the same level, `标签管理`, path `/data-governance/tags`, with no children; it is not a child of 数据地图, because the dictionary is not catalog browsing.

The console has two panes: keys on the left, values and usage counts on the right, with rename, delete and a batch action that selects assets from the catalog and applies `ADD` or `REMOVE`. The batch action calls `POST /bindings/batch`; it does not loop over the single-table endpoint.

The catalog filter becomes a multi-select grouped by key. The asset detail keeps its tag block, now grouped by key, and its edit action opens the selector from section 5.

## Compatibility, Deprecation, and Migration Plan

- **Migration order.** This proposal does not depend on FYOIP-16 and only needs the next unused migration number. FYOIP-16 is accepted and shipped as `V9__v1_11_0.sql`, with `V8__v1_10_0.sql` taken by v1.10.0; the proposal therefore expected `V10`, but the v1.12.0 governance migration claimed it first, so implementation takes `V11`. Two proposals must not both claim one number.
- **Data.** Every existing tag and binding survives as a value under the `legacy` key. No tag name is rewritten, no binding is dropped, and no asset is re-tagged.
- **Removed endpoints.** `GET/POST /api/v1/data-map/tags`, `PUT/DELETE /api/v1/data-map/tags/{tagId}` and `PUT /api/v1/data-map/tables/{tableId}/tags` are deleted in the same release. The frontend is the only consumer and moves to `/api/v1/tags` in the same pull request. There is no alias endpoint and no dual-write window.
- **Changed parameter.** `DataMapTableQueryDTO.tagId` becomes `tagValueIds`. An old client that still sends `tagId` is ignored by Spring, so its filter silently disappears rather than failing; this is acceptable only because the UI ships together. No compatibility parameter is added.
- **Changed response.** `DataMapTagVO` (`{id, name}`) is removed and replaced by `DataMapTagRefVO` (`{keyId, keyName, valueId, valueName}`). Every frontend caller is updated in the same release.
- **Behavior change.** Deleting a tag no longer detaches it from assets. The delete now fails with `TAG_IN_USE` until the tag is unbound, which is the point of the proposal. Release notes must state it.
- **No change** to the crawler, the catalog query contract beyond the filter, the datasource or modeling modules, or the workspace membership model.
- **Rollback.** There is no downgrade path. The supported rollback is restoring the pre-upgrade backup, consistent with previous releases. The dropped tables are not recreated by any script.

## Test Plan

- **Migration:** seed two Workspaces, one with three tags bound to two tables and one without tags; run the migration; assert one `legacy` key exists only for the first, every tag name appears once as a value, every binding survives with its original `create_by`, and both old tables are gone.
- **Migration ids:** run the migration twice on two copies of the same seed and assert the generated key and value ids are identical, so a restored backup and a fresh migration produce the same identities.
- **Delete protection:** bind a value, delete the value and assert `TAG_IN_USE` with the row intact; delete its key and assert `TAG_IN_USE`; unbind, delete the value, then delete the key and assert both succeed and the values cascade.
- **Asset deletion:** delete a catalog asset that carries three bindings and assert the bindings are gone while the dictionary rows remain.
- **Binding target:** assert the binding table has no target-type column, that its composite foreign key rejects a table id that does not exist in the Workspace, and that no tag endpoint accepts a target type, so a second asset type cannot be bound without a migration.
- **Uniqueness:** create a key, then create `Dept` when `dept` exists and assert `DUPLICATE_TAG_KEY`; create the same value name under two different keys and assert both succeed; create a duplicate value under one key and assert `DUPLICATE_TAG_VALUE`.
- **Permission:** a `MEMBER` creates, renames and deletes a key and a value and gets `TAG_WRITE_FORBIDDEN`; the same member replaces an asset's tags successfully; an `ADMIN` and an `OWNER` can do both. Assert `WorkspaceService.isManager` returns true only for `OWNER` and `ADMIN`, because that one method is the gate for the whole dictionary.
- **Workspace isolation:** bind a value from another Workspace, rename a foreign key and delete a foreign value; each returns `RESOURCE_NOT_EXISTS` and changes nothing; counts never include another Workspace's bindings.
- **Limits:** a replace-set of 20 values succeeds and 21 returns `TOO_MANY_TAGS_PER_ASSET` with no change; the 200th value in a key succeeds and the 201st returns `TOO_MANY_TAG_VALUES_PER_KEY`.
- **Replace-set:** `[a, b]` replaces `[b, c]` with exactly `{a, b}`; an empty list clears the asset; two concurrent replace-sets leave the asset equal to one of the two requests, never a mix.
- **Batch:** 100 tables add one value in one transaction; 101 tables return `BATCH_TOO_LARGE`; one foreign table or one table that would exceed 20 values leaves every table unchanged and names the offending id.
- **Filter:** two values of one key return the union; two values of different keys return the intersection; an unknown value id returns `RESOURCE_NOT_EXISTS`; the same filter used for the list count returns the same number.
- **Rename propagation:** rename a key and a value and assert the asset detail, the list and the filter return the new names while binding rows are unchanged.
- **Display:** an asset with five values renders three and `+N` in the list, and five grouped under their keys in the detail.
- **Frontend:** the console is reachable before and after sign-in; a `MEMBER` sees read-only dictionary actions; deleting a bound value shows the usage count from `GET /keys` and the `TAG_IN_USE` message; the catalog filter sends repeated `tagValueIds`.
- **Verification commands:** backend `bash mvnw -pl '!yak-ops-ui,!yak-ops-dist' verify`; frontend checks follow `yak-ops-ui/docs/tooling.md`. Migration and repository tests use the repository-owned MySQL resources, not a developer database.

## Rejected Alternatives

1. **Keep the flat tag and add a naming convention such as `dept:电商`.** The string is parsed in the service, the UI, the filter and the export; a typo creates a new dimension; rename and delete still have no protection. A two-table dictionary is the same information with constraints the database can enforce.

2. **Keep `ON DELETE CASCADE` and guard deletes with a count query.** The count and the delete are two statements; a concurrent binding insert between them is deleted silently, which is exactly the failure this proposal exists to prevent.

3. **One value per key.** `UNIQUE (..., tag_key_id)` reads simpler, but it forces the migration to create one key per old tag, so the dictionary ends up being the old tag list plus a meaningless value on every row. Real dimensions such as `subject` also need more than one value per asset.

4. **A polymorphic `(target_type, target_id)` binding.** It trades a foreign key for a registry, adds orphan rows that every read must filter, and gives up `ON DELETE CASCADE` from the catalog. There is one target type with a screen; the generalization belongs to the proposal that adds the second. Recorded as D2.

5. **Store `tag_key_id` on the binding as well.** It is derivable from the value, can drift, and would justify a unique key that adds no constraint. The value is the identity of a binding.

6. **Keep an `owner_id` on the key.** The owner leaves the Workspace and the key becomes unmanageable; there is no transfer flow. Ownership here is a role, not a person.

7. **Let any member manage the dictionary.** It matches today's behavior and avoids a platform change, but it reproduces the silent global rename and delete that the proposal is meant to stop. A shared vocabulary needs one gate. Recorded as D1.

8. **Seed system tags such as `核心业务` or `高敏感`.** Presets bake a language and a policy into data, and every Workspace inherits terms it did not agree to. Users create their own keys.

9. **Add merge tooling in this release.** Merging two keys means rebinding every asset and deciding which values collapse; it needs its own conflict rules, dry run and audit. Rename plus manual re-tagging is enough for the first release.

10. **A tag event table for audit.** The dictionary holds `create_by` and `update_by`, and deletions are logged by the application. A full history is worth its own proposal when someone needs to answer "who removed this key" weeks later; a half-built log is not.

11. **Type the values as integers, booleans or dates.** Types need parsing, validation, sorting and a filter UI per type, and no requested use case needs them. A string with a clear key is enough.

12. **Extend `DataMapTagService` with key-value methods instead of replacing it.** One service would carry two models, two delete semantics and two response shapes, and every caller would have to know which one it holds. The flat model is replaced, not wrapped.

13. **Emit governance Issues for untagged assets.** That is a rule over the catalog, not a tag feature; it belongs to the governance rule set and needs its own dimension and score decision.

14. **Put the console under 数据地图 as a fifth child.** The dictionary is not catalog browsing, and its permissions differ from every data map page. A sibling entry states the ownership instead of hiding it under a browsing menu.
