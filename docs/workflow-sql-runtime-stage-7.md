# Unified Task Runtime · Stage 7

## 目标

Stage 7 将 Manual Run、Workflow Run 和后续 Schedule Run 收敛到同一个 `TaskExecutionGateway`，由 Runtime 根据任务类型路由到具体 `TaskExecutor`，再进入对应 `TaskPlugin`。

```text
Manual Run   ─┐
Workflow Run ─┼─> TaskExecutionGateway
Schedule Run ─┘          |
                         v
                   TaskExecutor
                         |
                         v
                     TaskPlugin
```

本阶段先完成 SQL 任务的统一入口，不扩展 Shell / Python，也不改变现有前端手动运行接口。

## 统一触发来源

Runtime 的启动入口现在显式携带 `TaskExecutionTrigger`：

- `MANUAL`：Data Development 编辑器手动运行；
- `WORKFLOW`：Workflow 节点运行，旧的 `start(...)` 入口默认保持该语义；
- `SCHEDULE`：为后续调度入口预留统一语义。

`SqlTaskExecutorAdapter` 不再把 trigger 固定为 `WORKFLOW`，而是将 Runtime 收到的 trigger 原样传入 `TaskExecutionContext`。

## Manual Run

Data Development 手动运行不再直接调用：

```text
DevelopmentTaskRunService
  -> TaskPluginRegistry
  -> SqlTaskPlugin
```

现在统一为：

```text
DevelopmentTaskRunService
  -> 当前编辑器 TaskDefinition
  -> 临时 TaskVersionSnapshot(version = 0)
  -> TaskExecutionGateway(trigger = MANUAL)
  -> SqlTaskExecutorAdapter
  -> TaskPluginRegistry
  -> SqlTaskPlugin / SqlTaskExecutor
  -> DataSourceExecutionProvider
  -> JDBC
```

这里的 `version = 0` 明确表示“当前未发布草稿的运行时快照”，不会污染 Task Catalog，也不会伪装成已发布 TaskRevision。

手动运行 API 仍保持原来的同步返回体验：Runtime 内部异步执行，`DevelopmentTaskRunService` 通过统一 `status` 接口等待终态后映射回现有 `DevelopmentTaskRunResult`。

## Workflow Run

Workflow 继续消费 Stage 6 已经固定的不可变 `TaskVersionSnapshot`：

```text
WorkflowVersion / test-run
  -> TaskVersionSnapshot(SQL)
  -> TaskExecutionGateway(trigger = WORKFLOW)
  -> SqlTaskExecutorAdapter
  -> TaskPluginRegistry
  -> SqlTaskPlugin / SqlTaskExecutor
```

核心约束保持不变：**工作流运行时只消费已经固定的不可变任务快照，禁止重新读取 Data Development 草稿、TaskAsset 当前版本或最新 TaskRevision。**

因此：工作流 v1 固定 SQL v1 后，即使任务资产已经发布 SQL v2，工作流 v1 后续运行仍执行 SQL v1。只有显式升级工作流草稿中的 TaskRevision 并重新发布，才会切换到 v2。

## 生命周期映射

SQL Plugin 的执行状态统一映射到 Task Runtime：

| Task Plugin | TaskExecution |
| --- | --- |
| `PENDING` | `PENDING` |
| `RUNNING` | `RUNNING` |
| `SUCCESS` | `SUCCEEDED` |
| `FAILED` | `FAILED` |
| `CANCELLED` | `CANCELED` |
| `TIMEOUT` | `TIMED_OUT` |

SQL Adapter 使用虚拟线程异步执行插件；`start`、`status`、`cancel` 均由同一 Runtime 抽象对外提供。取消仍调用插件执行器的取消能力，并最终向 JDBC Statement 传播。

## 幂等

Workflow 使用当前 node attemptId 作为 `idempotencyKey`，同一个 attempt 重复 `start` 返回同一个 SQL execution，避免恢复或重复提交时产生第二次物理 SQL 执行。

Manual Run 不传固定 `idempotencyKey`，每次用户点击运行都会创建一次新的物理执行，符合编辑器调试语义。

## 边界

- SQL：Manual / Workflow 已统一进入 `TaskExecutionGateway -> SqlTaskExecutorAdapter -> TaskPlugin`；
- SYNC：保持现有 `SyncTaskExecutorAdapter`，通过兼容入口继续工作；
- Schedule：Runtime 已具备 `SCHEDULE` trigger 入口，具体调度接线可在后续阶段使用同一 Gateway；
- 本阶段不新增 Shell / Python；
- 本阶段不引入持久化 `TaskExecution` 表，当前执行句柄仍由具体 Runtime Adapter 管理。

## 回归重点

- Manual Run 必须以 `MANUAL` trigger 进入 SQL Plugin；
- Workflow 旧入口必须继续默认使用 `WORKFLOW` trigger；
- 固定 SQL v1 后发布 SQL v2，旧 Workflow 仍执行 v1；
- 缺失 `definitionSnapshotJson` 时直接失败，不能回读最新任务定义；
- SQL 成功输出原样进入调用方 output；
- SQL FAILED / TIMEOUT / CANCELLED 正确映射到统一状态；
- 同一个 Workflow attempt 重复启动不会产生重复 SQL execution；
- SYNC 任务执行路径不受影响。
