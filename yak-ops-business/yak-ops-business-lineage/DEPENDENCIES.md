# Lineage Dependencies

本文定义 Lineage 的 package 依赖方向与跨模块 corridor。

## Target Dependency Graph

长期方向保持单向、可解释：

```text
controller
    ↓
service
    ├──────────────→ analysis ─────→ domain
    ├──────────────→ collector ────→ domain
    │                    └─────────→ analysis
    ├──────────────→ repository ───→ domain
    │                    ↓
    │                   dao
    └──────────────→ domain

config  <- infrastructure-only configuration
```

建议依赖矩阵：

| Source | May depend on |
| --- | --- |
| `controller` | `service`, transport-local converter/dto/vo |
| `service` | `domain`, `analysis`, `collector`, `repository` |
| `analysis` | JDK, `domain` when a shared domain value is required |
| `collector` | `domain`, `analysis`, stable service contract |
| `repository` | `domain`, `dao`, `config`, `repository.support` |
| `dao` | `config`, DAO-local mapper/model/support |
| `domain` | no Lineage application/infrastructure package |
| `config` | configuration-only dependencies |

目标图不得形成 `domain -> repository -> service`、`dao -> repository` 或其他反向依赖。

## Current Structure

```text
controller -> service -> repository -> domain
                              └-----> dao

analysis/sql
  -> source-neutral SQL projection contract
```

根包没有 production Java 类型。Service 不直接依赖 DAO/Mapper/PO，跨模块调用方不能引用旧根包 Service、Domain 或 Analyzer。

## SQL Analysis Corridors

共享 contract：

```text
io.yak.ops.business.lineage.analysis.sql.SqlProjectionLineageAnalyzer
```

允许的真实实现与消费边界：

```text
Data Development lineage/analysis implementation
    -> shared SQL analysis contract

Dataset gateway/lineage adapter
    -> shared SQL analysis contract
```

Lineage 模块不能为了复用 parser 反向依赖 Data Development。Dataset 也不能直接依赖 Data Development parser；它只依赖自身 Gateway 和共享 contract。

## Collector Corridors

当前没有 Collector production implementation，也不以空目录或空接口模拟未来架构。

出现 Flink / Spark / Hadoop 等真实采集能力时，平台依赖只能进入对应 `collector/<platform>` adapter。Collector 不得把 SDK 类型暴露给 Domain、Service 或 Repository contract。

## Forbidden Shortcuts

以下依赖不允许出现：

```text
controller -> repository / dao / mapper / PO
service -> dao / mapper / PO
analysis -> service / repository / dao / controller / Spring
repository -> controller / DTO / VO
dao -> controller / service / repository / domain orchestration
domain -> Spring / MyBatis / repository / controller / analysis
```

HTTP transport mapping 留在 `controller`；持久化兼容转换留在 `repository`/`dao`；外部平台 SDK 留在 `analysis`/`collector` 的边界实现。

## External Module Dependencies

跨业务模块复用 Lineage 时，依赖稳定公开 contract，不调用对方 DAO 或内部实现类。

具体 SQL parser 由 Data Development 持有；Lineage 只持有 source-neutral Analyzer contract。未来平台采集器同样由 adapter 所在模块持有技术实现，统一回到 Lineage Domain 和写入边界。

## Maven Boundary

保持单一 `yak-ops-business-lineage` Maven module，不提前拆 `lineage-core / lineage-api / lineage-flink / lineage-spark`。

只有当出现真实的编译期隔离需求，例如某个平台 SDK 体积大、依赖冲突明显、需要独立发布或可选装载时，才考虑拆 Maven artifact。先把 Java 依赖方向拆清楚，再决定物理 jar 边界。

## Governance

`LineageArchitectureTest` 保护反射可见的层次语义；`LineageDependencyBoundaryTest` 扫描 production source，保护：

- 根包保持空白；
- top-level package 必须经过声明；
- 稳定 Service 集合固定；
- SQL Analyzer contract 只能位于 `analysis/sql`；
- Analysis 保持 source-neutral、无 Spring/持久化依赖；
- Controller 不穿透持久化；
- Repository 不依赖 HTTP contract；
- DAO 不反向依赖上层；
- Domain 保持 framework/persistence free；
- 旧根包 Service、Domain 和 Analyzer import 不能回流；
- `common/helper/utils/base` 业务大桶不能回流。
