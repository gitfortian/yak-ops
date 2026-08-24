# Job Requirements

本文件只回答：Job 模块需要提供什么能力。

## 模块定位

Job 是业务无关的任务发现与执行路由层。业务域负责定义任务和拥有业务执行事实；Job 负责把这些能力统一暴露给 Workflow、Data Development 等调用方。

## 核心能力

### Task Discovery

业务域通过 `TaskProvider` 暴露任务。Job 不应在 Registry 中直接扫描某个业务 Service。

每个被发现任务至少包含：

```text
TaskDefinition         = 可选择的任务描述
TaskVersionSnapshot    = 被固定的执行输入
```

Registry 必须拒绝重复 Task ID，并保证描述与快照属于同一个 Task。

### Task Execution

调用方统一通过：

```text
TaskExecutionGateway
    -> TaskExecutor
    -> TaskExecution
```

Job 根据 Task Type 路由，不复制各业务域的执行状态机。

SQL / Python / Java / Shell 这类 Task Plugin 应共享同一套本地执行生命周期：校验、幂等、运行句柄、状态、取消和结果转换只实现一次。

SYNC 属于外部业务运行时：Job 只做适配，不拥有 Offline Sync 的 Execution 真相。

### Immutable Snapshot

有版本能力的任务执行时必须使用已经固定的 `TaskVersionSnapshot`。运行期间不能回读当前草稿或最新版本替换它。

### Runtime Context

Task Runtime Context 可以包含参数、Trigger、全局环境变量及任务类型能力。环境变量通过窄接口提供，执行层不依赖设置页面的 CRUD 实现。

## 非职责

Job 不负责：

- Cron / Schedule 注册与生命周期；
- Offline Sync / Data Development 等业务任务定义；
- Workflow 编排；
- 业务 Execution 持久化；
- 把所有 Task Type 的业务逻辑集中到一个超级 Service。

## 兼容要求

Stage 1 保持现有 REST、Task SPI、Workflow 调用方式、Offline Sync 执行行为和环境变量 API 不变；不新增数据库迁移。
