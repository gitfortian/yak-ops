# Realtime Sync 领域边界与统一语言

> 状态：Proposed（阶段 1；本 PR 合并后视为 Accepted）  
> 范围：确定实时同步领域的职责边界、邻接上下文、统一语言和术语禁区；本阶段不重构现有 Java 领域类，不修改数据库结构和运行链路。

## 1. 为什么先定义领域边界

实时同步已经具备 Wizard、Yak YAML、统一 Spec、校验、保存、发布、启动、停止、日志与指标等完整一期主流程。

后续如果继续直接按页面、接口或 Flink 能力增加模型，很容易出现：

- `WizardSpec / YamlSpec / FlinkSpec` 等第二事实模型；
- `singleTable / multiTable / database / sharding` 等场景枚举持续膨胀；
- `FlinkJobId / PipelineYaml / SSH` 等技术概念进入核心业务对象；
- Task、Definition、Execution 职责混在一起；
- AI 为了完成局部需求，创建一套局部合理但与全局模型冲突的新结构。

因此阶段 1 先固定一条规则：

> **任何实时同步需求，都必须先使用本文件的统一语言描述，并确认属于实时同步领域还是外围适配。无法映射时，先讨论领域模型，不直接编码。**

---

## 2. Realtime Sync Domain 的使命

实时同步领域负责描述并维护：

> **一份数据从哪里来、哪些数据需要被持续同步、同步到哪里、遵循什么同步语义，以及某个已发布定义如何形成一次可追踪的运行。**

领域关注的是：

```text
任务身份
  ↓
同步定义
  ↓
发布版本
  ↓
运行实例
```

领域不围绕某一种执行引擎设计。

Flink CDC 是当前一期的执行实现，但不是实时同步领域本身。

---

## 3. 有界上下文：领域负责什么

Realtime Sync Context 拥有以下业务语义。

### 3.1 实时同步任务身份

负责维护一个长期存在的实时同步任务：

- 任务是谁；
- 名称、描述等业务元数据；
- 当前正在编辑哪份定义；
- 当前发布的是哪个定义版本；
- 任务是否允许继续编辑、发布、运行。

统一术语：`RealtimeSyncTask`。

### 3.2 同步定义

负责表达“这条任务到底要怎么同步”：

- Source；
- Sink；
- Source 到 Sink 的 Route；
- 同步语义；
- 执行策略。

统一术语：`SyncDefinition`。

`SyncDefinition` 是实时同步配置的领域事实模型。Wizard、Yak YAML、REST DTO、Flink Pipeline YAML 都不能成为第二份领域事实来源。

### 3.3 发布版本

负责将可变编辑态定义固化为明确版本，并允许运行实例绑定到该版本。

统一术语：`DefinitionVersion`。

发布版本的具体不可变规则与版本生命周期在阶段 3 进一步确定。

### 3.4 一次运行实例

负责表达“某个已发布定义的一次实际运行”：

- 运行的是哪个任务；
- 基于哪个已发布定义；
- 使用哪个运行环境快照；
- 用户希望它运行还是停止；
- 外部执行引擎实际处于什么状态；
- 外部运行实例引用是什么。

统一术语：`SyncExecution`。

---

## 4. 不属于 Realtime Sync Domain 的内容

以下能力可以服务实时同步，但不由实时同步领域拥有。

### 4.1 Data Source Management

数据源上下文拥有：

- JDBC URL；
- Host / Port；
- Username / Password；
- Connection JSON；
- Driver；
- 数据源连通性；
- Database / Schema / Table / Column Catalog 元数据。

Realtime Sync 只保存或引用 `DataSourceRef`，不复制连接凭据。

因此领域对象中不应出现：

```text
password
jdbcUrl
hostname
connectionJson
```

### 4.2 Compute / Runtime Environment

运行环境上下文拥有：

- Flink Home；
- Flink CDC Home；
- Flink REST URL；
- Java Home；
- SSH host / user / key；
- Flink / Flink CDC 版本；
- 本地提交还是远程提交；
- 环境启停与配置管理。

Realtime Sync 可以引用运行环境，并在创建运行实例时保存必要快照，但不拥有运行环境本身的生命周期。

### 4.3 Flink / CDC Engine

Flink 属于 Infrastructure Adapter。

以下概念不能成为核心领域语言：

```text
Flink Job
Flink REST
Flink CDC Pipeline YAML
flink-cdc.sh
SSH submit
CLI PID
Connector JAR
SECRET placeholder
```

领域只允许使用引擎中立概念，例如：

- `SyncExecution`；
- `EngineExecutionRef`；
- `ExecutionState`。

`Flink JobId` 只能作为某一种 `EngineExecutionRef` 的基础设施表达。

### 4.4 UI / API

以下属于 Interface Adapter：

- Wizard；
- YAML Editor；
- Controller；
- Request / Response DTO；
- VO；
- 页面 Tab；
- URL Query 参数。

因此核心领域模型中禁止出现：

```text
editorMode
wizardMode
yamlContent
pageType
tabKey
```

### 4.5 Lineage / Workflow / Task Catalog

这些是邻接上下文，不由实时同步核心领域直接实现：

- Lineage 可以消费 `SyncRoute` 产生的 Source → Sink 关系；
- Task Catalog 可以暴露已发布的实时同步任务资产；
- Workflow 可以引用明确的已发布版本；
- Realtime Sync 不应为了这些上下文复制第二套定义模型。

---

## 5. 统一语言（Ubiquitous Language）

阶段 1 固定以下术语。后续设计、代码评审、AI 分析优先使用这些名称表达概念。

| 中文 | 英文术语 | 定义 | 明确不是什么 |
|---|---|---|---|
| 实时同步任务 | `RealtimeSyncTask` | 用户长期维护的一条实时同步任务身份 | 不是 Flink Job，不是一次运行记录 |
| 同步定义 | `SyncDefinition` | 描述从哪里同步、同步什么、到哪里以及遵循什么策略的统一领域定义 | 不是 YAML，不是页面 Form，不是 Flink Pipeline |
| 来源端点 | `SourceEndpoint` | 同步数据的业务来源端点，通常引用平台数据源 | 不持有密码、JDBC URL，不等于 Flink Source Connector |
| 目标端点 | `SinkEndpoint` | 同步数据的业务目标端点，通常引用平台数据源 | 不等于 Flink Sink Connector |
| 同步路由 | `SyncRoute` | Source 数据对象选择与 Sink 目标之间的一条同步关系 | 不是“单表任务类型”或“多表任务类型” |
| 来源选择器 | `SourceSelector` | 描述 Route 从 Source 中选择哪些数据对象 | 不预设只能是 EXACT/REGEX，阶段 2 决定具体结构 |
| 目标对象 | `SinkTarget` | Route 最终写入的业务目标对象 | 不等于 JDBC Sink 配置 |
| 回放键 | `ReplayKey` | 在至少一次交付语义下支撑安全重放/幂等写入的数据键语义 | 不简单等同于 UI 自动识别动作 |
| 同步策略 | `SyncPolicy` | 描述初始化、持续同步、Schema Evolution、Replay Safety 等同步语义 | 不包含 SSH、Flink CLI 参数 |
| 启动策略 | `StartupPolicy` | 第一次运行时应如何读取 Source 的领域语义 | 不直接使用 `scan.startup.mode` 作为领域语言 |
| Schema 演进策略 | `SchemaEvolutionPolicy` | Source 结构变化时同步系统应如何处理 | 不等于某个 Connector 的 YAML 字段 |
| 执行策略 | `ExecutionPolicy` | 描述并行度、Checkpoint、Restart、Sink 写入调优等运行偏好 | 不拥有运行环境，不包含 SSH 配置 |
| 定义版本 | `DefinitionVersion` | 发布后可被运行明确引用的一版同步定义 | 不是页面上的临时 Draft state |
| 同步运行实例 | `SyncExecution` | 某个已发布定义的一次实际运行记录 | 不是 Task 本身，不直接等于 Flink Job |
| 运行环境引用/快照 | `RuntimeEnvironmentRef / Snapshot` | 一次定义或运行所绑定的平台运行资源信息 | 运行环境配置生命周期由 Compute Context 管理 |
| 引擎运行引用 | `EngineExecutionRef` | 指向外部执行引擎运行实例的中立引用 | 不把 `flinkJobId` 固化为领域模型唯一形式 |
| 期望状态 | `DesiredState` | 用户/控制面希望运行实例达到的状态 | 不是外部引擎当前真实状态 |
| 观测状态 | `ObservedState` | 系统从外部执行引擎观测到的实际状态 | 不是用户操作意图 |
| 领域缺口 | `Domain Gap` | 新需求无法被当前统一语言和领域边界正确表达 | 不是“先写一个临时字段以后再说” |

---

## 6. 领域语言中的关键区别

### 6.1 Task ≠ Definition ≠ Execution

必须始终区分：

```text
RealtimeSyncTask
    “这条长期任务是谁”

SyncDefinition
    “这一版到底怎么同步”

SyncExecution
    “这一版这一次怎么运行”
```

任何设计如果把三者压缩成一个“大 Job 对象”，都需要重新评审。

### 6.2 Wizard ≠ YAML ≠ SyncDefinition

正确关系是：

```text
Wizard ─────┐
            ├──> SyncDefinition
Yak YAML ───┘
```

不是：

```text
WizardSpec
YamlSpec
FlinkSpec
```

当前系统已经采用 `CdcPipelineSpec` 作为统一结构化配置载体；阶段 2 会决定它与 `SyncDefinition` 的正式关系，阶段 1 不做代码重命名。

### 6.3 SyncExecution ≠ Flink Job

正确关系是：

```text
SyncExecution
      ↓ Infrastructure Adapter
EngineExecutionRef
      ↓ Flink Adapter
Flink JobId
```

这允许未来替换或增加其他执行引擎，而不改变实时同步核心语言。

### 6.4 单表 / 多表 / 整库不是 Task Type

阶段 1 固定原则：

> **同步场景优先由 SourceSelector + SyncRoute 的组合能力表达，不优先增加 `syncType / sceneType`。**

例如：

```text
1 个 EXACT Route       -> 用户体验上可以称为单表
N 个 EXACT Route       -> 用户体验上可以称为多表
1 个规则 Selector      -> 可以表达规则匹配
未来 Database Selector -> 可以表达整库
```

“单表 / 多表 / 整库 / 分库分表”可以是 UI 场景名称，但不默认升级为核心任务类型枚举。

---

## 7. 分层边界

后续代码应尽量保持以下方向：

```text
Interface / UI
      ↓
Application
      ↓
Domain
      ↑
Ports
      ↑
Infrastructure Adapters
```

### Domain

只表达：

- 实时同步业务概念；
- Value Object / Entity / Aggregate；
- 领域不变量；
- 引擎中立状态与策略。

Domain 不依赖 Controller、YAML、Flink、SSH、数据库 Mapper。

### Application

负责用例编排，例如：

- 保存草稿；
- 发布定义；
- 启动；
- 停止；
- 对账；
- 查询运行详情。

Application 可以调用 Domain 和 Port，但不把 Flink 细节提升为领域概念。

### Infrastructure

负责：

- `PipelineYamlCompiler`；
- Flink CDC CLI；
- Flink REST；
- SSH；
- Secret 注入；
- Repository / Mapper；
- DataSource / Runtime Environment Adapter。

### Interface / UI

负责：

- Wizard；
- Yak YAML；
- HTTP DTO；
- 页面状态；
- 表单校验反馈。

---

## 8. 邻接上下文关系

```text
DataSource Context
      |
      | DataSourceRef / Catalog
      v
Realtime Sync Context
      |
      | Published Definition / Execution
      +----------> Task Catalog / Workflow
      |
      | Source -> Sink route facts
      +----------> Lineage Context
      |
      | RuntimeEnvironmentRef
      v
Compute Environment Context
      |
      v
Infrastructure: Flink / SSH / CDC Connector
```

边界原则：

- Realtime Sync 引用 DataSource，不复制数据源连接模型；
- Realtime Sync 引用 Runtime Environment，不管理 Flink 安装和 SSH 凭据；
- Workflow 引用已发布事实，不复制实时同步 Draft；
- Lineage 消费 Route 事实，不反向控制 SyncDefinition。

---

## 9. 新需求的边界判断示例

### “支持 Kafka Sink”

领域表达：扩展 `SinkEndpoint` 可支持的端点能力。  
基础设施：增加 Kafka Sink Adapter / Compiler 支持。

禁止直接创建：

```text
KafkaRealtimeSyncTask
KafkaSyncDefinition
KafkaJobController
```

### “支持整库同步”

优先判断是否属于 `SourceSelector / SyncRoute` 能力扩展。

禁止先增加：

```text
syncType = WHOLE_DATABASE
```

除非阶段 2 的领域模型证明它确实是不可由 Route/Selector 组合表达的独立概念。

### “YAML 注释要保留”

这是 Editor / Serialization Concern，不是 `SyncDefinition` 业务语义。

如果未来要保留源码，需要设计独立编辑文档模型，不能把 `yamlContent` 塞入核心同步定义。

### “Windows 本机通过 SSH 提交远端 Flink CDC”

这是 Infrastructure / Runtime Environment Concern。

不会产生新的实时同步任务类型。

### “任务发布后允许回滚到 v3”

这是 `DefinitionVersion` 与发布生命周期问题，属于 Realtime Sync Domain。

### “展示 Flink Metrics / Checkpoint”

这是 Execution Observability / Infrastructure Concern。

Metrics 不进入 `SyncDefinition`。

### “实时同步产生数据血缘”

Realtime Sync 提供 `SyncRoute` 的 Source → Sink 事实；血缘图、上下游查询、图存储由 Lineage Context 负责。

---

## 10. 术语禁区

以下命名如果出现在核心领域设计中，默认需要阻止并重新判断边界。

### 禁止把编辑方式建模为领域类型

```text
WizardTask
YamlTask
WizardSpec
YamlSpec
editorMode
```

### 禁止把执行引擎建模为核心任务类型

```text
FlinkRealtimeTask
FlinkSyncDefinition
FlinkJob as aggregate root
```

### 禁止把技术配置放进核心 Definition

```text
pipelineYaml
sshCommand
flinkHome
flinkRestUrl
jdbcUrl
password
```

### 谨慎增加场景枚举

```text
syncType
sceneType
SINGLE_TABLE
MULTI_TABLE
WHOLE_DATABASE
SHARDING
```

场景名称可以服务 UI，但要进入核心 Domain 必须先证明它不能由 Route / Policy 组合表达。

---

## 11. 当前实现与统一语言的临时映射

阶段 1 **只做语义映射，不做重构承诺**。

| 当前实现 | 阶段 1 领域语言中的位置 |
|---|---|
| `CdcPipelineSpec` | 当前最接近 `SyncDefinition` 的结构化载体 |
| `CdcPipelineSpec.TableRoute` | 当前最接近 `SyncRoute` 的实现 |
| `startupMode` | 当前 `StartupPolicy` 的基础设施/实现表达 |
| `schemaEvolution` | 当前 `SchemaEvolutionPolicy` 的实现表达 |
| parallelism / checkpoint / restart / sink tuning | 当前 `ExecutionPolicy` 的实现字段 |
| `definitionVersion / publishedVersion` | 已存在的 `DefinitionVersion` 语义基础 |
| `DeploymentRow` | 当前最接近 `SyncExecution` 的持久化表达 |
| `RealtimeStateMachine` | Execution 生命周期规则的当前实现基础 |
| `PipelineYamlCompiler` | Infrastructure Compiler |
| `RealtimeEngineGateway` | Execution Engine Port |
| `FlinkCdcEngineGateway` | Flink Infrastructure Adapter |
| Wizard / `WizardJobEditor` | Interface Editor Adapter |
| Yak YAML / `RealtimeYamlCodec` | Definition Serialization / Editor Adapter |

阶段 2 会基于这个 Mapping 决定真正的聚合、Entity、Value Object 和命名；当前代码不因为阶段 1 文档立即改名。

---

## 12. AI / 开发在阶段 1 必须遵守的最小检查

完整 AI 领域宪法属于阶段 5；从阶段 1 开始，所有实时同步需求至少先回答：

1. 这个需求属于 Realtime Sync Domain，还是 DataSource / Runtime Environment / Infrastructure / UI 等邻接上下文？
2. 它影响 `RealtimeSyncTask`、`SyncDefinition`、`DefinitionVersion` 还是 `SyncExecution`？
3. 如果是新的同步场景，能否通过 `SourceSelector + SyncRoute + Policy` 组合表达？
4. 是否正在引入第二套同步定义事实模型？
5. 是否把 Flink、YAML、SSH、JDBC Credential 等技术细节塞进 Domain？

如果第 2 个问题无法回答，应标记：

```text
Domain Gap
```

并先进入领域模型讨论，而不是直接增加代码字段或新 `*Spec / *Task / *Service`。

---

## 13. 阶段 1 明确不解决的问题

以下内容留到后续阶段：

- `RealtimeSyncTask` 是否最终聚合根；
- `SyncDefinition` 是 Entity 还是 Value Object；
- `DefinitionVersion` 与 Task 的精确聚合关系；
- `SyncExecution` 是否独立聚合根；
- `SourceSelector / SinkTarget / ReplayKey` 的精确类型结构；
- `SyncPolicy` 与 `ExecutionPolicy` 的字段边界；
- Desired / Observed 状态完整状态机；
- Draft、Published、Execution Snapshot 的不可变规则；
- 现有 Java 包和类如何迁移；
- ArchUnit / 架构测试等自动化护栏。

这些分别属于阶段 2、3、4、6、7。

---

## 14. 阶段 1 验收标准

阶段 1 完成后，团队和 AI 应能稳定回答：

- “实时同步任务”和“Flink Job”为什么不是同一个东西；
- Wizard 与 YAML 为什么不能各自拥有一套 Spec；
- Source/Sink 为什么只引用数据源而不保存密码；
- 单表、多表、整库为什么优先是 Route/Selector 的组合，而不是 Task Type；
- 哪些需求属于 Definition，哪些属于 Execution，哪些只是 Infrastructure；
- 一个无法映射到统一语言的新需求为什么必须先标记为 Domain Gap。

阶段 2 在此基础上继续确定 **核心领域模型 v1**。
