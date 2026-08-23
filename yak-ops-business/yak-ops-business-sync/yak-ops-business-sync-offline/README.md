# Yak Ops Offline Sync

## 领域建设

离线同步运行模型固定为 `Task -> Batch -> Attempt`。Backfill/Cursor 已进入同一模型，历史 execution 仅保留持久化与查询兼容职责。

修改代码或 Review 前按顺序阅读：

- [REQUIREMENTS.md](./REQUIREMENTS.md) — 当前有效需求：模块需要什么
- [DOMAIN.md](./DOMAIN.md) — 当前硬规则：实现不能违反什么
- [REVIEW.md](./REVIEW.md) — Review 标准：按什么规则判卷
- [Domain Mapping](../../../docs/offline-sync/domain/README.md) — Stage 6 Wave 0-6 历史迁移映射
- [Architecture Responsibility Inventory](../../../docs/offline-sync/architecture/README.md) — Stage 7 职责盘点
- [Stage 8 Package Restructuring](../../../docs/offline-sync/architecture/STAGE8.md) — 业务子系统目录归位
- [Stage 9 Application Entry Consolidation](../../../docs/offline-sync/architecture/STAGE9.md) — Application 入口边界
- [Stage 10 Role Naming](../../../docs/offline-sync/architecture/STAGE10.md) — 角色命名约定
- [Stage 11 Core Responsibility Decomposition](../../../docs/offline-sync/architecture/STAGE11.md) — 核心职责拆分
- [Stage 12 Dependency Boundary Governance](../../../docs/offline-sync/architecture/STAGE12.md) — 当前 package 依赖规则
- [Legacy Compatibility Cleanup](../../../docs/offline-sync/architecture/LEGACY-COMPATIBILITY-CLEANUP.md) — Stage 12 后 Java 过渡兼容清理

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

**Stage 12 已完成：Dependency Boundary Governance。**

Stage 8-11 已经依次解决目录、入口、角色和职责热点；Stage 12 固定 top-level package 的依赖方向，并把跨子系统调用收敛成明确 corridor。

三个稳定 Application Facade：

```text
OfflineJobDefinitionService
OfflineJobExecutionService
OfflineBackfillService
```

内部专业角色：

```text
OfflineExecutionCoordinator          # 执行流程协调
OfflineExecutionClaimManager         # 新 Batch admission
OfflineExistingBatchClaimManager     # 已有 Batch -> Retry / Backfill Attempt
OfflineExecutionAttemptFactory       # frozen Batch -> persistence Attempt
OfflineExecutionStateManager         # 状态 / Retry / Event / Task projection
OfflineBatchRuntime                  # Batch runtime truth
OfflineExecutionQuery                # execution read model
OfflineExecutionLogQuery             # unified log query
OfflineCursorManager                 # cursor 内部实现
OfflineBackfillPlanner               # Scope / Cursor / Snapshot planning
```

`@Service` 仍只表达稳定 Application 入口；内部 Coordinator / Manager / Runtime / Query / Planner / Factory 使用 `@Component`。

## Java 过渡兼容清理

Stage 12 之后已删除完成使命的 Java compatibility layer：

```text
OfflineExecutionControlRepository
OfflineExecutionIdempotencyRepository
LegacyBatchTriggerCompatibilityMapper
LegacyOfflineExecutionCompatibilityMapper
```

仍有价值的能力已经归位：

- Schedule trigger identity 由 `BatchTriggerToken` 表达，token 编码与 `BatchKey.schedule(...)` 语义保持不变；
- persistence Attempt -> Core `ExecutionAttempt` 的 hydration 由 `OfflineBatchExecutionRepositoryAdapter` 负责；
- `OfflineSyncLayeringConventionTest` 禁止生产源码重新引入 `@Deprecated` 过渡实现、`domain.compat` 和上述 compatibility 类型。

这次只删除 Java 过渡层。数据库表名、Flyway、历史 execution 查询以及 `batch_id = NULL` 只读兼容继续保留。

## 显式跨子系统边界

Stage 12 新增三条窄边界：

```text
OfflineCursorGateway
OfflineExecutionScopeValidator
OfflineScheduleExecutionGateway
```

含义：

- Cursor 子系统之外只能依赖 `OfflineCursorGateway`，不能直接依赖 `OfflineCursorManager`；
- Backfill 做 execution scope 前置校验时只依赖 `OfflineExecutionScopeValidator`，不直接依赖 `execution.adapter`；
- Schedule Handler 只依赖自己定义的 `OfflineScheduleExecutionGateway`，不 import `execution.*`；该 Gateway 由 `OfflineJobExecutionService` 实现。

因此跨子系统调用不再等于“知道对方内部哪个类能用”。

## Application Entry

```text
Controller
   |
   +-> OfflineJobDefinitionService
   +-> OfflineJobExecutionService
   `-> OfflineBackfillService
```

后台入口：

```text
OfflineBackfillDispatcher
    -> OfflineJobExecutionService

OfflineExecutionReconciler
    -> OfflineJobExecutionService

OfflineScheduleHandler
    -> OfflineScheduleExecutionGateway
         ^
         |
       implemented by OfflineJobExecutionService
```

Schedule 不再直接依赖 execution package，因此 `definition -> schedule -> execution -> definition` 的 top-level 循环已经消失。

## Execution 内部链路

```text
OfflineJobExecutionService
        |
        +-> OfflineExecutionCoordinator
        |       |
        |       +-> OfflineExecutionClaimManager
        |       |       |
        |       |       +-> OfflineExecutionAttemptFactory
        |       |       `-> OfflineExistingBatchClaimManager
        |       |
        |       +-> OfflineBatchScopeExecutionAdapter
        |       +-> LinkUpClient
        |       `-> OfflineExecutionStateManager
        |
        +-> OfflineExecutionQuery
        +-> OfflineExecutionLogQuery
        `-> OfflineBatchRuntime
```

职责边界：

- `OfflineExecutionCoordinator` 只决定 claim -> scoped JobSpec -> engine -> state application 的流程顺序；
- `OfflineExecutionStateManager` 负责 Attempt 状态、metrics、Retry window、Event、Task last-* projection；
- `OfflineExecutionClaimManager` 负责创建新 Batch；
- `OfflineExistingBatchClaimManager` 负责 Retry / PENDING Backfill 在已有 Batch 上创建 Attempt；
- `OfflineExecutionAttemptFactory` 统一构造 persistence Attempt；
- `OfflineBatchRuntime` 通过 `OfflineCursorGateway` 通知 Cursor success hook，不直接依赖 CursorManager。

## Definition / Schedule 边界

Definition 的运行中保护只需要判断 Task 是否存在 occupying Batch。

Stage 12 后：

```text
OfflineJobDefinitionService
    -> OfflineBatchExecutionRepository.hasOccupyingBatch()
```

不再为了一个 runtime guard 反向依赖 `OfflineBatchRuntime`。这里仍然读取 Batch runtime truth，没有退回 Task `last-*` projection。

Definition 继续通过明确的 schedule corridor 使用：

```text
OfflineScheduleSupport
OfflineScheduleLifecycle
```

## Backfill / Cursor

```text
Backfill Request
  -> OfflineBackfillService
  -> OfflineBackfillPlanner
       -> Scope normalize
       -> existing Batch reuse
       -> shared ExecutionSnapshot
       -> OfflineCursorGateway
       -> OfflineExecutionScopeValidator
  -> PENDING Batch group materialization
  -> OfflineBackfillDispatcher
       -> OfflineCursorGateway
       -> OfflineJobExecutionService
  -> Attempt 1
```

`OfflineBackfillService` 只负责 Application command、Task lock 和 Batch materialization；规划逻辑集中在 `OfflineBackfillPlanner`。

`OfflineCursorManager` 管理 Cursor route + position + stateVersion，只在对应 `CursorRange` Batch `SUCCEEDED` 后通过 Gateway 被 `OfflineBatchRuntime` 触发 CAS 推进。

## Top-level package 依赖

当前允许的 offline 内部依赖：

| Source | Allowed |
| --- | --- |
| `controller` | `config`, `definition`, `execution`, `backfill` |
| `backfill` | `config`, `cursor`, `definition`, `domain`, `execution`, `repository` |
| `reconcile` | `config`, `domain`, `engine`, `execution`, `repository` |
| `execution` | `config`, `cursor`, `definition`, `domain`, `engine`, `mapping`, `repository`, `schedule` |
| `definition` | `config`, `domain`, `engine`, `mapping`, `repository`, `schedule` |
| `schedule` | `config`, `domain`, `repository` |
| `cursor` | `config`, `domain`, `repository` |
| `mapping` | `domain`, `engine` |
| `repository` | `config`, `dao`, `domain` |
| `dao` | `config` |
| `engine` | `config` |
| `domain` | none |
| `config` | none |

同一 top-level package 内的子 package 属于同一个子系统，例如 `execution.query` 和 `execution.adapter`；但它们不自动成为跨子系统 API。

## 工程依赖约束

当前架构护栏要求：

- Controller 只依赖三个稳定 Application Facade；
- Backfill Dispatcher / Execution Reconciler 进入 execution 只依赖 `OfflineJobExecutionService`；
- Schedule Handler 只依赖 `OfflineScheduleExecutionGateway`，不依赖 execution package；
- Definition 不依赖 execution package；
- Backfill 不直接依赖 `execution.adapter`；
- 跨子系统 Cursor 调用只依赖 `OfflineCursorGateway`；
- execution 内部角色不作为跨子系统公共 API；
- `OfflineExecutionCoordinator` 必须通过 `OfflineExecutionStateManager` 应用状态；
- `OfflineExecutionClaimManager` 组合 `OfflineExistingBatchClaimManager + OfflineExecutionAttemptFactory`；
- `OfflineBackfillService` 组合 `OfflineBackfillPlanner`；
- Repository adapter 只依赖 `domain / dao / config`；
- DAO 不反向依赖业务子系统；
- Domain 不依赖 Application / Engine / Persistence；
- 实际源码生成的 top-level package graph 必须无环。

`OfflineSyncLayeringConventionTest` 守护 Stage 7-12 的角色/分层契约与兼容清理；`OfflineSyncDependencyBoundaryTest` 专门守护 Stage 12 的 package dependency graph 和 corridor。

## Link-Up 边界

离线同步通过固定 Link-Up 地址完成执行代理：

```text
Yak Ops -> GET /api/v1/node
Yak Ops -> POST /api/v1/jobs
Yak Ops -> GET /api/v1/jobs/{jobId}
Yak Ops -> DELETE /api/v1/jobs/{jobId}
```

Batch Snapshot 只保存不含凭据的 logical JobSpec；数据源凭据在 Attempt submit boundary 才解析。

## 数据表

- `yak_offline_job_definition` — Task/current definition + query projection
- `yak_offline_batch_execution` — Batch identity、Scope、frozen Snapshot、runtime status
- `yak_offline_job_execution` — ExecutionAttempt persistence compatibility
- `yak_offline_execution_event` — Attempt event history
- `yak_offline_sync_cursor` — Task Cursor route/position/CAS version

`yak_offline_job_execution` 的表名和部分重复 snapshot 字段为了历史兼容继续保留；它们不再作为运行真相。`batch_id = NULL` 的旧记录只允许历史查询。

## Stage 状态

```text
Stage 6         COMPLETE  Domain Runtime Contract
Stage 7         COMPLETE  Service Responsibility Inventory
Stage 8         COMPLETE  Package Restructuring
Stage 9         COMPLETE  Application Entry Consolidation
Stage 10        COMPLETE  Role Naming
Stage 11        COMPLETE  Core Responsibility Decomposition
Stage 12        COMPLETE  Dependency Boundary Governance
Legacy Cleanup  COMPLETE  Transitional Java Compatibility Removal
Stage 13        DEFERRED  Architecture Guardrails
```
