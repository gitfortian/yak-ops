# Job Domain

核心关系：

```text
TaskDefinition != TaskVersionSnapshot != TaskExecution
```

当前类名 `TaskDefinition` 为兼容名称；在 Job 领域里它只表示“可发现的任务描述”，不是可执行定义本体。

## Truth Ownership

```text
TaskDefinition        = Job 可发现的任务描述
TaskVersionSnapshot   = 一次运行固定的不可变输入
TaskExecution         = Job 对一次执行的统一观察视图
TaskProvider          = 业务域到 Job 的发现边界
TaskExecutor          = 一种 Task Type 的执行能力
Business Module       = 业务任务定义与业务执行真相 Owner
Scheduler             = 业务时间触发 Owner
```

## 硬规则

1. Job 不拥有业务 Task Definition，只持有描述和执行快照。
2. 有版本能力的 Task 必须执行固定 Snapshot，不能运行时漂移到最新版本。
3. `TaskRegistration` 中 descriptor 与 snapshot 必须引用同一 Task ID 和 Task Type。
4. `TaskRegistry` 只聚合 `TaskProvider`，不直接知道 Offline Sync、Data Development 等业务实现。
5. `TaskExecutionGateway` 只做路由，不实现 SQL/SYNC 等业务逻辑。
6. Plugin Task 共用一套本地执行生命周期；Task Type 只贡献 Capability。
7. SYNC 执行事实属于 Offline Sync，Job 只转换为统一 `TaskExecution` 视图。
8. `TaskExecution` 是统一观察模型，不是所有业务执行记录的持久化 Owner。
9. Environment 是 Runtime Context，不是 Task 或 Execution 真相。
10. Job 不注册业务 Schedule，不维护 Cron 生命周期。

## Snapshot Contract

```text
Workflow publish / manual prepare
    -> TaskVersionSnapshot
    -> execution
```

`version > 0` 的业务版本必须携带对应的不可变 definition/config snapshot。`version = 0` 可以表示当前编辑器等临时运行，但调用方必须显式构造这份快照。

## Discovery Contract

业务 Provider 交付：

```text
TaskRegistration
├── TaskDefinition
└── TaskVersionSnapshot
```

Job 只负责聚合、冲突检查和查找，不重新解释业务发布状态。
