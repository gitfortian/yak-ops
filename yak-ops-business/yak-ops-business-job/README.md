# yak-ops-business-job

`yak-ops-business-job` 是 Yak Ops 的通用 **Task Discovery + Snapshot + Execution Routing** 模块，主要服务 Workflow、Data Development 等上层运行入口。

它不拥有业务任务定义，也不负责 Cron / Schedule 生命周期。

核心关系：

```text
TaskDefinition (discovery descriptor)
        -> TaskVersionSnapshot
        -> TaskExecution
```

其中：

- `TaskProvider`：业务域向 Job 暴露可执行任务；
- `TaskRegistry`：聚合 Provider 并校验任务 ID 冲突；
- `TaskExecutionGateway`：按 Task Type 路由到 `TaskExecutor`；
- Plugin Task Executor：SQL / Python / Java / Shell 复用统一执行生命周期；
- SYNC：通过 Offline Sync 业务运行时执行，Job 不拥有其执行事实；
- `TaskEnvironmentResolver`：向运行上下文提供全局环境变量。

业务时间调度继续由各业务域自己管理，不在 Job 模块注册计划或维护调度状态。

架构约束：

- [REQUIREMENTS.md](REQUIREMENTS.md)
- [DOMAIN.md](DOMAIN.md)
- [ARCHITECTURE.md](ARCHITECTURE.md)

仓库级代码规范统一看 `../../CODE_STYLE.md`。
