# Yak Ops Datasource

数据源模块负责数据源注册、连接测试、Catalog 元数据访问和数据源插件编排，并为数据开发、同步和任务等上层模块提供稳定的数据源引用。

## 开发前必读

```text
REQUIREMENTS.md  -> 模块需要什么
DOMAIN.md        -> 实现不能违反什么
REVIEW.md        -> 按什么标准 Review
```

三份文档只维护当前有效规则，历史设计过程看 Issue / PR / Git。

## 核心领域模型

```text
DataSourceDefinition  (Aggregate Root，当前保留历史命名)
├── identity / name / dbType / environment
├── ConnectionProfile
│   ├── jdbcUrl
│   ├── normalizedJson
│   └── originalJson
├── connectionStatus
├── remark
└── timestamps
```

关键业务规则：

- 数据源类型创建后不可修改。
- 新建数据源连接状态为 `UNKNOWN`。
- 修改连接配置后，旧连接测试结果失效并回到 `UNKNOWN`。
- 已保存数据源连接测试成功进入 `CONNECTED`，失败进入 `DISCONNECTED`。
- 未保存配置的连接测试不产生持久化状态。
- Secret、完整连接 JSON 和可能携带凭据的 JDBC URL 不得通过 `toString()` 或普通日志输出。

详细规则见 `DOMAIN.md`。

## 工程分层

```text
Controller
    ↓
Application Service / View Mapper
    ↓
Domain + Business Gateway Port
    ↓                 ↑
Repository Adapter    │
    ↓             SPI Gateway Adapter
DAO / PO              ↓
                  Datasource Plugin SPI
                         ↓
                  Plugin Implementation
```

Phase 2 后，数据源管理和 Catalog 主链路不再直接认识 `DataSourcePlugin`、`DataSourceConnection`、`DataSourceCatalog` 或 SPI Metadata Model：

```text
DataSourceServiceImpl
    -> DataSourcePluginGateway
    <- SpiDataSourcePluginGateway
    -> Datasource Plugin SPI

DataSourceCatalogServiceImpl
    -> DataSourceCatalogGateway
    <- SpiDataSourceCatalogGateway
    -> Datasource Plugin SPI
```

SPI Adapter 负责：

- 插件发现与路由；
- Connection 解析与规范化；
- Secret 合并和展示脱敏；
- 连接测试异常映射；
- Catalog 创建和调用；
- SPI Table / Column / QueryResult -> Business Gateway Contract 转换。

## 分层约束

- Controller 只通过 Service 进入业务链路，不直接依赖 Repository、DAO、Mapper 或插件实现。
- Application Service 使用 Domain + Repository Port + Business Gateway，不直接注入 Datasource Plugin Registry。
- `DataSourcePluginGateway` / `DataSourceCatalogGateway` 不暴露 Plugin SPI、HTTP DTO / VO 或 PO。
- SPI 具体模型只能存在于 `gateway.adapter`、plugin registry/helper 或明确的外向 SPI Adapter 中。
- Repository 接口只暴露 Domain；PO 仅存在于 Adapter / DAO / Mapper 持久化层。
- Core Domain 不依赖 Spring、MyBatis、HTTP DTO / VO / PO 或 Datasource Plugin SPI。
- HTTP 详情通过 `DataSourceViewMapper -> DataSourcePluginGateway` 完成连接地址与连接 JSON 脱敏。
- Catalog 的只读校验仍由 Application Service 承担，物理 Catalog 访问和 SPI 模型转换由 Gateway Adapter 承担。
- `Map<String, Object>` Catalog 请求是当前兼容协议，不允许继续用新增隐式 key 扩展业务语义；Phase 3 负责类型化。

## 当前兼容边界

Phase 2 不修改：

```text
REST API
DTO / VO JSON 结构
yak_ops_data_source 表结构
Flyway 历史
Datasource Plugin SPI 签名
MySQL / PostgreSQL / Oracle / Doris 等插件实现
```

两个明确的兼容例外：

- `DataSourcePluginConfigServiceImpl` 当前仍承接 `pluginConfig() -> DataSourcePluginConfigVO` 历史协议；插件 Descriptor / Form Model 去 VO 化留给后续 Plugin API 标准化。
- `BusinessDataSourceExecutionProvider` 本身是向 Task Plugin SPI 暴露 SQL Executor 的 Adapter，因此允许在 Adapter 边界依赖相关 SPI。

## 持久化

当前数据源管理继续维护现有业务表：

```text
yak_ops_data_source
```

`jdbc_url / connection_params / original_json / conn_status` 是持久化投影，不是新的领域边界。

业务模块共享 `yak.database` 对应的 `yakBusinessDataSource`、MyBatis SessionFactory 和事务管理器；数据源模块继续拥有自己的 Flyway migration location/history 边界。

## 后续领域 Gap

当前明确留给后续阶段处理：

```text
Catalog Domain Model / typed request protocol
SQL Execution Domain Model
Plugin Capability / Descriptor
Plugin API VO dependency
PluginConfig compatibility bridge cleanup
Aggregate physical naming cleanup
```
