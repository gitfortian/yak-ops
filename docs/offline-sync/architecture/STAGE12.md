# Offline Sync Stage 12 — Dependency Boundary Governance

## Goal

Stage 12 在 Stage 8-11 已经完成的目录、入口、角色和职责拆分之上，固定 **top-level package 的依赖方向**。

本阶段解决的问题不是“类还能不能继续拆”，而是：

```text
哪个子系统可以依赖哪个子系统？
跨子系统调用时，允许穿过哪一扇门？
内部 Manager / Adapter 是否会重新泄漏成公共 API？
整个 offline package 能否保持无环？
```

Stage 12 不追求一套脱离现状的纯理论分层。规则先从当前业务语义出发，再把合法协作收敛成明确的 Gateway / Validator / Repository contract。

## Dependency principles

### 1. Stable domain and persistence direction

```text
Application / Runtime
        |
        v
   Repository contract
        |
        v
       DAO

Domain <---------------- business/runtime
```

硬规则：

- `domain` 不反向依赖 Application、Engine、Repository、DAO；
- `dao` 不依赖业务子系统；
- `repository` adapter 只依赖 `domain / dao / config`；
- `engine` 不依赖 Definition / Execution / Backfill / Schedule；
- Controller 不直接依赖 Repository / DAO / Engine。

### 2. Cross-subsystem calls use named corridors

跨子系统不是禁止协作，而是禁止“随便 import 一个内部类”。

Stage 12 新增三条稳定边界：

```text
OfflineCursorGateway
OfflineExecutionScopeValidator
OfflineScheduleExecutionGateway
```

它们表达调用方真正需要的最小能力，而不是把实现类暴露出去。

## Changes

### Cursor boundary

Before:

```text
BackfillPlanner ------> OfflineCursorManager
BackfillDispatcher ---> OfflineCursorManager
BatchRuntime ---------> OfflineCursorManager
ScopeAdapter ---------> OfflineCursorManager
```

After:

```text
BackfillPlanner ------+
BackfillDispatcher ---+
BatchRuntime ---------+--> OfflineCursorGateway
ScopeAdapter ---------+            |
                                   v
                         OfflineCursorManager
```

`OfflineCursorManager` 继续负责 Cursor route、position、stateVersion 和 success-only CAS；跨子系统代码只认识 `OfflineCursorGateway`。

### Backfill -> Execution scope boundary

Before:

```text
OfflineBackfillPlanner
    -> execution.adapter.OfflineBatchScopeExecutionAdapter
```

After:

```text
OfflineBackfillPlanner
    -> OfflineExecutionScopeValidator
             |
             v
OfflineBatchScopeExecutionAdapter
```

Backfill 只需要回答：

> 这个 frozen logical JobSpec 是否可以应用当前 BatchScope？

它不需要知道 execution 子系统具体使用哪个 Adapter、如何修改 JobSpec。

### Schedule -> Execution inversion

Stage 11 仍存在：

```text
Definition -> Schedule -> Execution -> Definition
```

虽然每一条依赖都有业务理由，但 top-level package 已形成环。

Stage 12 将 Schedule 的执行需求定义为调用方拥有的 Port：

```text
schedule.OfflineScheduleExecutionGateway
```

由 `OfflineJobExecutionService` 实现：

```text
OfflineScheduleHandler
        |
        v
OfflineScheduleExecutionGateway   <--- defined by schedule
        ^
        |
OfflineJobExecutionService        <--- implementation by execution
```

Schedule Handler 不再 import `execution.*`，也不再接触 `OfflineJobExecutionVO`；它只拿到本次提交的 `executionId`。

这样原来的：

```text
Definition -> Schedule -> Execution -> Definition
```

变成：

```text
Execution -> Definition -> Schedule
Execution -------> Schedule Port
```

实际 package graph 可以保持无环。

### Definition runtime guard

Before:

```text
OfflineJobDefinitionService
    -> OfflineBatchRuntime
```

Definition 只需要判断：

```text
Task 当前是否存在 occupying Batch？
```

而这个事实已经由 `OfflineBatchExecutionRepository.hasOccupyingBatch()` 定义为 Batch runtime truth。

After:

```text
OfflineJobDefinitionService
    -> OfflineBatchExecutionRepository
```

因此 Definition 不再为了一个 runtime guard 依赖整个 Execution 内部 Runtime 组件，同时没有退回到 Task `last-*` projection。

## Top-level dependency matrix

Stage 12 的可执行白名单：

| Source package | Allowed offline dependencies |
| --- | --- |
| `controller` | `config`, `definition`, `execution`, `backfill` |
| `backfill` | `config`, `cursor`, `definition`, `domain`, `execution`, `repository` |
| `reconcile` | `config`, `domain`, `engine`, `execution`, `repository` |
| `execution` | `config`, `cursor`, `definition`, `domain`, `engine`, `mapping`, `repository`, `schedule` |
| `definition` | `config`, `domain`, `engine`, `mapping`, `repository`, `schedule` |
| `schedule` | `config`, `domain`, `repository` |
| `cursor` | `config`, `domain`, `repository` |
| `mapping` | `domain`, `engine` |
| `repository` | `config`, `dao`, `domain` |
| `dao` | `config` |
| `engine` | `config` |
| `domain` | none |
| `config` | none |

同一个 top-level package 内部可以自由组织子 package，例如：

```text
execution
├── query
└── adapter
```

但这不意味着其他子系统可以直接穿透 `execution.query` 或 `execution.adapter`。

## Named corridors

### Into execution

允许的跨包 execution import 被限制为：

```text
controller
    -> OfflineJobExecutionService

backfill
    -> OfflineJobExecutionService
    -> OfflineExecutionScopeValidator

reconcile
    -> OfflineJobExecutionService

schedule
    -> no execution import

definition
    -> no execution import
```

### Into cursor

Cursor 对外只有：

```text
OfflineCursorGateway
```

跨子系统禁止 import `OfflineCursorManager`。

### Into schedule

当前显式走廊：

```text
definition
    -> OfflineScheduleLifecycle
    -> OfflineScheduleSupport

execution
    -> OfflineScheduleExecutionGateway
```

Stage 12 不为了“接口化一切”给每一个现有组件再套一层 Port；只有跨包依赖已经造成扩散或循环时才增加边界。

## Architecture tests

### OfflineSyncLayeringConventionTest

继续守护 Stage 7-11 的历史契约：

- Controller -> stable Application Facade；
- `@Service` 只保留给三个 Application Service；
- Coordinator / Manager / Runtime / Query / Planner 的职责拆分；
- 后台入口不穿透 execution internals；
- Stage 10 旧角色名不回流；
- Repository / DAO transport boundary。

Stage 12 同时验证：

```text
OfflineCursorManager implements OfflineCursorGateway
OfflineBatchScopeExecutionAdapter implements OfflineExecutionScopeValidator
OfflineJobExecutionService implements OfflineScheduleExecutionGateway
```

### OfflineSyncDependencyBoundaryTest

Stage 12 新增独立的依赖治理测试，职责只有三个：

1. 扫描生产源码的 offline 内部 import，验证 top-level package 白名单；
2. 根据真实 import 生成 package graph，并验证 graph 无环；
3. 验证 execution / cursor / schedule 的跨子系统 import 只能经过声明过的 corridor。

这意味着未来新代码如果出现：

```text
schedule -> execution.OfflineExecutionCoordinator
backfill -> execution.adapter.OfflineBatchScopeExecutionAdapter
execution -> cursor.OfflineCursorManager
repository -> execution.*
domain -> repository.*
```

测试会直接失败。

## Resulting graph

当前主要依赖方向可以概括为：

```text
controller
   |
   +---------> definition ---------> schedule
   |                |                  |
   |                +-----> engine     +-----> repository
   |                +-----> mapping             |
   |                +-----> repository           v
   |                                           dao
   +---------> execution ---------> definition
   |                |
   |                +-----> engine
   |                +-----> cursor -------> repository
   |                +-----> mapping
   |                +-----> repository
   |                `-----> schedule port
   |
   `---------> backfill ----------> execution corridor
                    |
                    +-----> cursor gateway
                    +-----> definition
                    `-----> repository

reconcile ----------> execution facade / engine / repository

domain               # leaf business model
config                # leaf module configuration
```

依赖图允许多个上层子系统共享 Domain / Repository contract，但不允许底层反向认识上层流程组件。

## Runtime invariants preserved

Stage 12 不改变：

- `Task != Batch != Attempt`；
- Batch 是 runtime truth；
- Task `last-*` 只是 projection；
- Retry 只在原 Batch 内追加 Attempt；
- UNKNOWN 禁止盲 Retry；
- terminal Batch 禁止追加 Attempt；
- Backfill 物化 Batch group；
- Cursor 只在 SUCCEEDED CursorRange Batch 后 CAS 推进；
- batchless legacy execution 只读；
- Snapshot 不持久化运行时数据源凭据；
- REST / DTO / VO / DB / Flyway / Link-Up API contract。

## Non-goals

Stage 12 不做：

- 不继续拆 Stage 11 的核心类；
- 不把所有内部协作都接口化；
- 不移动 Domain / Repository / DAO package；
- 不引入新的 Shared Kernel；
- 不改变业务事务和状态机；
- 不使用 package graph 规则代替领域测试。

原则仍然是：

> **只有跨边界的能力才需要稳定门；子系统内部保持直接、清晰。**

## Next

```text
Stage 13 — Architecture Guardrails
```

Stage 13 应对 Stage 7-12 已形成的架构 contract 做最终固化：整理架构测试入口、Review checklist、文档索引和 CI 可见性，避免后续演进重新退回“service 大平层 + 随意穿透”的状态。
