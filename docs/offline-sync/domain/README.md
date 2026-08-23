# Offline Sync Domain

> 当前实现阶段：Stage 6 / Wave 1。本文保留当前代码到目标领域的迁移映射；日常开发规则以模块 `DOMAIN.md` 为准。

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
| `yak_offline_batch_execution` | `BatchExecution` persistence | WAVE 1 DONE |
| `yak_offline_job_execution.batch_id` | Attempt -> Batch 绑定 | WAVE 1 DONE / nullable |
| `OfflineJobExecution` | legacy 运行链仍混合 Batch + Attempt | MIGRATE |
| `attempt_no / retry_from_execution_id` | Attempt 次序/血缘 | KEEP |
| `idempotency_key / external_execution_id` | Attempt 幂等与外部提交身份 | KEEP |
| `engine_job_id / worker_instance_id` | Engine evidence | KEEP |
| metrics / error / start/end | Attempt 运行证据 | KEEP |
| legacy execution snapshot | 过渡期兼容副本 | MIGRATE |
| `OfflineExecutionEvent` | Attempt 事件历史 | KEEP |
| Task `last-*` | 只允许做 Read Model projection | MIGRATE |
| Link-Up / credential resolver | Infrastructure boundary | KEEP |

## 3. 仍未解决的 Domain Debt

### Trigger 尚未切 Batch

Batch 表已经存在，但当前手动/调度/工作流执行仍直接创建 legacy execution。Wave 2 才切成：

```text
Trigger -> claim Batch -> create Attempt 1
```

### Retry 漂移

当前 `retryFrom(previous)` 会重新读取 current Task；`configureRetry()` 还会读取 current Schedule/RetryPolicy。

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

### Schedule 幂等不足

当前 Schedule 仍主要靠 `hasActiveExecution(taskId)` 防重复。Wave 2 必须使用：

```text
SCHEDULE BatchKey = scheduleId + plannedFireTime
```

### Runtime truth 尚未切换

Task `lastJobStatus` 仍参与部分命令判断；生命周期最终必须只读 Batch/Attempt。

## 4. Wave 1 Persistence

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

Wave 1 约束：

- 新增 Batch 表，不重建旧 execution/event 历史；
- execution `batch_id` nullable，旧记录不要求回填；
- `bindBatch(executionId, batchId)` 只允许 `NULL -> batchId` 或重复绑定同一个 Batch；
- 不创建物理 FK；
- Batch Repository 只持久化 Batch 业务身份、Scope、Snapshot/RetryPolicy 和状态；
- Engine Job、metrics、error 继续属于 Attempt persistence；
- 现有 Service/Orchestrator/Schedule/Retry 尚不使用 Batch Repository。

## 5. 迁移波次

```text
Wave 0  DONE  Core VO + compatibility mapper
Wave 1  DONE  Batch persistence + execution.bind(batch_id)
Wave 2  NEXT  Trigger -> Batch -> Attempt 1 + Schedule BatchKey
Wave 3        Retry / UNKNOWN + durable retry reservation
Wave 4        Runtime truth -> Batch/Attempt; Task last-* projection only
Wave 5        Backfill / Cursor
Wave 6        Legacy cleanup
```

采用 `expand -> dual read/write -> switch -> verify -> contract`，不做一次性 schema 重建。

## 6. 当前结论

1. Batch 已有真实持久化身份，但尚未成为现有运行链的入口。
2. `yak_offline_job_execution` 继续演进为 Attempt persistence，不推倒重建。
3. Wave 2 是第一次运行链切换：Trigger 先 claim Batch，再创建 Attempt 1。
4. Retry/UNKNOWN、Task runtime truth、Backfill/Cursor 继续按后续 Wave 独立迁移。
5. 暂不引入 immutable DefinitionVersion，也不抽 Realtime/Offline Shared Kernel。
