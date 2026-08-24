# Data Development Architecture

本文件定义 Data Development 的长期结构 contract。需求看 `REQUIREMENTS.md`，领域硬规则看 `DOMAIN.md`，依赖方向看 `DEPENDENCIES.md`，Review 看 `REVIEW.md`。

## 设计原则

1. **业务子系统优先。** package 要能表达 Node、Task、Execution、Dataset、Release、Lineage 等业务角色。
2. **Service 是入口角色，不是分层目录。** 允许稳定 Application Service，禁止通用 Service 大桶。
3. **名字表达职责。** Manager / Publisher / Validator / Normalizer / Coordinator / Resolver / Parser / Analyzer / Reader / Gateway / Worker 各自只承担对应角色。
4. **Truth 只有一个 owner。** Node、Draft、Revision、Execution、Task Catalog projection、Lineage evidence 不互相冒充。
5. **结构重构不偷改行为。** package move 与 REST / DB / Domain semantic change 分开。
6. **外部系统停在边界。** Task Runtime、Task Catalog、Dataset、Data Service Runtime、Lineage、DataSource 都是邻接上下文。
7. **架构必须可执行。** 文档由 architecture tests 和固定 legacy allowlist 守住。

## Package Map

```text
io.yak.ops.business.development
├── controller          # HTTP inbound only
├── node                # Node identity / metadata lifecycle
├── directory           # workspace directory lifecycle
├── task                # Draft / validation / immutable Revision / Task Catalog projection
├── execution           # editor manual run + execution history
├── dataset             # Dataset output-node application boundary
├── release             # published Task Catalog read/activation boundary
├── editor              # editor preference boundary
├── lineage             # outbox / worker / write transaction orchestration
├── domain              # core values and immutable facts
├── repository          # persistence contracts + adapters
├── dao                 # MyBatis persistence primitives
├── config              # module configuration
└── service             # frozen legacy Data Service / SQL lineage algorithm island
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

## Stable Application Entries

Stage 2 的新稳定入口按业务包放置：

```text
node.DevelopmentNodeService
directory.DevelopmentDirectoryService
task.DevelopmentTaskService
execution.DevelopmentTaskRunService
execution.DevelopmentTaskExecutionService
dataset.DevelopmentDatasetNodeService
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
```

Editor Run 使用临时 `TaskVersionSnapshot(version=0)`。它不是 Publish，也不能创建 immutable Revision。

Execution history 是 Data Development 的运行记录；真正的 executor runtime state 仍由共享 Task Runtime 提供。

## Node / Directory / Dataset / Release

这些能力不再放在通用 service 包：

```text
node      -> Node identity / rename / delete / updater metadata
directory -> hierarchy / path / empty-delete rule
dataset   -> Dataset-owned datasource + SQL + field contract
release   -> Task Catalog release projection + online/offline/activate
editor    -> user editor settings
```

`Release` 读取 Task Catalog projection 与 immutable Task Revision，但不能修改历史 Revision。

## Lineage Boundary

```text
Task publish
   -> DevelopmentLineageOutbox
   -> DevelopmentLineageWorker
   -> legacy SQL parser/analyzer preparation
   -> DevelopmentLineageWriteTransaction
   -> yak-ops-business-lineage
```

Outbox / Worker / transaction orchestration 已归 `lineage`。SQL Parser / Analyzer 的大算法当前继续留在 frozen legacy island，避免纯 package move 与算法变化混在同一 PR。

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
DevelopmentSqlProjectionLineageAnalyzer
DevelopmentTaskValidationException
SqlColumnLineageParser
SqlTableLineageParser
TableIdentityResolver
```

其中两个 Exception 是兼容 corridor；其余均为 Data Service / SQL Lineage 历史实现。

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

Stage 2 使用 architecture tests 固化：

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
  -> technical roles do not masquerade as Service
  -> no new broad business buckets
```

如果架构规则真的变化，同一个 PR 中同时修改文档、测试和代码；不要因为护栏报错就删除护栏。
