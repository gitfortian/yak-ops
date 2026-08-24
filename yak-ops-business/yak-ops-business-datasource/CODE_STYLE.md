# Datasource Code Style

> 本文定义 Datasource 模块的代码组织和角色命名约定。它不是格式化工具说明；重点是让源码本身表达架构。

## 1. 优先级

```text
Correctness / Safety
> explicit ownership
> acyclic dependencies
> simple control flow
> testability
> reuse
> brevity
```

为了少写一个类而把多个职责塞回 `ServiceImpl`、`Helper` 或 `Utils`，不算简洁。

## 2. Package 就是架构

先确定业务能力，再确定类角色：

```text
management/DataSourceManager
query/DataSourceReader
connection/DataSourceConnectionTester
catalog/CatalogReadPolicy
gateway/DataSourceCatalogGateway
execution/DefaultSqlExecutionRuntime
```

不要默认生成：

```text
service/XxxService
service/impl/XxxServiceImpl
common/XxxHelper
utils/XxxUtils
```

新增顶层 package 必须有稳定语义，并进入 `DEPENDENCIES.md`。

## 3. 角色词汇

- `Manager`：Command / aggregate lifecycle。
- `Reader`：read-side。
- `Resolver`：配置、引用、环境解析。
- `Tester`：显式资源探测。
- `Policy`：业务/安全决策。
- `Matcher`：明确匹配算法。
- `Registry`：外部能力发现与注册。
- `Runtime`：执行生命周期、并发、事务编排。
- `Observer`：旁路观测。
- `Gateway`：Business Port。
- `Repository`：Domain persistence Port。
- `Adapter`：协议/模型翻译。
- `Mapper`：HTTP DTO/VO 边界转换。
- `Codec` / `Masker`：窄技术职责。

避免没有业务含义的 `Handler / Processor / Helper / Utils / Common / Base`。确有语义时，名称应说明处理的对象和动作。

## 4. Service 使用规则

`Service` **不是为了去 Service 而禁止**。

允许条件：它确实是跨多个内部角色的稳定 Application Facade，并且调用方需要这个稳定入口。此时 Service 内部仍应委托 Manager/Coordinator/Reader 等明确角色，不承载所有逻辑。

当前 Datasource 没有 `@Service` allowlist。新增 `@Service` 前必须：

1. 说明为什么明确角色不足；
2. 更新 `ARCHITECTURE.md / DEPENDENCIES.md`；
3. 更新 Architecture Guard；
4. 不使用 `XxxServiceImpl` 作为默认实现命名。

多实现接口优先用语义名称，如 `DefaultSqlExecutionRuntime`、`ObservableSqlExecutionRuntime`，而不是 `SqlExecutionRuntimeImpl`。

## 5. Spring stereotype

- `@Component`：Manager/Reader/Resolver/Tester/Policy/Matcher/Registry/Runtime/Adapter 等明确角色。
- `@Repository`：DAO / Repository Adapter 等持久化角色。
- `@Configuration`：基础设施装配。
- Domain：不使用 Spring stereotype。
- 禁止 field injection；使用构造器注入。

## 6. Controller 与 Mapper

Controller 负责：

```text
HTTP binding
permission
transport compatibility
role invocation
response projection
```

HTTP DTO/VO 不向 management/query/connection/catalog 泄漏。兼容 `Map<String,Object>` 只允许停留在 controller/mapper，并立即映射成 `CatalogReadRequest`。

RequestMapper 可以做 transport normalization 和 enum parsing；不要为了“复用”把 HTTP parsing 抽成跨层 Validator，再让底层反向依赖。

ViewMapper 可以依赖 Business Gateway 完成必要脱敏，但不得接触 raw Plugin SPI。

## 7. Domain Model

Domain 不是换包后的 PO。

```java
DataSourceDefinition.create(...)
definition.updateConfiguration(...)
definition.markConnected()
definition.markDisconnected()
```

优先领域行为，不开放 public setter。持久化重建使用明确的 `restore(...)` / persistence mapper，不用 `BeanUtils.copyProperties` 绕过不变量。

Value Object 优先不可变 record/class；构造时归一化并验证基本不变量。

## 8. Gateway / Adapter

Business Role 只能依赖 Gateway interface，不依赖 `gateway.adapter`、Plugin Registry 或 raw SPI。

Adapter 负责：

- Business model ↔ SPI model；
- SPI exception ↔ Business exception；
- capability/descriptor boundary；
- Secret JSON codec 等仅属于 SPI 边界的技术细节。

Gateway contract 不暴露 DTO/VO/PO/SPI type。

## 9. Repository / DAO

Repository 用 Domain 说话；DAO 用 persistence model 说话。

```text
Role -> Repository -> RepositoryAdapter -> DAO -> Mapper/PO
```

Management/Query/Connection/Catalog 不直接依赖 DAO。PO 和 MyBatis type 不进入 Repository contract。

## 10. Catalog

- 内部统一 typed `CatalogReadRequest`、`CatalogTable`、`CatalogColumn`、`CatalogQueryResult`。
- SQL 只读安全属于 `CatalogReadPolicy`，不要散落 Controller/Reader/Gateway。
- 匹配算法属于 `CatalogTableMatcher`。
- 新语义扩 typed model，不新增隐藏 Map key。
- legacy alias 只在 `CatalogRequestMapper` 兼容。

## 11. Connection 与 Secret

Connection 原始 JSON 经 `DataSourceConnectionResolver` / `DataSourcePluginGateway` 规范化为 `ConnectionProfile`。

- 掩码、空值或缺失 Secret 编辑时可复用已保存值。
- Descriptor 的 PASSWORD 字段是 Secret Schema 来源。
- `DataSourceSecretCodec` 位于 Plugin Gateway Adapter 边界。
- 通用文本凭据由 `SensitiveTextMasker` 脱敏。
- 不在日志、断言失败信息和 `toString()` 中打印完整连接配置。

## 12. SQL Execution

`SqlExecutionAggregate` 只保存领域生命周期，不持有线程/Future/physical Session。

`DefaultSqlExecutionRuntime` 负责执行编排，不复制第二套状态机。物理执行只经 `SqlExecutionGateway.Session`；事务 begin/execute/commit/rollback 必须保持同 Session。

## 13. 控制流

优先 guard clause，避免深层嵌套：

```java
if (invalid) throw ...;
if (terminal) return ...;
```

生命周期代码先写“状态能否变化”，再写外部动作；异常映射在边界一次完成，不在多层重复 catch/rethrow。

## 14. 注释

注释解释：

```text
为什么这样设计
哪个不变量必须保持
哪个兼容行为不能删
哪个外部边界存在危险
```

不要写历史迁移阶段、提交说明或显而易见的逐行翻译。历史属于 Git/PR。

## 15. 测试

功能测试覆盖业务结果；架构测试覆盖“以后不允许长回去”。至少保持：

- aggregate lifecycle / no public setter；
- Connection status / Secret；
- Catalog read-only / typed request；
- Gateway contract no SPI/DTO/VO/PO；
- package dependency graph acyclic；
- no broad business bucket / no default ServiceImpl；
- no wildcard import / field injection / stdout；
- Controller compatibility endpoint/permission。

修改架构规则时，文档与测试同一 PR 更新；不要通过删除测试解决失败。

## 16. PR 自检

```text
- Package 是否表达能力？
- Class 名是否表达角色？
- 是否新建了模糊 bucket？
- 是否引入反向 package edge？
- DTO/VO/PO/SPI/Map 是否越界？
- Domain 是否仍拥有状态变化？
- Secret 是否可能泄漏？
- 对应 guardrail 是否能挡住回退？
```
