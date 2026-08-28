# Data Development Architecture

本文件定义 Data Development 的长期结构 contract。需求看 `REQUIREMENTS.md`，领域硬规则看 `DOMAIN.md`，依赖方向看 `DEPENDENCIES.md`，Review 看 `REVIEW.md`。

## 设计原则

1. **业务子系统优先。** package 要能表达 Node、Task、Execution、Dataset、Data Service Publication、Release、Lineage 等业务角色。
2. **Service 是入口角色，不是分层目录。** 允许稳定 Application Service，禁止通用 Service 大桶。
3. **名字表达职责。** Manager / Publisher / Validator / Normalizer / Coordinator / Resolver / Parser / Analyzer / Reader / Gateway / Worker 各自只承担对应角色。
4. **Truth 只有一个 owner。** Node、Draft、Revision、Execution、Task Catalog projection、Data Service Runtime projection、Lineage evidence 不互相冒充。
5. **Domain 只放领域事实。** 纯 API / query read model 跟随所属业务子系统，不因为是 record 就进入 `domain`。
6. **结构重构不偷改行为。** package move 与 REST / DB / Domain semantic change 分开。
7. **外部系统停在边界。** Task Runtime、Task Catalog、Dataset、Data Service Runtime、Lineage、DataSource 都是邻接上下文。
8. **架构必须可执行。** 文档由 architecture tests 和固定 legacy allowlist 守住。

## Package Map

```text
io.yak.ops.business.development
├── controller          # HTTP inbound only
├── node                # Node identity / metadata lifecycle
├── directory           # workspace directory lifecycle
├── task                # Draft / validation / immutable Revision / Task Catalog projection
├── execution           # editor manual run + execution history
│   └── model           # execution query / response projections
├── dataset             # Dataset output-node application boundary
├── dataservice         # Data Service Node -> Runtime publication owner boundary
├── release             # published Task Catalog read/activation boundary
│   └── model           # release-center query projections
├── editor              # editor preference boundary
├── lineage             # outbox / worker / write transaction / analysis adapter
│   └── analysis        # shared Lineage analysis-contract implementation
├── domain              # truth-bearing facts / value objects / invariants only
├── repository          # persistence contracts + adapters
├── dao                 # MyBatis persistence primitives
├── config              # module configuration
└── service             # frozen legacy Data Service / SQL parser algorithm island
```

禁止新增 `common / helper / helpers / util / utils / base` 业务大桶。

## Core Truth

```text
DevelopmentNode
      │ author
      ▼
DevelopmentTaskDraft (mutable)
      │ publish
      ▼
DevelopmentTaskRevision (immutable)
      │ project
      └────────> Task Catalog

current editor definition
      │ run
      ▼
DevelopmentTaskExecution
```

固定关系：

```text
Node != Draft != Revision != Execution
```

DATASET / DATA_SERVICE 是 Output Node，不进入 executable Task Draft/Revision lifecycle。

Node / Directory 的命令侧名称规则由 `DevelopmentNodeName`、`DevelopmentDirectoryName` 值对象持有；Node Type 的合法性和 capability gate 由 `DevelopmentNodeType` 持有。Application Service 只负责仓储、事务和邻接上下文协调。

## Model Placement

核心领域模型与读侧投影必须分开：

```text
domain
  -> identity / lifecycle truth / value object / invariant

release.model
  -> release page / summary / detail projection

execution.model
  -> execution page / summary / detail / synchronous run response
```

Read Model 可以引用多个已有 truth owner 来组装接口返回，但不能因此变成新的领域事实。后续新增 `Page / Summary / Detail / View / Response` 类型时，默认放在所属业务子系统，而不是 `domain`。

## Stable Application Entries

稳定入口按业务包放置：

```text
node.DevelopmentNodeService
directory.DevelopmentDirectoryService
task.DevelopmentTaskService
execution.DevelopmentTaskRunService
execution.DevelopmentTaskExecutionService
dataset.DevelopmentDatasetNodeService
dataservice.DevelopmentDataServicePublicationService
release.DevelopmentReleaseService
editor.DevelopmentEditorSettingsService
```

Controller 只依赖这些入口以及明确保留的 legacy lineage preview corridor，不直接进入 Repository / DAO。

## Task Subsystem

```text
DevelopmentTaskService
├── DevelopmentTaskNodeResolver
├── DevelopmentTaskDefinitionNormalizer
├── DevelopmentTaskDraftManager
├── DevelopmentTaskValidator
├── TaskDefinitionDigestCalculator
├── DevelopmentTaskPublisher
└── DevelopmentTaskRevisionReader
```

固定职责：

- NodeResolver：Node lookup + executable capability gate；
- DefinitionNormalizer：唯一 TaskDefinition normalization；
- DraftManager：Draft read/save/lock；
- Validator：Task Plugin publish validation；
- DigestCalculator：immutable definition digest；
- Publisher：Revision append/reuse + Task Catalog projection；
- RevisionReader：immutable Revision read side。

Task publish 成功后只向 `lineage.DevelopmentLineageOutbox` 投递 SQL evidence 工作，不直接同步写 Lineage 图。

## Execution Subsystem

```text
DevelopmentTaskRunService
├── DevelopmentTaskNodeResolver
├── DevelopmentTaskDefinitionNormalizer
├── shared TaskExecutionGateway
└── DevelopmentTaskExecutionService

execution.model
├── DevelopmentTaskExecutionSummary
├── DevelopmentTaskExecutionPage
├── DevelopmentTaskExecutionDetail
└── DevelopmentTaskRunResult
```

Editor Run 使用临时 `TaskVersionSnapshot(version=0)`。它不是 Publish，也不能创建 immutable Revision。

Execution history 是 Data Development 的运行记录；真正的 executor runtime state 仍由共享 Task Runtime 提供。`execution.model` 只是读侧 / response projection，不拥有运行状态。

## Node / Directory / Dataset / Data Service Publication / Release

这些能力不再放在通用 service 包：

```text
node        -> Node identity / rename / delete / updater metadata
directory   -> hierarchy / path / empty-delete rule
dataset     -> Dataset-owned datasource + SQL + field contract
dataservice -> Data Service Node 对相邻 Runtime projection 的上线/下线 owner boundary
release     -> Task Catalog release projection + online/offline/activate
editor      -> user editor settings
```

`DevelopmentDataServicePublicationService` 不拥有 Data Service Runtime truth。它只保证 source-managed Runtime 的创建、重发和启停必须从 Data Development authoring context 进入；通用 Data Service 管理 API 不得绕过这个 owner boundary 修改 Data Development 来源的服务。

`Release` 读取 Task Catalog projection 与 immutable Task Revision，但不能修改历史 Revision。Release API 的 `Summary / Page / Detail` 位于 `release.model`，避免把组合查询结果伪装成核心领域事实。

## Lineage Boundary

```text
Task publish
   -> DevelopmentLineageOutbox(project_id)
   -> DevelopmentLineageWorker
   -> ProjectContextScope(project_id)
   -> legacy SQL parser preparation
   -> lineage.analysis.DevelopmentSqlProjectionLineageAnalyzer
   -> DevelopmentLineageWriteTransaction
   -> yak-ops-business-lineage
```

Outbox / Worker / transaction orchestration 和 source-neutral projection analyzer adapter 已归 `lineage`。具体 SQL Parser 大算法仍留在 frozen legacy island；共享 Analyzer contract 由 `yak-ops-business-lineage.analysis.sql` 持有，Data Development 只提供基于现有 parser 的技术实现。

后台 Outbox 不能在空 `CurrentProject` 下读取 Node/Revision。Worker 必须从持久化 `project_id` 恢复受信项目上下文，并校验 Outbox 与 Node 的项目身份一致后才能写 Lineage。

固定方向：

```text
Dataset -> shared Lineage Analyzer contract
Data Development lineage.analysis -> shared Lineage Analyzer contract
shared Lineage -X-> Data Development parser implementation
```

## Frozen Legacy Service Island

`service` 已不是新代码的落点，只允许以下固定类型：

```text
DerivedAwareSqlColumnLineageParser
DevelopmentDataServiceNodeService
DevelopmentDataServiceNodeSourceProvider
DevelopmentDataServiceSqlCompiler
DevelopmentDraftConflictException
DevelopmentSqlLineagePreviewService
DevelopmentSqlLineageService
DevelopmentTaskValidationException
SqlColumnLineageParser
SqlTableLineageParser
TableIdentityResolver
```

其中两个 Exception 是兼容 corridor；其余均为 Data Service / SQL Lineage 历史实现。`DevelopmentSqlProjectionLineageAnalyzer` 已迁入 `lineage.analysis`，不得以 compatibility wrapper 形式回流。

规则：

- 不得在该目录新增文件；
- 不得把新 Node/Task/Execution/Dataset/Release 功能放回这里；
- 修改 legacy 算法可以原地修 bug，但新增能力优先建立目标业务包；
- 后续迁移必须独立 PR，不与 SQL parser 行为变化混做。

## Persistence Boundary

Repository 是领域持久化 contract，DAO 是 MyBatis/JDBC primitive。Application roles 不直接依赖 DAO。

```text
application role -> repository -> dao
```

当前 Execution / Editor / Lineage Outbox 等历史 JDBC 边界属于已知工程债务；新增持久化能力不应继续复制这种模式，除非有明确设计理由并同步更新 `DEPENDENCIES.md`。

## Architecture Guards

架构 contract 使用测试固化：

```text
DataDevelopmentArchitectureDocumentationTest
  -> six module docs exist and cross-link

DataDevelopmentDependencyBoundaryTest
  -> controller cannot enter repository/dao
  -> domain purity
  -> package graph cycle check
  -> frozen service allowlist

DataDevelopmentRoleConventionTest
  -> stable @Service entries
  -> Analyzer / Parser / Worker 等技术角色不伪装成 Service
  -> moved roles cannot return as compatibility wrappers
  -> no new broad business buckets

DataDevelopmentGovernanceContractTest
  -> every Data Development controller is PROJECT_REQUIRED + READ
  -> EDIT / DELETE / EXECUTE / PUBLISH / RELEASE stay explicit

DataDevelopmentDomainModelPlacementTest
  -> Release / Execution read models cannot return to core domain
  -> read models must stay with their owning subsystem
```

如果架构规则真的变化，同一个 PR 中同时修改文档、测试和代码；不要因为护栏报错就删除护栏。
