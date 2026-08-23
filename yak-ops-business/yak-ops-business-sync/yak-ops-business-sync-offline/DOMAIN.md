# Offline Sync Domain

> 本文件是离线同步模块的当前领域约束。设计过程与迁移分析见 `docs/offline-sync/domain/README.md`。

## Mission

离线同步负责：定义一项离线同步任务，并把手动、调度、工作流或补数触发转换成可追踪、可重试、可取消的业务批次。

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

核心关系：`Task != Batch != Attempt`。

## Hard Rules

1. `OfflineSyncTask` 是长期配置；`BatchExecution` 是一次业务批次；`ExecutionAttempt` 是一次实际提交。
2. Retry 创建新 Attempt，不创建新 Batch。
3. Batch 创建时冻结 `ExecutionSnapshot`；Retry 不得回读 current Task 改变 Snapshot。
4. Retry 必须保持同一个 Batch、BatchScope、ExecutionSnapshot 和 RetryPolicy Snapshot。
5. `UNKNOWN != FAILED`；结果不确定时必须先 reconcile，禁止盲目 Retry。
6. Batch 终态后不再追加 Attempt；再次运行同一数据范围应创建新 Batch。
7. V1 同一个 Task 最多一个 `RUNNING / WAITING_RETRY / UNKNOWN` Batch；并发需求变化必须先定义 `ConcurrencyPolicy`。
8. 每个 Batch 必须有稳定 `BatchKey`；Schedule 使用 `scheduleId + plannedFireTime`，不能使用实际回调时间。
9. `BatchScope` 描述数据范围；不得用 `sceneType/syncType` 代替 Scope / Route / Policy。
10. Backfill 创建一组 Batch，不创建新的 Task 类型；同一次 Backfill 优先共享同一 Snapshot。
11. Cursor 只允许在 Batch `SUCCEEDED` 后推进；`FAILED / CANCELED / UNKNOWN` 不得推进。
12. Task `last-*` 只能作为查询投影；运行真相只能来自 Batch / Attempt。
13. `DefinitionRevision` 只是修订标识，不等于 immutable `DefinitionVersion`。
14. Link-Up Job / Worker / Quartz / HTTP DTO / DataSource credential 都不是 Core Domain 对象。
15. SyncDefinition / Snapshot 不长期保存密码；凭据只在 Attempt 提交边界解析。
16. 实时同步和离线同步保持独立 Core；不得因为名字相似提前抽 Shared Sync Kernel。

## Domain Impact Analysis

修改离线同步代码前，必须先回答：

1. 变化属于 Task、Batch、Attempt、Scope、Snapshot 还是 Policy？
2. 是否改变 Batch / Attempt 生命周期？
3. 是否改变 Retry、UNKNOWN、Cancel 或并发语义？
4. 是否改变 BatchKey / 幂等身份？
5. 是否让 Retry 回读 current Task 或 current SchedulePolicy？
6. 是否把 Task `last-*`、Engine 状态或外部 JobId 当成领域真相？
7. 是否引入新的 `sceneType/syncType`、技术类型或凭据泄漏？
8. 是否能映射现有模型？不能映射则记录 `Domain Gap`，先设计再编码。

## Current Transition

当前代码尚未完全满足上述目标模型。Wave 2 / Wave 3 已解决 Trigger、Schedule BatchKey、Retry 漂移与 UNKNOWN 自动重试问题；仍保留的主要 Domain Debt：

```text
OfflineJobExecution = legacy 运行链仍以 execution 为中心
Batch status         = 已持久化，但尚未成为完整 runtime truth
Task last-*          = 仍部分参与生命周期判断
Legacy history       = Wave 1 前 execution 允许没有 batch_id，不可安全 Retry
```

Wave 2 已切成 `Trigger -> Batch -> Attempt 1`，Schedule BatchKey 固定为 `scheduleId + plannedFireTime`。Wave 3 已把 Retry 切成原 Batch 内新 Attempt：从 Batch 读取冻结的 Snapshot / RetryPolicy，从 Attempt 1 读取过渡期冻结 JobSpec；`retry_created` 通过 CAS reservation 与下一 Attempt 创建处于同一事务。`LOST` 仅作为旧数据兼容输入并统一解释为 `UNKNOWN`，UNKNOWN 继续 reconcile，不进入自动 Retry。

```text
Wave 0  DONE  Core VO + compatibility mapper
Wave 1  DONE  Batch persistence + execution.bind(batch_id)
Wave 2  DONE  Trigger -> Batch -> Attempt 1 + Schedule BatchKey
Wave 3  DONE  Retry / UNKNOWN + durable retry reservation
Wave 4  NEXT  Runtime truth -> Batch/Attempt; Task last-* projection only
Wave 5        Backfill / Cursor
Wave 6        Legacy cleanup
```

迁移原则：`expand -> dual read/write -> switch -> verify -> contract`。

## Forbidden Shortcuts

禁止：

- 把 Retry 实现成一次新的普通 execute；
- 把 UNKNOWN/LOST 直接当 FAILED 自动重试；
- 用 `hasActiveExecution()` 代替 Batch 级并发和 reservation；
- 用 actual callback time 作为 Schedule Batch 身份；
- 让 Attempt 自己重新生成 Snapshot；
- 用 Task `lastJobStatus` 决定真实运行状态；
- 把 Link-Up JobSpec、Worker、Connector、Quartz 类型放进 Core Domain；
- 为“全量/增量/单表/多表/补数”继续增加任务类型枚举；
- 为了和 realtime 一致而提前增加 immutable DefinitionVersion 或 Shared Sync Kernel。

如果需求无法在本模型内表达：`Domain Gap -> STOP -> 先补领域设计`。
