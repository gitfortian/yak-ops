# Data Sync Execution Retry / Attempt Contract

Status: Active

Scope: Execution / Attempt 身份、状态聚合、Retry、取消、指标和持久化兼容性。

## Identity

```text
Task
 ├─ Execution E1（现有 DataSyncInstance）
 │    ├─ Attempt 1 FAILED
 │    └─ Attempt 2 SUCCEEDED
 └─ Execution E2
      └─ Attempt 1 RUNNING
```

一次手动运行、有效 Cron Fire 或启动自动恢复创建一个 Execution。Retry 只在该 Execution 内创建 attemptNo+1，不创建新的根记录，不复活旧 Attempt。最终失败后用户再次运行是新 Execution。

Execution 的 workspaceId、taskId、taskName 快照、taskVersion、syncType、root trigger、definitionSnapshot 和 Retry Policy 在创建时固定。所有 Attempt 使用同一冻结输入，不读取最新 Task 定义。

Attempt 拥有自身状态、指标、时间与脱敏错误。`(executionId, attemptNo)` 唯一，attemptNo 从 1 递增；不允许跨 Execution 移动或让终态 Attempt 回到 RUNNING。

## Trigger Ownership

新 Execution 的根触发来源为 MANUAL、SCHEDULE 或 AUTO_RECOVERY。RETRY 枚举存储值仅保留历史兼容，不是新 Execution 的正常创建来源。

例如 SCHEDULE / AUTO_RECOVERY Execution 的第二个 Attempt 仍保留原根 trigger。自动恢复与同根 Retry 的区别见 [Realtime Desired State](realtime-desired-state.md)。

## Execution Status

| 层级 | 活动状态 | 终态 |
| --- | --- | --- |
| Execution | PENDING、RUNNING、RETRY_WAITING | SUCCEEDED、FAILED、CANCELED、LOST |
| Attempt | PENDING、RUNNING | SUCCEEDED、FAILED、CANCELED、LOST |

RETRY_WAITING 属于 Execution，不属于已经 FAILED 的 Attempt。等待 backoff 仍占用该 Task 的活动执行位置，调度跳过、禁止下线 / 删除和取消入口必须覆盖它。

```text
PENDING → Attempt RUNNING
             ├─ 成功 → Execution SUCCEEDED
             ├─ 失败且可重试 → RETRY_WAITING → 下一 Attempt
             ├─ 失败且耗尽次数 → Execution FAILED
             └─ 用户取消 → Execution CANCELED

进程所有权丢失 → LOST
```

Execution 进入终态后不能自动追加 Attempt。连续 REALTIME Source 意外自然结束按失败处理，不把暂时无数据或异常结束当成功。

## Retry Policy

`maxAttempts` 包含首次执行；默认 1 表示不自动重试，3 表示最多两次 Retry。`backoffSeconds` 是固定等待时间，默认 60 秒。请求范围与默认值由 DataSyncRetryPolicyDTO 维护。

只有 FAILED 尝试进入通用 Retry 决策，SUCCEEDED / CANCELED / LOST 不自动重试。OFFLINE 与 REALTIME 共用生命周期；REALTIME Retry 复用既有 task/version CDC state。

Backoff 使用进程内等待，不使用 Quartz，不是跨进程 durable timer。进程在 RETRY_WAITING 时退出，下次启动将旧根记录标记 LOST，不继续旧 Attempt 序号；需要恢复的 REALTIME Task 由独立 desired-state 协调创建新根记录。

## Cancel Semantics

取消对象是整个 Execution，不是“跳过当前 Attempt 后继续 Retry”。PENDING / RETRY_WAITING 可以取消，后续等待不得再启动新 Attempt；RUNNING 通过本地注册表取消 Runtime，再收口 Execution / 当前活动 Attempt。

若 RUNNING Execution 的本地 Runtime 引用已经丢失，当前取消路径将其标记 LOST，而不是假报成功取消。对已终态 Execution 重复取消只返回历史记录；不会复活、追加尝试，也不会借此修改当前 Task 的运行意图。

## Definition Snapshot

快照保存稳定资源引用、任务类型 / 版本、表范围、写入方式和对应配置，不保存真实连接 JSON、密码、Token、SSH 私钥、CDC offsets、状态路径或 serverId 租约。

每次 Attempt 执行时按快照中的 datasource ID 安全解析当前连接并校验 Catalog。冻结 Task 输入不等于冻结外部数据、表结构或 Datasource 连接；不能据此承诺重试幂等或数据源变更安全。

## Metrics Semantics

- 单个 Runtime / Attempt 内计数保持单调；readRows 统计进入 Channel 的事件，writeRows 统计 SinkWriter.write 成功返回的事件，不是事务提交证明。
- Execution 只镜像当前或最终 Attempt 的计数。新 Attempt 开始时 readRows / writeRows 归零，因此同一个 Execution 跨 Attempt 可以下降。
- 不把多个 Attempt 相加为业务同步量；失败前可能已提交部分数据，重试可能重新读取或写入。
- REALTIME 计数是变更事件；一次 UPDATE 可以贡献前后两个事件，不直接等于 Source 表行数。

指标轮询和终态 flush 不应更改上述身份边界。详情通过 Attempt History 观察每次尝试，不伪造 checkpoint 时间或全局业务总量。

## Write Safety

Retry 不改变 [OFFLINE 写入方式](README.md#offline-execution) 或 [YakFlow 写入语义](../yak-flow/README.md#jdbc-batch-connector)。APPEND 重放可能重复写；OVERWRITE 再次尝试会重新执行破坏性清空；UPSERT / CHANGELOG 的主键应用不构成端到端 exactly-once。

默认不开自动重试，是兼容性和风险边界，不得在整理文档时改成默认多次执行。

## Persistence and Compatibility

[现有 V3 migration](../../../yak-ops-dao/src/main/resources/db/migration/yak-ops/V3__data_sync_execution_attempt.sql) 保存 Task Retry Policy、Execution 的冻结策略 / 当前尝试 / 下次重试时间，以及 `yak_ops_data_sync_attempt`。

`yak_ops_data_sync_instance` 和既有 Instance ID 保持不变。V3 前的记录按单次执行解释；迁移没有为每条历史 Instance 回填实体 Attempt 行，所以历史 attempts 查询可以为空，不应伪造历史尝试。

旧 Task 默认回填 maxAttempts=1、backoffSeconds=60。Schema 由 DAO 维护，遵守 [Flyway Rules](../../../yak-ops-dao/FLYWAY_RULES.md)，不修改已冻结迁移或建立第二套 Task / Instance 模型。

## Operations Contract

列表一行对应一个 Execution，展示当前 / 最终 Attempt 信息；详情读取 `GET /api/v1/data-sync/instances/{id}/attempts` 获取尝试历史。Retry Policy 的请求契约由后端 DTO 维护，页面如何配置由前端 owner 负责；Attempt 观察与策略编辑是不同职责。

## Current Limits

不提供指数退避、jitter、跨进程重试定时器、分布式 Attempt ownership 或 exactly-once。通用 Retry 不恢复 LOST；启动自动恢复有自己的新 Execution 身份和规则。

## Code and Verification

状态持久化：[DataSyncAttemptLifecycle](../../../yak-ops-business/yak-ops-business-data-sync/src/main/java/io/yak/ops/business/datasync/execution/lifecycle/DataSyncAttemptLifecycle.java) 与 [DataSyncInstanceRepositoryImpl](../../../yak-ops-dao/src/main/java/io/yak/ops/dao/repository/datasync/impl/DataSyncInstanceRepositoryImpl.java)。

验证入口：[DataSyncAutomationAcceptanceIT](../../../yak-ops-business/yak-ops-business-data-sync/src/test/java/io/yak/ops/business/datasync/impl/DataSyncAutomationAcceptanceIT.java) 及 [验证导航](README.md#code-and-verification)。重点是根身份 / 快照不变、次数与 backoff、取消阻断、活动集合及跨 Attempt 指标语义；执行结果绑定实际提交，不在这里记通过流水。
