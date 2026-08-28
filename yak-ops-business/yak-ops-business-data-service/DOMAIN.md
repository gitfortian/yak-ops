# Data Service Domain

> `REQUIREMENTS.md` 定义需要什么；`ARCHITECTURE.md` 定义职责归属；本文件定义**业务事实、Truth Owner 和不变量**。无法用当前模型表达需求时报告 `Domain Gap`。

## 1. 核心模型

```text
DataServiceDefinition (Aggregate Root)
├── projectId
├── DataServiceSettings
├── PublishedRuntimeSnapshot
├── SourceReference
├── RuntimePolicy
└── AuthMode

Access
└── DataServiceApiKey

Documentation
└── DataServiceDocumentation

Observability
└── InvocationRecord(projectId snapshot)

Invocation Result
└── DataServiceQueryResponse
```

## 2. 三层 Truth

Data Service 最重要的领域边界不是“Controller/Service/DAO”，而是三种不同生命周期的事实。

### 2.1 Upstream Revision Truth

由 `DataServiceSourceProvider` 解析：

```text
sourceType + sourceRef
        |
        v
immutable source revision
        |
        +-- sourceRevisionId / No
        +-- SQL
        +-- dataSourceId
        +-- service-facing definition
        +-- request/response contract
```

它决定**发布时应该复制什么**，但不是 Data Service Runtime 的在线查询入口。

### 2.2 Persisted Data Service Truth

`DataServiceDefinition` 是稳定 Data Service 的 Aggregate Root。

- `projectId`：Yak Ops Management Plane 所属 Project Space。
- `DataServiceSettings`：name/path/maxRows/timeout/enabled/description/pagination。
- `PublishedRuntimeSnapshot`：已发布的 `dataSourceId + SQL`。
- `SourceReference`：来源类型、引用、固定 Revision。
- `RuntimePolicy`：Cache/Circuit 的持久化策略。
- `AuthMode`：NONE/API_KEY。

运行调用读取这里的持久化快照，不在每次请求时重新解析上游 Source。

`projectId` 是 Console ownership，不进入 Public Runtime URL。外部调用通过全局唯一 path 找到 Definition 后，自然获得该服务的 Project identity；调用方不能通过 Project Header 选择或覆盖服务归属。

### 2.3 Process-local Runtime Truth

`LocalDataServiceRuntime` 维护：

```text
Cache entries
Circuit state
Runtime metrics
```

这些状态只对当前 JVM 节点成立，不持久化，不代表集群全局事实。

## 3. DataServiceDefinition 不变量

1. Data Service ID 一旦创建就是稳定服务身份。
2. Project ownership 创建后属于稳定管理事实；republish 不改变 `projectId`。
3. name/path 必须有效；由于 Public Invocation URL 没有 Project namespace，path 跨 Project 全局唯一。
4. `PublishedRuntimeSnapshot.dataSourceId` 必须指向有效已发布 Datasource identity。
5. `PublishedRuntimeSnapshot.sql` 必须存在，并在 Publication / Execution boundary 验证为单条 SELECT。
6. `SourceReference` 固定到不可变 Revision；republish 更新引用而不是新建服务身份。
7. republish 不能重置 `AuthMode`、`RuntimePolicy` 或 Project ownership。
8. 服务侧 settings 更新不能改 SQL/dataSourceId/source revision/projectId。
9. enable/disable 只改变服务可调用性。
10. Aggregate 通过领域行为变更，不把 PO setter 暴露为业务 API。
11. PO/Mapper 不是业务事实，持久化重建由 Repository Adapter 负责。

## 4. Publication 生命周期

```text
                publish
ONLINE Source -------------> DataServiceDefinition(projectId)
                                  |
                                  | republish newer Revision
                                  v
                         same Data Service ID
                         same Project ownership
                         new SourceReference
                         new RuntimeSnapshot
                         preserved Auth/Policy
```

### Source-managed Definition

Provider 返回 `managesServiceDefinition=true` 时：

```text
Source Revision owns:
name / path / maxRows / timeout / pagination / description / contract

Data Service owns:
projectId / enabled / auth / API keys / runtime policy / local runtime state
```

客户端不能用 publish/update 请求覆盖 Source-owned 字段。

Data Development source-managed 服务的 Project ownership 必须与 owning Data Development node 一致。Legacy backfill 优先从 source node 推断；发现已持久化 ownership 冲突时应拒绝启动而不是静默迁移。

### Frozen Legacy Source

如果已持久化的历史 Source 类型不再有 Provider：

- 已发布 Runtime Snapshot 继续是有效运行事实；
- 不重新解析一个不存在的 Source；
- 不允许假装 republish 成功；
- Legacy global row 在 Project cutover 时进入 compatibility Project。

## 5. Access 不变量

`DataServiceApiKey` 包含 Key 的持久化身份和策略，但**不包含 raw secret**。

```text
raw secret --SHA-256--> keyHash -> persisted
     |
     +---------------> returned once on create/rotate

prefix ----------------------------> persisted for identification/audit
```

硬规则：

- raw secret 永不写 PO、日志、Domain `toString()` 或普通响应。
- API_KEY 模式必须至少有一个 enabled 且未过期的 Key。
- 最后一个有效 Key 不能被 disable/delete。
- rotate 替换 hash/prefix，并使本机旧 rate-limit bucket 失效。
- successful authorize 更新 `lastUsedAt`。
- rate limit bucket 是进程本地状态，不是 Aggregate。
- Console Key lifecycle 必须先通过父 Data Service 的 CurrentProject ownership；不能只凭 keyId 修改另一 Project 的 Key。
- Public Invocation 的 Key 查找属于已解析 Data Service 的访问执行过程，不依赖 Yak Console Project Header。

## 6. SQL Template / Invocation 不变量

```text
HTTP parameters
   |
   v
named parameter validation
   |
   v
CompiledSql(sql + bindings)
   |
   v
Local Runtime protection
   |
   v
SqlExecutionRuntime
```

- 只允许单条 SELECT。
- 参数绑定走 JDBC named parameter compiler，不拼接请求值。
- `DataServiceParameterNameReader` 是 query read-side 需要的窄 port；Execution 的 compiler 提供实现，Query 不反向依赖 Execution implementation。
- Pagination control 不进入 SQL bindings，但进入 cache identity。
- `DataServiceQueryResponse` 是 runtime-neutral Domain value，Runtime 不依赖 Execution package。
- `SqlExecutionRuntime` 是唯一物理 SQL 调用入口。
- Public Invocation 只允许通过全局 path resolve corridor 读取 Definition；所有 Console ID/source/list read 都是 Project-scoped。

## 7. Runtime Policy 与 State

`RuntimePolicy` 是持久化业务策略：

```text
cacheEnabled
cacheTtlSeconds
cacheMaxEntries
circuitBreakerEnabled
failureThreshold
recoverySeconds
```

`LocalDataServiceRuntime` 根据 Policy 构造本地状态：

```text
RuntimePolicy change
       |
       v
invalidate cache / reset affected circuit
       |
       v
new local state follows persisted policy
```

本机 Metrics 只用于诊断，不写回 Aggregate。

## 8. Documentation 不变量

`DataServiceDocumentation` 是显式维护的契约元数据：

- SQL fingerprint；
- parameter docs；
- response field docs；
- update time。

当前 SQL 参数名是 parameter docs 的事实来源：

```text
current SQL names + saved docs
          |
          v
merge descriptions/examples by name
          |
          v
current parameter contract
```

删除的 SQL 参数不能继续出现在当前文档；新增参数必须出现，即使只有默认 type。

Documentation 自身不重复存 Project identity；Console reader/manager 必须先通过父 Data Service ownership，再读写文档 projection。

## 9. InvocationRecord

`InvocationRecord` 是调用审计事实，而不是执行生命周期 Aggregate。

它保存调用完成后的快照：

- Project ID；
- 服务身份/名称/Path；
- Caller/API Key identification；
- 参数 JSON；
- success/duration/rows/error；
- 时间。

Project ID 必须在 Invocation 时从 resolved Data Service Definition 快照下来。历史记录不因 Data Service 后续改名、删除或 Key rotate 而重写，因此 Overview/Logs 在服务删除后仍能按 Project 归属查询。

## 10. Management vs Invocation boundary

```text
Console request
  -> PROJECT_REQUIRED
  -> Project membership
  -> Data Service RBAC
  -> project-scoped repository

External request
  -> global runtime path
  -> NONE/API_KEY
  -> global findByRuntimePath only
  -> resolved Definition(projectId)
```

Project Header 不是 Public Invocation authorization input，也不能被外部客户端用于跨 Project 选择服务。

## 11. Persistence Projection

```text
Database PO
   |
   v
Repository Adapter
   |
   v
Domain
```

允许 `dao/model` 使用 Lombok bean/setter 适配 ORM；Domain 不因此恢复为 `@Data` 贫血 Bean。

管理 Repository 的 `findById/findByPath/findBySource/findAll/save/delete` 必须绑定 CurrentProject。`findByRuntimePath` 是唯一显式 global read corridor，且只服务 Invocation Plane。

## 12. 修改协议

```text
Domain Impact Analysis
- Aggregate/value object:
- Truth owner:
- invariant/lifecycle impact:
- process-local vs persisted impact:
- Project/Invocation plane impact:
- Domain Gap: yes/no

Domain Compliance Report
- rules preserved/changed:
- tests/guards:
- known gaps:
```

领域规则真实变化时，同一 PR 更新 Requirements / Domain / behavior tests；不要删除或放宽 Guard 来掩盖模型冲突。
