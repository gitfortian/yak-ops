# Offline Sync Code Style

本规范参考 Apache Spark、Flink、Hadoop 的工程习惯，并结合 Yak Ops 的 Spring / Java 21 架构蒸馏而成。**不是照抄某个项目；本文件才是 offline-sync 的实际规范。**

Apache 风格最值得保留的不是排版，而是：**简单、明确、角色化、可测试、可演进。**

## Apache Inspiration

- **Spark**：改动应 simple / targeted / tested；优先修根因，避免 big-bang，并保持代码库一致性。
- **Flink**：正确性之外，还要求清晰的职责分离、显式假设、构造器注入、少 collaborators、early return；代码说明 what/how，注释说明 why。
- **Hadoop**：大量使用 `Service / Manager / Dispatcher / Monitor / Launcher` 等角色名，并把生命周期和组件边界显式建模。

参考：

- Spark Contributing: `https://spark.apache.org/contributing.html`
- Flink Code Style and Quality Guide: `https://flink.apache.org/how-to-contribute/code-style-and-quality-preamble/`
- Hadoop Service API: `https://hadoop.apache.org/docs/current3/api/org/apache/hadoop/service/package-summary.html`

## 1. Package Is Architecture

先按**业务子系统**放代码，再在子系统内部表达技术角色。

推荐：

```text
execution/OfflineExecutionCoordinator
execution/query/OfflineExecutionQuery
backfill/OfflineBackfillPlanner
cursor/OfflineCursorManager
schedule/OfflineScheduleHandler
```

避免：

```text
service/
common/
helper/
utils/
```

这些目录很容易把职责重新拍平。只有真正跨场景、语义稳定的抽象才值得共享。

## 2. Name by Role

| Suffix | Meaning |
| --- | --- |
| `Service` | 稳定 Application use-case / facade |
| `Coordinator` | 编排多个步骤，不吞掉所有细节 |
| `Manager` | 拥有一类状态、资源或生命周期 |
| `Runtime` | 维护运行期真相 |
| `Planner` | 把输入规划成可执行计划，不负责实际执行 |
| `Factory` | 集中构造具有约束的对象 |
| `Query` | read model / 查询职责 |
| `Dispatcher` | 后台扫描并分发待处理工作 |
| `Reconciler` | 让本地状态与外部事实收敛 |
| `Gateway` | 跨子系统或外部能力的窄契约 |
| `Adapter` | 两种模型 / 协议之间的转换边界 |
| `Repository` | Domain persistence contract |
| `Mapper` | 纯映射，无业务生命周期 |
| `Handler` | Framework callback / inbound handler |
| `Support` | 仅限窄而无状态的解析/校验支持 |

不要用 `Helper / Common / Utils / Base` 回避命名。**如果起不出角色名，通常是职责还没想清楚。**

`@Service` 在 offline-sync 中只用于稳定 Application Service；内部角色使用 `@Component` 或普通对象。

## 3. Class Design

一个类应有一个清楚的业务理由发生变化。

- 构造器注入依赖；依赖关系应在字段上可见。
- 构造器变得很长时，先检查类是否承担了多个角色，而不是把依赖藏进 Context。
- public method 表达 use-case / contract；private method 表达流程步骤。
- Coordinator 负责顺序，Manager 负责规则，Adapter 负责边界转换；不要互相冒充。
- Runtime truth 只能有一个 owner，其他类通过 contract 读取或请求变化。
- 优先不可变 Value Object / Snapshot；不要让运行中的 Batch 回读 current Task 改写冻结语义。

### Component Template

```java
@Component
class ExampleCoordinator {

  private final ExampleGateway gateway;
  private final ExampleStateManager stateManager;

  ExampleCoordinator(ExampleGateway gateway, ExampleStateManager stateManager) {
    this.gateway = gateway;
    this.stateManager = stateManager;
  }

  Result execute(Command command) {
    validate(command);
    Prepared prepared = prepare(command);
    ExternalResult result = gateway.submit(prepared);
    return stateManager.apply(result);
  }

  private void validate(Command command) {
    // fail fast
  }
}
```

模板表达的是**结构**：依赖显式、主流程短、步骤命名清楚。不是要求每个类机械复制。

## 4. Keep the Main Path Flat

优先 guard clause / early return，避免深层嵌套。

```java
if (batch.isTerminal()) {
  throw new IllegalStateException("terminal Batch cannot create Attempt");
}

if (!reservation.acquired()) {
  return existing;
}

return submit(batch);
```

让正常路径尽量靠左。复杂条件应提炼成有业务含义的方法，而不是堆布尔表达式。

## 5. Make Illegal States Explicit

- 前置条件尽早检查，错误尽早失败。
- 能用 enum / value object 表达的语义，不长期依赖 magic string / boolean flag。
- `UNKNOWN`、`FAILED` 等不同业务状态不能为了代码简单合并。
- 不吞异常；只有真正的边界层才做统一转换，并保留 cause。
- Compatibility 如果必须存在，应放在拥有协议/持久化格式的边界，不建立全局 legacy facade。

## 6. Java Practices

- 不使用 wildcard import 和 raw type。
- 日志使用参数化占位符，不做字符串拼接：`LOG.info("batchId={}", batchId)`。
- 不记录密码、token、完整 credential 或带 Secret 的 JobSpec。
- `Optional` 优先用于“可能不存在”的返回值，不作为字段或到处传递的参数容器。
- Stream 只在让局部转换更清楚时使用；长链和副作用循环用普通控制流更易读。
- 不使用静态可变业务状态。
- 不用反射 / PowerMock / Whitebox 绕过正常依赖；难测试通常意味着设计边界有问题。
- 格式以仓库现有 formatter / Checkstyle / 邻近代码为准；不要为了个人偏好制造纯格式 diff。

## 7. Comments Explain Why

代码和命名应该解释 **what / how**；注释主要解释 **why**。

不要：

```java
// Check batch status.
if (batch.isTerminal()) { ... }
```

可以：

```java
// A terminal Batch is immutable; a new business run must create a new Batch.
if (batch.isTerminal()) { ... }
```

Javadoc 用于说明类的角色、非直观 contract、线程/生命周期约束。不要为了“有注释”重复类名和方法名。

## 8. Tests Protect Behavior and Boundaries

优先测试：

- Domain invariant / state transition；
- Retry、幂等、并发、CAS、UNKNOWN；
- persistence / external compatibility；
- regression root cause；
- package dependency / architecture corridor。

测试应从公开行为验证结果，避免依赖 private implementation。修 bug 时应尽量留下能复现原问题的 regression test。

## 9. Keep PRs Reviewable

Apache 项目普遍偏好可审查的小改动。Yak Ops 也遵循：

- 一个 PR 解决一个主要问题；
- package move、rename、class split、behavior change 尽量分开；
- 重构默认不顺手改 REST / DB / Domain semantics；
- 新行为必须说明 contract 和测试；
- 少做“顺手统一整个模块”的 big-bang cleanup。

好的 diff 应该让 reviewer 很快回答：**为什么改、边界在哪、行为有没有变、哪个测试证明它。**

## Review Template

新增类或重大修改前，自查：

```text
[ ] 属于哪个 subsystem？
[ ] 类名是否表达真实 role？
[ ] 稳定入口是谁？
[ ] runtime truth 的 owner 是谁？
[ ] dependencies 是否符合 DEPENDENCIES.md？
[ ] 是否新增了 Helper/Common/Utils 或隐式耦合？
[ ] 主流程是否能从 public method 顺着读下来？
[ ] comments 是否主要解释 why？
[ ] tests 是否锁住行为/边界，而不是实现细节？
[ ] PR 是否足够 targeted，能独立 review / rollback？
```

如果大部分问题都能直接回答，代码通常已经有比较强的 Apache 味道。
