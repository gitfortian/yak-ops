# Datasource Domain

> 本文件只保留**实现必须遵守的硬规则**。`REQUIREMENTS.md` 定义需要什么；`DOMAIN.md` 定义不能违反什么；`REVIEW.md` 定义怎么判卷。

## 核心模型

```text
DataSourceDefinition (Aggregate Root)
└── ConnectionProfile

Catalog Domain
├── CatalogReadRequest
├── CatalogTableQuery / CatalogTablePath
├── CatalogTable / CatalogColumn
└── CatalogQueryResult

Plugin Domain Projection
└── DataSourcePluginDescriptor

SQL Execution
├── yak-ops-core: Request / Plan / Result / Snapshot
├── SqlExecutionAggregate        <- lifecycle truth
└── SqlExecutionGateway          <- physical execution Port
```

```text
Application
   ├── DataSourceRepository      -> Repository Adapter -> DAO / PO
   ├── DataSourcePluginGateway   <- SPI Adapter -> Plugin SPI Descriptor
   ├── DataSourceCatalogGateway  <- SPI Adapter -> typed Catalog SPI
   └── SqlExecutionGateway       <- SPI Adapter -> Execution SPI
```

Datasource Plugin SPI 是邻接上下文，不是 Core Domain，也不是 HTTP Contract。

## 12 条硬规则

1. **`DataSourceDefinition` 是数据源配置唯一业务事实。** DTO、VO、PO、Plugin SPI Model 不得替代 Domain。
2. **`ConnectionProfile` 是连接配置值对象。** `dbType` 创建后不可变；连接配置变化后状态回到 `UNKNOWN`；已保存配置真实测试才产生 `CONNECTED / DISCONNECTED`。
3. **Core Domain 不依赖 Spring / MyBatis / HTTP DTO / VO / PO / Datasource Plugin SPI。** Secret、JDBC、插件私参属于边界之外。
4. **Application / Runtime 主链路只依赖 Business Port。** Plugin Registry、Plugin SPI、Secret helper 不进入受保护 Service / Runtime。
5. **Business Gateway Contract 不暴露 Plugin SPI、HTTP DTO / VO、PO。** SPI 类型转换和异常映射只在 `gateway.adapter`。
6. **Catalog Business 和 Plugin SPI 都必须类型化。** HTTP Map 只允许在兼容入口解析一次；`DataSourceCatalogGateway` 与 `DataSourceCatalog` 均禁止 Map 协议。
7. **Catalog Metadata 是 Business-owned Domain。** Plugin Table / Column / QueryResult 必须经 Adapter 转为 `CatalogTable / CatalogColumn / CatalogQueryResult`。
8. **Plugin Descriptor 是插件元数据唯一稳定协议。** Plugin API 不依赖 `DataSourcePluginConfigVO`；Business `DataSourcePluginDescriptor` 是反腐投影，HTTP VO 只在 View Mapper 生成。
9. **Plugin Capability 必须显式声明且与实现一致。** `TRANSACTIONS -> SQL_EXECUTION`；`CATALOG_READ -> CATALOG_METADATA`；调用方不得用异常探测正常能力。
10. **SQL Execution 不复制 `yak-ops-core` contract。** `SqlExecutionAggregate` 拥有 Execution/Statement 生命周期；Runtime 只负责并发、事务编排、Session I/O 和异常识别。
11. **物理 SQL 只能通过 `SqlExecutionGateway`。** Runtime 不直接持有 Datasource execution SPI；事务、取消、关闭必须经同一 Gateway Session。
12. **无法表达现有需求就是 `Domain Gap`。** 不通过隐藏 Map key、临时 boolean、VO 字段或 Plugin 私有字段绕过模型。

## DataSource 不变量

- `name / dbType / environment / ConnectionProfile.normalizedJson` 必须存在。
- `dbType` 不可变。
- ConnectionProfile 变化 -> `UNKNOWN`。
- 已保存连接测试成功 -> `CONNECTED`；连通失败 -> `DISCONNECTED`。
- 未保存配置测试不持久化状态。
- Secret、完整连接 JSON、可能携带凭据的 JDBC URL 不进入普通日志或 `toString()`。

## Catalog 不变量

```text
HTTP Map
  -> CatalogReadRequest
  -> read-only validation
  -> DataSourceCatalogGateway
  -> DataSourceCatalogReadRequest
  -> DataSourceCatalog
```

- `TABLE` 必须有 `tablePath`；`SQL` 必须有 `sql/query`。
- preview / count / describe 的 SQL 模式只允许单条 SELECT。
- HTTP `paramsList` 只在兼容入口解析为 typed variable；空变量值维持历史“忽略替换”语义。
- Plugin SPI 不再接受 `Map<String,Object>`。
- 新 Catalog 语义扩 typed Domain / SPI，不新增隐藏 key。

## Plugin Contract

```text
DataSourcePlugin
├── dbType
├── descriptor
│   ├── apiVersion
│   ├── capabilities
│   └── connectionForm
├── parseConnection
├── testConnection
├── createCatalog
└── createSqlExecutor (when SQL_EXECUTION)
```

- `descriptor.dbType == plugin.dbType()`。
- 当前 `apiVersion = 1`。
- `TRANSACTIONS` 只能和 `SQL_EXECUTION` 一起声明。
- `CATALOG_READ` 只能和 `CATALOG_METADATA` 一起声明。
- Secret 字段通过 `FieldType.PASSWORD` 声明，Business Secret Codec 从 Descriptor 推导。
- Plugin 单例不持有请求级 Connection/Statement 状态。
- PluginConfig HTTP shape 由 `DataSourcePluginViewMapper` 投影，不是 SPI 模型。
- 详细插件开发规则见 `yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN.md`。

## SQL Execution 生命周期

```text
PENDING -> RUNNING -> CANCELLING
                   -> SUCCEEDED | FAILED | CANCELLED | TIMED_OUT
```

- 终态不能重新打开。
- 失败/取消/超时后未执行 Statement -> `SKIPPED`。
- Statement timeout -> Execution `TIMED_OUT`。
- `SINGLE_TRANSACTION` begin/execute/commit/rollback 使用同一 Session。
- physical cancel best-effort，但领域终态必须收敛。

## 允许的依赖方向

```text
Application / Runtime -> Business Port <- SPI Adapter -> Datasource Plugin SPI
Plugin SPI Descriptor -> SPI Adapter -> Business Plugin Descriptor -> View Mapper -> HTTP VO
```

禁止：

```text
Plugin API -> Business DTO / VO
Application / Runtime -> Plugin SPI
Business Port -> SPI Model
Catalog Domain -> HTTP Map / SPI Model
SqlExecutionAggregate -> Spring / concurrency / physical Session
```

唯一保留的明确 SPI 例外：`BusinessDataSourceExecutionProvider` 本身是向 Task Plugin SPI 暴露 Executor 的外向 Adapter；该例外不得扩散到 Application 主链路。

## 持久化兼容

`yak_ops_data_source` 及 `jdbc_url / connection_params / original_json / conn_status` 保持现状；它们是持久化投影，由 Repository Adapter 映射。

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
Catalog Business + Plugin SPI no Map
Plugin API no Business VO
Descriptor / Capability contract tests
SqlExecutionAggregate lifecycle tests
ConnectionProfile / connection status tests
```

**不要通过删护栏或放宽文档预算绕过失败。** 规则真实变化时，同一 PR 同步修改文档与测试。

## 已知独立 Gap

```text
DataSourceDefinition 物理命名清理
```
