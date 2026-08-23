# Realtime Sync Review

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

### 3. Correctness

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

### 4. Compatibility

检查是否破坏：

- REST API；
- DB / Flyway；
- Yak YAML；
- 历史数据；
- 前端调用；
- 已存在运行实例或版本记录。

破坏性变更必须有明确迁移方案，禁止 Big-Bang 修改。

### 5. Safety

重点检查：

- 重复启动 / 双实例；
- stop-during-start；
- `UNKNOWN / CONFLICT`；
- runtime identity 恢复；
- RuntimeEnvironmentSnapshot；
- 密码 / Secret 是否落库或进日志；
- 提交临时文件是否安全清理。

### 6. Tests / Guardrails

每个 P0 / P1 问题都回答：

```text
现有哪个测试应该挡住？
```

如果没有，指出缺失测试。优先补能锁住领域行为的回归测试，不为了覆盖率堆测试。

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
场景：什么输入或并发顺序会触发
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