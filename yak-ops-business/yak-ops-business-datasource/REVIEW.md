# Datasource Review

> 本文件定义**如何 Review**。Reviewer / AI 是裁判，不是需求设计者；不得边 Review 边自行补需求。

## Review 前必读

```text
REQUIREMENTS.md
DOMAIN.md
REVIEW.md
yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN.md
PR diff / tests
```

## 1. Requirement Alignment

检查是否属于已有能力，是否改变数据源生命周期、Catalog、SQL Execution 或 Plugin Contract。未定义的新能力统一报告：

```text
Requirement Gap
```

## 2. Domain Compliance

重点检查：

- DTO / VO / PO / Plugin SPI Model 是否冒充 Domain；
- `dbType` 不可变、ConnectionProfile -> `UNKNOWN` 是否保持；
- Core Domain 是否引入 Spring / MyBatis / Plugin SPI；
- Application / Runtime 是否绕过 Business Gateway；
- `DataSourcePluginGateway / DataSourceCatalogGateway / SqlExecutionGateway` 是否暴露 SPI、DTO / VO 或 PO；
- Business Catalog 或 Plugin `DataSourceCatalog` 是否重新接受 `Map<String,Object>`；
- Catalog SPI metadata 是否绕过 Adapter 进入 Domain/Application；
- Plugin API 是否重新依赖 `DataSourcePluginConfigVO` 或其他 Business/HTTP VO；
- Plugin 是否缺少 Descriptor / apiVersion / Capability；
- Capability 是否与真实实现冲突；
- Secret 是否仍从 HTTP VO 猜测，而不是 Descriptor Schema；
- `SqlExecutionAggregate` 是否重新持有线程、Future、Spring 或物理 Session；
- Runtime 是否重新维护第二套 Execution / Statement 状态机或直接调用 execution SPI；
- 是否通过隐藏 Map key、boolean、VO 字段绕过 `Domain Gap`。

违反规则报告 `Domain Violation`；模型无法表达需求报告：

```text
Domain Gap
```

## 3. Correctness

检查真实风险：

- 空值、边界值、类型 / 环境解析；
- Secret merge / mask 和嵌套 SSH Secret；
- Descriptor `dbType / apiVersion / connectionForm`；
- `TRANSACTIONS -> SQL_EXECUTION`、`CATALOG_READ -> CATALOG_METADATA`；
- Plugin Registry 重复类型与 descriptor mismatch；
- Catalog TABLE / SQL 模式、变量、历史 HTTP alias；
- Catalog preview/count/describe 单条 SELECT；
- SPI typed Catalog request 与 Business CatalogReadRequest 字段映射；
- SQL Policy 是否在打开数据源前拒绝非法 SQL；
- transaction 同 Session、rollback、cancel、timeout 和 `SKIPPED`；
- PluginConfig Descriptor -> Business -> VO 是否保持前端 shape。

## 4. Compatibility

必须保持：

```text
REST API / JSON shape
yak_ops_data_source / Flyway
yak-ops-core SQL Execution contract
built-in plugin runtime behavior
Task Plugin SQL execution provider
```

Phase 4 允许且仅允许本次明确的 Plugin SPI source-level 迁移：

```text
pluginConfig() -> descriptor()
Catalog Map -> DataSourceCatalogReadRequest
```

第三方插件必须有迁移说明；禁止借此连带修改 REST / DB。后续再次做 SPI breaking change 必须先定义新的 API version 和迁移计划。

## 5. Safety

重点检查：

- Secret 明文输出、Descriptor defaultValue 泄漏 Secret、掩码覆盖真实凭据；
- JDBC URL 凭据泄漏；
- Plugin / Adapter 异常消息泄漏 Secret；
- Capability 声明了不真实的能力；
- Catalog 绕过只读检查；
- read-only caller 绕过 SQL Policy；
- cancel 后继续 Statement；transaction 失败未 rollback；timeout 误归类。

## 6. Tests / Guardrails

每个 P0 / P1 都回答“哪个测试应该挡住”。至少覆盖：

```text
DataSource type immutable / ConnectionProfile -> UNKNOWN
Core Domain / Repository boundary
Application / Runtime -> Business Gateway only
Gateway Port no SPI / DTO / VO / PO
Business Catalog + Plugin Catalog no Map
Plugin API no DataSourcePluginConfigVO
Descriptor apiVersion / dbType / capability dependencies
MySQL/PostgreSQL/Oracle/Doris/达梦/Kingbase descriptor contract
Descriptor -> PluginConfig VO compatibility projection
Descriptor-driven Secret masking
Catalog typed request mapping
SqlExecutionAggregate lifecycle
transaction / rollback / cancellation / timeout
```

## 当前允许的边界例外

```text
BusinessDataSourceExecutionProvider
  -> outward Task Plugin SPI adapter

DataSourceSecretCodec
  -> plugin SPI adapter technical helper; reads SPI Descriptor, not HTTP VO
```

例外不能扩散到普通 Application 主链路。

## 严重级别

```text
P0 Blocker
- Secret 泄漏
- 数据不可恢复破坏
- 明确安全问题

P1 Must Fix
- 业务结果错误
- 违反 REQUIREMENTS.md / DOMAIN.md / PLUGIN.md
- SPI 泄漏进受保护主链路
- Plugin API 重新依赖 VO / Catalog Map
- Capability 与实现不一致
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
