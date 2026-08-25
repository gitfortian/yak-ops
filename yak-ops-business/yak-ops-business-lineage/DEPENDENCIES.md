# Lineage Dependencies

本文定义 Lineage 的 package 依赖方向与当前过渡 corridor。

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
| `controller` | `service`, transport-local mapper/dto/vo |
| `service` | `domain`, `analysis`, `collector`, `repository` |
| `analysis` | `domain` |
| `collector` | `domain`, `analysis` |
| `repository` | `domain`, `dao`, `config`, `repository.support` |
| `dao` | `config`, DAO-local mapper/model/support |
| `domain` | no Lineage application/infrastructure package |
| `config` | configuration-only dependencies |

目标图不得形成 `domain -> repository -> service`、`dao -> repository` 或其他反向依赖。

## Current Transitional Corridors

Stage 1 不移动 production class，因此当前存在两个明确债务：

```text
controller -> root LineageService
root LineageService / LineageMaintenanceService -> repository
repository adapter -> root Asset / Relation domain records
```

根包同时放着 Domain、Service 和 Analyzer，使完整 package graph 暂时无法表达为无环图。这个状态被 `LineageDependencyBoundaryTest` 显式冻结：**债务可以减少，不能继续增长**。

后续顺序：

```text
Domain move
  -> Service facade split
  -> Analyzer / Collector role placement
  -> tighten dependency matrix
```

## Forbidden Shortcuts

以下依赖不允许出现：

```text
controller -> repository / dao / mapper / PO
service -> dao / mapper / PO
repository -> controller / DTO / VO
dao -> controller / service / repository / domain orchestration
domain -> Spring / MyBatis / repository / controller
```

HTTP transport mapping 留在 `controller`；持久化兼容转换留在 `repository`/`dao`；外部平台 SDK 留在 `analysis`/`collector` 的边界实现。

## External Module Dependencies

Lineage 当前可以依赖 Yak Ops 公共能力与 datasource 业务模块，但不能为了 SQL lineage 直接反向依赖 data-development 的具体 parser/runtime 实现。

跨业务模块复用 Lineage 时，优先依赖稳定公开 contract，不允许调用对方 DAO 或内部实现类。

## Maven Boundary

Stage 1 保持单一 `yak-ops-business-lineage` Maven module，不提前拆 `lineage-core / lineage-api / lineage-flink / lineage-spark`。

只有当出现真实的编译期隔离需求，例如某个平台 SDK 体积大、依赖冲突明显、需要独立发布或可选装载时，才考虑拆 Maven artifact。先把 Java 依赖方向拆清楚，再决定物理 jar 边界。

## Governance

`LineageArchitectureTest` 保护反射可见的层次语义；`LineageDependencyBoundaryTest` 扫描 production source，保护：

- 根包过渡债务不增长；
- top-level package 必须经过声明；
- Controller 不穿透持久化；
- Repository 不依赖 HTTP contract；
- DAO 不反向依赖上层；
- 未来 Domain package 保持 framework/persistence free；
- `common/helper/utils/base` 业务大桶不能回流。
