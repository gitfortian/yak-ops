# Yak Ops Code Style

本文件定义 Yak Ops 仓库级长期工程风格。它参考 Apache Spark / Flink / Hadoop 等大型 Java 数据系统常见实践，但不复制任何单一项目的格式规则。目标是让代码长期保持：**简单、显式、角色清楚、可测试、可治理**。

具体业务模块的需求、领域硬规则、架构图与依赖矩阵仍由各模块自己的 `REQUIREMENTS.md / DOMAIN.md / ARCHITECTURE.md / DEPENDENCIES.md` 定义。本文件只定义跨模块统一的工程规则；模块领域规则优先于为了“统一风格”做的机械改写。

## 1. 优先级

```text
Correctness / Safety
    > Explicit ownership
    > Simple control flow
    > Testability
    > Reuse
    > Brevity
```

不要为了少几行代码而隐藏业务身份、冻结快照、幂等键、状态、外部运行标识、Cursor、事务/CAS 线性化点或其他安全证据。

## 2. Package Is Architecture

先按**业务子系统**组织代码，再在子系统内部表达技术角色。准确的 package graph 以模块自己的 `ARCHITECTURE.md / DEPENDENCIES.md` 为准。

production 不创建下面这类模糊业务桶：

```text
service/
common/
helper/
utils/
base/
```

一个类不知道放哪里时，先回答：

1. 它拥有哪个 truth？
2. 它是什么 role？
3. 谁调用它？
4. 是否跨子系统？
5. 跨边界应该经过哪个 Facade / Gateway / Resolver / Repository？

只有真正跨场景、语义稳定的抽象才值得共享。不要因为两个模块“看起来相似”就提前抽共享 Kernel。

## 3. Role Vocabulary

### Service

只表示稳定 Application use-case / facade。不要把所有 Spring Bean 都命名为 `Service`。

### Coordinator

负责多个内部角色的顶层编排，强调步骤顺序，不吞掉所有实现细节。

### Manager

拥有明确资源、状态约束或生命周期，例如 admission、reservation、cursor lifecycle、environment lifecycle。

### Runtime

维护某个明确运行对象的 runtime truth 或基于运行证据推导稳定状态。不能成为新的全局 Context。

### Resolver

根据已知事实解析确定结果，不偷偷改变业务状态。

### Planner

把输入规划成不可变或可执行计划，不负责真正提交外部执行。

### Factory

集中创建带有业务约束的对象；如果只是 `new` 的别名，不需要 Factory。

### Dispatcher

后台扫描并分发待处理工作，不拥有被分发对象的最终业务状态。

### Reconciler

根据外部事实使本地状态收敛。无法证明时保留领域定义的不确定状态，不能靠猜测制造确定性。

### Query / Reader

只做 read model / read evidence 聚合，不拥有 command transition。

### Gateway / Client / Probe

位于跨子系统或外部系统边界。HTTP / Process / SSH / Quartz / Link-Up / Flink 等协议细节停在这里，不进入 Core Domain。

### Repository

表示业务持久化 contract；实现可以适配 DAO，但不向 Application 暴露 DAO model / Mapper。

### Adapter / Mapper / Codec

用于边界模型、协议和格式转换。兼容转换放在真正拥有兼容责任的边界，不放进 Core Domain。

### Handler

用于 Framework callback / inbound callback，例如 scheduler callback；Handler 应尽快进入稳定业务边界，不自己成为 truth owner。

### Support

仅限窄而无状态的解析、校验或格式支持。`Support` 不能演化成隐藏多种业务职责的 Helper。

如果起不出准确角色名，通常说明职责还没拆清楚。

## 4. Spring Stereotype

- `@Service`：稳定 Application Facade；
- `@Component`：内部专业角色；
- `@Repository`：Persistence adapter；
- `@Configuration`：wiring/config only；
- Core Domain：不使用 Spring annotation。

默认 constructor injection。不要用 field injection、static locator、手工 `ApplicationContext` lookup、reflection 或全局可变状态绕过显式依赖图。

构造器依赖明显过多时，优先检查类是否承担了多个 role，而不是把依赖藏进 Context/Common 对象。

## 5. Method Design

高风险流程要让顺序直接可读。典型形式：

```text
validate input
 -> freeze target / snapshot
 -> reservation / CAS / re-check
 -> external call
 -> apply result
 -> publish projection / event
```

不要把关键步骤隐藏进没有业务含义的 `execute / handle / process` 私有大方法。

推荐：

- guard clause / early return；
- 不可变 command-time target / snapshot；
- 复杂条件提炼成有业务含义的方法；
- 事务内只做需要线性化的持久化工作；
- 外部网络 / CLI 调用不要无意间进入长事务；
- public method 表达 use-case / contract，private method 表达真实流程步骤。

## 6. State And Lifecycle

状态和生命周期必须使用明确的 domain type 表达。

持续保持：

```text
identity != execution evidence
current mutable config != frozen execution snapshot
UNKNOWN / CONFLICT / unavailable evidence != FAILED
retry != new business run（除非模块领域规则明确如此）
domain state != compatibility projection
```

具体对象关系以模块 `DOMAIN.md` 为准。

能使用 enum / value object / record 表达的长期语义，不依赖 magic string、数组下标或 boolean flag。字符串只保留在协议、持久化兼容、事件格式等真正的边界。

如果现有模型表达不了新需求，先标记 **Domain Gap**，不要先增加新的 mode/type/flag 绕过领域模型。

## 7. External Uncertainty

对 Flink / Link-Up / CLI / SSH / REST / Quartz 等外部系统：

- 无证据就不猜；
- 请求失败不自动等于业务执行失败；
- 提交结果不确定时必须保留恢复所需 identity/evidence；
- 多匹配、状态不可用等场景按模块领域规则进入 UNKNOWN / CONFLICT / pending-reconcile 等明确状态；
- 不能为了 UI 状态好看而伪造 STOPPED / FAILED / SUCCEEDED；
- 错误处理优先保护证据链，而不是只追求快速返回。

## 8. Runtime Truth And Snapshot

运行真相只能有一个 owner。Task、Batch、Attempt、Execution、Cursor、Environment 等对象各自拥有的 truth 由模块 `DOMAIN.md / ARCHITECTURE.md` 定义。

一旦业务执行已经冻结配置或 Snapshot：

```text
existing execution -> persisted frozen snapshot
```

后续 current Task / Environment / Schedule / Definition 修改不得隐式改变历史执行语义。

Compatibility projection、`last-*` 字段和 UI 聚合不能反向成为 command truth owner。

## 9. Sensitive Configuration

敏感配置只在必要的外部调用边界短暂存在：

- 不进入 Core Domain；
- 不写入不需要 Secret 的业务定义格式；
- 不扩大持久化范围；
- 不出现在日志、异常文本或调试 `toString()`；
- 日志与诊断输出必须经过现有 redaction 边界；
- 可以清零的短生命周期 secret buffer 应及时清理。

## 10. Logging

使用参数化日志：

```java
LOG.info("taskId={}, executionId={}", taskId, executionId);
```

不要用字符串拼接生成日志。

日志应携带稳定业务定位信息，如 TaskId、BatchId、AttemptId、ExecutionId、DefinitionVersionId、CursorId、外部运行标识（已知时）。日志不能成为业务状态 truth，也不能输出完整 JobSpec / Credential / Secret payload。

## 11. Null / Optional / Collections

- `Optional` 主要用于“可能不存在”的返回值；
- 不把 `Optional` 当字段或到处传递的参数容器；
- collection 返回空集合，不返回 `null`；
- nullable 字段必须有单一、明确、文档化的语义；
- 已有明确领域状态时，不再用 `null` 偷偷表达 Unknown；
- 不用数组下标或 `Map<String, Object>` 隐式表达固定业务角色。

## 12. Types And Generics

- 不使用 raw type；
- 避免 wildcard import；
- 跨边界优先明确 record / value object；
- 不用 `Map<String, Object>` 承载长期业务 contract；
- 外部动态响应可以临时使用 `JsonNode`，但业务判断应尽快转成明确语义；
- Stream 只在让局部转换更清楚时使用，长链和有副作用的循环优先普通控制流；
- 不使用静态可变业务状态。

## 13. Comments

代码和命名解释 **what / how**；注释解释 **why / invariant / danger**。

安全线性化点、兼容债务、非显然 fallback、外部不确定性处理必须解释原因。

production 注释描述**当前 contract**，不记录 Stage / Wave / 迁移过程。历史演进通过 Git / PR 追溯。

## 14. Tests

### Behavior Safety Tests

优先保护：

- domain invariant / state transition；
- idempotency / retry / CAS / concurrency；
- UNKNOWN / CONFLICT / uncertain result；
- frozen snapshot；
- Cursor / Batch / Attempt / Execution lifecycle；
- persistence / external compatibility；
- bug regression root cause。

### Architecture Tests

保护：

- stable `@Service` facade；
- internal role stereotype；
- package dependency matrix；
- no cycle；
- cross-subsystem corridor；
- Core Domain purity；
- no broad business bucket；
- Repository / DAO / Engine boundary；
- 低争议且可执行的 code-style rules。

重构 PR 不能只保证行为测试，也必须保证 architecture guards。

测试优先从公开行为验证结果，避免依赖 private implementation。不要用反射 / PowerMock / Whitebox 绕过正常依赖；难测试通常意味着设计边界有问题。

## 15. Change Size

优先一个 PR 一个主要边界或一个行为关注点。

不要把下面这些混成一次“顺手重构”：

```text
package move
DB schema
REST breaking change
state-machine semantic change
new connector / engine feature
large formatting-only churn
```

好的 diff 应让 reviewer 很快回答：为什么改、边界在哪、行为有没有变、哪个测试证明它。

## 16. Review Questions

提交前至少回答：

1. 这个类属于哪个 subsystem，角色名准确吗？
2. 它拥有哪个 truth，是否出现第二个 owner？
3. 新 dependency 是否符合模块 `DEPENDENCIES.md`？
4. 有没有把内部角色暴露成新的 Application API？
5. 有没有把外部不确定性误当业务失败/成功？
6. 有没有让历史执行读取 current mutable config 破坏冻结语义？
7. 有没有让 Engine / DAO / Repository 反向依赖 Application？
8. 有没有扩大敏感配置的生命周期或输出范围？
9. 有没有用 magic string / boolean / array index 隐藏稳定业务语义？
10. 哪个 behavior test 和 architecture test 保护这次修改？
11. PR 是否足够 targeted，能独立 review / rollback？

如果答案依赖“大家约定不要这么用”，说明规则还不够可执行。