# Datasource Domain

> 本文件只保留**实现必须遵守的硬规则**。`REQUIREMENTS.md` 定义需要什么；`DOMAIN.md` 定义不能违反什么；`REVIEW.md` 定义怎么判卷。

## 核心模型

```text
DataSourceDefinition (Aggregate Root)
└── ConnectionProfile

Catalog Subdomain
├── CatalogReadRequest
├── CatalogTableQuery / CatalogTablePath
├── CatalogTable / CatalogColumn
└── CatalogQueryResult

SQL Execution
├── yak-ops-core: SqlExecutionRequest / Plan / Result / Snapshot
├── SqlExecutionAggregate          <- lifecycle truth
└── SqlExecutionGateway            <- physical execution Port
```

```text
Application
   ├── DataSourceRepository      -> Repository Adapter -> DAO / PO
   ├── DataSourcePluginGateway   <- SPI Adapter -> Plugin SPI
   ├── DataSourceCatalogGateway  <- SPI Adapter -> Plugin SPI
   └── SqlExecutionGateway       <- SPI Adapter -> Execution SPI
```

Datasource Plugin SPI 是邻接上下文，不是 Core Domain，也不是 Application Contract。

## 12 条硬规则

1. **`DataSourceDefinition` 是数据源配置唯一业务事实。** DTO、VO、PO、Plugin SPI Model 不能替代 Domain。
2. **`ConnectionProfile` 是连接配置值对象。** 修改连接配置必须通过 Aggregate 行为；`dbType` 创建后不可修改；配置变化后状态回到 `UNKNOWN`。
3. **连接状态只描述当前已保存配置最近一次真实连接测试。** 成功 `CONNECTED`，连通失败 `DISCONNECTED`；未保存配置测试不持久化状态。
4. **Core Domain 不依赖 Spring / MyBatis / HTTP DTO / VO / PO / Datasource Plugin SPI。** Secret、JDBC、插件私参属于边界之外。
5. **Application 主链路只依赖 Business Port。** `DataSourceServiceImpl`、`DataSourceCatalogServiceImpl`、`DataSourceViewMapper`、`DefaultSqlExecutionRuntime` 不直接依赖 Datasource Plugin SPI。
6. **Business Gateway Contract 不暴露 Plugin SPI、HTTP DTO / VO、PO。** SPI 类型转换与异常映射只在 `gateway.adapter`。
7. **Catalog 内部必须类型化。** HTTP `Map<String,Object>` 只允许在兼容入口解析成 `CatalogReadRequest`；`DataSourceCatalogGateway` 禁止 Map 协议。旧 Plugin SPI Map 只能由 SPI Adapter 投影。
8. **Catalog Metadata 是 Business-owned Domain。** `CatalogTable / CatalogColumn / CatalogQueryResult` 不以 SPI Table / Column / QueryResult 为 identity。
9. **SQL Execution 不复制 `yak-ops-core` contract。** Request / Plan / Result / Snapshot 的公共真相继续由 core 提供；Datasource 只拥有生命周期与物理数据源适配。
10. **`SqlExecutionAggregate` 拥有执行和 Statement 状态变化。** Runtime 负责线程、Future、Session、I/O；不得重新在 Runtime 中维护第二套 PENDING/RUNNING/终态状态机。
11. **物理 SQL 只能通过 `SqlExecutionGateway`。** `DefaultSqlExecutionRuntime` 不直接持有 `DataSourceExecutionProvider / DataSourceSqlExecutor / DataSourceSqlRequest / DataSourceSqlResult`。
12. **无法表达现有需求就是 `Domain Gap`。** 不通过隐藏 Map key、临时 boolean、VO 字段或 Plugin 私有字段绕过模型。

## DataSource 不变量

- `name / dbType / environment / ConnectionProfile.normalizedJson` 必须存在。
- `dbType` 不可变。
- ConnectionProfile 变化 -> `UNKNOWN`。
- 已保存连接测试成功 -> `CONNECTED`；连通失败 -> `DISCONNECTED`。
- Secret、完整连接 JSON、可能携带凭据的 JDBC URL 不得进入普通日志或 `toString()`。

## Catalog 不变量

```text
HTTP Map
  -> Application parse once
  -> CatalogReadRequest
  -> read-only validation
  -> DataSourceCatalogGateway
  -> SpiDataSourceCatalogGateway
  -> legacy Plugin Map
```

- `TABLE` 模式必须有 `tablePath`；`SQL` 模式必须有 `sql`。
- preview / count / describe 的 SQL 模式只允许单条 SELECT。
- `paramsList` 等历史字段只在兼容入口和 Adapter 之间映射，不是 Domain extension point。
- 新 Catalog 语义先扩 typed Domain，不增加隐藏 Map key。

## SQL Execution 生命周期

```text
PENDING
  -> RUNNING
  -> CANCELLING
  -> SUCCEEDED | FAILED | CANCELLED | TIMED_OUT
```

Statement 终态为 `SUCCEEDED / FAILED / CANCELLED / TIMED_OUT / SKIPPED`。

- Cancel 请求不能把已终态执行重新打开。
- 失败/取消/超时后，未执行 Statement 必须进入 `SKIPPED`。
- Statement timeout 必须使 Execution 收敛到 `TIMED_OUT`。
- `SINGLE_TRANSACTION` 的 begin/execute/commit/rollback 必须使用同一 Gateway Session。
- 物理 cancel 是 best-effort；领域终态不能悬空。

## Gateway / Adapter 边界

允许：

```text
Application / Runtime -> Business Gateway Port <- SPI Adapter -> Datasource Plugin SPI
```

禁止：

```text
Application / Runtime -> Datasource Plugin SPI
Business Gateway Port -> SPI model
Catalog Domain -> HTTP Map / SPI model
SqlExecutionAggregate -> Spring / concurrency / physical Session
Core Domain -> Gateway / SPI
```

当前兼容例外：

- `DataSourcePluginConfigServiceImpl` 仍承接历史 `pluginConfig() -> DataSourcePluginConfigVO`。
- `BusinessDataSourceExecutionProvider` 是向 Task Plugin SPI 暴露 Executor 的外向 Adapter。
- `DataSourceSecretCodec` 是 SPI Adapter technical helper。

例外不得扩散到新的主链路。

## 持久化兼容

`yak_ops_data_source` 以及 `jdbc_url / connection_params / original_json / conn_status` 保持现状；它们是持久化投影，由 Repository Adapter 映射。

## 修改前后

```text
Domain Impact Analysis
- Aggregate(s):
- Invariant/lifecycle impact:
- Layer:
- Domain Gap: yes/no

Domain Compliance Report
- Rule changed/implemented:
- Safety/tests:
- Known gaps:
```

## 自动护栏

至少保留：

```text
Core Domain dependency guardrail
Application / Runtime -> Gateway only
Gateway Port no SPI/DTO/VO/PO
Catalog Gateway no Map protocol
Catalog typed boundary tests
SqlExecutionAggregate lifecycle tests
SqlExecutionGateway SPI adapter tests
ConnectionProfile / connection status tests
```

**不要通过删护栏或放宽文档预算绕过失败。** 规则真实变化时，同一个 PR 同步修改文档与测试。

## 已知独立 Gap

```text
Plugin Capability / Descriptor
Plugin API VO dependency
PluginConfig compatibility bridge cleanup
Plugin Catalog Map SPI 最终类型化
Aggregate physical naming cleanup
```
