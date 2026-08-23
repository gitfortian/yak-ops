# Realtime Sync Review

> 本文件定义**如何 Review**。Reviewer / AI 是裁判，不是需求设计者；不得边 Review 边自行补需求。

## Review 前必读

按顺序读取：

```text
REQUIREMENTS.md  -> 模块需要什么
DOMAIN.md        -> 实现不能违反什么
ARCHITECTURE.md  -> 代码边界与角色要收敛到哪里
REVIEW.md        -> 按什么标准判卷
PR diff / tests  -> 实际改了什么
```

如果 PR 是纯 package move / rename / class split，也必须同时检查 Domain 和 Architecture；“只是重构”不能成为绕过运行安全规则的理由。

## Review 顺序

### 1. Requirement Alignment

检查代码是否符合 `REQUIREMENTS.md`：

- 是否实现已有能力？
- 是否改变已有业务行为？
- 是否引入文档中没有的新能力？
- 是否越过模块边界？

出现未定义的新能力或行为变化时，报告：

```text
Requirement Gap
```

不要替产品或开发者自行补需求。

### 2. Domain Compliance

检查 `DOMAIN.md`，重点关注：

- Task / DefinitionVersion / SyncExecution 是否混淆；
- Execution 是否读取 current Draft；
- Published Version 是否可能被原地修改；
- Restart 是否可能隐式升级；
- Apply 的目标版本是否可能执行中漂移；
- `UNKNOWN / CONFLICT` 是否可能创建第二实例；
- Runtime state 是否重新回到 Task；
- Flink / SSH / Credential / Adapter 参数是否泄漏进 Core Domain；
- 是否重新引入 `syncType / sceneType / 第二套 Spec`。

违反现有领域规则时，报告：

```text
Domain Violation
```

如果现有领域模型无法表达需求，报告：

```text
Domain Gap
```

### 3. Architecture Alignment

检查 `ARCHITECTURE.md`。当前允许 `service/` 作为迁移期 package，但新的代码和拆分方向必须向业务子系统收敛。

重点关注：

- 新类是否能明确归属 Definition / Execution / Reconcile / Observability / Environment / Engine / Persistence；
- 是否继续向顶层 `service/` 增加宽泛职责；
- Controller 是否依赖稳定 Application Facade，而不是内部 Coordinator / Manager / Repository / Engine；
- `@Service` 是否被滥用于内部专业角色；
- Query / Observability 是否开始承担 command 状态迁移；
- Reconcile 是否仍使用明确 runtime identity / environment snapshot，而不是猜外部 Job；
- Repository contract 是否泄漏 DAO / PO / Controller DTO；
- Domain 是否依赖 Spring / Jackson / MyBatis / Flink / SSH；
- Compatibility mapper / facade 是否重新进入 Core Domain；
- 是否为了减少重复提前抽 realtime/offline Shared Sync Kernel；
- package move 是否同时改变 REST / DB / Domain behavior，导致 PR 难以独立 review / rollback。

出现明确违反目标架构且会形成新的长期耦合时，报告：

```text
Architecture Violation
```

如果当前目标架构无法表达真实需求，先报告：

```text
Architecture Gap
```

不要通过新增 `Helper / Common / Utils / Base` 绕过边界。

### 4. Correctness

检查真实错误，不做泛泛而谈：

- 状态迁移；
- 空值和边界值；
- 事务边界；
- 并发 / CAS / 锁；
- 幂等；
- 重试；
- 外部调用超时和部分失败；
- Start / Stop / Reconcile / Restart / Apply 的竞态；
- 快照、版本、外部 JobId 是否可能错配。

### 5. Compatibility

检查是否破坏：

- REST API；
- DB / Flyway；
- Yak YAML；
- 历史数据；
- 前端调用；
- 已存在运行实例或版本记录。

破坏性变更必须有明确迁移方案，禁止借架构重构做 Big-Bang contract change。

### 6. Safety

重点检查：

- 重复启动 / 双实例；
- stop-during-start；
- `UNKNOWN / CONFLICT`；
- runtime identity 恢复；
- RuntimeEnvironmentSnapshot；
- replacement-stop reservation；
- prepared version re-check；
- 密码 / Secret 是否落库或进日志；
- 提交临时文件是否安全清理。

### 7. Tests / Guardrails

每个 P0 / P1 问题都回答：

```text
现有哪个测试应该挡住？
```

如果没有，指出缺失测试。优先补能锁住领域行为和架构边界的回归测试，不为了覆盖率堆测试。

迁移期间：

- `RealtimeArchitectureTest` 继续作为基础边界安全网；
- Domain guardrail 不得因为 package move 被删除；
- 当 Definition / Execution / Reconcile 等目标 package 稳定后，再逐步补完整 dependency graph / corridor tests；
- 不要提前把临时 `service/` 依赖写成永久 architecture whitelist。

## Refactor PR Rules

纯结构重构默认遵守：

```text
一个 PR 一个主要边界
package move / class split / behavior change 尽量分开
不顺手改 REST / DB / Flyway / Domain semantics
不长期保留新旧双入口
先有行为回归测试，再拆 Execution 高风险路径
```

好的重构 PR 应让 reviewer 快速回答：

```text
为什么拆？
目标 subsystem / role 是什么？
runtime truth owner 有没有变化？
public contract 有没有变化？
哪个测试证明行为没变？
```

### Domain / Architecture Impact block

涉及核心结构调整的 PR 建议在描述中包含：

```text
Domain Impact Analysis
- Aggregate(s):
- Invariant/lifecycle impact:
- Domain Gap: yes/no

Architecture Impact Analysis
- Target subsystem:
- Stable entry / gateway:
- Runtime truth owner:
- Dependency direction changed: yes/no
```

## 严重级别

```text
P0 Blocker
- 数据丢失 / 不可恢复破坏
- 重复运行导致严重数据风险
- Secret 泄漏
- 明确安全问题

P1 Must Fix
- 业务结果错误
- 违反 REQUIREMENTS.md / DOMAIN.md
- 明确并发、幂等、事务、兼容性缺陷
- 高概率导致运行故障
- 引入明确的长期架构越界并破坏稳定边界

P2 Suggestion
- 有明确收益的可维护性、性能或测试改进
- 非阻塞架构收敛建议
```

纯命名、格式、个人风格偏好不要作为问题提交，除非会造成真实歧义或边界风险。

## 每个问题必须有证据

一个有效 Review 问题至少包含：

```text
位置：文件 / 行或方法
级别：P0 / P1 / P2
依据：Requirement / Domain / Architecture / correctness fact
场景：什么输入、依赖关系或并发顺序会触发
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

## Architecture Gap
无 / 说明

## Missing Tests
无 / 说明
```

规则：

- 有 P0 / P1 -> `CHANGES_REQUIRED`。
- 只有 P2 -> 可以 `PASS`，P2 不阻塞。
- 没发现真实问题 -> 直接 `PASS`，不要为了显得有价值硬凑问题。
- Review 结论只基于当前需求、领域规则、架构 contract、代码事实和可复现风险，不猜未来需求。
