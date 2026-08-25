# Lineage

Lineage 是 Yak Ops 的统一元数据血缘模块，负责维护可稳定寻址的元数据资产、带来源证据的有向关系，以及受限深度的上下游图查询。

## Read First

本目录只维护**当前有效 contract**，历史迁移过程以 Git / PR 为准。

| Document | Answers |
| --- | --- |
| [`REQUIREMENTS.md`](./REQUIREMENTS.md) | 模块需要提供什么能力、哪些事情不在这里做 |
| [`DOMAIN.md`](./DOMAIN.md) | Asset / Relation / Evidence / Graph 的领域语义 |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 长期 package、角色与持久化边界 |
| [`DEPENDENCIES.md`](./DEPENDENCIES.md) | package 可以依赖谁、当前过渡债务是什么 |
| [`CODE_STYLE.md`](../../CODE_STYLE.md) | Yak Ops 仓库统一工程与代码规范 |
| [`REVIEW.md`](./REVIEW.md) | Lineage PR 的评审标准 |

## Current Contract

当前生产代码已经具备 `controller -> application service -> repository -> dao` 的基础分层，并把 MyBatis PO 与 HTTP DTO 隔离在边界之外。Stage 2 已将 Asset / Relation / Graph 及相关枚举、Draft 收拢到 `io.yak.ops.business.lineage.domain`；稳定 Service 和 SQL 分析接口仍暂时保留在根包。

Stage 1 建立长期架构合同和可执行护栏，Stage 2 只完成 Domain package 迁移与引用修复，**不修改 REST、数据库、Flyway、领域行为与持久化语义**。后续阶段继续按合同消除剩余根包过渡债务，而不是一次性重写。

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

## Planned Package Shape

```text
io.yak.ops.business.lineage
├── controller          # HTTP inbound + transport mapping
├── service             # stable application facades
├── domain              # framework-free lineage domain/value objects
├── analysis            # source-neutral lineage analysis roles
├── collector           # Flink/Spark/Hadoop 等来源的采集角色
├── repository          # persistence contracts + adapters
├── dao                 # MyBatis persistence primitives / PO
└── config              # module configuration
```

技术名称不直接决定业务层级。Flink、Spark、Hadoop 等实现应挂在明确角色下面，例如 `collector/flink`，而不是各自形成一套 Service / DAO / Domain。

## Evolution Rule

后续重构遵守三条底线：

1. package move 与业务行为修改分开；
2. 新入口稳定后不长期保留 production 双入口；
3. 架构文档、dependency test 与实际代码一起演进，不能为了让测试通过无条件扩大依赖白名单。
