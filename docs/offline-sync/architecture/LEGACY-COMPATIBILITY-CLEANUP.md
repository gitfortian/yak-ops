# Offline Sync Legacy Compatibility Cleanup

本清理发生在 Stage 12 之后，不属于 Stage 13。

目标是删除已经完成使命的 Java 过渡兼容层，同时继续保留仍有业务价值的持久化和历史查询兼容。

## Removed

以下过渡类型已经删除：

```text
repository.OfflineExecutionControlRepository
repository.OfflineExecutionIdempotencyRepository
domain.compat.LegacyBatchTriggerCompatibilityMapper
domain.compat.LegacyOfflineExecutionCompatibilityMapper
```

同时删除对应的 `domain.compat` 专属测试目录。

## Capability migration

删除兼容类型不等于删除现有能力。

### Trigger identity

原 `LegacyBatchTriggerCompatibilityMapper` 的稳定 Schedule trigger identity 提升为正式 Core 类型：

```text
BatchTriggerToken
```

Schedule token 的编码格式保持不变，已有 `BatchKey.schedule(scheduleId, plannedFireTime)` 语义不变。

### Persisted Attempt hydration

原 `LegacyOfflineExecutionCompatibilityMapper` 的持久化 Attempt -> `ExecutionAttempt` 映射收进：

```text
OfflineBatchExecutionRepositoryAdapter
```

Repository Adapter 继续兼容已持久化的状态名称，例如：

```text
LOST -> UNKNOWN
FINISHED / COMPLETED -> SUCCEEDED
CANCELLED -> CANCELED
CANCELLING -> CANCELING
```

这些属于持久化读取职责，不再通过独立的 legacy mapper 暴露。

## Compatibility intentionally retained

本次不删除：

- `yak_offline_job_execution` 等既有表名；
- Attempt persistence 中为了历史数据保留的字段；
- `batch_id = NULL` 的历史 execution 查询能力；
- 旧记录只读规则；
- 已存在的 Flyway migration；
- REST / DTO / VO 兼容；
- Task / Batch / Attempt / Cursor 领域语义。

因此本次清理是 **transitional Java compatibility removal**，不是历史数据迁移或破坏性 schema cleanup。

## Guardrail

`OfflineSyncLayeringConventionTest` 会扫描 offline-sync 生产源码，禁止重新引入：

- `@Deprecated` 过渡实现；
- `domain/compat`；
- 已删除的四个 compatibility 类型。

如果未来确实需要兼容，应优先把兼容规则放在拥有该数据或协议的明确边界，例如 Domain value object、Repository Adapter 或 inbound/outbound adapter，而不是重新创建全局 Legacy/Common compatibility facade。

## Stage status

```text
Stage 12        COMPLETE  Dependency Boundary Governance
Legacy Cleanup  COMPLETE  Transitional Java Compatibility Removal
Stage 13        DEFERRED  Architecture Guardrails
```
