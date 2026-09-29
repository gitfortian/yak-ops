# Data Sync Scheduler Contract

Status: v1.1 PR1 — Contract + Quartz Boundary

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

PR1 只建立该回调边界，不提供业务 Listener 实现，因此本 PR 不创建 Instance、不执行 Task。

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

PR1 不新增数据库表，不修改 Flyway。

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

`V1__baseline.sql` 已冻结，任何相关数据库变化必须从新的 Migration 开始。

## 11. PR1 Non-Goals

本 PR 明确不做：

- Schedule Entity / Repository。
- Schedule CRUD API。
- Schedule UI。
- 启用 / 禁用。
- Publish / Unpublish 联动。
- Cron 到点创建 Instance。
- `SKIP_IF_RUNNING` 实现。
- Retry / Attempt。
- Quartz JDBC JobStore。
- Scheduler Cluster。
- Realtime Auto Recovery。

## 12. PR1 Verification

至少证明：

- Business Scheduler Contract 不依赖 Quartz。
- Quartz 依赖只进入 Boot。
- JobData 只有稳定 ID。
- Cron 使用显式 Time Zone。
- Misfire 固定为 DO_NOTHING。
- Reschedule 可以更新 Cron。
- Unschedule 幂等。
- 不修改 V1 Flyway baseline。
