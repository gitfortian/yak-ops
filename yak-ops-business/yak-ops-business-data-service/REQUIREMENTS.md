# Data Service Requirements

> 本文件只描述**模块需要什么**，不描述具体类和实现方式。历史讨论和迁移过程由 Issue / PR / Git 保存。

## 1. 模块目标

Data Service 将已发布的数据定义转化为稳定、可调用、可保护、可观测的只读在线数据 API。

它需要同时解决：

- 稳定服务身份与上游 Revision 更新之间的解耦；
- SQL / Datasource 来源可信；
- 调用参数安全绑定；
- API Key 鉴权和调用方识别；
- 缓存、熔断等单节点 Runtime 保护；
- 文档、OpenAPI、调用日志和运行概览；
- 不绕过 Datasource 的统一 SQL Execution。

## 2. 服务定义

- Data Service 必须有稳定 ID、名称、Path、启用状态、最大返回行数、超时、分页开关和访问模式。
- Path 在模块内唯一。
- 启用/停用不能改变已发布 SQL、Datasource 或 Source Revision。
- 删除服务时必须清理其 API Key，并移除当前节点的 Runtime state。
- Runtime/Access 配置可以独立调整，不要求重新发布上游 Revision。

## 3. Publication

```text
Source Provider
  -> immutable published revision
  -> server resolved SQL / datasource / contract
  -> stable Data Service identity
```

- 只有可发布状态的 Source 才能发布。目前要求 Source `status = ONLINE`。
- Source 必须提供有效的 `sourceRevisionId + sourceRevisionNo`。
- SQL 和 `dataSourceId` 必须由服务端 Source Provider 解析，客户端不得直接覆盖。
- 首次 publish 创建稳定 Data Service；republish 更新同一 Data Service，不创建第二个身份。
- republish 必须刷新 SourceReference 和 PublishedRuntimeSnapshot。
- republish 必须保留访问策略和 Runtime Policy。
- Provider 声明 `managesServiceDefinition=true` 时，name/path/maxRows/timeout/pagination/description 由 Source Revision 管理；Data Service 客户端不能覆盖这些定义。
- Provider 不管理服务定义时，兼容既有服务侧设置，但仍不能由客户端注入 SQL/dataSourceId。
- 历史冻结来源如果已无 Provider，可以继续使用持久化 Runtime Snapshot，但不能伪造重新发布。

## 4. SQL Execution

- Data Service 只支持**单条 SELECT**。
- SQL 必须使用命名参数绑定，不允许把请求值字符串拼接进 SQL。
- SQL 中声明的命名参数必须在调用请求中存在。
- SQL 字符串、注释、类型转换等上下文中的 `:` 不能被误识别为命名参数。
- SQL 物理执行统一调用 `yak-ops-core` 的 `SqlExecutionRuntime`。
- Caller 必须标记为 `DATA_SERVICE`，并携带 Data Service ID 作为调用上下文。
- 返回值必须是 ResultSet；非查询结果视为错误。
- 最大取数和超时使用已发布 Data Service 配置。
- Pagination 控制参数 `pageNum/pageSize/returnTotalNum` 不得作为 SQL 命名参数透传。
- `pageSize` 不能超过服务最大返回行数。

## 5. Access / API Key

- 支持 `NONE` 和 `API_KEY` 两种访问模式。
- 切换到 `API_KEY` 前必须至少存在一个有效 Key。
- Key create / rotate 时产生的 raw secret 只返回一次。
- raw secret 永不落库；持久化只保存不可逆 hash 和可识别 prefix。
- Key 可以设置名称、每分钟调用上限、过期时间和 enabled 状态。
- 已启用 `API_KEY` 时不能禁用或删除最后一个有效 Key。
- 鉴权失败返回 401；超过本节点限流返回 429。
- 成功鉴权后调用日志需要记录 Key ID / Name / Prefix 快照，不记录 raw secret。
- 当前 rate limit 是**进程本地 fixed-window 保护**，不是多节点全局配额 Truth。

## 6. Runtime Resilience

`RuntimePolicy` 持久化以下策略：

- cache enabled
- cache TTL
- cache max entries
- circuit breaker enabled
- failure threshold
- recovery seconds

要求：

- Cache、Circuit state、Runtime metrics 当前均为单进程状态。
- Runtime Policy 变化时必须清理受影响的本地缓存/熔断状态。
- 服务停用、删除、重新发布关键 Runtime 配置时必须使本机旧状态失效。
- Circuit 打开时返回 503。
- Cache key 必须覆盖 compiled SQL 和绑定值；分页控制也必须进入 key，避免页间串数据。
- 本地 Metrics 可以用于运行诊断，但不能反向覆盖持久化业务状态。

## 7. Documentation / OpenAPI

- 当前 SQL 中的命名参数集合是请求参数文档的唯一事实来源。
- 文档不能声明 SQL 中不存在的参数。
- SQL 新增参数但文档未填写时，必须生成安全默认描述，而不是丢掉参数。
- SQL 指纹变化时文档需要可识别 `schemaStale`。
- Response field 文档是显式业务契约，不从单次查询结果自动永久推断。
- OpenAPI 必须反映当前 runtime path、鉴权模式、参数和响应字段。
- 文档 schema type 必须来自明确 allowlist；未知类型不能静默降级。

## 8. Observability

每次真实调用必须能记录：

- Data Service ID / 名称 / Path 快照；
- Caller 类型；
- API Key ID / 名称 / Prefix（如果存在）；
- 请求参数 JSON（受长度限制）；
- 成功/失败；
- duration；
- row count；
- error message；
- create time。

运行概览需要支持 `24h / 7d / 30d`，至少提供 API 数量、启停数量、调用量、成功率、平均耗时、返回行数、趋势、热点 API 和近期失败。

## 9. 模块边界

本模块负责：

- Data Service definition；
- Source publication / republish；
- Data Service invocation；
- API Key / auth / local rate limit；
- local cache / circuit / metrics；
- API documentation / OpenAPI；
- invocation audit / overview。

本模块不负责：

- Data Development 的草稿/Revision 创作；
- Datasource connection/catalog/plugin 管理；
- Offline / Realtime Sync 调度；
- 任意 DML/DDL；
- 分布式缓存 Truth；
- 多节点全局限流；
- 数据血缘或质量计算。

## 10. 兼容性要求

结构重构默认保持：

```text
/api/v1/data-service REST 路径
现有主要 JSON shape
yak_ops_data_service_* 表结构与 Flyway
yak-ops-core SqlExecutionRuntime contract
Datasource Plugin / Catalog contract
Data Development SourceProvider 业务语义
401 / 429 / 503 HTTP 状态语义
```

## 11. 需求变更协议

本文件没有描述的新业务行为统一报告：

```text
Requirement Gap
- requested behavior:
- affected API/domain:
- compatibility impact:
- proposed requirement update:
```

需求确认并更新本文件之后再改生产行为；Reviewer / AI 不得从实现细节反推并偷偷补需求。
