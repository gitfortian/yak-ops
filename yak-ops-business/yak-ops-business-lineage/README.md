# Lineage

Lineage 是 Yak Ops 的统一元数据血缘模块，负责维护可稳定寻址的元数据资产、带来源证据的有向关系，以及受限深度的上下游图查询。

## Read First

本目录只维护**当前有效 contract**，历史迁移过程以 Git / PR 为准。

| Document | Answers |
| --- | --- |
| [`REQUIREMENTS.md`](./REQUIREMENTS.md) | 模块需要提供什么能力、哪些事情不在这里做 |
| [`DOMAIN.md`](./DOMAIN.md) | Asset / Relation / Evidence / Graph 的领域语义 |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 长期 package、角色与持久化边界 |
| [`DEPENDENCIES.md`](./DEPENDENCIES.md) | package 可以依赖谁、跨模块 corridor 是什么 |
| [`CODE_STYLE.md`](../../CODE_STYLE.md) | Yak Ops 仓库统一工程与代码规范 |
| [`REVIEW.md`](./REVIEW.md) | Lineage PR 的评审标准 |

## Current Contract

当前生产代码已形成：

```text
controller
   -> query / write / maintenance service
   -> repository
   -> dao

analysis/sql
   -> source-neutral SQL projection analysis contract
```

共享分析契约的完整角色路径是：

```text
analysis/sql/SqlProjectionLineageAnalyzer
```

Asset / Relation / Graph 位于 `domain`，稳定应用入口位于 `service`。具体 SQL parser 实现在 Data Development 的 Lineage 角色包中，Dataset 只通过自身 Gateway Adapter 使用该 contract。

`io.yak.ops.business.lineage` 根包不再承载 production Java 类型，也不作为兼容大桶。

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
├── repository          # persistence contracts + adapters
├── dao                 # MyBatis persistence primitives / PO
└── config              # module configuration
```

技术名称不直接决定业务层级。未来 Flink、Spark、Hadoop 等能力只有在存在真实采集链路时，才进入 `collector/<platform>`；它们复用统一 Domain 和稳定写入边界，不复制 Service / DAO / Domain。

## Evolution Rule

后续重构遵守三条底线：

1. package move 与业务行为修改分开；
2. 新入口稳定后不长期保留 production 双入口；
3. 架构文档、dependency test 与实际代码一起演进，不能为了让测试通过无条件扩大依赖白名单。
