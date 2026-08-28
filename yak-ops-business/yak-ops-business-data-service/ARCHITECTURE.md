# Data Service Architecture

> 本文是 `yak-ops-business-data-service` 的长期架构契约。Project/RBAC 与公网调用边界详见 `PROJECT_GOVERNANCE.md`；多实例运行边界详见 `CLUSTER_RUNTIME.md`。

## 1. 模块定位

Data Service 位于“已发布数据资产”与“在线查询调用”之间。

负责：

- 稳定 Data Service 定义和生命周期；
- 从 versioned Source Provider 发布/重新发布不可变 Revision；
- 固定 SQL + Datasource Runtime Snapshot；
- 单条只读 SELECT 编译与调用；
- API Key 生命周期、鉴权和集群共享限流；
- node-local Cache / Circuit Breaker；
- cluster invocation metric projection；
- API contract documentation / OpenAPI；
- sanitized invocation audit / overview / retention / hourly rollup；
- Yak Ops Management Plane 的 Project ownership 与 RBAC。

不负责：

- 上游 Data Development 草稿、编辑和 Revision 生产；
- Datasource connection/catalog/plugin 生命周期；
- Offline/Realtime Sync orchestration；
- 任意 DML/DDL；
- shared result cache / distributed circuit state；
- API Gateway；
- 血缘、质量计算；
- 用 Yak Project Header 替代 Public Runtime 的 NONE/API_KEY 鉴权。

## 2. 设计原则

1. **Package 表达能力，Class 表达角色。** 不恢复 `controller -> service -> serviceImpl -> dao` 默认模板。
2. **发布与调用分离。** Publication 解析上游 Revision；Runtime invocation 读取已经持久化的 Snapshot。
3. **稳定身份与不可变 Revision 分离。** Republish 更新同一 Data Service，不创建第二个身份。
4. **Domain 是业务事实，PO/HTTP model 是边界表示。** 业务角色不直接操作 MyBatis Mapper/PO。
5. **Truth 分层。** Definition/Policy、cluster coordination/evidence、node-local resilience 不能混为一个 Runtime 状态。
6. **SQL Trust Boundary 在服务端。** SQL/dataSourceId 只能来自 Source Provider。
7. **安全 Secret 最小暴露。** raw API key 只在 create/rotate 返回一次；audit 参数在落库前脱敏。
8. **读写角色分开。** Manager/Publisher 管 command，Reader 管 query/projection。
9. **管理面与调用面分离。** Console 使用 Project + RBAC；外部 Invocation 使用全局 path + NONE/API_KEY。
10. **热路径最小化。** Result Cache/Circuit 留在节点内；历史清理不进入 invocation 热路径。
11. **依赖图无环。** 不用新增 package edge 掩盖职责混乱。
12. **结构重构默认不改行为。** REST、HTTP status、SqlExecution contract 的变化先更新 Requirements/Domain。

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
         +--------+---------+
                  |
                  v
DataServiceDefinition(projectId, runtimeGeneration)
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

Invocation
  +--> shared API-key minute window
  +--> node-local Cache/Circuit
  +--> sanitized audit -> raw log -> hourly rollup
                                  |
                                  +--> cluster runtime metrics
```

入口仍明确分成两个平面：

```text
Yak Ops User
   -> Project membership + Data Service RBAC
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
├── controller             # Console management + isolated public invocation boundary
├── publication/source     # publish / republish / source extension contract
├── management             # stable aggregate command lifecycle
├── query                  # Data Service read side / projection
├── access                 # API Key lifecycle / authorize / cluster rate-limit coordinator
├── execution              # compiler / invoker / physical query / audit sanitizer+recorder
├── runtime                # node-local cache/circuit + cluster metric projection facade
├── documentation          # docs persistence / merge / OpenAPI rendering
├── observability          # logs / overview / bounded retention-rollup maintenance
├── domain                 # business facts / values
├── repository             # persistence + cluster-coordination ports/adapters
├── dao                    # MyBatis mapper / PO
└── config                 # module wiring / conditions
```

禁止重新创建 `service / common / helper / utils / util / base` 这类模糊业务桶。

## 5. Role Vocabulary

| Role | 本模块职责 | 例子 |
| --- | --- | --- |
| `Manager` | Aggregate command / lifecycle | `DataServiceManager`, `DataServiceApiKeyManager` |
| `Publisher` | Source Revision -> Data Service snapshot | `DataServicePublisher` |
| `Reader` | read-side 查询/projection | `DataServiceReader`, `DataServiceOverviewReader` |
| `Registry` | Source extension discovery | `DataServiceSourceRegistry` |
| `Authorizer` | 每次调用访问判定 | `DataServiceAuthorizer` |
| `RateLimiter` | 调用共享 minute-window coordination | `DataServiceRateLimiter` |
| `Compiler` | SELECT 校验和 named binding | `DataServiceSqlCompiler` |
| `Invoker` | 一次在线调用编排 | `DataServiceInvoker` |
| `Executor` | 已编译 SQL 物理执行 | `DataServiceQueryExecutor` |
| `Runtime` | node-local resilience | `LocalDataServiceRuntime` |
| `Recorder` | sanitized 调用审计写入 | `DataServiceInvocationRecorder` |
| `Repository` | Domain / coordination persistence port | `DataServiceRepository`, `DataServiceRateLimitRepository` |
| `Adapter` | persistence translation / SQL coordination | `*RepositoryAdapter` |
| `Renderer` | typed contract -> artifact | `OpenApiRenderer` |
| `Factory` | read-side projection | `DataServiceViewFactory` |

## 6. Definition / Publication / Runtime Generation

初次发布绑定 CurrentProject；source-managed ownership 规则延续 Stage 2。

`runtimeGeneration` 是 Data Service 持久化在线代际：

```text
create -> generation 1
settings/auth/policy/enable/republish mutation
       -> generation + 1
```

Cache namespace 还包含 Source Revision 与影响执行/缓存的 settings/policy shape，因此并发管理请求即便观察到同一旧 generation，也不能让不同最终配置误用同一 Cache entry。

Republish 保持：

```text
same Data Service ID
same Project ownership
preserved AuthMode / RuntimePolicy
new SourceReference / RuntimeSnapshot
new runtimeGeneration
```

## 7. Management Plane

Console 管理入口统一 `PROJECT_REQUIRED`，并按动作使用：

```text
READ / PUBLISH / MANAGE / DELETE / ACCESS / RUNTIME / OBSERVE
```

管理 Repository 的 ID/path/source/list/save/delete 绑定 `CurrentProject`。API Key / Documentation 不重复保存 projectId，但 Console 读写必须先经过父 Data Service ownership。

首页系统 Cockpit 的全局 Data Service count 是一个显式只读 aggregate corridor：只返回数量，不暴露 API identity/path/SQL/source，因此不能被 Console management 复用。

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
       +-- global DataServiceReader.requireByPath
       +-- NONE / API_KEY authorization
       +-- cluster shared rate-limit admission
       +-- pagination / SQL compile
       +-- node-local Cache/Circuit
       +-- SqlExecutionRuntime
       +-- sanitized best-effort audit
```

Public Controller 故意没有 Yak `@ProjectScope` / `@RequiresPermission`。Path 跨 Project 全局唯一。

## 9. Cluster-wide rate-limit corridor

调用限流不再使用 JVM `ConcurrentHashMap`。

```text
DataServiceAuthorizer
   -> DataServiceRateLimiter
   -> DataServiceRateLimitRepository
   -> MySQL CAS window(apiKeyId, epochMinute)
```

Repository Adapter 的 compare-and-set 保证多实例共享一份 count。协调存储异常时 fail closed。

Key rotate / disable / delete 通过 `rateLimiter.invalidate(keyId)` 清理共享窗口；旧分钟窗口由维护任务处理，不扫描调用热路径。

未来接 Redis 时只替换 Repository Adapter；Authorizer/API Key Domain 不变。

## 10. Node-local Cache / Circuit

`LocalDataServiceRuntime` 继续拥有 Caffeine Cache 与 Circuit state：

```text
Persisted RuntimePolicy + runtimeGeneration
          |
          v
LocalDataServiceRuntime per instance
          +-- Caffeine
          +-- Circuit
```

不要求跨节点主动 invalidation 才能保证正确性。每次请求重新读取持久化 Definition，cache namespace 已带 durable generation + runtime shape，新代际无法命中旧 entry。

这是**version-safe local cache**，不是 shared cache。

## 11. Cluster Runtime Metrics

Runtime status 的调用规模来自 durable evidence：

```text
raw call log (recent)
      +
hourly rollup (older)
      |
      v
DataServiceRuntimeMetricsRepository
```

集群指标：total/success/failure/successRate/average/last success/failure。

P95 使用最近 raw duration 的有界样本；不是全历史精确 histogram。

同一个 Runtime DTO 中 Cache/Circuit 仍来自当前节点，因此返回 `metricsScope=CLUSTER_INVOCATION_LOCAL_RESILIENCE`，明确两层 evidence 的差异。

## 12. Audit Security / Reliability

`DataServiceInvocationRecorder` 的职责顺序是：

```text
request parameters
  -> DataServiceAuditSanitizer
  -> JSON serialize
  -> length bound
  -> InvocationRecord
  -> Repository
```

Secret/PII 不能先序列化再靠 UI 隐藏。

Stage 1 的可靠性规则保持：audit persistence failure 不能把成功查询变成失败，也不能覆盖原始 SQL/auth/rate-limit 异常。

## 13. Observability Lifecycle

Raw call log 默认保留 30 天；hourly rollup 默认保留 365 天。

维护按小时、有界执行：

```text
Transaction
  INSERT ... SELECT raw hour -> hourly rollup
  DELETE same raw hour
Commit
```

任何一步失败整个小时回滚，防止双算或丢数。过期 rate-limit window 也由该维护角色统一清理。

## 14. Persistence Boundary

允许：

```text
Business Role
  -> Repository port
  -> RepositoryAdapter
  -> Mapper/PO or JdbcTemplate
```

`JdbcTemplate` 只能存在于 repository adapter 等明确 persistence boundary，用于 CAS / aggregation / transactional maintenance 等不适合简单 Mapper CRUD 的 SQL。

禁止：Controller/Access/Execution/Runtime/Observability 业务角色直接拥有 JdbcTemplate/Mapper/PO。

## 15. Cross-module Boundary

上游模块只依赖：

```text
io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider
```

Data Service 不反向依赖 Data Development implementation。物理 SQL 只走 `yak-ops-core` execution contract；Project scope 只依赖 core Project contract。

## 16. Failure Semantics

- invalid source/settings/SQL -> 参数/业务错误；
- missing Console Project / permission -> Project/RBAC error；
- invalid Public API key -> 401；
- shared RPM exceeded -> 429；
- shared rate-limit storage unavailable -> fail closed；
- node-local circuit open -> 503；
- datasource/query failure -> 原始调用失败；
- audit persistence failure -> 不改变已确定调用结果；
- maintenance failure -> 保留该小时 raw evidence，下一轮可重试。

## 17. Architecture Guards

```text
DataServiceDependencyBoundaryTest
DataServiceCodeStyleConventionTest
DataServiceGovernanceContractTest
DataServiceClusterRuntimeContractTest
```

Stage 3 Guard 锁定：

- Rate Limit 不得退回 per-JVM Map；
- Cache 可以 local，但 namespace 必须带 durable generation/runtime shape；
- Audit 必须先 sanitize；
- Rollup + raw delete 必须共享 transaction boundary；
- Cluster invocation metrics 与 local resilience 必须显式区分。

## 18. 修改协议

```text
Architecture Impact
- capability owner package:
- command/read/runtime role:
- management vs invocation plane:
- persisted / cluster-coordination / node-local truth:
- project ownership impact:
- new dependency edge:
- REST/DB compatibility impact:
```

如果引入新的顶层 package edge，需要同步更新 `DEPENDENCIES.md` 和可执行 Guard；不允许只改测试白名单。
