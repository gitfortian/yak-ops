# Offline Sync Migration Contract

Offline Sync 在 Yak Ops 第一版正式发布前使用一个干净的 Flyway 基线。

## Namespace ownership

Offline Sync 只拥有自己的 migration location 和 history table：

```text
classpath:db/migration/yak-offline-sync
yak_offline_sync_schema_history
```

不得把 Offline Sync migration 放入 Datasource、Data Development、Data Service 或其他模块的 namespace。

## First-release baseline

第一版正式发布前，数据库数据可丢弃，因此开发期的增量 migration 被收敛为：

```text
V1__baseline_offline_sync.sql
```

V1 直接表达当前最终 schema，不重放开发过程。

历史开发过程包括：

```text
V1  Task / Attempt / Event baseline
V2  BatchExecution + Attempt.batch_id
V3  Batch status data backfill
V4  frozen logical JobSpec + Cursor
V5  LOST -> UNKNOWN data normalization
V6  Project scope
V7  overview indexes
```

最终 V1 的规则：

1. V2/V4/V6/V7 的最终字段和索引直接进入 `CREATE TABLE`。
2. V3/V5 属于历史数据修复；空的一期数据库不需要执行，因此不进入 baseline。
3. Baseline 不包含 `ALTER TABLE`、历史 `UPDATE` backfill 或 Wave/Stage 迁移过程。
4. Baseline 只创建当前仍由 Offline Sync 拥有的物理表。

当前表：

```text
yak_offline_job_definition
yak_offline_batch_execution
yak_offline_job_execution
yak_offline_execution_event
yak_offline_sync_cursor
```

## Development database reset

这次 squash 不兼容已有开发期 Flyway checksum/history。合并后应一次性清理无价值的本地开发数据：

```sql
DROP TABLE IF EXISTS yak_offline_execution_event;
DROP TABLE IF EXISTS yak_offline_sync_cursor;
DROP TABLE IF EXISTS yak_offline_job_execution;
DROP TABLE IF EXISTS yak_offline_batch_execution;
DROP TABLE IF EXISTS yak_offline_job_definition;

DROP TABLE IF EXISTS yak_offline_core_schema_history;
DROP TABLE IF EXISTS yak_offline_sync_schema_history;
```

不要在有价值或生产数据库执行该 reset。

## After first formal release

第一版正式发布后：

- `V1__baseline_offline_sync.sql` 立即冻结，不再修改或重命名；
- 后续 schema 变更使用 `V2__...`, `V3__...`；
- 不再通过 reset/squash 规避 Flyway history；
- 已执行 migration 的 checksum/version/description 必须保持稳定。
