# yak-ops-business-job

Yak Ops 的通用任务发现与执行路由模块，主要服务于 Workflow 等上层能力。

## 职责边界

本模块负责：

- 统一任务发现与 `TaskRegistry`；
- SQL / Python / Sync 等任务执行适配；
- Workflow 运行期间对任务执行状态的查询、取消和结果转换；
- `SYNC` 任务通过 `OfflineJobExecutionService` 复用离线同步业务执行能力。

本模块 **不负责业务任务的时间调度**，也不注册 Yak Schedule 计划。

## 调度归属

Yak Ops 的业务时间调度已经统一下沉到各业务域：

```text
Business definition
      -> XxxScheduleEngineBridge
      -> YakScheduleGateway
      -> Yak Schedule
      -> XxxScheduleHandler
      -> Business execution
```

当前：

- Offline Sync：由 `yak-ops-business-sync-offline` 的 `OfflineScheduleEngineBridge / Lifecycle / Reconciler / Handler` 负责；
- Workflow：由 Workflow 模块自己的调度生命周期负责；
- Data Quality：由 Quality 模块自己的调度生命周期负责。

因此 `yak-ops-business-job` 不应再维护独立的 `JobScheduleRegistrar`、周期扫描器或同名业务 `ScheduleHandler`。

## Offline Sync 依赖说明

本模块仍依赖 `yak-ops-business-sync-offline`，是因为 Workflow 的 `SYNC` Task Runner 会调用 `OfflineJobExecutionService` 执行业务任务。

这个依赖仅用于 **执行路由**，不用于 Cron 注册、计划同步或调度生命周期管理。
