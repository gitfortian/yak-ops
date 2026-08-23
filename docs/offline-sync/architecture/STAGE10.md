# Offline Sync Stage 10 — Role Naming

## Goal

Stage 10 将 Stage 7 定义的角色词汇正式落实到 Java 类型名。

Stage 8 已解决“类放在哪里”，Stage 9 已解决“谁可以调用谁”。Stage 10 解决的是：**看到类名时，能否直接知道它在运行链路中的角色。**

本阶段只改变角色表达，不改变 Task / Batch / Attempt / Cursor 业务语义，不拆大类，不修改 REST、数据库或 Flyway。

## Naming result

| Stage 9 name | Stage 10 name | Role |
| --- | --- | --- |
| `OfflineExecutionOrchestrator` | `OfflineExecutionCoordinator` | 协调 claim -> engine -> runtime -> event 的执行流程 |
| `OfflineExecutionClaimService` | `OfflineExecutionClaimManager` | 管理 Batch/Attempt claim、reservation、Retry/Backfill 创建 |
| `OfflineBatchRuntimeService` | `OfflineBatchRuntime` | 维护 Batch runtime truth 与 latest Attempt 状态推导 |
| `OfflineCursorService` | `OfflineCursorManager` | 管理 Cursor route、position 与 success-only CAS 推进 |
| `OfflineExecutionReadService` | `OfflineExecutionQuery` | Execution page/detail/events/metrics 读模型 |
| `OfflineExecutionLogService` | `OfflineExecutionLogQuery` | Yak Ops Event + Link-Up 日志统一查询 |

## Stable Application Services

以下三个名字保持不变：

```text
OfflineJobDefinitionService
OfflineJobExecutionService
OfflineBackfillService
```

它们是真正的 Application Facade，对 Controller 或其他入口提供稳定业务能力，因此保留 `Service` 语义。

Stage 10 同时把内部 `Coordinator / Manager / Runtime` 从 Spring `@Service` 调整为 `@Component`。这不会改变 Bean 生命周期或依赖注入行为，只让代码语义更明确：

```text
@Service   = Application Facade
@Component = internal specialized role
```

## Current execution chain

```text
Controller / Schedule / Backfill Dispatcher / Reconciler
                         |
                         v
              OfflineJobExecutionService
                         |
          +--------------+--------------+
          |              |              |
          v              v              v
ExecutionCoordinator  BatchRuntime  ExecutionQuery
          |                            |
          v                            v
 ExecutionClaimManager          ExecutionLogQuery
          |
          v
       Link-Up
```

Cursor 独立为：

```text
OfflineCursorManager
  <- OfflineBatchRuntime      # SUCCEEDED Batch CAS advance
  <- OfflineBackfillService   # initialize / validate
  <- ScopeExecutionAdapter    # resolve cursor source column
```

## Naming vocabulary

Stage 10 后使用以下角色词汇：

| Suffix / role | Meaning |
| --- | --- |
| `Service` | 稳定 Application Facade / use-case entry |
| `Coordinator` | 协调跨组件流程，本身不是公共 API |
| `Manager` | 管理一类状态资源、reservation 或生命周期 |
| `Runtime` | 运行时真相边界与状态推导 |
| `Query` | 查询、读模型或展示聚合，不承担状态命令 |
| `Dispatcher` | 扫描待处理对象并分发工作 |
| `Reconciler` | 对账外部运行状态并恢复本地真相 |
| `Adapter` | 两个明确边界之间的模型投影 |
| `Mapper` | 纯模型转换 |
| `Support` | 局部解析、校验或构建能力 |
| `Client / Gateway` | 外部协议边界 |
| `Repository` | 领域持久化契约 |

命名不是为了“去 Service 化”而机械替换。只有角色已经稳定、职责能被一个准确名词表达时才改名。

## Guardrails

`OfflineSyncLayeringConventionTest` 增加 Stage 10 规则：

1. 三个 Application Facade 必须继续使用 `@Service`；
2. Coordinator / ClaimManager / BatchRuntime / CursorManager / Query 必须是内部 `@Component`，不能伪装成 Application Service；
3. Schedule / Backfill Dispatcher / Reconciler 仍必须通过 `OfflineJobExecutionService` 进入 execution；
4. 非 execution 内部代码不得直接 import Coordinator / ClaimManager / BatchRuntime / Query / Adapter，除 Stage 9 已明确的 Facade 内部例外；
5. 生产源码不得重新出现六个 Stage 9 旧角色名。

## Test alignment

核心测试同步按角色命名：

```text
OfflineExecutionCoordinatorTest
OfflineExecutionClaimManagerTest
OfflineBatchRuntimeTest
OfflineCursorManagerTest
OfflineExecutionLogQueryTest
```

Stage 8 遗留在 `service` 测试包的旧日志测试同步迁到 `execution.query`，并使用当前 Domain + Repository 契约；测试行为仍是验证 Yak Ops Event 与 Link-Up Worker Log 的统一时间线。

## Non-goals

Stage 10 明确不做：

- 不拆 `OfflineExecutionCoordinator`；
- 不拆 `OfflineExecutionClaimManager`；
- 不拆 `OfflineBackfillService`；
- 不改变 Retry / UNKNOWN / Cancel / Backfill / Cursor 规则；
- 不改变 Controller 路径或 DTO / VO；
- 不改变 Repository / DAO / PO；
- 不修改数据库与 Flyway；
- 不引入新的抽象接口层；
- 不为了追求统一而重命名已经准确的 Dispatcher / Reconciler / Adapter / Mapper / Support。

## Review focus

Stage 10 Review 只需要回答：

```text
1. 新名字是否准确表达当前职责？
2. 是否还有生产代码引用旧角色名？
3. Application Service 与内部组件的语义是否更清晰？
4. rename 是否保持原有运行行为？
```

业务逻辑拆分不属于本 PR。

## Next

```text
Stage 11 — Core Responsibility Decomposition
```

Stage 11 才开始真正审视大类的独立变化原因，优先关注：

```text
OfflineExecutionCoordinator
OfflineExecutionClaimManager
OfflineBackfillService
```

原则仍然是：**有独立变化原因才拆，不制造 HelperService / CommonService / SupportService。**
