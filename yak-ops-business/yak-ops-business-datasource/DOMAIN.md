# Datasource Domain

> 本文件只保留**实现必须遵守的硬规则**，不记录设计过程。历史演进看 Git / PR。
>
> `REQUIREMENTS.md` 定义“需要什么”；`DOMAIN.md` 定义“不能违反什么”；`REVIEW.md` 定义“怎么判卷”。

## 核心模型

```text
DataSourceDefinition  (Aggregate Root，当前保留历史命名)
├── id
├── name
├── dbType
├── environment
├── ConnectionProfile
│   ├── jdbcUrl
│   ├── normalizedJson
│   └── originalJson
├── connectionStatus
├── remark
└── timestamps
```

```text
DataSourceDefinition
      │ persisted by
      ▼
DataSourceRepository (Domain Port)
      │ implemented by
      ▼
Repository Adapter -> DAO / PO / MyBatis
```

Datasource Plugin SPI 是**邻接上下文**，不是 Core Domain。

## 12 条硬规则

1. **`DataSourceDefinition` 是 Business Datasource 当前唯一数据源业务事实。** DTO、VO、PO、Plugin SPI Model 都只是 Adapter / Projection / Protocol Model。
2. **`ConnectionProfile` 是连接配置的领域值对象。** 新业务代码修改连接配置必须经过 Aggregate 行为，不直接散落修改 `jdbcUrl / connectionParams / originalJson`。
3. **DataSource Type 创建后不可修改。** 更新时请求类型必须与 Aggregate 当前 `dbType` 一致。
4. **连接配置被替换后，连接状态必须回到 `UNKNOWN`。** 旧配置的 `CONNECTED / DISCONNECTED` 不能继承给新配置。
5. **`CONNECTED / DISCONNECTED` 只表示最近一次针对当前已保存配置的连接测试结果。** 配置可解析不等于连接成功。
6. **未保存配置的连接测试不改变任何 Aggregate 状态。** 只有已保存数据源测试才持久化连接状态。
7. **Core Domain 不依赖 Spring / MyBatis / HTTP DTO / VO / PO / Datasource Plugin SPI。** 插件、JDBC、Catalog 实现细节属于边界之外。
8. **Repository Contract 只暴露 Domain。** PO、MyBatis `IPage`、Mapper Row 不得越过 Repository Adapter。
9. **Secret 不是可观察业务数据。** 完整连接 JSON、密码、Token 不得进入 `toString()`、普通日志或未脱敏响应。
10. **Catalog / SQL Execution / Plugin Capability 属于相邻子域。** Phase 1 不为了统一风格把它们硬塞进 `DataSourceDefinition`。
11. **新的业务语义优先扩明确领域模型。** 不通过新增 `Map<String, Object>` key、临时 boolean、展示 VO 字段来绕过 Domain Gap。
12. **无法映射现有模型就是 `Domain Gap`。** 先讨论模型和边界，不用 DTO / VO / PO / Plugin Model 冒充 Domain。

## 关键不变量

- `name` 必须非空。
- `dbType` 必须存在。
- `environment` 必须存在。
- `ConnectionProfile.normalizedJson` 必须存在。
- `dbType` 一旦创建不可变。
- `ConnectionProfile` 变更后 `connectionStatus = UNKNOWN`。
- 连接测试成功：`connectionStatus = CONNECTED`。
- 连接测试失败：`connectionStatus = DISCONNECTED`。

## 命令语义

```text
Create
  -> new DataSourceDefinition
  -> ConnectionProfile
  -> UNKNOWN

Update
  -> same dbType
  -> replace editable metadata / ConnectionProfile
  -> UNKNOWN

TestConnection(saved)
  -> success -> CONNECTED
  -> failure -> DISCONNECTED

TestConnection(unsaved)
  -> validation only
  -> no persisted state
```

## 持久化兼容

当前物理表仍然是：

```text
yak_ops_data_source
```

当前 PO 仍然保存：

```text
jdbc_url
connection_params
original_json
conn_status
```

这些是持久化投影，不定义业务边界。Phase 1 保持物理结构兼容，由 Repository Adapter 负责 Domain 与持久化模型之间的映射。

## 邻接上下文

当前 Datasource Plugin SPI 提供连接解析、连接测试、Catalog 和 SQL 执行能力。Phase 1 为兼容性仍允许 Application Service 调用 SPI；后续阶段必须通过 Gateway / Adapter 收口。

因此当前明确禁止：

```text
Plugin SPI Model -> Core Domain identity
Plugin private config -> DataSource global field
Controller VO -> Plugin stable business fact
PO -> Service direct access
```

## 修改代码前后

修改前先确认 `REQUIREMENTS.md` 中已有对应能力，再写一个短块：

```text
Domain Impact Analysis
- Aggregate(s):
- Invariant/lifecycle impact:
- Layer:
- Domain Gap: yes/no
```

修改后写：

```text
Domain Compliance Report
- Rule changed/implemented:
- Safety/tests:
- Known gaps:
```

代码 Review 固定按 `REVIEW.md` 执行。

## 自动护栏

至少保留以下自动检查：

```text
Core Domain 不依赖 DTO / VO / PO / MyBatis / Spring / Datasource Plugin SPI
Repository 只暴露 Domain
Service 不直接注入 DAO / PO
Controller 只通过 Service 进入业务链路
ConnectionProfile / connection status 行为回归测试
```

**不要因为功能被护栏拦住就删护栏。** 如果规则真的变化，同一个 PR 中同步修改 `DOMAIN.md` 和对应测试。

## 已知独立 Gap

```text
Datasource Plugin Gateway / Adapter
Catalog Domain Model
SQL Execution Domain Model
Plugin Capability / Descriptor
Plugin API VO dependency
Map-based Catalog protocol
Aggregate physical naming cleanup
```
