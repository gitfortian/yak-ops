# Offline Sync Domain — Stage 1

> 目标：先统一边界和语言，不在本阶段决定最终 Aggregate、数据库结构或 Java 类型。

## 1. 领域使命

离线同步领域负责描述：

> **一份数据从哪里读取、按什么同步定义写入哪里，以及一次手动、调度或外部触发如何形成可追踪、可重试、可取消的离线执行。**

离线同步的核心不是“某个 Link-Up Job”，而是 **Task Definition → Trigger → Execution Snapshot → Offline Execution**。

```text
OfflineSyncTask
      │ trigger
      ▼
ExecutionSnapshot
      │
      ▼
OfflineExecution
      │ submit / reconcile / cancel
      ▼
Engine Adapter
```

## 2. 领域边界

### 本领域负责

- 离线同步任务及其可编辑同步定义；
- 来源、目标、表选择/映射及读写策略；
- 手动、定时、工作流等执行触发；
- 每次执行使用的配置快照；
- 执行状态、取消、失败重试和执行历史；
- 执行指标、事件及外部引擎执行引用；
- 任务级调度策略，但不负责调度器实现。

### 本领域不负责

- 数据源密码、JDBC 连接等凭据的长期存储；
- 数据源生命周期管理；
- Quartz / Yak Schedule 的实现细节；
- Link-Up 节点、Worker、Connector 注册和调度；
- 工作流 DAG 编排；
- 将 Link-Up Job、Worker 或 HTTP 协议对象作为核心领域对象；
- 数据开发中的通用 SQL/ETL 编排能力。

## 3. 已确认统一语言

| 术语 | 含义 |
| --- | --- |
| `OfflineSyncTask` | 用户长期维护的一项离线同步任务。Task 不等于某次执行。 |
| `SyncDefinition` | 描述来源、目标、选择/映射和同步策略的业务定义。编辑器 JSON、JobSpec 都只是其表现或投影。 |
| `SourceEndpoint` | 来源端业务引用。连接凭据属于 DataSource 上下文，不进入核心定义。 |
| `SinkEndpoint` | 目标端业务引用。 |
| `SyncRoute` | 一组来源选择到目标写入的映射关系。单表/多表优先由 Route/Selector 组合表达。 |
| `SchedulePolicy` | 任务“什么时候触发”的业务策略；Quartz/Yak Schedule 只是基础设施。 |
| `ExecutionTrigger` | 创建一次执行的原因，例如 `MANUAL`、`SCHEDULE`、`WORKFLOW`、`RETRY`。 |
| `OfflineExecution` | 一次具体离线执行。它拥有自己的状态和执行证据，不是 Task 的运行字段。 |
| `ExecutionSnapshot` | Execution 创建时固定的定义/执行配置证据；后续修改 Task 不应改写已有执行。 |
| `EngineExecutionRef` | 对外部执行引擎实例的引用，例如 Link-Up JobId；引擎标识不是领域主键。 |
| `IdempotencyKey` | 同一执行请求的幂等身份，用于避免重复创建/提交。 |
| `RetryPolicy` | 失败后是否重试、最大次数和退避等规则。 |
| `ExecutionEvent` | 执行状态变化和关键操作的历史证据。 |

## 4. 当前执行状态语言

当前实现已经稳定使用：

```text
CREATED -> SUBMITTED -> QUEUED -> RUNNING
                                  ├-> SUCCEEDED
                                  ├-> FAILED
                                  ├-> CANCELED
                                  └-> LOST
```

Stage 1 只确认这些词用于描述当前执行状态；完整状态迁移规则留到 Stage 3。

## 5. 暂不定型的候选概念

以下概念对离线同步很重要，但 **Stage 1 不直接定成最终模型**：

| 候选概念 | Stage 2 要回答的问题 |
| --- | --- |
| `DefinitionVersion` | 当前 `version` 是修订号还是应升级为不可变发布版本？ |
| `BatchScope` / `DataWindow` | 一次执行是否必须明确“处理哪段业务数据”？ |
| `BatchExecution` | 是否需要把“一个业务批次”与技术执行实例分开？ |
| `ExecutionAttempt` | `attemptNo/retryFromExecutionId` 是否应成为独立 Attempt 模型？ |
| `Backfill` | 补历史数据是普通触发、批次集合还是独立业务动作？ |
| `IncrementalCursor` | 增量位置属于 Definition、BatchScope 还是 Execution 证据？ |
| `PartitionScope` | 分区范围是否是 BatchScope 的一种表达？ |

如果 Stage 2 无法自然解释这些场景，应记录为 `Domain Gap`，不要先增加 `syncType/sceneType`。

## 6. 现有代码的临时对照

这只是帮助阅读当前代码，**不是最终模型承诺**：

```text
OfflineJobDefinition
≈ Task + mutable definition + schedule + last execution summary

OfflineJobExecution
≈ OfflineExecution + snapshot + retry metadata + engine evidence

OfflineSchedule
≈ SchedulePolicy 的查询/持久化投影

OfflineExecutionOrchestrator
= Application orchestration

LinkUpClient / LinkUpJobSpecFactory
= Infrastructure / Engine Adapter
```

当前 `OfflineJobDefinition` 混合了多个关注点，后续 Stage 4 再决定哪些 KEEP / ADAPT / MIGRATE。

## 7. Stage 1 语言规则

1. `Task != Execution`。
2. 外部 Engine Job 不等于 `OfflineExecution`。
3. Execution 必须能保留自己的执行快照，不能靠回读当前 Task 解释历史。
4. Quartz、Yak Schedule、Link-Up、Worker、HTTP DTO 不进入核心领域语言。
5. 数据源凭据只在执行边界解析，不成为 SyncDefinition 的长期事实。
6. `GUIDE_SINGLE / GUIDE_MULTI` 属于当前交互/配置模式，不定义为不同的业务 Task 类型。
7. 全量、增量、单表、多表优先尝试用 Scope / Route / Policy 组合表达，不先创造 `sceneType/syncType`。
8. Retry 不应改写已经结束的执行历史；是否引入 `ExecutionAttempt` 留给 Stage 2 验证。
9. 实时同步与离线同步暂时保持独立 Core，不因为名字相似提前抽 Shared Sync Kernel。
10. 新需求无法映射到上述语言时，先记 `Domain Gap`，不要直接编码。

## 8. Stage 2 输入

下一阶段只做核心模型设计，并用以下场景验证：

```text
A. 单表手动执行
B. 每日定时执行
C. 失败后重试
D. 历史补数
```

Stage 2 的重点不是“照搬实时同步”，而是回答：

> **离线同步的一次业务批次、一次执行、一次重试，到底是不是同一个东西？**
