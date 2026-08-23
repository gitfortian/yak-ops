# Offline Sync Domain

> 当前阶段：Stage 2。只定义核心模型，不决定数据库结构或 Java 实现。

## 1. 领域使命

> **描述一份数据从哪里读取、按什么定义写入哪里，以及一次手动、调度、工作流或补数触发如何形成可追踪、可重试、可取消的离线批次。**

```text
OfflineSyncTask
      │ trigger
      ▼
BatchExecution
      ├── ExecutionSnapshot
      ├── BatchScope
      └── ExecutionAttempt 1..N
                │
                ▼
          Engine Adapter
```

核心不是 Link-Up Job。

## 2. 核心模型

### OfflineSyncTask

用户长期维护的一项离线同步任务。

```text
OfflineSyncTask
├── TaskId / TaskProfile
├── SyncDefinition
├── SchedulePolicy?
├── RetryPolicy
└── DefinitionRevision
```

### BatchExecution

一次明确的**业务批次**。

```text
BatchExecution
├── BatchExecutionId
├── TaskId
├── ExecutionTrigger
├── BatchScope
├── ExecutionSnapshot
└── ExecutionAttempt 1..N
```

### ExecutionAttempt

同一批次的一次实际提交尝试。

```text
ExecutionAttempt
├── AttemptNo
├── IdempotencyKey
├── AttemptReason
├── ExecutionStatus
├── EngineExecutionRef?
├── Metrics / Error
└── StartedAt / EndedAt
```

因此：

```text
Task != BatchExecution != ExecutionAttempt

Batch B100
├── Attempt 1 FAILED
├── Attempt 2 FAILED
└── Attempt 3 SUCCEEDED
```

业务上仍然只有一个 B100。

## 3. 关键决策

### Retry

```text
ExecutionTrigger:
MANUAL | SCHEDULE | WORKFLOW | BACKFILL

AttemptReason:
INITIAL | RETRY
```

Retry 创建新 Attempt，不创建新 Batch。

### ExecutionSnapshot

Batch 创建时冻结：

```text
ExecutionSnapshot
├── SyncDefinition Snapshot
├── DefinitionRevision
├── RetryPolicy Snapshot
└── ConfigDigest
```

Retry 必须继续使用同一 Snapshot，不能重新读取当前 Task。

### 暂不引入 immutable DefinitionVersion

当前采用：

```text
mutable Task Definition
      │ create batch
      ▼
immutable ExecutionSnapshot
```

`DefinitionRevision` 只是修订标识，不是历史定义的 Source of Truth。

只有未来明确需要“上线 V3 同时编辑 V4”或“任意重跑历史 V3”时，再把 immutable `DefinitionVersion` 作为 Domain Gap 单独设计。

### BatchScope

`BatchScope` 回答：**这个批次处理哪部分数据？**

候选表达：

```text
FullSelection | DataWindow | PartitionScope | CursorRange
```

它不是 `syncType/sceneType`。具体范围和游标规则留到 Stage 3。

### SyncDefinition

```text
SyncDefinition
├── SourceEndpoint
├── SinkEndpoint
├── SyncRoute[]
├── ReadPolicy
└── WritePolicy
```

编辑器 JSON 是表现形式，Link-Up JobSpec 是执行适配产物，DataSource 凭据不进入定义。`GUIDE_SINGLE/GUIDE_MULTI` 不进入核心模型。

## 4. 四个场景验证

```text
A. 手动执行
Task T1 -> Batch B1 -> Attempt 1

B. 每日调度
08-22 -> Batch B22 -> Attempt 1
08-23 -> Batch B23 -> Attempt 1

C. 失败重试
Batch B22
├ Attempt 1 FAILED
└ Attempt 2 SUCCEEDED

D. 历史补数
Scope 08-01 -> Batch B01
Scope 08-02 -> Batch B02
Scope 08-03 -> Batch B03
```

补数创建一组 Batch，不创建 `BACKFILL_TASK` 类型；同一次补数应优先固定同一份 ExecutionSnapshot。

## 5. 当前代码的目标映射

```text
OfflineJobDefinition
≈ OfflineSyncTask + SyncDefinition + SchedulePolicy
  + legacy last-execution projection

OfflineJobExecution
≈ BatchExecution + ExecutionAttempt
  + ExecutionSnapshot + Engine evidence

attemptNo / retryFromExecutionId
≈ 当前代码尚未拆开的 Attempt 线索
```

这是 Stage 4 的输入，不代表现在就改代码。

## 6. Stage 2 硬规则

1. `Task != BatchExecution != ExecutionAttempt`。
2. Retry 创建新 Attempt，不创建新 Batch。
3. Batch 创建后固定 ExecutionSnapshot。
4. Retry 不得回读当前 Task 改变 Snapshot。
5. `DefinitionRevision` 不等于 immutable DefinitionVersion。
6. `BatchScope` 描述数据范围，不增加 `sceneType/syncType`。
7. 单表/多表由 Route/Selector 组合表达。
8. Backfill 生成多个 Batch，不创造新的 Task 类型。
9. Engine Job 只是 Attempt 的外部引用。
10. 实时同步与离线同步继续保持独立 Core。

## 7. Stage 3 输入

下一阶段只定义生命周期和不变量：

- BatchExecution / ExecutionAttempt 状态；
- 什么时候允许创建下一 Attempt；
- LOST / uncertain 如何处理；
- 同一 Task 是否允许多个 Batch 并行；
- Schedule 重复触发如何幂等；
- Backfill 如何固定 Snapshot；
- BatchScope / CursorRange 的边界。

这些问题没有结论前，不修改数据库和执行代码。
