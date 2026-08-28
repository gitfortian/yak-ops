# Data Service Architecture

> 本文是 `yak-ops-business-data-service` 的长期架构契约，描述职责归属、依赖方向和运行边界。历史迁移过程看 Git / PR。通用 Role Vocabulary 与 Java 规范遵循仓库根目录 [`CODE_STYLE.md`](../../CODE_STYLE.md)。Project/RBAC 与公网调用边界详见 `PROJECT_GOVERNANCE.md`。

## 1. 模块定位

Data Service 位于“已发布数据资产”与“在线查询调用”之间。

负责：

- 稳定 Data Service 定义和生命周期；
- 从 versioned Source Provider 发布/重新发布不可变 Revision；
- 固定 SQL + Datasource Runtime Snapshot；
- 单条只读 SELECT 编译与调用；
- API Key 生命周期、鉴权和本地限流；
- 单节点 Cache / Circuit Breaker / Runtime Metrics；
- API contract documentation / OpenAPI；
- invocation audit / overview；
- Yak Ops Management Plane 的 Project ownership 与 RBAC。

不负责：

- 上游 Data Development 草稿、编辑和 Revision 生产；
- Datasource connection/catalog/plugin 生命周期；
- Offline/Realtime Sync orchestration；
- 任意 DML/DDL；
- 全局分布式缓存/限流；
- 血缘、质量、指标计算；
- 用 Yak Project Header 替代 Public Runtime 的 NONE/API_KEY 鉴权。

## 2. 设计原则

1. **Package 表达能力，Class 表达角色。** 不恢复 `controller -> service -> serviceImpl -> dao` 默认模板。
2. **发布与调用分离。** Publication 解析上游 Revision；Runtime invocation 读取已经持久化的 Snapshot。
3. **稳定身份与不可变 Revision 分离。** Republish 更新同一 Data Service 的 SourceReference/RuntimeSnapshot，不创建新服务身份。
4. **Domain 是业务事实，PO/HTTP model 是边界表示。** 业务角色不直接操作 MyBatis Mapper/PO。
5. **持久化策略与本地状态分离。** RuntimePolicy 在 DB；Cache/Circuit/Metrics 在 JVM。
6. **SQL Trust Boundary 在服务端。** SQL/dataSourceId 只能来自 Source Provider，HTTP settings 更新不能注入执行定义。
7. **安全 Secret 最小暴露。** raw API key 只在 create/rotate 返回一次，之后只使用 hash + prefix。
8. **读写角色分开。** Manager/Publisher 管 command，Reader 管 query/projection。
9. **管理面与调用面分离。** Console Management 使用 Project + RBAC；外部 Invocation 使用全局 path + NONE/API_KEY。
10. **依赖图无环。** 不通过白名单接受 Query↔Execution、Runtime↔Execution 等反向依赖。
11. **结构重构默认不改行为。** REST、DB、HTTP status、SqlExecution contract 变化必须先更新 Requirements/Domain。

## 3. 总体架构

```text
Data Development / other published source
                  |
                  v
       DataServiceSourceProvider
                  |
                  v
         +------------------+
         |   Publication    |
         | Publisher/Reader |
         | SourceRegistry   |
         +--------+---------+
                  |
                  v
 DataServiceDefinition(projectId)
      +-----------+-----------+
      |           |           |
      v           v           v
   Access       Runtime   Documentation
      |           |           |
      +-----------+-----------+
                  |
                  v
             Execution
       Invoker / QueryExecutor
                  |
                  v
        yak-ops-core SqlExecutionRuntime
                  |
                  v
              Datasource

Invocation -> Recorder -> CallLogRepository(projectId snapshot) -> Observability
```

入口明确分成两个平面：

```text
Yak Ops User
   -> Project membership
   -> Data Service RBAC
   -> Management Controllers
   -> project-scoped Repository

External Client
   -> /api/v1/data-service/runtime/{servicePath}
   -> NONE / X-API-Key
   -> global findByRuntimePath
   -> resolved DataServiceDefinition(projectId)
```

## 4. 包结构与职责

```text
dataservice
├── controller
│   └── v1                 # Console management + isolated public invocation boundary
├── publication
│   └── source             # publish / republish / source extension contract
├── management             # stable aggregate command lifecycle
├── query                  # Data Service read side / projection
├── access                 # API Key lifecycle / authorize / local rate limit
├── execution              # SQL compiler / invoker / physical query coordinator
├── runtime                # local resilience + persisted policy commands
├── documentation          # docs persistence / merge / OpenAPI rendering
├── observability          # logs and overview read side
├── domain
│   ├── access
│   └── documentation      # business facts / values
├── repository             # persistence ports + Project-aware adapters
├── dao                    # MyBatis mapper / PO
└── config                 # module wiring / conditional configuration
```

禁止重新创建 `service / common / helper / utils / util / base` 这类模糊业务桶。

## 5. Role Vocabulary

| Role | 本模块职责 | 例子 |
| --- | --- | --- |
| `Manager` | Aggregate command / lifecycle / transactional mutation | `DataServiceManager`, `DataServiceApiKeyManager` |
| `Publisher` | 把可信 Source Revision 发布成 Data Service snapshot | `DataServicePublisher` |
| `Reader` | read-side 查询和 projection | `DataServiceReader`, `DataServiceOverviewReader` |
| `Registry` | Source extension discovery / uniqueness / lookup | `DataServiceSourceRegistry` |
| `Authorizer` | 每次请求访问判定 | `DataServiceAuthorizer` |
| `RateLimiter` | 当前进程限流状态 | `DataServiceRateLimiter` |
| `Compiler` | 只读 SQL 校验和 named binding 编译 | `DataServiceSqlCompiler` |
| `Invoker` | 一次 Data Service 调用流程编排 | `DataServiceInvoker` |
| `Executor` | 已编译 SQL 的物理执行 | `DataServiceQueryExecutor` |
| `Runtime` | process-local resilience / metrics | `LocalDataServiceRuntime` |
| `Recorder` | 调用完成后的审计写入 | `DataServiceInvocationRecorder` |
| `Repository` | Domain persistence port | `DataServiceRepository` |
| `Adapter` | Domain ↔ persistence / Project translation | `DataServiceRepositoryAdapter` |
| `Renderer` | typed contract -> presentation artifact | `OpenApiRenderer` |
| `Factory` | read-side projection construction | `DataServiceViewFactory` |

当前模块没有通用 Service facade。未来如果真的需要稳定 Application Service，必须先更新 `DEPENDENCIES.md` 和 Architecture Guard，而不是新增 `XxxServiceImpl`。

## 6. Definition 与 Publication

### 6.1 初次发布

```text
PROJECT_REQUIRED HTTP publish request
       |
       +-- Project membership
       +-- data-service:publish
       v
PublicationReader.normalizeIdentity
       |
       v
SourceRegistry.require
       |
       v
SourceProvider.resolve
       |
       +-- validate ONLINE / revision / datasource / SELECT
       v
DataServiceManager.savePublished
       |
       +-- bind CurrentProject.projectId
       v
Project-scoped Repository
```

Source Provider 是**扩展 contract**，不是 Data Service 的内部 Repository。

### 6.2 Source-managed ownership

Provider `managesServiceDefinition=true`：

```text
Source owns:
name/path/maxRows/timeout/pagination/description/contract

Data Service owns:
projectId/enabled/auth/API keys/runtime policy/runtime local state
```

这条边界用于避免一个已发布 Revision 在 Data Development 和 Data Service 两侧同时被编辑成不同定义。

Data Development source-managed API 的 Project ownership 必须与 owning Data Development Node 对齐；兼容迁移优先从 Source Node 推断，不允许把同一 authoring/runtime projection 拆到两个 Project。

### 6.3 Republish

```text
same Data Service ID
same Project ownership
      |
resolve latest immutable source revision
      |
      +-- replace SourceReference
      +-- replace PublishedRuntimeSnapshot
      +-- refresh source-owned settings/docs
      +-- preserve AuthMode
      +-- preserve RuntimePolicy
      v
invalidate local runtime state
```

## 7. Management Plane

Console 管理入口统一 `PROJECT_REQUIRED`，并按动作使用显式权限：

```text
READ      -> marketplace / detail / docs / OpenAPI
PUBLISH   -> sources / publish / republish / publication state
MANAGE    -> settings / enable-disable / editable docs
DELETE    -> delete
ACCESS    -> auth mode / API key lifecycle
RUNTIME   -> runtime policy / console test
OBSERVE   -> overview / invocation logs
```

Repository 的 Console 方法必须绑定 `CurrentProject`：

```text
findById
findByPath
findBySource
findAll
save
delete
```

API Key / Documentation 不重复持久化 projectId，但任何 Console mutation/read 必须先经过父 Data Service 的 Project ownership。

## 8. Public Invocation Plane

```text
GET /api/v1/data-service/runtime/{servicePath}
       |
       v
DataServiceInvocationController
       |
       v
DataServiceInvoker
       |
       +-- DataServiceReader.requireByPath
       |       |
       |       v
       |  Repository.findByRuntimePath   # sole global Data Service read corridor
       |
       +-- require enabled definition
       +-- DataServiceAuthorizer: NONE / API_KEY
       +-- pagination normalization
       +-- DataServiceSqlCompiler: SELECT + named bindings
       +-- LocalDataServiceRuntime: cache/circuit
       +-- DataServiceQueryExecutor
       |       |
       |       v
       |   SqlExecutionRuntime
       |
       +-- DataServiceInvocationRecorder(projectId snapshot)
```

`DataServiceInvocationController` 故意没有 Yak `@ProjectScope` / `@RequiresPermission`。外部调用方不需要也不能使用 `X-YAK-SECURITY-PROJECT-ID` 选择服务；Path 因此继续跨 Project 全局唯一。

Console test 调用真实 Datasource，但有意绕过外部 cache/circuit 行为，用于检查当前数据源和 SQL；它属于 Management Plane，需要 RUNTIME 权限和当前 Project。

## 9. SQL read-side corridor

`DataServiceView` 需要展示 SQL parameter names，但 Query 不能反向依赖 Execution implementation。

```text
query.DataServiceParameterNameReader  <--- port
              ^
              |
execution.DataServiceSqlCompiler      <--- implementation
```

因此 dependency direction 保持 `execution -> query`，不存在 `query -> execution` 环。

`DataServiceQueryResponse` 放在 Domain，因为它同时被 Execution、Runtime 和 HTTP boundary 使用，是 runtime-neutral value，不属于 Execution implementation。

## 10. Runtime Truth

```text
Persisted:
DataServiceDefinition.runtimePolicy
        |
        v
LocalDataServiceRuntime
        |
        +-- Cache
        +-- Circuit state
        +-- Metrics

Restart / another JVM -> local state can differ
```

RuntimeManager 负责 Policy command + invalidation；LocalRuntime 不直接操作 Repository。

当前 Cache/Circuit/Metrics 是 node-local。未来若引入 Redis/global quota，需要新增明确的 distributed Runtime port，不得把当前内存 Map 伪装成集群事实。

## 11. Access Security

```text
create/rotate (Management Plane)
  -> verify parent API in CurrentProject
  -> SecureRandom raw key
  -> SHA-256 hash persisted
  -> prefix persisted
  -> raw key returned once

invoke (Public Invocation Plane)
  -> global path resolves API
  -> hash incoming X-API-Key
  -> repository lookup
  -> enabled / expiry
  -> local rate limit
  -> mark last used
```

Controller、日志、Domain 持久化都不保存 raw secret。Public Invocation 的 Key lookup 属于已解析 API 的访问执行过程，不读取 Yak Project Header。

## 12. Documentation

```text
Published SQL
   |
   +-- parameterNames -------------------+
   |                                     |
Saved documentation ---------------------+
                                         v
                               DocumentationReader
                                         |
                                         v
                                     OpenAPI
```

SQL parameter names 是事实，description/example 是人工/上游元数据。文档不能创造不存在的 SQL 参数。

Documentation projection 自身不重复保存 Project identity；Console 入口先验证父 API ownership。

## 13. Observability

`DataServiceInvocationRecorder` 写入完成后的调用事实；`DataServiceCallLogReader` 和 `DataServiceOverviewReader` 只做 read-side。

Invocation Record 快照 `projectId`，因此 API 删除后历史 evidence 仍能归属 Project。Console 的日志、Overview、热点 API、失败记录全部按 CurrentProject 聚合。

Observability 不参与调用成功与否的业务判定，不拥有 Runtime state machine；audit persistence failure 不能覆盖业务成功或原始调用异常。

## 14. Persistence Boundary

允许：

```text
Management Business Role
   -> Repository
   -> CurrentProject predicate
   -> DAO Mapper / PO

Public Invocation
   -> DataServiceReader.requireByPath
   -> Repository.findByRuntimePath
```

禁止：

```text
Controller   -> Mapper / PO
Publisher    -> Mapper / PO
Manager      -> Mapper / PO
Execution    -> Mapper / PO
Runtime      -> Mapper / PO
Domain       -> Mapper / PO
Management   -> findByRuntimePath
Invocation   -> Yak Project Header selector
```

PO 字段可以为了 ORM 使用 setter；Domain 通过 constructor/behavior 表达状态。

## 15. Cross-module Boundary

允许上游模块依赖：

```text
io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider
```

Data Service 不反向依赖 Data Development implementation。

Data Service 的物理 SQL 能力只依赖 `yak-ops-core` execution contract；Project management 只依赖 `yak-ops-core` 的 Project scope/current-project contract，不依赖 Yak Security implementation。

## 16. Failure Semantics

- invalid source / invalid settings / invalid SQL -> 业务参数错误；
- missing/invalid Console Project -> Project contract error；
- missing Console permission -> RBAC forbidden；
- missing/invalid Public API key -> 401；
- local rate limit -> 429；
- circuit open -> 503；
- datasource/query failure -> 调用失败并尽力记录 audit；
- audit persistence failure 不改变已经确定的调用结果；
- documentation error 不改变已发布 SQL；
- observability read failure 不应被解释成 Data Service definition state。

## 17. Architecture Guards

```text
DataServiceDependencyBoundaryTest
  -> package graph / persistence / cross-module corridors

DataServiceCodeStyleConventionTest
  -> role-oriented source conventions / secret boundaries

DataServiceGovernanceContractTest
  -> Management Controllers are PROJECT_REQUIRED
  -> Public Invocation Controller has no Project/RBAC annotation
  -> permission vocabulary remains explicit
  -> only findByRuntimePath is a deliberate global runtime corridor
```

Project compatibility backfill 另由 Boot contract test 守住：source-managed API 先继承 Data Development node Project，再对 legacy global rows 使用 compatibility Project。

## 18. 修改协议

新能力进入前回答：

```text
Architecture Impact
- capability owner package:
- command/read/runtime role:
- management vs invocation plane:
- persisted truth vs process-local state:
- project ownership impact:
- new package dependency edge:
- SourceProvider / SqlExecution boundary impact:
- REST/DB compatibility impact:
```

如果引入新的顶层 package edge，需要同步更新 `DEPENDENCIES.md` 和可执行 Guard；不允许只修改测试白名单而不说明职责变化。
