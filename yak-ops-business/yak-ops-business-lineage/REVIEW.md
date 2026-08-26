# Lineage Review Guide

Lineage PR 评审优先看**领域语义、依赖方向和运行时 owner 有没有被破坏**，不是只看代码能否编译。

## Scope

- 这个 PR 是否只有一个主要关注点？
- 纯结构/POM 调整是否偷改了 REST、DB schema、Flyway migration 或业务语义？
- package、dependency ownership 与 behavior change 是否可以拆开？

## Domain

- Asset identity 是否仍然统一，不因技术来源产生第二套模型？
- Relation 是否始终表达 upstream source -> downstream target？
- Evidence 是否只说明来源，不冒充 Asset identity？
- replacement / revision ordering 是否保持保守和并发安全？
- Graph/query 是否仍然是只读、有边界的 projection？

## Roles

- 新类名是否能直接说明角色？
- `Service` 是否只用于稳定应用入口，而不是所有 Spring Bean？
- Analyzer / Collector / Resolver / Converter / Repository / DAO / Configuration 是否各自停在正确边界？
- SQL Analyzer contract 是否位于 `analysis/sql`，具体 parser 实现是否留在拥有 parser 的 adapter 模块？
- Flink / Spark / Hadoop 是否作为真实 Collector 实现接入，而不是复制一套业务层？
- 是否为了“看起来完整”提前新增了没有调用方的 Collector / Resolver 空抽象？

## Dependencies

- Controller 是否只走稳定 Service 和 transport mapping？
- Service 是否绕过 Repository 直接访问 DAO/Mapper/PO？
- Analysis contract 是否依赖 Spring、Repository、DAO、Controller 或具体 parser？
- Repository contract 是否暴露 HTTP 或 persistence implementation types？
- DAO 是否反向依赖应用层或直接 import Datasource？
- Domain 是否出现 Spring/MyBatis/平台 SDK？
- top-level package 图是否仍然符合声明且无环？
- 根包是否重新出现 production Java 类型？
- 是否新增了 `common/helper/utils/base` 之类的大桶？

## Persistence

- Domain ↔ PO 转换是否停在 RepositoryAdapter？
- MyBatis Mapper/XML 是否只承担数据库 primitive？
- JSON/compatibility codec 是否位于 persistence boundary，而不是 Core Domain？
- Datasource import 是否只存在于两个声明的 `config` 文件？
- DAO 是否通过 `ConditionalOnLineagePersistence` 接收装配条件？
- 是否顺手改变了 Flyway location、history table、baseline 或 bean name？

## Maven Ownership

新增或调整依赖时必须回答：

```text
Dependency Impact Analysis
- Capability owner:
- Compile API or runtime adapter:
- Direct / optional / runtime / test scope:
- Does it propagate to Lineage consumers:
- Existing owner module/plugin:
- POM guard updated: yes/no
```

Lineage POM 默认拒绝重新加入：

```text
spring-boot-starter-jdbc
springdoc-openapi-starter-webmvc-ui
mybatis-plus-jsqlparser-4.9
flyway-mysql
mysql-connector-j
```

检查运行时 owner：

- MyBatis JSqlParser 与 Flyway MySQL 由 Datasource 提供；
- JDBC driver 与 OpenAPI UI 属于显式应用/插件装配，不属于 Lineage；
- 不借 Lineage PR 顺手改写其他业务模块的历史 POM；
- 直接消费 Lineage 的模块必须显式依赖 Datasource。

## Compatibility

纯架构/POM PR 默认要求：

```text
REST contract unchanged
DB schema unchanged
Flyway migration and history semantics unchanged
Asset/Relation semantics unchanged
existing behavior tests unchanged or strengthened
```

任何例外都必须在 PR body 中单独说明原因与迁移策略。

## Tests

至少检查：

- `LineageArchitectureTest`；
- `LineageDependencyBoundaryTest`；
- `LineageMavenDependencyBoundaryTest`；
- 被修改用例对应的 behavior test；
- 受 POM scope 调整影响的 Datasource / Boot 装配；
- 如果调整 dependency allowlist，文档是否同步且理由是否真实。

## Reject Signals

以下情况默认要求拆分或返工：

- 为了让测试通过直接扩大 package 或 POM 白名单；
- 一个 PR 同时大规模 move package、改表、改接口、改领域行为；
- 新增 `FlinkLineageService / SparkLineageService / HadoopLineageService` 但职责只是同一用例的技术分叉；
- 创建没有真实调用方的 Collector/Resolver 层，只为填满目录；
- Controller 直接操作 Repository/DAO；
- Analysis contract 携带 Spring、Parser SDK 或持久化类型；
- DAO 直接依赖 Datasource 模块；
- 把 driver、vendor runtime 或 UI starter 放回 Lineage POM；
- Domain 开始携带 Mapper/PO/HTTP DTO；
- 只有 Markdown 约定，没有可执行架构护栏。
