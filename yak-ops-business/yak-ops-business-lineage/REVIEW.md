# Lineage Review Guide

Lineage PR 评审优先看领域语义、公共 API 和依赖方向是否被破坏，而不是只看代码能否编译。

## Scope

- PR 是否只有一个主要关注点？
- 纯结构调整是否偷改 REST、DB schema、Flyway 或业务语义？
- Package move、公共 API 变化和行为变化是否可以拆开？
- 新抽象是否有真实调用方，还是为了“看起来完整”提前占位？

## Domain

- Asset identity 是否仍然统一，不因技术来源产生第二套模型？
- Relation 是否始终表达 upstream source -> downstream target？
- Evidence 是否只说明来源，不冒充 Asset identity？
- Replacement / revision ordering 是否保持保守和并发安全？
- Graph/query 是否仍然是只读、有边界的 projection？
- Draft 是否继续停留在模块内部写入过程？

## Roles

- `Service` 是否只用于 Query、Write、Maintenance 三个稳定应用入口？
- Analyzer、Converter、RepositoryAdapter、DAO 是否停在正确 package？
- SQL Analyzer contract 是否保持 source-neutral？
- 是否创建了没有真实调用链的 Collector、Resolver 或技术栈平行业务层？
- 新平台实现是否复用统一 Domain 与写入边界？

## Public API

邻接模块新增 Lineage import 时必须检查：

- 是否属于声明的 Analyzer、Domain 或 Service 类型根？
- 是否可以通过调用方自己的 Gateway/Adapter 缩小扩散范围？
- 是否意外依赖 Repository、DAO、Config、Controller 或 Draft？
- 稳定 Service 的公开方法签名是否泄漏实现类型？
- 新公共类型是否同时更新 `ARCHITECTURE.md`、`DEPENDENCIES.md` 和 `LineagePublicApiBoundaryTest`？

仅仅因为 Java 类型声明为 `public`，不代表它自动成为跨模块 contract。

## Dependencies

- 活动 top-level package 是否仍与精确矩阵一致？
- Controller 是否只进入 Service/Domain？
- Service 是否绕过 Repository 直接访问 DAO、Mapper、PO 或 Config？
- Analysis 是否依赖 Spring、Repository、DAO、Controller 或具体 parser？
- Repository contract 是否暴露 HTTP 或 persistence implementation types？
- DAO 是否反向依赖应用层或 Domain orchestration？
- Domain 是否出现 Spring、MyBatis 或平台 SDK？
- 根包是否重新出现 production 类型或全限定引用？
- 是否新增 `common/helper/utils/base` 大桶？
- 声明图和实际 import 图是否继续无环？

## Persistence

- Domain 与 PO 转换是否停在 RepositoryAdapter？
- MyBatis Mapper/XML 是否只承担数据库 primitive？
- JSON/compatibility codec 是否位于 `repository.support`？
- DAO 是否只使用 `ConditionalOnLineagePersistence`？
- Datasource import 是否仍只存在于两个声明的 Config corridor 文件？

## Maven

- Lineage POM 是否新增了调用方不需要的 runtime 实现？
- Driver、Flyway database extension、JSqlParser 等能力是否放在真正的装配 owner？
- 消费 Lineage 的模块是否显式声明自身运行所需 provider？
- 是否因为目录美观而提前拆 Maven artifact？
- 新依赖是否同步更新 Maven boundary test？

## Compatibility

纯架构 PR 默认要求：

```text
REST contract unchanged
DB schema unchanged
Flyway unchanged
Asset/Relation semantics unchanged
existing behavior tests unchanged or strengthened
```

任何例外都必须在 PR 正文中单独说明原因、影响面和迁移策略。

## Tests

至少检查：

- `LineageArchitectureTest`
- `LineageDependencyBoundaryTest`
- `LineageMavenDependencyBoundaryTest`
- `LineagePublicApiBoundaryTest`
- `LineageCodeStyleConventionTest`
- `LineageDocumentationContractTest`
- 被修改用例对应的 behavior test
- 跨模块调用方联合编译

新增真实 Collector 时，还必须覆盖重复事件、失败重试、replacement、ownership 和平台事件转换。

## Reject Signals

以下情况默认要求拆分或返工：

- 为了让测试通过直接扩大 package、public API 或 dependency 白名单；
- 声明不存在的活动 package，等待未来代码补齐；
- 一个 PR 同时大规模移动代码、改表、改接口和改领域行为；
- Controller 直接操作 Repository/DAO；
- Service 公开方法暴露 Repository、DAO、Config、PO 或 Draft；
- Analysis contract 携带 Spring、Parser SDK 或持久化类型；
- Domain 携带 Mapper、PO、HTTP DTO 或平台 SDK；
- 新增 `FlinkLineageService / SparkLineageService / HadoopLineageService` 复制同一业务层；
- 创建没有真实调用方的 Collector/Resolver 空抽象；
- 运行时 driver/extension 被重新塞回 Lineage 并传递给所有消费者；
- 只有 Markdown 约定，没有可执行架构护栏。
