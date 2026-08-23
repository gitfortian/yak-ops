# Yak Ops Offline Sync

## 领域建设

离线同步运行模型固定为 `Task -> Batch -> Attempt`。Backfill/Cursor 已进入同一模型，legacy execution 仅保留持久化兼容职责。

修改代码或 Review 前按顺序阅读：

- [REQUIREMENTS.md](./REQUIREMENTS.md) — 当前有效需求：模块需要什么
- [DOMAIN.md](./DOMAIN.md) — 当前硬规则：实现不能违反什么
- [REVIEW.md](./REVIEW.md) — Review 标准：按什么规则判卷
- [Domain Mapping](../../../docs/offline-sync/domain/README.md) — Stage 6 Wave 0-6 历史迁移映射
- [Architecture Responsibility Inventory](../../../docs/offline-sync/architecture/README.md) — Stage 7 职责盘点
- [Stage 8 Package Restructuring](../../../docs/offline-sync/architecture/STAGE8.md) — 当前业务子系统目录

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

**Stage 8 已完成：Package Restructuring。**

离线同步不再把不同角色平铺在一个 `service` 包中。当前生产目录按业务子系统组织：

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

三个稳定 Application Facade：

```text
OfflineJobDefinitionService
OfflineJobExecutionService
OfflineBackfillService
```

其余 Coordinator / Runtime / Query / Dispatcher / Reconciler / Adapter / Mapper 均作为内部专业角色归入所属子系统。Stage 8 只改变 package/import，不改变业务语义；类级角色命名和核心大类拆分留给后续阶段。

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
  -> BatchExecution
  -> ExecutionAttempt
  -> Link-Up
```

Yak Schedule 只负责“什么时候触发”。Task 是否已有运行占用、是否能创建新 Batch，只读取 Batch runtime truth。

Link-Up 状态对账和失败重试由 `reconcile.OfflineExecutionReconciler` 负责；Wave 1 前 `batch_id = NULL` 的 execution 是只读历史，不参与 Reconcile / Retry / Cancel。

## Backfill / Cursor

```text
Backfill Request
  -> PENDING Batch group
  -> dispatcher reservation
  -> Attempt 1
```

V1 同 Task 保持单 occupying Batch。Cursor 独立持久化 route + position + stateVersion，只在对应 `CursorRange` Batch `SUCCEEDED` 后 CAS 推进。

## 工程依赖约束

Stage 8 改变目录，不改变已有依赖边界：

- Controller 通过 Application Facade 进入业务链路，不直接依赖 Repository、DAO 或 Link-Up Client。
- Definition / Execution / Backfill 使用 Domain，不直接操作 MyBatis PO。
- Repository 接口只暴露 Domain；PO 与 DAO 仅存在于持久化适配层。
- Task runtime occupancy 只由 Batch Repository / Runtime 提供；Attempt Repository 不提供 `hasActiveExecution`。
- 新 Attempt 创建时必须已经绑定 Batch；不提供 retroactive `bindBatch`。
- Query 负责读取/展示，不承担 Batch/Attempt 状态命令。
- Link-Up 协议对象不直接暴露为 HTTP Domain。

## 数据表

- `yak_offline_job_definition` — Task/current definition + query projection
- `yak_offline_batch_execution` — Batch identity、Scope、frozen Snapshot、runtime status
- `yak_offline_job_execution` — ExecutionAttempt persistence compatibility
- `yak_offline_execution_event` — Attempt event history
- `yak_offline_sync_cursor` — Task Cursor route/position/CAS version

`yak_offline_job_execution` 的表名和部分重复 snapshot 字段为了历史兼容继续保留；它们不再作为运行真相。`batch_id = NULL` 的旧记录只允许历史查询。

## Stage 状态

```text
Stage 6  COMPLETE  Domain runtime contract
Stage 7  COMPLETE  Service Responsibility Inventory
Stage 8  COMPLETE  Package Restructuring
Stage 9  NEXT      Application Entry Consolidation
```
