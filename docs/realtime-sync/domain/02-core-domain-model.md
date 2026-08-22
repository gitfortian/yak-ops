# Realtime Sync 核心领域模型 v1

> 状态：Proposed（阶段 2；本 PR 合并后视为 Accepted）  
> 前置：[阶段 1：领域边界与统一语言](./01-domain-boundary-and-language.md)  
> 范围：确定 Realtime Sync Domain 的聚合边界、Entity / Value Object 分类及核心对象关系；本阶段不修改 Java 生产代码、数据库结构和执行链路。

## 1. 阶段 2 要解决什么

阶段 1 已经固定了实时同步的领域边界和统一语言，但刻意留下了几个关键问题：

- `RealtimeSyncTask` 是否是聚合根；
- `SyncDefinition` 是 Entity 还是 Value Object；
- `DefinitionVersion` 是否只是 Task 的一个整数版本号；
- `SyncExecution` 是否应该独立于 Task；
- `SourceSelector / SinkTarget / ReplayKey` 的具体结构；
- `SyncPolicy / ExecutionPolicy` 的字段边界；
- 哪些当前 `CdcPipelineSpec` 字段是真正的 Domain，哪些只是 Flink/JDBC 实现参数。

阶段 2 对这些问题给出 **Core Domain Model v1** 的正式答案。

本阶段的目标不是为了类名好看而立即重构，而是建立后续需求、代码和 AI 都必须遵守的模型坐标系。

---

## 2. 核心结论

Realtime Sync v1 采用 **三个聚合根**：

```text
RealtimeSyncTask

DefinitionVersion

SyncExecution
```

核心关系：

```text
                  ┌──────────────────────┐
                  │  RealtimeSyncTask    │
                  │  Aggregate Root      │
                  │                      │
                  │  stable identity     │
                  │  current draft       │
                  │  published ref       │
                  └──────────┬───────────┘
                             │ publish
                             │ produces
                             ▼
                  ┌──────────────────────┐
                  │ DefinitionVersion    │
                  │ Aggregate Root       │
                  │ immutable            │
                  │                      │
                  │ SyncDefinition       │
                  │ RuntimeEnvironmentRef│
                  │ digest/version       │
                  └──────────┬───────────┘
                             │ start
                             │ creates
                             ▼
                  ┌──────────────────────┐
                  │   SyncExecution      │
                  │   Aggregate Root     │
                  │                      │
                  │ definition snapshot  │
                  │ runtime snapshot     │
                  │ desired/observed     │
                  │ engine execution ref │
                  └──────────────────────┘
```

`SyncDefinition` **不是聚合根，也不是 Entity**。

它是一个不可变 Value Object：

```text
SyncDefinition
├── SourceEndpoint
├── SinkEndpoint
├── SyncRoute[]
├── SyncPolicy
└── ExecutionPolicy
```

因此领域核心不是：

```text
一个巨大的 RealtimeJob
  + definition
  + yaml
  + flinkJobId
  + ssh
  + runtime
  + metrics
```

而是明确分离：

```text
Task      = 长期身份与编辑入口
Version   = 不可变发布事实
Execution = 一次实际运行
Definition= 可比较、可快照的同步语义
```

---

## 3. 为什么是三个聚合根

### 3.1 RealtimeSyncTask：任务聚合根

`RealtimeSyncTask` 是用户长期管理的实时同步任务身份。

它负责：

- 任务稳定身份；
- 名称、描述等业务元数据；
- 当前 Draft；
- 当前 Published Definition 引用；
- 与发布相关的任务级并发控制入口。

概念结构：

```text
RealtimeSyncTask
├── TaskId
├── TaskProfile
├── DefinitionDraft?
└── PublishedDefinitionRef?
```

这里的 `?` 表示新建基础任务时可以暂时没有完整 Definition，也可以尚未发布。

`RealtimeSyncTask` **不拥有**：

- 所有历史 DefinitionVersion 的内存集合；
- 所有历史 SyncExecution 的内存集合；
- Flink JobId；
- Pipeline YAML；
- SSH / REST / CLI；
- Runtime Environment 的完整配置。

#### 为什么 Task 不直接持有历史版本列表

历史版本会持续增长：

```text
v1
v2
v3
...
v1000
```

如果把所有历史版本作为 Task 聚合内部 Entity 集合，会导致：

- 聚合无限增长；
- 每次编辑 Task 都需要承担历史对象加载成本；
- Execution / Workflow / Task Catalog 很难稳定引用某一个内部 Entity；
- 聚合锁粒度过大。

因此 Task 只保留当前发布版本引用：

```text
PublishedDefinitionRef
```

历史版本由独立的 `DefinitionVersion` 聚合管理。

---

### 3.2 DefinitionVersion：不可变发布版本聚合根

`DefinitionVersion` 是一次 publish 后形成的不可变领域事实。

它不是简单的：

```text
int version = 3
```

而是一个拥有独立身份、可以被稳定引用的不可变 Aggregate Root。

概念结构：

```text
DefinitionVersion
├── DefinitionVersionId
├── TaskId
├── VersionNo
├── SyncDefinition
├── RuntimeEnvironmentRef
├── DefinitionDigest
└── PublicationMetadata
```

其中：

- `DefinitionVersionId`：版本稳定身份；
- `TaskId`：该版本属于哪条实时同步任务；
- `VersionNo`：对同一 Task 单调递增的业务版本号；
- `SyncDefinition`：该版本真正的同步定义；
- `RuntimeEnvironmentRef`：发布时绑定的运行环境引用；
- `DefinitionDigest`：Definition + Runtime Binding 的规范化摘要；
- `PublicationMetadata`：发布时间、发布人等审计信息。

#### 为什么 DefinitionVersion 是独立聚合根

它需要被以下对象独立引用：

```text
SyncExecution
Workflow
Task Catalog
未来 Rollback / Compare
审计
```

如果它只是 Task 内部不可寻址的子对象，会使这些跨上下文引用重新依赖 Task 的“当前状态”。

核心规则：

> **Published Definition 必须可以脱离当前 Draft 被独立、稳定、不可变地引用。**

#### DefinitionVersion 不包含什么

不包含：

```text
Flink JobId
Pipeline YAML
RuntimeEnvironmentSnapshot
Metrics
Checkpoint
Submission Log
```

这些都属于执行阶段或 Infrastructure。

---

### 3.3 SyncExecution：运行聚合根

`SyncExecution` 表示某个已发布 DefinitionVersion 的一次实际运行。

概念结构：

```text
SyncExecution
├── ExecutionId
├── TaskId
├── DefinitionVersionRef
├── ExecutionDefinitionSnapshot
├── RuntimeEnvironmentSnapshot
├── DesiredState
├── ObservedState
├── EngineExecutionRef?
└── ExecutionMetadata
```

它拥有独立聚合的原因：

- 运行生命周期与编辑生命周期完全不同；
- 一个 Task 可以有多次历史运行；
- Execution 有自己的并发、幂等、停止和对账问题；
- Task 编辑不能要求锁住一个正在运行的 Execution；
- Execution 可以继续保留，即使 Task 后续已经产生新 Draft / 新 Version。

核心规则：

```text
Task edit
    ≠
Execution mutation
```

以及：

```text
SyncExecution
    never reads current Task draft
```

运行只能来自明确的 Published Definition。

---

## 4. 为什么 SyncDefinition 是 Value Object

### 4.1 定义

`SyncDefinition` 回答：

> 这一版实时同步到底如何从 Source 将哪些数据同步到 Sink，并遵循什么同步和执行策略？

模型：

```text
SyncDefinition
├── source: SourceEndpoint
├── sink: SinkEndpoint
├── routes: Collection<SyncRoute>
├── syncPolicy: SyncPolicy
└── executionPolicy: ExecutionPolicy
```

### 4.2 它没有独立身份

不存在业务需求：

```text
给 SyncDefinition 自己分配一个长期 ID，
然后脱离 Task / Version 单独维护它。
```

真正需要身份的是：

```text
RealtimeSyncTask
DefinitionVersion
SyncExecution
```

因此 `SyncDefinition` 按内容比较，是不可变 Value Object。

### 4.3 修改方式是整体替换

正确语义：

```text
old SyncDefinition
       ↓ edit
new SyncDefinition
```

不是：

```text
mutable definition entity
  setSource(...)
  setRoute(...)
  setFlinkYaml(...)
```

实现层未来可以通过 Builder/Form/DTO 逐步编辑，但进入 Domain 后应形成一个完整的新 Value Object。

### 4.4 DefinitionVersion 才赋予版本身份

同一份内容可以在不同 Task 或不同发布时间出现。

`SyncDefinition` 本身没有：

```text
versionNo
publishedAt
publishedBy
```

这些属于 `DefinitionVersion`。

---

## 5. RealtimeSyncTask 内部 Value Object

### 5.1 TaskId

任务稳定身份。

领域上避免直接把所有 `long` 都当成可以互换的 ID。

概念：

```text
TaskId(value)
```

### 5.2 TaskProfile

任务的业务展示元数据：

```text
TaskProfile
├── name
└── description
```

`TaskProfile` 不进入 `SyncDefinition`。

原因：

- 改任务名称不代表 Source/Sink/Route 语义发生变化；
- 下游执行引擎如何命名 Job 属于 Adapter Concern；
- Definition Digest 不应天然依赖 UI 展示名称。

### 5.3 DefinitionDraft

Task 当前可编辑工作副本：

```text
DefinitionDraft
├── SyncDefinition
├── RuntimeEnvironmentRef
└── DraftRevision
```

`DefinitionDraft` 是 Task 聚合内部 Value Object，不是独立 Aggregate Root。

保存草稿的语义是：

```text
replace current DefinitionDraft
```

而不是创建一个可以被生产运行引用的 Published Version。

`DraftRevision` 用于乐观并发和“当前草稿是否被别人修改”的识别，具体并发规则阶段 3 再确定。

### 5.4 PublishedDefinitionRef

Task 只引用当前已发布版本：

```text
PublishedDefinitionRef
├── DefinitionVersionId
└── VersionNo
```

它不是 Definition 内容的副本。

---

## 6. SourceEndpoint / SinkEndpoint

### 6.1 SourceEndpoint

v1：

```text
SourceEndpoint
└── DataSourceRef
```

职责：表达“同步数据来自平台里的哪个逻辑数据源”。

禁止进入：

```text
host
port
username
password
jdbcUrl
driver
Flink connector type
```

这些由 DataSource Context / Adapter 在校验和执行边界解析。

### 6.2 SinkEndpoint

v1：

```text
SinkEndpoint
└── DataSourceRef
```

同样不复制数据源连接模型。

### 6.3 为什么 Endpoint 不直接保存 dbType

`MYSQL / POSTGRESQL / KAFKA ...` 的真实能力来自被引用的数据源资产。

领域可以通过 Port 查询“这个 Endpoint 是否支持 Source / Sink 某种能力”，但不应该为了缓存方便复制一份可能漂移的 DataSource Definition。

因此：

```text
Endpoint = identity/reference
Capability = resolve at boundary
Connection = adjacent context
```

---

## 7. SyncRoute：Definition 中最核心的组合单元

### 7.1 v1 结构

```text
SyncRoute
├── SourceSelector
├── SinkTarget
└── ReplayKey
```

`SyncRoute` v1 是 Value Object。

它没有独立生命周期，也没有必要为了数据库方便强行增加 `routeId`。

如果未来出现：

- Route 单独审批；
- Route 单独启停；
- Route 单独版本历史；
- Route 被外部上下文长期稳定引用且不能通过 Version + Route Content 定位；

应先标记 Domain Gap，再讨论是否升级为 Entity。

### 7.2 SourceSelector

v1 使用类型化选择器，而不是：

```text
sourceTable: String
matchMode: enum
```

概念模型：

```text
SourceSelector
├── ExactTableSelector
│     └── tableName
│
└── TablePatternSelector
      └── pattern
```

伪代码：

```java
sealed interface SourceSelector {
    record ExactTableSelector(TableName table) implements SourceSelector {}
    record TablePatternSelector(TablePattern pattern) implements SourceSelector {}
}
```

这样不会出现：

```text
matchMode = EXACT
但 sourceTable 却装着 pattern
```

类型本身表达语义。

#### v1 支持

- EXACT table；
- table pattern / regex。

#### v1 不预先加入

```text
DatabaseSelector
SchemaSelector
ShardingSelector
```

但 `SourceSelector` 是明确扩展点。

未来整库同步优先考虑：

```text
SourceSelector += DatabaseSelector
```

而不是：

```text
RealtimeSyncTask.syncType = WHOLE_DATABASE
```

### 7.3 SinkTarget

v1：

```text
SinkTarget
└── TableTarget
      └── targetObjectName
```

`targetObjectName` 是 Sink 侧的逻辑目标对象标识，例如：

```text
orders
public.ods_orders
```

Domain 不负责解析 PostgreSQL quoting、JDBC identifier escaping 等数据库方言细节。

这些属于 Sink Adapter。

未来整库/分表可能需要：

```text
SameNameTarget
TemplateTarget
DynamicTargetMapping
```

阶段 2 不提前设计这些类型。

### 7.4 ReplayKey

`ReplayKey` 表示至少一次交付语义下，用于安全重放/幂等写入的数据键：

```text
ReplayKey
└── fields: non-empty field collection
```

重要：

> **ReplayKey 是业务/同步语义，不等于“UI 自动读取数据库 PRIMARY KEY 这个动作”。**

一期 Wizard 通过 Source Catalog 自动读取 PK，只是帮助用户构造 ReplayKey 的一种方式。

未来如果某种 Source/Sink 支持业务唯一键作为 ReplayKey，领域模型不需要改名。

---

## 8. Route Collection 的语义

`SyncDefinition.routes` 是一个 Route 集合。

### 8.1 单表 / 多表是派生场景

```text
1 个 ExactTable Route
    => UI 可称“单表”

N 个 ExactTable Route
    => UI 可称“多表”
```

Domain 不增加：

```text
SyncType.SINGLE_TABLE
SyncType.MULTI_TABLE
```

### 8.2 Route 顺序没有业务语义

如果两份 Definition 只有 Route 排列顺序不同：

```text
[A, B, C]
[C, A, B]
```

业务含义应该相同。

因此未来 canonical digest / equality / diff 应避免把纯顺序变化误判成同步语义变化。

具体规范化算法留到阶段 3 / 4。

### 8.3 Pattern Route

一个 Pattern Selector 可以匹配多个 Source Object。

v1 仍允许绑定一个 `TableTarget`，保持和当前能力一致。

如果未来需要“每个匹配表自动映射为同名 Sink”或“按模板生成目标表”，应扩展 `SinkTarget`，而不是创建新的 Task Type。

---

## 9. SyncPolicy：只表达同步语义

v1：

```text
SyncPolicy
├── StartupPolicy
└── SchemaEvolutionPolicy
```

### 9.1 StartupPolicy

领域名称不能直接使用 Flink 字段值作为核心语言。

v1：

```text
INITIAL_AND_CONTINUOUS
CHANGES_ONLY
```

含义：

#### INITIAL_AND_CONTINUOUS

```text
先处理当前已有数据
然后持续消费新变化
```

当前 Flink Adapter 可以翻译为：

```text
scan.startup.mode = initial
```

但 `initial` 不是领域枚举名。

#### CHANGES_ONLY

```text
不处理启动前历史数据
只处理启动后的新变化
```

当前 Flink Adapter 可以翻译为：

```text
scan.startup.mode = latest-offset
```

### 9.2 SNAPSHOT_ONLY 暂不进入 v1

Flink CDC 可以支持 snapshot，但当前 Yak 生命周期尚未正确区分正常 `FINISHED` 与异常终止。

因此：

```text
SNAPSHOT_ONLY
```

不是 v1 Domain Allowed Value。

未来完成生命周期语义后，再作为领域能力扩展讨论。

### 9.3 SchemaEvolutionPolicy

v1：

```text
EVOLVE
IGNORE
FAIL
```

这些是引擎中立业务语义。

Flink Adapter 再翻译成具体 connector / pipeline option。

### 9.4 Replay Safety 不建 boolean 开关

当前实现存在：

```text
strictReplaySafety = true
```

阶段 2 明确：

> **v1 Strict Replay Safety 是领域不变量，不是用户可选 Policy。**

因此 Core Domain Model 不设计：

```text
boolean strictReplaySafety
```

而通过：

```text
SyncRoute 必须有 ReplayKey
+
阶段 3 Replay Safety invariants
```

表达。

当前 boolean 属于现有实现兼容字段，阶段 4 再决定如何迁移。

---

## 10. ExecutionPolicy：表达运行偏好，不表达运行环境

v1 概念：

```text
ExecutionPolicy
├── Parallelism
├── CheckpointPolicy
├── RestartPolicy
└── SinkWritePolicy
```

### 10.1 Parallelism

表示用户希望该同步定义以怎样的逻辑并行度运行。

Domain 不关心 Flink Pipeline YAML 对应字段名称。

### 10.2 CheckpointPolicy

概念：

```text
CheckpointPolicy
└── interval
```

Checkpoint 是运行可靠性语义，可以属于 ExecutionPolicy。

但当前 Flink CDC MVP 并没有把 `checkpointIntervalMs` 编译进 Pipeline YAML；阶段 2 将其记录为 **Implementation Gap**：

> 如果一个 ExecutionPolicy 被 Domain 接受，具体 Adapter 必须明确“支持并执行”或“拒绝该能力”，不能长期静默保存但不生效。

具体处理在阶段 4 / 6 决定。

### 10.3 RestartPolicy

v1 Core Domain 只接受语义完整的策略类型：

```text
NoRestart
FixedDelayRestart
```

例如：

```text
FixedDelayRestart
├── maxAttempts
└── delay
```

当前 `CdcPipelineSpec` 允许字符串：

```text
failure-rate
```

但只带 `attempts + delayMs`，缺少完整 Failure Rate Window 语义。

因此阶段 2 **不把当前这个不完整字符串直接固化成 Core Domain Policy**。

如果未来需要 Failure Rate Restart，应先定义完整 Value Object，再进入 Domain。

### 10.4 SinkWritePolicy

v1 可以承载引擎相对中立的写入偏好：

```text
SinkWritePolicy
├── maxRetries
├── batchSize
├── flushInterval
└── maxBatchBytes
```

### 10.5 statementCacheSize 不进入 Core Domain

当前 `SinkTuning.statementCacheSize` 明显与 JDBC Sink 实现相关。

阶段 2 将其归类为：

```text
AdapterTuning / Infrastructure Configuration
```

而不是 `ExecutionPolicy` 的稳定核心字段。

如果未来 MySQL / PostgreSQL / Kafka / StarRocks Sink 各自有不同私有调优参数，应由 Adapter Capability / Adapter Tuning 承载，不能持续向 `SyncDefinition` 增加 Connector 私有字段。

---

## 11. Runtime Environment 为什么不属于 SyncDefinition

`SyncDefinition` 回答：

```text
怎么同步？
```

`RuntimeEnvironmentRef` 回答：

```text
这版定义准备在哪里运行？
```

两者生命周期不同。

因此 v1 结构是：

```text
DefinitionDraft
├── SyncDefinition
└── RuntimeEnvironmentRef
```

publish 后：

```text
DefinitionVersion
├── SyncDefinition
└── RuntimeEnvironmentRef
```

start 后：

```text
SyncExecution
├── DefinitionVersionRef
├── ExecutionDefinitionSnapshot
└── RuntimeEnvironmentSnapshot
```

### 11.1 Draft / Version 保存 Ref

Realtime Sync 不拥有 Runtime Environment 本身。

因此 Draft / DefinitionVersion 只保存：

```text
RuntimeEnvironmentRef
```

### 11.2 Execution 保存 Snapshot

真正 start 时，从 Compute Environment Context 获取当前可执行环境，并固化：

```text
RuntimeEnvironmentSnapshot
```

这样后续：

- 环境名称变化；
- SSH 配置变化；
- Flink 版本升级；
- REST URL 调整；

都不会改变历史 Execution “当时实际使用的环境事实”。

---

## 12. ExecutionDefinitionSnapshot：快照不是第二事实模型

`SyncExecution` v1 建议保留：

```text
ExecutionDefinitionSnapshot
```

包含：

```text
DefinitionVersionRef
SyncDefinition
DefinitionDigest
```

它来自已经发布的 `DefinitionVersion`。

### 为什么 Execution 仍保存一份 Definition Snapshot

用于：

- 运行审计；
- 故障恢复；
- 历史查询；
- 避免运行记录依赖外部版本表才能解释；
- 检测版本数据异常。

这不违反“唯一 Definition 模型”规则，因为：

```text
DefinitionVersion.definition
```

是发布事实来源；

```text
ExecutionDefinitionSnapshot
```

只是从发布事实复制出的 **不可变运行证据**。

禁止：

```text
修改 Execution Snapshot
然后反向更新 DefinitionVersion
```

如果两者 digest 不一致，应视为数据完整性问题，而不是允许双向同步。

---

## 13. EngineExecutionRef

Domain 不保存裸 `flinkJobId` 作为唯一模型。

v1 使用：

```text
EngineExecutionRef
├── engine
└── externalExecutionId
```

例如 Flink Adapter：

```text
engine = FLINK_CDC
externalExecutionId = <32-char Flink JobId>
```

未来如果增加其他引擎：

```text
Debezium
SeaTunnel
other CDC engine
```

`SyncExecution` 不需要更换聚合结构。

`EngineExecutionRef` 在 STARTING 初期可以为空，外部提交成功并得到 ID 后再绑定。

具体绑定状态和并发规则属于阶段 3。

---

## 14. Entity / Aggregate / Value Object 分类表

### Aggregate Root / Entity

| 类型 | 分类 | 原因 |
|---|---|---|
| `RealtimeSyncTask` | Aggregate Root + Entity | 长期稳定身份，管理当前 Draft 和 Published Ref |
| `DefinitionVersion` | Aggregate Root + immutable Entity | 独立身份、不可变、可被 Execution / Workflow / Catalog 稳定引用 |
| `SyncExecution` | Aggregate Root + Entity | 独立运行生命周期、并发、幂等、停止和对账 |

### Value Object

| 类型 | 分类 | 说明 |
|---|---|---|
| `TaskId` | VO | 强类型任务身份值 |
| `TaskProfile` | VO | name / description |
| `DefinitionDraft` | VO | Task 内部当前工作副本 |
| `PublishedDefinitionRef` | VO | 当前发布版本引用 |
| `DefinitionVersionRef` | VO | 运行或外部上下文使用的版本引用 |
| `SyncDefinition` | VO | 同步配置唯一领域事实模型 |
| `SourceEndpoint` | VO | Source DataSourceRef |
| `SinkEndpoint` | VO | Sink DataSourceRef |
| `DataSourceRef` | VO | 邻接数据源引用 |
| `SyncRoute` | VO | selector + target + replay key |
| `SourceSelector` variants | VO | EXACT / PATTERN 选择语义 |
| `SinkTarget` variants | VO | Sink 目标对象语义 |
| `ReplayKey` | VO | 安全回放键 |
| `SyncPolicy` | VO | 同步语义 |
| `StartupPolicy` | VO / enum-like | 初始化与持续读取语义 |
| `SchemaEvolutionPolicy` | VO / enum-like | Schema 变化语义 |
| `ExecutionPolicy` | VO | 引擎中立执行偏好 |
| `CheckpointPolicy` | VO | Checkpoint 偏好 |
| `RestartPolicy` variants | VO | 重启语义 |
| `SinkWritePolicy` | VO | Sink 写入偏好 |
| `RuntimeEnvironmentRef` | VO | 邻接运行环境引用 |
| `RuntimeEnvironmentSnapshot` | VO | 一次 Execution 使用的环境证据 |
| `DefinitionDigest` | VO | 规范化 Definition + Binding 摘要 |
| `ExecutionDefinitionSnapshot` | VO | Execution 不可变定义证据 |
| `EngineExecutionRef` | VO | 外部运行引用 |
| `DesiredState` / `ObservedState` | VO / enum-like | 生命周期值，完整状态机阶段 3 定义 |

### v1 不需要内部 Entity

阶段 2 不为 `SyncRoute` 等对象强行增加 ID。

原则：

> **没有独立身份和生命周期，就优先 Value Object。不要为了数据库主键或前端 key 把所有东西建成 Entity。**

---

## 15. Aggregate 之间如何引用

跨聚合只通过稳定引用：

```text
RealtimeSyncTask
   └── PublishedDefinitionRef
            ↓
      DefinitionVersion

SyncExecution
   ├── TaskId
   └── DefinitionVersionRef
            ↓
      DefinitionVersion
```

禁止在一个聚合中直接嵌入另一个聚合的可变对象图。

例如不要：

```java
class RealtimeSyncTask {
    List<DefinitionVersion> allVersions;
    List<SyncExecution> allExecutions;
}
```

也不要：

```java
class SyncExecution {
    RealtimeSyncTask mutableTask;
}
```

Application Service / Repository 负责按引用加载需要的聚合。

---

## 16. 关键领域流程如何穿过模型

本阶段只描述对象关系，不定义完整状态机。

### 16.1 新建

```text
create
  ↓
RealtimeSyncTask
  ├── TaskId
  ├── TaskProfile
  ├── DefinitionDraft? = empty
  └── PublishedDefinitionRef = empty
```

允许先创建 Task Shell，再进入 Wizard / YAML 完成 Draft。

### 16.2 保存 Draft

```text
Wizard / YAML
     ↓
SyncDefinition
     ↓
DefinitionDraft(new)
     ↓ replace
RealtimeSyncTask.currentDraft
```

保存 Draft 不创建生产可运行版本。

### 16.3 Publish

```text
RealtimeSyncTask.currentDraft
          ↓ publish snapshot
DefinitionVersion vN (immutable)
          ↓
RealtimeSyncTask.publishedRef = vN
```

旧的 vN-1 不修改。

### 16.4 再次编辑

```text
Published v3
     ↓
用户编辑 Draft v4 candidate
```

此时：

```text
PublishedDefinitionRef = v3
Current Draft          = new content
```

两者可以同时存在。

### 16.5 Start

```text
RealtimeSyncTask.publishedRef
          ↓
DefinitionVersion v3
          ↓ create
SyncExecution #E100
          ├── DefinitionVersionRef v3
          ├── Definition Snapshot v3
          └── Runtime Environment Snapshot
```

禁止：

```text
start -> read current Draft
```

### 16.6 Draft 在运行中继续编辑

```text
Execution #E100 -> v3 snapshot（不变）

Task Draft       -> v4 candidate（可继续编辑）
```

是否允许某些状态下编辑、发布属于阶段 3 生命周期和不变量。

---

## 17. 场景如何由模型组合表达

### 17.1 单表

```text
SyncDefinition
└── routes
    └── SyncRoute
        ├── ExactTableSelector("orders")
        ├── TableTarget("ods_orders")
        └── ReplayKey(["id"])
```

无需：

```text
syncType = SINGLE_TABLE
```

### 17.2 多表

```text
routes = [
  orders   -> ods_orders,
  customer -> ods_customer,
  payment  -> ods_payment
]
```

无需：

```text
syncType = MULTI_TABLE
```

### 17.3 Pattern / Regex

```text
SyncRoute
├── TablePatternSelector("order_.*")
├── TableTarget("orders")
└── ReplayKey(["id"])
```

### 17.4 未来整库

优先扩展：

```text
SourceSelector
    += DatabaseSelector
```

如果同时需要每个 Source Table 自动映射目标表，再扩展：

```text
SinkTarget
    += SameNameTarget / TemplateTarget
```

不优先新增：

```text
WholeDatabaseRealtimeSyncTask
WHOLE_DATABASE syncType
```

### 17.5 未来 Kafka Sink

如果 Kafka 被 DataSource / Connection Asset Context 支持：

```text
SinkEndpoint(DataSourceRef kafkaRef)
```

由 Adapter Capability 判断是否支持。

不新增：

```text
KafkaSyncDefinition
KafkaRealtimeTask
```

---

## 18. 当前 CdcPipelineSpec 与 Core Model 的差异

当前：

```text
CdcPipelineSpec
├── sourceDataSourceRef
├── sinkDataSourceRef
├── tables
├── startupMode
├── schemaEvolution
├── parallelism
├── checkpointIntervalMs
├── restart
└── sink tuning
```

阶段 2 不立即重构，但已经可以明确 Mapping。

| 当前字段 | Core Domain v1 |
|---|---|
| `sourceDataSourceRef` | `SourceEndpoint(DataSourceRef)` |
| `sinkDataSourceRef` | `SinkEndpoint(DataSourceRef)` |
| `TableRoute.sourceTable + matchMode` | `SourceSelector` |
| `TableRoute.sinkTable` | `SinkTarget.TableTarget` |
| `TableRoute.keyColumns` | `ReplayKey` |
| `startupMode` | `StartupPolicy`，由 Adapter 做 `initial/latest-offset` 翻译 |
| `schemaEvolution` | `SchemaEvolutionPolicy` |
| `parallelism` | `ExecutionPolicy.parallelism` |
| `checkpointIntervalMs` | `CheckpointPolicy`；当前 Adapter 存在未真正应用的 Implementation Gap |
| `restart` | `RestartPolicy`；当前 `failure-rate` 表达不完整，不直接升格为 Core Model |
| `sink.maxRetries/batchSize/flushInterval/maxBatchBytes` | `SinkWritePolicy` |
| `sink.statementCacheSize` | JDBC Adapter Tuning，不属于 Core Domain |
| `sink.strictReplaySafety` | v1 领域不变量，不作为 boolean Policy |

因此阶段 2 的结论不是：

```text
CdcPipelineSpec = 永久的最终领域模型
```

而是：

> `CdcPipelineSpec` 是当前统一结构化载体，已经包含大量正确方向，但同时混有 Infrastructure Tuning 和硬编码布尔策略。阶段 4 再制定迁移 Mapping。

---

## 19. 当前 DefinitionRow / DeploymentRow 的语义差异

当前 `DefinitionRow` 同时包含：

```text
Task identity
current spec
environment id
release state
desired / observed state
definitionVersion
publishedVersion
error
```

这说明当前 Persistence Model 为一期实现做了合理压缩，但它 **不是阶段 2 最终领域聚合边界**。

阶段 2 目标模型会拆成：

```text
RealtimeSyncTask
DefinitionVersion
SyncExecution
```

具体数据库是否立即拆表、如何兼容现有表，属于阶段 4 / 6。

当前 `DeploymentRow` 已经非常接近：

```text
SyncExecution persistence model
```

尤其已有：

- definitionVersion；
- specSnapshot；
- runtimeEnvironment snapshot；
- engineJobId；
- idempotencyKey；
- status / error。

阶段 4 会基于这一事实做最小迁移方案，而不是推倒重写。

---

## 20. 阶段 2 后禁止的建模方式

### 20.1 禁止一个大 Job 聚合吞掉所有概念

禁止：

```text
RealtimeJob
├── definition
├── yaml
├── flinkJobId
├── metrics
├── runtime config
├── deployment history
└── editor mode
```

### 20.2 禁止为 Source/Sink 类型创建 Task 子类

默认禁止：

```text
MysqlRealtimeTask
KafkaRealtimeTask
PostgresRealtimeTask
```

除非未来它们真的拥有完全不同的领域生命周期和不变量，而不仅仅是 Adapter 能力不同。

### 20.3 禁止 `SyncDefinition` 拥有版本 ID

版本身份属于：

```text
DefinitionVersion
```

不是 `SyncDefinition`。

### 20.4 禁止 Execution 读取当前 Draft

运行必须来自：

```text
DefinitionVersion
```

### 20.5 禁止把 Adapter 私有参数持续塞进 ExecutionPolicy

例如：

```text
statementCacheSize
flinkRestAddress
sshUser
connectorJarPath
```

### 20.6 禁止为 Route 强行增加 Entity 身份

没有独立生命周期时：

```text
SyncRoute = Value Object
```

### 20.7 禁止把 UI 场景当核心枚举

```text
single/multi/database/sharding
```

优先由 Selector + Route + Policy 派生。

---

## 21. AI 在阶段 2 后必须先做的模型定位

后续 AI 接到实时同步需求时，至少先回答：

```text
1. 修改的是哪个 Aggregate Root？
   - RealtimeSyncTask
   - DefinitionVersion
   - SyncExecution
   - 或者根本不属于 Realtime Sync Domain

2. 如果修改 SyncDefinition，属于哪一部分？
   - Endpoint
   - Route / Selector / Target / ReplayKey
   - SyncPolicy
   - ExecutionPolicy

3. 这个概念有独立身份/生命周期吗？
   - 有：才考虑 Entity / Aggregate
   - 没有：优先 Value Object

4. 新场景能否通过 Selector + Route + Policy 组合？

5. 是否正在把 Adapter 私有参数加入 Core Domain？
```

如果无法定位：

```text
Domain Gap
```

先讨论模型，不直接编码。

---

## 22. 阶段 2 明确不解决的问题

以下留给阶段 3：

- Source 与 Sink 是否允许同一引用；
- Route 重复、冲突的精确定义；
- ReplayKey 的强制规则；
- Draft / Published 的精确可变与不可变规则；
- VersionNo / DraftRevision 的并发规则；
- Publish / Start / Stop / Restart 状态机；
- DesiredState / ObservedState 的完整状态集合和转换；
- 同一 Task 是否允许多个并行 Active Execution；
- Execution 创建 / 提交失败 / 不确定结果如何状态转换；
- Snapshot 一致性校验规则；
- 删除 Task / Version / Execution 的约束。

以下留给阶段 4：

- 现有 `CdcPipelineSpec / DefinitionRow / DeploymentRow` 的逐类代码 Mapping；
- 数据库兼容方案；
- Repository 边界迁移。

以下留给阶段 6：

- Java 类实际重命名和最小重构。

---

## 23. 阶段 2 验收标准

阶段 2 完成后，团队和 AI 应能稳定回答：

### “哪个是聚合根？”

```text
RealtimeSyncTask
DefinitionVersion
SyncExecution
```

### “SyncDefinition 是什么？”

```text
不可变 Value Object
```

### “版本号放哪里？”

```text
DefinitionVersion
```

### “单表和多表怎么表达？”

```text
SyncRoute 数量和 SourceSelector 组合派生
```

### “REGEX 放哪里？”

```text
TablePatternSelector
```

### “主键 / Replay Key 放哪里？”

```text
SyncRoute.ReplayKey
```

### “Flink JobId 放哪里？”

```text
Flink Adapter -> EngineExecutionRef -> SyncExecution
```

### “运行环境放 SyncDefinition 吗？”

```text
不放。
Draft / DefinitionVersion 保存 RuntimeEnvironmentRef；
SyncExecution 保存 RuntimeEnvironmentSnapshot。
```

### “statementCacheSize 是核心领域字段吗？”

```text
不是，属于 JDBC Adapter Tuning。
```

### “strictReplaySafety 是用户 Policy 吗？”

```text
v1 不是；它是领域不变量，阶段 3 固定规则。
```

### “新功能映射不到三个聚合和 Definition 子模型怎么办？”

```text
Domain Gap -> 先扩模型，再编码。
```

---

## 24. 下一阶段

阶段 3 将在本模型上定义：

```text
领域不变量
+
生命周期
+
状态转换
+
并发规则
+
Snapshot 规则
```

即从“这个世界有哪些对象”继续推进到：

> **这个世界里，哪些事情允许发生，哪些事情绝对不能发生。**
