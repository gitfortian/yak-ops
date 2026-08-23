# Yak Ops Datasource

数据源模块负责数据源注册、连接测试、Catalog 元数据访问、SQL Execution 和数据源插件编排，并为数据开发、同步、任务等上层模块提供稳定的数据源引用。

## 开发前必读

```text
REQUIREMENTS.md  -> 模块需要什么
DOMAIN.md        -> 实现不能违反什么
REVIEW.md        -> 按什么标准 Review
```

三份文档只维护当前有效规则，历史设计过程看 Issue / PR / Git。

## 核心模型

```text
DataSourceDefinition (Aggregate Root)
└── ConnectionProfile

Catalog Domain
├── CatalogReadRequest
├── CatalogTableQuery / CatalogTablePath
├── CatalogTable / CatalogColumn
└── CatalogQueryResult

SQL Execution
├── yak-ops-core canonical Request / Plan / Result / Snapshot
├── SqlExecutionAggregate
└── SqlExecutionGateway
```

关键规则：数据源类型创建后不可修改；连接配置变化回到 `UNKNOWN`；Secret 不输出；Catalog 内部 typed；SQL 生命周期由 Aggregate 管理，物理执行经 Gateway。

## 工程分层

```text
Controller / Application
      ↓
Domain + Business Gateway Port
      ↓                     ↑
Repository Adapter      SPI Gateway Adapter
      ↓                     ↓
DAO / PO             Datasource Plugin SPI
```

主要边界：

```text
DataSourceServiceImpl -> DataSourcePluginGateway <- SpiDataSourcePluginGateway
DataSourceCatalogServiceImpl -> DataSourceCatalogGateway <- SpiDataSourceCatalogGateway
DefaultSqlExecutionRuntime -> SqlExecutionGateway <- SpiSqlExecutionGateway
```

SPI Adapter 负责插件发现、Connection 解析、Secret 处理、Catalog 协议转换、SQL Executor 适配和异常边界；Application / Runtime 不直接持有 Datasource Plugin SPI。

## Catalog 类型化

现有 REST POST 接口为兼容前端仍接受 `Map<String,Object>`，但 Map 只存在于入口：

```text
HTTP Map
  -> DataSourceCatalogServiceImpl
  -> CatalogReadRequest
  -> DataSourceCatalogGateway
  -> SpiDataSourceCatalogGateway
  -> legacy Plugin Map
```

支持的历史字段为 `read_mode/readMode`、`table_path/tablePath/table`、`query/sql`、`paramsList`。未知 Map key 不再作为 Business 扩展协议；新语义必须进入 typed Catalog Domain。

Catalog 的 Table / Column / QueryResult 由 Business 自己拥有，Plugin SPI metadata 只能在 Adapter 中转换。

## SQL Execution

`yak-ops-core` 已定义跨模块统一 SQL contract，因此 Datasource 不复制同名 DTO：

```text
SqlExecutionRequest / SqlExecutionPlan
  -> SqlExecutionPolicy
  -> SqlExecutionAggregate
  -> DefaultSqlExecutionRuntime
  -> SqlExecutionGateway
  -> Datasource execution SPI
```

职责拆分：

- `SqlExecutionAggregate`：Execution / Statement 状态、取消意图、终态和 Snapshot。
- `DefaultSqlExecutionRuntime`：线程、Future、事务编排、物理 Session、超时异常识别。
- `SqlExecutionGateway`：Datasource 物理 SQL Session Port。
- `SpiSqlExecutionGateway`：`DataSourceExecutionProvider / DataSourceSqlExecutor` 的唯一 Runtime Adapter。
- `ObservableSqlExecutionRuntime`：终态观察和审计通知，不改变底层生命周期。

## 分层约束

- Controller 只通过 Service 进入业务链路。
- Repository 只暴露 Domain，PO/MyBatis 只在持久化 Adapter 内。
- Core Domain 不依赖 Spring、MyBatis、DTO / VO / PO、Datasource Plugin SPI。
- `DataSourcePluginGateway / DataSourceCatalogGateway / SqlExecutionGateway` 不暴露 Plugin SPI、DTO / VO 或 PO。
- `DataSourceCatalogGateway` 不接受 `Map<String,Object>`。
- `DefaultSqlExecutionRuntime` 不直接依赖 datasource execution SPI。
- Catalog 只读检查仍在 Application 层；物理访问由 Adapter 执行。
- Secret 脱敏通过 `DataSourceViewMapper -> DataSourcePluginGateway` 完成。

## 兼容边界

Phase 3 不修改：

```text
REST API 路径和主要 JSON 结构
yak_ops_data_source 表结构
Flyway 历史
Datasource Plugin SPI 签名
MySQL / PostgreSQL / Oracle / Doris 等插件实现
yak-ops-core SQL Execution contract
```

当前例外：`DataSourcePluginConfigServiceImpl` 仍承接历史 PluginConfig VO；`BusinessDataSourceExecutionProvider` 是向 Task Plugin SPI 暴露 Executor 的外向 Adapter。这两项留给后续 Plugin API 标准化。

## 后续领域 Gap

```text
Plugin Capability / Descriptor
Plugin API VO dependency
PluginConfig compatibility bridge cleanup
Plugin Catalog Map SPI 最终类型化
DataSourceDefinition 物理命名清理
```
