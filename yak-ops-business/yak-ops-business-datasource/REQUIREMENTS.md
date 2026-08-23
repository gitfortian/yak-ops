# Datasource Requirements

> 本文件只描述**模块需要什么**，不描述怎么实现。历史需求和讨论看 Issue / PR / Git。

## 目标

Datasource 提供 Yak Ops 的统一数据源控制面：维护数据源配置、验证连接、访问 Catalog 元数据，并向数据开发、同步、任务等模块提供稳定的数据源引用和 SQL 执行能力。

## 核心能力

- 创建、编辑、查询和删除数据源。
- 管理名称、类型、运行环境、备注和连接配置。
- 支持已保存数据源和未保存配置的连接测试。
- 记录最近一次已保存配置的连接测试结果：`UNKNOWN / CONNECTED / DISCONNECTED`。
- 提供数据库、Schema、表、字段等 Catalog 元数据访问能力。
- 提供数据预览、数量统计、SQL 模板和 SQL 变量解析能力。
- Catalog 业务内部使用明确的 typed request / metadata / result，不依赖隐式 Map key 扩展语义。
- 提供统一 SQL Execution contract，支持单语句、多语句、事务、取消、超时和终态观测。
- 通过 Datasource Plugin SPI 扩展不同数据库或数据源类型。
- 对连接 Secret 做合并、脱敏和安全输出。

## 关键业务行为

### 数据源生命周期

```text
Create
  -> validate + normalize
  -> DataSourceDefinition / ConnectionProfile
  -> UNKNOWN

Update
  -> dbType unchanged
  -> merge Secret + replace ConnectionProfile
  -> UNKNOWN

Test saved datasource
  -> success: CONNECTED
  -> connectivity failure: DISCONNECTED

Test unsaved configuration
  -> validation only
  -> no persisted state
```

配置可解析不等于连接成功；连接配置变化后旧测试结果失效。

### Catalog

```text
HTTP compatibility request
  -> typed CatalogReadRequest
  -> read-only validation when executing preview/count/describe SQL
  -> DataSourceCatalogGateway
  -> Plugin Adapter
```

- 表模式必须有 `table_path`；SQL 模式必须有 `query`。
- 预览、统计和字段探测的 SQL 模式只允许单条只读 SELECT。
- 现有 HTTP JSON 和 Plugin SPI Map 协议保持兼容，但 Map 不是新的业务扩展机制。

### SQL Execution

```text
SqlExecutionRequest / SqlExecutionPlan   (yak-ops-core canonical contract)
  -> policy validation
  -> SqlExecutionAggregate lifecycle
  -> SqlExecutionGateway
  -> physical datasource session
```

- Runtime 不自行发明第二套 Request / Plan / Snapshot 真相。
- 多语句边界由调用方显式提供，不按分号自动拆 SQL。
- `SINGLE_TRANSACTION` 必须在同一物理 Session 中执行；不支持事务时快速失败。
- Cancel 是 best-effort 物理动作，但生命周期必须最终收敛到明确终态。
- Statement timeout 必须提升为 execution `TIMED_OUT`。
- 终态至少包括 `SUCCEEDED / FAILED / CANCELLED / TIMED_OUT`。

## 安全要求

- Secret 不得通过普通 DTO / VO、异常文本、`toString()` 或业务日志明文暴露。
- HTTP 详情中的 JDBC 地址和连接 JSON 必须经过脱敏边界。
- 编辑时掩码、空值或缺失 Secret 可以沿用已保存值，但掩码不得覆盖真实凭据。
- Dataset / Data Service / Analysis 等只读调用方不得绕过 SQL Policy 执行写操作。
- Catalog preview/count/describe 不得绕过只读检查。

## 模块边界

本模块负责：

- DataSource 聚合与持久化；
- 连接测试编排；
- typed Catalog 子域和轻量读取入口；
- SQL Execution 生命周期与 Datasource 物理执行适配；
- Datasource Plugin 的发现、调用和反腐边界。

本模块不负责：

- 实时同步任务定义、发布和 Flink 生命周期；
- 任意 ETL / 工作流编排；
- 数据血缘计算；
- JDBC Driver 或第三方服务部署；
- 把插件私有参数升级为全局业务字段。

## 兼容性要求

当前阶段保持：

- REST API 路径和主要请求/响应结构；
- `yak_ops_data_source` 表结构和 Flyway 历史；
- Datasource Plugin SPI 现有签名；
- MySQL / PostgreSQL / Oracle / Doris 等已有插件实现；
- `yak-ops-core` SQL Execution 对外 contract。

领域改造不得 Big-Bang 同时修改 REST、DB 和 Plugin SPI。

## 当前明确未解决

```text
Plugin Capability / Descriptor 标准化
Plugin API 中 VO 依赖清理
PluginConfig compatibility bridge cleanup
Plugin Catalog Map SPI 的最终类型化
DataSourceDefinition 物理命名清理
```

## 需求变更规则

本文件未描述的新能力或行为变化统一报告：

```text
Requirement Gap
```

先确认需求并更新本文件，再实现代码。Reviewer / AI 不得自行补需求。
