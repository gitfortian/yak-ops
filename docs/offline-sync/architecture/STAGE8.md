# Offline Sync Stage 8 — Package Restructuring

## Goal

Stage 8 将 Stage 7 已确认的职责分类落实为代码目录。目标不是减少类数量，而是让目录直接表达离线同步的核心子系统和运行角色。

本阶段只做 package/import 结构迁移，不改变 REST、数据库、Task/Batch/Attempt/Cursor 领域语义，不做类级重命名和大类拆分。

## Result

生产代码从单一 `service` 大平层迁移为：

```text
io.yak.ops.business.sync.offline
├── controller
├── definition
│   ├── OfflineJobDefinitionService
│   └── OfflineDefinitionSupport
├── execution
│   ├── OfflineJobExecutionService
│   ├── OfflineExecutionOrchestrator
│   ├── OfflineExecutionClaimService
│   ├── OfflineBatchRuntimeService
│   ├── query
│   │   ├── OfflineExecutionReadService
│   │   ├── OfflineExecutionLogService
│   │   └── OfflinePipelineMetricsMapper
│   └── adapter
│       └── OfflineBatchScopeExecutionAdapter
├── backfill
│   ├── OfflineBackfillService
│   └── OfflineBackfillDispatcher
├── cursor
│   └── OfflineCursorService
├── schedule
│   ├── OfflineScheduleEngineBridge
│   ├── OfflineScheduleHandler
│   ├── OfflineScheduleLifecycle
│   ├── OfflineScheduleReconciler
│   └── OfflineScheduleSupport
├── reconcile
│   └── OfflineExecutionReconciler
├── mapping
│   └── OfflineSyncViewMapper
├── domain
├── engine
├── repository
├── dao
└── config
```

## Stable application entry points

Stage 8 保留 Stage 7 判定的三个 Application Facade，不把所有 `Service` 后缀机械消灭：

```text
OfflineJobDefinitionService  -> definition
OfflineJobExecutionService   -> execution
OfflineBackfillService       -> backfill
```

Controller 仍通过这些业务入口进入链路，不直接依赖 Repository、DAO 或 Link-Up Client。

## Internal role placement

| Class | Stage 8 package | Role |
| --- | --- | --- |
| `OfflineExecutionOrchestrator` | `execution` | execution coordinator |
| `OfflineExecutionClaimService` | `execution` | batch/attempt claim manager |
| `OfflineBatchRuntimeService` | `execution` | batch runtime truth |
| `OfflineExecutionReadService` | `execution.query` | read model |
| `OfflineExecutionLogService` | `execution.query` | log query/aggregation |
| `OfflinePipelineMetricsMapper` | `execution.query` | query mapper |
| `OfflineBatchScopeExecutionAdapter` | `execution.adapter` | scope -> engine JobSpec projection |
| `OfflineBackfillDispatcher` | `backfill` | background dispatcher |
| `OfflineCursorService` | `cursor` | cursor boundary |
| `OfflineExecutionReconciler` | `reconcile` | background reconciliation |
| `OfflineScheduleSupport` | `schedule` | schedule config support |
| `OfflineSyncViewMapper` | `mapping` | output model mapping |

## Non-goals

Stage 8 explicitly does not:

- rename `Orchestrator` to `Coordinator` or `ClaimService` to `ClaimManager`;
- split `OfflineExecutionOrchestrator`, `OfflineExecutionClaimService` or `OfflineBackfillService`;
- move Domain / Repository / DAO into a new persistence hierarchy;
- change REST paths or DTO/VO contracts;
- change Flyway or table schemas;
- change Retry / UNKNOWN / Cancel / Backfill / Cursor runtime rules.

Those changes belong to later stages so Review can distinguish structural movement from semantic changes.

## Test alignment

Active tests that exercise moved runtime components follow the same subsystem packages. Package-private collaborators such as `OfflinePipelineMetricsMapper` and `OfflineScheduleSupport` move together with their tests.

Historical orphan tests that are unrelated to the Stage 8 production package set are not silently rewritten as part of this migration; they should be handled as separate test-debt cleanup if needed.

## Next

```text
Stage 9 — Application Entry Consolidation
```

Stage 9 should focus on who is allowed to call the three stable application entry points and prevent internal Coordinator / Runtime / Query components from becoming accidental cross-module APIs.
