# Yak Ops Datasource

Datasource 负责数据源注册、连接测试、typed Catalog、SQL Execution 与 Plugin 编排，并向数据开发、同步、任务等模块提供稳定数据源引用。

## 开发前必读

```text
REQUIREMENTS.md  -> 模块需要什么
DOMAIN.md        -> 实现不能违反什么
REVIEW.md        -> 按什么标准 Review
PLUGIN.md        -> Datasource Plugin 开发标准
```

Plugin 文档位于：`yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN.md`。

## 核心模型

```text
DataSourceDefinition -> ConnectionProfile

Catalog Domain
  -> CatalogReadRequest / Table / Column / QueryResult

Plugin Domain Projection
  -> DataSourcePluginDescriptor

SQL Execution
  -> yak-ops-core Request / Plan / Result / Snapshot
  -> SqlExecutionAggregate
  -> SqlExecutionGateway
```

## 最终依赖方向

```text
Controller / Application
      ↓
Domain + Business Port
      ↓                       ↑
Repository Adapter        SPI Adapter
      ↓                       ↓
DAO / PO             Datasource Plugin SPI
                          ↓
                   Plugin Implementation
```

主要链路：

```text
DataSourceServiceImpl
  -> DataSourcePluginGateway
  <- SpiDataSourcePluginGateway
  -> DataSourcePlugin / Descriptor

DataSourceCatalogServiceImpl
  -> CatalogReadRequest
  -> DataSourceCatalogGateway
  <- SpiDataSourceCatalogGateway
  -> DataSourceCatalogReadRequest
  -> DataSourceCatalog

DefaultSqlExecutionRuntime
  -> SqlExecutionAggregate
  -> SqlExecutionGateway
  <- SpiSqlExecutionGateway
  -> Datasource execution SPI
```

## Plugin 标准

Phase 4 后 Plugin API 不再返回 HTTP VO：

```text
DataSourcePlugin
  -> descriptor(apiVersion, capabilities, connectionForm)
  -> parseConnection
  -> testConnection
  -> createCatalog
  -> createSqlExecutor
```

`DataSourcePluginDescriptor` 是 SPI 唯一插件元数据协议；Business Gateway 将其映射为 Business-owned Descriptor，`DataSourcePluginViewMapper` 再投影成现有 `DataSourcePluginConfigVO`。因此前端 JSON shape 保持不变，但 Plugin API 与 HTTP VO 解耦。

当前 Capability：

```text
CONNECTION_TEST
CATALOG_METADATA
CATALOG_READ
SQL_EXECUTION
TRANSACTIONS
SSH_TUNNEL
```

Registry 在加载时校验 apiVersion、dbType 和 Capability 依赖。Secret 字段由 Descriptor 的 `PASSWORD` field 声明。

## Catalog 类型化

REST 为兼容旧前端仍可接受 `Map<String,Object>`，但 Map 到此为止：

```text
HTTP Map
  -> DataSourceCatalogServiceImpl
  -> CatalogReadRequest
  -> DataSourceCatalogGateway
  -> DataSourceCatalogReadRequest
  -> Plugin Catalog
```

Business Gateway 和 Plugin `DataSourceCatalog` 都不接受 Map。历史 `paramsList` 被解析为 typed variable；新增语义必须扩 typed model。

## SQL Execution

```text
SqlExecutionRequest / SqlExecutionPlan (yak-ops-core)
  -> SqlExecutionPolicy
  -> SqlExecutionAggregate
  -> DefaultSqlExecutionRuntime
  -> SqlExecutionGateway
  -> Datasource execution SPI
```

- Aggregate：Execution / Statement 状态、取消意图、终态、Snapshot。
- Runtime：线程、Future、事务编排、物理 Session、超时识别。
- Gateway：物理 SQL Session Port。
- Adapter：唯一 execution SPI 翻译边界。

## 分层约束

- Core Domain 不依赖 Spring、MyBatis、DTO / VO / PO、Plugin SPI。
- Repository 只暴露 Domain。
- Application / Runtime 不直接依赖 Plugin Registry / SPI。
- Business Gateway 不暴露 SPI、DTO / VO / PO。
- Business Catalog 和 Plugin Catalog 都不接受 `Map<String,Object>`。
- Plugin API 不依赖 `DataSourcePluginConfigVO`。
- PluginConfig HTTP VO 只由 Business View Mapper 生成。
- Default SQL Runtime 不直接依赖 datasource execution SPI。
- Secret 不进入普通日志、异常、`toString()` 或未脱敏响应。

## Phase 4 兼容边界

保持：

```text
REST API 路径和主要 JSON shape
yak_ops_data_source 表结构
Flyway 历史
yak-ops-core SQL Execution contract
内置数据库插件运行行为
```

Phase 4 有意做一次 Plugin SPI source-level migration：

```text
pluginConfig() -> descriptor()
Catalog Map -> DataSourceCatalogReadRequest
```

仓库内置插件同步迁移；第三方插件按 `PLUGIN.md` 升级。该迁移不改变 REST / DB。

## 持久化

`yak_ops_data_source` 以及 `jdbc_url / connection_params / original_json / conn_status` 仍是持久化投影，由 Repository Adapter 映射。

## 剩余独立 Gap

```text
DataSourceDefinition 物理命名清理
```
