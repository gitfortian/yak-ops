# Offline Sync Architecture Responsibility Inventory

> 当前阶段：**Stage 7 COMPLETE — Service Responsibility Inventory**。
>
> 本文只记录当前代码事实、角色分类、依赖热点和后续迁移边界。Stage 7 **不移动 package、不重命名 Java 类、不改变运行语义**。当前有效业务规则仍以模块 `REQUIREMENTS.md + DOMAIN.md + REVIEW.md` 为准。

## 1. Stage 7 目标

Stage 6 已经把离线同步的运行语义收敛为：

```text
Task -> Batch -> Attempt -> Engine
          |
          +-> Scope / Snapshot / Retry Policy
          +-> Cursor success-only CAS
```

Stage 7 解决的是另一个问题：**代码角色已经分化，但仍大量平铺在 `service` package 中，目录无法直接表达系统结构。**

当前 `service` 下共有 16 个 Java 文件（13 个顶层类 + 3 个 `support` 类），实际包含：

- Application Facade；
- Execution Coordinator；
- Claim / Reservation Manager；
- Batch Runtime Boundary；
- Query / Log Aggregator；
- Background Dispatcher / Reconciler；
- Mapper；
- Execution Adapter；
- Definition / Schedule Support。

因此 Stage 7 的目标不是“减少 Service 数量”，而是回答：

```text
这个类在业务流程中是什么角色？
属于哪个稳定子系统？
谁可以调用它？
Stage 8 应该移动到哪里？
Stage 10 是否需要角色化命名？
Stage 11 是否需要真正拆职责？
```

## 2. Non-Goals

Stage 7 明确不做：

- 不移动 `service` 下任何 Java 类；
- 不修改 `package`；
- 不修改 Controller / Service API；
- 不修改 Repository / DAO / PO；
- 不修改表结构与 Flyway；
- 不修改 Task / Batch / Attempt / Cursor 领域语义；
- 不为了“类太大”机械拆类；
- 不提前引入新的 DDD 分层模板；
- 不提前抽 realtime/offline Shared Sync Kernel。

本阶段产物是 Stage 8 的施工底稿，不是 Stage 8 本身。

## 3. 当前 HTTP 业务入口

当前 Controller 已经自然形成三个稳定 Application Facade：

```text
OfflineJobDefinitionController
        -> OfflineJobDefinitionService

OfflineJobExecutionController
OfflineControlPlaneController
        -> OfflineJobExecutionService

OfflineBackfillController
        -> OfflineBackfillService
```

`OfflineJobScheduleController` 当前只是 Cron 表达式辅助接口，不进入任务执行服务链。

### 结论

以下三个类继续保留 `Service` 语义，作为外部业务入口候选：

1. `OfflineJobDefinitionService`
2. `OfflineJobExecutionService`
3. `OfflineBackfillService`

Stage 8/10 不应为了“去 Service 化”删除这些 Facade。真正需要处理的是它们后面的内部角色。

## 4. Role Taxonomy

后续离线同步统一使用下面的角色词汇理解类职责：

| Role | 含义 | 典型职责 |
| --- | --- | --- |
| Application Facade / Service | 对 Controller、Workflow 等提供稳定业务入口 | execute / save / backfill / query facade |
| Coordinator | 协调一次跨组件业务流程 | claim -> engine -> runtime -> event |
| Manager | 管理一类业务资源、reservation 或状态操作 | claim、cursor、resource state |
| Runtime | 维护运行时真相与状态推导 | latest Attempt -> BatchStatus |
| Query | 只负责查询、读模型或展示聚合 | page/detail/log/metrics |
| Dispatcher | 扫描待处理对象并分发工作 | pending Backfill dispatch |
| Reconciler | 对账外部运行状态并恢复本地真相 | engine reconcile / retry scan |
| Adapter | 在两个模型/边界之间做明确投影 | BatchScope -> execution JobSpec |
| Mapper | 纯模型转换 | Domain -> VO / engine metrics -> view |
| Support / Builder | 局部解析、校验、构建 | definition normalize / schedule parse |
| Gateway / Client | 外部系统协议边界 | Link-Up HTTP client |
| Repository | 领域持久化契约 | Domain persistence contract |

原则：**角色词汇表达职责，不要求所有类都立刻重命名。Stage 8 先归位，Stage 10 再决定命名。**

## 5. Service Responsibility Inventory

### 5.1 Application Facade

| 当前类 | 当前职责 | 当前主要调用方 | 目标子系统 | Stage 8 | Stage 10/11 |
| --- | --- | --- | --- | --- | --- |
| `OfflineJobDefinitionService` | Task 定义保存、上线/下线/删除、定义查询、Schedule 同步 | `OfflineJobDefinitionController`、内部执行/Backfill | `definition` | MOVE | 保留 Service；后续只在确有收益时拆内部职责 |
| `OfflineJobExecutionService` | Execution 对外门面：execute/retry/cancel/query/log/health | Execution / ControlPlane Controller、Reconciler | `execution` | MOVE | 保留 Service；Facade 本身不承担核心状态规则 |
| `OfflineBackfillService` | Backfill command：Scope 规范化、共享 Snapshot、Batch 物化、Cursor 前置校验 | `OfflineBackfillController` | `backfill` | MOVE | 保留 Service；Stage 11 评估内部 planning/materialization 拆分 |

### 5.2 Execution Command / Runtime

| 当前类 | Primary Role | 当前职责 | 目标子系统 | Stage 8 | Stage 10/11 |
| --- | --- | --- | --- | --- | --- |
| `OfflineExecutionOrchestrator` | Coordinator | claim 后提交 Link-Up、retry/cancel、applySnapshot、UNKNOWN、event、Task projection、retry 配置 | `execution` | MOVE | 候选 `OfflineExecutionCoordinator`；Stage 11 第一优先级 |
| `OfflineExecutionClaimService` | Claim / Reservation Manager | 初始 Batch+Attempt、Workflow snapshot claim、Retry claim、PENDING Backfill claim、幂等与 reservation | `execution` | MOVE | 候选 `OfflineExecutionClaimManager`；Stage 11 第二优先级 |
| `OfflineBatchRuntimeService` | Runtime Boundary | occupying Batch、latest Attempt、Attempt 持久化、BatchStatus 推导、WAITING_RETRY cancel、Cursor success hook | `execution` | MOVE | 候选 `OfflineBatchRuntime`；优先保持职责聚合 |
| `OfflineCursorService` | Cursor Manager | Cursor 初始化/查询、sourceColumn route、SUCCEEDED Batch CAS 推进 | `cursor` | MOVE | 候选 `OfflineCursorManager`；当前职责已经比较单一 |

### 5.3 Query / Read Model

| 当前类 | Primary Role | 当前职责 | 目标子系统 | Stage 8 | Stage 10/11 |
| --- | --- | --- | --- | --- | --- |
| `OfflineExecutionReadService` | Query | execution page/detail/events/table metrics；终态优先 snapshot，活动态读取 Link-Up | `execution.query` | MOVE | 候选 `OfflineExecutionQuery` |
| `OfflineExecutionLogService` | Query / Log Aggregator | 合并 Yak Ops Event 与 Link-Up 物理日志，生成统一时间线 | `execution.query` | MOVE | 候选 `OfflineExecutionLogQuery` |
| `OfflinePipelineMetricsMapper` | Mapper | Link-Up pipeline 嵌套指标 -> 页面表级扁平指标 | `execution.query` | MOVE | 保留 Mapper |
| `OfflineSyncViewMapper` | Output Mapper | Definition / Execution / Event / Engine health -> VO | `mapping` | MOVE | 保留 Mapper；禁止进入业务判断 |

### 5.4 Background Process

| 当前类 | Primary Role | 当前职责 | 目标子系统 | Stage 8 | Stage 10/11 |
| --- | --- | --- | --- | --- | --- |
| `OfflineExecutionReconciler` | Reconciler | 扫描 active Attempt、Link-Up 对账、Worker 变化 -> UNKNOWN、Retry candidate 调度 | `reconcile` | MOVE | 保留 Reconciler；不改成 `*Service` |
| `OfflineBackfillDispatcher` | Dispatcher | 扫描 PENDING Backfill、检查 Task slot / Cursor readiness、触发 Attempt 1 | `backfill` | MOVE | 保留 Dispatcher；不改成 `*Service` |

### 5.5 Support / Adapter

| 当前类 | Primary Role | 当前职责 | 目标子系统 | Stage 8 | Stage 10/11 |
| --- | --- | --- | --- | --- | --- |
| `OfflineDefinitionSupport` | Definition Builder / Support | DTO 规范化、Definition JSON、JobSpec 构建、运行时凭据解析入口、edit detail | `definition.internal` | MOVE | Stage 10 再评估 `Assembler/Builder` 命名，不急拆 |
| `OfflineScheduleSupport` | Schedule Parser / Support | Schedule JSON -> `OfflineSchedule`、Cron 规范化、RetryPolicy 输入解析 | `schedule` | MOVE | 可保留 Support，除非后续职责继续增长 |
| `OfflineBatchScopeExecutionAdapter` | Execution Adapter | frozen `BatchScope` -> frozen logical JobSpec execution predicate | `execution.adapter` | MOVE | 保留 Adapter；这是执行边界，不是通用 Helper |

## 6. 当前依赖热点

仅统计当前源码中的直接构造器依赖，目的不是用数字判定“坏代码”，而是确定 Stage 11 优先级。

| 类 | 直接依赖数 | 观察 |
| --- | ---: | --- |
| `OfflineExecutionOrchestrator` | 10 | 同时连接 Definition、Claim、3 类 Repository、Runtime、Scope Adapter、Event、Link-Up、JSON；是最大的流程协调热点 |
| `OfflineExecutionClaimService` | 7 | 同时处理初始执行、Workflow、Retry、Backfill claim 与 reservation；是状态创建热点 |
| `OfflineBackfillService` | 7 | 同时承担输入规范化、Snapshot、Scope、Cursor、Batch materialization；Stage 11 候选 |
| `OfflineJobDefinitionService` | 7 | Facade 同时连接 definition persistence、runtime guard、schedule lifecycle 和 view；先保留入口，再观察拆分收益 |
| `OfflineJobExecutionService` | 5 | 主要是 Facade fan-out，复杂度性质不同于 Orchestrator，不应仅按依赖数量拆分 |

### 6.1 最重要的判断

`OfflineExecutionOrchestrator` 大，不等于必须拆成大量小类。

Stage 11 只有在出现独立变化原因时才拆，例如：

```text
ExecutionCoordinator
  -> Engine submission
  -> State application
  -> Event recording
  -> Task runtime projection
```

如果拆分只会产生 `HelperService/CommonService/SupportService`，则不拆。

## 7. 当前调用主链

### 7.1 手动执行

```text
Controller
  -> OfflineJobExecutionService        [Application Facade]
  -> OfflineExecutionOrchestrator      [Coordinator]
  -> OfflineExecutionClaimService      [Claim/Reservation]
  -> OfflineBatchRuntimeService        [Runtime Truth]
  -> LinkUpClient                      [Engine Gateway]
  -> OfflineBatchRuntimeService        [Attempt -> Batch]
```

### 7.2 查询

```text
Controller
  -> OfflineJobExecutionService
       -> OfflineExecutionReadService
       -> OfflineExecutionLogService
       -> OfflineSyncViewMapper
```

### 7.3 Backfill

```text
Controller
  -> OfflineBackfillService
       -> Batch materialization
       -> Cursor initialization / validation

OfflineBackfillDispatcher
  -> OfflineExecutionOrchestrator
  -> OfflineExecutionClaimService
  -> Attempt 1
```

### 7.4 Reconcile / Retry

```text
OfflineExecutionReconciler
  -> LinkUpClient
  -> OfflineJobExecutionService
       -> OfflineExecutionOrchestrator
       -> OfflineExecutionClaimService (Retry)
       -> OfflineBatchRuntimeService
```

## 8. Stage 8 Package Restructuring — Recommended First Wave

Stage 8 第一波只解决当前最明显的 `service` 大平层，不同时搬动 Domain / Repository / DAO。这样可以把结构变化与领域/持久化变化分开 Review。

**Stage 8 第一波使用当前类名，不重命名：**

```text
io.yak.ops.business.sync.offline
|
|-- controller                         # 当前保留
|
|-- definition
|   |-- OfflineJobDefinitionService
|   `-- internal
|       `-- OfflineDefinitionSupport
|
|-- execution
|   |-- OfflineJobExecutionService
|   |-- OfflineExecutionOrchestrator
|   |-- OfflineExecutionClaimService
|   |-- OfflineBatchRuntimeService
|   |
|   |-- query
|   |   |-- OfflineExecutionReadService
|   |   |-- OfflineExecutionLogService
|   |   `-- OfflinePipelineMetricsMapper
|   |
|   `-- adapter
|       `-- OfflineBatchScopeExecutionAdapter
|
|-- backfill
|   |-- OfflineBackfillService
|   `-- OfflineBackfillDispatcher
|
|-- cursor
|   `-- OfflineCursorService
|
|-- reconcile
|   `-- OfflineExecutionReconciler
|
|-- schedule                           # 当前 package 已存在
|   |-- OfflineScheduleEngineBridge
|   |-- OfflineScheduleHandler
|   |-- OfflineScheduleLifecycle
|   |-- OfflineScheduleReconciler
|   `-- OfflineScheduleSupport         # 从 service/support 归位
|
|-- mapping
|   `-- OfflineSyncViewMapper
|
|-- engine                             # Stage 8 第一波保持当前结构
|-- domain                             # Stage 8 第一波保持当前结构
|-- repository                         # Stage 8 第一波保持当前结构
|-- dao                                # Stage 8 第一波保持当前结构
`-- config                              # 保持
```

### 为什么 Stage 8 第一波不同时移动 Domain / Repository / DAO？

因为当前已经有明确领域 contract 和 layering test。一次 PR 同时做：

```text
Service 分包
+ Domain 模型搬迁
+ Repository/Adapter 搬迁
+ DAO/PO 搬迁
+ 类重命名
```

会让机械 import diff 淹没真正的架构变化，也会提高回归和 Review 成本。

Stage 8 的第一目标是：

> 打开 `offline` package 后，可以直接看到 definition / execution / backfill / cursor / reconcile，而不是先进入一个 16 类的 service 大目录。

## 9. End-State Direction

Stage 8 第一波完成后，再根据 Stage 10-12 的角色命名和依赖规则决定是否继续收敛到完整业务子系统结构：

```text
offline
|-- controller
|-- definition
|-- execution
|-- backfill
|-- cursor
|-- schedule
|-- reconcile
|-- engine
|-- persistence
|-- mapping
`-- config
```

这里是**方向**，不是 Stage 7 对 Stage 8 的强制一次性改动范围。

尤其：

- Domain 模型是否跟随 definition/execution/cursor/schedule 子系统移动，要先看依赖方向；
- Repository 接口与 Adapter 是否拆为 `domain contract + persistence adapter`，要在 Stage 12 dependency governance 决定；
- 不为了目录对称制造空 package 或无意义 wrapper。

## 10. Stage 8 Migration Order

推荐按下面顺序迁移，每一步都只做 package/import 变化：

```text
1. mapping
   OfflineSyncViewMapper

2. definition
   OfflineDefinitionSupport
   OfflineJobDefinitionService

3. cursor
   OfflineCursorService

4. execution.query
   OfflinePipelineMetricsMapper
   OfflineExecutionReadService
   OfflineExecutionLogService

5. execution.adapter
   OfflineBatchScopeExecutionAdapter

6. execution command/runtime
   OfflineBatchRuntimeService
   OfflineExecutionClaimService
   OfflineExecutionOrchestrator
   OfflineJobExecutionService

7. backfill
   OfflineBackfillService
   OfflineBackfillDispatcher

8. reconcile
   OfflineExecutionReconciler

9. schedule support
   OfflineScheduleSupport
```

每个迁移 commit 应保持：

- public REST path 不变；
- Spring Bean 行为不变；
- transaction manager 不变；
- Schedule Bean/handler identity 不变；
- Repository/DAO contract 不变；
- Domain rule 不变；
- 测试同步移动/import 更新。

## 11. Stage 10 Rename Candidates

Stage 8 先移动，Stage 10 再统一角色命名。当前候选：

```text
OfflineExecutionOrchestrator
  -> OfflineExecutionCoordinator

OfflineExecutionClaimService
  -> OfflineExecutionClaimManager

OfflineBatchRuntimeService
  -> OfflineBatchRuntime

OfflineCursorService
  -> OfflineCursorManager

OfflineExecutionReadService
  -> OfflineExecutionQuery

OfflineExecutionLogService
  -> OfflineExecutionLogQuery
```

明确保留：

```text
OfflineJobDefinitionService
OfflineJobExecutionService
OfflineBackfillService
OfflineExecutionReconciler
OfflineBackfillDispatcher
OfflinePipelineMetricsMapper
OfflineSyncViewMapper
OfflineBatchScopeExecutionAdapter
```

候选不是承诺。Stage 10 仍需根据迁包后的实际可读性决定是否重命名。

## 12. Existing Guardrails

当前已有 `OfflineSyncLayeringConventionTest`，已经覆盖部分重要规则：

- Controller 不绕过 Service 直接依赖 Repository / DAO / Engine；
- Repository 不暴露 DTO / VO / PO / MyBatis 类型；
- Attempt persistence 不重新暴露 legacy Task runtime / retroactive bind；
- Repository 分页使用共享 `PageData`；
- DAO 不依赖 transport model；
- Batch/Task/Attempt/Event 表兼容映射保持。

Stage 8 必须同步调整该测试的 package/class import，但不得为了迁包删除这些约束。

Stage 13 再补业务子系统级 guardrail，例如：

```text
controller -> application facade only
query      -> no command mutation
engine     -> no controller/transport dependency
model      -> no Spring/HTTP/Link-Up dependency
reconcile  -> through execution/runtime boundary
```

## 13. Stage 7 Acceptance Checklist

- [x] 16 个 `service` 侧 Java 文件全部完成角色归类。
- [x] 确认三个稳定 Application Facade。
- [x] 明确 Coordinator / Manager / Runtime / Query / Dispatcher / Reconciler / Mapper / Adapter 等角色词汇。
- [x] 识别 ExecutionOrchestrator / ClaimService / BackfillService 等依赖热点。
- [x] 给出 Stage 8 第一波 package 目标与迁移顺序。
- [x] 给出 Stage 10 命名候选，但不提前改名。
- [x] 给出 Stage 11 拆分优先级，但不机械拆类。
- [x] 记录现有 LayeringConventionTest，作为后续迁移护栏。
- [x] 未修改 Java / REST / DB / Domain runtime semantics。

## 14. Stage 7 Conclusion

当前真正的问题不是“Service 太多”，而是：

> **多个不同角色共享一个 `service` package，导致目录无法表达离线同步的运行结构。**

Stage 7 将现有代码重新解释为：

```text
Definition      = Task 定义入口
Execution       = 执行 Facade + Coordinator + Claim + Runtime + Query
Backfill        = 补数 command + dispatcher
Cursor          = 游标规则
Schedule        = 时间触发
Reconcile       = 外部状态恢复
Engine          = Link-Up 边界
Mapping         = 输出模型转换
Persistence     = Repository / Adapter / DAO（后续阶段再治理目录）
```

因此 Stage 8 可以进入纯结构迁移，而不需要一边搬 package 一边重新讨论“这个类到底是什么”。

**Stage 7 COMPLETE. Next: Stage 8 — Package Restructuring.**
