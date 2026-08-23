# Offline Sync Stage 11 — Core Responsibility Decomposition

## Goal

Stage 11 在 Stage 8-10 已经稳定的目录、入口和角色命名之上，开始拆真正的职责热点。

目标不是让类变得越小越好，而是让每个核心组件只有一个主要变化原因：

```text
ExecutionCoordinator      -> 流程协调
ExecutionStateManager     -> 状态应用 / Event / Retry / Task projection
ExecutionClaimManager     -> 新 Batch admission
ExistingBatchClaimManager -> 已有 Batch continuation admission
ExecutionAttemptFactory   -> frozen Batch -> Attempt persistence model
BackfillService           -> Application command + Batch materialization
BackfillPlanner           -> Scope / Cursor / Snapshot / execution-scope planning
```

Stage 11 不改变 Task / Batch / Attempt / Cursor 领域语义，不改变 REST、数据库或 Link-Up 协议。

## 1. Execution Coordinator

### Before

`OfflineExecutionCoordinator` 同时承担：

- claim；
- scoped JobSpec；
- Link-Up submit/cancel；
- Attempt 状态迁移；
- metrics 映射；
- retry window 计算；
- Event 记录；
- Task last-* projection；
- UNKNOWN / terminal state application。

因此一个引擎流程类同时知道 Definition Repository、Execution Event Repository、RetryPolicy 和 Task projection。

### After

```text
OfflineExecutionCoordinator
  -> OfflineExecutionClaimManager
  -> OfflineBatchScopeExecutionAdapter
  -> LinkUpClient
  -> OfflineExecutionStateManager
```

Coordinator 只决定流程顺序：

```text
claim
 -> resolve scoped JobSpec
 -> probe Link-Up
 -> submit/cancel
 -> delegate state application
```

`OfflineExecutionStateManager` 独立负责：

- CREATED/SUBMITTING/FAILED/UNKNOWN 状态应用；
- Link-Up snapshot -> Attempt metrics；
- frozen RetryPolicy -> nextRetryTime；
- Attempt Event；
- Task last-* 查询投影；
- late Attempt 不覆盖 latest Attempt projection。

这样引擎流程变化和状态规则变化不再修改同一个类。

## 2. Claim decomposition

### Stable distinction

Stage 11 不再按 MANUAL / WORKFLOW / RETRY / BACKFILL 把所有 claim 规则塞进一个 Manager，而是按是否创建新 Batch 分界：

```text
OfflineExecutionClaimManager
  = new Batch + Attempt 1

OfflineExistingBatchClaimManager
  = existing Batch -> new Attempt
```

### New Batch admission

`OfflineExecutionClaimManager` 继续负责：

- Manual / Schedule 初始运行；
- Workflow snapshot/idempotency；
- BatchKey；
- frozen ExecutionSnapshot；
- Batch insert；
- Attempt 1 insert。

### Existing Batch continuation

`OfflineExistingBatchClaimManager` 负责：

- Retry 来源 Attempt 校验；
- UNKNOWN 禁止盲重试；
- latest Attempt 校验；
- frozen maxAttempts；
- retry CAS reservation；
- PENDING Backfill reservation；
- existing Batch -> next Attempt。

Retry 和 PENDING Backfill 放在同一组件，不是因为触发来源相同，而是因为二者共享同一个稳定边界：**已有 Batch 上创建 Attempt，不能创建新 Batch。**

### Attempt factory

`OfflineExecutionAttemptFactory` 统一负责：

```text
BatchExecution.snapshot
 + attemptNo
 + triggerType
 + retryFromExecutionId
 + idempotencyKey
 -> OfflineJobExecution persistence Attempt
```

避免 Initial / Retry / Backfill 三条路径分别复制 persistence model 构造规则。

`OfflineExecutionClaim` 是 claim 阶段向 Coordinator 交付的值对象，不是新的业务 Service。

## 3. Backfill decomposition

### Before

`OfflineBackfillService` 同时承担：

- Request 校验；
- Scope normalization；
- Cursor range continuity；
- Cursor route initialization；
- existing Backfill reuse；
- shared Snapshot；
- RetryPolicy freeze；
- BatchScope -> JobSpec validation；
- Batch materialization。

### After

```text
OfflineBackfillService
  -> lock Task
  -> require Definition
  -> OfflineBackfillPlanner
  -> materialize PENDING Batch group
```

`OfflineBackfillPlanner` 负责：

- Request / Scope normalization；
- existing Batch lookup + idempotent reuse；
- shared ExecutionSnapshot；
- Cursor continuity / route；
- execution-scope validation。

`OfflineBackfillService` 保持 Application Service 身份，只负责事务入口与 Batch group 持久化。

## 4. Public boundary remains unchanged

Stage 9 的 Application Entry 继续成立：

```text
Controller
  -> OfflineJobDefinitionService
  -> OfflineJobExecutionService
  -> OfflineBackfillService
```

Schedule Handler / Backfill Dispatcher / Reconciler 仍然只通过 `OfflineJobExecutionService` 进入 execution。

Stage 11 新增的：

```text
OfflineExecutionStateManager
OfflineExistingBatchClaimManager
OfflineExecutionAttemptFactory
OfflineBackfillPlanner
```

全部是内部组件，不是新的跨模块 API。

## 5. Runtime invariants preserved

Stage 11 必须继续满足：

- `Task != Batch != Attempt`；
- Retry 只在原 Batch 内创建新 Attempt；
- UNKNOWN 不能自动 Retry；
- terminal Batch 不能追加 Attempt；
- Task occupancy 只读 Batch runtime truth；
- Backfill 物化 Batch group，不创建新 Task；
- Cursor 只在对应 Batch `SUCCEEDED` 后 CAS 推进；
- Task last-* 只是 projection；
- batchless legacy execution 只读；
- Snapshot 不保存运行时数据源凭据。

## 6. Test responsibility alignment

Stage 11 同步调整测试结构：

```text
OfflineExecutionCoordinatorTest
  -> 流程委托 / scope / engine boundary

OfflineExecutionStateManagerTest
  -> Retry / UNKNOWN / Event / Task projection

OfflineExecutionClaimManagerTest
  -> new Batch admission / workflow idempotency

OfflineExistingBatchClaimManagerTest
  -> Retry / pending Backfill continuation admission

OfflineExecutionAttemptFactoryTest
  -> frozen Batch -> Attempt fields

OfflineBackfillServiceTest
  -> Batch group materialization

OfflineBackfillPlannerTest
  -> Scope / Cursor / Snapshot / execution validation
```

测试边界与生产职责保持一致，避免一个大测试类再次成为所有运行规则的集合。

## 7. Architecture guardrails

`OfflineSyncLayeringConventionTest` 增加 Stage 11 约束：

- Coordinator 必须通过 `OfflineExecutionStateManager` 应用状态；
- Coordinator 不再持有 Definition Repository / Event Repository；
- ClaimManager 必须组合 `OfflineExistingBatchClaimManager + OfflineExecutionAttemptFactory`；
- BackfillService 必须组合 `OfflineBackfillPlanner`；
- BackfillService 不再直接持有 CursorManager / ScheduleRepository / ScopeExecutionAdapter；
- 新拆出的组件保持内部 `@Component`，不能升级成新的 Application Service；
- Stage 9 Application Entry 规则继续生效。

## 8. Non-goals

Stage 11 不做：

- 不修改 REST path / DTO / VO；
- 不修改 Repository / DAO / PO contract；
- 不修改 Flyway / DB；
- 不修改 Link-Up API；
- 不重新设计 Batch/Attempt 状态机；
- 不引入 `CommonService / HelperService / Utils`；
- 不为了继续降低 LOC 再机械拆 StateManager / Planner；
- 不在本阶段移动 persistence/domain package。

## Next

```text
Stage 12 — Dependency Boundary Governance
```

Stage 12 在当前职责稳定后，进一步固定 definition / execution / backfill / cursor / schedule / engine / persistence 之间允许的依赖方向。
