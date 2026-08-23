# Datasource Review

> 本文件定义**如何 Review**。Reviewer / AI 是裁判，不是需求设计者；不得边 Review 边自行补需求。

## Review 前必读

```text
REQUIREMENTS.md  -> 模块需要什么
DOMAIN.md        -> 实现不能违反什么
REVIEW.md        -> 按什么标准判卷
PR diff / tests  -> 实际改了什么
```

## 1. Requirement Alignment

检查：

- 是否属于 `REQUIREMENTS.md` 已有能力；
- 是否改变创建、编辑、删除、连接测试或 Catalog 行为；
- 是否引入未定义的新能力；
- 是否越过 Datasource 模块边界。

未定义的新能力或行为变化报告：

```text
Requirement Gap
```

## 2. Domain Compliance

重点检查：

- DTO / VO / PO / Plugin SPI Model 是否被当成业务模型；
- `dbType` 是否可能在更新时修改；
- ConnectionProfile 变更后是否仍保留旧连接状态；
- 未保存连接测试是否错误写状态；
- Core Domain 是否引入 Spring / MyBatis / Plugin SPI；
- Repository 是否泄漏 PO / Mapper / MyBatis 类型；
- `DataSourceServiceImpl / DataSourceCatalogServiceImpl / DataSourceViewMapper` 是否直接依赖 Plugin SPI、`DataSourcePluginRegistry` 或 SPI Secret helper；
- `DataSourcePluginGateway / DataSourceCatalogGateway` 是否暴露 Plugin SPI、HTTP DTO / VO 或 PO；
- SPI Table / Column / QueryResult 是否绕过 Adapter 进入 Application；
- Secret 是否进入 `toString()`、日志或未脱敏响应；
- 是否继续通过 `Map<String, Object>` key 偷渡新业务语义。

违反现有规则：

```text
Domain Violation
```

现有模型无法表达需求：

```text
Domain Gap
```

## 3. Correctness

只检查真实风险：

- 空值、边界值、名称重复；
- 类型 / 环境解析；
- ConnectionProfile 规范化；
- Secret 合并和脱敏；
- Gateway Adapter 异常映射；
- SPI metadata -> Business Gateway Contract 字段映射；
- 事务边界；
- 连接测试成功 / 失败状态；
- Catalog 只读约束；
- SQL 执行取消、超时、审计一致性。

## 4. Compatibility

不得无迁移方案破坏：

```text
REST API
yak_ops_data_source
Flyway history
Frontend calls
Datasource Plugin SPI
Existing database plugins
PluginConfig dynamic form contract
Task Plugin SQL execution provider
```

禁止 Big-Bang 修改。

## 5. Safety

重点检查：

- Secret 明文输出；
- 掩码覆盖真实密码；
- JDBC URL 凭据泄漏；
- Gateway Adapter 吞异常或错误分类；
- 失败连接仍显示 `CONNECTED`；
- 新配置继承旧连接状态；
- SQL / Catalog 绕过只读约束。

## 6. Tests / Guardrails

每个 P0 / P1 都回答：

```text
现有哪个测试应该挡住？
```

至少应覆盖：

```text
DataSource type immutable
ConnectionProfile change -> UNKNOWN
connection test success -> CONNECTED
connection test failure -> DISCONNECTED
ConnectionProfile toString secret-free
Core Domain dependency guardrail
Repository boundary guardrail
Application -> Business Gateway only
Gateway Port no Plugin SPI / DTO / VO / PO
SPI Connection -> ConnectionProfile mapping
SPI Catalog metadata -> Business Gateway Contract mapping
```

## 当前允许的边界例外

```text
DataSourcePluginConfigServiceImpl
  -> historical pluginConfig() / VO compatibility bridge

BusinessDataSourceExecutionProvider
  -> outward Task Plugin SPI adapter

DataSourceSecretCodec
  -> SPI adapter technical helper
```

例外不能扩散到新的 Application 主链路。

## 严重级别

```text
P0 Blocker
- Secret 泄漏
- 数据不可恢复破坏
- 明确安全问题

P1 Must Fix
- 业务结果错误
- 违反 REQUIREMENTS.md / DOMAIN.md
- Plugin SPI 泄漏进受保护 Application 主链路
- Gateway Port 暴露 SPI / DTO / VO / PO
- 连接状态 / 类型不可变规则错误
- 明确事务 / 兼容性缺陷
- 高概率导致数据源不可用

P2 Suggestion
- 有明确收益的可维护性、性能或测试改进
- 不阻塞合并
```

纯命名、格式、个人风格偏好不要作为问题，除非造成真实歧义或风险。

## 问题证据要求

每个有效问题至少包含：

```text
位置：文件 / 行或方法
级别：P0 / P1 / P2
依据：Requirement / Domain rule / correctness fact
场景：触发输入或调用顺序
风险：实际结果
建议：修复方向
测试：应补或应命中的测试
```

没有触发场景和风险，不要凑问题。

## 固定输出格式

```text
# Review Result

Conclusion: PASS | CHANGES_REQUIRED

## P0 Blocker
无 / 问题列表

## P1 Must Fix
无 / 问题列表

## P2 Suggestion
无 / 问题列表

## Requirement Gap
无 / 说明

## Domain Gap
无 / 说明

## Missing Tests
无 / 说明
```

- 有 P0 / P1 -> `CHANGES_REQUIRED`。
- 只有 P2 -> 可以 `PASS`。
- 没有真实问题 -> `PASS`，不要硬凑问题。
