# Job Architecture

Job 采用“发现、快照、执行、运行上下文”四个明确角色，不以调度为中心。

## Stage 1 结构

```text
controller
    -> task registry / env service

task
├── discovery
│   TaskProvider / TaskRegistration / TaskRegistry
│
├── execution
│   TaskExecutionGateway / TaskExecutor
│
├── runtime
│   AbstractTaskExecutorAdapter
│   SQL / Python / Java / Shell adapters
│
└── sync adapter
    SyncTaskExecutorAdapter / OfflineSyncTaskRunner

env
├── SystemEnvVarService
└── TaskEnvironmentResolver
```

现阶段仍保留 `task` 单包以控制改动；Stage 2 再做物理 package 收敛。

## Discovery

`InMemoryTaskRegistry` 只能依赖 `TaskProvider`：

```text
Business source
    -> TaskProvider
    -> TaskRegistration
    -> TaskRegistry
```

Offline Sync 在 Stage 1 先通过 `OfflineSyncTaskProvider` 隔离直接依赖；是否把 Provider/Executor Adapter 物理移回 Offline Sync 模块，留到 Stage 2 做 Maven 依赖反转。

## Execution

```text
caller
  -> TaskExecutionGateway
  -> TaskExecutor by type
```

Gateway 只负责路由和输入防御。

Plugin Task 的公共生命周期由 `AbstractTaskExecutorAdapter` 负责：

```text
snapshot validation
 -> Task Plugin lookup
 -> plugin validation
 -> TaskExecutionContext
 -> idempotency
 -> async execution
 -> status / cancel / result conversion
```

SQL 不再复制这套生命周期，只负责贡献 `DataSourceExecutionProvider` / `SqlExecutionRuntime` Capability。

SYNC 是外部业务 Runtime Adapter，不强行继承 Plugin Runtime。

## Environment

```text
TaskExecutionContextFactory
    -> TaskEnvironmentResolver
```

Factory 不依赖环境变量 CRUD/DAO 细节。`SystemEnvVarService` 是当前 Resolver 实现和设置页稳定入口。

## 角色词汇

```text
Provider   暴露业务能力
Registry   聚合与查找
Gateway    稳定外部入口/路由
Executor   执行一种 Task Type
Adapter    翻译外部 Runtime / Plugin
Resolver   解析运行上下文
Factory    构造上下文对象
Service    稳定应用入口
```

Stage 1 不为了去 Service 改名；Stage 2 再补依赖矩阵、角色约束和完整架构测试。
