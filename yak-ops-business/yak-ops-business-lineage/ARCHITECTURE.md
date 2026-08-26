# Lineage Architecture

本文定义 Lineage 当前有效的长期架构 contract：package 表达职责，稳定 Service 作为应用入口，专业能力以角色命名，外部技术停在边界。

需求看 [`REQUIREMENTS.md`](./REQUIREMENTS.md)，领域硬规则看 [`DOMAIN.md`](./DOMAIN.md)，依赖矩阵看 [`DEPENDENCIES.md`](./DEPENDENCIES.md)，评审标准看 [`REVIEW.md`](./REVIEW.md)。

## Design Principles

1. **Domain first.** Asset、Relation、Evidence、Graph 不被 HTTP、MyBatis 或平台 SDK 污染。
2. **Stable entry, specialized roles.** Controller 和邻接模块进入稳定 Service 或明确的 Analyzer contract。
3. **One lineage model.** Dataset、SQL Task、Flink、Spark、Hadoop 不各建一套 Asset/Relation。
4. **External technology stops at boundary.** Parser、SDK、CLI、HTTP client、PO 不能穿透到 Domain。
5. **Read does not mutate.** Graph/query side 不因读取失败反向改写领域事实。
6. **Active structure is exact.** 文档和测试只声明真实存在的 package，不用未来占位白名单制造虚假架构。
7. **Architecture is executable.** Package、公共 API、Maven、代码风格和文档 contract 都由测试保护。

## Active Package Map

```text
io.yak.ops.business.lineage
├── analysis
│   └── sql             # source-neutral SQL projection contract
├── config              # persistence enablement and Spring wiring
├── controller
│   └── v1              # HTTP inbound + DTO/VO/converter
├── dao
│   ├── impl
│   ├── mapper
│   └── model           # MyBatis primitives and PO
├── domain              # framework-free facts and value objects
├── repository
│   └── support         # persistence contract, adapter and codecs
└── service             # stable query/write/maintenance facades
```

这七个 top-level package 是精确集合。新增或删除 package 必须同步修改架构文档与 dependency guard。

`common / helper / utils / base` 不能成为新业务大桶。根包也不能承担兼容层。

## Application Boundary

当前稳定应用入口是：

```text
LineageController
    ├── LineageQueryService
    └── LineageWriteService

neighboring module adapters / internal publishers
    ├── LineageQueryService
    ├── LineageWriteService
    └── LineageMaintenanceService
```

职责固定：

- Query：资产定位、搜索和有界图遍历；
- Write：资产/关系注册与批量写入；
- Maintenance：evidence replacement、清理和 revision guard。

`@Service` 只用于这三个稳定入口。Analyzer、RepositoryAdapter、DAO、Converter 等角色不通过 `*Service` 掩盖职责。

## Public API Boundary

邻接业务模块可直接 import 的类型根只有：

```text
io.yak.ops.business.lineage.analysis.sql.SqlProjectionLineageAnalyzer

io.yak.ops.business.lineage.domain.LineageAsset
io.yak.ops.business.lineage.domain.LineageAssetType
io.yak.ops.business.lineage.domain.LineageDirection
io.yak.ops.business.lineage.domain.LineageGraph
io.yak.ops.business.lineage.domain.LineageRelation
io.yak.ops.business.lineage.domain.LineageRelationType

io.yak.ops.business.lineage.service.LineageQueryService
io.yak.ops.business.lineage.service.LineageWriteService
io.yak.ops.business.lineage.service.LineageMaintenanceService
```

公开嵌套命令、结果和 scope 跟随所属 Service/Analyzer 类型根。

以下 package/type 即使因 Spring、MyBatis 或 Java 编译需要声明为 `public`，也仍是模块内部实现：

```text
controller/*
config/*
dao/*
repository/*
domain/LineageAssetDraft
domain/LineageRelationDraft
```

跨模块调用方必须通过自身 Gateway/Adapter 隔离 Lineage contract，不能把 Lineage 类型扩散到自己的核心 Domain。

## Domain Boundary

`domain/` 只保存资产、关系、图和必要值对象，不依赖：

```text
Spring MVC / Spring Service
MyBatis / Mapper / PO
Controller DTO / VO
Repository / DAO
SQL parser / platform SDK
```

Draft 是 Repository/Service 写入过程的内部表达，不属于邻接模块公共 API。

## Analysis Boundary

共享 SQL projection contract 位于：

```text
analysis/sql/SqlProjectionLineageAnalyzer
```

它只描述 source-neutral 输入输出，不依赖 Spring、Repository、DAO、Data Development 或具体 parser library。

真实实现位于拥有 parser 的邻接上下文：

```text
data-development
└── lineage/analysis/DevelopmentSqlProjectionLineageAnalyzer
        -> local SQL lineage parser
        -> shared Analyzer contract
```

依赖方向固定：

```text
Dataset -> Lineage Analyzer contract
Data Development -> Lineage Analyzer contract
Lineage -X-> Data Development parser implementation
```

## Persistence Boundary

```text
Service
   ↓
LineageRepository
   ↓
LineageRepositoryAdapter
   ↓
LineageDao
   ↓
Mapper / PO / MyBatis / DB
```

固定规则：

- Repository contract 不暴露 DAO model、Mapper、Controller DTO/VO；
- Service 不直接依赖 DAO、Mapper、PO 或 persistence config；
- DAO 不反向依赖 Service、Repository 或 Domain orchestration；
- RepositoryAdapter 是 Domain 与 Persistence 的转换位置；
- JSON/compatibility codec 留在 `repository.support`。

## Persistence Configuration Corridor

Lineage 通过自己的条件注解表达装配语义：

```text
ConditionalOnLineagePersistence
    -> ConditionalOnDataSourceEnabled

LineagePersistenceConfiguration
    -> BusinessDatabaseConfiguration
    -> DataSourceProperties
```

只有上述 `config` 文件可以直接 import Datasource 模块。DAO 只依赖 Lineage-owned condition，不感知 Datasource 的 enablement 类型。

## Extension Protocol

当前没有活动的 Collector package。未来引入 Flink、Spark、Hadoop 或其他来源时，只有真实采集协议和调用链已经存在，才新增：

```text
collector/<platform>/<RoleImplementation>
```

首个 Collector PR 必须同时：

1. 定义 evidence ownership、重放和 replacement 语义；
2. 将 SDK/event 类型限制在 adapter 内；
3. 复用现有 Domain 与 Write/Maintenance 边界；
4. 更新精确 package 集合和依赖图；
5. 评估是否需要新增公共 contract；
6. 增加真实行为、失败和重复事件测试。

禁止提前创建空 Collector、空 Resolver 或 `flink/domain`、`spark/service` 等平行业务层。

## Root Package Rule

`io.yak.ops.business.lineage` 根包必须保持空白，不放 production Java 类型。

架构测试使用结构规则阻止任何 `io.yak.ops.business.lineage.<UppercaseType>` 引用回流，不再维护按类名枚举的临时黑名单。

## Executable Guards

```text
LineageArchitectureTest
  -> reflection-visible layering and domain serialization rules

LineageDependencyBoundaryTest
  -> exact active package set
  -> declared/actual graph acyclic
  -> package corridors and root-package structural ban

LineageMavenDependencyBoundaryTest
  -> direct dependency set and runtime capability owner

LineagePublicApiBoundaryTest
  -> exact cross-module type roots and signature purity

LineageCodeStyleConventionTest
  -> package/path, public type/file and role placement conventions

LineageDocumentationContractTest
  -> exact document set, cross-links and final-contract vocabulary
```

## Change Rules

1. 一个 PR 一个主要边界或行为关注点；
2. package move 与行为修改分开；
3. 新入口稳定后不保留 production 双入口；
4. 新 package、新公共类型和新 Maven dependency 必须有明确真实调用方；
5. Maven 子模块拆分由 SDK 隔离、依赖冲突、可选装载或独立发布需求驱动；
6. 架构真的变化时，同一个 PR 同时修改代码、文档和 guard；
7. 不允许为了让测试通过而扩大白名单或降低精确检查。
