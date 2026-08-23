# Offline Sync Domain

> 当前实现阶段：Stage 6 / Wave 5。本文保留当前代码到目标领域的迁移映射；日常开发规则以模块 `DOMAIN.md` 为准。

## 1. 目标模型

```text
OfflineSyncTask
      │ trigger / backfill request
      ▼
BatchExecution
      ├── BatchKey
      ├── BatchScope
      ├── ExecutionSnapshot
      │     └── logical JobSpec (no credentials)
      └── ExecutionAttempt 1..N
                │
                ▼
          Engine Adapter

CursorRange Batch SUCCEEDED
      │
      ▼
Task Cursor CAS
```

硬规则：`Task != Batch != Attempt`；Retry 复用原 Batch/Scope/Snapshot；`UNKNOWN != FAILED`；Batch runtime status 只由 latest Attempt 推导；Cursor 只由 SUCCEEDED Batch 推进。

## 2. 当前 Mapping

| 当前实现 | 目标语义 | 状态 |
| --- | --- | --- |
| `OfflineJobDefinition` | Task + SyncDefinition + Schedule/Retry Policy + 查询投影 | ADAPT |
| `definition_json` | 当前 SyncDefinition 表现 | KEEP |
| `mode=GUIDE_SINGLE/GUIDE_MULTI` | UI/配置模式，不是领域类型 | ADAPT |
| `version` | `DefinitionRevision` | ADAPT |
| `job_spec_json` | current logical JobSpec，不是 runtime truth | ADAPT |
| `release_state` | Task 是否允许产生新 Batch | KEEP |
| `OfflineSchedule` | SchedulePolicy + RetryPolicy + runtime projection | ADAPT |
| `yak_offline_batch_execution` | Batch persistence + runtime truth | WAVE 4 DONE |
| `logical_job_spec_json` | Batch 冻结的不含凭据逻辑 JobSpec | WAVE 5 DONE |
| Trigger claim | `Trigger -> Batch -> Attempt 1` | WAVE 2 DONE |
| Schedule identity | `scheduleId + plannedFireTime` BatchKey | WAVE 2 DONE |
| Retry Attempt | same Batch + frozen Snapshot/RetryPolicy/JobSpec | WAVE 3/5 DONE |
| `UNKNOWN` / legacy `LOST` | 持续 reconcile，不自动 Retry | WAVE 3 DONE |
| Batch status dual-write | latest Attempt -> BatchStatus | WAVE 4 DONE |
| Task `last-*` | Read Model projection only | WAVE 4 DONE |
| Backfill request | 一次请求 -> 一组 PENDING BACKFILL Batch | WAVE 5 DONE |
| Backfill dispatcher | PENDING reservation -> Attempt 1，V1 单 Task 串行 | WAVE 5 DONE |
| `yak_offline_sync_cursor` | Task Cursor route + position + CAS version | WAVE 5 DONE |
| Cursor advancement | only SUCCEEDED CursorRange Batch | WAVE 5 DONE |
| `OfflineJobExecution` | Attempt persistence compatibility view | MIGRATE |
| `attempt_no / retry_from_execution_id` | Attempt 次序/血缘 | KEEP |
| `engine_job_id / worker_instance_id` | Engine evidence | KEEP |
| metrics / error / start/end | Attempt 运行证据 | KEEP |
| legacy execution snapshot | Attempt 级兼容副本 | MIGRATE |
| `OfflineExecutionEvent` | Attempt 事件历史 | KEEP |
| Link-Up / credential resolver | Infrastructure boundary | KEEP |

## 3. Wave 5 Backfill Group

Backfill 不创建新的 Task 类型。一次请求先固定 Task 定义、RetryPolicy 与 logical JobSpec，再物化一组 Batch：

```text
Backfill(requestId)
      │
      ├── Scope A -> BatchKey(requestId + scopeFingerprint) -> PENDING Batch
      ├── Scope B -> BatchKey(requestId + scopeFingerprint) -> PENDING Batch
      └── Scope C -> BatchKey(requestId + scopeFingerprint) -> PENDING Batch

all batches share one ExecutionSnapshot
```

关键约束：

- 同一 `requestId + scopeFingerprint` 重放复用原 Batch，不重复物化；
- 同一个 Backfill Request 的 Batch 必须共享同一 Snapshot；
- Snapshot 现在包含 definition、DefinitionRevision、RetryPolicy、configDigest 和不含凭据的 logical JobSpec；
- PENDING Backfill 不占 V1 execution slot，可以先排队；
- dispatcher 只在 Task 没有 `RUNNING / WAITING_RETRY / UNKNOWN` Batch 时提交下一个；
- PENDING -> RUNNING 使用 CAS reservation，再创建 Attempt 1；
- Attempt 1 以及后续 Retry 都只读 Batch 冻结证据，不回读 current Task；
- Scope 在 Engine Adapter 边界投影为实际过滤条件，凭据随后才解析。

### Scoped execution V1

当前 V1 有意收窄为 JDBC 单表 source：

- `DataWindow`：使用冻结 JobSpec 的 `source.options.partition_column`；
- `PartitionScope`：使用同一 `partition_column`；
- `CursorRange`：使用 Cursor route 保存的 `sourceColumn`；
- 已有 `where_condition` 与 Scope 条件使用 AND 合并；
- multi-table scoped Backfill 和 custom source query + Scope 不做猜测，显式拒绝，后续需要先补 Domain Gap。

## 4. Wave 5 Cursor

Cursor 不属于 Attempt，也不把 cursorId 当成字段名。它单独持久化：

```text
OfflineSyncCursor
  ├── taskId
  ├── cursorId
  ├── sourceColumn      <- route
  ├── position
  ├── lastSucceededBatchId
  └── stateVersion
```

CursorRange Batch 使用：

```text
(cursorId, afterExclusive, throughInclusive)
```

推进规则固定为：

```text
Batch status == SUCCEEDED
AND current.position == afterExclusive
AND current.stateVersion == expectedVersion
        │
        ▼ CAS
position = throughInclusive
```

因此：

- FAILED / CANCELED / UNKNOWN Batch 永不推进 Cursor；
- Attempt SUCCEEDED 但 Batch 尚未形成 SUCCEEDED runtime truth 时不推进；
- 旧 Batch 晚到成功时，如果 Cursor 已离开 `afterExclusive`，结果为 STALE，不允许回退/跳跃；
- 同一个 cursorId 的 Backfill ranges 必须连续；dispatcher 只有在 Cursor 当前 position 等于该 Batch `afterExclusive` 时才会启动它。

## 5. Runtime Truth（Wave 4 保持不变）

```text
Task command
    │
    ▼
BatchExecution
    │ status
    ▼
latest ExecutionAttempt

Task last-* = projection only
```

BatchStatus：活动 Attempt -> RUNNING；FAILED + Retry 窗口 -> WAITING_RETRY；FAILED -> FAILED；UNKNOWN -> UNKNOWN；SUCCEEDED/CANCELED 对应同名终态。旧 Attempt 晚到事件不能回退 Batch，也不能覆盖 latest Task projection。

## 6. Persistence Migration

Wave 5 新增 V4 migration：

```text
yak_offline_batch_execution
  + logical_job_spec_json

yak_offline_sync_cursor
  + task/cursor unique identity
  + source_column
  + position_value
  + last_succeeded_batch_id
  + state_version
```

旧 Wave 2-4 Batch 的 `logical_job_spec_json` 从其 Attempt 1 `submitted_config` 回填。字段迁移期允许 NULL，但 repository 读取时如果旧行尚未回填，会退回读取 Attempt 1；若两者都不存在则拒绝猜测。

## 7. 当前仍未解决的 Domain Debt

### Legacy Attempt persistence 名称/结构

`yak_offline_job_execution` 已实际承担 Attempt persistence，但类名、表名和部分字段仍保留 execution-centered 结构。Wave 6 再做 contract/cleanup，不为了命名整洁推倒历史。

### Wave 1 前历史没有 Batch identity

`batch_id = NULL` 的旧 execution 不属于新的 Batch runtime truth，没有 BatchScope / frozen RetryPolicy / frozen logical JobSpec，不能通过 current Task 猜测补齐后 Retry。

### Attempt snapshot 兼容副本

`definition_snapshot_json / submitted_config / config_digest` 仍同时保留在 legacy Attempt persistence，用于兼容旧接口与历史。Batch Snapshot 已成为新运行链冻结证据，重复字段清理由 Wave 6 决定。

## 8. 迁移波次

```text
Wave 0  DONE  Core VO + compatibility mapper
Wave 1  DONE  Batch persistence + execution.bind(batch_id)
Wave 2  DONE  Trigger -> Batch -> Attempt 1 + Schedule BatchKey
Wave 3  DONE  Retry / UNKNOWN + durable retry reservation
Wave 4  DONE  Runtime truth -> Batch/Attempt; Task last-* projection only
Wave 5  DONE  Backfill group / BatchScope / Cursor success-only CAS
Wave 6  NEXT  Legacy cleanup
```

采用 `expand -> dual read/write -> switch -> verify -> contract`，不做一次性 schema 重建。

## 9. 当前结论

1. Batch 已承担业务身份、冻结执行证据与 runtime truth；Attempt 只记录每次实际提交证据。
2. Backfill 已成为 Batch group，而不是新的 Task 类型；PENDING queue 与实际执行解耦。
3. Retry 和延迟 dispatch 都不再回读 current Task 生成执行配置。
4. Cursor 已与 Attempt 生命周期分离，只接受 SUCCEEDED Batch 的有序 CAS 推进。
5. Stage 6 下一步只剩 Wave 6 legacy cleanup：删除/收缩兼容分支、旧命名与重复 snapshot 字段前，先验证新链路已经完全覆盖。
