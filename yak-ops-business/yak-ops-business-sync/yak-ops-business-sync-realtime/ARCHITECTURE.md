# Realtime Sync Architecture

本文件定义 Realtime Sync 本轮重构需要收敛到的**长期架构 contract**。它描述目标边界、稳定入口、运行真相与迁移约束，不记录 Stage / Wave 过程；历史迁移过程以 Git / PR 为准。

需求语义看 `REQUIREMENTS.md`，领域硬规则看 `DOMAIN.md`，Review 标准看 `REVIEW.md`。

> 当前 Definition、Execution、Reconcile、Query / Observability 已分别收敛到 `definition/`、`execution/`、`reconcile/`、`execution/query/`、`observability/`。顶层 `service/` 只剩 Environment / RuntimeResolver 迁移期内部实现；这些 transitional 结构不代表目标架构。后续重构必须在不改变现有业务 contract 的前提下继续向本文件收敛。

## 设计原则

1. **业务子系统优先，技术分层第二。** package 本身应能表达 Realtime Sync 的业务架构。
2. **稳定入口，隐藏内部角色。** Controller 和后台入口只依赖明确的 Application Facade / Gateway。
3. **名字表达角色。** `Service / Coordinator / Manager / Runtime / Resolver / Query / Reconciler / Gateway / Adapter` 不互相冒充。
4. **运行真相只有一个主人。** Task 管长期定义上下文，DefinitionVersion 管不可变发布版本，SyncExecution 管实际运行生命周期。
5. **外部系统停在边界。** Flink、Flink CDC CLI、SSH、HTTP DTO、MyBatis、Credential 不进入 Core Domain。
6. **查询与命令分离。** Observability / Query 读取事实与投影，不反向拥有 Execution command 语义。
7. **重构不偷偷改业务语义。** package move、rename、class split 与 REST / DB / Domain behavior change 分开进行。
8. **架构规则最终可执行。** 稳定后用 architecture tests 守住 package dependency graph 与跨子系统 corridor。

## Target Package Map

目标结构：

```text
io.yak.ops.business.sync.realtime
├── controller          # HTTP inbound + API DTO / VO / mapper
├── definition          # Draft / Publish / immutable DefinitionVersion
├── execution           # Start / Stop / Restart / Apply execution lifecycle
│   ├── query           # execution/task read model where execution ownership is relevant
│   └── adapter         # execution-boundary projection / preparation
├── reconcile           # runtime identity recovery + external state convergence
├── observability       # events / logs / checkpoint / metrics read side
├── environment         # Compute Environment application boundary
├── engine              # Flink / Flink CDC / SSH outbound boundary
├── repository          # domain persistence contracts + adapters
├── dao                 # MyBatis persistence primitives
├── domain              # framework-free core domain / value objects
└── config              # module configuration
```

不要把现有 `service/` 平移成新的 `common / helper / utils` 大桶。新增或迁移一个类前，先回答：它属于哪个业务子系统，它是什么角色，它允许从哪里被调用。

### Transitional packages

当前以下结构允许在迁移期间继续存在：

```text
service/
```

但规则是：

- 不再向 `service/` 增加新的宽泛业务角色；
- Definition 已迁出的 Validation / YAML / Draft / Publish 职责不得重新放回 `service/`；
- Execution 已迁出的 Start / Stop / Restart / Apply 编排职责不得重新放回 `service/`；
- Reconcile 已迁出的 runtime identity recovery / state convergence / scheduled reconcile 职责不得重新放回 `service/`；
- Query / Observability 已迁出的 read model / events / logs / SSE 职责不得重新放回 `service/`；
- `service/RealtimeJobService`、`service/RealtimeJobLifecycleCoordinator`、`service/RealtimeJobReconciler`、`service/RealtimeJobQueryService`、`service/RealtimeObservabilityService`、`service/RealtimeEventStreamService` 已退出 production，不得重新作为跨子系统入口引入；
- 后续 PR 只继续迁出 Environment / RuntimeResolver，不做新的 `service/` 兼容大桶；
- 迁出类完成后删除旧入口，不长期保留双路运行语义；
- transitional package 不是稳定 API，不能据此设计新的跨包依赖。

## Core Domain Model

领域主线固定为：

```text
RealtimeSyncTask
      │ publish
      ▼
DefinitionVersion (immutable)
      │ start / restart / apply
      ▼
SyncExecution
      │
      ▼
Engine Adapter
```

核心关系：

```text
Task != DefinitionVersion != SyncExecution
```

运行时配置：

```text
SyncDefinition
├── SourceEndpoint
├── SinkEndpoint
├── SyncRoute[]
├── SyncPolicy
└── ExecutionPolicy
```

`SyncDefinition` 是唯一配置事实；Wizard、Yak YAML、HTTP DTO、DB JSON、Flink YAML 都只是 Adapter / Projection。

## Truth Ownership

```text
RealtimeSyncTask            = long-lived task identity + current draft/published references
DefinitionVersion           = immutable published definition truth
SyncExecution               = desired/observed lifecycle + runtime execution truth
RuntimeEnvironmentSnapshot  = execution-time compute environment truth
Runtime Identity            = external job recovery identity
Task last/latest-*          = query projection / compatibility only
Flink Job                   = external runtime evidence
```

如果一个状态可以同时由 Task 字段、Deployment compatibility mirror 和 SyncExecution 决定，必须以 `SyncExecution` 为运行真相，并把其他字段限制为 compatibility / projection。

## Stable Application Entries

稳定入口按 use-case 划分：

```text
RealtimeJobDefinitionService
RealtimeJobExecutionService
RealtimeJobQueryService
RealtimeObservabilityService
ComputeEnvironmentService
```

入口关系：

```text
RealtimeJobController
   ├── RealtimeJobDefinitionService
   ├── RealtimeJobExecutionService
   ├── RealtimeJobQueryService
   └── RealtimeObservabilityService

ComputeEnvironmentController
   └── ComputeEnvironmentService
```

`@Service` 只用于这种稳定 Application Facade。内部专业角色使用更准确的角色名和 `@Component` / 普通对象。

Definition、Execution、Reconcile、Query / Observability 的内部职责已经迁出 `service/`。当前只剩 RuntimeResolver、Environment 内部实现需要继续收敛。

## Definition Subsystem

Definition 子系统负责：

```text
Create Task Shell
 -> Save Draft
 -> Validate Definition
 -> Publish immutable DefinitionVersion
```

当前协作结构：

```text
RealtimeJobDefinitionService
        |
        +-> RealtimeDefinitionManager
        |       `-> Draft create / save / delete
        +-> RealtimeDefinitionPublisher
        |       `-> publish / persisted runtime validation
        +-> RealtimeDefinitionValidator
        |       `-> unsaved definition preflight
        +-> RealtimeSourceConfigDigestCalculator
        |       `-> Draft/source compatibility digest
        +-> RealtimeYamlCodec
        |       `-> Yak Realtime YAML adapter
        `-> adapter/CdcPipelineSpecCompatibilityMapper
                `-> legacy Spec <-> Core SyncDefinition
```

角色规则：

- `RealtimeJobDefinitionService` 是 Definition 唯一稳定 Application Facade；
- `Manager / Publisher / Validator / Codec / DigestCalculator / Adapter` 是内部角色，不使用 `@Service`；
- Definition 内部迁移完成后不保留旧 Validation / YAML / Draft / Publish 代理层。

核心约束：

- Draft 可以继续编辑；
- Published Version 不可变；
- 运行中的 SyncExecution 不读取 current Draft；
- Publish 在外部 runtime validation 后必须重新校验 Draft revision、source config digest 与 runtime environment binding；
- `DefinitionDigest`、source config digest、artifact digest 分属不同语义；
- source config digest 必须包含 logical Spec 与 RuntimeEnvironmentRef，不能与 artifact digest 混用；
- Wizard / Yak YAML 只转换同一个 `SyncDefinition`，不得建立第二套业务定义；
- Yak YAML 不允许承载连接密码等 Credential；
- Compatibility mapper 必须停在拥有兼容协议的边界，不长期留在 Core Domain。

## Execution Core

Execution 子系统负责命令生命周期：

```text
Start
Stop
RestartExecution
ApplyPublishedVersion
```

当前协作结构：

```text
RealtimeJobExecutionService                 @Service / stable application facade
        |
        +-> RealtimeExecutionCoordinator     @Component
        |       +-> RealtimeExecutionStarter
        |       +-> RealtimeExecutionStateManager
        |       `-> RealtimeExecutionReplacementManager
        |
        +-> RealtimeExecutionPreparation     @Component
        |       `-> runtime capabilities read boundary
        |
        +-> RealtimeReconcileCoordinator     reconcile corridor
        `-> RealtimeDeleteSafetyChecker      reconcile safety corridor

RealtimeExecutionStarter
        +-> RealtimeExecutionPreparation
        +-> RealtimeExecutionReservationManager
        `-> RealtimeExecutionStateManager

RealtimeExecutionReplacementManager
        +-> RealtimeExecutionPreparation
        +-> RealtimeExecutionReservationManager
        +-> RealtimeExecutionStateManager
        `-> RealtimeExecutionStarter
```

角色语义：

- `Coordinator`：只负责同一 Task 的 in-process command serialization 与顶层编排；
- `Preparation`：固定 DefinitionVersion、RuntimeEnvironmentSnapshot、compiled artifact，并负责提交边界 Credential 短生命周期；
- `ReservationManager`：拥有 Idempotency-Key、single Active/Uncertain claim、replacement-stop reservation 与 DB linearization point；
- `StateManager`：拥有 Start result commit、Stop、stop-during-start、UNKNOWN/STOPPED 状态提交；
- `Starter`：执行 prepare -> reservation -> external submit -> state commit；
- `ReplacementManager`：拥有 RestartExecution / ApplyPublishedVersion 的 target pinning、replacement intent 与 resume contract；
- `RealtimeJobExecutionService` 是唯一稳定 Execution Application Facade；内部角色全部使用 `@Component`，不冒充 Application Service。

`service/RealtimeJobService` 已从 production 删除。Stage 2 / Wave 5 行为测试通过 test-scope source-compatible adapter 继续运行真实拆分后的 Execution Core；该 adapter 不进入 production artifact，也不是 Spring Bean。

必须保持以下 contract：

- 同一 Task 最多一个 Active / Uncertain Execution；
- Start 先 DB reservation，再外部 submit；
- Idempotency-Key race 可以通过已存在 Execution 恢复；
- prepared DefinitionVersion 在提交前重新校验；
- Stop during Start 返回 JobId 后必须绑定并立即取消该精确外部 Job；
- Stop 结果不确定必须进入 `UNKNOWN`，不能伪装 `STOPPED`；
- RestartExecution 固定当前 Execution 的原 DefinitionVersion；
- ApplyPublishedVersion 固定命令开始时的 Published Version；
- replacement intent 必须持久化 command type / target / Idempotency-Key，STOPPED 后仍可恢复；
- `UNKNOWN / CONFLICT` 先 reconcile，不猜测失败或创建第二实例；
- artifact digest 属于 compiled execution artifact，不与 source config / Definition digest 混用。

## Reconcile Subsystem

Realtime Sync 的外部 Flink Job 是长生命周期事实，Reconcile 是一级业务子系统，不是附属定时任务。

当前协作结构：

```text
RealtimeJobExecutionService
        |
        +-> RealtimeReconcileCoordinator
        |       +-> RealtimeRuntimeIdentityRecovery
        |       `-> RealtimeRuntimeStateReconciler
        |
        `-> RealtimeDeleteSafetyChecker

RealtimeReconciler (@Scheduled)
        |
        +-> reconcile lease
        `-> RealtimeReconcileCoordinator.reconcileAll()
```

角色语义：

- `RealtimeReconcileCoordinator`：统一手工与批量对账入口，负责候选 Execution 迭代和连续 Engine failure threshold；
- `RealtimeRuntimeIdentityRecovery`：只通过持久化 deterministic runtime identity 查找 JobId，负责 recovery grace window 与唯一匹配回填；
- `RealtimeRuntimeStateReconciler`：只根据 `SyncExecution desired state + Flink RuntimeStatus` 收敛 observed state；
- `RealtimeDeleteSafetyChecker`：删除元数据前同时验证本地 Execution terminality 与外部 Flink inactivity；
- `RealtimeReconciler`：只负责定时触发与多实例 lease，拿不到 lease 就不执行批量对账；
- Reconcile 内部角色全部使用 `@Component`，不新增第二个 Application Service。

Reconcile 必须保持：

- 提交结果不确定时，只有 deterministic runtime identity 可以恢复 JobId；
- runtime identity 唯一匹配才允许绑定 JobId；
- 多匹配时 STARTING/RUNNING 类状态进入 `CONFLICT`，STOPPING 保持 `UNKNOWN`，绝不猜任意 JobId；
- orphan recovery grace window 内不提前把未发现 Job 当作终态；
- grace window 后，`desired=RUNNING` 且确认无匹配 runtime 才收敛 FAILED；`desired=STOPPED` 才收敛 STOPPED；
- `RuntimeStatus.UNKNOWN` 只能收敛 `UNKNOWN`，不能伪造 FAILED / STOPPED；
- `desired=RUNNING` 但 Flink 已 TERMINATED/NONE 才作为 lost runtime 收敛 FAILED；
- `desired=STOPPED` 但 Flink 仍 RUNNING 时先收敛 STOPPING，再停止该精确 Job；
- 连续 Engine 失败达到 `reconcileFailureThreshold` 才标记 UNKNOWN，单次短暂故障不污染运行状态；
- 成功对账后清除该 Task 的连续 Engine failure 计数；
- 删除前若 Flink 仍 RUNNING 或状态 UNKNOWN，必须拒绝删除；
- 多实例后台 reconcile 必须先取得 `yak_realtime_runtime_lease`。

`service/RealtimeJobLifecycleCoordinator` 与 `service/RealtimeJobReconciler` 已从 production 删除。Stage 2 / lifecycle 行为测试可通过 test-scope source-compatible fixture 继续执行真实拆分后的 Reconcile Core；该 fixture 不进入 production artifact。

## Query / Observability

Query / Observability 是纯 read side。它们可以组合持久化投影与 Flink 运行证据，但不能拥有 Execution command truth。

当前协作结构：

```text
RealtimeJobQueryService                    @Service / stable query facade
        `-> RealtimeJobReadModelQuery       @Component
                +-> RealtimeJobListQuery
                `-> RealtimeJobStore view projection

RealtimeObservabilityService               @Service / stable observability facade
        +-> RealtimeObservabilityReader     @Component
        |       +-> RealtimeJobStore
        |       +-> RuntimeEnvironmentSnapshot resolver
        |       `-> FlinkObservabilityClient
        +-> RealtimeEventQuery              @Component
        |       `-> persisted event projection
        `-> RealtimeEventStream             @Component
                `-> AFTER_COMMIT event broadcast + SSE heartbeat
```

职责固定为：

- `RealtimeJobReadModelQuery`：任务 detail / page projection，只读 Repository contract；
- `RealtimeObservabilityReader`：submission log、runtime log、checkpoint / metrics snapshot 等 Flink read evidence；
- `RealtimeEventQuery`：读取持久化 execution event；
- `RealtimeEventStream`：广播已提交事务的 change event 与 heartbeat，不修改业务状态；
- `RealtimeJobQueryService / RealtimeObservabilityService` 是稳定 Application Facade，内部角色使用 `@Component`。

read-side contract：

- detail / page / events / logs / metrics / checkpoints 都不得触发 Start / Stop / Restart / Apply / Reconcile；
- Query / Observability 不依赖 `SyncExecutionStateMachine`；
- Query / Observability 不依赖 Execution Coordinator / Reservation / State / Replacement 角色；
- Query / Observability 不依赖 Reconcile command 角色；
- submission log 只依赖 Idempotency-Key，因此 JobId 尚未恢复时仍可读取；
- runtime log / metrics / checkpoint 等需要精确 Flink Job 时必须要求已绑定 JobId，不能按任务名猜测；
- SSE 只消费事务提交后的 `RealtimeJobChangeEvent`，不成为新的 state owner；
- Flink REST 不可用时 read side 可以返回读取失败，但不得反向写 `UNKNOWN / FAILED / STOPPED`。

`service/RealtimeJobQueryService`、`service/RealtimeObservabilityService`、`service/RealtimeEventStreamService` 已从 production 删除。

## Environment / Engine Boundary

Compute Environment 是邻接上下文，不是 SyncDefinition 的内部字段集合。

```text
Execution
   |
RuntimeEnvironmentSnapshot
   |
Engine Gateway
   |
   +-> Flink REST
   +-> Flink CDC CLI
   `-> LOCAL / SSH execution adapter
```

边界规则：

- Execution 保存运行环境 Snapshot，不跟随后续环境修改漂移；
- Credential 只在提交边界短暂解析与使用；
- Pipeline YAML 只作为提交 artifact，不能成为长期业务真相；
- SSH password 不由 Realtime Sync 托管；
- Flink / SSH DTO、Process/HTTP 实现、secret 不进入 Core Domain。

## Persistence Boundary

目标依赖方向：

```text
Domain       -> no framework / persistence / engine dependency
Repository   -> Domain contracts + DAO adapter
DAO          -> persistence primitives
Engine       -> external protocol boundary
```

Repository contract 不暴露 MyBatis PO / Mapper / Controller DTO；DAO 不依赖 Application Service；Core Domain 不依赖 Spring/Jackson/MyBatis/Flink/SSH。

## Migration Rules

后续结构改造必须遵守：

1. **先锁行为，再移动代码。** Start / Stop / Restart / Apply / Reconcile 的安全测试先于大规模拆分。
2. **一个 PR 一个主要边界。** definition、execution、reconcile、observability、environment 分开迁移。
3. **不做双真相兼容层。** 新入口稳定后删除旧 production 入口，不让两套 service 同时决定状态；test-scope fixture 不属于 production compatibility layer。
4. **不借重构改 REST / DB / Domain semantics。** 真正需要行为变化时单独走 Requirement / Domain review。
5. **不提前抽 realtime/offline Shared Sync Kernel。** 两个模块可以共享工程思想，但 Core Domain 独立演进。
6. **现有 architecture test 是安全网，不是最终 dependency contract。** package 收敛后再增加完整 dependency graph / corridor guardrails。

## Change Rule

新增或移动代码前，依次回答：

1. 它属于 Definition、Execution、Reconcile、Observability、Environment、Engine 还是 Persistence？
2. 它是什么角色？名字是否表达职责？
3. 谁是它的稳定调用入口？
4. 它读取或修改的 truth 属于 Task、DefinitionVersion、SyncExecution、Environment Snapshot 还是外部 Runtime Evidence？
5. 是否跨子系统？如果是，应该通过哪个 Facade / Gateway？
6. 是否把 Flink / SSH / Credential / DTO / PO 泄漏进 Core Domain？
7. 哪个测试能证明迁移没有改变现有 contract？

答不清楚时，不要创建新的 `Helper / Common / Utils / Base`；先把边界设计清楚。
