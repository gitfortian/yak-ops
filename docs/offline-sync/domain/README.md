# Offline Sync Domain

> 当前实现阶段：Stage 6 / Wave 4。本文保留当前代码到目标领域的迁移映射；日常开发规则以模块 `DOMAIN.md` 为准。

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

硬规则：`Task != Batch != Attempt`；Retry 复用原 Batch/Scope/Snapshot；`UNKNOWN != FAILED`；Batch runtime status 只由 latest Attempt 推导。

## 2. 当前 Mapping

| 当前实现 | 目标语义 | 状态 |
| --- | --- | --- |
| `OfflineJobDefinition` | Task + SyncDefinition + Schedule/Retry Policy + 查询投影 | ADAPT |
| `definition_json` | 当前 SyncDefinition 表现 | KEEP |
| `mode=GUIDE_SINGLE/GUIDE_MULTI` | UI/配置模式，不是领域类型 | ADAPT |
| `version` | `DefinitionRevision` | ADAPT |
| `job_spec_json` | Link-Up 执行产物，不是定义真相 | ADAPT |
| `release_state` | Task 是否允许产生新 Batch | KEEP |
| `OfflineSchedule` | SchedulePolicy + RetryPolicy + runtime projection | ADAPT |
| `yak_offline_batch_execution` | `BatchExecution` persistence + runtime truth | WAVE 4 DONE |
| `yak_offline_job_execution.batch_id` | Attempt -> Batch 绑定 | WAVE 1 DONE / nullable history |
| Trigger claim | `Trigger -> Batch -> Attempt 1` | WAVE 2 DONE |
| Schedule identity | `scheduleId + plannedFireTime` BatchKey | WAVE 2 DONE |
| `retry_created` | FAILED Attempt 的 durable Retry reservation | WAVE 3 DONE |
| Retry Attempt | same Batch + frozen Snapshot/RetryPolicy | WAVE 3 DONE |
| `UNKNOWN` / legacy `LOST` | 不确定结果，持续 reconcile，不自动 Retry | WAVE 3 DONE |
| Batch status dual-write | latest Attempt -> BatchStatus | WAVE 4 DONE |
| Task command occupancy | `RUNNING/WAITING_RETRY/UNKNOWN` Batch | WAVE 4 DONE |
| Task `last-*` | Read Model projection only | WAVE 4 DONE |
| `OfflineJobExecution` | Attempt persistence compatibility view | MIGRATE |
| `attempt_no / retry_from_execution_id` | Attempt 次序/血缘 | KEEP |
| `idempotency_key / external_execution_id` | Attempt 幂等与外部提交身份 | KEEP |
| `engine_job_id / worker_instance_id` | Engine evidence | KEEP |
| metrics / error / start/end | Attempt 运行证据 | KEEP |
| legacy execution snapshot | 过渡期兼容副本；Retry 从 Attempt 1 读取冻结 JobSpec | MIGRATE |
| `OfflineExecutionEvent` | Attempt 事件历史 | KEEP |
| Link-Up / credential resolver | Infrastructure boundary | KEEP |

## 3. Wave 4 Runtime Truth

Wave 4 把运行真相从 Task/legacy execution 判断切换为：

```text
Task command
    │
    ▼
BatchExecution
    │ status
    ▼
latest ExecutionAttempt
```

BatchStatus 推导规则：

```text
CREATED / SUBMITTED / QUEUED / RUNNING -> RUNNING
FAILED + nextRetryTime                 -> WAITING_RETRY
FAILED                                 -> FAILED
UNKNOWN / legacy LOST                  -> UNKNOWN
SUCCEEDED                              -> SUCCEEDED
CANCELED                               -> CANCELED
```

关键约束：

- Attempt persistence 更新后同步刷新 Batch status；
- Batch status 每次重新读取同 Batch 的 latest Attempt 后推导，不使用调用方传入的旧 Attempt 直接覆盖；
- 旧 Attempt 的晚到 reconcile 不能把新 Attempt 已形成的 Batch 状态回退；
- Initial Attempt 创建后 Batch 从 `PENDING` 切到 `RUNNING`；
- Retry Attempt 创建后 Batch 从 `WAITING_RETRY` 切回 `RUNNING`；
- FAILED 且仍有 Retry 窗口时 Batch 是 `WAITING_RETRY`，仍占用 V1 Task 执行槽位；
- UNKNOWN Batch 继续占用执行槽位，必须先 reconcile；
- `SUCCEEDED / FAILED / CANCELED` 是 Batch 终态。

### 命令侧切换

以下判断现在统一读取 Batch runtime truth：

- 新 Batch 创建的 V1 单任务并发限制；
- Schedule 到点时是否跳过本次触发；
- Task 下线；
- Task 删除；
- Task 编辑保护；
- Task 级 `cancelLatest`。

`cancelLatest` 不再读取 `Task.lastExecutionId`。对于 `RUNNING / UNKNOWN` Batch，停止 latest Attempt；对于 `WAITING_RETRY` Batch，没有活动引擎 Attempt，因此直接关闭 Retry window，并把 Batch 标记为 `CANCELED`，已失败 Attempt 的历史证据保持不变。

### Task last-* 投影

`lastExecutionId / lastEngineJobId / lastJobStatus / lastErrorMessage / metrics` 继续写入 Task，目的是兼容列表和详情查询。它们不再用于运行、停止、编辑、下线、删除或调度并发判断。

### 历史 Batch 回填

Wave 2 / Wave 3 期间 Batch 创建后长期保持初始 `PENDING`，因此 Wave 4 新增 V3 data migration：按照每个 Batch 的 latest bound Attempt 回填 `yak_offline_batch_execution.status`。不增加新表或新列。

## 4. 当前仍未解决的 Domain Debt

### Backfill / Cursor 尚未迁移

补数与 Cursor 推进还没有按目标模型完整实现。Wave 5 需要保证：

```text
Backfill request
  -> Batch group
  -> explicit BatchScope

Cursor
  -> only Batch SUCCEEDED advances
```

`FAILED / CANCELED / UNKNOWN` 一律不能推进 Cursor。

### Legacy Attempt persistence 仍保留 execution 形态

`yak_offline_job_execution` 已承担 Attempt persistence，但类名、表名和部分字段仍保留旧 execution-centered 结构。Wave 6 再做 contract/cleanup，不在 Wave 4 为了命名整洁重建历史。

### Wave 1 前历史没有 Batch identity

旧 execution 允许 `batch_id = NULL`。这类记录没有冻结 BatchScope / RetryPolicy Snapshot，不参与新的 Batch runtime truth，也不允许通过 current Task/SchedulePolicy 猜测后安全 Retry。

## 5. Wave 1-4 已完成迁移

当前持久化关系：

```text
yak_offline_job_definition
        │ 1:N
        ▼
yak_offline_batch_execution
        │ 1:N
        ▼
yak_offline_job_execution
```

已完成约束：

- execution `batch_id` nullable，旧记录不强制回填；
- Trigger 先创建 Batch，再创建 Attempt 1；
- Schedule BatchKey 使用稳定 planned fire identity；
- Retry 不创建新 Batch，不回读 current Task；
- RetryPolicy 从 Batch `ExecutionSnapshot` 读取，不回读 current SchedulePolicy；
- Retry 使用 Attempt 1 已冻结的逻辑 JobSpec，只在提交边界解析最新凭据；
- `retry_created` 使用 CAS durable reservation，reservation 与新 Attempt insert 同事务；
- FAILED 才能 Retry；UNKNOWN / legacy LOST 只 reconcile；
- Batch status 与 Attempt 生命周期 dual-write；
- Batch status 只由 latest Attempt 推导；
- V1 Task occupancy 只看 `RUNNING / WAITING_RETRY / UNKNOWN` Batch；
- Task `last-*` 只作为查询投影；
- 不创建物理 FK；
- Engine Job、metrics、error 继续属于 Attempt persistence。

## 6. 迁移波次

```text
Wave 0  DONE  Core VO + compatibility mapper
Wave 1  DONE  Batch persistence + execution.bind(batch_id)
Wave 2  DONE  Trigger -> Batch -> Attempt 1 + Schedule BatchKey
Wave 3  DONE  Retry / UNKNOWN + durable retry reservation
Wave 4  DONE  Runtime truth -> Batch/Attempt; Task last-* projection only
Wave 5  NEXT  Backfill / Cursor
Wave 6        Legacy cleanup
```

采用 `expand -> dual read/write -> switch -> verify -> contract`，不做一次性 schema 重建。

## 7. 当前结论

1. Batch 已经同时承担业务身份和运行状态真相；Attempt 承担每次实际提交证据。
2. Retry 固定 Batch/Scope/Snapshot/RetryPolicy，UNKNOWN 与 FAILED 分离。
3. Task 命令生命周期不再依赖 `last-*` 或 `hasActiveExecution()`；Task `last-*` 仅保留查询投影职责。
4. legacy `yak_offline_job_execution` 继续作为 Attempt persistence compatibility view，命名和历史清理留到 Wave 6。
5. 下一步 Wave 5 只处理 Backfill / Cursor，不提前抽 Shared Sync Kernel，也不引入 immutable DefinitionVersion。
