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

检查是否属于已有能力，是否改变数据源生命周期、Catalog 或 SQL Execution 行为，是否越过模块边界。未定义的新能力统一报告：

```text
Requirement Gap
```

## 2. Domain Compliance

重点检查：

- DTO / VO / PO / Plugin SPI Model 是否冒充 Domain；
- `dbType` 不可变和 ConnectionProfile -> `UNKNOWN` 是否保持；
- Core Domain 是否引入 Spring / MyBatis / Plugin SPI；
- Repository 是否泄漏持久化类型；
- `DataSourceServiceImpl / DataSourceCatalogServiceImpl / DataSourceViewMapper / DefaultSqlExecutionRuntime` 是否直接依赖 Datasource Plugin SPI；
- `DataSourcePluginGateway / DataSourceCatalogGateway / SqlExecutionGateway` 是否暴露 SPI、DTO / VO 或 PO；
- `DataSourceCatalogGateway` 是否重新接受 `Map<String,Object>`；
- HTTP Map 是否在 Application 入口后继续传播；
- Catalog SPI metadata 是否绕过 Adapter 进入 Domain/Application；
- `SqlExecutionAggregate` 是否重新持有线程、Future、Spring 或物理 Session；
- Runtime 是否重新维护第二套 Execution / Statement 状态机；
- Runtime 是否绕过 `SqlExecutionGateway` 直接调用 datasource execution SPI；
- Secret 是否进入日志、异常或未脱敏响应；
- 是否通过隐藏 Map key 绕过 `Domain Gap`。

违反规则报告 `Domain Violation`；模型无法表达需求报告：

```text
Domain Gap
```

## 3. Correctness

检查真实风险：

- 空值、边界值、名称重复、类型 / 环境解析；
- Secret 合并和脱敏；
- Gateway Adapter 异常及字段映射；
- Catalog TABLE / SQL 模式解析与历史 alias；
- Catalog preview/count/describe 单条 SELECT 约束；
- Catalog variable 投影是否保持兼容；
- SQL Policy 是否在打开数据源前拒绝非法 SQL；
- 多 Statement 顺序和 `SKIPPED` 语义；
- `SINGLE_TRANSACTION` 是否同 Session begin/commit/rollback；
- cancel、timeout、失败是否收敛到正确终态；
- 审计观察是否不改变物理执行生命周期。

## 4. Compatibility

不得无迁移方案破坏：

```text
REST API / JSON shape
yak_ops_data_source / Flyway
Datasource Plugin SPI
Existing database plugins
PluginConfig dynamic form contract
Task Plugin SQL execution provider
yak-ops-core SQL Execution contract
```

禁止 Big-Bang 修改。Catalog HTTP Map 可以作为兼容入口存在；Plugin Map 可以作为 Adapter 投影存在，但两者不得重新成为 Business Contract。

## 5. Safety

重点检查：

- Secret 明文输出或掩码覆盖真实密码；
- JDBC URL 凭据泄漏；
- Gateway Adapter 吞异常或错误分类；
- 连接状态错误；
- Catalog 绕过只读检查；
- Dataset / Data Service / Analysis 绕过 SQL read-only policy；
- cancel 后继续执行剩余 Statement；
- transaction 失败未 rollback；
- timeout 被错误归类为普通 FAILED。

## 6. Tests / Guardrails

每个 P0 / P1 都回答“哪个测试应该挡住”。至少覆盖：

```text
DataSource type immutable
ConnectionProfile change -> UNKNOWN
Core Domain / Repository boundary
Application / Runtime -> Business Gateway only
Gateway Port no Plugin SPI / DTO / VO / PO
DataSourceCatalogGateway no Map
HTTP Map -> CatalogReadRequest
CatalogReadRequest -> legacy Plugin Map only in Adapter
SPI Catalog metadata -> Catalog Domain
SqlExecutionAggregate lifecycle
SqlExecutionGateway SPI mapping
policy reject before datasource open
transaction commit / rollback / cancellation
timeout -> TIMED_OUT
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

例外不能扩散。

## 严重级别

```text
P0 Blocker
- Secret 泄漏
- 数据不可恢复破坏
- 明确安全问题

P1 Must Fix
- 业务结果错误
- 违反 REQUIREMENTS.md / DOMAIN.md
- SPI 泄漏进受保护主链路
- Gateway 暴露外部模型或 Catalog Map 回流
- SQL lifecycle / transaction / cancel / timeout 错误
- 明确兼容性缺陷

P2 Suggestion
- 有明确收益的可维护性、性能或测试改进
- 不阻塞合并
```

纯命名、格式或个人偏好不算问题，除非造成真实歧义或风险。

## 问题证据要求

每个有效问题包含：位置、级别、依据、触发场景、风险、修复方向、应命中的测试。没有触发场景和实际风险，不要凑问题。

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
