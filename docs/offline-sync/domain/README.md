# Offline Sync Domain

> 当前阶段：Stage 4。只做现有代码到目标领域的映射，不改业务实现。

## 1. 目标模型

```text
OfflineSyncTask
      │ trigger
      ▼
BatchExecution
      ├── BatchKey
      ├── BatchScope
      ├── ExecutionSnapshot
      └── ExecutionAttempt 1..N
                │
                ▼
          Engine Adapter
```

硬规则：`Task != Batch != Attempt`；Retry 复用原 Batch/Scope/Snapshot；`UNKNOWN != FAILED`。

## 2. Mapping

标记：`KEEP` 直接复用；`ADAPT` 保留但调整语义；`MIGRATE` 需要迁移；`DEBT` 当前行为违反目标领域。

| 当前实现 | 目标语义 | 结论 |
| --- | --- | --- |
| `OfflineJobDefinition` | Task + SyncDefinition + Schedule/Retry Policy + 查询投影 | ADAPT |
| `definition_json` | 当前 SyncDefinition 表现 | KEEP |
| `mode=GUIDE_SINGLE/GUIDE_MULTI` | UI/配置模式，不是领域类型 | ADAPT |
| `version` | `DefinitionRevision` | ADAPT |
| `job_spec_json` | Link-Up 执行产物，不是定义真相 | ADAPT |
| `release_state` | Task 是否允许产生新 Batch | KEEP |
| `OfflineSchedule` | SchedulePolicy + RetryPolicy + runtime projection | ADAPT |
| `OfflineJobExecution` / `yak_offline_job_execution` | 当前更接近 `ExecutionAttempt` | MIGRATE |
| `attempt_no / retry_from_execution_id` | Attempt 次序/血缘 | KEEP |
| `idempotency_key / external_execution_id` | Attempt 幂等与外部提交身份 | KEEP |
| `engine_job_id / worker_instance_id` | Engine evidence | KEEP |
| metrics / error / start/end | Attempt 运行证据 | KEEP |
| `definition_snapshot_json / submitted_config` | 应归属 Batch 的 ExecutionSnapshot | MIGRATE |
| `OfflineExecutionEvent` | Attempt 事件历史 | KEEP |
| `OfflineExecutionStatus` | Attempt 状态 | MIGRATE |
| `OfflineExecutionClaimService` | Batch/Attempt claim 的基础 | ADAPT |
| `OfflineExecutionReconciler` | Attempt reconcile | ADAPT |
| `OfflineScheduleHandler` | Schedule trigger adapter | ADAPT |
| Task `last-*` | 只允许做 Read Model projection | MIGRATE |
| Link-Up / credential resolver | Infrastructure boundary | KEEP |

## 3. 已确认 Domain Debt

### Retry 漂移

当前 `retryFrom(previous)` 重新进入 `execute(definitionId, ...)`，会读取 **current Task**；`configureRetry()` 还会读取 **current Schedule/RetryPolicy**。

目标：

```text
Retry
 -> same Batch
 -> same ExecutionSnapshot
 -> same RetryPolicy Snapshot
 -> new Attempt
```

### LOST 自动重试

当前 `LOST` 会进入 retry candidate。目标中无法确认引擎结果属于 `UNKNOWN`，必须先 reconcile，不能直接 Retry。

### 缺少 Batch 身份

当前一条 `yak_offline_job_execution` 同时承担 Batch + Attempt。数据库缺少：

```text
batch_id
batch_key
batch_scope
batch_status
```

现有 execution 表包含大量 Attempt 字段，因此优先把它演进为 Attempt persistence，而不是推倒重建。

### Schedule 幂等不足

当前 Schedule 主要靠 `hasActiveExecution(taskId)` 防重复，没有稳定 `BatchKey`。同一计划触发重复回调、且前一次已快速结束时，可能形成第二个业务批次。

目标：

```text
SCHEDULE BatchKey = scheduleId + plannedFireTime
```

### WAITING_RETRY 未占用 Task 执行资格

当前 FAILED/LOST execution 等待 `nextRetryTime` 时已经不算 active，新的手动/调度执行可能先进入；这与 V1 “同 Task 最多一个 RUNNING / WAITING_RETRY / UNKNOWN Batch” 不一致。

### Task last-* 仍参与命令判断

`lastJobStatus` 当前不仅用于展示/筛选，还参与 `ensureEditable()`。目标中 Task `last-*` 只能是查询投影，生命周期判断必须读取 Batch/Attempt。

### 状态更新缺少目标状态机保护

当前 reconcile 主要依赖 Engine 返回状态直接覆盖 execution。后续需要按 Attempt 状态机和单调版本处理 stale/out-of-order snapshot，终态不能被旧响应复活。

## 4. 现有资产怎么复用

### 当前 execution 表

不建议重建整套执行历史。目标迁移：

```text
new BatchExecution persistence
        │ 1:N
        ▼
yak_offline_job_execution
        ≈ ExecutionAttempt persistence
```

先新增 `batch_id` 绑定；物理表名可暂时保留，命名清理放最后。

### Snapshot

当前每个 execution 已保存 definition/JobSpec snapshot，这是好基础。迁移后：

```text
Batch owns immutable ExecutionSnapshot
Attempt references Batch Snapshot
```

过渡期允许 Attempt 保留兼容副本，但不能成为第二份真相。

### Task 行锁与幂等

Task 行锁可以继续作为 V1 串行化命令入口；现有 Attempt `idempotencyKey` 继续使用，同时新增 BatchKey 解决“同一个业务批次”的幂等。

### 凭据边界

现有 `sanitizeForPersistence + resolveExecutionJobSpec()` 方向正确：SyncDefinition/Snapshot 不保存 DataSource 密码，Attempt 提交时再解析当前凭据。

## 5. 推荐迁移波次

```text
Wave 0  Core VO + compatibility mapper
        BatchKey / BatchScope / BatchExecution / ExecutionAttempt

Wave 1  Batch persistence
        新增 Batch 表；现有 execution 绑定 batch_id

Wave 2  Trigger -> Batch -> Attempt 1
        手动/调度/工作流先 claim Batch，再创建 Attempt
        Schedule 使用 planned BatchKey

Wave 3  Retry / UNKNOWN
        Retry 固定 Batch Snapshot/Policy
        UNKNOWN 禁止自动 Retry
        持久化 retry reservation

Wave 4  Runtime truth
        生命周期只读 Batch/Attempt
        Task last-* 降级为 projection

Wave 5  Backfill / Cursor
        shared Snapshot、Scope identity、成功后推进 cursor

Wave 6  Cleanup
        LOST / legacy retry fields / GUIDE mode / 物理命名兼容清理
```

采用 `expand -> dual read/write -> switch -> verify -> contract`，不做一次性 schema 重建。

## 6. Stage 4 结论

1. 当前 Task/Engine/事件/凭据边界大部分可复用。
2. `yak_offline_job_execution` 应优先演进为 Attempt persistence。
3. 新增 Batch persistence 是后续重构的核心支点。
4. Retry 漂移、LOST 自动重试、Schedule 无 BatchKey 是优先安全债。
5. 暂不引入 immutable DefinitionVersion；Batch Snapshot 继续作为执行真相。
6. 暂不抽 Realtime/Offline Shared Kernel。

下一阶段先建立短小的 `DOMAIN.md`，把上述规则变成 AI 修改离线同步前必须遵守的约束；仍不开始大规模重构。