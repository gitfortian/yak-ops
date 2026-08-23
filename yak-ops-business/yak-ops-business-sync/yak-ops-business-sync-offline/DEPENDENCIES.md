# Offline Sync Dependencies

本文件定义离线同步 package 的**依赖方向与跨子系统入口**。原则只有三个：**显式、窄、无环**。

架构职责看 `ARCHITECTURE.md`；若本文件与代码冲突，应先判断代码是否越界，不要直接放宽规则。

## Top-level Dependency Graph

允许的内部依赖如下：

| Source | Allowed |
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

同一 top-level package 内的子包属于同一子系统，例如 `execution.query`、`execution.adapter`；但它们不会自动成为其他子系统的公共 API。

## Declared Corridors

跨子系统依赖不能因为某个类是 `public` 就直接 import。当前 corridor：

### Enter Execution

```text
controller -> OfflineJobExecutionService
backfill   -> OfflineJobExecutionService
backfill   -> OfflineExecutionScopeValidator
reconcile  -> OfflineJobExecutionService
```

除这些入口外，其他子系统不得直接依赖 `OfflineExecutionCoordinator / ClaimManager / BatchRuntime / execution.query / execution.adapter` 等内部实现。

### Enter Cursor

```text
any subsystem -> OfflineCursorGateway
```

`OfflineCursorManager` 是 cursor 内部实现，不是跨包 API。

### Enter Schedule

```text
definition -> OfflineScheduleLifecycle
           -> OfflineScheduleSupport

execution  -> OfflineScheduleExecutionGateway
```

Schedule 不能反向 import execution implementation。`OfflineScheduleExecutionGateway` 由 execution 侧实现，但接口属于 schedule 边界。

## Bottom-layer Rules

```text
Domain       -> no upper-layer dependency
DAO          -> config only
Repository   -> domain + dao + config
Engine       -> config only
```

具体约束：

- Domain 不知道 Spring Controller、Repository Adapter、Link-Up、Quartz、MyBatis、DTO/VO/PO。
- DAO 不知道业务 Service / Manager / Coordinator。
- Repository contract 使用 Domain 类型；PO / MyBatis 细节停在 adapter / DAO。
- Engine package 封装 Link-Up 协议，不把外部 DTO 变成 Core Domain。
- Credential 只在 outbound submit boundary 解析，不向 Domain / Snapshot 泄漏。

## No Cycles

Top-level package dependency graph 必须无环。

出现类似：

```text
definition -> schedule -> execution -> definition
```

不要靠 Spring lazy、事件总线或接口搬家掩盖循环。先重新确认职责 owner，并设计窄 corridor。

## Dependency Injection

依赖通过构造器显式注入。业务类不要：

- 在方法内部查 Spring Bean；
- 使用全局静态可变对象共享业务状态；
- 为了绕开依赖规则使用反射；
- 把多个依赖塞进 `Context/Common/Helper` 对象隐藏真实耦合。

构造器依赖过多是职责过宽的信号，应优先拆角色或收窄 Gateway。

## Guardrails

当前主要由两类测试守护：

```text
OfflineSyncLayeringConventionTest
OfflineSyncDependencyBoundaryTest
```

它们负责检查稳定入口、角色约定、top-level dependency graph、无环和 corridor。

## Adding a New Dependency

当一个新 import 不在允许图中时，不要第一反应修改白名单。按顺序判断：

1. **类放错 package 了吗？** —— 先归位。
2. **已有 Facade / Gateway 能表达需求吗？** —— 优先复用。
3. **确实缺一个边界吗？** —— 在能力 owner 一侧定义最小接口。
4. **架构真的改变了吗？** —— 同一 PR 更新本文件和 architecture test。

只有第 4 种情况才应该扩大 dependency graph。
