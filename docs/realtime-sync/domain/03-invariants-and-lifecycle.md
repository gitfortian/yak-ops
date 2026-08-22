# Realtime Sync 领域不变量与生命周期

> 状态：Proposed（阶段 3；本 PR 合并后视为 Accepted）  
> 前置：[阶段 1：领域边界与统一语言](./01-domain-boundary-and-language.md) / [阶段 2：核心领域模型 v1](./02-core-domain-model.md)  
> 范围：定义 Realtime Sync Domain 的领域不变量、Draft / Publish / Start / Stop / Restart 生命周期、Desired / Observed 状态机和并发规则；本阶段不修改 Java 生产代码和数据库结构。

## 1. 阶段 3 要解决什么

阶段 2 已经确定三个聚合根：

```text
RealtimeSyncTask
DefinitionVersion
SyncExecution
```

以及不可变 `SyncDefinition` Value Object。

阶段 3 继续回答：

> **这些对象允许怎么变化，哪些事情绝对不能发生？**

阶段 3 的目标不是把当前代码行为照抄成文档，而是区分：

```text
真正的领域不变量
当前一期 Application / Persistence 约束
Infrastructure 暂时能力边界
```

以后 AI 如果为了某个局部需求违反本文件中的不变量，应先停止编码并标记 `Domain Gap`。

---

## 2. 不变量分三层

### 2.1 Intrinsic Invariant：对象自身必须永远成立

不依赖数据库、Flink、Catalog 在线状态即可判断。

例如：

- `SyncDefinition` 必须有 Source / Sink；
- v1 Source 与 Sink 不能引用同一个 DataSource；
- Definition 至少有一条 Route；
- Route 必须有非空 ReplayKey；
- `DefinitionVersion` 一旦创建不可修改。

违反 Intrinsic Invariant 的对象不能进入 Domain。

### 2.2 Contextual Invariant：需要邻接上下文事实才能判断

例如：

- Source DataSource 是否存在；
- Selector 是否能匹配真实 Source Object；
- ReplayKey 是否仍与 Source 当前唯一键一致；
- Sink Adapter 是否支持该 Route / Schema Policy；
- Runtime Environment 是否存在、启用并支持当前 Definition。

这类规则通过 Port / Application Preflight 判断。

外部系统临时不可用，不代表 `SyncDefinition` 内容本身突然变成非法 Value Object。

### 2.3 Cross-Aggregate Invariant：多个聚合之间必须保持一致

例如：

- Task 的 `PublishedDefinitionRef` 必须指向同一个 Task 的 DefinitionVersion；
- Execution 必须引用 Published DefinitionVersion；
- Execution Snapshot digest 必须与引用的 DefinitionVersion 一致；
- 同一个 Task v1 最多只有一个 Active / Uncertain Execution。

这类不变量通常由 Application Service + Repository Transaction / Lock / Unique Constraint 共同维护。

---

# Part A：RealtimeSyncTask 与 Draft 生命周期

## 3. RealtimeSyncTask 不变量

### T-1 TaskId 永久稳定

Task 创建后：

```text
TaskId immutable
```

改名称、改 Definition、发布新版本、重新运行，都不能更换 TaskId。

### T-2 TaskProfile 与 SyncDefinition 分离

```text
TaskProfile
├── name
└── description
```

修改名称或描述：

- 不创建新的 DefinitionVersion；
- 不改变 DefinitionDigest；
- 不影响正在运行的 SyncExecution。

如果某个执行引擎需要 Job Name，由 Adapter 在执行边界生成，不把 TaskProfile 混入 Definition 语义。

### T-3 Task 可以没有 Draft

允许创建 Task Shell：

```text
RealtimeSyncTask
├── TaskId
├── TaskProfile
├── DefinitionDraft = empty
└── PublishedDefinitionRef = empty
```

此时不能 Publish / Start。

### T-4 已保存的 DefinitionDraft 必须满足 Intrinsic Invariant

Task Shell 可以没有 Definition；但一旦形成 `DefinitionDraft`，它必须是完整的 Domain Value Object。

不允许持久化：

```text
half-built SyncDefinition
source only
route without ReplayKey
invalid selector variant
```

Wizard / YAML 页面内部可以有半成品表单状态，但那属于 Interface State，不是 Domain Draft。

### T-5 Draft 可以与 Published Version 同时存在

这是 v1 的核心规则。

例如：

```text
PublishedDefinitionRef -> v3
Current Draft           -> v4 candidate
Running Execution       -> v3
```

三者同时存在是合法状态。

因此 Domain 不使用一个互斥的：

```text
ReleaseState = DRAFT | PUBLISHED
```

来表示 Task 的完整发布事实。

页面状态应由事实派生，例如：

```text
UNPUBLISHED
PUBLISHED_CLEAN
PUBLISHED_WITH_CHANGES
```

它们是 Projection / UI Label，不是核心互斥状态轴。

### T-6 运行中允许继续编辑 Draft

**领域允许。**

因为：

```text
SyncExecution -> immutable DefinitionVersion v3
Task Draft     -> independent v4 candidate
```

修改 Draft 不得修改 v3，也不得修改已有 Execution Snapshot。

如果某个一期页面为了降低实现复杂度暂时禁止运行中编辑，这是 Application/UI Constraint，不是 Domain Invariant。

### T-7 运行中允许 Publish 新版本

**领域允许。**

例如：

```text
Execution E100 -> DefinitionVersion v3
Publish Draft   -> DefinitionVersion v4
Task published  -> v4
```

E100 仍然继续运行 v3，不自动升级。

这条规则保证发布和运行解耦。

---

## 4. DraftRevision 并发规则

### D-1 DraftRevision 对同一 Task 单调递增

每次成功替换 DefinitionDraft：

```text
DraftRevision N -> N + 1
```

仅修改 TaskProfile 不要求增加 DraftRevision。

### D-2 保存 Draft 应使用 optimistic compare-and-set

目标语义：

```text
save(taskId, expectedDraftRevision, newDraft)
```

如果当前 revision 已变化：

```text
reject stale write
```

禁止最后写入者静默覆盖其他人的 Draft。

当前实现通过数据库锁保护服务端并发，但客户端没有完整 `expectedDraftRevision` 语义；阶段 4 记录 Mapping，阶段 6 再决定最小迁移。

### D-3 Draft 保存与运行状态正交

DraftRevision 不依赖 Execution 状态。

不能定义：

```text
RUNNING -> draft immutable
```

运行实例已经通过 DefinitionVersion Snapshot 隔离。

---

# Part B：SyncDefinition 不变量

## 5. Endpoint 不变量

### SD-1 SourceEndpoint / SinkEndpoint 必须存在

完整 Definition 必须包含：

```text
SourceEndpoint
SinkEndpoint
```

### SD-2 Endpoint 只引用 DataSource

Core Domain 中禁止出现：

```text
password
jdbcUrl
host
port
username
Flink connector config
```

### SD-3 v1 Source 与 Sink DataSourceRef 必须不同

v1 固定：

```text
source.dataSourceRef != sink.dataSourceRef
```

原因是当前实时同步一期优先避免同一数据源内自同步导致的回环、误写和边界不清。

未来如果明确需要“同一数据源 A 表 -> B 表”，应作为 Domain Gap 重新讨论安全约束，而不是直接删掉校验。

---

## 6. Route 不变量

### R-1 Definition 至少存在一条 Route

```text
routes.size >= 1
```

### R-2 每条 Route 必须完整

```text
SyncRoute
├── SourceSelector
├── SinkTarget
└── ReplayKey
```

三者缺一不可。

### R-3 ReplayKey 必须非空且字段唯一

```text
ReplayKey.fields != empty
ReplayKey.fields has no duplicates
```

v1 不提供：

```text
strictReplaySafety = false
```

Replay Safety 是领域不变量，而不是 boolean option。

### R-4 ReplayKey 字段集合语义不依赖排列顺序

例如：

```text
[id, tenant_id]
[tenant_id, id]
```

在 v1 ReplayKey 语义上视为同一字段集合。

Adapter 如有物理索引顺序要求，应自行处理或显式声明 Capability，不应污染 ReplayKey 的核心身份。

### R-5 Exact Selector 必须表示单个 Source Object

`ExactTableSelector` 不接受列表语法、逗号拼接或 Adapter 特有转义表达式。

### R-6 Pattern Selector 必须是合法模式表达式

语法必须在进入 Domain / Publishable Definition 前验证。

Pattern 的字符串本身属于 Definition 内容；两个语法不同但数学上等价的正则，v1 不尝试自动证明等价。

### R-7 SinkTarget 必须有有效业务目标标识

不允许空目标。

数据库方言 quoting / escaping 属于 Adapter。

### R-8 Route 顺序没有业务语义

```text
[A, B, C]
[C, A, B]
```

如果 Route 内容完全相同，两份 Definition 语义相同。

Canonical Digest 必须消除纯 Route 排序差异。

### R-9 同一个 Source Object v1 最多解析到一条 Route

这是 Contextual Invariant。

在真实 Source Catalog 展开 Selector 后：

```text
one source object -> at most one SyncRoute
```

禁止出现：

```text
Exact orders -> sink_a
Pattern order.* -> sink_b
```

且 `orders` 同时被两条 Route 命中。

否则属于 Ambiguous Routing，Publish / Start Preflight 必须拒绝。

### R-10 多个 Source Object 是否允许写入同一 SinkTarget 由 Capability 决定

Pattern / Sharding 场景天然可能形成 fan-in。

Core Domain 不一刀切禁止，但 Contextual Validation 必须确认：

- Sink Adapter 支持；
- ReplayKey / Schema 兼容；
- 不会产生不可控覆盖。

不支持时明确拒绝，不能静默提交。

---

## 7. Policy 不变量

### P-1 StartupPolicy v1 只允许持续型任务

```text
INITIAL_AND_CONTINUOUS
CHANGES_ONLY
```

不允许：

```text
SNAPSHOT_ONLY
```

因为当前 v1 Execution 生命周期没有“正常有限任务完成”的 Domain State。

### P-2 当前持续型任务的自然 FINISHED 不是成功

对于：

```text
INITIAL_AND_CONTINUOUS
CHANGES_ONLY
```

如果外部引擎自然结束且用户没有 Stop 意图：

```text
ObservedState -> FAILED
```

未来引入 `SNAPSHOT_ONLY` 时，必须先扩展领域状态，例如 `COMPLETED`，不能继续把正常 FINISHED 当失败。

### P-3 SchemaEvolutionPolicy 必须使用领域枚举

v1：

```text
EVOLVE
IGNORE
FAIL
```

Adapter 必须显式映射支持能力。

### P-4 ExecutionPolicy 必须是语义完整的策略

v1：

```text
Parallelism
CheckpointPolicy
NoRestart | FixedDelayRestart
SinkWritePolicy
```

不接受语义不完整的 `failure-rate` 半成品 Value Object。

### P-5 Adapter 对 ExecutionPolicy 必须“执行或拒绝”

禁止：

```text
Domain 接受配置
DB 保存配置
Adapter 静默忽略
```

任何 Adapter 必须：

```text
supports(policy) -> execute
or
reject with explicit capability error
```

当前 `checkpointIntervalMs / restart` 已保存但没有真正进入 Flink Pipeline，属于 Implementation Gap，阶段 4 继续 Mapping。

### P-6 Adapter 私有 Tuning 不得反向升级为 Core Policy

例如：

```text
statementCacheSize
connectorJarPath
flinkRestAddress
```

不属于 Core `ExecutionPolicy`。

---

# Part C：Contextual Validation 生命周期

## 8. Draft / Publish / Start 的校验层级

### 8.1 保存 Draft：必须满足 Intrinsic Invariant

Domain 要求：

```text
Draft structurally valid
```

### 8.2 保存 Draft 是否强制访问 Source Catalog，是 Application Policy

当前一期实现保存前会读取 Source Catalog、检查主键漂移和 Connector Capability。

阶段 3 明确：

> 外部 Catalog 临时不可用不属于 DefinitionDraft 的 Intrinsic Invariant。

Application 可以继续把“保存前完整 Preflight”作为当前产品策略，但不能把“Source DB 必须在线”写成 `SyncDefinition` 的永久领域规则。

### 8.3 Publish 必须执行 Contextual Preflight

Publish 前必须确认至少：

- Source / Sink Ref 当前存在；
- Endpoint 类型/能力受支持；
- Selector 在 Source Catalog 中可解析；
- 每个 Source Object 只被一条 Route 命中；
- ReplayKey 与 Source 当前唯一键事实兼容；
- Sink / Schema Evolution / fan-in 能力兼容；
- RuntimeEnvironmentRef 存在且允许绑定；
- 当前 Adapter 能执行 ExecutionPolicy。

Publish 成功后 DefinitionVersion 不因外部环境后来变化而被修改。

### 8.4 Start 必须重新执行 Runtime Context Preflight

发布成功不代表未来永远可运行。

Start 前必须重新确认：

- DefinitionVersion 仍可解析；
- Source Schema / ReplayKey 未发生危险漂移；
- DataSource / Runtime Environment 当前可用；
- Adapter / Connector Capability 当前仍支持；
- 环境启用；
- ExecutionPolicy 能被当前 Runtime Adapter 执行。

如果外部事实已漂移：

```text
Start rejected
DefinitionVersion remains immutable
```

不能为了“让它能启动”偷偷改历史 DefinitionVersion。

---

# Part D：DefinitionVersion 生命周期

## 9. DefinitionVersion 不变量

### V-1 Version 只能由 Publish 创建

不能：

```text
save draft -> silently become production version
```

### V-2 VersionNo 对同一 Task 单调递增且不复用

```text
v1, v2, v3 ...
```

即使某个版本后来不再是 current published，也不回收版本号。

### V-3 DefinitionVersion 一经创建永久不可变

以下全部不可原地修改：

- `SyncDefinition`；
- `RuntimeEnvironmentRef`；
- digest；
- publication metadata；
- TaskId / VersionNo。

“修改已发布版本”必须形成新版本。

### V-4 PublishedDefinitionRef 必须引用同 Task Version

```text
Task A.publishedRef -> Task A DefinitionVersion
```

禁止跨 Task 指向。

### V-5 Publish 必须基于明确 DraftRevision

Publish 过程应携带：

```text
expectedDraftRevision
expectedDefinitionDigest
```

如果在 Preflight 期间 Draft 已变化：

```text
abort publish
refresh / retry
```

不能发布“用户已经改掉的旧草稿”。

### V-6 Publish 创建 Version + 更新 PublishedRef 必须原子化

成功语义：

```text
create immutable DefinitionVersion vN
+
Task.publishedRef = vN
```

二者必须一起成功或一起失败。

### V-7 重复发布同一 Draft 内容应幂等

如果当前 Draft 的 canonical digest + RuntimeEnvironmentRef 与 current Published Version 完全一致：

```text
publish again -> keep current published version
```

默认不制造无意义的重复版本。

如果未来需要“相同内容重新发布也创建审计版本”，必须作为显式领域需求重新讨论。

### V-8 发布新版本不影响旧 Execution

```text
Execution E100 -> v3
publish v4
E100 still -> v3
```

绝不自动迁移。

---

## 10. 不使用单一 ReleaseState 描述 Task

阶段 3 正式确认：

```text
DRAFT | PUBLISHED
```

不够表达真实生命周期，因为：

```text
published v3
+
draft v4 candidate
```

可以同时存在。

Domain 使用事实：

```text
currentDraft
publishedRef
```

UI 可以派生：

| Projection | 含义 |
|---|---|
| `UNPUBLISHED` | 有 Draft，但从未发布 |
| `PUBLISHED_CLEAN` | Draft 与 Published 内容一致 |
| `PUBLISHED_WITH_CHANGES` | 已有 Published，但 Draft 又发生变化 |
| `EMPTY` | Task Shell 尚无完整 Draft |

这些不是 Core enum 的强制持久化模型。

---

# Part E：SyncExecution 生命周期

## 11. Execution 创建不变量

### E-1 Execution 只能来自 DefinitionVersion

禁止：

```text
start current draft
```

正确：

```text
StartTask
  -> resolve Task.publishedRef
  -> load DefinitionVersion
  -> create SyncExecution
```

### E-2 Draft 是否有未发布修改不影响启动已发布版本

例如：

```text
Published = v4
Draft     = v5 candidate
```

Start 明确运行：

```text
v4
```

允许。

页面可以提示“存在未发布修改，本次运行 v4”，但不能把 v4 判定为不可启动。

### E-3 同一个 Task v1 最多一个 Active / Uncertain Execution

Active / Uncertain 状态：

```text
STARTING
RUNNING
STOPPING
UNKNOWN
CONFLICT
```

只要存在这些状态的 Execution：

```text
new Start rejected
```

原因：避免同一实时同步任务产生双写和重复 CDC 消费。

终态：

```text
STOPPED
FAILED
```

终态 Execution 不阻止创建新的 Execution。

### E-4 每次 Start 创建新的 SyncExecution

不能把历史：

```text
STOPPED E100
```

重新修改为：

```text
STARTING E100
```

新的启动必须：

```text
E101
```

因此 `STOPPED / FAILED` 对单个 Execution 是终态。

### E-5 Execution Definition Snapshot 必须与 Version 一致

创建时：

```text
Execution.definitionVersionRef = vN
Execution.definitionSnapshot.digest = vN.digest
```

不一致直接视为数据完整性错误。

### E-6 RuntimeEnvironmentSnapshot 在 Start 时冻结

DefinitionVersion 保存：

```text
RuntimeEnvironmentRef
```

Start 时解析并保存：

```text
RuntimeEnvironmentSnapshot
```

后续 stop / reconcile / observability 必须优先使用 Execution 自己的 Snapshot，不重新用 Task 当前环境配置解释历史运行。

### E-7 EngineExecutionRef 一旦绑定不可替换

STARTING 初期：

```text
engineRef = empty
```

引擎接受后：

```text
engineRef = EngineExecutionRef(engine, externalId)
```

一旦绑定，不能静默改成另一个 externalId。

发现多个候选或不同 ID 时进入 `CONFLICT` / integrity handling，而不是“挑一个覆盖”。

---

# Part F：DesiredState / ObservedState

## 12. 两条状态轴含义

### DesiredState

表示控制面当前希望 **这个 Execution** 达到什么状态：

```text
RUNNING
STOPPED
```

它不是 Runtime 真实状态。

### ObservedState

表示系统目前对 **这个 Execution** 的事实判断：

```text
STARTING
RUNNING
STOPPING
STOPPED
FAILED
UNKNOWN
CONFLICT
```

### 关键规则

```text
Desired != Observed
```

并不意味着数据错误。

例如：

```text
desired = STOPPED
observed = STOPPING
```

是正常收敛过程。

---

## 13. ObservedState 分类

### 13.1 Active / Uncertain

```text
STARTING
RUNNING
STOPPING
UNKNOWN
CONFLICT
```

这些状态都必须认为“可能仍有外部运行存在”。

因此：

- 阻止创建第二个 Execution；
- 阻止危险硬删除运行证据；
- UNKNOWN / CONFLICT 必须先 Reconcile /人工处理。

### 13.2 Terminal

```text
STOPPED
FAILED
```

单个 Execution 进入终态后不再回到 STARTING / RUNNING。

如果用户再次启动：创建新的 SyncExecution。

如果终态之后又发现外部运行仍存在：

```text
orphan / integrity conflict
```

不能简单把历史 Execution 从 STOPPED/FAILED 改回 RUNNING。

阶段 4/6 再决定具体 orphan evidence 持久化方式。

---

## 14. ObservedState 合法迁移

### STARTING

允许：

```text
STARTING -> RUNNING
STARTING -> STOPPING
STARTING -> FAILED
STARTING -> UNKNOWN
STARTING -> CONFLICT
```

### RUNNING

允许：

```text
RUNNING -> STOPPING
RUNNING -> FAILED
RUNNING -> UNKNOWN
RUNNING -> CONFLICT
```

### STOPPING

允许：

```text
STOPPING -> STOPPED
STOPPING -> FAILED
STOPPING -> UNKNOWN
STOPPING -> CONFLICT
```

### UNKNOWN

Reconcile 后允许：

```text
UNKNOWN -> RUNNING
UNKNOWN -> STOPPING
UNKNOWN -> STOPPED
UNKNOWN -> FAILED
UNKNOWN -> CONFLICT
```

### CONFLICT

冲突解除后允许：

```text
CONFLICT -> RUNNING
CONFLICT -> STOPPING
CONFLICT -> STOPPED
CONFLICT -> FAILED
CONFLICT -> UNKNOWN
```

### STOPPED / FAILED

```text
terminal
no outgoing transition
```

新的启动创建新的 Execution。

---

## 15. DesiredState 变化规则

### DS-1 新 Execution 初始 DesiredState = RUNNING

Start 成功完成 Execution reservation 后：

```text
desired = RUNNING
observed = STARTING
```

### DS-2 Stop 命令立即设置 DesiredState = STOPPED

不要等外部引擎返回成功才改变 intent。

```text
RUNNING + stop
=> desired STOPPED
=> observed STOPPING
```

### DS-3 Reconcile 不修改 DesiredState

Reconciler 只根据外部事实更新：

```text
ObservedState
EngineExecutionRef
error evidence
```

不能把用户意图偷偷改掉。

### DS-4 FAILED 可以保留 desired = RUNNING

如果用户希望运行，但外部任务意外终止：

```text
desired = RUNNING
observed = FAILED
```

准确表达：

> 用户希望运行，但本次 Execution 已失败。

FAILED 已是终态，因此不会触发“原 Execution 自动复活”。用户再次 Start 会创建新 Execution。

如果失败前已经收到 Stop：

```text
desired = STOPPED
observed = FAILED | STOPPED
```

由实际终止原因决定。

---

# Part G：Start 生命周期

## 16. Start Precondition

Start Task 前必须：

1. Task 存在；
2. Task 有 `PublishedDefinitionRef`；
3. Published Ref 对应 DefinitionVersion 存在且属于该 Task；
4. 不存在 Active / Uncertain Execution；
5. Start Contextual Preflight 通过；
6. Runtime Environment 当前可解析并启用；
7. Adapter 明确支持 Definition / Policy；
8. Idempotency 规则通过。

**不要求：**

```text
current Draft == published version
```

Draft 可以领先。

---

## 17. Start Reservation 必须固定 DefinitionVersion

Start 在并发边界内先固定：

```text
TaskId
DefinitionVersionRef
ExecutionId
RuntimeEnvironmentSnapshot
IdempotencyKey
```

一旦 Reservation 成功：

```text
后续即使 Task 又发布 vN+1
当前 Execution 仍运行已预留 vN
```

不能在 CLI/REST 提交结束后重新读取 `Task.publishedRef` 决定运行版本。

---

## 18. Start 与 Publish 并发

允许：

```text
Start reserve v3
      ||
Publish v4
```

如果 Start 已经原子预留 v3：

```text
Execution -> v3
Task.publishedRef -> v4
```

两者都可以成功。

这不是冲突，因为 Execution 绑定明确 Version。

如果 Start 还没有预留版本，则以它事务内读取到的 Published Ref 为准。

---

## 19. Start Idempotency

### I-1 同一 IdempotencyKey + 同一 Task 返回同一 Execution

重复请求不能创建多个 Execution。

### I-2 同一 Key 被其他 Task 使用必须拒绝

当前实现已经具备此行为，可保留。

### I-3 已创建 Execution 后，即使 FAILED，也不能用同一个 Key 创建新 Execution

同一 Key 永远代表同一次 Start Intent。

重新尝试必须使用新 Key。

### I-4 Preflight 尚未创建 Reservation 前失败，可以安全重试

如果请求在创建 SyncExecution 前就因为 Contextual Preflight 被拒绝，没有产生 Execution Identity；Application 可以允许相同 Key 重新尝试。

---

## 20. Start 提交结果语义

### 20.1 明确成功

```text
STARTING
-> bind EngineExecutionRef
-> RUNNING
```

### 20.2 明确失败

如果能确定外部运行没有被创建：

```text
STARTING -> FAILED
```

### 20.3 结果不确定

例如：

- CLI 超时；
- 网络断开；
- 提交返回不完整；
- JobId 未确认，但可能已经创建。

必须：

```text
STARTING -> UNKNOWN
```

绝不能立即当成 FAILED 再创建第二个 Execution。

必须通过 runtime identity / discovery / reconcile 先确认。

### 20.4 多个候选运行实例

如果一次 Execution Identity 匹配多个外部 Job：

```text
-> CONFLICT
```

禁止自动挑一个 JobId 绑定。

---

# Part H：Stop 生命周期

## 21. Stop Intent

Stop 命令首先：

```text
Execution.desired = STOPPED
```

再驱动 observed 收敛。

### RUNNING + Stop

```text
RUNNING -> STOPPING -> STOPPED
```

### STARTING + Stop

允许：

```text
STARTING
-> desired STOPPED
-> STOPPING
```

如果 EngineExecutionRef 尚未返回：

```text
保持 STOPPING
```

不能因为当前没有 JobId 就假装已经 STOPPED。

一旦提交返回外部 ID：

```text
bind exact EngineExecutionRef
immediately cancel it
-> STOPPED / UNKNOWN
```

当前一期实现已经采用这个原则，应保留。

### UNKNOWN / CONFLICT + Stop

Stop 不能凭猜测宣布成功。

需要：

- 先找到唯一外部运行；或
- 证明不存在活动运行；或
- 保持 UNKNOWN / CONFLICT 并要求对账。

---

## 22. Stop 结果不确定

如果：

- Stop REST 超时；
- Engine 状态不可读；
- 不确定 cancel 是否成功；

则：

```text
observed -> UNKNOWN
```

不能直接：

```text
STOPPED
```

直到 Reconcile 获取确定事实。

---

# Part I：Reconcile 生命周期

## 23. Reconcile 的职责

Reconcile 是：

> 用外部运行事实纠正 `ObservedState`，使 Execution 接近真实世界。

Reconcile 不负责：

- 修改 Draft；
- 切换 PublishedDefinitionRef；
- 修改 DefinitionVersion；
- 修改 DesiredState；
- 自动创建第二个 Execution。

---

## 24. Reconcile 规则

### RC-1 External status 不可读

达到容错阈值后：

```text
-> UNKNOWN
```

短暂网络抖动不应立刻污染 Domain State。

### RC-2 desired RUNNING，但确认外部运行不存在 / 已异常终止

当前连续型任务：

```text
-> FAILED
```

### RC-3 desired STOPPED，且确认外部运行不存在

```text
-> STOPPED
```

### RC-4 desired STOPPED，但发现外部仍 RUNNING

```text
-> STOPPING
```

Application / Adapter 应继续发出 stop。

### RC-5 runtime identity 匹配多个 Job

```text
-> CONFLICT
```

不得自动绑定。

### RC-6 UNKNOWN 经确认后可以恢复事实状态

例如：

```text
UNKNOWN -> RUNNING
UNKNOWN -> STOPPING
UNKNOWN -> STOPPED
UNKNOWN -> FAILED
```

UNKNOWN 是“知识不足”，不是执行失败。

---

# Part J：Restart 与版本升级

## 25. Restart 必须区分“重启同版本”和“应用新版本”

这是阶段 3 的重要领域规则。

### RestartExecution

语义：

> 把当前/指定 Execution 使用的同一个 DefinitionVersion 再运行一次。

流程：

```text
E100(v3) stop
      ↓
E101(v3) start
```

必须仍然是 v3。

### ApplyPublishedVersion / Redeploy

语义：

> 停止当前 Execution，然后使用 Task 当前 PublishedDefinitionRef 创建新 Execution。

例如：

```text
E100(v3) running
Task published -> v4

ApplyPublishedVersion
  ↓
stop E100(v3)
  ↓
start E101(v4)
```

### 为什么必须区分

如果“Restart”偷偷读取当前 PublishedRef：

```text
用户以为重启 v3
实际升级到 v4
```

会产生不可接受的隐式版本升级。

当前一期 `restart()` 通过 stop + current task start 实现，后续阶段应检查它是否可能跨版本；阶段 4 标记具体 Mapping，阶段 6 再重构。

---

# Part K：并发规则

## 26. Edit 与 Execution 并发

允许：

```text
edit Draft v4
while Execution E100(v3) RUNNING
```

二者不同聚合，不互相锁死。

## 27. Publish 与 Execution 并发

允许：

```text
publish v4
while E100(v3) RUNNING
```

已有 Execution 不受影响。

## 28. Start 与 Start 并发

必须通过：

- per-Task active Execution uniqueness；
- transaction / row lock；
- IdempotencyKey；
- DB unique constraint（适合时）；

保证最多创建一个 Active Execution。

## 29. Start 与 Stop 并发

Stop Intent 优先被持久化。

如果提交已经跨过外部边界但还没拿到 JobId：

```text
Execution desired STOPPED
observed STOPPING
```

等 JobId 返回后取消精确外部运行。

不能覆盖用户已经提交的 Stop Intent。

## 30. Publish 与 Draft Save 并发

Publish 必须基于 `expectedDraftRevision`。

Draft 在 Publish Contextual Preflight 期间发生变化：

```text
Publish abort
```

不能发布旧快照后再把 Task 错误标成“当前 Draft 已发布”。

## 31. Runtime Environment 变更与 Start 并发

Start 在 Reservation 阶段解析并固定：

```text
RuntimeEnvironmentSnapshot
```

之后 validate / compile / deploy / stop / reconcile 应围绕该 Snapshot 工作。

环境后续配置修改不能改变已经创建的 Execution 事实。

---

# Part L：Snapshot / Digest 不变量

## 32. DefinitionDigest 范围

Canonical Publication Digest 至少覆盖：

```text
SyncDefinition
+
RuntimeEnvironmentRef
```

不覆盖：

```text
TaskProfile.name
UI editor mode
Yak YAML formatting/comments
Flink compiled YAML
RuntimeEnvironmentSnapshot mutable details
```

### Canonicalization

必须消除无业务语义差异，例如：

- Route 集合顺序；
- ReplayKey 字段顺序；
- JSON 对象属性输出顺序。

Pattern 原始表达式字符串视为语义内容，不尝试证明两个不同 regex 是否数学等价。

## 33. Execution Snapshot 一致性

创建 Execution 时：

```text
snapshot.definition == referenced Version.definition
snapshot.digest == referenced Version.digest
```

后续两者不一致：

```text
Data Integrity Error
```

不是“哪个新就覆盖哪个”。

## 34. Execution Snapshot 永远只读

不能：

```text
edit Execution Snapshot
-> update Version
```

也不能：

```text
Version changed
-> rewrite historical Execution Snapshot
```

---

# Part M：删除与历史证据

## 35. SyncExecution 不允许业务侧硬删除

Execution 是运行审计事实。

正常产品操作只能：

- Start；
- Stop；
- Reconcile；
- 查询；
- 按 retention policy 归档。

不能因为“任务列表想干净”直接删除运行记录。

## 36. DefinitionVersion 不允许原地删除被引用版本

只要被以下任一对象引用：

- SyncExecution；
- Workflow；
- Task Catalog；
- 审计记录；

该 Version 必须保留。

## 37. RealtimeSyncTask 硬删除限制

v1 安全原则：

### 可以直接硬删除

仅建议用于：

```text
never published
+
no execution history
+
no external reference
+
no active/uncertain runtime
```

的 Task Shell / Draft-only Task。

### 已经 Published / Executed

默认不应物理级联删除历史 Version / Execution。

后续更适合引入：

```text
Archive / Tombstone
```

具体持久化模型留给阶段 4 / 6。

当前一期允许“已停止即删除 Task metadata”的实现属于需要在阶段 4 评估的 Domain Debt。

---

# Part N：当前实现与阶段 3 的差异

## 38. 当前 RealtimeStateMachine 中值得保留的原则

现有实现已经有：

```text
DesiredState / ObservedState 分轴
STARTING / RUNNING / STOPPING / UNKNOWN / CONFLICT
明确 transition 校验
启动幂等
停止与启动竞争处理
UNKNOWN 不假装成功
```

这些方向与阶段 3 一致。

## 39. 当前 desired/observed 放在 DefinitionRow 是 Persistence Shortcut

阶段 3 目标语义：

```text
DesiredState / ObservedState belong to SyncExecution
```

当前为了 MVP 将它们压在 Task Definition Row 上，不代表最终 Domain 归属。

## 40. 当前“只有 STOPPED / FAILED 才能编辑/发布”不是 Domain Invariant

现有 `RealtimeStateMachine.requireDefinitionMutable()` 将 Draft mutation 与 runtime state 绑定。

阶段 3 正式决定：

```text
运行中的 Execution 已绑定 immutable Version
=> Task Draft 可以继续编辑
=> 新 Version 可以继续发布
```

因此当前限制属于一期 Application Constraint / Domain Debt。

阶段 4 映射，阶段 6 再决定最小改造顺序。

## 41. 当前“Draft 变化后旧 Published 不允许 Start”不是目标 Domain Rule

当前实现要求：

```text
publishedVersion == definitionVersion
```

才能 Start。

阶段 3 目标：

```text
Start reads PublishedDefinitionRef
Draft can be ahead
```

存在未发布修改时仍可运行明确的已发布版本。

## 42. 当前 ReleaseState DRAFT/PUBLISHED 是压缩表示

目标 Domain 不把它当唯一发布事实。

应该以：

```text
currentDraft
publishedRef
```

作为真实事实，再派生 UI 状态。

## 43. 当前 ObservedState FAILED 可以重新跳 STARTING/RUNNING 是 Task-level 状态遗留

阶段 3 中：

```text
单个 SyncExecution 的 FAILED 是终态
```

再次启动创建新的 Execution。

## 44. 当前 Restart 需要检查是否会发生隐式版本升级

目标：

```text
RestartExecution = same Version
ApplyPublishedVersion = current Published Version
```

必须分开。

## 45. 当前保存 Draft 强制完整 Contextual Preflight 是产品策略，不是 Intrinsic Domain Invariant

可以暂时保留，但后续不能因为 Source DB 临时离线就认定历史 `SyncDefinition` 内容本身非法。

## 46. 当前 delete 只验证 runtime inactive 仍不足以保护历史聚合

未来需要同时考虑：

```text
DefinitionVersion references
Execution history
Workflow / Task Catalog references
retention policy
```

---

# Part O：AI / 开发强制检查

## 47. 新需求在编码前必须回答

### 领域定位

```text
1. 修改 RealtimeSyncTask、DefinitionVersion、SyncExecution 中哪个聚合？
2. 是否只是 UI / Infrastructure / DataSource / Runtime Environment concern？
```

### 不变量影响

```text
3. 是否改变本文件已有 Intrinsic Invariant？
4. 是否增加 Contextual Invariant？需要哪个 Port 提供事实？
5. 是否改变 Cross-Aggregate Invariant？
```

### 生命周期影响

```text
6. 是否新增状态？
7. 是否新增状态迁移？
8. 是否会让 STOPPED / FAILED 终态重新复活？如果会，默认拒绝。
```

### 版本安全

```text
9. 是否会让 Draft 修改已发布 Version？
10. 是否会让 Publish 自动影响已有 Execution？
11. 是否会让 Restart 隐式升级版本？
```

### 运行安全

```text
12. 是否可能同 Task 创建第二个 Active / Uncertain Execution？
13. 是否在结果不确定时错误创建新 Execution？
14. 是否会覆盖已绑定 EngineExecutionRef？
```

无法满足时：

```text
Domain Gap
```

先讨论模型，不直接写临时字段绕过。

---

## 48. 阶段 3 后的核心铁律摘要

```text
1. Task / Version / Execution 生命周期分离。

2. Draft 可以在运行中继续编辑；Publish 新版本不自动影响旧 Execution。

3. Start 只运行明确 Published DefinitionVersion，不读取当前 Draft。

4. Draft 可以领先 Published；未发布修改不使旧 Published 自动失效。

5. DefinitionVersion 永远不可变。

6. 每次 Start / Restart 都创建新的 SyncExecution。

7. 同 Task v1 最多一个 Active / Uncertain Execution。

8. STOPPED / FAILED 是单个 SyncExecution 的终态。

9. UNKNOWN 表示知识不足，不等于失败；CONFLICT 禁止自动猜测绑定。

10. Stop Intent 先落 DesiredState，再等待 ObservedState 收敛。

11. Execution Snapshot 与 Runtime Snapshot 一经创建不可被后续 Draft/环境修改污染。

12. Restart 同版本与 ApplyPublishedVersion 必须语义分开。

13. ReplayKey 是必选领域语义，不存在 strictReplaySafety=false 的 v1 Core Model。

14. Adapter 对领域 Policy 必须执行或明确拒绝，禁止静默忽略。

15. Published / Executed 历史事实默认保留，不以“停止了”作为任意硬删除理由。
```

---

## 49. 阶段 3 验收示例

### “运行中的 v3 能不能编辑 v4？”

```text
可以。
Draft 与 Execution 是不同聚合生命周期。
```

### “运行中的 v3 能不能发布 v4？”

```text
可以。
v3 Execution 不变，Task publishedRef 可推进到 v4。
```

### “此时点击 Start 会怎样？”

```text
已有 Active Execution -> 拒绝创建第二个 Execution。
```

### “停止 v3 后点击 Start 呢？”

```text
使用当前 PublishedDefinitionRef，例如 v4，创建新的 Execution。
```

### “点 Restart v3 呢？”

```text
语义应继续运行 v3，而不是偷偷升级 v4。
```

### “YAML 改了但没发布，还能启动旧 v3 吗？”

```text
可以，明确启动 Published v3；页面可以提示存在未发布 Draft。
```

### “Flink 提交超时，不知道有没有成功，可以立即再启动吗？”

```text
不可以。
当前 Execution -> UNKNOWN，必须先 Reconcile。
```

### “FAILED 的 Execution 可以改回 STARTING 吗？”

```text
不可以。
FAILED 是该 Execution 终态；新 Start 创建新的 Execution。
```

### “发布后 Source 主键变了怎么办？”

```text
DefinitionVersion 不修改；Start Preflight 拒绝运行并提示 ReplayKey drift。
```

### “运行环境发布后被升级了怎么办？”

```text
DefinitionVersion 仍保存 RuntimeEnvironmentRef；Start 解析当前环境并冻结 RuntimeEnvironmentSnapshot。
历史 Execution 始终使用自己的 Snapshot 解释运行事实。
```

---

## 50. 下一阶段

阶段 4 将把阶段 1~3 的目标领域模型映射回现有代码：

```text
CdcPipelineSpec
DefinitionRow
DeploymentRow
RealtimeStateMachine
RealtimeJobService
RealtimeJobLifecycleCoordinator
PipelineYamlCompiler
DB schema
```

并把每项标记成：

```text
KEEP
ADAPT
MIGRATE
REMOVE FROM DOMAIN
IMPLEMENTATION GAP
```

阶段 4 仍以 Mapping 和迁移设计为主，不进行大规模重构。
