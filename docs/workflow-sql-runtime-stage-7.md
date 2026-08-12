# Workflow SQL Runtime · Stage 7

## 目标

Stage 7 将 Workflow 的通用 `TaskExecutionGateway` 与 Stage 4 已落地的 SQL Task Plugin Runtime 接通，使 Workflow 的 `run / test-run` 可以执行 Stage 6 固定下来的 SQL `TaskVersionSnapshot`。

核心约束只有一条：**运行时只消费工作流已经固定的不可变任务快照，禁止重新读取 Data Development 草稿、TaskAsset 当前版本或最新 TaskRevision。**

## 执行链路

```text
WorkflowVersion / test-run
  -> TaskVersionSnapshot(SQL)
  -> TaskExecutionGateway
  -> SqlTaskExecutorAdapter
  -> TaskPluginRegistry
  -> SqlTaskPlugin / SqlTaskExecutor
  -> DataSourceExecutionProvider
  -> JDBC
```

`WorkflowRuntimeService` 仍然只依赖通用 `TaskExecutionGateway`，SQL 特有逻辑不会进入 Workflow Engine。

## 快照语义

Stage 6 在工作流保存/发布时已经将具体 `TaskRevision` 解析成 `TaskVersionSnapshot`，其中 `definitionSnapshotJson` 保存完整的 `TaskDefinition`。

Stage 7 的 `SqlTaskExecutorAdapter`：

- 只反序列化 `definitionSnapshotJson` 创建 SQL Plugin Executor；
- 不依赖 Data Development Repository；
- 不依赖 Task Catalog Service；
- 不查询“当前草稿”或“最新版本”；
- 快照缺失时直接失败，不提供 fallback。

因此：工作流 v1 固定 SQL v1 后，即使任务资产已经发布 SQL v2，工作流 v1 后续运行仍执行 SQL v1。只有显式升级工作流草稿中的 TaskRevision 并重新发布，才会切换到 v2。

## 生命周期映射

SQL Plugin 的执行状态统一映射到 Workflow Task Runtime：

| Task Plugin | TaskExecutionGateway |
| --- | --- |
| `PENDING` | `PENDING` |
| `RUNNING` | `RUNNING` |
| `SUCCESS` | `SUCCEEDED` |
| `FAILED` | `FAILED` |
| `CANCELLED` | `CANCELED` |
| `TIMEOUT` | `TIMED_OUT` |

SQL Adapter 使用虚拟线程异步执行插件，`start` 返回后 Workflow 延续现有轮询模型；`cancel` 继续调用插件的取消能力，最终由 Stage 4 的 JDBC SQL Executor 向底层 Statement 传播取消。

## 幂等

Workflow 使用当前 node attemptId 作为 `idempotencyKey`。SQL Adapter 对同一个 key 重复 `start` 返回同一个 SQL execution，避免恢复/重复提交时产生第二次物理 SQL 执行。

## 与现有能力的边界

- SYNC：保持现有 `SyncTaskExecutorAdapter` 不变；
- SQL 手动运行：继续使用 Stage 4 的 `DevelopmentTaskRunService -> TaskPluginRegistry`；
- SQL Workflow 运行：通过本阶段新增的 `SqlTaskExecutorAdapter -> TaskPluginRegistry`；
- 两条入口最终复用同一个 `SqlTaskPlugin / SqlTaskExecutor / DataSourceExecutionProvider`，不维护两套 SQL 实现。

## 回归重点

- 固定 SQL v1 后发布 SQL v2，旧 Workflow 仍执行 v1；
- 缺失 `definitionSnapshotJson` 时直接失败，不能回读最新任务定义；
- SQL 成功输出（columns / rows / affectedRows 等）原样进入 Workflow node output；
- SQL FAILED / TIMEOUT / CANCELLED 正确映射为 Workflow 可识别状态；
- 同一个 Workflow attempt 重复启动不会产生重复 SQL execution；
- SYNC 任务执行路径不受影响。
