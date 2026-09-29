# Data Sync Execution Retry / Attempt Contract

Status: v1.1 PR4 — Persistence + Runtime

Depends On:

- [Data Sync Capability](./README.md)
- [Data Sync Task Publication Lifecycle](./task-lifecycle.md)
- [Data Sync Scheduler Contract](./scheduler.md)
- [Yak Ops v1.1.0 Release Contract](../../release/v1.1.0.md)
- [Yak Ops Architecture](../../../ARCHITECTURE.md)

## 1. Goal

v1.1 的 Retry 不把一次失败重新包装成一条新的独立 Task Instance。

目标模型：

```text
Task
  ↓
Execution
  ├── Attempt #1
  ├── Attempt #2
  └── Attempt #3
```

Yak Ops 当前的 `DataSyncInstance` 从本 Contract 起定义为 **Execution 根身份**。

当前已经存在的 v1.0 / v1.1 Instance 数据视为：

```text
Execution
  └── implicit Attempt #1
```

PR3 冻结身份、状态与触发关系；PR4 已实现 Attempt Persistence + Runtime Retry。

当前实现：

```text
Task.retryPolicy
      ↓ freeze
Execution(DataSyncInstance)
      ↓
Attempt #1
      ↓ FAILED
Execution = RETRY_WAITING
      ↓ fixed backoff
Attempt #2
```

默认 `maxAttempts=1`，因此升级后历史任务不会自动改变执行行为。

## 2. Terminology

### Task

Task 描述“同步什么”。

Task 的 `definitionVersion` 仍然只描述可执行 Task Definition，不描述执行次数和 Retry 次数。

### Execution

Execution 描述“一次外部运行请求”。

Execution 的创建来源包括：

```text
MANUAL
SCHEDULE
future AUTO_RECOVERY
```

一次手工 Run 创建一个 Execution。

一次 Cron Fire 在满足并发规则时创建一个 Execution。

用户在某个 Execution 已经最终失败后再次点击 Run：

```text
new Execution
```

不是继续旧 Execution 的 Attempt。

### Attempt

Attempt 描述“Execution 内部真正交给 Runtime 的一次执行尝试”。

```text
Execution E1
  ├── Attempt 1 FAILED
  ├── Attempt 2 FAILED
  └── Attempt 3 SUCCEEDED
```

Retry 只创建新的 Attempt，不创建新的 Execution 根记录。

## 3. Existing Instance Compatibility

当前持久化表：

```text
yak_ops_data_sync_instance
```

继续作为 Execution Root。

不重命名表，不重建历史数据，不改变现有 Instance ID。

兼容规则：

- 已有 Instance = 一个 Execution。
- 在 Attempt 持久化正式引入以前，该 Execution 只有一个隐式 Attempt #1。
- 现有 Instance API / Operations Center 路由继续使用 Instance 作为产品名称，内部语义逐步收口为 Execution Root。
- 后续 Attempt 表必须引用现有 Instance ID，而不是反过来迁移 Instance 到新表。

因此后续目标关系是：

```text
yak_ops_data_sync_instance
        1
        │
        └──────── N
          yak_ops_data_sync_attempt
```

PR3 不创建 `yak_ops_data_sync_attempt`；Schema 属于 Retry Backend follow-up PR。

## 4. Execution Identity

Execution 一旦创建，以下身份固定：

- workspaceId。
- taskId。
- taskName snapshot。
- taskVersion。
- syncType。
- root trigger type。
- sanitized definition snapshot。

Retry 不允许重新读取最新 Task Definition 作为新的执行输入。

所有 Attempts 必须复用 Execution 创建时固定的：

```text
taskVersion
definitionSnapshot
```

因此：

> Retry 重试的是同一次 Execution，不是“拿当前 Task 再跑一次”。

如果用户需要使用新的 Task Definition，必须创建新的 Execution。

## 5. Attempt Identity

每个 Attempt 至少需要稳定身份：

```text
attemptId
executionId
attemptNo
status
readRows
writeRows
startTime
finishTime
errorCode
errorMessage
```

约束：

- `attemptNo` 从 1 开始。
- 同一 Execution 内严格递增。
- `(executionId, attemptNo)` 唯一。
- Attempt 不允许跨 Execution 移动。
- 已终态 Attempt 不允许重新变成 RUNNING。
- Retry 创建 `attemptNo + 1`，不得复活旧 Attempt。

## 6. Trigger Ownership

Execution Root 的 trigger type 表示“是谁创建了这次 Execution”。

当前：

```text
MANUAL
SCHEDULE
```

Retry 不是新的 Execution Root trigger。

因此目标语义是：

```text
SCHEDULE Execution
  ├── Attempt 1
  ├── Attempt 2  ← retry
  └── Attempt 3  ← retry
```

Execution 的 root trigger 仍然是：

```text
SCHEDULE
```

而不是 RETRY。

现有 `DataSyncTriggerType.RETRY` 暂时保留用于兼容已有 API / 持久化枚举值，但 v1.1 Retry Contract 不再把它作为新 Execution Root 的正常创建方式。

如果后续需要记录 Attempt 的启动原因，应由 Attempt 自己拥有明确字段，而不是修改 Execution Root trigger。

## 7. Retry Policy

v1.1 第一阶段 Retry Policy 使用显式、简单语义：

```text
maxAttempts
backoffSeconds
```

其中：

- `maxAttempts` 包含第一次执行。
- `maxAttempts = 1` 表示不自动重试。
- `maxAttempts = 3` 表示最多 Attempt 1 / 2 / 3，总计最多两次 Retry。
- `backoffSeconds` 表示固定等待时间。
- v1.1 不引入 exponential backoff / jitter / retry expression。

示例：

```text
maxAttempts = 3
backoffSeconds = 60

Attempt 1 FAILED
    ↓ wait 60s
Attempt 2 FAILED
    ↓ wait 60s
Attempt 3 FAILED
    ↓
Execution FAILED
```

PR3 只定义 Policy Contract，不决定最终 DTO / Entity 字段形状。

## 8. Retry Eligibility

v1.1 Generic Retry 只处理：

```text
Attempt FAILED
```

以下状态不自动 Retry：

```text
SUCCEEDED
CANCELED
LOST
```

原因：

- SUCCEEDED 已成功。
- CANCELED 表示用户明确终止，禁止系统自动重新启动。
- LOST 代表进程 / runtime ownership 丢失，恢复语义属于单独 Recovery Contract，不能偷偷等价成 Retry。

REALTIME 的应用重启续传仍由：

```text
Desired State / Auto Recovery
```

负责，不通过 Generic Retry 混合实现。

## 9. Execution State Aggregation

当前 `DataSyncInstanceStatus` 直接表示单次 Runtime 结果。

Attempt 模型落地后，Execution Status 变成 Attempt 状态的聚合结果。

目标状态机：

```text
Execution PENDING
   ↓
Attempt 1 RUNNING
   ↓
   ├── SUCCEEDED
   │      ↓
   │   Execution SUCCEEDED
   │
   ├── FAILED + retry available
   │      ↓
   │   Execution RETRY_WAITING
   │      ↓
   │   Attempt N+1
   │
   ├── FAILED + no retry available
   │      ↓
   │   Execution FAILED
   │
   └── user cancel
          ↓
       Execution CANCELED
```

`RETRY_WAITING` 已在 PR4 进入 `DataSyncInstanceStatus` 与 V3 Schema。

```text
PENDING / RUNNING / RETRY_WAITING
        =
Active Execution
```

因此 Schedule 并发判断、Task Unpublish 阻断和 Operations Center Active 判断都会覆盖等待重试状态。

## 10. Cancel Semantics

Cancel 的对象是 Execution，不是“只取消当前 Attempt 然后继续 Retry”。

用户执行 Stop / Cancel：

```text
Execution
    ↓ cancel
current Attempt → CANCELED
    ↓
pending retry → canceled
    ↓
Execution → CANCELED
```

硬规则：

> CANCELED Execution 永远不能由自动 Retry 再次启动。

用户想再次运行，必须显式创建新的 Execution。

## 11. Scheduler / Concurrency Relationship

PR2 当前 Scheduled Fire 在已有 Active Instance 时固定 Skip。

Attempt 模型加入后：

```text
PENDING
RUNNING
RETRY_WAITING
```

都属于 Active Execution。

因此未来 `SKIP_IF_RUNNING` 判断必须覆盖：

```text
RUNNING Attempt
or
waiting Retry
```

不能因为 Attempt 刚失败、正在 backoff，就让下一次 Cron Fire 创建第二个并发 Execution。

## 12. Definition Snapshot

Execution 的 `definitionSnapshot` 是所有 Attempts 的执行输入 Source of Truth。

Attempts 不保存新的完整 Task Definition 副本。

目标结构：

```text
Execution
  taskVersion
  definitionSnapshot
      │
      ├── Attempt 1
      ├── Attempt 2
      └── Attempt 3
```

Attempt 只保存 Runtime 结果与 Attempt-local diagnostics。

数据源凭证仍然不进入：

- Execution snapshot。
- Attempt。
- Retry Policy。
- 日志。

Runtime connection material 每次 Attempt 开始时仍按 Execution Snapshot 中的 datasource ID 从 Datasource capability 安全解析。

## 13. Metrics Semantics

Attempt 拥有自己的：

```text
readRows
writeRows
startTime
finishTime
error
```

Execution 层的运行指标不能把多个 Attempts 简单累加。

原因：

> Retry 可能重复读取 / 重复写入部分数据，直接求和会把业务同步量夸大。

v1.1 目标展示规则：

- Execution 列表显示当前 / 最终 Attempt 的指标。
- Execution Detail 可以展开 Attempts。
- 每个 Attempt 展示自己的完整指标。
- 不把所有 Attempt 的 readRows / writeRows 当作“本次业务总同步量”。

## 14. Final Result

Execution 最终状态规则：

```text
任何 Attempt SUCCEEDED
  → Execution SUCCEEDED

最后一个允许的 Attempt FAILED
  → Execution FAILED

用户 Cancel
  → Execution CANCELED

Runtime ownership 丢失且无独立 Recovery 接管
  → Execution LOST
```

一旦 Execution 进入最终终态：

```text
SUCCEEDED
FAILED
CANCELED
LOST
```

不得再自动追加 Attempt。

FAILED Execution 的“再次运行”是新 Execution。

## 15. Persistence Target

PR4 已新增：

```text
V3__data_sync_execution_attempt.sql
```

持久化方向：

```text
yak_ops_data_sync_instance
  → Execution Root

yak_ops_data_sync_attempt
  → Attempt History
```

V3 同时完成：

- `yak_ops_data_sync_task.retry_policy`：Task 级 Retry Policy JSON。
- Execution Root：`max_attempts / backoff_seconds / current_attempt / next_retry_time`。
- Execution Status：新增 `RETRY_WAITING`。
- `yak_ops_data_sync_attempt`：Attempt 状态、指标、时间与失败诊断。
- 已有 Task backfill 为 `maxAttempts=1 / backoffSeconds=60`，保持升级前不自动 Retry。

禁止：

- 回改 `V1__baseline.sql`。
- 回改已发布 `V2__data_sync_schedule.sql`。
- 为 Retry 复制一套第二 Task / Instance 模型。
- 用 Quartz Job / Trigger 当 Attempt Persistence。
- 让 YakFlow Runtime 自己决定产品 Retry Policy。

## 16. Operations Center Contract

目标展示：

```text
Execution #E1  SUCCEEDED
  ├── Attempt 1  FAILED
  ├── Attempt 2  FAILED
  └── Attempt 3  SUCCEEDED
```

列表默认展示 Execution，不把每个 Attempt 平铺成独立任务记录。

Execution Detail 再展示 Attempt History。

至少需要：

- Attempt No。
- Status。
- Start / Finish。
- Duration。
- readRows / writeRows。
- Error。
- 是否由 Retry 创建。

PR3 不实现 UI。

## 17. PR4 Runtime

PR4 已实现：

- OFFLINE / REALTIME FAILED Attempt 自动 Retry。
- 固定 `backoffSeconds`。
- Attempt #N 独立持久化。
- Execution Root 不变。
- root trigger 不变。
- 每次 Retry 继续使用同一个 `definitionSnapshot`。
- REALTIME Retry 继续复用同 Task + definitionVersion 的 CDC state。
- Attempt metrics 独立持久化，Execution 只镜像当前 / 最终 Attempt 指标。
- `GET /instances/{id}/attempts` 只读 Attempt History。
- CANCELED / LOST 不自动 Retry。
- RETRY_WAITING 可被用户 Cancel。
- 应用重启时 PENDING / RUNNING Attempt 与 PENDING / RUNNING / RETRY_WAITING Execution 统一标记 LOST。

Backoff 由当前进程内虚拟线程等待，不使用 Quartz。

因此：

> PR4 不提供跨进程 durable retry timer。

应用在 RETRY_WAITING 时退出，下一次启动会标记 Execution LOST；跨进程恢复由后续 Recovery Contract 负责。

## 18. PR4 Non-Goals

本 PR 不做：

- Retry Policy 配置 UI。
- exponential backoff / jitter。
- durable retry timer。
- Quartz Retry Trigger。
- 自动恢复 LOST。
- Realtime Desired State / Auto Recovery。
- Distributed Retry ownership / fencing。
- Attempt Operations UI。

## 19. Implementation Invariants

真正实现 Retry 时必须证明：

- Retry 不创建新的 Execution Root。
- Retry 不改变 root trigger type。
- Retry 复用同一个 definitionSnapshot。
- maxAttempts 包含 Attempt #1。
- CANCELED 不 Retry。
- Generic LOST 不 Retry。
- backoff 期间 Execution 仍被视为 Active。
- 最后 Attempt 决定 FAILED 或 SUCCEEDED。
- Retry 不依赖 Quartz。
