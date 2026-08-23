# Datasource Review

> 本文件定义**如何 Review**。Reviewer / AI 是裁判，不是需求设计者；不得边 Review 边自行补需求。

## Review 前必读

按顺序读取：

```text
REQUIREMENTS.md  -> 模块需要什么
DOMAIN.md        -> 实现不能违反什么
REVIEW.md        -> 按什么标准判卷
PR diff / tests  -> 实际改了什么
```

## Review 顺序

### 1. Requirement Alignment

检查代码是否符合 `REQUIREMENTS.md`：

- 是否实现已有能力？
- 是否改变数据源创建、编辑、删除、连接测试或 Catalog 行为？
- 是否引入文档中没有的新能力？
- 是否越过 Datasource 的模块边界？

出现未定义的新能力或行为变化时，报告：

```text
Requirement Gap
```

不要替产品或开发者自行补需求。

### 2. Domain Compliance

检查 `DOMAIN.md`，重点关注：

- DTO / VO / PO / Plugin SPI Model 是否被当成业务模型；
- `dbType` 是否可能在更新时被修改；
- ConnectionProfile 修改后是否仍保留旧 `CONNECTED / DISCONNECTED`；
- 未保存配置的连接测试是否错误写入状态；
- Core Domain 是否引入 Spring / MyBatis / Plugin SPI；
- `DataSourceServiceImpl / DataSourceCatalogServiceImpl / DataSourceViewMapper` 是否重新直接依赖 Plugin SPI、`DataSourcePluginRegistry` 或 SPI Secret helper；
- `DataSourcePluginGateway / DataSourceCatalogGateway` 是否暴露 Plugin SPI、HTTP DTO / VO 或 PO；
- SPI Table / Column / QueryResult 是否绕过 Adapter 直接进入 Application；
- Repository 是否泄漏 PO / Mapper / MyBatis 类型；
- Secret 是否可能进入 `toString()`、日志或响应；
- 是否继续通过 `Map<String, Object>` key 偷渡新的业务语义。

违反现有领域规则时，报告：

```text
Domain Violation
```

如果现有领域模型无法表达需求，报告：

```text
Domain Gap
```

### 3. Correctness

检查真实错误，不做泛泛而谈：

- 空值和边界值；
- 名称重复校验；
- 数据源类型解析；
- 环境解析；
- ConnectionProfile 规范化；
- Secret 合并 / 脱敏；
- Gateway Adapter 异常映射；
- SPI metadata -> Business Gateway Contract 字段映射；
- 事务边界；
- 连接测试成功 / 失败状态；
- Catalog 只读约束；
- SQL 执行取消、超时和审计一致性。

### 4. Compatibility

检查是否破坏：

- REST API；
- `yak_ops_data_source` 表结构；
- Flyway 历史；
- 前端已有调用；
- Datasource Plugin SPI；
- 已有 MySQL / PostgreSQL / Oracle / Doris 等插件；
- PluginConfig 动态表单历史协议；
- Task Plugin SQL execution provider。

破坏性变更必须有明确迁移方案，禁止 Big-Bang 修改。

### 5. Safety

重点检查：

- Secret 明文输出；
- 掩码值覆盖真实密码；
- JDBC URL 中凭据泄漏；
- Gateway Adapter 是否把插件异常吞掉或错误分类；
- 失败连接是否仍显示 `CONNECTED`；
- 新连接配置是否继承旧连接状态；
- SQL / Catalog 入口是否绕过只读约束。

### 6. Tests / Guardrails

每个 P0 / P1 问题都回答：

```text
现有哪个测试应该挡住？
```

如果没有，指出缺失测试。优先补能锁住领域规则和边界的回归测试，不为了覆盖率堆测试。

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

以下不是普通 Application Service，可按 `DOMAIN.md` 的边界说明单独判断：

```text
DataSourcePluginConfigServiceImpl
  -> historical pluginConfig() / VO compatibility bridge

BusinessDataSourceExecutionProvider
  -> outward Task Plugin SPI adapter

DataSourceSecretCodec
  -> SPI adapter technical helper
```

这些例外不能作为在新 Service 中直接调用 Plugin SPI 的理由。

## 严重级别

```text
P0 Blocker
- Secret 泄漏
- 数据不可恢复破坏
- 明确安全问题

P1 Must Fix
- 业务结果错误
- 违反 REQUIREMENTS.md / DOMAIN.md
- Plugin SPI 重新泄漏进受保护 Application 主链路
- Gateway Port 暴露 SPI / DTO / VO / PO
- 连接状态错误
- 类型不可变规则失效
- 明确事务 / 兼容性缺陷
- 高概率导致数据源不可用

P2 Suggestion
- 有明确收益的可维护性、性能或测试改进
- 不阻塞合并
```

纯命名、格式、个人风格偏好不要作为问题提交，除非会造成真实歧义或风险。

## 每个问题必须有证据

一个有效 Review 问题至少包含：

```text
位置：文件 / 行或方法
级别：P0 / P1 / P2
依据：Requirement / Domain rule / correctness fact
场景：什么输入或调用顺序会触发
风险：会造成什么结果
建议：修复方向，不必替作者重写整段代码
测试：应补或应命中的测试
```

没有可说明的触发场景和风险，就不要凑问题。

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

规则：

- 有 P0 / P1 -> `CHANGES_REQUIRED`。
- 只有 P2 -> 可以 `PASS`，P2 不阻塞。
- 没发现真实问题 -> 直接 `PASS`，不要为了显得有价值硬凑问题。
- Review 结论只基于当前需求、领域规则、代码事实和可复现风险，不猜未来需求。
