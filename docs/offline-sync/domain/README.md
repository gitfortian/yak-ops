# Offline Sync Domain

> 当前实现阶段：**Stage 6 COMPLETE / Wave 6 DONE**。日常开发规则以模块 `DOMAIN.md` 为准；本文记录 Stage 6 从 legacy execution-centered 实现迁移到 Task / Batch / Attempt 模型的最终映射。

## 1. 最终模型

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

固定规则：`Task != Batch != Attempt`；Retry 复用原 Batch/Scope/Snapshot；`UNKNOWN != FAILED`；Batch runtime status 只由 latest Attempt 推导；Cursor 只由 SUCCEEDED Batch 推进。

## 2. 最终 Mapping

| 当前实现 | 最终语义 | 状态 |
| --- | --- | --- |
| `OfflineJobDefinition` | Task + current SyncDefinition + Schedule/Retry Policy + 查询投影 | CONTRACT |
| `definition_json` | current SyncDefinition | KEEP |
| `version` | `DefinitionRevision` | CONTRACT |
| `job_spec_json` | current logical JobSpec，不是已创建 Batch 的 runtime truth | CONTRACT |
| `release_state` | Task 是否允许产生新 Batch | KEEP |
| `OfflineSchedule` | SchedulePolicy + RetryPolicy + runtime projection | CONTRACT |
| `yak_offline_batch_execution` | Batch persistence + runtime truth | CONTRACT |
| `logical_job_spec_json` | Batch 冻结的不含凭据 logical JobSpec；唯一冻结 JobSpec truth | CONTRACT |
| Trigger claim | `Trigger -> Batch -> Attempt 1` | CONTRACT |
| Schedule identity | `scheduleId + plannedFireTime` BatchKey | CONTRACT |
| Retry Attempt | same Batch + frozen Snapshot/RetryPolicy/JobSpec | CONTRACT |
| `UNKNOWN` | 不确定结果，持续 reconcile，不自动 Retry | CONTRACT |
| legacy persisted `LOST` | read alias -> `UNKNOWN`；不再是领域枚举 | CONTRACT |
| Batch status dual-write | latest Attempt -> BatchStatus | CONTRACT |
| Task `last-*` | Read Model projection only | CONTRACT |
| Backfill request | 一次请求 -> 一组 PENDING BACKFILL Batch | CONTRACT |
| Backfill dispatcher | PENDING reservation -> Attempt 1，V1 单 Task 串行 | CONTRACT |
| `yak_offline_sync_cursor` | Task Cursor route + position + CAS version | CONTRACT |
| Cursor advancement | only SUCCEEDED CursorRange Batch | CONTRACT |
| `OfflineJobExecution` | ExecutionAttempt persistence compatibility view | CONTRACT |
| `yak_offline_job_execution.batch_id IS NULL` | Wave 1 前历史，只读 | CONTRACT |
| Attempt duplicate definition/config/submittedConfig | 历史审计/旧 schema 兼容副本 | CONTRACT |
| engine ref / metrics / error / start-end | Attempt 运行证据 | KEEP |
| `OfflineExecutionEvent` | Attempt 事件历史 | KEEP |
| Link-Up / credential resolver | Infrastructure boundary | KEEP |

## 3. Runtime Truth

```text
Task command
    │
    ▼
BatchExecution
    │ status
    ▼
latest ExecutionAttempt

Task last-*             = projection only
Attempt config copies   = audit compatibility only
batch_id = NULL history = query only
```

BatchStatus 推导保持：

```text
CREATED / SUBMITTED / QUEUED / RUNNING -> RUNNING
FAILED + nextRetryTime                 -> WAITING_RETRY
FAILED                                 -> FAILED
UNKNOWN                                -> UNKNOWN
SUCCEEDED                              -> SUCCEEDED
CANCELED                               -> CANCELED
```

Attempt 写入后刷新 Batch；刷新时重新读取同 Batch latest Attempt。旧 Attempt 晚到事件不能回退 Batch，也不能覆盖 Task latest projection。

## 4. Frozen Snapshot Contract

Wave 5 已把不含凭据的 logical JobSpec 冻结到 Batch：

```text
ExecutionSnapshot
├── definitionSnapshot
├── definitionRevision
├── RetryPolicySnapshot
├── configDigest
└── logicalJobSpec
```

Wave 6 完成 contract：

- Batch persistence 是 Snapshot 的唯一读取来源；
- `logical_job_spec_json` 缺失时不再 fallback 到 Attempt 1 `submitted_config`；
- Retry、延迟 Backfill Attempt 1、workflow 幂等重放全部验证/读取 Batch Snapshot；
- Attempt 上 `definition_snapshot_json / config_digest / submitted_config` 继续写入，只为旧 schema、历史 API 与审计兼容；
- 凭据仍只在 Attempt submit boundary 解析，不进入 Batch Snapshot。

V4 migration 已负责把 Wave 2-4 Batch 的 `logical_job_spec_json` 从当时的 Attempt 1 冻结副本一次性回填；**迁移完成后运行代码不再 dual-read**。

## 5. Backfill / Cursor

Backfill 不创建 Task 类型：

```text
Backfill(requestId)
      ├── Scope A -> PENDING Batch
      ├── Scope B -> PENDING Batch
      └── Scope C -> PENDING Batch

PENDING -> RUNNING CAS -> Attempt 1
```

同一 request + scope fingerprint 幂等复用，同组共享 Snapshot。当前 scoped execution V1 只支持可明确投影的 JDBC 单表 source；不清楚语义的 multi-table/custom-query Scope 显式拒绝。

Cursor 独立持久化 route、position、lastSucceededBatchId 与 stateVersion。推进固定为：

```text
Batch.status == SUCCEEDED
AND cursor.position == range.afterExclusive
AND cursor.stateVersion == expectedVersion
        │
        ▼ CAS
cursor.position = range.throughInclusive
```

FAILED / CANCELED / UNKNOWN 不推进；旧成功晚到不能回退或跨越 Cursor。

## 6. Wave 6 Legacy Contract

Wave 6 不重建历史表，而是把 legacy 能力收缩到明确边界。

### 删除的旧运行路径

`OfflineJobExecutionRepository / Dao` 不再暴露：

```text
hasActiveExecution(taskId)
bindBatch(executionId, batchId)
```

Task execution slot 只能由 Batch repository/runtime service 判断。新 Attempt 必须在创建时就绑定 Batch，不允许把 Wave 1 前历史 execution 事后绑定到猜测出的 Batch。

### batchless history

`batch_id = NULL` 的旧记录：

```text
查询 / 日志 / 历史展示   YES
Retry                    NO
Cancel                   NO
Reconcile                NO
Task last-* projection   NO
retroactive Batch bind   NO
```

active reconcile scan 现在只扫描 `batch_id IS NOT NULL` 的 Attempt。

### LOST contract

`LOST` 已从 `OfflineExecutionStatus` 领域枚举移除。读取历史值时：

```text
LOST -> UNKNOWN
```

V5 migration 同时把持久化的 execution/task projection `LOST` 归一为 `UNKNOWN`。这不是把 UNKNOWN 当 FAILED；自动 Retry 规则保持不变。

### compatibility mapper

`LegacyOfflineExecutionCompatibilityMapper` 现在只负责：

```text
legacy execution persistence -> ExecutionAttempt evidence
```

不再允许：

```text
legacy Attempt copy -> ExecutionSnapshot
```

Snapshot 只能从 Batch persistence 取得。

## 7. Persistence Migration

Stage 6 相关迁移最终为：

```text
V2  Batch identity + Attempt.batch_id
V3  Batch runtime status backfill
V4  Batch logical_job_spec_json + Cursor persistence
V5  legacy LOST -> UNKNOWN contract normalization
```

没有新增物理 FK，也没有强制把 Wave 1 前历史补成虚假的 Batch。

当前仍保留的物理兼容：

- 表名 `yak_offline_job_execution`；
- 类名 `OfflineJobExecution`；
- Attempt 上重复的 definition/config/submittedConfig 字段；
- Wave 1 前 nullable `batch_id` 历史。

这些已经不再影响 runtime semantics。未来如需删列/改名，应单独做 contract migration，而不是混入业务功能 PR。

## 8. Stage 6 波次

```text
Wave 0  DONE  Core VO + compatibility mapper
Wave 1  DONE  Batch persistence + execution.bind(batch_id)
Wave 2  DONE  Trigger -> Batch -> Attempt 1 + Schedule BatchKey
Wave 3  DONE  Retry / UNKNOWN + durable retry reservation
Wave 4  DONE  Runtime truth -> Batch/Attempt; Task last-* projection only
Wave 5  DONE  Backfill group / BatchScope / Cursor success-only CAS
Wave 6  DONE  Legacy runtime cleanup + compatibility contract

Stage 6 COMPLETE
```

迁移采用 `expand -> dual read/write -> switch -> verify -> contract`。Wave 6 是 contract 阶段：删除旧运行入口，而不是再扩领域模型。

## 9. 最终结论

1. Batch 已承担业务身份、冻结执行证据和 runtime truth；Attempt 只承担一次实际提交与运行证据。
2. Task `last-*`、Attempt snapshot compatibility copies、Engine JobId 都不能反向成为运行真相。
3. Retry、Backfill、Cursor、UNKNOWN 已全部进入 Batch/Attempt 规则。
4. Wave 1 前 batchless execution 被明确隔离为只读历史，不再污染新运行链。
5. Stage 6 到此收口；后续功能开发应直接遵守 `DOMAIN.md`，而不是继续叠加 legacy compatibility path。
