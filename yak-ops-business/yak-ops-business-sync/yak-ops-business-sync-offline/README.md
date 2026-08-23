# Yak Ops Offline Sync

## 领域建设

离线同步 **Stage 6 已完成**。当前运行模型固定为 `Task -> Batch -> Attempt`，Backfill/Cursor 已进入同一模型，legacy execution 仅保留持久化兼容职责。

修改代码或 Review 前按顺序阅读：

- [REQUIREMENTS.md](./REQUIREMENTS.md) — 当前有效需求：模块需要什么
- [DOMAIN.md](./DOMAIN.md) — 当前硬规则：实现不能违反什么
- [REVIEW.md](./REVIEW.md) — Review 标准：按什么规则判卷
- [Domain Mapping](../../../docs/offline-sync/domain/README.md) — Stage 6 Wave 0-6 历史迁移映射
- [Architecture Responsibility Inventory](../../../docs/offline-sync/architecture/README.md) — Stage 7 Service 职责盘点与 Stage 8 迁移施工图

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

**Stage 7 已完成：Service Responsibility Inventory。**

Stage 7 只做职责盘点，不修改 Java package、类名、REST、数据库或运行语义。当前 `service` 大目录中的类已经按下面的稳定角色完成分类：

```text
Application Service / Facade
Coordinator
Manager / Runtime
Query
Dispatcher / Reconciler
Adapter / Mapper
Support / Builder
```

下一阶段 Stage 8 将优先把 `service` 大平层按 `definition / execution / backfill / cursor / reconcile / mapping` 等业务子系统归位；第一波只做 package/import 结构迁移，不同时重命名类或改领域模型。

详细 inventory、依赖热点、目标目录与迁移顺序见 [Architecture Responsibility Inventory](../../../docs/offline-sync/architecture/README.md)。

## Link-Up 边界

离线同步一期通过固定 Link-Up 地址完成执行代理：

```text
Yak Ops -> GET /api/v1/node
Yak Ops -> POST /api/v1/jobs
Yak Ops -> GET /api/v1/jobs/{jobId}
Yak Ops -> DELETE /api/v1/jobs/{jobId}
```

Link-Up 地址统一来自：

```yaml
yak:
  sync:
    offline:
      engine:
        base-url: http://127.0.0.1:18080
```

Batch Snapshot 只保存不含凭据的 logical JobSpec；数据源凭据在 Attempt submit boundary 才解析。

## 调度与运行边界

任务级 Cron 时间触发统一交给 Yak Framework Schedule：

```text
Offline Job Definition
  -> OfflineScheduleEngineBridge
  -> Yak Schedule / Quartz
  -> OfflineScheduleHandler
  -> BatchExecution
  -> ExecutionAttempt
  -> Link-Up
```

Yak Schedule 只负责“什么时候触发”。任务是否已有运行占用、是否能创建新 Batch，只读取 Batch runtime truth。

Link-Up 状态对账和失败重试由 `OfflineExecutionReconciler` 负责；扫描对象只包含已经绑定 Batch 的 Attempt。Wave 1 前 `batch_id = NULL` 的 execution 是只读历史，不参与 Reconcile / Retry / Cancel。

## Backfill / Cursor

Backfill 一次请求创建一组 PENDING Batch，不创建新的 Task 类型：

```text
Backfill Request
  -> PENDING Batch group
  -> dispatcher reservation
  -> Attempt 1
```

V1 同 Task 仍保持单 occupying Batch。Cursor 独立持久化 route + position + stateVersion，只在对应 `CursorRange` Batch `SUCCEEDED` 后 CAS 推进。

## 工程分层

> 以下是 Stage 7 时仍然有效的当前分层约束。Stage 8 会做 package 结构迁移，但不会绕过这些依赖规则；规则的正式更新应和迁移代码一起 Review。

```text
Controller -> DTO -> Service -> Domain
           -> Repository -> Adapter -> DAO
           -> BaseMapper / Mapper XML -> PO -> MySQL

Domain -> View Mapper -> VO -> Controller
```

约束：

- Controller 只通过 Service 进入业务链路，不直接依赖 Repository、DAO 或 Link-Up Client。
- Service 和运行状态机使用 Domain，不直接操作 MyBatis PO。
- Repository 接口只暴露 Domain；PO 与 DAO 仅存在于持久化适配层。
- Task 级 runtime occupancy 只由 Batch Repository / Runtime Service 提供；Attempt Repository 不提供 `hasActiveExecution`。
- 新 Attempt 创建时必须已经绑定 Batch；不提供 retroactive `bindBatch`。
- 分页遵循 `DAO IPage<PO> -> Adapter PageData<Domain> -> Service PagingData<VO>`。
- HTTP 分页继续保持 `bizData + pagination`。
- 普通单表操作使用 MyBatis-Plus；行锁/CAS 等数据库原子语义可以进入 DAO/Mapper。
- Link-Up 协议对象只存在于 engine/service 内部，不直接暴露为 HTTP Domain。

## 数据表

当前核心表：

- `yak_offline_job_definition` — Task/current definition + query projection
- `yak_offline_batch_execution` — Batch identity、Scope、frozen Snapshot、runtime status
- `yak_offline_job_execution` — ExecutionAttempt persistence compatibility
- `yak_offline_execution_event` — Attempt event history
- `yak_offline_sync_cursor` — Task Cursor route/position/CAS version

`yak_offline_job_execution` 的表名和部分重复 snapshot 字段为了历史兼容继续保留；它们不再作为运行真相。`batch_id = NULL` 的旧记录只允许历史查询。

## 数据库迁移

Flyway 使用 `yak_offline_core_schema_history`。Stage 6 相关 contract：

```text
V2  Batch identity + nullable Attempt.batch_id
V3  Batch runtime status backfill
V4  Batch logical JobSpec + Cursor persistence
V5  legacy LOST -> UNKNOWN normalization
```

不创建物理 FK，不猜测回填 Wave 1 前 batchless execution，也不为了改名重建历史表。

## Stage 状态

```text
Stage 6  COMPLETE  Domain runtime contract
Stage 7  COMPLETE  Service Responsibility Inventory
Stage 8  NEXT      Package Restructuring
```

Stage 6 波次：

```text
Wave 0  DONE  Core VO + compatibility mapper
Wave 1  DONE  Batch persistence + execution.bind(batch_id)
Wave 2  DONE  Trigger -> Batch -> Attempt 1 + Schedule BatchKey
Wave 3  DONE  Retry / UNKNOWN + durable retry reservation
Wave 4  DONE  Runtime truth -> Batch/Attempt; Task last-* projection only
Wave 5  DONE  Backfill group / BatchScope / Cursor success-only CAS
Wave 6  DONE  Legacy runtime cleanup + compatibility contract

Stage 6 COMPLETE
```
