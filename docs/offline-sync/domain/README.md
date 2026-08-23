# Offline Sync Domain

> 当前阶段：Stage 3。只定义生命周期和不变量，不修改数据库或 Java 实现。

## 1. 核心模型

```text
OfflineSyncTask
      │ trigger
      ▼
BatchExecution
      ├── BatchScope
      ├── ExecutionSnapshot
      └── ExecutionAttempt 1..N
                │
                ▼
          Engine Adapter
```

```text
Task != BatchExecution != ExecutionAttempt
```

- `OfflineSyncTask`：长期维护的同步任务。
- `BatchExecution`：一次业务批次。
- `ExecutionAttempt`：同一批次的一次实际提交尝试。
- Retry 创建新 Attempt，不创建新 Batch。

## 2. ExecutionAttempt 生命周期

目标状态：

```text
CREATED
  ↓
SUBMITTING
  ↓
SUBMITTED -> QUEUED -> RUNNING
    │          │          │
    └──────────┴──────────┼-> SUCCEEDED
                          ├-> FAILED
                          ├-> CANCELING -> CANCELED
                          └-> UNKNOWN

UNKNOWN
  └-> reconcile -> SUBMITTED / QUEUED / RUNNING /
                   SUCCEEDED / FAILED / CANCELED
```

规则：

- `SUCCEEDED / FAILED / CANCELED` 是 Attempt 终态。
- `UNKNOWN` 不是失败，也不是终态；它表示结果无法确认。
- 提交超时、断连、Worker 身份变化但无法确认旧 Job 结果时进入 `UNKNOWN`。
- `UNKNOWN` 未解决前禁止创建下一 Attempt。
- Cancel 只有确认后才能进入 `CANCELED`；取消结果不确定时进入 `UNKNOWN`。
- Engine JobId 只是 Attempt 的外部引用，不能作为 Attempt 身份。

当前代码的 `LOST` 更接近目标模型的 `UNKNOWN`。`LOST -> 自动重试` 属于 Stage 4 需要迁移的 Domain Debt。

## 3. BatchExecution 生命周期

Batch 状态描述业务批次，而不是直接复制 Engine 状态：

```text
PENDING
   ↓
RUNNING
   ├-> SUCCEEDED
   ├-> WAITING_RETRY -> RUNNING
   ├-> FAILED
   ├-> CANCELED
   └-> UNKNOWN -> reconcile -> RUNNING / SUCCEEDED /
                              WAITING_RETRY / FAILED / CANCELED
```

终态：

```text
SUCCEEDED | FAILED | CANCELED
```

含义：

- `PENDING`：Batch 已创建，但还没有 Attempt 获得执行资格。
- `RUNNING`：当前 Attempt 正在创建、提交、排队或执行。
- `WAITING_RETRY`：最近 Attempt 已明确失败，满足 RetryPolicy，等待退避时间。
- `UNKNOWN`：当前 Attempt 结果不确定，禁止新 Attempt。
- `FAILED`：失败不可重试或重试次数耗尽。

Batch 终态后不再追加 Attempt；如果用户要重新跑同一数据范围，应创建新的 Batch。

## 4. Retry 不变量

创建 Attempt N+1 必须同时满足：

1. 属于同一个 BatchExecution；
2. 最近 Attempt 已明确 `FAILED`；
3. Failure 被 RetryPolicy 判定为 retryable；
4. 未超过最大 Attempt 数；
5. backoff 已到期；
6. 当前不存在 Active/Unknown Attempt。

Retry 必须复用：

```text
same BatchExecutionId
same BatchScope
same ExecutionSnapshot
new AttemptNo
new Attempt IdempotencyKey
```

禁止：

```text
UNKNOWN -> Retry
SUCCEEDED -> Retry
CANCELED -> Retry
Retry 时回读 current Task
Retry 时改变 BatchScope
```

## 5. Task 并发不变量

V1 采用保守规则：

> **同一个 Task 同时最多只有一个 RUNNING / WAITING_RETRY / UNKNOWN Batch。**

可以提前创建多个 `PENDING Batch`，但只有一个 Batch 可以获得 Attempt 执行资格。

这样可以同时支持：

- 普通任务串行执行；
- Backfill 一次创建多个待执行 Batch；
- Schedule 到点时先形成稳定业务身份，再等待执行资格；
- Retry 不会被后来的 Batch 抢占。

未来如果需要同 Task 并行多个 Batch，应作为 `Domain Gap` 单独设计 ConcurrencyPolicy，不能直接删除这个保护。

## 6. BatchKey 与触发幂等

每个 Batch 必须有稳定 `BatchKey`，同一 Task 内唯一。

建议身份：

```text
MANUAL   = requestId
SCHEDULE = scheduleId + plannedFireTime
WORKFLOW = workflow execution/attempt identity
BACKFILL = backfillRequestId + scopeFingerprint
```

规则：

- 重复收到同一个 BatchKey，不创建第二个 Batch。
- Schedule 使用“计划触发时间”，不能使用实际回调时间生成身份。
- Retry 沿用原 Batch，不生成新的 BatchKey。
- 幂等重放只能返回/继续原 Batch，不能偷偷使用 current Task 创建新 Snapshot。

Schedule 的 misfire 是调度策略问题，但不能破坏上述 Batch 身份规则。

## 7. Snapshot 与 Backfill

Batch 创建时冻结：

```text
ExecutionSnapshot
├── SyncDefinition Snapshot
├── DefinitionRevision
├── RetryPolicy Snapshot
└── ConfigDigest
```

不变量：

- Snapshot 创建后不可变。
- Snapshot 不长期保存 DataSource 密码。
- 所有 Attempt 使用 Batch 自己的 Snapshot。
- Task 后续修改不影响已有 Batch。

一次 Backfill 请求应先冻结一份 Snapshot，再按 Scope 创建多个 Batch：

```text
BackfillRequest R1
  ├ Scope 08-01 -> B1 -> Snapshot S1
  ├ Scope 08-02 -> B2 -> Snapshot S1
  └ Scope 08-03 -> B3 -> Snapshot S1
```

相同 `backfillRequestId + scopeFingerprint` 重放时复用已有 Batch，允许从部分创建失败中恢复。

## 8. BatchScope 边界

`BatchScope` 创建后不可变。

```text
FullSelection
DataWindow    = [startInclusive, endExclusive)
PartitionScope = non-empty normalized unique partitions
CursorRange   = (afterExclusive, throughInclusive]
```

规则：

- `DataWindow` 必须 `start < end`。
- `PartitionScope` 不能为空、不能包含重复分区。
- `CursorRange` 两端必须属于同一 Cursor，并满足 `after < through`。
- Retry 必须使用完全相同的 Scope。
- 自动增量场景只有前一个 CursorRange Batch `SUCCEEDED` 后才能推进 committed cursor。
- `FAILED / CANCELED / UNKNOWN` 不得推进 cursor。

单表/多表继续由 Route/Selector 表达，不进入 BatchScope 类型。

## 9. Runtime truth

运行状态只属于 `BatchExecution / ExecutionAttempt`。

Task 上的：

```text
lastExecutionId
lastJobStatus
lastErrorMessage
lastDuration...
```

只能作为查询投影，不能成为生命周期判断的事实来源。

## 10. Stage 3 硬规则

1. `Task != Batch != Attempt`。
2. Attempt 的 `UNKNOWN` 不等于 `FAILED`，禁止盲目 Retry。
3. Retry 只在明确 FAILED 后发生，并复用原 Snapshot/Scope。
4. Batch 终态后不再追加 Attempt。
5. 同 Task 最多一个 RUNNING / WAITING_RETRY / UNKNOWN Batch。
6. BatchKey 决定触发幂等，Schedule 使用 planned fire identity。
7. Snapshot 在 Batch 创建时冻结，Task 修改不影响历史 Batch。
8. Backfill 多 Batch 优先共享同一 Snapshot。
9. Cursor 只在 Batch 成功后推进。
10. Task 的 last-* 字段只做投影，不做 runtime truth。
11. `DefinitionRevision` 仍不等于 immutable DefinitionVersion。
12. 无法映射这些规则时先记录 `Domain Gap`。

## 11. Stage 4 输入

下一阶段只做“当前代码 -> 目标领域”的映射，重点检查：

- `OfflineJobExecution` 当前把 Batch + Attempt 合在一起；
- `LOST` 与目标 `UNKNOWN` 的语义冲突；
- 当前 Retry 是否回读 current Task / current SchedulePolicy；
- 当前 `hasActiveExecution` 是否足以表达 Batch 并发规则；
- Schedule 是否缺少稳定 BatchKey；
- Task `last-*` 是否被用于生命周期判断；
- 当前 execution snapshot 能否承载 BatchScope / Backfill identity。

Stage 4 只标记 KEEP / ADAPT / MIGRATE / DOMAIN DEBT，不开始重构代码。
