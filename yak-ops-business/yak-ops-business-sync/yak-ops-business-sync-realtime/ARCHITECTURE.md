# Realtime Sync Architecture

本文件定义 Realtime Sync 的**长期架构 contract**。它描述稳定边界、角色、运行真相与依赖方向，不记录 Stage / Wave 过程；历史演进以 Git / PR 为准。

需求语义看 `REQUIREMENTS.md`，领域硬规则看 `DOMAIN.md`，Review 标准看 `REVIEW.md`。

> Definition、Execution、Reconcile、Query / Observability、Environment 已分别收敛到明确业务子系统。production `service/` 大桶已经退出；后续治理不得重新创建宽泛 Service 层绕过这些边界。

## 设计原则

1. **业务子系统优先，技术分层第二。** package 本身必须能表达 Realtime Sync 架构。
2. **稳定入口，隐藏内部角色。** Controller / scheduler 只依赖明确的 Application Facade 或专业 Coordinator。
3. **名字表达角色。** `Service / Coordinator / Manager / Resolver / Query / Reader / Reconciler / Gateway / Adapter` 不互相冒充。
4. **运行真相只有一个主人。** Task 管长期定义上下文，DefinitionVersion 管不可变发布版本，SyncExecution 管运行生命周期。
5. **外部系统停在边界。** Flink、Flink CDC CLI、SSH、HTTP DTO、MyBatis、Credential 不进入 Core Domain。
6. **查询与命令分离。** Query / Observability 读取事实与投影，不反向拥有 Execution command 语义。
7. **Environment 是邻接上下文。** Definition 引用 Environment，Execution 冻结 RuntimeEnvironmentSnapshot；环境后续修改不能让历史 Execution 漂移。
8. **重构不偷偷改业务语义。** package move、rename、class split 与 REST / DB / Domain behavior change 分开进行。
9. **架构规则必须可执行。** Architecture tests 与最终 dependency corridor guardrails 共同守住依赖方向。

## Package Map

```text
io.yak.ops.business.sync.realtime
├── controller          # HTTP inbound + API DTO / VO / mapper
├── definition          # Draft / Publish / immutable DefinitionVersion
├── execution           # Start / Stop / Restart / Apply lifecycle
│   └── query           # task/execution read model
├── reconcile           # runtime identity recovery + state convergence
├── observability       # events / logs / checkpoint / metrics read side
├── environment         # Compute Environment + runtime snapshot resolution
├── engine              # Flink / Flink CDC / SSH outbound boundary
├── repository          # domain persistence contracts + adapters
├── dao                 # MyBatis persistence primitives
├── domain              # framework-free core domain / value objects
└── config              # module configuration
```

production 不再使用：

```text
service/
common/
helper/
utils/
```

作为业务职责大桶。新增类前必须先回答：它属于哪个业务子系统、是什么角色、允许从哪里被调用。

### Retired service package

以下旧 production 入口已经退出：

```text
service/RealtimeJobService
service/RealtimeJobLifecycleCoordinator
service/RealtimeJobReconciler
service/RealtimeJobQueryService
service/RealtimeObservabilityService
service/RealtimeEventStreamService
service/ComputeEnvironmentService
service/RealtimeRuntimeResolver
```

规则：

- 不得重新创建新的宽泛 `service/` 业务层；
- 已迁出的职责不得通过 compatibility facade 回流；
- 测试源码可短期保留 source-compatible fixture / alias，用于证明行为迁移不改 contract；这些类型不能进入 production artifact，也不能成为 Spring Bean；
- 跨子系统依赖必须通过稳定 Facade、专业 Resolver、Repository contract 或 Engine Gateway 表达。

## Core Domain Model

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
RealtimeSyncTask            = long-lived identity + current draft/published references
DefinitionVersion           = immutable published definition truth
SyncExecution               = desired/observed lifecycle + runtime execution truth
RuntimeEnvironmentSnapshot  = execution-time environment truth
Runtime Identity            = external job recovery identity
Task last/latest-*          = query projection / compatibility only
Flink Job                   = external runtime evidence
```

如果 Task 字段、Deployment compatibility mirror 与 SyncExecution 对同一运行状态有不同表达，必须以 `SyncExecution` 为运行真相。

## Stable Application Entries

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

`@Service` 只用于这种稳定 Application Facade。内部专业角色使用 `@Component` 或普通对象。

## Definition Subsystem

```text
RealtimeJobDefinitionService                 @Service
        |
        +-> RealtimeDefinitionManager         @Component
        +-> RealtimeDefinitionPublisher       @Component
        +-> RealtimeDefinitionValidator       @Component
        +-> RealtimeSourceConfigDigestCalculator
        +-> RealtimeYamlCodec
        `-> adapter/CdcPipelineSpecCompatibilityMapper
```

职责：

- `Manager`：Task shell、Draft save、metadata delete；
- `Publisher`：完整 preflight、runtime validate、Draft re-check、immutable DefinitionVersion publish；
- `Validator`：未保存 Definition preflight，不把 Flink 临时不可用变成 Draft 永久非法；
- `DigestCalculator`：source-config compatibility digest；
- `YamlCodec`：Yak YAML 与同一 logical definition 的转换；
- compatibility mapper 停在 Definition boundary，不进入 Core Domain。

必须保持：

- Draft / Published / Running 可以同时存在；
- Published Version 不可变；
- 运行中的 SyncExecution 不读取 current Draft；
- Publish 在外部校验后重新检查 Draft revision、source config digest 与 RuntimeEnvironmentRef；
- `DefinitionDigest != sourceConfigDigest != artifactDigest`；
- Yak YAML 不承载 Credential。

## Execution Core

```text
RealtimeJobExecutionService                  @Service
        |
        +-> RealtimeExecutionCoordinator      @Component
        |       +-> RealtimeExecutionStarter
        |       +-> RealtimeExecutionStateManager
        |       `-> RealtimeExecutionReplacementManager
        |
        +-> RealtimeExecutionPreparation
        +-> RealtimeReconcileCoordinator
        `-> RealtimeDeleteSafetyChecker

RealtimeExecutionStarter
        +-> RealtimeExecutionPreparation
        +-> RealtimeExecutionReservationManager
        `-> RealtimeExecutionStateManager
```

角色语义：

- `Coordinator`：同一 Task 的 in-process command serialization；
- `Preparation`：固定 DefinitionVersion、RuntimeEnvironmentSnapshot、compiled artifact，并管理 submit-boundary Credential 生命周期；
- `ReservationManager`：Idempotency-Key、single Active/Uncertain claim、prepared-version re-check、replacement reservation、DB linearization point；
- `StateManager`：Start result commit、Stop、stop-during-start、FAILED / UNKNOWN / STOPPED 状态提交；
- `Starter`：`prepare -> reservation -> submit -> state commit`；
- `ReplacementManager`：Restart / Apply target pinning 与 replacement resume。

必须保持：

- 同一 Task 最多一个 Active / Uncertain Execution；
- Start 先 reservation，再外部 submit；
- same-key race 可恢复；
- Stop during Start 必须绑定并取消返回的精确 JobId；
- Stop 结果不确定进入 `UNKNOWN`；
- RestartExecution 固定当前 Execution 的原 DefinitionVersion；
- ApplyPublishedVersion 固定命令开始时 Published Version；
- `UNKNOWN / CONFLICT` 先 Reconcile，不猜失败、不创建第二实例。

## Reconcile Subsystem

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

职责：

- `Coordinator`：手工 / 批量对账、candidate iteration、连续 Engine failure threshold；
- `RuntimeIdentityRecovery`：只用持久化 deterministic runtime identity 精确恢复 JobId；
- `RuntimeStateReconciler`：根据 SyncExecution desired state + Flink RuntimeStatus 收敛 observed state；
- `DeleteSafetyChecker`：删除前验证本地 terminality + 外部 Flink inactivity；
- `RealtimeReconciler`：scheduled trigger + multi-instance lease。

必须保持：

- runtime identity 唯一匹配才绑定 JobId；
- 多匹配不猜：STARTING/RUNNING -> `CONFLICT`，STOPPING -> `UNKNOWN`；
- orphan recovery grace window 内不提前终结；
- `RuntimeStatus.UNKNOWN -> UNKNOWN`；
- expected RUNNING 且 Flink TERMINATED/NONE 才收敛 FAILED；
- desired STOPPED 但 Flink RUNNING 时停止该精确 Job；
- 连续故障达到 threshold 才标 UNKNOWN，成功后清零 failure counter；
- 删除前 Flink RUNNING / UNKNOWN 都必须拒绝；
- 后台 reconcile 必须先取得 reconcile lease。

## Query / Observability

```text
RealtimeJobQueryService                     @Service
        `-> RealtimeJobReadModelQuery        @Component

RealtimeObservabilityService                @Service
        +-> RealtimeObservabilityReader      @Component
        +-> RealtimeEventQuery               @Component
        `-> RealtimeEventStream              @Component
```

read-side contract：

- detail / page / events / logs / metrics / checkpoints 不得触发 Start / Stop / Restart / Apply / Reconcile；
- 不依赖 `SyncExecutionStateMachine`；
- 不依赖 Execution command roles 或 Reconcile command roles；
- submission log 只依赖 Idempotency-Key，因此 JobId 未恢复时仍可读；
- runtime log / metrics / checkpoint 需要精确 JobId，不按任务名猜；
- SSE 只消费 AFTER_COMMIT change event，不成为 state owner；
- Flink read 失败不得反向写 `UNKNOWN / FAILED / STOPPED`。

## Environment / Runtime Boundary

Compute Environment 是邻接上下文，不属于 `SyncDefinition` 内部字段集合。

当前协作结构：

```text
ComputeEnvironmentController
        `-> ComputeEnvironmentService           @Service / stable facade
                +-> ComputeEnvironmentManager    @Component
                |       `-> ComputeEnvironmentConfigNormalizer
                `-> ComputeEnvironmentDiagnoser @Component

Definition / Execution / Reconcile / Observability
        `-> RealtimeRuntimeResolver              @Component
                +-> ComputeEnvironmentStore
                `-> RealtimeJobStore deployment snapshot
```

角色语义：

- `ComputeEnvironmentService`：唯一稳定 Environment Application Facade；
- `ComputeEnvironmentManager`：create / update / enable / default / delete 与引用约束；
- `ComputeEnvironmentConfigNormalizer`：LOCAL / SSH config normalization 与格式安全规则；
- `ComputeEnvironmentDiagnoser`：saved / preview runtime probe；saved diagnosis 只持久化小型诊断摘要；
- `RealtimeRuntimeResolver`：解析新命令所需当前 Environment Snapshot，以及既有 Execution 的冻结 Snapshot；它不是 Environment lifecycle service。

### Runtime snapshot contract

```text
Draft / DefinitionVersion
        -> RuntimeEnvironmentRef
        -> current enabled ComputeEnvironment
        -> new RuntimeEnvironmentSnapshot

SyncExecution
        -> persisted RuntimeEnvironmentSnapshot
        -> always prefer execution snapshot
        -> never drift with later Environment edits
```

固定规则：

- 新 Draft / Publish / Start 使用 Environment 时可以要求 `enabled=true`；
- Execution 创建时保存 immutable RuntimeEnvironmentSnapshot；
- 对既有 Execution，`RealtimeRuntimeResolver.deployment()` 优先使用 execution row 已冻结 snapshot；
- 若 row 未 hydrate snapshot，只允许从该 deployment 的持久化 snapshot 恢复；不得回读“当前 Environment”替代历史 snapshot；
- Environment 后续修改、停用、切换默认值不能改变已存在 Execution 的运行上下文；
- 删除 Environment 时必须检查 Draft、Published Version、Execution 引用；
- 默认 Environment 不能直接停用或删除；
- SSH 模式要求远端 Flink/Flink CDC/Java Home 使用 Linux 绝对路径；
- SSH 配置只保存 executable / host / user / key path 等连接配置，不托管 SSH password；
- Credential 只在 Engine submit boundary 短暂解析和使用。

## Engine Boundary

```text
RuntimeEnvironmentSnapshot
        |
        ▼
RealtimeEngineGateway / Flink clients
        |
        +-> Flink REST
        +-> Flink CDC CLI
        `-> LOCAL / SSH execution adapter
```

边界规则：

- Engine 只消费明确 RuntimeEnvironmentSnapshot，不读取 Task 当前环境配置作为隐式 fallback；
- Pipeline YAML 只是提交 artifact，不成为长期业务真相；
- Runtime identity 必须在 CLI 可能启动前持久化；
- Flink / SSH DTO、Process/HTTP 实现、secret 不进入 Core Domain。

## Persistence Boundary

```text
Domain       -> no framework / persistence / engine dependency
Repository   -> Domain contracts + DAO adapter
DAO          -> persistence primitives
Engine       -> external protocol boundary
```

Repository contract 不暴露 MyBatis PO / Mapper / Controller DTO；DAO 不依赖 Application Facade；Core Domain 不依赖 Spring/Jackson/MyBatis/Flink/SSH。

## Migration / Change Rules

1. **先锁行为，再移动代码。** 高风险生命周期测试先于结构重构。
2. **一个 PR 一个主要边界。** Definition、Execution、Reconcile、Observability、Environment 分开治理。
3. **不做 production 双入口。** 新入口稳定后删除旧入口；test-scope fixture 不属于 production compatibility layer。
4. **不借重构改 REST / DB / Domain semantics。** 行为变化必须单独走 Requirement / Domain review。
5. **不提前抽 realtime/offline Shared Sync Kernel。** 两个模块共享工程思想，但 Core Domain 独立演进。
6. **跨子系统 corridor 必须显式。** Application Facade / Resolver / Repository / Gateway 之外的跨包捷径需要 Review。
7. **禁止重新创建 `service/common/helper/utils` 大桶。** 角色与 truth owner 不清楚时先设计边界。

## Change Rule

新增或移动代码前，依次回答：

1. 它属于 Definition、Execution、Reconcile、Observability、Environment、Engine 还是 Persistence？
2. 它是什么角色？名字是否表达职责？
3. 谁是它的稳定调用入口？
4. 它读取或修改的 truth 属于 Task、DefinitionVersion、SyncExecution、Environment Snapshot 还是外部 Runtime Evidence？
5. 是否跨子系统？如果是，应该通过哪个 Facade / Resolver / Gateway？
6. 是否把 Flink / SSH / Credential / DTO / PO 泄漏进 Core Domain？
7. 哪个测试能证明改动没有破坏现有 contract？

答不清楚时，不要创建新的 `Helper / Common / Utils / Base`；先把边界设计清楚。
