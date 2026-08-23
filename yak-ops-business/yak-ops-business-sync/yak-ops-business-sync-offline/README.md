# Yak Ops Offline Sync

## 领域建设

离线同步运行模型固定为 `Task -> Batch -> Attempt`。Backfill/Cursor 已进入同一模型，legacy execution 仅保留持久化兼容职责。

修改代码或 Review 前按顺序阅读：

- [REQUIREMENTS.md](./REQUIREMENTS.md) — 当前有效需求：模块需要什么
- [DOMAIN.md](./DOMAIN.md) — 当前硬规则：实现不能违反什么
- [REVIEW.md](./REVIEW.md) — Review 标准：按什么规则判卷
- [Domain Mapping](../../../docs/offline-sync/domain/README.md) — Stage 6 Wave 0-6 历史迁移映射
- [Architecture Responsibility Inventory](../../../docs/offline-sync/architecture/README.md) — Stage 7 职责盘点
- [Stage 8 Package Restructuring](../../../docs/offline-sync/architecture/STAGE8.md) — 业务子系统目录归位
- [Stage 9 Application Entry Consolidation](../../../docs/offline-sync/architecture/STAGE9.md) — Application 入口边界
- [Stage 10 Role Naming](../../../docs/offline-sync/architecture/STAGE10.md) — 当前角色命名约定

```text
OfflineSyncTask
      │ trigger / backfill
      ▼
BatchExecution
      ├── BatchKey
      ├── BatchScope
      ├── frozen ExecutionSnapshot
      └── ExecutionAttempt 1..N
                │
                ▼
             Link-Up
```

核心约束：`Task != Batch != Attempt`。Task `last-*` 只是查询投影；Batch 是业务身份与 runtime truth；Attempt 是一次实际提交证据。

## 架构职责治理

**Stage 10 已完成：Role Naming。**

Stage 8 解决“类放在哪里”，Stage 9 解决“谁可以调用谁”，Stage 10 进一步让类名直接表达运行角色。当前生产目录继续按业务子系统组织：

```text
offline
├── controller
├── definition
├── execution
│   ├── query
│   └── adapter
├── backfill
├── cursor
├── schedule
├── reconcile
├── mapping
├── domain
├── engine
├── repository
├── dao
└── config
```

三个稳定 Application Facade 保留 `Service`：

```text
OfflineJobDefinitionService
OfflineJobExecutionService
OfflineBackfillService
```

execution 内部角色使用明确名称：

```text
OfflineExecutionCoordinator   # 流程协调
OfflineExecutionClaimManager  # Batch/Attempt claim + reservation
OfflineBatchRuntime           # Batch runtime truth
OfflineExecutionQuery         # execution read model
OfflineExecutionLogQuery      # unified log query
OfflineCursorManager          # cursor route + CAS advancement
```

`Service` 表达稳定 Application 入口；`Coordinator / Manager / Runtime / Query / Dispatcher / Reconciler / Adapter / Mapper` 表达内部专业角色。内部 Coordinator / Manager / Runtime 使用 `@Component`，不再用 `@Service` 混淆 Application Service 语义。

## Application Entry

```text
Controller
   |
   +-> OfflineJobDefinitionService
   +-> OfflineJobExecutionService
   `-> OfflineBackfillService
```

Schedule Handler、Backfill Dispatcher、Execution Reconciler 进入 execution 子系统时统一通过 `OfflineJobExecutionService`，不直接依赖 execution 内部角色。

Facade 内部可以协调专业组件；Stage 10 只改变角色命名，不复制业务规则，也不改变 Stage 9 的入口边界。

## Execution 内部链路

```text
OfflineJobExecutionService
        |
        +-> OfflineExecutionCoordinator
        |       |
        |       `-> OfflineExecutionClaimManager
        |               |
        |               `-> OfflineBatchRuntime
        |
        +-> OfflineExecutionQuery
        +-> OfflineExecutionLogQuery
        `-> OfflineBatchRuntime
```

`OfflineExecutionCoordinator` 仍负责 Attempt 提交、取消、状态落库和执行流程协调；真正的大类职责拆分留给 Stage 11。

## Link-Up 边界

离线同步通过固定 Link-Up 地址完成执行代理：

```text
Yak Ops -> GET /api/v1/node
Yak Ops -> POST /api/v1/jobs
Yak Ops -> GET /api/v1/jobs/{jobId}
Yak Ops -> DELETE /api/v1/jobs/{jobId}
```

Batch Snapshot 只保存不含凭据的 logical JobSpec；数据源凭据在 Attempt submit boundary 才解析。

## 调度与运行边界

```text
Offline Job Definition
  -> OfflineScheduleEngineBridge
  -> Yak Schedule / Quartz
  -> OfflineScheduleHandler
  -> OfflineJobExecutionService
  -> OfflineExecutionCoordinator
  -> BatchExecution / ExecutionAttempt
  -> Link-Up
```

Yak Schedule 只负责“什么时候触发”。Task 是否已有运行占用、是否能创建新 Batch，只读取 `OfflineBatchRuntime` 的 Batch runtime truth；Schedule Handler 不直接持有 Runtime / Coordinator。

Link-Up 状态对账和失败重试由 `reconcile.OfflineExecutionReconciler` 负责；Reconciler 通过 `OfflineJobExecutionService` 应用 execution 状态规则。Wave 1 前 `batch_id = NULL` 的 execution 是只读历史，不参与 Reconcile / Retry / Cancel。

## Backfill / Cursor

```text
Backfill Request
  -> OfflineBackfillService
  -> PENDING Batch group
  -> OfflineBackfillDispatcher
  -> OfflineJobExecutionService
  -> OfflineExecutionCoordinator
  -> Attempt 1
```

`OfflineCursorManager` 管理 Cursor route + position + stateVersion，只在对应 `CursorRange` Batch `SUCCEEDED` 后由 `OfflineBatchRuntime` 触发 CAS 推进。

## 工程依赖约束

Stage 10 在 Stage 9 Application Entry 护栏之上增加角色命名约束：

- Controller 只依赖三个稳定 Application Facade，不直接依赖 Repository、DAO、Engine Client 或 execution 内部组件。
- Schedule Handler / Backfill Dispatcher / Execution Reconciler 进入 execution 时只依赖 `OfflineJobExecutionService`。
- `OfflineExecutionCoordinator / OfflineExecutionClaimManager / OfflineBatchRuntime / execution.query.* / execution.adapter.*` 不作为跨子系统公共 API。
- `@Service` 保留给三个 Application Facade；内部角色使用 `@Component`。
- 生产源码不重新使用 Stage 10 已淘汰的旧角色名。
- Definition / Execution / Backfill 使用 Domain，不直接操作 MyBatis PO。
- Repository 接口只暴露 Domain；PO 与 DAO 仅存在于持久化适配层。
- Task runtime occupancy 只由 Batch Repository / Runtime 提供；Attempt Repository 不提供 `hasActiveExecution`。
- 新 Attempt 创建时必须已经绑定 Batch；不提供 retroactive `bindBatch`。
- Query 负责读取/展示，不承担 Batch/Attempt 状态命令。
- Link-Up 协议对象不直接暴露为 HTTP Domain。

这些边界由 `OfflineSyncLayeringConventionTest` 持续守护。

## 数据表

- `yak_offline_job_definition` — Task/current definition + query projection
- `yak_offline_batch_execution` — Batch identity、Scope、frozen Snapshot、runtime status
- `yak_offline_job_execution` — ExecutionAttempt persistence compatibility
- `yak_offline_execution_event` — Attempt event history
- `yak_offline_sync_cursor` — Task Cursor route/position/CAS version

`yak_offline_job_execution` 的表名和部分重复 snapshot 字段为了历史兼容继续保留；它们不再作为运行真相。`batch_id = NULL` 的旧记录只允许历史查询。

## Stage 状态

```text
Stage 6   COMPLETE  Domain Runtime Contract
Stage 7   COMPLETE  Service Responsibility Inventory
Stage 8   COMPLETE  Package Restructuring
Stage 9   COMPLETE  Application Entry Consolidation
Stage 10  COMPLETE  Role Naming
Stage 11  NEXT      Core Responsibility Decomposition
```
