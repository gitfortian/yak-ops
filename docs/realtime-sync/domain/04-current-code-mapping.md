# Realtime Sync 现有代码到领域模型 Mapping

> 状态：Proposed（阶段 4；本 PR 合并后视为 Accepted）  
> 前置：[阶段 1：领域边界与统一语言](./01-domain-boundary-and-language.md) / [阶段 2：核心领域模型 v1](./02-core-domain-model.md) / [阶段 3：领域不变量与生命周期](./03-invariants-and-lifecycle.md)  
> 范围：把当前 `yak-ops-business-sync-realtime` 的 Domain、Application、Repository、Infrastructure、Interface 和数据库结构逐项映射到目标 Realtime Sync Domain；本阶段只形成迁移施工图，不修改 Java 生产代码和数据库。

---

## 1. 阶段 4 的目的

阶段 1～3 已经回答：

```text
阶段 1：实时同步这个世界的边界和语言是什么？
阶段 2：这个世界有哪些核心对象？
阶段 3：这些对象允许怎么变化？
```

阶段 4 开始回答：

> **当前 yak-ops 里的代码，分别对应领域模型里的什么？哪些已经正确，哪些边界错位，哪些能力根本还不存在？**

本阶段不是为了找“坏代码”。一期实时同步已经能够完成创建、编辑、发布、启动、停止、对账、日志和指标，当前实现里有很多值得保留的安全能力。

阶段 4 的目标是防止后续重构出现两种错误：

```text
错误 A：为了 DDD 名字好看，把可用代码全部推倒重写。
错误 B：只改类名，不修复 Task / Version / Execution 真正的职责混合。
```

因此本文件使用以下五类 Mapping 结果。

---

## 2. Mapping 分类

### 2.1 KEEP

当前职责、方向和领域边界基本正确。

含义不是“永远不改代码”，而是：

> **阶段 6 不应该为了领域重构而主动重写它。**

典型对象：

- Flink REST / CLI Adapter；
- SSH Command Runner；
- submission-scoped credential binding；
- Runtime Job Identity 恢复机制；
- Read Query Adapter。

### 2.2 ADAPT

当前概念值得保留，但类型、边界或职责需要调整。

典型情况：

```text
现在是 Application + Domain + Infrastructure 混在一个 Validator
                ↓
保留能力，但拆成 Intrinsic Validator + Contextual Preflight + Adapter Validation
```

### 2.3 MIGRATE

当前对象承载了未来核心领域职责，但职责边界已经与阶段 2/3 模型明显不一致。

需要迁移到新的聚合或 Value Object。

典型对象：

- `CdcPipelineSpec`；
- `RealtimeJobStore`；
- `RealtimeStateMachine`；
- `RealtimeJobService`；
- `yak_realtime_job_definition`。

### 2.4 REMOVE FROM DOMAIN

不是删除功能。

含义是：

> **这个概念可以继续存在，但不应该继续被认为是 Realtime Sync Core Domain 的内部对象。**

典型对象：

- Compute Environment；
- Query View / Page；
- SSE Change Notification；
- Flink/YAML/JDBC 私有模型。

物理上暂时继续放在同一个 Maven Module 并不违反该原则；重点是依赖和建模方向不能反过来污染 Core Domain。

### 2.5 IMPLEMENTATION GAP

阶段 1～3 已经明确需要某个领域能力，但当前代码没有真正实现，或实现语义不完整。

典型：

- 独立、不可变 `DefinitionVersion` 聚合；
- Published + newer Draft 共存；
- Execution 独立持有 Desired / Observed 状态；
- Semantic Definition Digest canonicalization；
- Checkpoint / Restart Policy 真正被执行 Adapter 应用。

一个对象可以同时出现：

```text
ADAPT + IMPLEMENTATION GAP
MIGRATE + IMPLEMENTATION GAP
```

这表示“当前能力可复用，但目标模型还缺关键语义”。

---

# 3. 阶段 4 总结论

先给结论，再展开逐类 Mapping。

当前实现并不是“完全没有领域模型”。相反，它已经形成了非常接近目标模型的一些重要基础：

```text
CdcPipelineSpec                  -> 已经是统一、无密码的逻辑定义载体
RealtimeJobDeploymentPO/Row      -> 已经非常接近 SyncExecution persistence
ComputeEnvironmentSnapshot       -> 已经有 Execution Runtime Snapshot 思维
Idempotency-Key                  -> 已经有 Execution command identity
UNKNOWN / CONFLICT               -> 已经有不确定性建模
Runtime Job Identity Recovery    -> 已经处理提交结果不确定问题
PipelineYamlCompiler             -> 已经把 Flink YAML 留在 Infrastructure
Wizard / Yak YAML                -> 已经统一收敛到同一 Spec
```

真正的核心问题集中在 **Task / DefinitionVersion / Execution 三个聚合还没有在持久化和 Application Service 中真正拆开**。

当前核心结构大致是：

```text
              yak_realtime_job_definition
              ┌────────────────────────────┐
              │ Task identity              │
              │ current Draft spec         │
              │ Draft revision             │
              │ published revision marker  │
              │ DesiredState               │
              │ ObservedState              │
              │ last runtime error         │
              └────────────┬───────────────┘
                           │
                           │ latest / history
                           ▼
              yak_realtime_job_deployment
              ┌────────────────────────────┐
              │ spec snapshot              │
              │ runtime environment snap   │
              │ idempotency key            │
              │ gateway job id             │
              │ deployment status          │
              │ uncertainty evidence       │
              └────────────────────────────┘
```

目标模型是：

```text
RealtimeSyncTask
  ├── current Draft
  └── PublishedDefinitionRef
             │
             ▼
DefinitionVersion (immutable)
             │
             ▼
SyncExecution
  ├── version snapshot
  ├── runtime snapshot
  ├── DesiredState
  ├── ObservedState
  └── EngineExecutionRef
```

因此后续迁移的重点不是重写 Flink 提交，而是：

```text
1. 把 current Draft 与 immutable Published Version 分开；
2. 把 runtime state 从 Task row 迁到 Execution；
3. 让 Start / Restart 明确引用 DefinitionVersion；
4. 把 Query/View 和邻接上下文对象从 Core Domain package 中移出；
5. 保留当前已经成熟的 Flink/SSH/uncertainty 基础设施能力。
```

---

# 4. 最关键发现：当前 definitionVersion 不是 DefinitionVersion

这是阶段 4 最重要的 Mapping。

当前 `yak_realtime_job_definition` 有：

```text
definition_version
published_version
spec_json
```

当前保存 Draft 时：

```text
spec_json = new draft

definition_version = definition_version + 1

release_state = DRAFT
```

也就是说：

> **当前 `definition_version` 的真实语义是 Draft Revision，而不是阶段 2 所定义的不可变 DefinitionVersion。**

建议领域 Mapping：

```text
当前 definition_version
        ↓
DraftRevision / DefinitionRevision
```

而不是：

```text
DefinitionVersion.VersionNo
```

## 4.1 当前 publishedVersion 也不是完整 DefinitionVersion

当前 publish 只做：

```text
published_version = definition_version
release_state     = PUBLISHED
```

它没有创建独立不可变版本记录。

因此：

```text
published_version = 3
```

只表示：

> “当前 definition row 的第 3 次修订曾经被发布过。”

它本身没有：

```text
immutable spec
runtime environment ref
publication digest
publication timestamp
stable version id
```

所以 Mapping 为：

```text
published_version
    = legacy published revision marker
    ≠ DefinitionVersion aggregate
```

## 4.2 Published + newer Draft 无法共存的根因

例如：

```text
发布 v3
spec_json = v3
published_version = 3

继续编辑保存
spec_json = v4 candidate
definition_version = 4
release_state = DRAFT
published_version = 3
```

此时数据库只剩：

```text
当前 Draft v4 内容
+
“以前发布过 revision 3”这个整数
```

**Published v3 的定义内容本身没有独立版本记录。**

如果 v3 已经运行过，某个 Deployment 的 `spec_snapshot_json` 可能碰巧保留了一份 v3 运行证据。

但如果 v3 发布后从未执行：

```text
没有 Deployment Snapshot
```

那么 v3 的内容已经无法从当前模型可靠恢复。

这是明确的：

```text
IMPLEMENTATION GAP: immutable DefinitionVersion persistence
```

也是当前：

```text
publishedVersion == definitionVersion
```

才能 Start 的根本原因。

---

# 5. 第二个关键发现：Deployment 已非常接近 SyncExecution

当前 `RealtimeJobDeploymentPO / DeploymentRow` 已经包含：

```text
id
Task/definition reference
definition revision
spec snapshot
spec summary
runtime environment snapshot
idempotency key
runtime job identity
gateway job id
runtime revision
status
result uncertain
error
timestamps
```

这与目标：

```text
SyncExecution
├── ExecutionId
├── TaskId
├── DefinitionVersionRef
├── ExecutionDefinitionSnapshot
├── RuntimeEnvironmentSnapshot
├── DesiredState
├── ObservedState
├── EngineExecutionRef
└── ExecutionMetadata
```

已经非常接近。

所以阶段 6 **不应该创建第四套运行表再把 Deployment 全部抛弃**。

更合理的迁移是：

```text
yak_realtime_job_deployment
        ↓ evolve
SyncExecution persistence
```

重点补：

```text
definition_version_id
execution desired/observed state ownership
engine type + external execution ref semantic naming
artifact digest semantic naming
```

而不是重新建设一套 execution history。

---

# 6. 第三个关键发现：同名 configDigest 表达两种不同东西

当前 Definition：

```text
DefinitionRow.configDigest
=
SHA-256(JSON(spec) + runtimeEnvironmentId)
```

当前 Deployment：

```text
DeploymentRow.configDigest
=
SHA-256(compiled Flink Pipeline YAML)
```

虽然都叫：

```text
configDigest
```

但语义完全不同。

阶段 4 正式 Mapping：

```text
DefinitionRow.configDigest
        ↓
DefinitionDigest

DeploymentRow.configDigest
        ↓
ExecutionArtifactDigest / CompiledArtifactDigest
```

这两个概念必须在阶段 6 逐步拆名。

否则很容易出现：

```text
用 Pipeline YAML digest 判断 Domain Definition 是否变化
```

或者：

```text
用 Definition digest 判断实际提交产物是否一致
```

这样的错误。

## 6.1 当前 Definition Digest 还不是 Semantic Digest

当前 digest 直接序列化 `CdcPipelineSpec`。

这意味着以下无业务语义的顺序变化可能导致不同摘要：

```text
Route [A, B] -> [B, A]
ReplayKey [id, tenant] -> [tenant, id]
```

阶段 3 已经明确：

```text
Route collection order 没有业务语义
ReplayKey field order 没有业务语义
```

因此当前 digest 还存在：

```text
IMPLEMENTATION GAP: canonical semantic DefinitionDigest
```

目标：

```text
DefinitionDigest = hash(canonical(SyncDefinition + RuntimeEnvironmentRef))
```

其中 canonicalization 必须显式规定集合排序、Selector 规范化、Value Object 规范化。

---

# 7. Domain Package Mapping

当前：

```text
io.yak.ops.business.sync.realtime.domain
```

里面混有：

```text
Core Domain
Adjacent Compute Context
Query Read Model
SSE notification model
Application validation result
```

阶段 6 不一定马上重排所有 package，但阶段 4 先明确真实归属。

## 7.1 CdcPipelineSpec

**分类：MIGRATE**

当前优点：

- 唯一统一结构化配置载体；
- 无密码；
- Wizard/YAML 共用；
- 发布/启动共用；
- immutable record 方向正确。

目标：

```text
CdcPipelineSpec
   ↓ migrate
SyncDefinition
```

字段 Mapping：

| 当前 | 目标 |
|---|---|
| `sourceDataSourceRef` | `SourceEndpoint(DataSourceRef)` |
| `sinkDataSourceRef` | `SinkEndpoint(DataSourceRef)` |
| `tables` | `Collection<SyncRoute>` |
| `sourceTable + matchMode` | `SourceSelector` |
| `sinkTable` | `SinkTarget.TableTarget` |
| `keyColumns` | `ReplayKey` |
| `startupMode` | `StartupPolicy` |
| `schemaEvolution` | `SchemaEvolutionPolicy` |
| `parallelism` | `ExecutionPolicy.parallelism` |
| `checkpointIntervalMs` | `CheckpointPolicy` |
| `restart` | `RestartPolicy` |
| Sink write fields | `SinkWritePolicy` |
| `statementCacheSize` | REMOVE FROM DOMAIN -> JDBC Adapter Tuning |
| `strictReplaySafety` | REMOVE option -> Intrinsic Invariant |

迁移原则：

```text
先增加 Domain Adapter / Mapper
再逐步切内部使用者
最后再考虑删除旧 CdcPipelineSpec
```

不能为了改名一次改完整条 API/YAML/DB 链路。

## 7.2 CdcPipelineSpecValidator

**分类：ADAPT**

应保留的 Intrinsic Invariant：

- Source/Sink ref 非空；
- v1 Source != Sink；
- Route 非空；
- ReplayKey 非空且字段不重复；
- Selector/Target 本身结构合法。

需要迁出的规则：

```text
exact table name 不允许点号/逗号
regex 对 \. / \, 的特殊限制
```

这些限制来自当前 Flink CDC selector/tokenizer 的编译方式，不应该永久成为 Core Domain Invariant。

目标拆分：

```text
SyncDefinitionInvariantValidator
    -> 只做 Intrinsic Domain Invariant

DefinitionContextualPreflight
    -> Catalog / unique key / capability

FlinkPipelineCompiler / Flink Adapter Validator
    -> Flink selector/YAML/tokenizer 限制
```

当前：

```text
strictReplaySafety == true
```

未来不再通过 boolean 校验，而是模型天然要求 `ReplayKey`。

## 7.3 ComputeEnvironment

**分类：REMOVE FROM REALTIME DOMAIN**

不是删除 Compute Environment。

它拥有：

```text
Flink Home
Flink CDC Home
REST URL
SSH
Java Home
Flink Version
submitter type
health diagnosis
```

阶段 1 已经明确这些属于：

```text
Compute / Runtime Environment Context
```

它现在物理位于：

```text
...sync.realtime.domain
```

属于 bounded-context ownership 错位。

阶段 6 建议：

```text
优先先改“依赖方向和语义”
不强制第一步就拆 Maven Module
```

Realtime Sync 只依赖：

```text
RuntimeEnvironmentRef
RuntimeEnvironmentSnapshot
RuntimeEnvironmentPort
```

## 7.4 ComputeEnvironmentDiagnosis

**分类：REMOVE FROM REALTIME DOMAIN**

属于 Compute Environment 的诊断结果。

Realtime Sync 可以读取该上下文的 readiness/capability，但不拥有它。

## 7.5 ComputeEnvironmentSnapshot

**分类：ADAPT + REMOVE FROM REALTIME CORE**

“Execution 保存运行环境快照”这个能力是正确的，应 KEEP 语义。

但当前 Snapshot：

- 直接嵌套 Flink-specific `RuntimeConfig`；
- `runtimeRevision()` 直接拼 `flink-cdc-cli-*`；

因此它不是 Engine-neutral Core Domain Value Object。

目标关系：

```text
Compute Environment Context
       │ produces
       ▼
RuntimeEnvironmentSnapshot
       │ consumed by
       ▼
SyncExecution
```

快照作为跨上下文不可变证据可以被 Execution 保存，但生成规则和 Flink-specific config ownership 不属于 Realtime Sync Core。

## 7.6 RealtimeJobState

**分类：MIGRATE**

### KEEP

```text
DesiredState.RUNNING / STOPPED
ObservedState STARTING/RUNNING/STOPPING/STOPPED/FAILED/UNKNOWN/CONFLICT
```

这些状态语义与阶段 3 基本一致。

### MIGRATE

状态 ownership：

```text
当前：Task/Definition row
目标：SyncExecution aggregate
```

### REMOVE / DERIVE

当前：

```text
ReleaseState.DRAFT / PUBLISHED
```

不是目标 Core Domain 的唯一事实。

应从：

```text
current Draft
publishedRef
DraftRevision / Published digest
```

派生 UI 状态：

```text
UNPUBLISHED
PUBLISHED_CLEAN
PUBLISHED_WITH_CHANGES
```

### DeploymentState

当前更多是 persistence / infrastructure submission state。

后续可继续保留为内部执行细节，但不要与 `SyncExecution.ObservedState` 混为一谈。

## 7.7 RealtimeStateMachine

**分类：MIGRATE**

值得保留：

```text
显式状态转换表
非法转换 fail fast
Start/Stop 并发意图校验
```

需要迁移：

当前一个状态机同时承担：

```text
Execution lifecycle
+
Task Definition mutation policy
```

尤其：

```text
requireDefinitionMutable()
```

将运行状态和 Draft/Publish 绑定，是阶段 3 已明确的 Domain Debt。

目标：

```text
SyncExecutionStateMachine
    -> 只管理一个 Execution 的生命周期

Task/Publish application policy
    -> 独立判断，不再依赖 execution observed state 阻止普通 Draft 编辑
```

另外当前：

```text
FAILED -> STARTING
FAILED -> STOPPED
```

属于 task-level projection 的历史做法。

目标单个 Execution：

```text
FAILED = terminal
STOPPED = terminal
```

再次启动创建新的 Execution。

## 7.8 RealtimeJobChangeEvent

**分类：REMOVE FROM DOMAIN**

当前注释已经说明它是：

```text
post-commit notification used by SSE stream
```

所以它是：

```text
Application / Integration Notification
```

不是 Realtime Sync Domain Event。

以后如果真正引入 Domain Event，应使用明确业务事件，例如：

```text
DefinitionPublished
ExecutionStarted
ExecutionStateChanged
```

SSE Notification 可以消费这些事件或应用事件，但不能因为 package 在 `domain` 就自动被当成领域事件。

## 7.9 RealtimeJobView / RealtimeJobPage / RealtimeJobEventView

**分类：REMOVE FROM DOMAIN**

这是 Query Read Model / API projection。

它们把：

```text
Task
Draft
Published marker
latest Deployment
```

压成一个方便前端展示的结构，这是合理的 CQRS Read Model。

但不应该被当成 Aggregate。

目标 package 语义：

```text
application.query / readmodel
```

或：

```text
interface projection
```

## 7.10 RealtimeObservabilityView

**分类：REMOVE FROM DOMAIN**

Metrics / Checkpoint / runtime diagnostics 是 observability read model，不是 Core Domain Aggregate。

## 7.11 RealtimeValidationResult

**分类：REMOVE FROM DOMAIN / ADAPT**

当前 `valid + deliverySemantics` 是 Application Preflight 结果。

可以保留模型，但应归：

```text
application.validation
```

不是核心领域对象。

---

# 8. Application Service Mapping

## 8.1 RealtimeJobService

**分类：MIGRATE**

这是当前最需要进行职责迁移的 Application Service，但不是要推倒重写。

当前一个 Service 承担：

```text
Task create
Draft save
Publish
Runtime validate
Start
Stop
Restart
Delete
Events query
Runtime capabilities query
Digest
Compilation preparation
Credential resolution orchestration
```

阶段 2 的三个聚合意味着未来 Application Use Case 至少要在语义上分开：

```text
Task Draft use cases
Publication use cases
Execution lifecycle use cases
```

阶段 6 不必机械拆成十几个 Service；核心要求是：

> **一个 use case 操作哪个 Aggregate 必须清楚。**

推荐逻辑拆分方向：

```text
RealtimeSyncTaskApplicationService
  - create task
  - save draft
  - archive task

DefinitionPublicationService
  - preflight
  - publish
  - find published version

SyncExecutionService
  - start
  - stop
  - restart execution
  - apply published version
```

现有 `RealtimeJobService` 可以先作为 facade，逐步把内部逻辑迁给新 use-case components，保持 Controller/API 不变。

### 必须 KEEP 的实现能力

当前 start/stop 里有成熟安全逻辑：

- `Idempotency-Key`；
- DB row lock；
- prepared definition re-check；
- 同 key race handling；
- start/stop 并发时不覆盖用户 Stop 意图；
- submission-scoped credential binding；
- uncertain error mapping。

这些都应迁移，不应重写掉。

## 8.2 create/save

当前：

```text
save Draft
 -> requires task stopped/failed
 -> increments definition_version
 -> release_state = DRAFT
```

Mapping：

```text
save()
 -> RealtimeSyncTask.replaceDraft()
 -> increment DraftRevision
```

阶段 3 目标：

```text
运行中也可以保存 Draft
```

所以 `requireDefinitionMutable()` 不应成为未来 Draft save 的 Domain invariant。

## 8.3 publish

当前 publish：

```text
validate current mutable row
set published_version = current definition_version
```

目标：

```text
load Task + Draft
contextual preflight
create immutable DefinitionVersion
atomically update Task.publishedRef
```

这是：

```text
MIGRATE + IMPLEMENTATION GAP
```

## 8.4 start

当前 start 的优点：

- preflight；
- lock；
- idempotency；
- snapshot；
- environment snapshot；
- uncertainty safe handling。

当前根本限制：

```text
prepare() 从 current definition row 读取 spec
requirePublished() 要求 publishedVersion == definitionVersion
```

目标：

```text
Task.publishedRef
      ↓
DefinitionVersionRepository
      ↓
immutable DefinitionVersion
      ↓
create SyncExecution
```

Start **不读取 current Draft**。

## 8.5 stop

**分类：ADAPT**

当前 Stop 的安全语义基本正确：

```text
先改变用户 desired intent
再调用外部 runtime
无法确认则 UNKNOWN
```

目标只需把 ownership 从：

```text
DefinitionRow desired/observed
```

迁到：

```text
SyncExecution desired/observed
```

## 8.6 restart

**分类：ADAPT**

阶段 3 要求：

```text
RestartExecution(E100 v3)
 -> E101 v3
```

当前代码：

```text
stopLocked(task)
startLocked(task)
```

并且新的 `startLocked()` 再从 Task 当前 Definition 解析。

当前因为：

```text
publishedVersion == definitionVersion
```

才能 start，所以存在 newer Draft 时 Restart 会被阻止，**当前并不会静默升级版本**。

这个安全结果值得肯定。

但未来放开 Published + newer Draft 后，Restart 不能简单继续“读取 Task 当前 Published Ref”。

应该显式：

```text
oldExecution.definitionVersionRef
```

启动同一 Version。

另一个操作单独叫：

```text
ApplyPublishedVersion / RedeployPublished
```

用于升级版本。

## 8.7 delete

**分类：MIGRATE**

当前 Controller：

```text
assert runtime inactive
        +
service.delete
```

安全检查本身值得保留。

但 DAO 当前删除 Task 后会继续：

```text
delete events
delete deployments
```

这直接删除运行历史证据。

与阶段 3 不变量冲突。

目标：

```text
never published + no execution + no external ref
    -> 可以 hard delete

published/executed/referenced
    -> Archive / Tombstone
```

## 8.8 RealtimeDefinitionValidator

**分类：ADAPT**

当前混合了三层：

```text
1. Bean + Spec intrinsic validation
2. Source Catalog / PK contextual validation
3. Environment / Connector / Compiler adapter validation
```

目标：

```text
SyncDefinitionInvariantValidator
      ↓ intrinsic only

DefinitionContextualPreflight
      ↓ Source Catalog / ReplayKey drift / runtime capability

FlinkCompilerValidation
      ↓ current engine constraints
```

### Draft Save 特别说明

阶段 3 已经明确：

```text
Source DB 当前在线
```

不是永久 Intrinsic Domain Invariant。

当前产品可以继续选择：

```text
保存 Draft 前执行完整 live preflight
```

但文档/代码命名必须承认它是：

```text
Application Policy
```

而不是 `SyncDefinition` 本身合法性的唯一标准。

## 8.9 RealtimeValidationService

**分类：ADAPT**

适合作为 Application Facade。

以后可以分别暴露：

```text
validateDraftIntrinsic
preflightPublish
preflightStart
```

不必让调用方理解 Flink/YAML 细节。

## 8.10 RealtimeJobLifecycleCoordinator

**分类：ADAPT + MIGRATE**

这是当前非常有价值的实现。

KEEP：

- authoritative reconciliation；
- recovery by runtime identity；
- ambiguous matches -> CONFLICT；
- engine unreachable threshold -> UNKNOWN；
- desired STOPPED 时发现 runtime RUNNING -> cancel；
- orphan grace window。

需要迁移：

```text
当前：协调 DefinitionRow + latest Deployment
目标：协调 active SyncExecution
```

未来 Coordinator 不应该为了更新 Execution 状态持续修改 Task row 的 desired/observed。

## 8.11 RealtimeJobReconciler

**分类：KEEP**

它是 Application/Infrastructure scheduler trigger。

只需要未来调用新的 `SyncExecutionReconciliationService`。

## 8.12 RealtimeRuntimeResolver

**分类：ADAPT**

正确方向已经存在：

```text
Definition binding -> resolve current environment
Deployment -> prefer immutable environment snapshot
```

目标：

```text
Draft/DefinitionVersion: RuntimeEnvironmentRef
Start: capture RuntimeEnvironmentSnapshot via Compute Environment Port
Execution: afterwards always use stored Snapshot
```

未来不应依赖 `DefinitionRow` 才能解释 Execution 的 Runtime Environment。

## 8.13 RealtimeJobQueryService

**分类：KEEP**

已经是 Read Query 边界。

阶段 6 只需让它投影新 Task/Version/Execution 模型，前端 API 尽量保持兼容。

## 8.14 RealtimeObservabilityService

**分类：KEEP / ADAPT**

Metrics/Checkpoint/runtime log 是 Execution Query / Infrastructure Observability。

未来 key 应从：

```text
task.latestDeployment
```

逐步切成：

```text
ExecutionId / active Execution
```

但功能不需要为了 DDD 重写。

## 8.15 RealtimeEventStreamService

**分类：KEEP**

SSE 是 Interface/Application notification。

将来只需消费新的 Task/Version/Execution application events。

## 8.16 RealtimeYamlCodec

**分类：KEEP + ADAPT**

核心方向完全正确：

```text
Yak YAML
   ↕
canonical logical definition
```

它不是原生 Flink YAML。

阶段 6 建议：

```text
YAML v1 compatibility DTO
      ↓ mapper
SyncDefinition
```

不要直接让 YAML document 类型变成 Domain Entity。

当前 YAML v1 中：

```text
statementCacheSize
strictReplaySafety
failure-rate string
```

属于旧 `CdcPipelineSpec` 兼容字段。

如果未来推出 YAML v2，可以把它们重新归位；不要为了领域纯洁性直接破坏现有 v1 文件兼容。

---

# 9. Repository / Persistence Port Mapping

## 9.1 RealtimeJobStore

**分类：MIGRATE**

当前一个 Store 同时管理：

```text
Task/Draft
Publish marker
Deployment/Execution
Runtime state
Events
Query page
Reconcile lease
View mapping
Runtime environment fallback
```

它更像：

```text
Realtime Module Persistence Facade
```

而不是一个聚合 Repository。

目标逻辑 Port：

```text
RealtimeSyncTaskRepository
DefinitionVersionRepository
SyncExecutionRepository
```

另外：

```text
RealtimeJobListQuery
ExecutionEventRepository / AuditLog
ReconcileLeaseStore
```

继续保持独立 read/infrastructure concern。

### 阶段 6 不要求一步拆完

可以先：

```text
新 Repository 接口
    ↓
由一个兼容 Adapter 委托当前 RealtimeJobStore/DAO
```

逐步迁移 Use Case。

## 9.2 DefinitionRow

**分类：MIGRATE**

当前压缩了：

```text
RealtimeSyncTask
DefinitionDraft
legacy published marker
latest execution projection
```

它不是未来 Domain Aggregate。

字段真实 Mapping：

| 当前字段 | 真实语义 |
|---|---|
| `id` | TaskId |
| `name/description` | TaskProfile |
| `spec` | current DefinitionDraft.SyncDefinition candidate |
| `runtimeEnvironmentId` | current Draft RuntimeEnvironmentRef |
| `definitionVersion` | **DraftRevision，不是 DefinitionVersion** |
| `publishedVersion` | legacy published revision marker |
| `releaseState` | derived/legacy presentation state |
| `desiredState/observedState` | latest Execution projection，目标应迁出 Task |
| `configDigest` | current Draft DefinitionDigest-like value |
| `lastError` | latest Execution/query projection |

## 9.3 DeploymentRow

**分类：ADAPT / MIGRATE TO SyncExecution**

这是当前最值得复用的数据模型。

字段 Mapping：

| 当前字段 | 目标 |
|---|---|
| `id` | ExecutionId |
| `definitionId` | TaskId |
| `definitionVersion` | legacy Draft/Published revision；目标 `DefinitionVersionRef` |
| `specSnapshot` | ExecutionDefinitionSnapshot.definition |
| `runtimeEnvironment` | RuntimeEnvironmentSnapshot |
| `idempotencyKey` | StartCommand / Execution creation identity |
| `engineJobId` | EngineExecutionRef.externalExecutionId |
| `runtimeRevision` | Execution infrastructure evidence |
| `status` | current execution/deployment observed projection |
| `resultUncertain` | uncertainty evidence / metadata |
| `errorMessage` | Execution failure/uncertainty evidence |
| `configDigest` | **ExecutionArtifactDigest**，不是 DefinitionDigest |

目标新增：

```text
definition_version_id
execution desired state
execution observed state
engine type
```

## 9.4 RealtimeJobStoreAdapter

**分类：MIGRATE**

当前 Adapter 的 JSON snapshot / runtime snapshot 逻辑值得保留。

需要拆掉：

```text
write repository + query projection + application event publishing
```

都放在一个类里的职责耦合。

尤其：

```text
view()
deploymentView()
```

属于 Query Projection，不应该继续是 write aggregate repository 的职责。

## 9.5 RealtimeJobListQuery / Adapter

**分类：KEEP**

这是当前已经具备 CQRS 味道的好边界。

未来只需改 SQL/Projection source，不需要变成 Domain Repository。

## 9.6 RealtimeRuntimeIdentityStore / Adapter

**分类：KEEP (Infrastructure)**

这是为了不确定提交恢复外部 JobId 的基础设施证据。

它不是 Domain Aggregate。

## 9.7 ComputeEnvironmentStore / Adapter

**分类：REMOVE FROM REALTIME DOMAIN**

属于 Compute Environment Context。

Realtime Sync 应通过明确 Port 引用，不应把它当自身 Aggregate Repository。

---

# 10. DAO / PO Mapping

## 10.1 RealtimeJobDefinitionPO

**分类：MIGRATE**

表：

```text
yak_realtime_job_definition
```

当前字段混合：

```text
Task
Draft
Publication marker
Execution current state
```

阶段 6 最小路线不是直接 rename/drop 表，而是先把它当作：

```text
Task + Draft compatibility row
```

逐步减少它承担的职责。

## 10.2 RealtimeJobDeploymentPO

**分类：ADAPT / MIGRATE**

表：

```text
yak_realtime_job_deployment
```

未来可直接演进为：

```text
SyncExecution persistence table
```

不建议新建一套平行 execution history 再双份维护。

## 10.3 RealtimeJobEventPO

**分类：KEEP / ADAPT**

它是审计事件记录，不是 Aggregate。

未来字段语义从：

```text
definition_id / deployment_id
```

逐步明确为：

```text
task_id / execution_id / definition_version_id(optional)
```

历史事件不能随着 Task 删除被级联业务删除。

## 10.4 RealtimeJobListRow

**分类：KEEP**

Read Model。

## 10.5 RealtimeRuntimeLeasePO

**分类：KEEP (Infrastructure)**

Reconcile scheduler lease，不是业务领域模型。

## 10.6 RealtimeJobDao / DaoImpl

**分类：MIGRATE**

当前 SQL 条件本身承载了很多旧领域规则：

```text
update draft only if STOPPED/FAILED
publish only if STOPPED/FAILED
markStarting updates task runtime state
reconcile updates definition row runtime state
```

阶段 6 不能只改 Service，而忽略这些 SQL CAS 条件。

否则 Application 允许“运行中编辑”，最终仍会被 DAO 静默拒绝。

因此 Mapping 必须覆盖：

```text
Application rule
+
DAO atomic condition
+
DB schema
```

三个层面。

---

# 11. Engine / Infrastructure Mapping

## 11.1 RealtimeEngineGateway

**分类：ADAPT / MIGRATE PORT BOUNDARY**

名字看起来是 Engine-neutral Port，但当前接口直接暴露：

```text
ComputeEnvironmentSnapshot (Flink-specific config)
String pipelineYaml
String jobId
JsonNode capabilities/health
```

所以它实际上更接近：

```text
Flink CDC Runtime Gateway Contract
```

而不是纯 Domain Port。

这本身不影响一期运行，但后续不应让 Core Domain 依赖它。

目标可以逐步形成：

```text
Application Port
  ExecutionEnginePort

Infrastructure
  FlinkCdcExecutionEngineAdapter
```

Domain 只知道：

```text
EngineExecutionRef
ObservedState
```

不需要知道 Pipeline YAML。

## 11.2 FlinkCdcEngineGateway

**分类：KEEP (Infrastructure)**

它做的正是 Infrastructure 应该做的事：

- Flink REST；
- Flink CDC CLI；
- local / SSH submission；
- secret resolution；
- temp YAML；
- JobId parsing；
- stop/status；
- log sanitization。

阶段 6 不应该重写它来“DDD 化”。

### 明确 Implementation Gap：FINISHED

当前 Flink：

```text
FINISHED
CANCELED
FAILED
SUSPENDED
```

统一映射成：

```text
RuntimeStatus.TERMINATED
```

对于当前 continuous realtime sync 可以接受：

```text
unexpected terminated -> FAILED
```

但未来 `SNAPSHOT_ONLY` 无法表达正常完成。

因此：

```text
IMPLEMENTATION GAP: normal completion state
```

在加入 snapshot-only 领域能力前必须修复。

## 11.3 PipelineYamlCompiler

**分类：KEEP + ADAPT + IMPLEMENTATION GAP**

边界正确：

```text
logical definition
    ↓ Infrastructure compiler
Flink CDC YAML
```

必须继续保持：

```text
Flink YAML 不持久化为 Domain Source of Truth
```

### 当前没有应用的 ExecutionPolicy

当前 compiler 真正使用：

- parallelism；
- schema evolution；
- startup mode；
- sink write tuning；
- routes。

但：

```text
checkpointIntervalMs
restart policy
```

当前被保存进 Spec，却没有真正编译/应用到运行引擎。

这是阶段 2 已记录的：

```text
IMPLEMENTATION GAP
```

未来原则：

> **Domain 接受的 ExecutionPolicy，当前 Engine Adapter 必须明确支持并执行，或在 Preflight 明确拒绝；不能静默忽略。**

## 11.4 RealtimeDataSourceResolver

**分类：KEEP + ADAPT (Adjacent Context Adapter)**

非常值得保留的设计：

- Core Spec 只存 DataSourceRef；
- 执行边界才解析 connection coordinates；
- credential 只在 submission lifetime 解析；
- CredentialBinding 可清零 password char[]。

需要调整的是：

```text
MySQL Source / MySQL/Postgres Sink
```

目前直接硬编码在 Resolver。

未来更适合作为：

```text
Source/Sink Capability
+
Engine Adapter Capability
```

的 Contextual Preflight，而不是 `SyncDefinition` 类型分支。

## 11.5 ResolvedCdcPipeline

**分类：KEEP (Infrastructure)**

这是编译/提交阶段解析后的 datasource coordinates。

不要升级为 Domain Endpoint。

## 11.6 RealtimeConnectorCapabilityResolver

**分类：KEEP + ADAPT**

正确归属：

```text
Contextual Preflight / Infrastructure Capability Adapter
```

未来输入从 `CdcPipelineSpec` 切成 `SyncDefinition`/Execution plan 即可。

## 11.7 RealtimeDeployRequest

**分类：KEEP (Infrastructure)**

它是 submission-scoped：

```text
compiled artifact
idempotency key
source credential
sink credential
```

Credential close/zeroize 机制应继续保留。

它不是 SyncExecution Domain Model。

## 11.8 RealtimeRuntimeIdentity

**分类：KEEP (Infrastructure)**

通过 Idempotency-Key 生成确定性 Flink runtime name，用于 uncertain submission recovery。

这是非常重要的基础设施安全能力。

但：

```text
regex rewrite pipeline YAML
```

必须始终留在 Flink Adapter 内，不进入 Definition/Execution Domain。

## 11.9 RecoverableRealtimeEngineGateway

**分类：KEEP (Infrastructure Decorator)**

提交前持久化 runtime identity，然后 decorate compiled artifact，方向正确。

后续如果改 Engine Port，只需迁移 decorator 接口，不重写恢复策略。

## 11.10 FlinkJobDiscoveryClient

**分类：KEEP (Infrastructure)**

用于 uncertain execution recovery。

## 11.11 FlinkObservabilityClient

**分类：KEEP (Infrastructure)**

Metrics/Checkpoint/exception read adapter。

## 11.12 FlinkRuntimeEnvironmentProbe

**分类：REMOVE FROM REALTIME DOMAIN / KEEP Compute Context Infrastructure**

是 Compute Environment readiness/diagnosis 能力。

## 11.13 SshFlinkCdcCommandRunner

**分类：KEEP (Infrastructure)**

SSH 是提交策略，不是实时同步任务类型。

## 11.14 RealtimeLogRedactor

**分类：KEEP (Infrastructure Security)**

不进入 Domain。

---

# 12. Interface / Controller Mapping

## 12.1 RealtimeJobController

**分类：KEEP + ADAPT**

Controller 是正确的 Interface Adapter。

阶段 6 推荐继续保持 API 兼容，内部逐步换 Application Use Case。

不应在 Controller 创建：

```text
WizardTaskService
YamlTaskService
FlinkTaskService
```

### publish endpoint

未来内部：

```text
POST /{id}/publish
  -> Create DefinitionVersion
```

API 路径不需要因为领域重构立即变化。

### start endpoint

未来：

```text
POST /{id}/start
  -> resolve Task.publishedRef
  -> create SyncExecution(versionRef)
```

### restart endpoint

未来语义必须明确：

```text
restart current/last Execution's DefinitionVersion
```

如果要升级当前 Published Version，使用另一条显式 use case。

### delete endpoint

未来对有历史的 Task 应转成 archive/tombstone 语义。

## 12.2 Request DTO / RealtimeRequestMapper

**分类：KEEP + ADAPT**

DTO 是 Interface Model，不是 Domain Model。

目标：

```text
HTTP/YAML DTO
    ↓ Mapper
SyncDefinition / Command
```

API v1 可以继续保持字段兼容，不要求立刻暴露新的 sealed selector 类型。

## 12.3 RealtimeViewMapper / RealtimeViews

**分类：KEEP**

Projection layer。

未来可以继续向前端输出：

```text
releaseState
observedState
latestDeployment
```

但这些值可以从 Task + Published Ref + Active/Latest Execution 派生，不能因此反向要求 Domain 继续保留旧大 Job 模型。

## 12.4 Wizard / YAML Editor

**分类：KEEP (Interface Editor Adapter)**

已经符合阶段 1～3：

```text
Wizard ─┐
        ├-> canonical logical definition
YAML ───┘
```

阶段 6 只替换中间 Domain Mapper，不重写两套编辑器。

## 12.5 RealtimeExecutionPanel

**分类：KEEP / ADAPT**

是 Application Workflow 的 UI projection。

未来可明确显示：

```text
Draft Revision
Published Version
Active Execution Version
```

帮助用户理解三者可同时存在。

---

# 13. Database Mapping

当前 Baseline 有：

```text
yak_compute_environment
yak_realtime_job_definition
yak_realtime_job_deployment
yak_realtime_job_event
yak_realtime_runtime_lease
```

目标领域最明显缺少：

```text
DefinitionVersion persistence
```

## 13.1 yak_compute_environment

**分类：KEEP，但属于 Compute Environment Context**

不需要为了 Realtime Sync Domain 重构这张表。

## 13.2 yak_realtime_job_definition

**分类：MIGRATE**

短期建议继续复用现有表作为：

```text
RealtimeSyncTask + current DefinitionDraft compatibility row
```

阶段 6 不建议第一步 rename 表，因为：

- 现有 DAO/Mapper/UI 全部依赖；
- 改表名没有领域收益；
- 可以通过新增表/列完成语义迁移。

目标逐步让它只承担：

```text
Task identity
Task profile
current Draft
DraftRevision
PublishedDefinitionRef
archive state
```

最终从 Task row 移出的字段：

```text
desired_state
observed_state
last_error (runtime)
legacy release_state
```

## 13.3 新增 yak_realtime_definition_version

**分类：IMPLEMENTATION GAP / REQUIRED MIGRATION**

建议目标结构（阶段 4 逻辑草案，阶段 6 migration 再定 SQL）：

```text
yak_realtime_definition_version
├── id                       DefinitionVersionId
├── task_id                  TaskId
├── version_no               monotonic published version
├── spec_json                immutable logical definition snapshot
├── runtime_environment_id   RuntimeEnvironmentRef
├── definition_digest        semantic digest
├── published_by             optional audit
├── create_time
└── unique(task_id, version_no)
```

注意：

```text
version_no
```

不是当前 `definition_version` 原样搬过去。

当前 `definition_version` 是 DraftRevision。

## 13.4 yak_realtime_job_deployment

**分类：ADAPT -> SyncExecution persistence**

短期继续复用表。

建议渐进新增/重命名语义：

```text
add definition_version_id
add desired_state
add observed_state
add engine_type
config_digest -> artifact_digest semantic rename
```

现有：

```text
spec_snapshot_json
runtime_environment_snapshot_json
idempotency_key
runtime_job_name
gateway_job_id
runtime_identity_state
```

都具有实际价值，应保留。

## 13.5 yak_realtime_job_event

**分类：KEEP + ADAPT**

应长期作为不可随意删除的审计证据。

可逐步增加：

```text
definition_version_id
execution_id semantic alias
```

## 13.6 yak_realtime_runtime_lease

**分类：KEEP Infrastructure**

---

# 14. Published Version 数据迁移的特殊风险

因为当前没有 immutable Version table，历史数据 backfill 不能简单写：

```text
published_version = N
=> current spec_json 就是 vN
```

这在 `release_state=DRAFT` 时是错误的。

## 14.1 情况 A：当前 row 仍是 PUBLISHED

如果：

```text
published_version == definition_version
release_state == PUBLISHED
```

可以用：

```text
current spec_json
current runtime_environment_id
```

重建当前 Published Version。

## 14.2 情况 B：已有 newer Draft，但 Published 曾经执行过

如果：

```text
published_version < definition_version
```

并且存在：

```text
Deployment.definition_version == published_version
```

可以从该 Deployment：

```text
spec_snapshot_json
runtime_environment_snapshot_json / environment id
```

恢复 Published Version 候选。

迁移时必须校验 digest/一致性。

## 14.3 情况 C：已有 newer Draft，Published 从未执行

这是最危险情况：

```text
published_version < definition_version
+
没有 published revision 对应 Deployment snapshot
```

旧 Published 内容已经无法从当前数据库可靠恢复。

迁移规则必须是：

> **绝不猜测、绝不把当前 Draft 假装成旧 Published。**

可接受策略：

```text
标记 legacy published snapshot missing
要求用户显式重新 Publish 当前 Draft
```

具体 UX / migration flag 在阶段 6 决定。

这条规则非常重要，因为错误“恢复”一个版本比丢失一个历史 marker 风险更大。

---

# 15. Lifecycle Use Case Mapping

## 15.1 Create Task

当前：

```text
insert definition row with nullable spec
```

目标：

```text
create RealtimeSyncTask shell
```

**分类：KEEP / ADAPT**

这个“两阶段创建”的产品能力可以继续。

## 15.2 Save Draft

当前：

```text
mutate same row
increment definition_version
force releaseState = DRAFT
only when runtime stopped/failed
```

目标：

```text
replace Task.DefinitionDraft
increment DraftRevision
PublishedDefinitionRef unchanged
active Execution unchanged
```

**分类：MIGRATE**

## 15.3 Publish

当前：

```text
set marker on same row
```

目标：

```text
create immutable DefinitionVersion
+
atomically move Task.publishedRef
```

**分类：IMPLEMENTATION GAP**

## 15.4 Validate

目标三层：

```text
Intrinsic Domain Validation
Contextual Definition Preflight
Engine/Artifact Validation
```

当前能力都有不少，但层次混合。

**分类：ADAPT**

## 15.5 Start

当前优秀能力：

```text
Idempotency
reservation
snapshot
credential lifetime
uncertain result
concurrent stop safety
```

全部 KEEP。

目标变更：

```text
读取 immutable DefinitionVersion
创建 SyncExecution
把 lifecycle 状态写 Execution
```

**分类：MIGRATE**

## 15.6 Stop

当前安全语义基本符合目标。

**分类：ADAPT ownership**

## 15.7 Reconcile

当前恢复能力很强。

**分类：KEEP logic / MIGRATE ownership**

## 15.8 Restart

当前不会静默升级，因为 newer Draft 会导致 start 被阻止。

目标：

```text
restart must explicitly pin previous Execution's DefinitionVersion
```

**分类：ADAPT**

## 15.9 Apply Published Version

当前没有独立 use case。

未来：

```text
stop active old execution
start new execution with Task.publishedRef
```

**分类：IMPLEMENTATION GAP**

## 15.10 Delete / Archive

当前 hard delete 会删除 Deployment + Event。

目标不允许破坏历史运行证据。

**分类：MIGRATE**

---

# 16. 当前实现值得原样保护的安全资产

领域重构最危险的事情之一，是“觉得旧代码不够优雅”而重写已经经过考虑的并发与恢复逻辑。

以下能力在阶段 6 应明确列入 **Protection List**。

## 16.1 Start Idempotency

```text
Idempotency-Key
unique DB key
same-key race recovery
owner validation
```

KEEP。

## 16.2 Start Reservation Before External Submit

先持久化 Execution/Deployment reservation，再跨外部边界。

KEEP。

## 16.3 Prepared Definition Re-check

外部 validation 与真正提交之间再次校验 revision/digest/runtime binding。

KEEP 思想。

未来比较对象改为：

```text
DefinitionVersion identity/digest
```

## 16.4 Stop During Start

Stop 不因为当前没有 JobId 就假装成功。

KEEP。

## 16.5 Uncertain Result

```text
external submission timeout / unknown
 -> UNKNOWN
```

KEEP。

## 16.6 Runtime Identity Recovery

确定性 runtime job name + discovery + CONFLICT 防自动误绑定。

KEEP。

## 16.7 Runtime Environment Snapshot

Execution 使用当时 Snapshot，不回读已经变化的环境配置解释历史执行。

KEEP。

## 16.8 Credential Lifetime

密码只在 submission boundary 临时解析、使用后清零。

KEEP。

## 16.9 Logs Redaction

KEEP。

---

# 17. Implementation Gap 清单

阶段 4 形成正式 Gap Register。

## P0：聚合语义缺失

### GAP-01 Immutable DefinitionVersion Store

当前没有独立不可变 Published Version。

这是后续所有正确 Draft/Publish/Execution 关系的前置。

### GAP-02 Published + newer Draft coexistence

当前 spec row 覆盖 Published 内容。

### GAP-03 Start by DefinitionVersionRef

当前 Start 读取 current mutable row。

### GAP-04 Execution lifecycle ownership

Desired/Observed 当前主要放 Task/Definition row。

### GAP-05 Terminal Execution identity

当前 Task-level FAILED 可以被“复活”；目标每次 Start 都是新的 Execution。

## P0：数据完整性

### GAP-06 DefinitionDigest canonicalization

当前 JSON serialization order-sensitive。

### GAP-07 DefinitionDigest / ArtifactDigest semantic split

当前都叫 `configDigest`。

### GAP-08 Audit-safe deletion

当前删除 Task 会删除 Deployment / Event。

## P1：运行语义

### GAP-09 Explicit Restart version pinning

当前 Restart 依赖 Task current published condition；未来要引用 old Execution version。

### GAP-10 ApplyPublishedVersion use case

版本升级与 Restart 必须分开。

### GAP-11 ExecutionPolicy application

Checkpoint / Restart 当前保存但没有真正应用。

### GAP-12 FINISHED normal completion

未来 SNAPSHOT_ONLY 前必须增加正常完成语义。

## P1：边界

### GAP-13 Validation layering

Intrinsic / Contextual / Flink Adapter validation 当前混合。

### GAP-14 Compute Environment ownership

当前 physically/semantically 位于 realtime domain package。

### GAP-15 Engine Port neutrality

当前 `RealtimeEngineGateway` 暴露 pipelineYaml / jobId / JsonNode / Flink environment snapshot。

## P2：结构清理

### GAP-16 Read Models in domain package

View/Page/Observability/ValidationResult/SSE notification 不应继续被视为 Core Domain。

### GAP-17 Adapter tuning in CdcPipelineSpec

`statementCacheSize`。

### GAP-18 Incomplete failure-rate Restart policy

当前类型名存在，但 VO 参数不足。

### GAP-19 Flink tokenizer rules in Domain Validator

需要迁移至 adapter validation。

---

# 18. Stage 6 最小迁移施工顺序

阶段 4 最重要的产物不是“最终类图”，而是让阶段 6 知道 **什么顺序改最安全**。

不建议 Big Bang。

推荐以下 waves。

## Wave 0：先建立新领域类型，不改变行为

目标：

```text
新增 SyncDefinition / SyncRoute / Policy 等 Core VO
新增 legacy CdcPipelineSpec <-> SyncDefinition mapper
```

保持：

- REST v1；
- Yak YAML v1；
- 当前 DB；
- Flink Compiler。

先用测试证明 round-trip 等价。

风险：低。

## Wave 1：补 DefinitionVersion persistence

新增：

```text
yak_realtime_definition_version
DefinitionVersionRepository
```

Publish 改为：

```text
Draft preflight
 -> insert immutable version
 -> atomically update published ref
```

Task 表继续保留 current Draft。

暂时保留 legacy `published_version/release_state` 作为 compatibility projection/dual write。

风险：中。

这是最关键 wave。

## Wave 2：Start 改为读取 Published Version

Start：

```text
Task.publishedRef
 -> DefinitionVersion
 -> compile
 -> Execution reservation
```

此时正式实现：

```text
Published v3 + Draft v4
仍然可以 Start v3
```

风险：中。

必须保留现有 idempotency/concurrency/uncertainty tests。

## Wave 3：Deployment 演进成真正 SyncExecution

给现有 deployment 增加：

```text
definition_version_id
desired_state
observed_state
engine_type
```

逐步把 lifecycle ownership 从 definition row 移过来。

Task list 通过 query projection 读取 latest/active execution state。

风险：高，但可以 dual-read/dual-write 过渡。

## Wave 4：放开运行中编辑 / 发布

在 Execution ownership 稳定后，移除：

```text
requireDefinitionMutable for draft save/publish
DAO STOPPED/FAILED mutation predicates
```

这一步不能早于 Wave 3，否则 Task row runtime state 仍是并发锁基础。

风险：中高。

## Wave 5：Restart / Apply Published Version 分离

实现：

```text
RestartExecution(versionRef from old execution)
ApplyPublishedVersion(task.publishedRef)
```

风险：中。

## Wave 6：清理 legacy 字段和 package ownership

最后才处理：

```text
release_state
task row desired/observed
legacy published_version marker
legacy definition_version naming
View models in domain package
ComputeEnvironment package ownership
CdcPipelineSpec compatibility types
```

不要提前做这些“看起来干净”的改名。

风险：低～中，但依赖前面迁移完成。

---

# 19. 数据迁移策略

阶段 6 每个 migration 都应遵守：

```text
expand -> dual write/read -> verify -> switch -> contract
```

而不是：

```text
rename/drop -> hope everything works
```

## 19.1 Version backfill

优先级：

```text
1. current PUBLISHED row
2. matching Deployment snapshot
3. 无法恢复 -> explicit missing，不猜
```

## 19.2 Execution backfill

当前 Deployment 已有足够多 Snapshot，可以逐步映射：

```text
ExecutionId = deployment.id
TaskId = definition_id
Definition snapshot = spec_snapshot_json
Runtime snapshot = runtime_environment_snapshot_json
Engine ref = gateway_job_id
```

历史 DesiredState 不一定完整存在。

对于已终态历史 deployment，可以通过 status 构造终态 Projection；不要人为制造不存在的历史 intent。

## 19.3 Audit events

历史 `definition_id` 可以解释为 legacy TaskId。

不要因为 schema 演进删除旧 event。

---

# 20. Target Logical Package Direction

阶段 4 不要求立刻执行 package move，但目标职责建议如下。

```text
realtime
├── domain
│   ├── task
│   │   └── RealtimeSyncTask
│   ├── definition
│   │   ├── SyncDefinition
│   │   ├── SyncRoute
│   │   └── policies/selectors/targets
│   ├── version
│   │   └── DefinitionVersion
│   └── execution
│       ├── SyncExecution
│       └── ExecutionStateMachine
│
├── application
│   ├── command
│   ├── query
│   ├── validation
│   └── reconciliation
│
├── port
│   ├── TaskRepository
│   ├── DefinitionVersionRepository
│   ├── SyncExecutionRepository
│   ├── RuntimeEnvironmentPort
│   ├── DataSourceMetadataPort
│   └── ExecutionEnginePort
│
└── infrastructure
    ├── persistence
    ├── flink
    ├── datasource
    ├── yaml
    └── ssh
```

但注意：

> **这是职责方向，不是要求阶段 6 第一 PR 就移动全部 package。**

先修正数据与行为边界，再做物理整理。

---

# 21. UI/API Compatibility Strategy

领域重构不应该强迫前端同步大改。

可以继续输出 legacy-friendly projection：

```text
releaseState
publishedVersion
observedState
latestDeployment
```

但后台来源改成：

```text
Task + PublishedDefinition + Active/Latest Execution
```

例如：

```text
releaseState =
  no publishedRef             -> DRAFT
  publishedRef && draft same  -> PUBLISHED
  publishedRef && newer draft -> DRAFT / 或新增 derived flag
```

更推荐未来新增不破坏字段：

```text
hasUnpublishedChanges
publishedDefinitionVersion
activeExecutionVersion
```

然后前端逐步迁移。

不要让旧 UI 字段定义 Core Domain。

---

# 22. 当前代码 Mapping 总表

| 当前对象 | 分类 | 目标定位 |
|---|---|---|
| `CdcPipelineSpec` | MIGRATE | `SyncDefinition` compatibility model |
| `CdcPipelineSpec.TableRoute` | MIGRATE | `SyncRoute` |
| `CdcPipelineSpecValidator` | ADAPT | Intrinsic Domain Validator + adapter constraints split |
| `ComputeEnvironment` | REMOVE FROM DOMAIN | Compute Environment Context |
| `ComputeEnvironmentSnapshot` | ADAPT | RuntimeEnvironmentSnapshot contract/evidence |
| `ComputeEnvironmentDiagnosis` | REMOVE FROM DOMAIN | Compute Context query model |
| `RealtimeJobState.ReleaseState` | MIGRATE/DERIVE | Task publication projection |
| `DesiredState/ObservedState` | KEEP semantics + MIGRATE ownership | `SyncExecution` |
| `DeploymentState` | KEEP internal | persistence/submission projection |
| `RealtimeStateMachine` | MIGRATE | `SyncExecutionStateMachine` |
| `RealtimeJobView/Page` | REMOVE FROM DOMAIN | query read model |
| `RealtimeObservabilityView` | REMOVE FROM DOMAIN | execution observability read model |
| `RealtimeValidationResult` | REMOVE FROM DOMAIN | application validation result |
| `RealtimeJobChangeEvent` | REMOVE FROM DOMAIN | SSE/application notification |
| `RealtimeDefinitionValidator` | ADAPT | intrinsic/contextual/adapter validation split |
| `RealtimeJobService` | MIGRATE | Task/Publication/Execution use cases |
| `RealtimeJobLifecycleCoordinator` | ADAPT/MIGRATE | active Execution reconciliation |
| `RealtimeJobReconciler` | KEEP | scheduler trigger |
| `RealtimeRuntimeResolver` | ADAPT | RuntimeEnvironmentRef/Snapshot port |
| `RealtimeJobQueryService` | KEEP | query application service |
| `RealtimeObservabilityService` | KEEP/ADAPT | execution query service |
| `RealtimeEventStreamService` | KEEP | interface notification service |
| `RealtimeYamlCodec` | KEEP/ADAPT | YAML serialization adapter |
| `RealtimeJobStore` | MIGRATE | 3 aggregate repositories + query/audit stores |
| `RealtimeJobStoreAdapter` | MIGRATE | compatibility persistence adapter |
| `DefinitionRow` | MIGRATE | Task + Draft compatibility row |
| `DeploymentRow` | ADAPT/MIGRATE | `SyncExecution` persistence |
| `RealtimeJobListQuery` | KEEP | read query port |
| `RealtimeRuntimeIdentityStore` | KEEP | infrastructure recovery store |
| `RealtimeEngineGateway` | ADAPT | engine/application port boundary |
| `FlinkCdcEngineGateway` | KEEP | Flink infrastructure adapter |
| `PipelineYamlCompiler` | KEEP/ADAPT | Flink compiler; apply/reject ExecutionPolicy |
| `RealtimeDataSourceResolver` | KEEP/ADAPT | DataSource adjacent-context adapter |
| `ResolvedCdcPipeline` | KEEP | infrastructure compile model |
| `RealtimeConnectorCapabilityResolver` | KEEP/ADAPT | contextual capability preflight |
| `RealtimeDeployRequest` | KEEP | submission-scoped infrastructure model |
| `RealtimeRuntimeIdentity` | KEEP | uncertain submission recovery |
| `RecoverableRealtimeEngineGateway` | KEEP | infrastructure decorator |
| `FlinkJobDiscoveryClient` | KEEP | recovery adapter |
| `FlinkObservabilityClient` | KEEP | observability adapter |
| `FlinkRuntimeEnvironmentProbe` | KEEP outside domain | Compute Context infra |
| `SshFlinkCdcCommandRunner` | KEEP | infrastructure |
| `RealtimeLogRedactor` | KEEP | infrastructure security |
| `RealtimeJobController` | KEEP/ADAPT | interface adapter |
| Request/View Mapper | KEEP/ADAPT | interface-domain mapper |
| `RealtimeJobDefinitionPO` | MIGRATE | Task + Draft persistence compatibility |
| `RealtimeJobDeploymentPO` | ADAPT/MIGRATE | Execution persistence |
| `RealtimeJobEventPO` | KEEP/ADAPT | audit history |
| `RealtimeRuntimeLeasePO` | KEEP | infrastructure lease |
| `yak_realtime_job_definition` | MIGRATE | Task/Draft row |
| `yak_realtime_job_deployment` | ADAPT/MIGRATE | Execution table |
| `yak_realtime_job_event` | KEEP/ADAPT | audit event table |
| `yak_realtime_runtime_lease` | KEEP | infrastructure |
| `yak_realtime_definition_version` | IMPLEMENTATION GAP | new immutable Version table |

---

# 23. 优先级：真正应该先改什么

阶段 4 给阶段 6 一个明确优先级。

## P0：先修领域事实模型

```text
1. SyncDefinition compatibility model
2. Immutable DefinitionVersion persistence
3. Task PublishedDefinitionRef
4. Start by VersionRef
5. DefinitionDigest canonicalization / digest semantic split
```

这些是后面所有生命周期正确性的基础。

## P1：再迁运行 ownership

```text
6. Deployment -> SyncExecution
7. desired/observed state move to Execution
8. reconciliation move to Execution
9. running edit/publish enabled
10. restart/apply-version split
11. audit-safe archive/delete
```

## P2：最后清边界和名字

```text
12. Read models move out domain package
13. Compute Environment context/package cleanup
14. Engine Port neutralization
15. legacy CdcPipelineSpec/YAML v1 cleanup
16. old release_state/runtime state columns contract
```

如果阶段 6 反过来先做 P2：

```text
先改包名
先改类名
先改表名
```

而没有 P0/P1，那么只是表面 DDD 化，不会解决模型稳定性问题。

---

# 24. 阶段 6 明确不要做的事情

## 24.1 不要 Big Bang 重写 RealtimeJobService

现有并发、幂等、恢复行为必须被保护。

## 24.2 不要重写 FlinkCdcEngineGateway

它已经正确位于 Infrastructure。

## 24.3 不要同时发布 API v2 + YAML v2 + DB v2 + Domain v2

风险过大。

## 24.4 不要直接 rename/drop baseline 表

先 expand。

## 24.5 不要把 Deployment 复制成第二套 Execution 表长期双写

优先演进现有 deployment。

## 24.6 不要从历史 marker 猜 Published Version 内容

无法恢复就显式暴露 migration gap。

## 24.7 不要为了“纯领域模型”把 Infrastructure 安全机制抽象掉

例如：

```text
runtime identity
idempotency
uncertainty
credential zeroize
log redaction
```

这些虽然不属于 Domain，但对于生产正确性同样重要。

---

# 25. 阶段 4 后 AI 必须使用的代码定位流程

从现在开始，AI 在修改 Realtime Sync 代码前，不仅要回答阶段 1～3 的领域问题，还必须先找到当前实现 Mapping。

建议固定输出：

```text
Domain target:
  Aggregate / Value Object / Policy

Current implementation:
  当前类 / 表 / API

Mapping class:
  KEEP / ADAPT / MIGRATE / REMOVE FROM DOMAIN / IMPLEMENTATION GAP

Migration impact:
  是否影响 DB / API / YAML / Execution history

Protected behavior:
  哪些已有安全逻辑不能回归
```

例如需求：

> “允许任务运行时修改配置。”

正确分析应该是：

```text
Domain target:
  RealtimeSyncTask.DefinitionDraft

Current implementation:
  RealtimeJobService.save
  RealtimeStateMachine.requireDefinitionMutable
  RealtimeJobDaoImpl.updateDefinition CAS condition

Mapping:
  MIGRATE

Dependency:
  不能只删 requireDefinitionMutable，
  因为 DAO 仍然用 desired/observed 做 CAS；
  最好在 Execution ownership migration 后放开。
```

而不是直接：

```text
删一个 if 就提交 PR
```

这就是阶段 4 Mapping 真正约束 AI 的价值。

---

# 26. 阶段 4 验收标准

阶段 4 完成后，团队和 AI 应能够回答：

### `definition_version` 是不是 DefinitionVersion？

不是。

```text
current definition_version = DraftRevision
```

真正不可变 DefinitionVersion 目前缺失。

### 当前 Published v3 + Draft v4 为什么不能真正共存？

因为只有一个 `spec_json`，v4 会覆盖 v3 内容，没有 immutable version store。

### 当前 Deployment 要不要废掉？

不要。

它已经非常接近 SyncExecution，应增量演进。

### desired/observed 应该放哪里？

目标放 `SyncExecution`，不是 `RealtimeSyncTask`。

### `RealtimeStateMachine` 要不要保留？

保留显式状态机思想，但迁移为 Execution 生命周期规则，删除 Definition mutation coupling。

### FlinkCdcEngineGateway 要不要 DDD 重写？

不要。

它正确属于 Infrastructure。

### ComputeEnvironment 为什么还在 realtime 模块里？

物理模块位置是现状；领域 ownership 属于邻接 Compute Context。短期可以同 Module，不能继续当 Realtime Sync Core Domain 扩展。

### 当前两个 configDigest 是同一概念吗？

不是。

```text
DefinitionDigest
ExecutionArtifactDigest
```

必须拆分语义。

### 阶段 6 应先改什么？

先补 immutable DefinitionVersion 和 Start-by-Version，再迁 Execution ownership，最后清类名/包名/legacy 字段。

---

# 27. 下一阶段

阶段 5 将基于阶段 1～4，形成真正可直接交给 AI/Codex 的：

```text
Realtime Sync AI Domain Constitution
```

它不再只是设计说明，而是硬性开发规则：

```text
哪些结构禁止新增
新增需求必须先回答什么
哪些 package/依赖方向禁止
哪些历史安全能力必须保护
何时必须标记 Domain Gap
PR 必须提供什么领域影响说明
```

阶段 6 再按照本文件的 Wave 顺序执行最小领域重构。
