# Data Development Dependencies

本文件定义 `io.yak.ops.business.development` 的允许依赖方向。它不是 Maven 依赖清单，而是业务 package contract。

## Top-level Direction

```text
controller
   -> node / directory / task / execution / dataset / release / editor
   -> frozen legacy preview corridor

node       -> repository -> dao
directory  -> repository -> dao
task       -> repository -> dao
           -> lineage (outbox only)
execution  -> task + shared Task Runtime
dataset    -> repository + Dataset / Task Catalog
release    -> repository + Task Catalog
lineage    -> repository + frozen SQL lineage implementation
editor     -> local persistence boundary
domain     -> framework-light values only
```

依赖必须总体向边界流动，不能通过扩大白名单掩盖循环。

## Package Matrix

| From | Allowed module-internal targets |
|---|---|
| `controller` | `node`, `directory`, `task`, `execution`, `dataset`, `release`, `editor`, legacy preview |
| `node` | `domain`, `repository` |
| `directory` | `domain`, `repository` |
| `task` | `domain`, `repository`, `lineage`, compatibility exception corridor |
| `execution` | `domain`, `task` |
| `dataset` | `domain`, `repository` |
| `release` | `domain`, `repository` |
| `lineage` | `domain`, `repository`, frozen legacy SQL lineage implementation |
| `repository` | `domain`, `dao` |
| `dao` | persistence primitives |
| `domain` | JDK + compatibility serialization annotations |

## Cross-module Corridors

Data Development 可以通过以下明确边界访问邻接模块：

```text
Task authoring  -> TaskPluginRegistry / Task Catalog
Execution       -> shared TaskExecutionGateway
Dataset         -> DevelopmentDatasetFacade
Data Service    -> Data Service publication/runtime boundary
Lineage         -> Lineage application boundary / DataSource Catalog
Node metadata   -> Task Catalog metadata projection
```

新增跨模块依赖前先回答：

```text
Dependency Impact Analysis
- New edge:
- Owner of the target truth:
- Existing corridor or new corridor:
- Can a local gateway/port make the boundary narrower:
- Cycle impact:
- DEPENDENCIES / guard updated: yes/no
```

## Forbidden Shortcuts

- Controller 不直接访问 Repository / DAO。
- Domain 不依赖 Controller、Repository、DAO、Task Runtime、Lineage Service、Spring JDBC、MyBatis。
- Task / Node / Directory / Dataset / Release 不直接进入本模块 DAO。
- Query/read 行为不得顺手修改 Draft/Revision/Execution truth。
- 不创建 `common/helper/utils/base/service` 作为新功能的默认落点。
- 不通过 `service` legacy island 作为“方便的中转层”形成新依赖。

## Legacy Corridors

当前仍有两类已知兼容边界：

1. `service.DevelopmentDraftConflictException` / `service.DevelopmentTaskValidationException` 保留旧调用方类型兼容；
2. Data Service 与 SQL Lineage Parser/Analyzer 大实现仍位于 frozen `service` island。

这些是**固定债务，不是新代码模板**。架构测试会精确限制该目录文件集合；新增文件即失败。

## Persistence Note

历史上的 Execution history、Editor settings、Lineage Outbox 仍直接使用 JDBC。这次 Stage 2 不把 package move 与 persistence redesign 混在一起。

新持久化能力默认走 Repository contract；如果确实需要直接 JDBC，需要在 PR 中写清楚原因、truth owner 和事务边界。
