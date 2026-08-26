# Lineage

Lineage 是 Yak Ops 的统一元数据血缘模块，负责维护可稳定寻址的资产、带来源证据的有向关系，以及有边界的上下游图查询。

## Read First

本目录只维护当前有效 contract。历史迁移过程以 Git 和 Pull Request 为准，不进入长期架构文档。

| Document | Answers |
| --- | --- |
| [`REQUIREMENTS.md`](./REQUIREMENTS.md) | 模块提供什么能力，哪些事情不属于 Lineage |
| [`DOMAIN.md`](./DOMAIN.md) | Asset、Relation、Evidence、Graph 的领域语义 |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 当前 package、公共入口与持久化边界 |
| [`DEPENDENCIES.md`](./DEPENDENCIES.md) | package、Maven 与跨模块依赖方向 |
| [`REVIEW.md`](./REVIEW.md) | Lineage 变更的评审和拒绝标准 |
| [`CODE_STYLE.md`](../../CODE_STYLE.md) | Yak Ops 仓库统一工程规范 |

## Current Contract

```text
HTTP / neighboring module
          ↓
Query / Write / Maintenance Service
          ↓
      Repository
          ↓
         DAO
          ↓
 MyBatis / Flyway / business database
```

SQL projection analysis 是独立的 source-neutral contract：

```text
analysis/sql/SqlProjectionLineageAnalyzer
```

具体 SQL parser 实现由拥有 parser 的 Data Development 模块提供；Dataset 只通过自身 Gateway Adapter 使用共享 contract。

## Active Package Shape

当前生产代码只允许以下七个 top-level package：

```text
io.yak.ops.business.lineage
├── analysis            # source-neutral analysis contracts
├── config              # persistence enablement and wiring
├── controller          # HTTP inbound + DTO/VO mapping
├── dao                 # MyBatis primitives, mapper and PO
├── domain              # framework-free lineage facts
├── repository          # domain persistence contract + adapter
└── service             # stable query/write/maintenance entries
```

根包不放 production Java 类型。`common / helper / utils / base` 不能成为新的业务大桶。

## Public API

邻接业务模块只允许编译依赖以下类型根：

```text
analysis.sql.SqlProjectionLineageAnalyzer

domain.LineageAsset
domain.LineageAssetType
domain.LineageDirection
domain.LineageGraph
domain.LineageRelation
domain.LineageRelationType

service.LineageQueryService
service.LineageWriteService
service.LineageMaintenanceService
```

上述 Service 与 Analyzer 的公开嵌套类型也属于对应类型根。`LineageAssetDraft`、`LineageRelationDraft`、Repository、DAO、Config、Controller 均为模块内部实现，不是跨模块 contract。

## Core Model

```text
LineageAsset
    │
    │ upstream source -> downstream target
    ▼
LineageRelation
    │
    └── evidence(sourceType + sourceId + version + observedAt)

LineageGraph = root + direction + bounded depth + assets + relations
```

关系方向始终是上游资产指向下游资产。查询层不能为了页面展示反转领域事实。

## Extension Protocol

当前没有活动的 `collector` package，也没有 Flink、Spark、Hadoop 占位接口。

当真实平台事件、SDK 或采集协议出现时，新增 Collector 的同一个 PR 必须同时完成：

- 明确 Collector 的输入、输出和 evidence ownership；
- 将平台 SDK 限制在 adapter 边界；
- 复用统一 Domain 与稳定写入入口；
- 更新精确 package 图、依赖图、公共 API 与架构测试；
- 增加真实行为测试，而不是只创建空目录或空接口。

## Architecture Guards

长期 contract 由以下测试保护：

```text
LineageArchitectureTest
LineageDependencyBoundaryTest
LineageMavenDependencyBoundaryTest
LineagePublicApiBoundaryTest
LineageCodeStyleConventionTest
LineageDocumentationContractTest
```

新增 package、跨模块类型或 Maven dependency 时，代码、文档和对应 guard 必须在同一个 PR 中一起变化。
