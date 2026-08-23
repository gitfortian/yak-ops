# Datasource Domain

> 本文件只保留**实现必须遵守的硬规则**，不记录设计过程。历史演进看 Git / PR。
>
> `REQUIREMENTS.md` 定义“需要什么”；`DOMAIN.md` 定义“不能违反什么”；`REVIEW.md` 定义“怎么判卷”。

## 核心模型

```text
DataSourceDefinition  (Aggregate Root，当前保留历史命名)
├── id / name / dbType / environment
├── ConnectionProfile
│   ├── jdbcUrl
│   ├── normalizedJson
│   └── originalJson
├── connectionStatus
├── remark
└── timestamps
```

```text
Application Service
   │
   ├── DataSourceRepository         -> Repository Adapter -> DAO / PO
   │
   ├── DataSourcePluginGateway      <- SPI Plugin Adapter  -> Plugin SPI
   │
   └── DataSourceCatalogGateway     <- SPI Catalog Adapter -> Plugin SPI
```

Datasource Plugin SPI 是**邻接上下文**，不是 Core Domain，也不是 Application Contract。

## 12 条硬规则

1. **`DataSourceDefinition` 是 Business Datasource 当前唯一数据源业务事实。** DTO、VO、PO、Plugin SPI Model 都只是 Adapter / Projection / Protocol Model。
2. **`ConnectionProfile` 是连接配置的领域值对象。** 新业务代码修改连接配置必须经过 Aggregate 行为，不直接散落修改 `jdbcUrl / connectionParams / originalJson`。
3. **DataSource Type 创建后不可修改。** 更新时请求类型必须与 Aggregate 当前 `dbType` 一致。
4. **连接配置被替换后，连接状态必须回到 `UNKNOWN`。** 旧配置的 `CONNECTED / DISCONNECTED` 不能继承给新配置。
5. **`CONNECTED / DISCONNECTED` 只表示最近一次针对当前已保存配置的连接测试结果。** 配置可解析不等于连接成功；未保存配置测试不持久化状态。
6. **Core Domain 不依赖 Spring / MyBatis / HTTP DTO / VO / PO / Datasource Plugin SPI。** JDBC、Catalog 实现和插件私有参数属于边界之外。
7. **Application 主链路不得直接依赖 Datasource Plugin SPI 或 `DataSourcePluginRegistry`。** 数据源管理通过 `DataSourcePluginGateway`，Catalog 通过 `DataSourceCatalogGateway`；SPI 类型转换与异常映射只在 Adapter 内完成。
8. **Business Gateway Contract 不暴露 Plugin SPI、HTTP DTO / VO 或 PO。** Gateway 内部模型属于边界协议，不可被当成新的全局领域事实。
9. **Repository Contract 只暴露 Domain。** PO、MyBatis `IPage`、Mapper Row 不得越过 Repository Adapter。
10. **Secret 不是可观察业务数据。** 完整连接 JSON、密码、Token 不得进入 `toString()`、普通日志或未脱敏响应；插件相关 Secret 处理必须留在 Gateway Adapter 边界。
11. **新的业务语义优先扩明确领域模型。** 不通过新增 `Map<String, Object>` key、临时 boolean、展示 VO 字段或 SPI 私有字段绕过 Domain Gap。
12. **无法映射现有模型就是 `Domain Gap`。** 先讨论模型和边界，不用 DTO / VO / PO / Plugin Model / Gateway 临时字段冒充 Domain。

## 关键不变量

- `name`、`dbType`、`environment`、`ConnectionProfile.normalizedJson` 必须存在。
- `dbType` 一旦创建不可变。
- `ConnectionProfile` 变更后 `connectionStatus = UNKNOWN`。
- 已保存配置测试成功：`connectionStatus = CONNECTED`。
- 已保存配置测试失败：`connectionStatus = DISCONNECTED`。
- 未保存配置测试不修改 Aggregate。
- `DataSourceServiceImpl / DataSourceCatalogServiceImpl / DataSourceViewMapper` 不直接出现 Datasource Plugin SPI 类型。

## 命令语义

```text
Create
  -> Plugin Gateway normalize
  -> new DataSourceDefinition / ConnectionProfile
  -> UNKNOWN

Update
  -> same dbType
  -> Plugin Gateway merge secret + normalize
  -> Aggregate updateConfiguration
  -> UNKNOWN

TestConnection(saved)
  -> Plugin Gateway
  -> success -> CONNECTED
  -> failure -> DISCONNECTED

TestConnection(unsaved)
  -> Plugin Gateway validation only
  -> no persisted state

Catalog
  -> load DataSourceDefinition
  -> validate application request
  -> Catalog Gateway
  -> SPI Adapter converts protocol model
```

## Gateway / Adapter 边界

允许的依赖方向：

```text
Application -> Business Gateway Port <- SPI Adapter -> Datasource Plugin SPI
```

禁止：

```text
Application -> DataSourcePlugin / DataSourceConnection / DataSourceCatalog
Application -> SPI metadata/query model
Business Gateway Port -> SPI model
Core Domain -> Gateway / SPI
SPI model -> Domain identity
```

当前明确例外：

- `DataSourcePluginConfigServiceImpl` 仍承接历史 `pluginConfig() -> DataSourcePluginConfigVO`，直到 Plugin Descriptor / Form Contract 去 VO 化。
- `BusinessDataSourceExecutionProvider` 自身是向 Task Plugin SPI 暴露执行能力的 Adapter，可以依赖相关 SPI。
- `DataSourceSecretCodec` 是 SPI Adapter 的技术 helper，不是 Domain Service。

这些例外不得扩散到新的 Application 主链路。

## Catalog Gateway 当前定位

Phase 2 的 `DataSourceCatalogGateway.Table / Column / QueryResult` 是**Business-owned boundary contract**，作用只是阻止 SPI Model 穿透。

它们暂时不是最终 Catalog Domain Model；`Map<String, Object>` 请求协议仍是已知 Gap。Phase 3 负责类型化 Catalog Request / Result，并决定哪些模型提升为正式 Domain。

## 持久化兼容

当前物理表仍然是：

```text
yak_ops_data_source
```

`jdbc_url / connection_params / original_json / conn_status` 是持久化投影，不定义业务边界。Repository Adapter 继续负责 Domain 与持久化模型之间的映射。

## 修改代码前后

修改前：

```text
Domain Impact Analysis
- Aggregate(s):
- Invariant/lifecycle impact:
- Layer:
- Domain Gap: yes/no
```

修改后：

```text
Domain Compliance Report
- Rule changed/implemented:
- Safety/tests:
- Known gaps:
```

代码 Review 固定按 `REVIEW.md` 执行。

## 自动护栏

至少保留：

```text
Core Domain dependency guardrail
Repository boundary guardrail
Application -> Gateway dependency guardrail
Gateway Port no SPI/DTO/VO/PO guardrail
SPI Adapter translation tests
ConnectionProfile / connection status behavior tests
```

**不要因为功能被护栏拦住就删护栏。** 如果规则真的变化，同一个 PR 中同步修改 `DOMAIN.md` 和对应测试。

## 已知独立 Gap

```text
Catalog Domain Model / typed Map protocol
SQL Execution Domain Model
Plugin Capability / Descriptor
Plugin API VO dependency
PluginConfig compatibility bridge cleanup
Aggregate physical naming cleanup
```
