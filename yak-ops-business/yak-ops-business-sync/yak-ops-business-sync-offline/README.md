# Yak Ops Offline Sync

## 领域建设

离线同步当前已完成 Stage 6 Wave 1：Batch persistence + nullable `execution.batch_id`。现有 Trigger/Retry/Schedule 仍走 legacy 链路；修改代码前先读当前规则，需要迁移背景时再看 Mapping 文档：

- [DOMAIN.md](./DOMAIN.md)
- [Domain Mapping](../../../docs/offline-sync/domain/README.md)

离线同步一期只承担三件事：任务配置、Link-Up 执行代理、执行历史。

```text
Yak Ops -> GET /api/v1/node
Yak Ops -> POST /api/v1/jobs
Yak Ops -> GET /api/v1/jobs/{jobId}
Yak Ops -> DELETE /api/v1/jobs/{jobId}
```

Link-Up 地址统一来自：

```yaml
yak:
  sync:
    offline:
      engine:
        base-url: http://127.0.0.1:18080
```

不再提供客户端管理、Connector 管理、多 Worker 调度、能力匹配、Preflight、动态注册和告警投递。

## 调度边界

任务级 Cron 时间触发统一交给 Yak Framework Schedule：

```text
Offline Job Definition
  -> OfflineScheduleEngineBridge
  -> Yak Schedule / Quartz
  -> OfflineScheduleHandler
  -> OfflineExecutionOrchestrator
  -> Link-Up
```

`yak_offline_job_definition` 仍是调度配置的事实来源；Yak Schedule 只负责“什么时候触发”。应用启动后会根据业务表恢复计划，并处理一次持久化的 missed trigger。

Link-Up 运行状态对账和业务失败重试仍由 `OfflineExecutionReconciler` 负责。这属于执行生命周期，不属于任务 Cron 调度，因此继续保留独立的短周期对账。

## 工程分层

离线同步遵循 Yak Ops 统一业务模块边界：

```text
Controller -> DTO -> Service -> Domain
           -> Repository -> Adapter -> DAO
           -> BaseMapper / Mapper XML -> PO -> MySQL

Domain -> View Mapper -> VO -> Controller
```

约束：

- Controller 只通过 Service 进入业务链路，不直接依赖 Repository、DAO 或 Link-Up Client。
- Service 和执行状态机使用 Domain，不直接操作 MyBatis PO。
- Repository 接口只暴露 Domain；PO 与 DAO 仅存在于持久化适配层。
- 分页遵循 `DAO IPage<PO> -> Adapter PageData<Domain> -> Service PagingData<VO>`；业务模块之间也直接传递 `PageData<Domain>`，不再使用 `OfflinePage`。
- HTTP 分页继续保持 `bizData + pagination`，第一阶段不要求前端迁移。
- DAO 不接收 HTTP DTO；分页筛选使用 DAO 自己的查询条件。
- 普通单表操作使用 MyBatis-Plus，只有行锁等数据库原子语义进入 Mapper XML。
- Link-Up 协议对象只存在于 engine/service 内部，不作为 HTTP 响应模型直接暴露。

## 数据表

当前保留：

- `yak_offline_job_definition`
- `yak_offline_batch_execution`
- `yak_offline_job_execution`
- `yak_offline_execution_event`

Wave 1 通过 V2 expand migration 新增 Batch 表，并给 `yak_offline_job_execution` 增加 nullable `batch_id`。旧 execution 可以暂时没有 Batch；Wave 2 才会让新 Trigger 先创建 Batch 再创建 Attempt。

调度配置继续保存在任务定义表。Batch 保存稳定业务身份、Scope 和 frozen Snapshot/RetryPolicy；execution 继续保存引擎提交、指标、错误等 Attempt 证据。

## 数据库迁移

Flyway 使用 `yak_offline_core_schema_history`：

- V1 是离线同步 baseline；
- V2 只做 expand：新增 `yak_offline_batch_execution` 和 nullable `execution.batch_id`；
- Wave 1 不回填旧 execution，不创建物理 FK，也不切换现有执行链。
