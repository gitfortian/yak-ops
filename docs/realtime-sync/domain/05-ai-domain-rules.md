# Realtime Sync AI 领域开发宪法

> 状态：Proposed（阶段 5；本 PR 合并后视为 Accepted）  
> 前置：  
> [阶段 1：领域边界与统一语言](./01-domain-boundary-and-language.md)  
> [阶段 2：核心领域模型 v1](./02-core-domain-model.md)  
> [阶段 3：领域不变量与生命周期](./03-invariants-and-lifecycle.md)  
> [阶段 4：现有代码 Mapping](./04-current-code-mapping.md)  
> 模块级硬规则：`yak-ops-business/yak-ops-business-sync/yak-ops-business-sync-realtime/DOMAIN.md`
>
> 范围：所有修改、扩展、评审 Yak Ops Realtime Sync 的人类开发者、AI、Codex、代码生成器和 Review Agent。

---

# 1. 为什么需要 AI 领域宪法

阶段 1～4 已经有完整领域设计，但“有文档”并不等于 AI 会遵守。

AI 的典型风险不是不会写代码，而是：

```text
用户提出一个局部需求
        ↓
AI 优先寻找最短实现路径
        ↓
新增字段 / enum / Service / DTO
        ↓
局部功能工作
        ↓
领域模型出现第二套事实、状态错位或技术细节泄漏
```

例如需求：

```text
“增加整库同步”
```

AI 很容易直接写：

```java
syncType = WHOLE_DATABASE;
```

或者：

```java
WholeDatabaseRealtimeTaskService
```

这在局部可能非常快，但会破坏阶段 1～4 已经确定的：

```text
SourceSelector + SyncRoute + SinkTarget + Policy
```

组合模型。

因此阶段 5 的目标不是新增领域模型，而是把阶段 1～4 转换成 AI 能执行的：

```text
前置分析规则
禁止项
停止条件
允许扩展点
迁移顺序
Review Checklist
输出契约
```

从本阶段开始，Realtime Sync 不再接受：

> “先把代码写出来，以后再重构领域模型。”

正确顺序是：

```text
Requirement
   ↓
Domain Impact Analysis
   ↓
Existing model fits?
   ├─ yes -> implement inside model
   └─ no  -> Domain Gap -> model decision first
```

---

# 2. 规则的适用范围

以下变更都必须遵守本宪法：

- Realtime Sync Java Domain；
- Application Service；
- Publish / Start / Stop / Restart / Reconcile；
- Repository / DAO / DB migration；
- Flink CDC Adapter；
- DataSource / Runtime Environment 集成；
- Yak Realtime YAML；
- Wizard；
- REST API / DTO；
- Observability；
- Task Catalog / Workflow 对 realtime 的引用；
- Lineage 对 realtime route 的消费；
- 新 Source / Sink；
- 新同步模式；
- 新 ExecutionPolicy；
- 数据迁移与兼容逻辑。

即使修改的文件不在 realtime Maven module 中，只要它改变了 Realtime Sync 的领域语义，也受本规则约束。

---

# 3. 规则权威顺序

Realtime Sync 的设计语义按以下顺序解释：

```text
1. 最新、明确、已确认的领域决策
2. realtime module DOMAIN.md
3. docs/realtime-sync/domain/ 阶段文档
4. 编码领域不变量的测试
5. 当前生产代码 / 当前数据库字段名
```

这一顺序非常重要。

当前代码是：

```text
兼容事实
运行事实
历史实现证据
```

但不是目标 Domain 的最高权威。

例如当前数据库叫：

```text
definition_version
```

并不意味着 AI 可以直接把它解释为阶段 2 的：

```text
DefinitionVersion.VersionNo
```

阶段 4 已明确它当前更接近：

```text
DraftRevision
```

所以：

> **永远不要只根据现有类名/字段名反推领域模型。**

---

# 4. AI 必须先完成 Domain Impact Analysis

任何 realtime 编码任务，在第一次写代码之前必须先输出：

```text
Domain Impact Analysis

Bounded Context:
Aggregate(s):
SyncDefinition Area:
Invariant/Lifecycle Impact:
Layer:
Existing Mapping / Gap:
Migration Wave:
Safety Protection List:
Domain Gap: yes/no
```

## 4.1 Bounded Context

只能先判断：

```text
Realtime Sync
DataSource
Compute/Runtime Environment
Task Catalog / Workflow
Lineage
Infrastructure Adapter
Interface/UI
```

不能默认：

```text
“功能在 realtime 页面上，所以一定属于 Realtime Sync Domain。”
```

例如：

```text
SSH 私钥路径
```

虽然页面可能出现在 realtime 设置流程里，但它属于 Compute Environment / Infrastructure，不属于 `SyncDefinition`。

## 4.2 Aggregate(s)

如果属于 Realtime Sync Domain，必须说明修改：

```text
RealtimeSyncTask
DefinitionVersion
SyncExecution
```

中的哪一个。

如果无法归属：

```text
Domain Gap = yes
```

## 4.3 SyncDefinition Area

如果定义内容发生变化，需要进一步归属：

```text
SourceEndpoint
SinkEndpoint
SyncRoute
SourceSelector
SinkTarget
ReplayKey
SyncPolicy
StartupPolicy
SchemaEvolutionPolicy
ExecutionPolicy
```

不能只说：

```text
“修改 PipelineSpec。”
```

## 4.4 Invariant/Lifecycle Impact

必须说明是否改变：

- Definition intrinsic invariant；
- Contextual preflight；
- Publish invariant；
- Execution active uniqueness；
- Desired / Observed transition；
- Version immutability；
- Snapshot consistency；
- restart/version semantics；
- deletion/history semantics。

## 4.5 Layer

每个主要改动必须属于：

```text
Domain
Application
Infrastructure
Interface/UI
```

如果一个字段同时看起来属于 Domain 又属于 Flink/JDBC：

```text
先视为边界警报
```

## 4.6 Existing Mapping / Gap

必须引用阶段 4 已记录的 Mapping 或 Gap。

例如：

```text
GAP-01 Immutable DefinitionVersion Store
GAP-04 Execution lifecycle ownership
GAP-11 ExecutionPolicy application
```

不能无视已有迁移施工图另起一套实现。

## 4.7 Migration Wave

涉及核心迁移时，必须说明属于：

```text
Wave 0 ~ Wave 6
```

并确认没有跳过前置 Wave。

## 4.8 Safety Protection List

如果改动触及 Start / Stop / Reconcile / Flink adapter，必须明确哪些安全资产不能退化。

## 4.9 Domain Gap

如果无法在当前模型中无歧义表达：

```text
Domain Gap = yes
```

此时 AI 的下一步应该是：

```text
提出领域扩展方案
列出模型变化
列出不变量变化
等待领域决策
```

而不是：

```text
创建一个临时字段然后继续编码
```

---

# 5. 三个聚合根是 AI 的固定坐标

阶段 2 已接受：

```text
RealtimeSyncTask
DefinitionVersion
SyncExecution
```

## 5.1 RealtimeSyncTask

只回答：

```text
这条长期任务是谁？
现在在编辑什么？
当前发布引用是什么？
```

不要往 Task 中塞：

```text
Flink JobId
Pipeline YAML
SSH Config
完整 Execution 历史
完整 Version 历史
Metrics
Checkpoint
```

## 5.2 DefinitionVersion

只回答：

```text
某次 Publish 固化了什么？
```

它是：

```text
immutable
independently addressable
stable reference target
```

不要：

```text
原地覆盖
原地 mutate
绑定当前 Flink JobId
```

## 5.3 SyncExecution

只回答：

```text
某个 Published Version 的这一次运行是什么？
```

它拥有：

```text
DefinitionVersionRef
ExecutionDefinitionSnapshot
RuntimeEnvironmentSnapshot
DesiredState
ObservedState
EngineExecutionRef
Execution metadata/evidence
```

不要让 Execution：

```text
read current Draft
follow latest Published automatically
own task edit state
```

---

# 6. SyncDefinition 是唯一配置事实

任何编辑渠道必须收敛到：

```text
SyncDefinition
```

允许：

```text
Wizard DTO -> mapper -> SyncDefinition
Yak YAML Document -> mapper -> SyncDefinition
REST DTO -> mapper -> SyncDefinition
DB JSON -> codec -> SyncDefinition
```

不允许：

```text
WizardSpec 作为第二领域模型
YamlSpec 作为第二领域模型
FlinkSpec 作为第二领域模型
```

## 6.1 为什么 serializer DTO 可以存在

例如 Yak YAML 需要：

```text
YamlDocument
YamlRoute
YamlRuntimeOptions
```

这是允许的。

但必须满足：

```text
DTO/document has serialization identity
Domain has business identity
```

也就是说 YAML Document 不能拥有独立的业务生命周期。

## 6.2 Pipeline YAML

只能是：

```text
Compiled Artifact
```

不能持久化成：

```text
Task.pipelineYaml
DefinitionVersion.pipelineYaml source of truth
```

可以保存 artifact digest / submission evidence，但不能让运行产物反向定义业务 Definition。

---

# 7. 场景建模规则

## 7.1 默认扩展点

新场景优先尝试：

```text
SourceSelector
SyncRoute
SinkTarget
SyncPolicy
ExecutionPolicy
```

## 7.2 高风险关键字

AI 准备新增以下概念时必须停止并说明为什么组合模型不足：

```text
syncType
sceneType
modeType
SINGLE_TABLE
MULTI_TABLE
WHOLE_DATABASE
SHARDING
KafkaRealtimeTask
MysqlRealtimeTask
WholeDatabaseSyncService
```

## 7.3 单表

正确：

```text
routes.size = 1
selector = ExactTableSelector
```

不要：

```text
syncType = SINGLE_TABLE
```

## 7.4 多表

正确：

```text
routes = N Exact Routes
```

## 7.5 Pattern

正确：

```text
TablePatternSelector
```

而不是：

```text
regexMode = true
```

如果可以通过类型表达，优先让类型本身表达语义。

## 7.6 整库

未来优先：

```text
DatabaseSelector
+
SameNameTarget / TemplateTarget
```

如果这个组合仍无法覆盖业务含义，才进入 Domain Gap 讨论。

---

# 8. Source / Sink 新能力的 AI 判断方式

需求：

```text
支持 Kafka Sink
```

错误第一反应：

```java
class KafkaSyncDefinition
class KafkaRealtimeTask
```

正确分析：

```text
Bounded Context:
Realtime Sync + DataSource + Infrastructure Adapter

Core Domain:
SinkEndpoint 仍然是 DataSourceRef
SyncDefinition 结构可能无需变化

Contextual capability:
该 datasource 是否支持 realtime sink

Infrastructure:
新增 Kafka adapter/compiler capability
```

如果 Kafka 需要不同的 `SinkTarget` 语义：

```text
TopicTarget
```

则扩展 `SinkTarget`。

不要因为 Connector 类型不同就产生 Task 子类。

---

# 9. ReplayKey 规则

v1 安全语义：

```text
每个 Route 必须有 ReplayKey
```

AI 必须区分：

```text
ReplayKey = Domain sync semantics
DB PRIMARY KEY = current Source Catalog fact
Wizard 自动读取 PK = UI convenience
```

当前实现要求配置 key 与数据库 PK 相同，这是当前 Contextual Preflight Policy。

未来如果支持：

```text
unique key
业务唯一键
Kafka message key
```

可以扩展 ReplayKey 的 contextual capability，不应把领域概念改回 `primaryKeyColumns`。

---

# 10. Runtime Environment 边界规则

Realtime Sync 对 Compute Environment 的关系是：

```text
reference + execution snapshot
```

不是：

```text
ownership
```

## 10.1 可以进入 DefinitionDraft / Version

```text
RuntimeEnvironmentRef
```

## 10.2 可以进入 Execution

```text
RuntimeEnvironmentSnapshot
```

## 10.3 不能进入 SyncDefinition

```text
restUrl
flinkHome
sshHost
sshUser
identityFile
flinkVersion
```

即使当前物理类 `ComputeEnvironment` 放在 realtime module/domain package，也不能把这个物理事实当成边界授权。

---

# 11. Flink / SSH 规则

Flink 和 SSH 是：

```text
Infrastructure
```

## 11.1 AI 可以做什么

- 改进 CLI 提交；
- 改进 REST status mapping；
- 新增 SSH submit capability；
- 修复临时 YAML 安全；
- 做 log redaction；
- runtime identity recovery；
- connector capability validation。

## 11.2 AI 不可以做什么

因为 Flink 支持某个字段，就直接加入 Core Domain。

例如 Flink option：

```text
scan.incremental.snapshot.chunk.size
```

不能直接变成：

```java
SyncDefinition.flinkChunkSize
```

先问：

```text
这是跨引擎稳定的 ExecutionPolicy 吗？
还是 Flink Connector Tuning？
```

如果只是 Flink 私有：

```text
AdapterTuning
```

---

# 12. ExecutionPolicy 规则

AI 新增 ExecutionPolicy 前必须回答：

```text
1. 用户表达的是稳定运行语义，还是某引擎参数？
2. 当前 Engine Adapter 能真正执行吗？
3. 其他引擎是否可以通过 capability 明确拒绝？
4. 有没有默默保存但不生效的风险？
```

允许 Domain 的方向：

```text
parallelism semantics
checkpoint semantics
restart semantics
sink batching/retry semantics
```

不自动允许：

```text
statementCacheSize
JDBC fetchSize
connector plugin path
flink rest retries
ssh connect timeout
```

## 12.1 Silent Ignore 禁止

如果 API / Domain 接受：

```text
CheckpointPolicy(interval=30s)
```

Adapter 必须：

```text
真正应用
或
preflight reject
```

禁止：

```text
保存成功
运行时完全忽略
```

阶段 4 GAP-11 就是该类问题。

---

# 13. StartupPolicy 规则

领域使用业务语义：

```text
INITIAL_AND_CONTINUOUS
CHANGES_ONLY
```

Flink Adapter 翻译：

```text
initial
latest-offset
```

不能把 `scan.startup.mode` 直接作为领域语言。

## 13.1 新增 SNAPSHOT_ONLY

如果用户说：

```text
增加仅全量，不持续同步
```

AI 必须先发现：

```text
当前 Flink FINISHED 被折叠成 TERMINATED
当前 continuous Execution 的 natural finish 被当异常
```

所以正确回答应该先指向：

```text
GAP-12 FINISHED normal completion
```

并设计：

```text
ObservedState / completion semantics
```

之后才能扩 `StartupPolicy`。

错误做法：

```text
只给 Wizard 增加 snapshot option
```

---

# 14. Version 规则

## 14.1 Publish

必须是：

```text
Draft
  ↓ full preflight
new immutable DefinitionVersion
  ↓ atomic
Task.publishedRef = new version
```

不是：

```text
mutable row.releaseState = PUBLISHED
```

## 14.2 Publish 幂等

完全相同的 Draft + Runtime Binding 重复 publish 默认不应该制造无意义版本风暴。

具体实现可以：

```text
return current published version
```

或其他显式领域策略，但不能因为 HTTP retry 随机产生多个相同版本。

## 14.3 VersionNo

同 Task：

```text
monotonic
never reused
```

删除历史版本后也不能回收编号。

## 14.4 Legacy Migration

AI 在迁移历史时必须区分：

```text
current definition_version = DraftRevision
published_version = old marker
```

旧 Published snapshot 缺失时：

```text
NO GUESSING
```

---

# 15. Execution 生命周期规则

## 15.1 每次 Start 新建 Execution

不能：

```text
FAILED -> STARTING in same Execution
STOPPED -> STARTING in same Execution
```

## 15.2 Active / Uncertain 唯一性

以下都阻止第二个 Execution：

```text
STARTING
RUNNING
STOPPING
UNKNOWN
CONFLICT
```

## 15.3 UNKNOWN

含义：

```text
外部结果目前无法确认
```

不是：

```text
执行失败，可以安全重试
```

AI 如果为了“恢复用户操作”把 UNKNOWN 自动变 FAILED 再启动，是高风险错误。

## 15.4 CONFLICT

含义：

```text
存在多个可能匹配的 runtime identity / external run
```

禁止自动选择。

## 15.5 Stop during Start

如果：

```text
desired = STOPPED
STARTING still waiting for external ID
```

不能：

```text
没有 JobId -> set STOPPED
```

必须保持：

```text
STOPPING / uncertain-safe state
```

等绑定到实际外部 Job 后取消。

现有实现已经具备这项安全能力，必须保护。

---

# 16. Restart / Apply Published Version 规则

需求：

```text
重启任务
```

AI 必须问语义：

```text
重启当前运行版本？
还是应用最新发布版本？
```

在本领域里默认是不同命令：

```text
RestartExecution
```

和：

```text
ApplyPublishedVersion
```

## 16.1 RestartExecution

必须 pin：

```text
oldExecution.definitionVersionRef
```

## 16.2 ApplyPublishedVersion

使用：

```text
Task.publishedRef
```

不能让一个按钮含糊地根据当前 Task 状态决定用哪个版本。

---

# 17. Validation 分层规则

AI 遇到“校验”需求不能只创建：

```java
RealtimeValidator.validateEverything(...)
```

至少概念上分：

## 17.1 Intrinsic

```text
无 I/O
纯 Domain
```

例：

- Source/Sink ref 非空；
- route 非空；
- selector 自身合法；
- replay key 非空/无重复；
- Policy VO 参数范围。

## 17.2 Contextual

依赖邻接上下文当前事实。

例：

- Source table 存在；
- Pattern 至少匹配一个物理表；
- ReplayKey 未漂移；
- route 不产生 ambiguity；
- DataSource role/capability；
- Runtime Environment enabled；
- engine supports requested policy。

## 17.3 Adapter Validation

例：

- Flink compiled artifact shape；
- Connector manifest；
- CLI readiness；
- REST health；
- SSH readiness。

## 17.4 Save / Publish / Start

推荐语义：

```text
Save Draft
  -> intrinsic required
  -> live contextual preflight 可作为产品策略

Publish
  -> intrinsic + contextual + compiler/adapter compatibility

Start
  -> 再次 contextual + runtime/engine preflight
```

Start 时发现 drift：

```text
reject start
```

不能：

```text
自动修改 immutable DefinitionVersion
```

---

# 18. Digest 规则

AI 如果修改 digest，必须先写清：

```text
这个 digest 表示什么事实？
```

## 18.1 DefinitionDigest

目标：

```text
semantic SyncDefinition + RuntimeEnvironmentRef
```

必须 canonicalize 无意义顺序。

不应包含：

```text
Task name
YAML comments
YAML whitespace
Wizard editor mode
Flink compiled YAML
```

## 18.2 ExecutionArtifactDigest

表示：

```text
这次准备/提交的具体运行产物
```

可以依赖 compiled YAML 等基础设施产物。

## 18.3 禁止混用

不能用 artifact digest 判断：

```text
“用户是否修改了 Definition”
```

也不能用 DefinitionDigest 证明：

```text
“实际提交的 YAML 字节完全一样”
```

---

# 19. Delete / Archive 规则

AI 看到：

```text
DELETE /realtime-sync/{id}
```

不能自动理解为：

```text
delete task + versions + executions + events
```

## 19.1 Hard Delete 条件

目标只适用于：

```text
never published
no execution
no external reference
no active/uncertain runtime
```

## 19.2 有历史的 Task

使用：

```text
Archive / Tombstone
```

历史 Version、Execution、Event 保留。

原因：

- 审计；
- 故障追溯；
- 血缘；
- Workflow 引用；
- 运行证据。

---

# 20. Query / View 不定义 Domain

当前前端可能需要：

```text
releaseState
desiredState
observedState
latestDeployment
```

这些可以继续作为兼容 API projection。

但是不要因为 API 有字段，就让 Core Domain 永久保留相同结构。

例如目标可以：

```text
Task + PublishedRef + ActiveExecution
       ↓ projection
legacy RealtimeViews.Job
```

View Model 是 Domain 的投影，不是 Domain 的上游约束。

---

# 21. Compute Environment 的 AI 规则

当前它物理上和 realtime 放在一起。

AI 必须记住：

```text
physical module co-location ≠ same bounded context
```

需求：

```text
支持 SSH ProxyJump
```

应归：

```text
Compute Environment / Infrastructure
```

而不是修改：

```text
SyncDefinition
SyncRoute
SyncExecution domain lifecycle
```

除非它改变了稳定的运行环境引用/快照语义。

---

# 22. Existing Safety Protection List

下面是后续重构的非功能不变量。

AI 改涉及这些区域前必须逐项说明是否保留。

## 22.1 Start Idempotency

保留：

```text
Idempotency-Key
DB unique constraint
same-key race recovery
owner validation
```

## 22.2 Reservation First

在外部 CLI/REST 提交前先有本地 Execution/Deployment reservation。

避免：

```text
external side-effect occurred
but no durable local identity
```

## 22.3 CAS / Lock

关键生命周期操作必须继续有数据库级并发保护。

不要只依赖：

```text
JVM ReentrantLock
```

因为多实例部署存在。

## 22.4 Re-check Before External Boundary

外部校验之后、真正状态变更/提交前仍要确认：

```text
version/digest/runtime binding unchanged
```

未来基准应切到 immutable DefinitionVersion identity/digest。

## 22.5 Stop During Start

必须保留。

## 22.6 Uncertain Submission

必须：

```text
UNKNOWN + recovery
```

而不是粗暴 retry。

## 22.7 Runtime Identity Recovery

必须保留确定性 runtime identity 和 multi-match conflict protection。

## 22.8 Runtime Snapshot

Execution 必须保存运行环境快照。

## 22.9 Credential Lifetime

密码只在提交边界读取，使用后 zeroize。

## 22.10 Log Redaction

必须保留。

## 22.11 Reconcile Lease

多实例全局 reconcile 继续使用租约/等效机制。

---

# 23. Stage 4 Migration Waves 是 AI 的施工边界

## Wave 0

```text
Core VO + compatibility mapper
```

不能顺手改数据库行为。

## Wave 1

```text
Immutable DefinitionVersion persistence
Publish dual-write
```

## Wave 2

```text
Start by DefinitionVersion
```

## Wave 3

```text
Deployment -> SyncExecution ownership
Desired/Observed migrate to execution
```

## Wave 4

```text
allow edit/publish while execution active
```

## Wave 5

```text
RestartExecution / ApplyPublishedVersion separation
```

## Wave 6

```text
legacy cleanup
package cleanup
naming cleanup
```

## 23.1 禁止跳 Wave

尤其：

```text
Wave 4 before Wave 3 = forbidden
```

因为当前 DAO 仍使用 Task-row runtime state 做 CAS。

## 23.2 一次 PR 范围

AI 默认应该把一个 PR 控制在一个 Wave 的单一闭环子目标。

不要：

```text
在同一个 PR 同时新增 Version 表
又迁 Execution 状态
又删旧字段
又重命名包
```

除非有非常强的必要性并明确说明事务/回滚方案。

---

# 24. 数据库迁移规则

所有核心 schema migration 使用：

```text
EXPAND
  ↓
DUAL WRITE / DUAL READ
  ↓
VERIFY
  ↓
SWITCH
  ↓
CONTRACT
```

## 24.1 禁止

```text
rename old table and update everything at once
DROP old columns in first migration
backfill with guessed historical data
```

## 24.2 Published backfill

恢复来源优先级：

```text
1. current row is still published and clean
2. matching execution/deployment snapshot
3. no reliable source -> explicit missing
```

不能：

```text
current draft -> pretend old published
```

## 24.3 Execution backfill

允许从 Deployment 历史恢复存在的事实。

不能编造不存在的历史 DesiredState。

---

# 25. Domain Gap 流程

Domain Gap 不是异常，它是模型演进正常入口。

AI 发现 Gap 后应该输出：

```text
Domain Gap

Requirement:
Why current model cannot express it:
Candidate domain concept:
Aggregate ownership:
Entity or Value Object:
New/changed invariants:
Lifecycle impact:
Compatibility impact:
Migration impact:
Alternative that avoids model expansion:
```

然后等待领域决策。

## 25.1 什么情况一定是 Gap

例如：

- Route 需要独立启停和生命周期；
- 同一个 Task 要允许多个并行 Execution；
- DefinitionVersion 要支持分支发布；
- 新同步语义无法由 Selector/Route/Policy 表达；
- snapshot-only 引入 normal-completion lifecycle；
- ReplayKey 不再足以表达新交付语义。

这些不能靠“加个 boolean”绕过。

---

# 26. 典型需求演练

## 26.1 支持 PostgreSQL Source

分析：

```text
Aggregate: SyncDefinition value changes? structure no
Area: SourceEndpoint capability
Layer: Contextual capability + Infrastructure Adapter
Domain Gap: usually no
```

不要：

```text
PostgresRealtimeTask
PostgresSyncDefinition
```

需要：

- DataSource capability；
- source adapter/compiler；
- catalog/preflight；
- connector capability。

## 26.2 支持 Kafka Sink

分析：

```text
SinkEndpoint capability
possibly SinkTarget extension (TopicTarget)
Infrastructure adapter
```

如果现有 `TableTarget` 无法表达 Kafka topic：

```text
Domain Gap: SinkTarget extension
```

这不是新 Task Type。

## 26.3 支持整库同步

优先：

```text
DatabaseSelector
+
SameNameTarget/TemplateTarget
```

再看是否需要新的 route compatibility invariant。

## 26.4 支持字段映射

归属优先考虑：

```text
SyncRoute
```

可能增加：

```text
FieldMapping
```

作为 Route VO 的组成部分。

不要直接放：

```text
Task.fieldMappings
```

## 26.5 支持字段过滤 / Transform

先问：

```text
它是 Route semantics 还是独立 processing domain？
```

简单字段选择可能属于 Route。

复杂 SQL/UDF transform 可能意味着新的 bounded context / processing stage，需要 Domain Gap，不要把 realtime sync 变成通用 ETL 引擎。

## 26.6 运行中修改配置立即生效

这是高风险需求。

当前领域规则：

```text
running Execution pinned to immutable Version
```

所以“立即生效”不能通过修改 Execution snapshot 实现。

需要：

```text
Publish new Version
+
ApplyPublishedVersion / controlled redeploy
```

如果要求 truly hot-reconfigure same Execution：

```text
Domain Gap
```

## 26.7 自动重启失败任务

先判断：

```text
Engine-level RestartPolicy
vs
Control-plane new SyncExecution retry
```

这两者不同。

不要一个 `autoRetry=true` 同时控制两层。

## 26.8 保存 YAML 注释

归属：

```text
Editor document model
```

不是 `SyncDefinition`。

可以保存独立 source document，但必须明确：

```text
source document != domain definition truth
```

## 26.9 增加 Flink Savepoint 恢复

分析：

```text
Execution lifecycle + Infrastructure
```

如果 Savepoint 是某次 Execution 恢复的稳定语义，需要设计：

```text
ExecutionResumePolicy / RecoveryPointRef
```

不要直接加：

```text
SyncDefinition.savepointPath
```

除非领域决策明确它属于 Definition 的长期运行策略。

## 26.10 支持多集群容灾

当前：

```text
DefinitionVersion RuntimeEnvironmentRef
Execution RuntimeEnvironmentSnapshot
```

自动 fallback 到另一环境可能改变“Published Binding”的语义。

这是 Domain/Application 级重要变化，不能只在 `RealtimeRuntimeResolver` 里偷偷 fallback。

需要 Domain Gap / explicit Failover Policy 讨论。

---

# 27. AI 禁止模式清单

以下代码形态默认触发 Review 阻止。

## 27.1 第二 Spec

```java
class WizardRealtimeSpec {}
class YamlRealtimeSpec {}
class FlinkRealtimeSpec {}
```

如果只是 DTO，名字/包必须明确 adapter/document 意图，且有 mapper 到 canonical Definition。

## 27.2 Scene Enum 爆炸

```java
enum SyncType {
  SINGLE,
  MULTI,
  DATABASE,
  SHARDING,
  SINGLE_INCREMENTAL,
  DATABASE_INCREMENTAL
}
```

## 27.3 技术字段污染 Domain

```java
record SyncDefinition(
    String flinkHome,
    String jdbcUrl,
    String sshHost,
    String pipelineYaml)
```

## 27.4 Execution 读 Draft

```java
execution.setSpec(task.getCurrentDraft().definition());
```

正确是来自明确 Published Version。

## 27.5 修改 Version

```java
version.setDefinition(newDefinition);
```

禁止。

## 27.6 UNKNOWN 自动 retry

```java
if (UNKNOWN) {
  markFailed();
  startAgain();
}
```

禁止。

## 27.7 Restart 偷升级

```java
restart() {
  start(task.getPublishedVersion());
}
```

如果当前 Execution 版本和 publishedRef 不同，这是版本升级，不是 restart。

## 27.8 Service-only rule change

```text
删除 requireDefinitionMutable()
```

但不改 DAO CAS，属于不完整实现。

任何 lifecycle rule change 必须同时检查：

```text
Domain rule
Application command
Repository atomic condition
DAO SQL
DB constraint/index
read projection
```

## 27.9 Big Bang DDD 重写

```text
rename all tables
move all packages
replace all services
rewrite Flink gateway
```

阶段 4 已明确禁止。

---

# 28. AI 推荐模式

## 28.1 Compatibility Mapper

在迁移期推荐：

```text
Legacy CdcPipelineSpec
       ↕
SyncDefinitionMapper
       ↕
New Core Domain
```

让 REST/YAML/DB v1 可以逐步迁移。

## 28.2 Anti-Corruption Adapter

DataSource / Compute Environment / Flink 通过 Port/Adapter 接入。

## 28.3 Read Projection

UI 兼容字段通过 Query Projection 派生。

## 28.4 Strong Value Objects

避免字符串语义过载：

```text
StartupPolicy
SourceSelector variants
RestartPolicy variants
DefinitionDigest
DefinitionVersionRef
EngineExecutionRef
```

## 28.5 Explicit Use Cases

推荐命名：

```text
SaveRealtimeSyncDraft
PublishRealtimeSyncDefinition
StartRealtimeSyncExecution
StopRealtimeSyncExecution
RestartRealtimeSyncExecution
ApplyRealtimeSyncPublishedVersion
```

不要求一定一个类一个 Use Case，但语义必须显式。

---

# 29. PR Review Checklist

每个影响 realtime domain 的 PR 应回答。

## Domain

- [ ] 需求属于哪个 bounded context？
- [ ] 修改哪个 Aggregate？
- [ ] 是否改变 SyncDefinition？修改哪个子模型？
- [ ] 是否改变领域不变量？
- [ ] 是否改变生命周期/状态转换？
- [ ] 是否新增 Domain Gap？

## Truth Model

- [ ] 是否仍只有一个 SyncDefinition 事实模型？
- [ ] 是否新增了 Wizard/YAML/Flink 第二 Spec？
- [ ] 是否误把 View/DTO 当 Domain？

## Version / Execution

- [ ] Execution 是否明确绑定 immutable DefinitionVersion？
- [ ] Draft 修改是否不会影响历史 Version/Execution？
- [ ] Restart 是否 pin 原 Version？
- [ ] 是否可能产生第二 Active/UNKNOWN/CONFLICT Execution？

## Boundary

- [ ] Flink/SSH/JDBC/DataSource Credential 是否留在 Adapter/Adjacent Context？
- [ ] Runtime Environment 是否仍使用 Ref/Snapshot？
- [ ] Adapter-private tuning 是否没有进入 Core Definition？

## Validation

- [ ] Intrinsic / Contextual / Adapter Validation 是否被正确区分？
- [ ] 外部临时故障是否没有污染历史 Domain truth？

## Persistence

- [ ] 是否遵循 expand/dual/verify/switch/contract？
- [ ] 是否有历史数据 guessing？
- [ ] 是否需要更新 DAO CAS / DB constraint？
- [ ] 是否保留 audit/history？

## Safety

- [ ] Idempotency 是否保留？
- [ ] multi-instance CAS/lock 是否保留？
- [ ] stop-during-start 是否保留？
- [ ] UNKNOWN/CONFLICT 是否保留？
- [ ] runtime identity recovery 是否保留？
- [ ] credentials zeroization / log redaction 是否保留？

## Tests

- [ ] 新/变更 invariant 是否有测试？
- [ ] version snapshot/digest 是否有测试？
- [ ] lifecycle transition 是否有测试？
- [ ] concurrency/idempotency 是否有测试？
- [ ] legacy compatibility 是否有测试？

---

# 30. AI 实现前输出模板

以后可以直接把这一段作为 AI Prompt Contract：

```text
Before modifying realtime sync code, read:
- yak-ops-business/.../yak-ops-business-sync-realtime/DOMAIN.md
- docs/realtime-sync/domain/01-domain-boundary-and-language.md
- docs/realtime-sync/domain/02-core-domain-model.md
- docs/realtime-sync/domain/03-invariants-and-lifecycle.md
- docs/realtime-sync/domain/04-current-code-mapping.md

Then output:

Domain Impact Analysis
- Requirement:
- Bounded Context:
- Aggregate(s):
- SyncDefinition Area:
- Invariant/Lifecycle Impact:
- Layer:
- Stage-4 Mapping/Gap:
- Migration Wave:
- Safety Protection List:
- Domain Gap: yes/no

Rules:
- If Domain Gap=yes, stop coding and propose model changes first.
- Do not introduce a second definition source of truth.
- Do not introduce scene/sync types before proving composition cannot express the requirement.
- Do not move Flink/YAML/SSH/JDBC credentials into Core Domain.
- Do not let Execution read mutable Draft.
- Do not mutate DefinitionVersion.
- Preserve idempotency, uncertainty recovery, stop-during-start, runtime snapshots, credential zeroization and log redaction.
```

---

# 31. AI 实现后输出模板

AI 完成修改后必须报告：

```text
Domain Compliance Report
- Domain rule implemented:
- Aggregate(s) changed:
- Invariant/lifecycle changes:
- Legacy compatibility kept:
- Safety properties preserved:
- DB migration mode:
- Tests added/updated:
- Known Domain/Implementation gaps remaining:
```

如果无法给出这份报告，说明改动可能只是“功能完成”，而没有完成领域一致性检查。

---

# 32. 对当前 Stage-6 重构的特殊 AI 约束

后续进入阶段 6 时，本文件优先约束以下行为。

## 32.1 Wave 0

AI 不得：

```text
顺手修改 publish/start behavior
```

目标只建立新 Core VO 和 compatibility mapping。

## 32.2 Wave 1

必须保证：

```text
DefinitionVersion insert
+
Task publishedRef move
```

事务一致。

同时保留 legacy projection/dual-write，直到后续切换。

## 32.3 Wave 2

必须证明：

```text
Published v3 + Draft v4
Start -> v3
```

并且原 start safety tests 全部保留。

## 32.4 Wave 3

是高风险阶段。

必须双读/双写验证 Task projection 和 Execution state 一致后，再迁 ownership。

## 32.5 Wave 4

只能在 Wave 3 完成后删除：

```text
STOPPED/FAILED only edit/publish guards
```

同时移除/改造 DAO 层同样条件。

## 32.6 Wave 5

UI/API 也要明确区分：

```text
Restart
Apply Published Version
```

## 32.7 Wave 6

只在行为/数据已经切换完成后做：

```text
package move
legacy rename
old field removal
```

不要把 clean-up 变成第一目标。

---

# 33. 阶段 5 不做什么

本阶段只建立规则，不实现自动化阻断。

不新增：

- ArchUnit rules；
- CI domain lint；
- PR template enforcement；
- static scanner；
- forbidden import test；
- automatic dependency rules。

这些属于阶段 7。

阶段 5 的职责是先明确：

```text
自动护栏到底应该检查什么
```

之后阶段 7 才把本文件规则转换成机器可执行检查。

---

# 34. 阶段 5 验收标准

阶段 5 完成后，把以下需求直接交给新的 AI 会话：

```text
支持整库同步
支持 Kafka Sink
支持 snapshot-only
运行中修改任务
重启任务
增加 SSH ProxyJump
删除已运行任务
增加 Flink 私有调优参数
```

AI 应该先做领域定位，而不是直接写代码。

至少应该能够判断：

```text
整库 -> Selector/Target composition first
Kafka -> Endpoint/Target + Adapter, not new Task type
snapshot-only -> lifecycle Domain Gap before UI
运行中修改 -> Draft/Version/Execution separation
restart -> pin old DefinitionVersion
SSH ProxyJump -> Compute Environment / Infrastructure
删除历史 -> Archive/Tombstone, preserve evidence
Flink private tuning -> AdapterTuning, not Core Definition
```

如果 AI 仍然第一步创建：

```text
syncType
new *Spec
new engine-specific Task
pipelineYaml domain field
```

则阶段 5 规则仍不足，需要继续加强。

---

# 35. 下一阶段

阶段 6 将正式开始最小领域重构。

执行顺序仍以阶段 4 Mapping 为准：

```text
Wave 0
Core VO + legacy mapper

Wave 1
Immutable DefinitionVersion persistence

Wave 2
Start by DefinitionVersion

Wave 3
SyncExecution lifecycle ownership

Wave 4
Run-time edit/publish decoupling

Wave 5
Restart vs ApplyPublishedVersion

Wave 6
Legacy cleanup
```

阶段 5 的 `DOMAIN.md` 与本文件会成为阶段 6 每一个 PR 的设计输入和 Review 基线。
