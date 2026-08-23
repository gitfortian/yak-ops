# Realtime Sync Code Style

本文件定义 Realtime Sync 的长期工程风格。它参考 Apache Spark / Flink / Hadoop 等大型 Java 数据系统常见实践，但不复制任何单一项目的格式规则。目标是让代码长期保持：**简单、显式、角色清楚、可测试、可治理**。

需求看 `REQUIREMENTS.md`，领域硬规则看 `DOMAIN.md`，总体架构看 `ARCHITECTURE.md`，包依赖看 `DEPENDENCIES.md`。

## 1. 优先级

```text
Correctness / Safety
    > Explicit ownership
    > Simple control flow
    > Testability
    > Reuse
    > Brevity
```

不要为了少几行代码而隐藏 DefinitionVersion、SyncExecution、Idempotency-Key、desired/observed state、RuntimeEnvironmentSnapshot、runtime identity 或事务线性化点。

## 2. Package is architecture

业务职责优先进入明确子系统：

```text
controller
 definition
 execution
 reconcile
 observability
 environment
 engine
 repository
 dao
 domain
 config
```

production 不创建 `service / common / helper / utils / base` 这类模糊业务桶。

一个类不知道放哪里时，先回答：它拥有哪个 truth？是什么角色？谁调用它？是否跨子系统？跨边界应该经过哪个 Facade / Resolver / Gateway / Repository？

## 3. Role vocabulary

### Service

只表示稳定 Application use-case facade：

```text
RealtimeJobDefinitionService
RealtimeJobExecutionService
RealtimeJobQueryService
RealtimeObservabilityService
ComputeEnvironmentService
```

不要把所有 Spring Bean 都命名为 Service。

### Coordinator

负责多个内部角色的顶层编排，不吞掉所有实现细节。例如 `RealtimeExecutionCoordinator`、`RealtimeReconcileCoordinator`。

### Manager

拥有明确资源的状态约束或生命周期。例如 Environment lifecycle、Execution reservation/state/replacement。

### Resolver

根据已知事实解析确定结果，不偷偷改变业务状态。例如 `RealtimeRuntimeResolver`。

### Reconciler

根据外部事实使本地状态收敛。无法证明时允许 `UNKNOWN / CONFLICT`，不能靠猜测制造确定性。

### Query / Reader

只做 read model / read evidence 聚合，不拥有 command transition。

### Gateway / Client / Probe

位于外部系统边界。协议、HTTP/Process/SSH 等实现细节停在这里，不进入 Core Domain。

### Repository

表示业务持久化 contract；实现可以适配 DAO，但不向 Application 暴露 DAO model / Mapper。

### Adapter / Mapper / Codec

用于边界模型和格式转换。兼容转换放在真正拥有兼容责任的边界，不放进 Core Domain。

## 4. Spring stereotype

- `@Service`：稳定 Application Facade；
- `@Component`：内部专业角色；
- `@Repository`：Persistence adapter；
- `@Configuration`：wiring/config only；
- Core Domain：不使用 Spring annotation。

默认 constructor injection。不要用 static locator、手工 ApplicationContext lookup、reflection 或全局可变状态绕过显式依赖图。

## 5. Method design

高风险流程要让顺序直接可读。例如 Start：

```text
prepare
 -> validate
 -> DB reservation / re-check
 -> external submit
 -> commit result
```

不要把关键步骤隐藏进泛化的 `execute / handle / process`。

推荐 guard clause、early return、不可变 command-time target/snapshot。事务内只做需要线性化的持久化工作，外部调用不要无意间进入长事务。

## 6. State and lifecycle

必须持续保持：

```text
Task != DefinitionVersion != SyncExecution
UNKNOWN != FAILED
CONFLICT != FAILED
RestartExecution != ApplyPublishedVersion
DefinitionDigest != sourceConfigDigest != artifactDigest
Current Environment != Execution RuntimeEnvironmentSnapshot
```

如果现有模型表达不了需求，先标记 **Domain Gap**。不要用新的 mode/type/flag 字段绕过领域模型。

## 7. External uncertainty

对 Flink / CLI / SSH / REST 等外部系统：

- 无证据就不猜；
- submit result uncertain 保持可恢复身份；
- runtime status unavailable -> UNKNOWN；
- runtime identity 多匹配 -> CONFLICT/UNKNOWN；
- 请求失败不自动等于业务执行失败；
- 不能为了 UI 状态好看而伪造 STOPPED。

错误处理要保护证据链，而不是只追求快速返回。

## 8. Runtime Environment

新工作可以解析当前 enabled Compute Environment；一旦 SyncExecution 建立：

```text
Execution -> immutable RuntimeEnvironmentSnapshot
```

后续 Environment 修改不得影响历史 Execution。禁止用当前 Environment 作为历史 Execution snapshot 的 fallback。

## 9. Sensitive configuration

敏感配置只在必要的外部调用边界短暂存在，不进入 Core Domain，不写入业务定义格式，不扩大持久化范围，也不出现在日志和异常文本中。日志与诊断输出必须经过现有 redaction 边界。

## 10. Logging

使用参数化日志，并携带稳定业务定位信息，如 TaskId、ExecutionId、DefinitionVersionId、外部运行标识（已知时）。日志不能成为业务状态 truth。

## 11. Null / Optional / collections

- `Optional` 主要用于返回值；
- collection 返回空集合，不返回 null；
- nullable 字段必须有单一明确语义；
- 已有 `UNKNOWN` 领域状态时，不再用 null 偷偷表达 Unknown。

## 12. Types and generics

- 不使用 raw type；
- 避免 wildcard import；
- 跨边界优先明确 record/value object；
- 不用 `Map<String, Object>` 承载长期业务 contract；
- 外部动态响应可以临时使用 JsonNode，但业务判断尽快转为明确语义。

## 13. Comments

注释解释 **why / invariant / danger**，不要复述代码。安全线性化点、兼容债务、非显然 fallback 必须解释原因。

## 14. Tests

### Behavior safety tests

保护：Idempotency、single Active/Uncertain、stop-during-start、Restart/Apply target pinning、UNKNOWN/CONFLICT、runtime identity recovery、frozen RuntimeEnvironmentSnapshot、Environment lifecycle。

### Architecture tests

保护：stable `@Service` facade、internal role stereotype、package dependency matrix、no cycle、cross-subsystem corridor、Core Domain purity、no broad business bucket、Repository/DAO/Engine boundary。

重构 PR 不能只保证行为测试，也必须保证 architecture guards。

## 15. Change size

优先一个 PR 一个主要边界或一个行为关注点。不要把 package move、DB schema、REST breaking change、state-machine 语义变化和新 connector feature 混成一次“顺手重构”。

## 16. Review questions

提交前至少回答：

1. 这个类属于哪个 subsystem，角色名准确吗？
2. 它拥有哪个 truth，是否出现第二个 owner？
3. 新 dependency 是否符合 `DEPENDENCIES.md`？
4. 有没有把内部角色暴露成新的 Application API？
5. 有没有把 UNKNOWN/CONFLICT 误当失败？
6. 有没有让历史 Execution 读取当前 Environment？
7. 有没有让 Engine/DAO/Repository 反向依赖 Application？
8. 有没有扩大敏感配置的生命周期或输出范围？
9. 哪个 behavior test 和 architecture test 保护这次修改？

如果答案依赖“大家约定不要这么用”，说明规则还不够可执行。
