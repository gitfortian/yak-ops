# YOIP-5: Data Integration Synchronization Model + Product Interaction

Status: Accepted

Discussion: https://github.com/weifuwan/yak-ops/pull/1636

Decision: [VOTE] Accepted — https://github.com/weifuwan/yak-ops/pull/1636#issuecomment-6160395888

Implementation: [Unified product entry and versioned solutions — PR #1637](https://github.com/weifuwan/yak-ops/pull/1637); [Explicit multi-table batch — PR #1639](https://github.com/weifuwan/yak-ops/pull/1639); [Database realtime and stage observation — PR #1641](https://github.com/weifuwan/yak-ops/pull/1641); [Initial schema preparation — PR #1643](https://github.com/weifuwan/yak-ops/pull/1643)

Release: N/A until released

Related FLIPs: N/A

## Motivation

Yak Ops Data Integration provides one "Data Sync" product entry. Users first choose source and target datasource types, then a supported synchronization solution and its steps. They subsequently configure actual connections, synchronization objects, target mappings, and execution settings. The list, editor, and execution details share one solution model.

**Offline and realtime are properties of synchronization solutions, not separate list, creation, editing, or detail pages.** A unified page does not erase the distinction between bounded batches and continuous execution, nor does it require identical forms for every scenario.

### Design References

DataWorks' [database-wide realtime configuration][dw-realtime] and [database-wide offline configuration][dw-offline] both begin from a synchronization task. They select a solution through source type, target type, specific synchronization type, and synchronization steps before configuring actual datasources. Its [supported solutions documentation][dw-solutions] distinguishes single-table and database-wide scenarios. Its [offline incremental configuration][dw-incremental] expresses batch ranges through filters and scheduling parameters. These references, together with screenshots supplied by the maintainer showing the creation dialog, batch table selection, and target table mappings, informed this proposal.

This proposal adopts that interaction structure; it does not claim to reproduce every DataWorks capability, API, or internal architecture. The fields, type identifiers, and delivery boundaries here belong to Yak Ops. Purchasing resource groups, CU quotas, Data Agents, and periodic Base/Log merges are out of scope.

### Existing Code and Issues to Consolidate

The following observations refer to `main` at `1da2d2555f4411e72550606b4fca45f5e2cdb788`. They are neither a complete capability inventory nor runtime test results.

| Code location | Observed behavior | Product impact |
| --- | --- | --- |
| [AppRouter][code-router] and `app/offline-sync`, `app/realtime-sync` | Register separate list, creation, edit, and detail routes; editing and details already share components | Page identity remains path-dependent and both lists maintain duplicate logic |
| [definition.ts][code-definition] | Task parameters contain single-table `source / sink`; `JDBC / MYSQL_CDC` implies offline or realtime | Specific solution identity and chosen steps are not represented independently |
| [MySqlCdcSourceFactory][code-cdc] | The current product path always uses a Hybrid Source | Existing realtime behavior means a full load followed by continuous incremental updates; arbitrary step combinations cannot be enabled by adding checkboxes |
| [JdbcSinkFactory][code-sink] | Existing write modes are `APPEND / UPSERT`, and changelog streams require `UPSERT` | Overwrite and schema migration must not appear as delivered options |
| [ExecutionConfigurationFactory][code-execution] | New Instances use isolated state directories and initially set `RESTORE_LATEST=false` | Submitting again after stopping does not resume the previous Instance's checkpoint |

Task identity, versions, submission, and execution follow [YOIP-2](YOIP-2-task-execution-lifecycle.md). Connector creation and datasource access follow [YOIP-3](YOIP-3-connector-creation-and-datasource-lookup.md). Scheduled submissions follow [YOIP-4](YOIP-4-schedule-lifecycle-quartz.md). Physical completion and checkpoint semantics follow [YOIP-1](YOIP-1-checkpoint-and-end-of-input.md). This proposal does not redefine those boundaries.

## Public Interfaces

The following are proposed public product contracts. The original proposal PR introduced only this document, not an executable API, parameter version, database migration, or runtime capability.

### Pages and Routes

Data Integration navigation retains "Datasources" and "Data Sync". Data Sync uses these unified routes:

| Route | Purpose |
| --- | --- |
| `/data-sync` | One list for all synchronization tasks |
| `/data-sync/new` | Configure a task from a creation draft |
| `/data-sync/:id/edit` | Edit a task from its saved solution |
| `/data-sync/:id/detail` | Task information and execution details, with `executionId` selecting a specific Instance |

Offline and realtime filters use one list and server-side pagination. The new page must not simply wrap the old pages in two tabs or fetch two independently paginated datasets and merge them in the browser. Operations Center retains its statistics role; update only necessary detail links, without moving its statistics or redesigning navigation.

### Query Available Synchronization Solutions

Proposed authenticated, Workspace-scoped read-only endpoint:

`GET /api/v1/data-sync/solutions?sourceType={datasourceType}&targetType={datasourceType}`

Here `sourceType / targetType` are product datasource types, not Connector identifiers such as `JDBC / MYSQL_CDC`. Retain existing datasource type IDs, including PostgreSQL's existing `POSTGRE_SQL`; this proposal does not rename them. Both parameters are required.

Return only enabled solutions for the directed source/target type pair. Each solution has a stable `solutionType`, display name, applicable step descriptions, explicitly allowed step combinations, and one default combination. Each allowed combination declares its applicable execution settings, including whether scheduled submission is supported. Required, optional, and disabled checkbox behavior derives from those combinations; the three checkboxes are not independent Boolean values.

An unknown type produces a parameter error. A recognized pair with no available solutions returns an empty collection. A failed query must not be interpreted as an empty collection or trigger an automatic offline fallback. Without an available solution, the creation flow blocks progression and shows a concise reason.

The type-level query neither loads actual connections nor accesses databases or credentials. After selecting concrete connections, saving, publishing, and execution admission must still validate Workspace permissions, actual datasource types, table schemas, steps, and write rules. Query results do not authorize later execution; the backend must reject a stale page submitting a no-longer-supported combination.

### Versioned Synchronization Parameters

Retain the generic Task Definition, Version, and Instance APIs with `taskType=DATA_SYNC`. New product parameters use the next format version, `parameterSchemaVersion=3`; this is the parameter *schema* version, not the incrementing Definition version.

| Parameter | Authoritative meaning and constraints |
| --- | --- |
| `solutionType` | User-selected specific solution identity; never inferred from the page URL or Connector name |
| `steps` | User-selected, server-validated steps with unique identifiers; persisted in schema, full, incremental display order |
| `source / sink` | Selected Workspace datasource IDs; the backend resolves and validates types, and task parameters never contain secrets |
| `tableSelection` | Selection intent; the first unified release accepts only `EXPLICIT`, not an unreleased rule mode |
| `tableMappings` | Explicit source objects, target objects, and per-mapping write modes; a single-table task is a collection of length one |
| Synchronization latency, scope summary, data strategy | Read-only projections of solution and steps, not a second set of conflicting writable options |
| Schedule, Instance, execution stages, offsets | Not editable static synchronization parameters; each follows its own lifecycle |

The proposed version 3 example below represents single-table realtime full-plus-incremental synchronization into an existing target table. It cannot be submitted directly to the current version 2 API.

```json
{
  "solutionType": "SINGLE_TABLE_REALTIME",
  "steps": ["FULL", "INCREMENTAL"],
  "source": { "datasourceId": "mysql-source" },
  "sink": { "datasourceId": "postgres-target" },
  "tableSelection": { "mode": "EXPLICIT" },
  "tableMappings": [
    {
      "source": { "database": "app", "table": "orders" },
      "target": { "database": "ods", "schema": "public", "table": "ods_orders" },
      "writeMode": "UPSERT"
    }
  ]
}
```

In `EXPLICIT` mode, the source objects in `tableMappings` are the persisted explicit selection. Do not persist a second, redundant source-table list; incomplete mappings exist only in the page draft. Source and target identifiers use structured database/schema/table values, rather than splitting dotted strings. Each mapping must belong to namespaces allowed by its selected connection.

For version 3, resolve Connector selection from the solution, selected datasources, and registered Factories; do not accept potentially contradictory `source.type` and `solutionType` fields. If user-configurable Connector parameters are added later, define and validate them in the configuration that owns their semantics, rather than introducing an unbounded arbitrary property bag. Preserve the existing empty-array meaning of `transform`; transformation is not enabled by this proposal.

### Execution Summaries and Stage Observation

Lists and details use the same Instance observation contract for `instanceId`, pinned Definition version, Instance status, and available stages. Each stage observation distinguishes at least the step identifier, stage status, nullable percentage, measurement basis, and sample time. Stage statuses include pending, running, completed, failed, canceled, and unknown.

A percentage belongs to a particular stage of a particular Instance, not to the Definition. Unknown progress is null. Do not store a display-only failure fallback of 0% in the actual measurement field. Historical Instances without stage data remain viewable; do not fabricate schema migration or full initialization steps for them.

The Data Sync business projection supplies these observations. Do not push table configuration into the generic Task lifecycle or add UI colors, labels, or Workspace dependencies to Core/Runtime. Implementations reuse existing Instance queries and batch summary endpoints instead of creating a second execution history API.

## Proposed Changes

### 1. Four Dimensions and Their UI Controls

| Dimension | Concepts | Question answered | UI representation |
| --- | --- | --- | --- |
| Synchronization latency | Offline, realtime | Run bounded batches or continuously synchronize changes? | Encoded in the specific solution type |
| Synchronization scope | Single table, multiple tables, database-wide, sharded databases/tables | Which objects are synchronized, and how are they organized? | A specific type opens the flow; selection and mapping define object scope |
| Data strategy | Full, incremental, full plus incremental | Synchronize existing data, subsequent changes, or both? | Expressed as combinations of full and incremental steps |
| Write mode | Append, Overwrite, Upsert, etc. | How does incoming data affect existing target data? | Configured alongside target-table mappings, limited to supported modes |

These are four conceptual dimensions, not four independent required dropdowns. After selecting "database-wide realtime", do not ask again for "synchronization latency: realtime". After selecting full and incremental steps, do not separately require "data strategy: full plus incremental".

Synchronization latency is not a Cron interval. Data strategy is not a starting offset. Full synchronization does not imply overwrite. Incremental synchronization does not imply Upsert. Schema migration is not a fourth data strategy.

### 2. Specific Solution Types and Scope

Use these stable solution identities. Do not treat single-table and realtime as mutually exclusive options at the same level.

| `solutionType` | Display name | Scope semantics |
| --- | --- | --- |
| `SINGLE_TABLE_OFFLINE` | Single-table offline | One table mapping, executed as bounded batches |
| `SINGLE_TABLE_REALTIME` | Single-table realtime | One table mapping, using a supported realtime data strategy |
| `DATABASE_OFFLINE` | Database-wide offline | Batch table selection and target mappings from one source connection |
| `DATABASE_REALTIME` | Database-wide realtime | Batch table selection and continuous synchronization from one source connection |

These names define the target product vocabulary; they do not mean every type is selectable in the first release. Multiple tables are expressed through the explicit selection set of a database-wide solution, not a separate `MULTI_TABLE` page or plugin. A database-wide solution stays database-wide even when only one table matches; it is not automatically reclassified as single-table.

Sharded databases/tables require selection and many-to-one mapping semantics, not just a different table count. Before exposing that capability, define source shard identity, target primary-key conflicts, schema consistency, event ordering, and deletion behavior. Ordinary batch mapping currently prohibits multiple sources writing into the same target table. Users must not bypass that restriction merely by giving targets the same name before the capability is verified.

The first unified scope supports one source connection and one target connection. Cross-datasource shard aggregation, dynamically adding tables to running jobs, and live execution-plan changes are not automatically enabled. Their persisted state and admission semantics require further design review.

### 3. Creation Entry: Types Before Connections

Use a single "Create Sync Task" dialog with fields in this fixed order:

```text
Source type        [Select]
Target type        [Select]
Solution type      [Load from source and target types]
Sync steps         [Schema migration] [Full sync] [Incremental sync]
Name               [Suggested automatically; editable]

                                Cancel   Next
```

Show only steps supported and applicable to the selected solution. Required steps are checked and cannot be unchecked; changing optional steps must result in an explicitly allowed combination. At least one of Full or Incremental is required; the initial design does not permit schema-only tasks. Where the current Hybrid realtime path supports only full plus incremental, both steps are required and cannot be deselected.

Choose source and target *types* from the datasource type catalog; choose concrete connections in the editor. Having no saved connections must not prevent users from exploring type-level solutions. Offer "Add datasource" inside the editor and retain the draft. Do not ask again for a type that a concrete connection already determines.

Dependency and change rules:

- When the type pair changes, cancel earlier requests or discard stale responses. Disable solution, steps, and Next while loading. Old results must not overwrite new selections.
- After changing the type pair, revalidate the solution and steps. Preserve selections that remain legal; otherwise clear invalid choices and require confirmation. Never silently downgrade to another data strategy.
- After changing the specific solution type, use its legal default step combination. If this discards configured objects, mappings, or stage settings, require confirmation before clearing the affected fields.
- Update an automatically suggested name only until the user manually edits it. Datasource or step changes must not overwrite a custom name.

"Next" creates a non-sensitive draft in the current page session only. It does not persist a Task Definition, publish a task, enable scheduling, or start execution. Isolate drafts by user, Workspace, and browser tab. On refresh, restore a valid draft; otherwise reopen the solution-selection entry. Logout or Workspace switching must not leak drafts or stale asynchronous results across scopes.

The first "Save" still requires valid task configuration and creates an unpublished Definition. Do not add another persisted task lifecycle for drafts missing connections or mappings.

### 4. Unified Editor: Show Only Applicable Configuration

The unified editor is driven by the saved or selected solution. New tasks read the creation draft; existing tasks read persisted configuration. Never reinterpret task identity from an old URL or navigation filter. Common areas are Basic Information, Datasources, Source Data, Target Table Mappings, and Execution Settings.

| Area | User action | Required behavior |
| --- | --- | --- |
| Basic Information | Name, description, Debug, and review of selected solution and steps | Do not duplicate already-determined conceptual dimensions |
| Datasources | Select actual source and target connections; test connectivity | Show only authorized connections of matching types; a successful connection test does not prove CDC prerequisites or target-write compatibility |
| Source Data | Select one table or batch-select tables | Keep single-table configuration lightweight; batch mode offers available objects, selected objects, search, and filtering |
| Target Table Mappings | Target names, schema preparation, write modes, and applicable mappings | Persist only valid final mappings, and distinguish previews from execution results |
| Execution Settings | Necessary incremental ranges, start settings, and recurring scheduling | Expand only settings supported by the selected steps; ordinary users do not configure internal engine parameters |

The editor may compose single-table selection, batch selection, mappings, and scheduling components. It does not need a generic dynamic-form platform. Conditional behavior expresses specific capabilities, rather than using a single `realtime` Boolean to control the entire layout, route, write behavior, and progress.

#### Explicit Selection vs. Rule Selection

`EXPLICIT` persists a concrete object set. Manually checking tables after search or regex filtering is still explicit selection. Only `RULES` represents persisted discovery rules with a defined discovery time; a frontend search filter does not make a rule-based selection.

The initial release accepts only `EXPLICIT`. Before enabling rule selection, define include/exclude behavior, namespaces, zero-match handling, object changes between preview and submission, discovery timing, and historical resolved results. Each scheduled batch must pin its own object set; refreshing metadata during execution must not silently add tables. Dynamic tables in continuous jobs additionally require new-table initialization and incremental handoff contracts.

Explicit and rule-based selection are mutually exclusive. If selected objects or mappings exist, switching requires confirmation and clears incompatible values; never merge the two modes implicitly. Yak Ops determines actual table-count and batch-size limits, rather than copying limits from screenshots.

#### Target Mappings and Schema Preparation

Batch mappings reuse the same model and support implemented bulk naming and bulk editing. Each mapping shows source, target, write mode, and useful validation results. Target conflicts, missing permissions, and incompatible schemas must not be reported only in a global toast.

Without `SCHEMA`, use existing target objects while still performing necessary structural validation. Selecting `SCHEMA` means actual preparation of target objects; a passed validation is not "schema migration completed". Schema preview must not execute DDL. Actual creation occurs only after task admission, permission checks, and target-resource claims. Existing incompatible targets must not be implicitly dropped, truncated, or altered.

Initial table creation and continuous DDL propagation are different capabilities. Expose automatic creation, field mapping, and shard merging only after end-to-end implementation and verification. Checking schema migration alone must not promise propagation of every future DDL change.

### 5. Step Combinations and Execution Semantics

Step identifiers are `SCHEMA`, `FULL`, and `INCREMENTAL`, displayed as Schema Migration, Full Sync, and Incremental Sync. The following table defines target product behavior; availability depends on verified source/target paths and legal combinations.

| Solution behavior | Data steps | Execution semantics |
| --- | --- | --- |
| Offline full | `FULL` | Bounded batch; may run manually, and each scheduled invocation still performs a full load where scheduling is supported |
| Offline incremental | `INCREMENTAL` | Read changes within a fixed batch range; exit when the batch finishes |
| Offline full plus incremental | `FULL, INCREMENTAL` | Complete full initialization first; subsequent scheduled Instances perform incremental batches without requiring two manually created tasks |
| Realtime incremental only | `INCREMENTAL` | Continuously consume changes from an explicitly supported and valid starting position |
| Realtime full plus incremental | `FULL, INCREMENTAL` | Complete initialization, hand off correctly to continuous changes, and keep the Instance running after the full phase |

The step array is not a client-orchestrated Job DAG. Schema, full, and incremental are units of product configuration and observation; they do not each require a separate Task, Instance, or Job. The existing engine and Connectors own full-load/CDC handoff. Obtaining a log start position only after full loading completes could lose changes in the handoff window and is not acceptable.

If a realtime solution later supports a full-only combination, it must correspond to a genuinely bounded physical plan with accurate scheduling eligibility. A solution named "realtime" must not force an otherwise finite plan to run indefinitely, nor may the UI uncheck Incremental while starting an endless CDC source. Do not expose that combination until the bounded branch is implemented.

#### Offline Incremental and Scheduled Initialization

[DataWorks' offline incremental example][dw-incremental] uses filtering conditions and scheduling parameters to define data ranges. Yak Ops distinguishes business-window reads from persisted-cursor reads; the two must not be conflated into an opaque "incremental offset".

Before enabling window-based incremental synchronization, define and pin each Instance's time or key range, timezone, and inclusive/exclusive bounds. A failed retry must reuse the original range. Manually backfilling a historical window must not automatically advance the regular schedule state. Before enabling cursor-based incremental synchronization, define equal-value ordering, concurrent updates, and when the cursor commits. Source read completion or Split completion is insufficient to advance state for data not yet successfully written.

Offline full-plus-incremental requires durable proof that initialization completed and an explicit compatible-version relationship. Following failed initialization, the next run must still perform the required initialization. An incompatible schema, target, or table selection change must not reuse an old completion marker. Quartz determines *when* to submit, but neither owns table-level watermarks nor orchestrates initialization and incremental steps.

Field-filtered incremental reads cannot automatically observe physical deletes. Promise deletion synchronization only where both capture and write semantics support it. Do not expose offline incremental as available before these states and boundaries are implemented.

#### Write Rules

Write settings belong to target mappings. Keep existing `APPEND / UPSERT` behavior and primary-key validation. Changelog streams must not silently fall back to Append. The word "Upsert" alone does not guarantee DELETE support; target replay must handle changes according to actual Connector behavior.

A full read and an overwrite write are separate choices. Any future Overwrite mode must specify which target is overwritten, when cleanup happens, and what happens on failure. Recovery, retry, and incremental batches must not repeatedly truncate targets.

When initialization and later writes differ—for example, "overwrite during initialization, then update by primary key"—express the two operations separately in target write configuration. Bulk defaults must resolve to explicit effective table-level settings, not persist conflicting global and per-table values. The initial release neither exposes an unimplemented Overwrite mode nor reinterprets historical Append as overwrite.

### 6. Unified Task List, Operations, and Details

The list shows all Data Sync tasks by default. Filters include name or table name, specific solution type, source type, target type, and publication status. Expand advanced filters as needed; do not duplicate the four conceptual dimensions as four redundant sets of controls.

| Column | Content |
| --- | --- |
| Task Name | Name and current version |
| Sync Path | Source to target datasource, preserving type icons without expanding table names |
| Sync Solution | Specific type and step summary, such as "Single-table realtime · Full + incremental" |
| Schedule | Actual Cron and scheduling status when applicable; `—` otherwise |
| Status | Definition published or unpublished status, not the Instance result |
| Execution Summary | Stages and progress explicitly tied to the latest Instance, with navigation to that Instance |
| Updated | Editor and update time |
| Actions | Actions allowed by existing Task and Instance capabilities |

The server performs global filtering, sorting, and pagination, then batch-fetches Instance and Schedule summaries for visible tasks. Active Instances determine permitted actions. If the active Instance differs from the latest Instance, never combine their statuses or progress into a fabricated execution summary. Without execution history, show `—`, not a fictitious 0%.

Save, publish, run now, cancel the current execution, and enable/disable scheduling retain separate meanings. The unified UI does not remove existing publication conditions, cancellation permissions, or resource-claim rules. Canceling the current execution does not stop future scheduling; disabling scheduling does not cancel an admitted Instance. Editing restrictions for published configurations and the existing coordination between unpublishing, deletion, and scheduling remain governed by YOIP-2/4.

Saving a Task and saving its Schedule are not one atomic transaction disguised as a frontend request. If the Task save succeeds but Schedule save fails, preserve the saved Task identity and the unconfirmed Schedule draft, and clearly identify the unsaved part. Retry must not create duplicate Tasks or claim full success.

The details page retains an execution list on the left. On the right, it provides Basic Information and tabs for Execution Status and Runtime Logs, with table-level execution results below. Logs remain text rather than a table. Distinguish current Definition details from the selected Instance's pinned configuration. Stages, mappings, and logs come from that Instance; subsequent edits to the Definition must not rewrite history.

### 7. Stage Progress and Trustworthy Display

The creation flow selects steps, the editor configures them, and the execution page shows only steps actually present in that Instance. Offline and realtime share the same progress component, rendered from observed stages rather than inferred from the URL or a solution-name suffix.

| Scenario | Display semantics |
| --- | --- |
| Waiting for a step | Show pending; do not imply the step has already processed anything |
| Full stage with a reliable total | Show measured progress, for example 37% |
| Full stage completed, including target commits and handoff conditions | Show green 100% for that stage |
| Continuous incremental running | Use the established `○ >>> ○` animation, without an overall percentage that can never reach completion |
| Failure with observed progress | Stop animation and retain the last observed value in red instead of resetting it to zero |
| Failure without progress data | A red 0% display fallback is permitted, but tooltip/accessibility text must say progress was unavailable; the API value remains null |
| Stopped, canceled, or awaiting reconciliation | Show the corresponding stopped/uncertain state, not a healthy running animation |

When a realtime task's full stage reaches 100%, its Instance may still be RUNNING in the incremental stage. A bounded Task displays overall success only after its existing lifecycle confirms completion. Schema validation, Source EOF, Split completion, Sink commits, and checkpoint completion have distinct meanings and are not interchangeable.

`completedSplits / plannedSplits` measures Split progress, not committed rows or time remaining. Label the measurement basis and estimated values where applicable. A single-Split task may not have a smooth intermediate percentage. Multi-table tasks must not naively average table percentages or allocate one-third of the total to each of schema, full, and incremental stages. Use indeterminate progress when measurement is unreliable.

Failed or stale sampling must not turn into a fresh 0%, 100%, or running state. Unknown stages must not be displayed as merely pending. Following recovery, repeatedly read records must not make progress appear monotonically increasing without real work. A historical single percentage may be projected to a stage only when its meaning is known.

### 8. Configuration Changes and Reruns

Persisted tasks retain their source type, target type, and specific solution type. Express substantial solution changes through "Duplicate as New" so an existing task's execution history and recovery state are not reinterpreted as a different pipeline. Duplication copies only configuration, not enabled schedules, Instances, offsets, or target claims, and creates an unpublished task.

Within one solution type, changes to legal steps, connections, mappings, write settings, or incremental conditions require impact confirmation and a new parameter version. Running Instances must never hot-switch to new configuration. Revalidate affected schema checks, mapping previews, and recovery compatibility. Never assume old state remains usable without evidence.

A new submission and recovery are different operations. Currently, a fresh Instance is not promised to restore the previous Instance's checkpoint; UI copy must reflect actual recovery support. Unified pages do not automatically authorize cross-Instance, cross-version, or cross-process recovery, offset resets, or rerunning only failed tables. Do not display these as executable actions without corresponding public contracts and verified behavior.

### 9. Ownership, Implementation Boundaries, and Capability Exposure

The DATA_SYNC business domain owns product solution types, step constraints, user configuration, and queries for available combinations. Existing Option/Factory validation handles Connector requirements. The frontend drives its controls from that authoritative business response, rather than duplicating a database-type-to-solution rules table. No new solution-plugin family, generic form engine, or layered capability registry is necessary.

Retain the path Task Definition → DATA_SYNC Plugin → JobPlanner → PipelineBuilder → YakFlow. The Task platform owns admission, versioning, and lifecycle. Actual scheduling eligibility follows parsed steps and physical execution capability, not merely a product label. Core/Runtime maintain database and product boundaries. Connectors do not depend on business Controllers or DAOs.

Before enabling multiple tables, atomically validate and acquire all target resource claims. An error must not partly start a job and leave orphaned claims. A partial multi-table failure is not overall Task success and does not imply a cross-table transaction. Recovery and retry scope follows actual execution contracts; the UI must not arbitrarily rerun a table based solely on row status.

| Delivery boundary | Capability that may be exposed | Conditions for exposure |
| --- | --- | --- |
| Initial unified entry and pages | Verified single-table JDBC offline `FULL`; verified single-table MySQL CDC realtime `FULL, INCREMENTAL` | Existing and new versions remain readable/executable; unified pagination and scheduling regressions pass; genuine end-to-end verification; no schema migration or arbitrary step deselection |
| Batch object capabilities | Explicit multi-table selection and one-to-one target mappings in database-wide solutions | End-to-end multi-table planning, schema handling, resource claims, results, and failure semantics |
| Extended synchronization strategies | Schema migration, offline incremental, full/incremental handoff, realtime incremental-only, or bounded full-only | Each source/target path and step combination independently satisfies configuration, execution, persistent-state, and verification requirements |
| Further capabilities | Rule-based discovery, dynamic tables, shard merging, cross-Instance recovery, etc. | Additional public-state and safety-contract reviews; support cannot be inferred from this proposal alone |

Complete product vocabulary does not mean displaying all future functionality at once. Unimplemented options must not remain in the UI as permanently visible "planned" features. Only implemented capabilities that the current objects cannot use should show a clear, concise blocking reason.

Use Yak UI, shared design tokens, and existing interaction conventions. Keep helper text minimal and add it only when data-loss risk, essential prerequisites, or blocking reasons cannot be expressed by controls. Preserve keyboard and focus behavior, responsive layout, and reduced-motion support. Do not copy promotional banners, resource-purchase flows, or redundant help text from reference screenshots.

## Compatibility, Deprecation, and Migration Plan

### 现有任务和参数

版本 2 任务使用服务端确定性投影进入统一产品界面：

| 已存配置 | 产品投影 | 必须保留 |
| --- | --- | --- |
| 已验证的单表 JDBC Source 与 JDBC Sink | `SINGLE_TABLE_OFFLINE`，`FULL` | 原连接、表、写入方式、任务状态和调度配置 |
| 已验证的单表 Hybrid MySQL CDC 与 JDBC Sink | `SINGLE_TABLE_REALTIME`，`FULL, INCREMENTAL` | 初始化及持续消费的既有执行行为 |
| 未识别或与上述形态不符的配置 | 明确提示不可识别，阻止误执行 | 原始数据，不猜测方案或静默转换 |

首次新建及修改执行参数写入版本 3，并形成新的不可变 Definition Version；仅修改名称、备注和 Debug 保持现有版本规则。保持仅针对版本 2 的明确读取与执行路径，使现有已发布版本无需重新发布即可继续使用。停止新写入版本 2，不在两套参数格式间双写。

已经固定的历史版本不原地重写，Instance 继续读取自己的参数版本。兼容读取的删除需要后续明确的历史保留与迁移决策，不能仅因默认写入已经切换就删除。未来按方案筛选必须同时覆盖版本 2 的确定性投影和版本 3，不能先分页再在浏览器过滤。

界面和路由迁移不改变任务 ID、Workspace、审计字段、发布状态、实例、日志、调度启用状态或恢复目录，不触发 DDL、目标清理、自动启动或重新初始化。已应用 Flyway 脚本保持不变。若实现需要索引或新持久字段，使用前向迁移并单独验证升级，不把 SQL 混入本提案 PR。

### 页面与旧链接

迁移两边有效能力后，删除 `app/offline-sync`、`app/realtime-sync` 下独立页面和包装入口，由 `app/data-sync` 拥有统一产品页面。不是将离线页面改名后继续保留一套实时分支页面。

旧地址只在路由边界转换，不保留旧业务页面：

- 旧列表地址进入 `/data-sync`，可保留对应的离线或实时筛选含义，仍使用同一列表。
- 旧 `/:id` 编辑地址和 `/:taskId/detail` 转向统一编辑、详情地址，保留任务与 `executionId` 等受支持参数。
- 旧 `/instances/:id` 先通过授权的 Instance 查询解析所属 Definition，再定位统一详情；不可访问时按现有权限或不存在语义处理。
- 旧 `/new` 地址进入统一创建流程，可预选合法的时效偏好，但不能绕过类型组合、步骤和连接校验。

同步更新菜单、返回列表、面包屑、登录回跳允许列表、运维中心详情链接和接受测试。只允许同源、安全的内部回跳；非法目标不因兼容地址转换获得访问能力。切换 Workspace 清理缓存与过期请求，历史 URL 不成为越权读取入口。

### 提案与实施关系

本文固定产品目标、参数语义和能力开放条件。接受之后，按可验收的垂直范围提交实现 PR；首次交付不以实现全部未来能力为前提，也不允许用未实现的控件替代实际能力。实现或 CI 通过不自动将本文标记为 Accepted；决定在提案 PR 中明确记录。

## Test Plan

以下是实现的验收要求，不是此文档 PR 已运行的测试结果。

| 范围 | 必须验证的场景 |
| --- | --- |
| 类型与步骤 | 不同有向类型组合返回不同方案；默认组合合法；当前 Hybrid 不允许取消必选数据步骤；无数据步骤、未知步骤、重复步骤和不支持组合被后端拒绝 |
| 联动与网络 | 快速切换来源、去向和具体类型；乱序响应、查询失败、无候选；旧结果不污染新选择，下一步不能使用过期组合 |
| 创建草稿 | 类型先于连接；新增连接后返回；刷新、返回和取消；用户名称不被覆盖；没有有效配置前不产生持久任务 |
| 单一产品入口 | 同一页混合展示所有同步方案；筛选、排序、总数和分页由服务端统一；无离线实时分页拼接、逐行实例或 Schedule 请求 |
| 参数版本 | 版本 2 的两类任务按原语义投影和执行；新参数版本 3 保存回显一致；非法混合格式被拒绝；不修改历史快照、不自动发布或运行 |
| 映射 | 单表恰好一条映射；批量零表、重复来源、重复目标、不兼容 Schema、来源或目标命名空间越界；整库只选一张表时方案身份不变 |
| 规则边界 | 初次统一拒绝规则输入；日后验证明确过滤与持续规则的区别、切换确认、预览失效和固定实例对象集合 |
| 结构与写入 | 校验和预览不执行 DDL；未启用结构迁移仍校验目标；覆盖范围与清理次数；不兼容目标不被悄悄删除；实时变更不降级为 Append |
| 运行与调度 | 已上线条件、实际调度资格；停止实例与停用调度互不混淆；任务保存成功但调度失败后重试不重复创建；现有取消和资源占用回归 |
| 阶段与进度 | 全量 37% 到阶段 100% 后进入增量动画；实例仍运行；未知、过期、失败、取消与待确认不伪造进度；Split 口径不冒充行数；不平均无限增量进度 |
| 历史与恢复 | 当前配置变更不改变历史阶段、映射和日志；新 Instance 不隐式恢复旧检查点；复制新建不复制位点或启用调度 |
| 多表准入 | 一个目标冲突时不部分启动；全部资源原子获得或释放；局部失败不会显示整体成功，不能通过 UI 绕过任务恢复边界 |
| Workspace 与访问 | 类型查询认证、实际连接授权、批量摘要、草稿、旧链接和登录回跳的隔离；没有明文凭据、跨空间缓存或迟到响应泄露 |
| 浏览器交互 | 真实列表、创建、编辑、详情和调度操作；键盘、焦点、窄屏、长名称、异常返回和 `prefers-reduced-motion` |

保留现有格式、类型、架构、构建和 CI 检查。新开放的 JDBC、CDC、步骤和目标写入组合必须提供真实数据库的产品端到端证据；引擎单测或前端构建通过不能代替这种验收。提案文档自身检查六章节、元数据、链接和单文件范围；文档范围 CI 的跳过项不能报告为运行验收通过。

## Rejected Alternatives

| 方案 | 不采用的原因 |
| --- | --- |
| 保留离线、实时两套页面，外面套两个 Tab | 只改导航外观，查询、操作与表单仍有两套身份 |
| 四个认知维度都做独立必填下拉框 | 用户重复表达时效与策略，容易保存冲突配置 |
| 先选实际连接，再决定是否存在支持方案 | 本次选择类型先行的方案发现入口；没有连接也能了解可用链路，连接权限检查留在具体配置阶段 |
| 根据映射数量或 Connector 名称猜测具体类型 | 规则恰好匹配一张表或更换 Connector 时，任务方案身份会错误改变 |
| 让三个 Checkbox 自由组合、执行时再报错 | 无法保证配置承诺与真实计划一致，可能跳过初始化或创建不终止的 Job |
| 为单表、多表、全量、增量分别新增 Task 插件和执行引擎 | 重复既有 Task、Factory、Planner 和 YakFlow 责任，不能解决产品表达问题 |
| 为动态候选建设通用表单平台和多层能力注册框架 | 当前只需具体的同步业务契约和已有组件，不需要没有独立调用方的抽象 |
| 为统一页面重写历史参数、自动覆盖目标或恢复旧检查点 | 页面合并不能改变执行事实、数据安全和已接受恢复边界 |
| 一次展示所有未来选项，再用说明文字标记尚未实现 | 用户可见选择应对应真实可运行能力，完整产品设计不等于已经交付 |

[dw-realtime]: https://help.aliyun.com/zh/dataworks/user-guide/configure-a-data-synchronization-solution-in-data-integration
[dw-offline]: https://help.aliyun.com/zh/dataworks/user-guide/configure-a-database-wide-offline-synchronization-task
[dw-solutions]: https://help.aliyun.com/zh/dataworks/user-guide/supported-data-source-types-and-read-and-write-operations/
[dw-incremental]: https://help.aliyun.com/zh/dataworks/user-guide/configure-a-batch-synchronization-node-to-synchronize-only-incremental-data
[code-router]: https://github.com/weifuwan/yak-ops/blob/1da2d2555f4411e72550606b4fca45f5e2cdb788/yak-ops-ui/apps/web/app/router/AppRouter.tsx
[code-definition]: https://github.com/weifuwan/yak-ops/blob/1da2d2555f4411e72550606b4fca45f5e2cdb788/yak-ops-ui/apps/web/service/data-sync/definition.ts
[code-cdc]: https://github.com/weifuwan/yak-ops/blob/1da2d2555f4411e72550606b4fca45f5e2cdb788/yak-flow/yak-flow-connector-cdc-mysql/src/main/java/io/yak/ops/connector/cdc/mysql/source/MySqlCdcSourceFactory.java
[code-sink]: https://github.com/weifuwan/yak-ops/blob/1da2d2555f4411e72550606b4fca45f5e2cdb788/yak-flow/yak-flow-connector-jdbc/src/main/java/io/yak/ops/connector/jdbc/sink/JdbcSinkFactory.java
[code-execution]: https://github.com/weifuwan/yak-ops/blob/1da2d2555f4411e72550606b4fca45f5e2cdb788/yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-data-sync/src/main/java/io/yak/ops/plugin/task/datasync/config/ExecutionConfigurationFactory.java
