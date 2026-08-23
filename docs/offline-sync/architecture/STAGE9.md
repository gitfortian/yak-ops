# Offline Sync Stage 9 — Application Entry Consolidation

## Goal

Stage 8 已经把离线同步按 `definition / execution / backfill / cursor / schedule / reconcile` 等子系统归位。Stage 9 进一步收紧调用边界：**目录内部类不因为 `public` 或 Spring Bean 身份而自动成为跨子系统 API。**

本阶段固定三个稳定 Application Facade：

```text
OfflineJobDefinitionService
OfflineJobExecutionService
OfflineBackfillService
```

Controller、后台触发器以及后续其他业务模块，应优先通过这些 Facade 进入离线同步业务链，而不是直接依赖 execution 内部 Coordinator / Runtime / Query / Adapter。

## Stable application entries

### Definition

```text
OfflineJobDefinitionController
  -> OfflineJobDefinitionService
```

`OfflineJobDefinitionService` 负责定义保存、查询、上下线和删除等应用入口。它可以在内部协调 Runtime Guard、Schedule Lifecycle、Repository 等专业组件。

### Execution

```text
OfflineJobExecutionController
OfflineControlPlaneController
OfflineScheduleHandler
OfflineBackfillDispatcher
OfflineExecutionReconciler
  -> OfflineJobExecutionService
```

`OfflineJobExecutionService` 是 execution 子系统对外稳定门面。Stage 9 新增的后台入口只做委托：

```text
hasOccupyingBatch(definitionId)
executeScheduled(definitionId, triggerToken)
executePendingBackfill(batchId)
```

Batch runtime truth、claim/reservation、Link-Up submit、Retry/UNKNOWN/Cancel 等规则仍留在现有内部组件，不复制到 Facade。

### Backfill command

```text
OfflineBackfillController
  -> OfflineBackfillService
```

Backfill request 的 Scope 规范化、Snapshot 冻结、Batch materialization 和 Cursor 前置校验仍由 Backfill 子系统内部完成。

## Removed cross-subsystem shortcuts

### Schedule

Stage 8 后：

```text
OfflineScheduleHandler
  -> OfflineBatchRuntimeService
  -> OfflineExecutionOrchestrator
```

Stage 9：

```text
OfflineScheduleHandler
  -> OfflineJobExecutionService
       -> OfflineBatchRuntimeService
       -> OfflineExecutionOrchestrator
```

Schedule Handler 仍负责 schedule truth、trigger token 和 runtime fire-state 更新，但不再知道 execution 内部 Runtime / Orchestrator。

### Backfill dispatcher

Stage 8 后：

```text
OfflineBackfillDispatcher
  -> OfflineBatchRuntimeService
  -> OfflineExecutionOrchestrator
```

Stage 9：

```text
OfflineBackfillDispatcher
  -> OfflineJobExecutionService
       -> OfflineBatchRuntimeService
       -> OfflineExecutionOrchestrator
```

Dispatcher 继续负责扫描 PENDING Backfill 和 Cursor readiness；execution slot 判断与 Attempt 提交通过 Facade 进入 execution 子系统。

### Reconcile

`OfflineExecutionReconciler` 在 Stage 8 已经通过 `OfflineJobExecutionService` 执行 `applySnapshot / markUnknown / retryFrom`，Stage 9 保留该边界，不做无意义改写。

## Internal execution API

下列类型继续是 execution 子系统内部专业角色，不作为跨子系统 Application API：

```text
OfflineExecutionOrchestrator
OfflineExecutionClaimService
OfflineBatchRuntimeService
execution.query.*
execution.adapter.*
```

它们可以被 `OfflineJobExecutionService` 以及确有业务职责的稳定 Facade 内部协调，但 Schedule / Dispatcher / Controller 等外部入口不得直接依赖。

Stage 9 不通过 Java `package-private` 强行隐藏 Spring Bean，因为现有测试和后续 Stage 10/11 仍需要独立演进这些角色；本阶段用明确依赖规则和架构测试建立边界。

## Architecture guardrails

`OfflineSyncLayeringConventionTest` 增加三组规则：

1. HTTP Controller 的业务依赖只能是三个稳定 Application Facade；
2. Schedule Handler、Backfill Dispatcher、Execution Reconciler 进入 execution 时必须经过 `OfflineJobExecutionService`；
3. 非 execution 内部源码不得随意 import Orchestrator / Claim / BatchRuntime / Query / Adapter。

当前保留两个明确的 Facade 内部例外：

```text
OfflineJobDefinitionService
OfflineBackfillService
```

它们本身是稳定 Application Facade，现阶段分别需要 Batch runtime guard 和 BatchScope execution validation。Stage 9 不为了消灭 import 制造 Definition <-> Execution 循环依赖。

## Validation

新增 `OfflineJobExecutionServiceEntryPointTest` 验证：

- occupancy 查询只委托 Batch Runtime；
- Schedule trigger token 原样传给 Orchestrator；
- PENDING Backfill 提交只委托 Orchestrator；
- Facade 不复制 execution 状态规则。

`OfflineBackfillDispatcherTest` 同步改为验证 Dispatcher 只通过 execution Facade 触发运行。

## Non-goals

Stage 9 不做：

- 不重命名 `OfflineExecutionOrchestrator / OfflineExecutionClaimService / OfflineBatchRuntimeService`；
- 不拆 `OfflineExecutionOrchestrator`、Claim 或 Backfill 大类；
- 不修改 REST 路径、DTO/VO；
- 不修改数据库、Flyway；
- 不改变 Task / Batch / Attempt / Cursor 运行语义；
- 不改变 Retry / UNKNOWN / Cancel / Backfill / Schedule 业务规则；
- 不引入新的通用 `ApplicationService`、`Facade` 接口层。

## Next

```text
Stage 10 — Role Naming
```

Stage 10 再基于 Stage 7 的角色分类评估内部命名，例如：

```text
OfflineExecutionOrchestrator  -> OfflineExecutionCoordinator
OfflineExecutionClaimService  -> OfflineExecutionClaimManager
OfflineBatchRuntimeService    -> OfflineBatchRuntime
```

命名变化与 Stage 9 的调用边界收敛分开 Review。
