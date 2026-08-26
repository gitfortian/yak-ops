# Lineage Dependencies

本文定义 Lineage 当前有效的 package、跨模块和 Maven 依赖方向。它描述真实结构，不把未来扩展当作活动白名单。

## Active Dependency Graph

```text
controller
    ├──────────────→ service
    └──────────────→ domain

service
    ├──────────────→ analysis
    ├──────────────→ repository
    └──────────────→ domain

analysis ──────────→ domain
repository ────────→ dao
repository ────────→ domain
dao ───────────────→ config

config
domain
```

允许矩阵：

| Source | May depend on |
| --- | --- |
| `controller` | `service`, `domain`, transport-local dto/vo/converter |
| `service` | `analysis`, `repository`, `domain` |
| `analysis` | `domain` |
| `repository` | `dao`, `domain`, `repository.support` |
| `dao` | `config`, DAO-local mapper/model |
| `config` | no other Lineage top-level package |
| `domain` | no Lineage application/infrastructure package |

实际 top-level package 集合必须与矩阵键完全一致，且声明图和 import 图都必须无环。

## Public API Corridors

邻接业务模块只允许直接编译依赖以下类型根：

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

允许嵌套类型跟随所属根类型，例如 `LineageWriteService.RegisterAssetCommand`。

禁止跨模块 import：

```text
controller/*
config/*
dao/*
repository/*
domain.LineageAssetDraft
domain.LineageRelationDraft
```

当前真实 corridor：

```text
Data Development lineage/analysis
    -> SqlProjectionLineageAnalyzer

Dataset gateway/lineage
    -> Analyzer + Query/Write/Maintenance Service + selected Domain

Analysis gateway/lineage
    -> Query/Write/Maintenance Service + selected Domain

Dashboard gateway/lineage
    -> Query/Write/Maintenance Service + selected Domain
```

邻接模块应继续通过自身 Gateway/Adapter 使用这些类型，不把 Lineage 依赖散落到 Controller、DAO 或核心 Domain。

## SQL Analysis Corridor

```text
io.yak.ops.business.lineage.analysis.sql.SqlProjectionLineageAnalyzer
```

固定方向：

```text
Data Development implementation -> shared contract
Dataset adapter                 -> shared contract
Lineage                          -X-> Data Development parser
```

共享 contract 不携带 Spring、Repository、DAO、Parser SDK 或调用方 Domain 类型。

## Persistence Corridor

```text
Service -> LineageRepository -> LineageRepositoryAdapter -> LineageDao
```

固定限制：

```text
controller -X-> repository / dao
service    -X-> dao / mapper / PO / config
analysis   -X-> Spring / service / repository / dao / config
repository -X-> controller / DTO / VO
dao        -X-> controller / service / analysis / repository / domain orchestration
domain     -X-> Spring / MyBatis / controller / service / repository / dao / analysis
```

## External Business Corridor

Lineage 只允许通过两个配置文件直接进入 Datasource：

```text
config/ConditionalOnLineagePersistence.java
    -> ConditionalOnDataSourceEnabled

config/LineagePersistenceConfiguration.java
    -> BusinessDatabaseConfiguration
    -> DataSourceProperties
```

DAO 和 Repository 不能直接 import `io.yak.ops.business.datasource.*`。

## Maven Boundary

Lineage 保持单一 Maven module，不提前拆 `lineage-core / lineage-api / lineage-flink / lineage-spark`。

直接依赖只承担源码编译和 Lineage 自身实现所需能力：

- Datasource 作为 optional persistence provider；
- Spring Web/Validation 用于当前 HTTP contract；
- Spring TX 用于稳定 Service transaction boundary；
- MyBatis-Plus starter 用于 DAO；
- Flyway Core 用于模块 migration；
- Swagger annotations 用于源码注解；
- Jackson/Lombok/Test 依赖按当前实现保留。

数据库 driver、Flyway database extension 和 JSqlParser runtime 由 Datasource/应用装配侧提供，不通过 Lineage 向所有消费者传播。

拆分 Maven artifact 只能由真实需求驱动，例如：

- 平台 SDK 体积或依赖冲突；
- 可选装载；
- 独立发布；
- 需要在无 Spring/无 Persistence 环境复用 contract。

## Extension Corridor

当前没有 `collector` top-level package，也没有活动依赖边。

首个真实 Collector 进入时，同一个 PR 必须明确并更新：

```text
new package
new dependency edge
evidence ownership
platform SDK boundary
public API impact
behavior tests
documentation and executable guards
```

不能先扩白名单，再等待未来实现补齐。

## Root Package Rule

以下结构永久禁止：

```text
io.yak.ops.business.lineage.LineageService
io.yak.ops.business.lineage.LineageAsset
io.yak.ops.business.lineage.<any production type>
```

根包类型禁令是结构规则，不再依赖有限的旧类名列表。公开 contract 必须归属 `analysis`、`domain` 或 `service` 的明确角色路径。

## Governance

- `LineageDependencyBoundaryTest`：精确 package 集合、依赖图、Datasource corridor、根包禁令；
- `LineageMavenDependencyBoundaryTest`：直接依赖集合、runtime provider、消费方显式装配；
- `LineagePublicApiBoundaryTest`：跨模块类型根和公开方法签名；
- `LineageCodeStyleConventionTest`：package/path、文件/类型和角色位置；
- `LineageDocumentationContractTest`：文档集合、链接和最终 contract。

新增 import 或 dependency 之前，先回答：

```text
Dependency Impact Analysis
- New edge:
- Owner of the target truth:
- Existing corridor or new corridor:
- Public API impact:
- Cycle impact:
- Maven/runtime impact:
- Documentation and guard updated: yes/no
```
