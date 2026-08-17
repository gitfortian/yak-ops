# Yak Ops 调度接入规范

Yak Ops 业务模块统一通过 Yak Framework Schedule 接入时间调度。本文定义业务调度的职责边界、生命周期、依赖规则和新增模块的标准接入方式。

## 1. 核心原则

调度系统只回答一个问题：**什么时候触发**。

业务模块继续负责：**执行什么、是否允许执行、如何执行、执行结果是什么**。

```text
Business Schedule / Definition   <- 业务事实来源
              |
              v
      XxxScheduleEngineBridge    <- 业务对象 -> ScheduleDefinition
              |
              v
        YakScheduleGateway       <- Yak Ops 统一引擎访问层
              |
              v
          ScheduleManager
              |
              v
       Schedule Provider         <- Quartz / XXL-JOB / ...
              |
              v
       XxxScheduleHandler
              |
              v
        Business Execution
```

业务表始终是事实来源。Yak Schedule 不替代业务定义表，也不引入一个新的通用业务任务表。

## 2. 职责边界

### Yak Schedule 负责

- Cron / One-Time 等时间触发
- Provider 路由
- pause / resume / delete / runNow
- 调度器级并发策略
- Misfire 策略
- 调度入口日志与操作审计
- `ScheduleSnapshot` 中的运行时调度状态

### 业务模块负责

- 业务定义及启停状态
- 执行实例和执行状态机
- 幂等、资源锁、业务并发准入
- 业务失败后的重试语义
- 业务运行日志、结果、告警
- 领域特有的恢复机制

例如：Workflow 的 Trigger Ledger、Offline Sync 的 Link-Up 对账与失败重试，都属于业务执行生命周期，不应塞进 Yak Schedule。

## 3. 标准组件

任何需要时间调度的业务模块，优先使用下面四个组件。

### `XxxScheduleEngineBridge`

只做两件事：

1. 把业务对象映射成 `ScheduleDefinition`；
2. 通过 `YakScheduleGateway` 操作当前 namespace 下的计划。

Bridge 不直接依赖 Quartz、XXL-JOB，也不维护业务执行状态。

### `XxxScheduleHandler`

实现 `ScheduleHandler`，作为所有时间触发的统一业务入口。

正常 Cron、`runNow`、启动后的 missed-trigger 恢复，都应尽量进入同一个 Handler，再由 Handler 调用业务执行 Service。

### `XxxScheduleLifecycle`

负责业务状态与调度引擎状态同步，并遵循统一语义：

| 业务动作 | Yak Schedule 动作 | 业务运行时状态 |
| --- | --- | --- |
| 保存 / 上线 | `save` | 回写 snapshot 的 `nextFireTime` |
| 下线 / 停用 | `pause` | 清空 `nextFireTime` |
| 删除 | `delete` | 清理业务调度运行时状态 |
| 立即执行 | `runNow` | 仍从 Handler 进入业务执行 |

业务删除与业务停用必须区分：**停用是 pause，删除才是 delete**。

### `XxxScheduleReconciler`

应用启动后，以业务表为事实来源完成恢复：

1. 加载业务侧有效计划；
2. 清理 namespace 下已经失效的引擎计划；
3. 对有效计划重新 `save`；
4. 从 `ScheduleSnapshot` 同步运行时状态；
5. 按业务策略恢复持久化的 missed trigger；
6. 恢复业务特有的等待队列、Trigger Ledger 等状态。

当前 Yak Schedule 可以使用内存型 Provider 状态，因此启动对账是业务可恢复性的必要组成部分。

## 4. `nextFireTime` 规则

`nextFireTime` 的运行时事实来源必须是 Yak Schedule 的 `ScheduleSnapshot`。

业务模块可以保存 Cron 和前端友好的调度配置，但不应再维护另一套运行时“下一次执行时间”计算器，也不应通过扫描 `nextFireTime <= now` 来驱动业务任务。

允许业务代码使用 Cron 工具做：

- 输入校验；
- DAILY / WEEKLY 等友好配置转 Cron；
- Provider 兼容格式规范化。

不允许业务代码使用 Cron 工具重新实现调度时钟。

## 5. 依赖规则

业务模块只允许依赖：

```xml
<dependency>
  <groupId>io.yak.framework</groupId>
  <artifactId>yak-schedule-api</artifactId>
</dependency>
```

Provider 只能由 Boot / 组装层引入，例如：

```text
yak-ops-boot
  -> yak-schedule-core
  -> yak-schedule-plugin-quartz
```

业务模块禁止直接依赖：

- `org.quartz-scheduler:quartz`
- XXL-JOB Provider SDK
- 其他具体调度 Provider

这样 Provider 替换不会影响业务模块。

## 6. `@Scheduled` 使用边界

业务任务的 Cron 时间触发禁止使用 Spring `@Scheduled` 自建轮询调度器。

以下场景可以保留独立短周期 `@Scheduled`：

- 外部执行引擎状态对账；
- 运行实例心跳 / 超时回收；
- 与“任务什么时候触发”无关的业务执行生命周期维护。

例如 Offline Sync 的 `OfflineExecutionReconciler` 负责 Link-Up 运行状态对账和业务失败重试，它不是任务 Cron Scheduler，因此可以保留。

## 7. 当前实现

| 业务域 | Namespace | Handler | 业务事实来源 |
| --- | --- | --- | --- |
| Workflow | `yak-ops-workflow` | `workflowScheduleHandler` | `yak_workflow_schedule` |
| Data Quality | `yak-ops-quality` | `qualityScheduleHandler` | 质量 Monitor / Settings |
| Offline Sync | `yak-ops-offline-sync` | `offlineSyncScheduleHandler` | `yak_offline_job_definition` |

三者都通过 `YakScheduleGateway -> ScheduleManager` 访问调度引擎，同时保留自己的执行模型和恢复逻辑。

跨业务的只读调度视图（例如首页“调度中心”）应直接聚合这些 namespace 下的 `ScheduleSnapshot`，使用 `ScheduleDefinition.trigger` 中已经归一化的触发配置，不应再次查询各业务表并自行兼容 Linux Cron / Quartz Cron 或重新拼装 DAILY / WEEKLY Cron。

## 8. 新模块接入检查表

新增 SQL Task、数据集刷新、报表刷新等调度能力时，提交前确认：

- [ ] 业务表是调度定义事实来源；
- [ ] 业务模块只依赖 `yak-schedule-api`；
- [ ] 使用独立 namespace，并优先在 `YakScheduleNamespaces` 中登记稳定 namespace；
- [ ] 有 `EngineBridge + Handler + Lifecycle + Reconciler`；
- [ ] 下线使用 pause，删除使用 delete；
- [ ] `runNow` 和 missed trigger 进入统一 Handler；
- [ ] `nextFireTime` 来自 `ScheduleSnapshot`；
- [ ] 没有 `@Scheduled` 扫描业务任务到期时间；
- [ ] 没有直接依赖 Quartz / XXL-JOB；
- [ ] 业务并发、重试、日志和告警仍保留在业务执行层。
