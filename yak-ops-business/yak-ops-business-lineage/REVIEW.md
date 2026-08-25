# Lineage Review Guide

Lineage PR 评审优先看**领域语义和依赖方向有没有被破坏**，不是只看代码能否编译。

## Scope

- 这个 PR 是否只有一个主要关注点？
- 纯结构调整是否偷改了 REST、DB schema、Flyway 或业务语义？
- package move 与 behavior change 是否可以拆开？

## Domain

- Asset identity 是否仍然统一，不因技术来源产生第二套模型？
- Relation 是否始终表达 upstream source -> downstream target？
- Evidence 是否只说明来源，不冒充 Asset identity？
- replacement / revision ordering 是否保持保守和并发安全？
- Graph/query 是否仍然是只读、有边界的 projection？

## Roles

- 新类名是否能直接说明角色？
- `Service` 是否只用于稳定应用入口，而不是所有 Spring Bean？
- Analyzer / Collector / Resolver / Mapper / Repository / DAO 是否各自停在正确边界？
- Flink / Spark / Hadoop 是否作为角色实现接入，而不是复制一套业务层？

## Dependencies

- Controller 是否只走稳定 Service？
- Service 是否绕过 Repository 直接访问 DAO/Mapper/PO？
- Repository contract 是否暴露 HTTP 或 persistence implementation types？
- DAO 是否反向依赖应用层？
- Domain 是否出现 Spring/MyBatis/平台 SDK？
- 是否新增了 `common/helper/utils/base` 之类的大桶？

## Persistence

- Domain ↔ PO 转换是否停在 RepositoryAdapter？
- MyBatis Mapper/XML 是否只承担数据库 primitive？
- JSON/compatibility codec 是否位于 persistence boundary，而不是 Core Domain？

## Compatibility

纯架构 PR 默认要求：

```text
REST contract unchanged
DB schema unchanged
Flyway unchanged
Asset/Relation semantics unchanged
existing behavior tests unchanged or strengthened
```

任何例外都必须在 PR body 中单独说明原因与迁移策略。

## Tests

至少检查：

- `LineageArchitectureTest`；
- `LineageDependencyBoundaryTest`；
- 被修改用例对应的 behavior test；
- 如果调整 dependency allowlist，文档是否同步且理由是否真实。

## Reject Signals

以下情况默认要求拆分或返工：

- 为了让测试通过直接扩大依赖白名单；
- 一个 PR 同时大规模 move package、改表、改接口、改领域行为；
- 新增 `FlinkLineageService / SparkLineageService / HadoopLineageService` 但职责只是同一用例的技术分叉；
- Controller 直接操作 Repository/DAO；
- Domain 开始携带 Mapper/PO/HTTP DTO；
- 只有 Markdown 约定，没有可执行架构护栏。
