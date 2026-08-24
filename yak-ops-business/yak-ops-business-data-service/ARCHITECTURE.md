# Data Service Architecture

> 本文是 `yak-ops-business-data-service` 的长期架构契约，描述职责归属、依赖方向和运行边界。历史迁移过程看 Git / PR。通用 Role Vocabulary 与 Java 规范遵循仓库根目录 [`CODE_STYLE.md`](../../CODE_STYLE.md)。

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
- invocation audit / overview。

不负责：

- 上游 Data Development 草稿、编辑和 Revision 生产；
- Datasource connection/catalog/plugin 生命周期；
- Offline/Realtime Sync orchestration；
- 任意 DML/DDL；
- 全局分布式缓存/限流；
- 血缘、质量、指标计算。

## 2. 设计原则

1. **Package 表达能力，Class 表达角色。** 不恢复 `controller -> service -> serviceImpl -> dao` 默认模板。
2. **发布与调用分离。** Publication 解析上游 Revision；Runtime invocation 读取已经持久化的 Snapshot。
3. **稳定身份与不可变 Revision 分离。** Republish 更新同一 Data Service 的 SourceReference/RuntimeSnapshot，不创建新服务身份。
4. **Domain 是业务事实，PO/HTTP model 是边界表示。** 业务角色不直接操作 MyBatis Mapper/PO。
5. **持久化策略与本地状态分离。** RuntimePolicy 在 DB；Cache/Circuit/Metrics 在 JVM。
6. **SQL Trust Boundary 在服务端。** SQL/dataSourceId 只能来自 Source Provider，HTTP settings 更新不能注入执行定义。
7. **安全 Secret 最小暴露。** raw API key 只在 create/rotate 返回一次，之后只使用 hash + prefix。
8. **读写角色分开。** Manager/Publisher 管 command，Reader 管 query/projection。
9. **依赖图无环。** 不通过白名单接受 Query↔Execution、Runtime↔Execution 等反向依赖。
10. **结构重构默认不改行为。** REST、DB、HTTP status、SqlExecution contract 变化必须先更新 Requirements/Domain。

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
        DataServiceDefinition
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

Invocation -> Recorder -> CallLogRepository -> Observability
```

## 4. 包结构与职责

```text
dataservice
├── controller
│   └── v1                 # REST mapping / request boundary
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
├── repository             # persistence ports and explicit adapters
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
| `Adapter` | Domain ↔ persistence translation | `DataServiceRepositoryAdapter` |
| `Renderer` | typed contract -> presentation artifact | `OpenApiRenderer` |
| `Factory` | read-side projection construction | `DataServiceViewFactory` |

当前模块没有通用 Service facade。未来如果真的需要稳定 Application Service，必须先更新 `DEPENDENCIES.md` 和 Architecture Guard，而不是新增 `XxxServiceImpl`。

## 6. Definition 与 Publication

### 6.1 初次发布

```text
HTTP publish request
       |
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
       v
Repository
```

Source Provider 是**扩展 contract**，不是 Data Service 的内部 Repository。

### 6.2 Source-managed ownership

Provider `managesServiceDefinition=true`：

```text
Source owns:
name/path/maxRows/timeout/pagination/description/contract

Data Service owns:
enabled/auth/API keys/runtime policy/runtime local state
```

这条边界用于避免一个已发布 Revision 在 Data Development 和 Data Service 两侧同时被编辑成不同定义。

### 6.3 Republish

```text
same Data Service ID
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

## 7. Invocation

```text
HTTP runtime request
       |
       v
DataServiceInvoker
       |
       +-- DataServiceReader: require enabled definition
       +-- DataServiceAuthorizer: NONE / API_KEY
       +-- pagination normalization
       +-- DataServiceSqlCompiler: SELECT + named bindings
       +-- LocalDataServiceRuntime: cache/circuit
       +-- DataServiceQueryExecutor
       |       |
       |       v
       |   SqlExecutionRuntime
       |
       +-- DataServiceInvocationRecorder
```

Console test 调用真实 Datasource，但有意绕过外部 cache/circuit 行为，用于检查当前数据源和 SQL。

## 8. SQL read-side corridor

`DataServiceView` 需要展示 SQL parameter names，但 Query 不能反向依赖 Execution implementation。

```text
query.DataServiceParameterNameReader  <--- port
              ^
              |
execution.DataServiceSqlCompiler      <--- implementation
```

因此 dependency direction 保持 `execution -> query`，不存在 `query -> execution` 环。

`DataServiceQueryResponse` 放在 Domain，因为它同时被 Execution、Runtime 和 HTTP boundary 使用，是 runtime-neutral value，不属于 Execution implementation。

## 9. Runtime Truth

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

## 10. Access Security

```text
create/rotate
  -> SecureRandom raw key
  -> SHA-256 hash persisted
  -> prefix persisted
  -> raw key returned once

invoke
  -> hash incoming X-API-Key
  -> repository lookup
  -> enabled / expiry
  -> local rate limit
  -> mark last used
```

Controller、日志、Domain 持久化都不保存 raw secret。

## 11. Documentation

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

## 12. Observability

`DataServiceInvocationRecorder` 写入完成后的调用事实；`DataServiceCallLogReader` 和 `DataServiceOverviewReader` 只做 read-side。

Observability 不参与调用成功与否的业务判定，不拥有 Runtime state machine。

## 13. Persistence Boundary

允许：

```text
RepositoryAdapter -> DAO Mapper / PO
```

禁止：

```text
Controller   -> Mapper / PO
Publisher    -> Mapper / PO
Manager      -> Mapper / PO
Execution    -> Mapper / PO
Runtime      -> Mapper / PO
Domain       -> Mapper / PO
```

PO 字段可以为了 ORM 使用 setter；Domain 通过 constructor/behavior 表达状态。

## 14. Cross-module Boundary

允许上游模块依赖：

```text
io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider
```

Data Service 不反向依赖 Data Development implementation。

Data Service 的物理 SQL 能力只依赖 `yak-ops-core` execution contract，不依赖 Datasource DAO/Mapper。

## 15. Failure Semantics

- invalid source / invalid settings / invalid SQL -> 业务参数错误；
- missing/invalid API key -> 401；
- local rate limit -> 429；
- circuit open -> 503；
- datasource/query failure -> 调用失败并记录 audit；
- documentation error 不改变已发布 SQL；
- observability read failure 不应被解释成 Data Service definition state。

## 16. 修改协议

新能力进入前回答：

```text
Architecture Impact
- capability owner package:
- command/read/runtime role:
- persisted truth vs process-local state:
- new package dependency edge:
- SourceProvider / SqlExecution boundary impact:
- REST/DB compatibility impact:
```

如果引入新的顶层 package edge，需要同步更新 `DEPENDENCIES.md` 和可执行 Guard；不允许只修改测试白名单而不说明职责变化。
