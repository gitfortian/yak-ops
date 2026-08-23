# Offline Sync Domain

> 当前实现阶段：Stage 6 / Wave 3。本文保留当前代码到目标领域的迁移映射；日常开发规则以模块 `DOMAIN.md` 为准。

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
| Trigger claim | `Trigger -> Batch -> Attempt 1` | WAVE 2 DONE |
| Schedule identity | `scheduleId + plannedFireTime` BatchKey | WAVE 2 DONE |
| `retry_created` | FAILED Attempt 的 durable Retry reservation | WAVE 3 DONE |
| Retry Attempt | same Batch + frozen Snapshot/RetryPolicy | WAVE 3 DONE |
| `UNKNOWN` / legacy `LOST` | 不确定结果，持续 reconcile，不自动 Retry | WAVE 3 DONE |
| `OfflineJobExecution` | legacy 运行链仍混合 Batch + Attempt | MIGRATE |
| `attempt_no / retry_from_execution_id` | Attempt 次序/血缘 | KEEP |
| `idempotency_key / external_execution_id` | Attempt 幂等与外部提交身份 | KEEP |
| `engine_job_id / worker_instance_id` | Engine evidence | KEEP |
| metrics / error / start/end | Attempt 运行证据 | KEEP |
| legacy execution snapshot | 过渡期兼容副本；Retry 从 Attempt 1 读取冻结 JobSpec | MIGRATE |
| `OfflineExecutionEvent` | Attempt 事件历史 | KEEP |
| Task `last-*` | 只允许做 Read Model projection | MIGRATE |
| Link-Up / credential resolver | Infrastructure boundary | KEEP |

## 3. 当前仍未解决的 Domain Debt

### Runtime truth 尚未切换

Batch 已经真实创建，Retry/UNKNOWN 也已经按 Batch 规则运行，但 Task `lastJobStatus` 和 legacy execution 查询仍参与部分命令判断。

Wave 4 目标：

```text
Runtime truth
  -> BatchExecution
  -> latest ExecutionAttempt

Task last-*
  -> projection only
```

### Batch 生命周期尚未完整 dual-write

`yak_offline_batch_execution.status` 已有持久化字段，但当前执行状态更新仍主要落在 legacy execution；Wave 4 才会把 Batch / Attempt 生命周期切成运行真相。

### Legacy history 无 Batch identity

Wave 1 前历史 execution 允许 `batch_id = NULL`。这类记录没有冻结的 BatchScope / RetryPolicy Snapshot，因此 Wave 3 明确禁止从它们安全 Retry，不通过回读 current Task/SchedulePolicy 猜测历史语义。

## 4. Wave 1-3 已完成迁移

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

- execution `batch_id` nullable，旧记录不要求回填；
- `bindBatch(executionId, batchId)` 只允许 `NULL -> batchId` 或重复绑定同一个 Batch；
- Trigger 先创建 Batch，再创建 Attempt 1；
- Schedule BatchKey 使用稳定 planned fire identity；
- Retry 不创建新 Batch，不回读 current Task；
- RetryPolicy 从 Batch `ExecutionSnapshot` 读取，不回读 current SchedulePolicy；
- Retry 使用 Attempt 1 已冻结的逻辑 JobSpec，只在提交边界解析最新凭据；
- `retry_created` 通过 CAS reservation 防止并发创建重复 Attempt，reservation 与新 Attempt insert 同事务；
- FAILED 才能 Retry；UNKNOWN / legacy LOST 只 reconcile；
- 不创建物理 FK；
- Engine Job、metrics、error 继续属于 Attempt persistence。

## 5. 迁移波次

```text
Wave 0  DONE  Core VO + compatibility mapper
Wave 1  DONE  Batch persistence + execution.bind(batch_id)
Wave 2  DONE  Trigger -> Batch -> Attempt 1 + Schedule BatchKey
Wave 3  DONE  Retry / UNKNOWN + durable retry reservation
Wave 4  NEXT  Runtime truth -> Batch/Attempt; Task last-* projection only
Wave 5        Backfill / Cursor
Wave 6        Legacy cleanup
```

采用 `expand -> dual read/write -> switch -> verify -> contract`，不做一次性 schema 重建。

## 6. 当前结论

1. Batch 已成为 Trigger 的真实业务身份，Attempt 1 与后续 Retry Attempt 都绑定同一 Batch。
2. Retry 已固定 Batch/Scope/Snapshot/RetryPolicy，不再通过 current Task/SchedulePolicy 漂移。
3. UNKNOWN 已与 FAILED 分离，不确定结果只 reconcile，不自动 Retry。
4. `yak_offline_job_execution` 继续作为过渡期 Attempt persistence；Batch/Attempt runtime truth 在 Wave 4 切换。
5. 暂不引入 immutable DefinitionVersion，也不抽 Realtime/Offline Shared Kernel。
