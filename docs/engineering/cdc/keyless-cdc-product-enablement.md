# Keyless MySQL CDC: Product Admission

## Supported path

- Single-table MySQL to MySQL `SINGLE_TABLE_REALTIME`, steps `FULL` + `INCREMENTAL`.
- A source table without a physical primary key and an explicit non-null numeric snapshot Chunk Key.
- An existing MySQL InnoDB target with a matching physical full-row projection, no unique keys, unsupported triggers, foreign keys or generated columns.
- Exact-row `RETRACT` with checkpoint-coordinated MySQL XA. One Sink writer, direct JDBC connections and durable aligned checkpoints are required.
- PostgreSQL/Oracle keyless CDC, multi-table keyless execution, keyless schema changes and automatic target creation are not supported.

## Task definition

The source table's physical primary key is discovered from authorized Catalog metadata. When no primary key exists, a non-null numeric snapshot Chunk Key is required. No persisted keyless flag or implicit row identity is accepted. The V4 job contract pins exact-row retraction under `sink.tables[].write.transaction.mode`, never under Source. The Schema Policy is owned by Sink. This example requires an authorized MySQL Source and Sink and verified target metadata.

```json
{
  "solutionType": "SINGLE_TABLE_REALTIME",
  "steps": ["FULL", "INCREMENTAL"],
  "source": {
    "datasourceId": "source-mysql",
    "tables": [{
      "id": "events",
      "database": "app",
      "table": "events",
      "snapshot": { "chunkKeyColumn": "bucket" }
    }]
  },
  "transform": [],
  "sink": {
    "datasourceId": "target-mysql",
    "schemaPolicy": {
      "initial": "VERIFY",
      "evolution": { "enabled": false, "allowDropColumns": false }
    },
    "tables": [{
      "input": "events",
      "database": "ods",
      "table": "events",
      "write": {
        "mode": "RETRACT",
        "transaction": { "mode": "CHECKPOINT_XA" }
      }
    }]
  },
  "env": {}
}
```

The Chunk Key selects snapshot ranges, not row identity. Identical physical rows retain multiplicity. UPDATE and DELETE match exactly one row by its complete before-image.

## Runtime requirements

The source requires `binlog_format=ROW`, `binlog_row_image=FULL`, empty `binlog_row_value_options`, replication permissions and a consistent snapshot lock. The target requires `xa_detach_on_prepare=ON` and XA recovery privileges. SSH-tunneled and unverified MySQL XA connections are not supported.

At each aligned Checkpoint the Sink prepares XA transactions. The Runtime persists prepared XIDs with Source offsets, then issues XA COMMIT after durable checkpoint publication. On restore, it reconciles only transactions owned by the current task instance and fails closed on ambiguous outcomes. Never broadly roll back unrelated prepared XIDs.

## Recovery and acceptance

`MySqlXaSinkIT` verifies prepared visibility, orphan cleanup and recovered commit acknowledgment. `RealtimeSyncIT` exercises the product MySQL-to-MySQL snapshot and Binlog path. The JDBC/CDC/process-recovery suites remain required. Isolated database tests do not prove every JVM crash boundary or distributed exactly-once semantics.

Definitions missing the required Sink checkpoint XA transaction mode are rejected. An incompatible previous execution and checkpoint are never implicitly upgraded or reinterpreted. After checking target data, explicitly create a new definition and execution rather than reusing stale state.

Unsupported cases include target schema changes, Chunk Key changes across snapshot ranges, unverified full-row comparisons, multiple Sink writers, remote/rescaled transactions and unbounded duplicate-row buffering.
