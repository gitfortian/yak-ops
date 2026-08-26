# Data Development Dependencies

本文件定义 `io.yak.ops.business.development` 的允许依赖方向。它不是 Maven 依赖清单，而是业务 package contract。

## Top-level Direction

```text
controller
   -> node / directory / task / execution / dataset / release / editor
   -> frozen legacy preview corridor

node       -> domain + repository -> dao
directory  -> domain + repository -> dao
task       -> domain + repository -> dao
           -> lineage (outbox only)
execution  -> domain + task + shared Task Runtime
             └── model = execution read/response projection

dataset    -> domain + repository + Dataset / Task Catalog
release    -> domain + repository + Task Catalog
             └── model = release read projection

lineage    -> domain + repository + frozen SQL parser implementation
           -> shared Lineage analysis/write contracts
editor     -> local persistence boundary
domain     -> framework-light truth/value types only
```

依赖必须总体向边界流动，不能通过扩大白名单掩盖循环。

## Package Matrix

| From | Allowed module-internal targets |
|---|---|
| `controller` | `node`, `directory`, `task`, `execution`, `dataset`, `release`, `editor`, legacy preview |
| `node` | `domain`, `repository` |
| `directory` | `domain`, `repository` |
| `task` | `domain`, `repository`, `lineage`, compatibility exception corridor |
| `execution` | `domain`, `task`; `execution.model` 只能作为 read/response projection |
| `dataset` | `domain`, `repository` |
| `release` | `domain`, `repository`; `release.model` 只能作为 read projection |
| `lineage` | `domain`, `repository`, frozen legacy SQL parser implementation |
| `repository` | `domain`, `dao` |
| `dao` | persistence primitives |
| `domain` | JDK + compatibility serialization annotations |

## Read Model Rule

Read Model 跟随所属业务子系统，但不能反向成为领域 truth owner：

```text
controller -> release.model
release.model -> immutable domain facts (read only)

controller -> execution.model
execution.model -> JDK / SPI response vocabulary

domain -X-> release.model / execution.model
```

新增 `Page / Summary / Detail / View / Response` 类型时，先判断它是否拥有业务不变量；如果只是组合查询结果，默认不能进入 `domain`。

## Cross-module Corridors

Data Development 可以通过以下明确边界访问邻接模块：

```text
Task authoring  -> TaskPluginRegistry / Task Catalog
Execution       -> shared TaskExecutionGateway
Dataset         -> DevelopmentDatasetFacade
Data Service    -> Data Service publication/runtime boundary
Lineage         -> Lineage application boundary / analysis contract / DataSource Catalog
Node metadata   -> Task Catalog metadata projection
```

SQL projection 方向固定为：

```text
lineage.analysis.DevelopmentSqlProjectionLineageAnalyzer
    -> io.yak.ops.business.lineage.analysis.sql.SqlProjectionLineageAnalyzer
    -> local frozen SQL parser
```

共享 Lineage 模块不允许反向进入 Data Development parser 实现。

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
- Domain 不依赖 Controller、Repository、DAO、Task Runtime、Lineage Service、Spring JDBC、MyBatis，也不依赖 release/execution read model。
- Task / Node / Directory / Dataset / Release 不直接进入本模块 DAO。
- Query/read 行为不得顺手修改 Draft/Revision/Execution truth。
- 不创建 `common/helper/utils/base/service` 作为新功能的默认落点。
- 不通过 `service` legacy island 作为“方便的中转层”形成新依赖。
- Analyzer contract 不携带 Data Development parser、Spring 或持久化类型。

## Legacy Corridors

当前仍有两类已知兼容边界：

1. `service.DevelopmentDraftConflictException` / `service.DevelopmentTaskValidationException` 保留旧调用方类型兼容；
2. Data Service 与 SQL Lineage Parser 大实现仍位于 frozen `service` island。

`DevelopmentSqlProjectionLineageAnalyzer` 已迁入 `lineage.analysis`，不再属于 legacy allowlist。这些剩余项是**固定债务，不是新代码模板**；架构测试会精确限制该目录文件集合，新增文件即失败。

## Persistence Note

历史上的 Execution history、Editor settings、Lineage Outbox 仍直接使用 JDBC。这次角色归位不把 Analyzer package move 与 persistence redesign 混在一起。

新持久化能力默认走 Repository contract；如果确实需要直接 JDBC，需要在 PR 中写清楚原因、truth owner 和事务边界。
