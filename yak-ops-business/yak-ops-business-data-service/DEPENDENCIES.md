# Data Service Dependency Contract

> 本文件定义 `yak-ops-business-data-service` 生产代码的**允许依赖方向**。它不是当前 import 的截图，而是长期架构约束；对应规则由 `src/test/.../architecture` 下的 executable guard 校验。

## 1. 原则

1. 顶层 package graph 必须无环。
2. Domain 不依赖 Spring、MyBatis、HTTP、Repository、Runtime 或上游业务模块。
3. MyBatis Mapper/PO 只允许出现在 `dao` 与 `repository` persistence corridor。
4. Controller 不直接访问 Repository/DAO/PO。
5. Publication/Management/Execution/Access/Runtime 等业务角色依赖 Repository port，不依赖 Mapper。
6. `publication.source` 是跨模块 Source Provider 的公开 extension contract，保持 JDK-only。
7. Data Service 不反向依赖 Data Development implementation。
8. SQL 物理执行只走 `yak-ops-core` `SqlExecutionRuntime`，不访问 Datasource DAO/Mapper。
9. Project-aware management persistence 只通过 `yak-ops-core` `CurrentProject` 获取受信 Project identity。
10. Public Invocation 只能通过显式 `findByRuntimePath` global read corridor 绕开 CurrentProject；其他 repository read/write 不允许退化成 global。
11. 不通过 `service/common/helper/utils/util/base` 等模糊 package 绕过依赖矩阵。

## 2. 顶层依赖图

```text
controller
   |
   +--> publication --> documentation --> execution --> access --> query --> repository --> dao
   |        |                 |              |            |           |          |
   |        +--> management --+              +--> runtime-+           |          +--> domain
   |        +--> query                       +--> query               +--> domain
   |        +--> execution                   +--> repository
   |
   +--> management --> access/runtime/query/repository
   +--> execution  --> access/runtime/query/repository
   +--> documentation --> execution/query/repository
   +--> observability --> query/repository
   +--> access --> query/repository
   +--> runtime --> query/repository
   +--> query --> repository

all business packages may depend on domain where required
repository -> core CurrentProject for management ownership predicates
config and domain have no internal outward dependencies
```

图中省略同 package import 和 `domain` 边，以避免噪声。

## 3. Allowed Top-level Matrix

| Source package | Allowed Data Service target packages |
| --- | --- |
| `controller` | `access`, `documentation`, `domain`, `execution`, `management`, `observability`, `publication`, `query`, `runtime` |
| `publication` | `documentation`, `domain`, `execution`, `management`, `query` |
| `management` | `access`, `domain`, `query`, `repository`, `runtime` |
| `documentation` | `domain`, `execution`, `query`, `repository` |
| `execution` | `access`, `domain`, `query`, `repository`, `runtime` |
| `observability` | `domain`, `query`, `repository` |
| `access` | `domain`, `query`, `repository` |
| `runtime` | `domain`, `query`, `repository` |
| `query` | `domain`, `repository` |
| `repository` | `dao`, `domain` |
| `dao` | none |
| `config` | none |
| `domain` | none |

同一顶层 package 内部引用不计作跨包 edge。

## 4. 为什么 Query 不依赖 Execution

`DataServiceViewFactory` 需要从 SQL 提取 parameter names，但如果直接 import `DataServiceSqlCompiler`，就会形成：

```text
query -> execution -> query
```

因此定义窄 port：

```text
query.DataServiceParameterNameReader
             ^
             |
execution.DataServiceSqlCompiler
```

`query` 只认识自己需要的 read capability，Execution 提供实现。

## 5. 为什么 Runtime 不依赖 Execution

Execution 需要 Runtime 做 cache/circuit，而 Runtime 也需要缓存查询结果。如果查询结果类型归 `execution`，会形成：

```text
execution -> runtime -> execution
```

因此 `DataServiceQueryResponse` 是 `domain` 下的 runtime-neutral value：

```text
execution -> domain <- runtime
execution -> runtime
```

## 6. Persistence / Project Corridor

唯一允许的业务到 ORM 路径：

```text
Business Role
    |
    v
Repository interface
    |
    v
RepositoryAdapter
    |
    +--> CurrentProject (management ownership)
    |
    v
DAO Mapper / PO
```

允许：

```text
repository/DataServiceRepositoryAdapter
  -> dao.mapper.DataServiceApiMapper
  -> dao.model.DataServiceApiPO
  -> io.yak.ops.core.project.CurrentProject
```

Management repository methods：

```text
findById
findByPath
findBySource
findAll
save
delete
```

都必须绑定 `CurrentProject`。

Public Runtime 唯一例外：

```text
DataServiceInvocationController
  -> DataServiceInvoker
  -> DataServiceReader.requireByPath
  -> DataServiceRepository.findByRuntimePath
```

该 corridor 不读取 Yak Project Header，因为外部调用地址没有 Project namespace；它从全局唯一 path resolve 出 `DataServiceDefinition(projectId)` 后，再按 NONE/API_KEY 执行。

禁止：

```text
controller -> dao/repository
publication -> dao
management -> dao
execution -> dao
runtime -> dao
access -> dao
documentation -> dao
observability -> dao
domain -> repository/dao
management page -> findByRuntimePath
external invocation -> CurrentProject selector
```

`repository` 可以使用 MyBatis type，因为 Adapter 就是 persistence boundary；其他业务 package 不可以。

## 7. Source Provider Corridor

跨模块只暴露：

```text
io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider
```

该 contract 当前只依赖 JDK type：`Instant / List / record`。

允许：

```text
Data Development
  -> DataServiceSourceProvider
```

禁止：

```text
Data Service -> Data Development implementation
Data Development provider -> Data Service repository/manager/runtime
publication.source -> query/repository/management/execution/access/runtime
```

Provider 负责返回**不可变、可发布的来源事实**，而不是进入 Data Service 内部执行链路。

Source-managed Data Service 的 owner-context permission 由上游 authoring context 负责；Data Service 的 Project/RBAC 不能成为绕过 authoring ownership 的新 corridor。

## 8. Datasource / Core Corridor

Data Service 当前允许的跨模块基础依赖：

```text
io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled
io.yak.ops.core.execution.sql.*
io.yak.ops.core.project.CurrentProject
io.yak.ops.core.project.ProjectScope
io.yak.ops.core.project.ProjectMigrationMode
```

`ConditionalOnDataSourceEnabled` 只是模块启用条件；物理 SQL 通过 core contract 执行；Project types 只承担 Management Plane 的受信 scope contract。

禁止 Data Service 生产代码依赖：

```text
io.yak.ops.business.datasource.dao.*
io.yak.ops.business.datasource.repository.*
io.yak.ops.business.datasource.gateway.adapter.*
io.yak.ops.business.datasource.plugin.*
io.yak.ops.business.development.*
```

如未来需要新的 Datasource Business Port，应先在 Datasource 定义稳定 contract，再更新本文件和 Guard。

## 9. HTTP Boundary

Management Controller 可以使用：

- `Result`；
- Spring MVC annotation；
- `@ProjectScope(PROJECT_REQUIRED)`；
- capability-owned input/view records；
- Domain query result / audit projection；
- action-specific Yak Security permission annotation。

`DataServiceInvocationController` 是唯一 Public Invocation Controller：禁止添加 Yak Project Scope 或 Console RBAC；外部鉴权只来自已发布的 NONE/API_KEY contract。

Controller 不应：

- 构造 MyBatis Wrapper；
- import Mapper / PO；
- 编译 SQL；
- 直接读写 Repository；
- 实现 API Key hash、rate-limit、circuit 等业务规则。

## 10. Domain Boundary

`domain/**` 只表达 business fact/value：

```text
DataServiceDefinition(projectId)
DataServiceSettings
PublishedRuntimeSnapshot
SourceReference
RuntimePolicy
DataServiceQueryResponse
DataServiceApiKey
DataServiceDocumentation
InvocationRecord(projectId snapshot)
```

Domain 不依赖：

```text
Spring
MyBatis
Controller
Repository
DAO
Publication
Execution
Runtime
Access implementation
Data Development / Datasource implementation
```

`domain.access` / `domain.documentation` 仍属于顶层 `domain`，不是新的依赖层。

## 11. No Service Facade by Default

当前没有通用 `DataServiceService` facade。Controller 进入明确 capability role。

如果未来确实需要 application facade：

1. 明确它解决的跨能力稳定 API 问题；
2. 不把业务逻辑重新搬进一个大类；
3. 更新 Architecture/Dependencies；
4. 显式调整 Guard allowlist。

禁止仅因为“Controller 注入项多”就恢复 `XxxService / XxxServiceImpl`。

## 12. Dependency Change Protocol

引入新的 import edge 前在 PR 描述中写：

```text
Dependency Impact
- source package:
- target package:
- why current owner cannot handle it:
- cycle check:
- persistence / source / datasource / project corridor impact:
- DEPENDENCIES.md updated: yes/no
```

Guard 失败时先检查职责是否放错；不要默认扩大 `ALLOWED_DEPENDENCIES`。
