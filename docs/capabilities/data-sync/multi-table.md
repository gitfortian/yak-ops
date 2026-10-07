# Data Sync Multi-Table Route Contract

Status: Active — v1.3 PR1 Persistence Foundation

Scope:

- Data Sync Task 与稳定 Table Route 的产品边界
- v1.2 单表 Task → one Route 的兼容迁移
- Route persistence / identity / ordering
- PR1 兼容读写边界

Depends On:

- [Data Sync Capability](./README.md)
- [v1.3.0 Release Contract](../../release/v1.3.0.md)
- [Task Publication Lifecycle](./task-lifecycle.md)
- [Schema / Logical Table](./schema-logical-table.md)

## 1. Goal

v1.3 不再把 Source / Target Table identity 长期绑定在 Task 根记录上。

稳定模型是：

```text
DataSyncTask
├── sourceDataSourceId
├── targetDataSourceId
├── writeMode / runtime / retry / schedule / lifecycle
└── TableRoute[]
    ├── Route A
    ├── Route B
    └── Route C
```

Table Route 表达一条稳定的：

```text
Source Table
    ↓
Target Table
```

后续 Incremental Cursor、Schema Baseline、Table Execution、Route Metrics 与 Health 都以 Route ID 作为产品身份，不通过表名字符串拼接身份。

PR1 只建立 Contract + Persistence Foundation；真正的多表 Definition Snapshot、Table Execution、Runtime 和 Editor 属于后续 PR。

## 2. Ownership

### Task owns shared policy

Task 继续拥有：

- Workspace / Name / syncType。
- Source Datasource ID。
- Target Datasource ID。
- Write Mode。
- Runtime Policy / Retry Policy。
- Schedule。
- Published / Desired State。
- definitionVersion。
- Remark。

一个 Task 固定：

```text
1 Source Datasource
1 Target Datasource
N Table Routes
```

### Route owns table semantics

每条 Route 拥有：

- stable route ID。
- source database / schema / table。
- target database / schema / table。
- Column Mapping。
- Auto Create Table。
- Task 内 sortOrder。

Route 不保存 Datasource credential，也不复制 Task 的 runtime / retry / schedule / lifecycle。

Mapping 与 Auto Create 从 v1.3 架构上属于 Route；PR1 为兼容当前单表 Runtime，Task 根记录上的历史字段暂时保留为单 Route projection。

## 3. Persistence Contract

PR1 新增：

```text
yak_ops_data_sync_table_route
```

关键约束：

```text
id                    stable route identity
workspace_id          workspace isolation
task_id               owning task
source_*              source table path
target_*              target table path
auto_create_table     per-route policy
mapping_config        per-route mapping
sort_order            stable order
```

规则：

- Route 使用 Yak Ops 统一 String snowflake ID。
- 不建立数据库物理外键。
- `(workspace_id, task_id, sort_order)` 唯一，防止一个 Task 出现不确定顺序。
- Repository 查询必须显式带 Workspace。
- Route 不是 opaque JSON；后续状态可以稳定引用 route ID。
- Task 删除由 Data Sync Service 在同一业务事务中删除当前 Route；历史 Execution 仍不级联删除。

## 4. v1.2 Compatibility Migration

v1.2 已发布 Task 仍把表级定义保存在：

```text
source_database / source_schema / source_table
target_database / target_schema / target_table
auto_create_table
mapping_config
```

v1.3 Draft Migration 将每个现有 Task 回填为一条 Route：

```text
Existing Task
        ↓
Route #0
```

回填时：

- Route ID 复用已有 Task ID，得到确定且稳定的 identity。
- taskId 仍指向原 Task。
- sortOrder = 0。
- Source / Target path、Mapping、Auto Create 原样复制。
- create / update audit 保留原 Task 的值。
- 不更新 Task `definitionVersion`。
- 不修改历史 Execution / Attempt / Event。
- 不修改历史 `definitionSnapshot`。
- 不启动任何 Runtime。
- 不修改 REALTIME desiredState 或 CDC state identity。

复用 Task ID 只用于 v1.2 历史 one-route 回填；v1.3 新建 Route 使用正常的独立雪花 ID。

## 5. PR1 Compatibility Write Boundary

PR1 还没有开放多表写 API。

当前创建 / 编辑请求仍是 v1.2 单表 DTO。为了保持 Runtime 与前端兼容，Data Sync Service 在同一事务中维护：

```text
legacy Task single-table projection
        +
one persisted Table Route
```

创建：

```text
insert Task
   ↓
insert Route #0
```

编辑：

```text
update Task projection
   ↓
update same Route identity
```

删除：

```text
delete Task
   ↓
delete current Routes
```

事务中任一持久化失败都不能留下 Task / Route 半完成状态。

如果后续已经存在多条 Route，旧单表编辑入口必须拒绝修改，不能用一条旧 DTO 静默覆盖多表定义。

## 6. Read Boundary

Task 详情和创建 / 编辑响应可以返回：

```text
tableRoutes[]
```

PR1 正常情况下只有一条 Route。

Task 分页列表暂不展开 Route 明细，避免为每行 Task 产生额外 Route 查询和大 payload；列表仍沿用当前摘要字段，PR4 再设计多表摘要 UI。

## 7. Runtime Boundary

PR1 **不改变 Runtime Source of Truth**。

当前 Execution 创建、definitionSnapshot、Schema Preview、OFFLINE / REALTIME Executor 仍继续读取 Task 根记录上的单表兼容字段。

这是有意的过渡边界：

```text
PR1
Route persistence exists
Runtime = legacy single-table projection

PR2
Definition Snapshot + Table Execution
Runtime starts consuming frozen Routes
```

因此 PR1 合并不代表“多表任务已经可以运行”。

## 8. definitionVersion

Migration 只把现有定义投影为 Route，不能推进 `definitionVersion`。

PR1 期间通过旧单表 API 修改 Source / Target / Mapping / Auto Create 时：

- 继续使用已有 executable-definition comparison。
- 真正发生可执行定义变化时，Task `definitionVersion` 按原规则 +1。
- Route 同步本身不能再次额外 +1。

后续多 Route 编辑时，任何会改变冻结可执行 Route 集合或 Route 内容的变化都必须进入统一 definitionVersion 比较；该部分由后续 Contract 冻结。

## 9. Migration Status

v1.2 已发布历史：

```text
V1__baseline.sql
V2__v1_1_0.sql
V3__v1_2_0.sql
```

永久冻结。

PR1 新增开发期 Draft：

```text
V4__data_sync_multi_table_route.sql
```

该文件只属于 v1.3 可重建开发 / E2E 历史；Release Freeze 前必须按照 Flyway Rules 与其它 v1.3 Draft 一起审查并收口为最多一个正式 `V4__v1_3_0.sql`。

## 10. PR1 Non-Goals

PR1 不做：

- 多 Route 创建 / 编辑 HTTP DTO。
- OFFLINE Multi-Table Runtime。
- Table Execution / per-table Attempt。
- Per-table Retry / Metrics。
- Multi-Table Editor。
- REALTIME Multi-Table CDC。
- Incremental Cursor。
- Catalog Refresh / Schema Diff。
- Schema Evolution。
- 删除 Task 根记录上的历史单表字段。

下一步：

```text
PR2 — Multi-Table Definition Snapshot + Table Execution
```
