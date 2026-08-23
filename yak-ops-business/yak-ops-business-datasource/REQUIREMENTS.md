# Datasource Requirements

> 本文件只描述**模块需要什么**，不描述怎么实现。历史需求和讨论看 Issue / PR / Git。

## 目标

Datasource 提供 Yak Ops 的统一数据源控制面：注册和维护数据源配置、验证连接可用性、访问 Catalog 元数据，并向上层业务提供稳定的数据源引用和基础查询能力。

## 核心能力

- 创建、编辑、查询和删除数据源。
- 支持数据源名称、类型、运行环境、备注和连接配置管理。
- 支持已保存数据源和未保存配置的连接测试。
- 记录数据源最近一次连接测试结果：`UNKNOWN / CONNECTED / DISCONNECTED`。
- 提供数据库、Schema、表、字段等 Catalog 元数据访问能力。
- 提供数据预览、数量统计和 SQL 模板等轻量读取能力。
- 通过 Datasource Plugin SPI 扩展不同数据库或数据源类型。
- 对连接密码等 Secret 做合并、脱敏和安全输出。
- 为数据开发、同步、任务等上层模块提供稳定的数据源引用。

## 关键业务行为

### 创建数据源

```text
Create
  -> validate name / type / environment / connection
  -> DataSource aggregate
  -> connection status = UNKNOWN
  -> persist
```

新建数据源不会因为“配置可被解析”就自动标记为 `CONNECTED`；只有真实连接测试成功后才能进入 `CONNECTED`。

### 编辑数据源

```text
Update
  -> load current DataSource
  -> data source type must remain unchanged
  -> merge stored Secret when needed
  -> replace connection profile / metadata
  -> connection status = UNKNOWN
  -> persist
```

数据源类型创建后不可修改。连接配置发生编辑后，旧连接测试结果不再代表当前配置，因此必须回到 `UNKNOWN`。

### 连接测试

```text
Test saved datasource
  -> load aggregate
  -> execute plugin connection test
  -> success: CONNECTED
  -> failure: DISCONNECTED
```

未保存配置的连接测试只验证输入，不产生持久化状态。

## 安全要求

- Secret 不得通过普通 DTO / VO、异常文本、`toString()` 或业务日志明文暴露。
- HTTP 详情中的 JDBC 地址和连接 JSON 必须经过脱敏边界。
- 数据源编辑时允许复用已保存 Secret，但不能把掩码字符串当成真实凭据覆盖原值。
- Domain、Repository Contract 和测试夹具不得为了调试方便打印完整连接 JSON。

## 模块边界

本模块负责：

- 数据源业务定义和生命周期规则；
- 数据源持久化；
- 连接测试编排；
- Catalog / 轻量读取能力的业务入口；
- Datasource Plugin 的发现和调用边界。

本模块不负责：

- 实时同步任务的定义、发布和运行状态；
- Flink / Flink CDC 集群生命周期；
- 任意 ETL / 工作流编排；
- 数据血缘计算；
- JDBC Driver 或第三方数据源服务的部署；
- 把某个插件的私有参数升级为全局业务字段。

## 兼容性要求

当前阶段保持以下外部契约兼容：

- REST API 路径和主要请求/响应结构；
- `yak_ops_data_source` 现有物理表结构；
- 现有 Flyway 历史；
- Datasource Plugin SPI 的现有签名；
- MySQL / PostgreSQL / Oracle / Doris 等已有插件实现。

领域改造不得以 Big-Bang 方式同时修改 REST、DB 和 Plugin SPI。

## 当前明确未解决

以下能力需要后续阶段单独设计，不在普通 Phase 1 修改中顺手完成：

```text
Business Domain 与 Datasource Plugin SPI 的 Gateway / Adapter 隔离
Catalog Metadata 的完整业务领域模型
SQL Execution 的领域模型与运行时拆分
Plugin Capability / Descriptor 标准化
Plugin API 中 VO 依赖清理
Map<String, Object> Catalog 协议类型化
DataSourceDefinition 物理命名清理
```

## 需求变更规则

如果 PR 引入本文件没有描述的新业务能力或改变已有业务行为：

```text
Requirement Gap
```

先确认需求并更新本文件，再实现代码。Reviewer / AI 不得自行补需求。

本文件只维护**当前有效需求**，不要追加迭代历史。
