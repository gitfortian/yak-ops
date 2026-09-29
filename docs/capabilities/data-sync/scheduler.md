# Data Sync Scheduler Contract

Status: v1.1 PR2 — Offline Schedule Persistence + Trigger

Depends On:

- [Data Sync Capability](./README.md)
- [Yak Ops v1.1.0 Release Contract](../../release/v1.1.0.md)
- [Yak Ops Architecture](../../../ARCHITECTURE.md)

## 1. Goal

v1.1 的 Scheduler 第一阶段只建立一个边界：

> Yak Ops 管 Task 和运行语义，Quartz 只管什么时候到点。

目标结构：

```text
Data Sync Business
        │
        ├── DataSyncScheduleDefinition
        ├── ScheduleEngine
        └── DataSyncScheduleFireListener
                 ↑
                 │ framework-neutral
                 │
Boot / Quartz ───┘
        │
        ├── QuartzScheduleEngine
        ├── QuartzDataSyncScheduleJob
        └── QuartzSpringBeanJobFactory
```

Quartz 类型不得进入 Data Sync Business Contract、Task DTO/VO、DAO Entity 或 YakFlow。

## 2. Schedule Engine Contract

`ScheduleEngine` 只负责：

- 注册 Cron。
- 修改 Cron。
- 移除 Cron。
- 查询下一次触发时间。

它不负责：

- Task Publish / Unpublish。
- 判断 Task 是否可以运行。
- 创建 Data Sync Instance。
- `SKIP_IF_RUNNING`。
- Retry / Attempt。
- Realtime desired state。
- Worker / 分布式资源调度。

这些都继续由 Data Sync 产品层负责。

## 3. Minimal Schedule Definition

传给调度引擎的定义只包含：

```text
scheduleId
workspaceId
taskId
cronExpression
timeZone
```

不允许包含：

- Task Definition JSON。
- Datasource Connection。
- Password / Token / SSH Key。
- Runtime Connection。
- Field Mapping。
- CDC Offset / Schema History。

Quartz JobData 当前也只保存：

```text
scheduleId
workspaceId
taskId
```

Quartz 是 Trigger Runtime，不是 Data Sync 业务数据库。

## 4. Cron / Time Zone

Cron 使用 Quartz Cron 语义。

Time Zone 必须显式进入 Schedule Definition，不能隐式依赖：

- JVM 默认时区。
- 宿主机时区。
- 浏览器时区。

因此相同 Cron + Time Zone 在不同部署环境中应保持相同调度语义。

## 5. Misfire

v1.1 第一阶段固定：

```text
MISFIRE = DO_NOTHING
```

例如：

```text
计划：02:00
应用：01:50 - 02:10 不可用
恢复：02:10
```

本次 02:00 不自动补跑。

原因：

- 避免恢复后突然补跑大批离线任务。
- 不把“补跑”偷偷混进 Scheduler Runtime。
- 后续如果需要 Catch-up，必须作为明确产品策略进入 Data Sync Contract。

## 6. Concurrency Ownership

Quartz 不拥有 Data Sync 的并发策略。

v1.1 目标策略：

```text
SKIP_IF_RUNNING
```

判断位置必须是：

```text
Quartz Fire
    ↓
Data Sync Business
    ↓
检查同 Task Active Instance
    ├── YES → SKIP
    └── NO  → 创建运行
```

不得用 Quartz `@DisallowConcurrentExecution` 代替 Data Sync 业务判断，因为后续 `QUEUE / PARALLEL` 也属于产品语义。

## 7. Retry Ownership

Quartz Fire 与 Retry 是两件事：

```text
Cron Fire
   ↓
Execution
   ↓
Attempt 1 FAILED
   ↓
Retry Policy
   ↓
Attempt 2
```

Quartz 只负责第一步。

Retry 不使用 Quartz refire 语义冒充 Data Sync Attempt。

## 8. Fire Boundary

Quartz 到点后转换为框架无关的：

```text
DataSyncScheduleFire
```

字段：

```text
scheduleId
workspaceId
taskId
scheduledFireTime
```

然后通过 `DataSyncScheduleFireListener` 交回 Business。

PR2 已由 `DataSyncServiceImpl` 实现该回调边界。

到点后必须重新读取数据库并校验：

```text
Schedule exists + enabled
        ↓
Task exists + OFFLINE + PUBLISHED
        ↓
Active Instance?
   ├── YES → SKIP
   └── NO  → create Instance(triggerType=SCHEDULE)
```

Quartz JobData 不是执行授权来源；真正执行前始终以 Yak Ops DB 当前状态为准。

## 9. Quartz Ownership

Quartz 依赖只放在 `yak-ops-boot`。

当前实现：

```text
ScheduleEngine
      ↑
QuartzScheduleEngine
```

`QuartzSpringBeanJobFactory` 只负责让 Quartz 创建的 Job 获得 Spring Bean，不承担业务规则。

`QuartzDataSyncScheduleJob` 只负责：

```text
Quartz JobData
   ↓
DataSyncScheduleFire
   ↓
DataSyncScheduleFireListener
```

不允许直接访问：

- DataSync Repository。
- Datasource Repository。
- YakFlow Runtime。
- LocalExecutionEngine。

## 10. Persistence Boundary

PR2 新增：

```text
V2__data_sync_schedule.sql
        ↓
yak_ops_data_sync_schedule
```

一条 Offline Task 在同一个 Workspace 内最多存在一个 Schedule。

持久化字段：

```text
id
workspace_id
task_id
cron_expression
time_zone
enabled
audit fields
```

新建 Schedule 默认 `enabled=false`；启用调度必须满足 Task = `OFFLINE + PUBLISHED`。

后续持久化必须遵守：

```text
yak_ops_data_sync_schedule
        =
Data Sync Schedule Source of Truth
```

Quartz 自身的 JobStore 只能作为 Scheduler Runtime State。

如果后续启用 Quartz JDBC JobStore：

- Quartz 表属于 Runtime Infrastructure。
- Schema 必须进入 Yak Ops Flyway Migration。
- 禁止通过 `spring.quartz.jdbc.initialize-schema=always` 在生产环境自动重建表。
- Data Sync 业务代码不得直接查询 `QRTZ_*`。

`V1__baseline.sql` 保持冻结；Schedule Schema 从 `V2__data_sync_schedule.sql` 开始演进。

## 11. PR2 Runtime Recovery

当前 Quartz 仍使用 RAMJobStore，因此 Quartz Trigger 本身不是持久化 Source of Truth。

应用启动后：

```text
Application Ready
      ↓
DataSyncService.restoreScheduleRuntime()
      ↓
query enabled schedules
      ↓
validate OFFLINE + PUBLISHED
      ↓
re-register Quartz Trigger
```

这保证应用重启后 Schedule Definition 不丢失，也不要求 PR2 提前引入 Quartz JDBC JobStore。

Task 下线时会在同一业务事务中把 Schedule 标记为 disabled，并在 commit 后移除 Quartz Trigger。

Task 删除时会同步删除 Schedule 业务记录，并在 commit 后清理 Quartz Runtime。

## 12. PR2 HTTP Contract

后端当前提供：

```text
PUT  /api/v1/data-sync/tasks/{id}/schedule
GET  /api/v1/data-sync/tasks/{id}/schedule
POST /api/v1/data-sync/tasks/{id}/schedule/enable
POST /api/v1/data-sync/tasks/{id}/schedule/disable
```

Save 只保存 Cron + Time Zone，新 Schedule 默认不启用。

## 13. PR2 Non-Goals

本 PR 明确不做：

- Schedule UI。
- 可配置的 Concurrency Policy。
- QUEUE / PARALLEL。
- Retry / Attempt。
- Quartz JDBC JobStore。
- Scheduler Cluster。
- Realtime Auto Recovery。

当前 Scheduled Fire 在发现 Active Instance 时固定跳过，作为单机调度安全语义；可配置并发策略属于后续 PR。

## 14. PR2 Verification

至少证明：

- Business Scheduler Contract 不依赖 Quartz。
- Quartz 依赖只进入 Boot。
- JobData 只有稳定 ID。
- Cron 使用显式 Time Zone。
- Misfire 固定为 DO_NOTHING。
- Reschedule 可以更新 Cron。
- Unschedule 幂等。
- 不修改 V1 Flyway baseline。
- V2 Schedule Schema / Entity / Repository 一致。
- 新 Schedule 默认 disabled。
- 只有 PUBLISHED OFFLINE Task 可以 enable。
- Task unpublish 自动 disable + unschedule。
- Scheduled Fire 创建 `triggerType=SCHEDULE` Instance。
- Active Instance 存在时 Scheduled Fire 不重复创建。
- 应用启动从 DB 恢复 enabled Schedule Runtime。
