# Lineage Dependencies

本文定义 Lineage 的 package 依赖方向、跨模块 corridor 与 Maven 运行时归属。

## Final Dependency Graph

长期方向保持单向、可解释，并由源码测试验证无环：

```text
controller
    ↓
service
    ├──────────────→ analysis ─────→ domain
    ├──────────────→ collector ────→ domain
    │                    └─────────→ analysis
    ├──────────────→ repository ───→ domain
    │                    ↓
    │                   dao ───────→ config
    └──────────────→ domain

config -> declared external persistence corridor only
```

允许矩阵：

| Source | May depend on |
| --- | --- |
| `controller` | `service`, `domain`, transport-local converter/dto/vo |
| `service` | `domain`, `analysis`, `collector`, `repository` |
| `analysis` | `domain` |
| `collector` | `domain`, `analysis` |
| `repository` | `domain`, `dao`, `repository.support` |
| `dao` | `config`, DAO-local mapper/model/support |
| `config` | declared external persistence configuration only |
| `domain` | no Lineage application/infrastructure package |

目标图不得形成 `domain -> repository -> service`、`dao -> repository`、`collector -> service` 或其他反向依赖。

## Current Structure

```text
controller -> service -> repository -> domain
                              └-----> dao -> config

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

## Persistence Configuration Corridor

Lineage 唯一允许的外部 business-module import 是 Datasource 配置 corridor：

```text
config/ConditionalOnLineagePersistence.java
  -> io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled

config/LineagePersistenceConfiguration.java
  -> io.yak.ops.business.datasource.config.BusinessDatabaseConfiguration
  -> io.yak.ops.business.datasource.config.DataSourceProperties
```

DAO、Repository、Service、Controller、Domain、Analysis 不得直接 import Datasource。`LineageDaoImpl` 通过 Lineage 自己的 `ConditionalOnLineagePersistence` 接收装配条件。

该列表是精确白名单，不是 package 前缀放行。新增类型必须说明为何不能通过现有配置边界表达。

## Collector Corridors

当前没有 Collector production implementation，也不以空目录或空接口模拟未来架构。

出现 Flink / Spark / Hadoop 等真实采集能力时，平台依赖只能进入对应 `collector/<platform>` adapter。Collector 不得把 SDK 类型暴露给 Domain、Service 或 Repository contract，也不得反向调用稳定 Service 形成环。

## Forbidden Shortcuts

以下依赖不允许出现：

```text
controller -> repository / dao / mapper / PO
service -> dao / mapper / PO
analysis -> service / repository / dao / controller / Spring
collector -> service / repository / dao
dao -> controller / service / repository / domain orchestration
repository -> controller / DTO / VO
domain -> Spring / MyBatis / repository / controller / analysis
non-config package -> Datasource business module
```

HTTP transport mapping 留在 `controller`；持久化兼容转换留在 `repository`/`dao`；外部平台 SDK 留在 `analysis`/`collector` 的边界实现。

## Maven Direct Dependency Surface

Lineage POM 的直接依赖集合由 `LineageMavenDependencyBoundaryTest` 精确锁定：

```text
Yak Ops:
  yak-ops-common
  yak-ops-business-datasource (optional)

Framework/API:
  spring-boot-starter-web
  spring-boot-starter-validation
  spring-tx
  mybatis-plus-spring-boot3-starter
  swagger-annotations-jakarta
  flyway-core
  lombok (optional)
  spring-boot-starter-test (test)
```

新增直接 dependency 必须同步说明 capability owner、scope 和传递影响。

## Runtime Ownership

运行时依赖按实际装配职责归属：

```text
Datasource module
  -> mybatis-plus-jsqlparser-4.9 (runtime)
  -> flyway-mysql (runtime)

Explicit application/plugin assembly
  -> concrete JDBC drivers
  -> Springdoc UI runtime

Lineage
  -X-> database driver
  -X-> Flyway vendor module
  -X-> Springdoc UI runtime
  -X-> direct JSqlParser runtime
```

Lineage 对 Datasource 的依赖为 optional。仓库内任何直接依赖 Lineage 的 POM，必须同时显式依赖 Datasource；测试会扫描全部项目 POM，防止偶然依赖传递装配。

这里约束的是 Lineage 依赖面，不把其他业务模块已有的数据库依赖重复声明纳入本阶段；它们由各模块后续独立治理。

## Maven Module Boundary

保持单一 `yak-ops-business-lineage` Maven module，不提前拆 `lineage-core / lineage-api / lineage-flink / lineage-spark`。

只有出现真实的编译期隔离需求，例如某个平台 SDK 体积大、依赖冲突明显、需要独立发布或可选装载时，才考虑拆 Maven artifact。先把 Java 与 Maven 依赖方向锁清楚，再决定物理 jar 边界。

## Governance

`LineageArchitectureTest` 保护反射可见的层次语义；`LineageDependencyBoundaryTest` 与 `LineageMavenDependencyBoundaryTest` 保护：

- 根包保持空白；
- top-level package 必须经过声明；
- 声明图和实际 import 图都保持无环；
- 稳定 Service 与 Analysis role 集合固定；
- Analysis 保持 source-neutral；
- Datasource import 只进入精确的 config corridor；
- DAO 使用 Lineage-owned persistence condition；
- Controller、Service、Repository、DAO、Domain 不发生反向穿透；
- 旧根包 contract 与业务大桶不能回流；
- Lineage POM 直接依赖集合固定；
- JSqlParser 与 Flyway vendor 由 Datasource 提供，driver/UI 不由 Lineage 传播；
- Lineage 消费方显式装配 Datasource。
