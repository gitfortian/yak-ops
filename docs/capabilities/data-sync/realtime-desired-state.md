# Realtime Desired State + Auto Recovery

Status: v1.1 PR5 — Backend

Depends On:

- [Data Sync Capability](./README.md)
- [Task Publication Lifecycle](./task-lifecycle.md)
- [Execution Retry / Attempt Contract](./execution-retry-attempt.md)
- [Yak Ops v1.1.0 Release Contract](../../release/v1.1.0.md)

## 1. Goal

REALTIME Task 需要区分三种不同状态：

```text
Publication State
Desired State
Execution State
```

它们回答不同问题：

```text
PUBLISHED / UNPUBLISHED
  → 这个 Task 当前是否允许创建新的 Execution？

RUNNING / STOPPED desiredState
  → 用户希望 REALTIME Task 持续运行还是保持停止？

PENDING / RUNNING / RETRY_WAITING / ...
  → 当前某个具体 Execution 正在发生什么？
```

三者禁止互相替代。

## 2. Desired State

REALTIME Task 新增：

```text
DataSyncDesiredState
├── STOPPED = 0
└── RUNNING = 1
```

字段持久化在：

```text
yak_ops_data_sync_task.desired_state
```

OFFLINE Task 固定为 STOPPED；Scheduler 继续通过 Schedule Definition 管理，不复用 desiredState。

## 3. User Commands

### Start

用户启动一个 PUBLISHED REALTIME Task：

```text
desiredState = RUNNING
        ↓
create MANUAL Execution
        ↓
Realtime Runtime
```

Desired State 和 Execution Root 在同一个 Business command 中处理。

如果 Execution 后续 FAILED / LOST，desiredState 不自动改回 STOPPED。

原因：

> 运行失败不等于用户改变了“我希望它运行”的意图。

### Stop

用户 Stop 当前 REALTIME Execution：

```text
desiredState = STOPPED
        ↓
cancel active Execution / Attempt
```

STOPPED 后应用启动不得自动恢复该 Task。

### Unpublish

REALTIME Task 成功 Unpublish 时：

```text
desiredState = STOPPED
status = UNPUBLISHED
```

因此一个 UNPUBLISHED REALTIME Task 永远不会被 Auto Recovery 拉起。

## 4. Application Restart

旧进程不能恢复 LocalExecution 对象。

启动阶段分两步：

```text
Phase A
PENDING / RUNNING / RETRY_WAITING Execution
        ↓
LOST

Phase B
PUBLISHED + REALTIME + desiredState=RUNNING
        ↓
no active Execution
        ↓
create new AUTO_RECOVERY Execution
```

旧 Execution 不复活。

新的 Execution 拥有新的 Instance ID。

## 5. CDC Continuation

Auto Recovery 使用当前 PUBLISHED Task 的同一个：

```text
taskId
definitionVersion
```

Realtime CDC state identity 仍然是：

```text
{workspaceId}/{taskId}/v{definitionVersion}
```

因此新的 AUTO_RECOVERY Execution：

```text
new Execution
     ↓
same task/version state directory
     ↓
reuse offsets.dat + schema-history.dat
     ↓
continue from completed Debezium offset
```

这不是：

- 复活旧 LocalExecution。
- Generic YakFlow checkpoint restore。
- fresh snapshot 保证。
- exactly-once。

仍然是现有 at-least-once continuation contract。

## 6. Root Trigger

Auto Recovery 创建新的 Execution Root：

```text
triggerType = AUTO_RECOVERY
```

它不是 RETRY Attempt。

例如：

```text
AUTO_RECOVERY Execution
  ├── Attempt #1 FAILED
  ├── RETRY_WAITING
  └── Attempt #2 RUNNING
```

Root trigger 始终保持 AUTO_RECOVERY。

Retry 仍然只发生在该 Execution 内部。

## 7. Retry Relationship

Generic Retry 与 Auto Recovery 解决不同问题：

```text
FAILED Attempt
  → Retry / Attempt

process restart / ownership loss
  → Desired State / Auto Recovery
```

CANCELED 不 Retry，也不 Auto Recover，因为 Stop command 已把 desiredState 写成 STOPPED。

RETRY_WAITING 在进程重启时会变 LOST；随后如果：

```text
desiredState = RUNNING
```

会创建新的 AUTO_RECOVERY Execution。

不会跨进程继续原 Execution 的 Attempt 序号。

## 8. First Upgrade Compatibility

V4 Migration 新增：

```text
desired_state
```

默认值：

```text
STOPPED
```

为了不破坏升级前正在运行的 REALTIME Task，Migration 会识别：

```text
PUBLISHED REALTIME Task
+
PENDING / RUNNING / RETRY_WAITING Execution
```

并回填：

```text
desired_state = RUNNING
```

因此：

- 升级前正在运行的 REALTIME Task → 重启后自动恢复。
- 升级前已经停止的 REALTIME Task → 保持 STOPPED。
- OFFLINE Task → 保持 STOPPED。

## 9. Startup Failure

Auto Recovery 逐 Task 执行。

单个 Task 恢复失败：

- 不阻止应用启动。
- 记录脱敏错误。
- desiredState 保持 RUNNING。
- 不修改为 STOPPED。
- 不无限创建新 Execution。

PR5 不提供常驻 watchdog。

后续 Operations Center 可以展示：

```text
desired = RUNNING
actual = no active execution / latest FAILED or LOST
```

由用户决定手工启动、停止意图或排查外部依赖。

## 10. Single-node Boundary

PR5 仍然是单节点 Contract。

不提供：

- leader election。
- multi-node fencing。
- distributed desired-state reconciliation。
- exactly-once ownership。
- continuous recovery watchdog。

在未来多节点 Runtime 中，Desired State 可以继续保留为产品意图层，但 reconciliation 必须增加唯一 owner / fencing。

## 11. Persistence

PR5 新增：

```text
V4__realtime_desired_state.sql
```

变化：

- `yak_ops_data_sync_task.desired_state`。
- realtime desired-state startup query index。
- Execution trigger comment 增加 AUTO_RECOVERY。

不修改：

- `V1__baseline.sql`。
- `V2__data_sync_schedule.sql`。
- `V3__data_sync_execution_attempt.sql`。

## 12. Acceptance

PR5 至少证明：

- 手工启动 REALTIME → desiredState=RUNNING。
- Stop / Unpublish → desiredState=STOPPED。
- OFFLINE 不使用 RUNNING desired state。
- 启动恢复只扫描 PUBLISHED + REALTIME + RUNNING desired state。
- 已有 Active Execution 时不重复恢复。
- Auto Recovery 创建新 Execution。
- Auto Recovery root trigger = AUTO_RECOVERY。
- taskVersion 保持不变。
- Realtime state identity 继续使用同 taskId + definitionVersion。
- 一个 Task 恢复失败不阻断其它启动恢复。
