# Lineage

Lineage 是 Yak Ops 的统一元数据血缘模块，负责维护可稳定寻址的元数据资产、带来源证据的有向关系，以及受限深度的上下游图查询。

## Read First

本目录只维护**当前有效 contract**，历史迁移过程以 Git / PR 为准。

| Document | Answers |
| --- | --- |
| [`REQUIREMENTS.md`](./REQUIREMENTS.md) | 模块需要提供什么能力、哪些事情不在这里做 |
| [`DOMAIN.md`](./DOMAIN.md) | Asset / Relation / Evidence / Graph 的领域语义 |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 长期 package、角色与持久化边界 |
| [`DEPENDENCIES.md`](./DEPENDENCIES.md) | package、跨模块 corridor 与 Maven 依赖归属 |
| [`CODE_STYLE.md`](../../CODE_STYLE.md) | Yak Ops 仓库统一工程与代码规范 |
| [`REVIEW.md`](./REVIEW.md) | Lineage PR 的评审标准 |

## Current Contract

当前生产代码形成两条明确路径：

```text
HTTP / module caller
   -> query / write / maintenance service
   -> repository
   -> dao
   -> shared business database

Dataset / Data Development
   -> analysis/sql/SqlProjectionLineageAnalyzer
```

Asset / Relation / Graph 位于 `domain`，稳定应用入口位于 `service`，共享 SQL 分析契约位于 `analysis/sql`。具体 SQL parser 实现在 Data Development 的 Lineage 角色包中，Dataset 只通过自身 Gateway Adapter 使用该 contract。

`io.yak.ops.business.lineage` 根包不承载 production Java 类型，也不作为兼容大桶。

## Persistence Contract

```text
LineageRepository
   -> LineageRepositoryAdapter
   -> LineageDao
   -> Mapper / PO / XML
```

Datasource 模块只允许从 `config` corridor 进入 Lineage：

```text
ConditionalOnLineagePersistence
LineagePersistenceConfiguration
```

DAO 依赖 Lineage 自己的条件注解，不直接感知 Datasource 模块。Repository contract 不暴露 PO、Mapper、HTTP DTO 或 MyBatis 类型。

## Maven Contract

Lineage 只声明自身编译所需的 API 和实现边界：Web/Validation、Spring Transaction、MyBatis、Flyway Core、Swagger annotations。

以下运行时能力不由 Lineage 向下游传播：

```text
Datasource module            -> MyBatis JSqlParser + Flyway MySQL runtime
explicit app/plugin assembly -> JDBC drivers + Springdoc UI
```

本阶段只锁定 Lineage 的直接依赖面，不顺手统一其他业务模块已有的持久化 POM。

Lineage 对 Datasource 的 Maven 依赖是 optional。任何直接消费 Lineage 的应用或业务模块，都必须显式声明 Datasource，而不能依赖传递引入。

## Core Model

```text
LineageAsset
    │
    │ source -> target
    ▼
LineageRelation
    │
    └── evidence(sourceType + sourceId + version + observedAt)

LineageGraph = root + direction + bounded depth + assets + relations
```

关系方向固定为**上游资产 -> 下游资产**。`READS_FROM / WRITES_TO / DERIVES_FROM / CONSUMES / CONTAINS` 描述下游如何使用、派生或包含上游。

## Package Shape

```text
io.yak.ops.business.lineage
├── controller          # HTTP inbound + transport mapping
├── service             # stable query / write / maintenance facades
├── domain              # framework-free lineage domain/value objects
├── analysis
│   └── sql             # source-neutral SQL analysis contract
├── collector           # real platform/source collectors when required
├── repository          # persistence contracts + domain/DAO adapter
│   └── support         # persistence-only codecs
├── dao                 # MyBatis primitives / mapper / PO
└── config              # persistence wiring + external infrastructure corridor
```

技术名称不直接决定业务层级。未来 Flink、Spark、Hadoop 等能力只有在存在真实采集链路时，才进入 `collector/<platform>`；它们复用统一 Domain 和稳定写入边界，不复制 Service / DAO / Domain。

## Evolution Rule

后续演进遵守三条底线：

1. package、POM 与业务行为修改分开评审；
2. 外部模块依赖只能进入文档和测试声明的窄 corridor；
3. 新 dependency 必须有真实 owner，不能为了临时编译把驱动、UI 或平台 SDK 塞进 Lineage。
