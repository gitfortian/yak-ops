# v1.1 Automation & Recovery 验收

状态：启用

## 目标

本目录收口 Yak Ops v1.1 Automation & Recovery 的手工 E2E。

自动化 CI 与人工 E2E 分工如下：

```text
Automation Acceptance
  → 产品状态机 / 边界 / 幂等

JDBC / CDC Cross-Database Acceptance
  → 真实数据库与 Connector 行为

Manual E2E
  → 用户可见完整产品流程与运维观察
```

任何一层都不能单独替代另外两层。

## v1.1 验收证据矩阵

| v1.1 Required Acceptance | 自动化证据 | 手工证据 |
| --- | --- | --- |
| Cron 到点触发一次 Offline Execution | `QuartzScheduleEngineTest` + `DataSyncAutomationAcceptanceIT` | [AUTOMATION-001](01-offline-cron-disable.md) |
| Disable Schedule 后不再触发 | `DataSyncAutomationAcceptanceIT` | [AUTOMATION-001](01-offline-cron-disable.md) |
| Active Task 命中 `SKIP_IF_RUNNING` 不创建并发 Execution | `DataSyncAutomationAcceptanceIT` | 不额外制造慢任务；以 CI 为准 |
| Retry Policy 按 `maxAttempts / backoffSeconds` 生效 | `DataSyncAutomationAcceptanceIT` | 以 CI 为发布硬证据 |
| Stop / Cancel 不被自动 Retry | `DataSyncAutomationAcceptanceIT` | AUTOMATION-002 最后验证 Stop 后不 Auto Recovery |
| Realtime `desiredState=RUNNING` 重启后创建新 Execution | `DataSyncAutomationAcceptanceIT` | [AUTOMATION-002](02-realtime-process-restart-auto-recovery.md) |
| Auto Recovery 复用同 `definitionVersion` CDC state identity | `DataSyncAutomationAcceptanceIT` + `RealtimeSyncStateNamespaceTest` | [AUTOMATION-002](02-realtime-process-restart-auto-recovery.md) |
| Auto Recovery 不退化为 fresh snapshot | `MySqlCdcIntegrationIT` 的 state reuse / offset continuation | [AUTOMATION-002](02-realtime-process-restart-auto-recovery.md) |

## 必选 Manual E2E

v1.1 Release 前至少完整执行：

| ID | 场景 |
| --- | --- |
| AUTOMATION-001 | [离线 Cron 调度与停用](01-offline-cron-disable.md) |
| AUTOMATION-002 | [实时同步进程重启自动恢复](02-realtime-process-restart-auto-recovery.md) |

执行结果应进入 v1.1 Release Readiness / Evidence。

## 配置 UI 边界

v1.1 PR6 的 Operations Center 负责自动化运行态观察，不负责 Definition 编辑。

因此当前版本：

- Realtime Start / Stop 使用 UI。
- Automation Runtime / Attempt / Auto Recovery 使用 UI 观察。
- Offline Schedule 配置尚无 UI。

当某个手工 E2E 的前置配置没有产品 UI 时，可以使用**已经对外发布的 Data Sync HTTP Endpoint**完成该前置配置，但必须满足：

- 用例明确标注该步骤为何使用 HTTP。
- 不调用 Java Service、Repository 或数据库内部表。
- 不直接修改 `yak_ops_data_sync_*`。
- 执行、运行态观察、停止、实例详情仍优先使用 Yak Ops UI。
- 业务结果仍通过真实 Source / Target 数据库验证。

这是一条受限的验收准备规则，不代表 Operations Center 接管 Schedule Definition 编辑。

## 自动化门禁

Backend Acceptance 增加：

```text
Data Sync Automation Acceptance
```

执行：

```text
DataSyncAutomationAcceptanceIT
QuartzScheduleEngineTest
```

该 Job 与 JDBC / CDC Acceptance 独立显示结果。

Backend Acceptance Gate 要求：

```text
JDBC Acceptance              success / skipped
CDC Acceptance               success / skipped
Automation Acceptance        success / skipped
```

任何实际执行的 Acceptance Job 失败，Gate 都必须失败。
