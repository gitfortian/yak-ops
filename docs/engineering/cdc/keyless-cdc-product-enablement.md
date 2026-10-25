# Keyless MySQL CDC: Product Admission

## Supported path

- Single-table MySQL to MySQL `SINGLE_TABLE_REALTIME`, steps `FULL` + `INCREMENTAL`.
- A source table without a physical primary key and an explicit non-null numeric snapshot Chunk Key.
- An existing MySQL InnoDB target with a matching physical full-row projection, no unique keys, unsupported triggers, foreign keys or generated columns.
- Exact-row `RETRACT` with checkpoint-coordinated MySQL XA. One Sink writer, direct JDBC connections and durable aligned checkpoints are required.
- PostgreSQL/Oracle keyless CDC, multi-table keyless execution, keyless schema changes and automatic target creation are not supported.

## Task definition

A keyless task must contain `xa: true`. This persisted protocol marker prevents an incompatible task definition from silently starting under a different transaction mechanism.

```json
{
  "solutionType": "SINGLE_TABLE_REALTIME",
  "steps": ["FULL", "INCREMENTAL"],
  "schemaChangesEnabled": false,
  "source": { "datasourceId": "source-mysql" },
  "sink": { "datasourceId": "target-mysql" },
  "tableSelection": { "mode": "EXPLICIT" },
  "tableMappings": [{
    "source": { "database": "app", "table": "events", "keyless": true, "chunkKeyColumn": "bucket", "xa": true },
    "target": { "database": "ods", "table": "events" },
    "writeMode": "RETRACT"
  }]
}
```

The Chunk Key selects snapshot ranges, not row identity. Identical physical rows retain multiplicity. UPDATE and DELETE match exactly one row by its complete before-image.

## Runtime requirements

The source requires `binlog_format=ROW`, `binlog_row_image=FULL`, empty `binlog_row_value_options`, replication permissions and a consistent snapshot lock. The target requires `xa_detach_on_prepare=ON` and XA recovery privileges. SSH-tunneled and unverified MySQL XA connections are not supported.

At each aligned Checkpoint the Sink prepares XA transactions. The Runtime persists prepared XIDs with Source offsets, then issues XA COMMIT after durable checkpoint publication. On restore, it reconciles only transactions owned by the current task instance and fails closed on ambiguous outcomes. Never broadly roll back unrelated prepared XIDs.

## Recovery and acceptance

`MySqlXaSinkIT` verifies prepared visibility, orphan cleanup and recovered commit acknowledgment. `RealtimeSyncIT` exercises the product MySQL-to-MySQL snapshot and Binlog path. The JDBC/CDC/process-recovery suites remain required. Isolated database tests do not prove every JVM crash boundary or distributed exactly-once semantics.

Definitions missing the required XA marker are rejected. An incompatible previous execution and checkpoint are never implicitly upgraded or reinterpreted. After checking target data, explicitly create a new definition and execution rather than reusing stale state.

Unsupported cases include target schema changes, Chunk Key changes across snapshot ranges, unverified full-row comparisons, multiple Sink writers, remote/rescaled transactions and unbounded duplicate-row buffering.
