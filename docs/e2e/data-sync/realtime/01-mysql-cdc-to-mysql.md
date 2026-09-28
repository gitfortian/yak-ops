# REALTIME-001: MySQL CDC → MySQL

## Verification Goal

Verify the complete Yak Ops REALTIME synchronization Golden Path:

```text
MySQL Source
    ↓ initial snapshot + binlog
Yak Ops Realtime Sync
    ↓
YakFlow Local Execution Engine
    ↓
JdbcSink CHANGELOG
    ↓
MySQL Target
```

This case proves:

- A REALTIME Task can be configured from the UI.
- Initial snapshot rows reach the Target.
- Source INSERT is applied to the Target.
- Source UPDATE is applied to the Target.
- Source DELETE is applied to the Target.
- Instance event counters increase with CDC events.
- The running Task can be stopped from the product UI.

This case does not prove restart continuation or exactly-once semantics.

## Preconditions

- Yak Ops is running.
- A non-production MySQL CDC Source is available.
- A MySQL Target is available.
- Two Yak Ops Datasource resources are available:
  - Source bound to `yak_e2e_realtime_source`.
  - Target bound to `yak_e2e_realtime_target`.
- Both Datasources pass connection validation.
- The Source account has the privileges required by the MySQL CDC connector.
- The Source MySQL instance has binary logging enabled for row-based CDC.

Check the Source MySQL runtime:

```sql
SHOW VARIABLES LIKE 'log_bin';
SHOW VARIABLES LIKE 'binlog_format';
SHOW VARIABLES LIKE 'binlog_row_image';
```

Expected:

```text
log_bin         = ON
binlog_format   = ROW
binlog_row_image = FULL
```

If the environment uses different valid CDC settings, record that deviation before executing the case.

## 1. Prepare Source

Run on the Source MySQL server:

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_realtime_source;
USE yak_e2e_realtime_source;

DROP TABLE IF EXISTS e2e_realtime_user;

CREATE TABLE e2e_realtime_user (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO e2e_realtime_user (id, name, balance, updated_at) VALUES
(1, 'Alice', 100.50, '2026-09-28 11:00:00'),
(2, 'Bob',   200.00, '2026-09-28 11:01:00'),
(3, 'Carol', 300.75, '2026-09-28 11:02:00');

SELECT id, name, balance, updated_at
FROM e2e_realtime_user
ORDER BY id;
```

Expected Source rows:

| id | name | balance |
| ---: | --- | ---: |
| 1 | Alice | 100.50 |
| 2 | Bob | 200.00 |
| 3 | Carol | 300.75 |

The primary key is required by the current REALTIME product contract.

## 2. Prepare Target

Run on the Target MySQL server:

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_realtime_target;
USE yak_e2e_realtime_target;

DROP TABLE IF EXISTS e2e_realtime_user;

CREATE TABLE e2e_realtime_user (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);
```

Confirm that the Target is empty:

```sql
SELECT COUNT(*) AS row_count
FROM e2e_realtime_user;
```

Expected:

```text
0
```

## 3. Create the Realtime Sync Task

Open Yak Ops:

```text
数据集成
  ↓
实时同步
  ↓
新建任务
```

Configure:

### Basic Information

```text
Task Name: e2e_realtime_mysql_cdc
Sync Type: REALTIME
```

### Datasource

Source:

```text
Datasource: MySQL Datasource bound to yak_e2e_realtime_source
```

Target:

```text
Datasource: MySQL Datasource bound to yak_e2e_realtime_target
```

### Data Source

Select:

```text
Table: e2e_realtime_user
```

### Data Target

Select:

```text
Table: e2e_realtime_user
```

### Field Mapping

Confirm that the automatic mapping is compatible:

```text
id         → id
name       → name
balance    → balance
updated_at → updated_at
```

Do not continue if the mapping preview reports incompatibility or the Source primary key cannot be resolved.

### Runtime

Use the normal realtime defaults for:

- checkpoint interval.
- CDC queue capacity.
- poll batch size.
- JDBC write batch size.
- timeout.

This Golden Path verifies product behavior, not runtime tuning.

## 4. Start the Task and Verify Initial Snapshot

Click:

```text
保存并启动
```

Expected product behavior:

```text
Task saved
   ↓
Instance created
   ↓
PENDING
   ↓
RUNNING
```

REALTIME execution remains `RUNNING` until stopped or failed.

Wait until the initial snapshot is visible on the Target, then run:

```sql
USE yak_e2e_realtime_target;

SELECT id, name, balance, updated_at
FROM e2e_realtime_user
ORDER BY id;
```

Expected:

| id | name | balance |
| ---: | --- | ---: |
| 1 | Alice | 100.50 |
| 2 | Bob | 200.00 |
| 3 | Carol | 300.75 |

At this point the Instance should have observed three snapshot INSERT events:

```text
readRows = 3
writeRows = 3
```

Do not perform the next Source mutation until the snapshot result is confirmed.

## 5. Verify INSERT

Run on the Source:

```sql
USE yak_e2e_realtime_source;

INSERT INTO e2e_realtime_user (id, name, balance, updated_at)
VALUES (4, 'David', 400.25, '2026-09-28 11:03:00');
```

Wait for CDC propagation, then run on the Target:

```sql
USE yak_e2e_realtime_target;

SELECT id, name, balance, updated_at
FROM e2e_realtime_user
WHERE id = 4;
```

Expected:

| id | name | balance |
| ---: | --- | ---: |
| 4 | David | 400.25 |

Expected event counters after the INSERT is applied:

```text
readRows = 4
writeRows = 4
```

## 6. Verify UPDATE

Run on the Source:

```sql
USE yak_e2e_realtime_source;

UPDATE e2e_realtime_user
SET name = 'Bobby',
    balance = 250.00,
    updated_at = '2026-09-28 11:04:00'
WHERE id = 2;
```

Wait for CDC propagation, then run on the Target:

```sql
USE yak_e2e_realtime_target;

SELECT id, name, balance, updated_at
FROM e2e_realtime_user
WHERE id = 2;
```

Expected:

| id | name | balance |
| ---: | --- | ---: |
| 2 | Bobby | 250.00 |

The current YakFlow CDC protocol emits an UPDATE as two change events:

```text
UPDATE_BEFORE
UPDATE_AFTER
```

Expected counters after the UPDATE is fully applied:

```text
readRows = 6
writeRows = 6
```

The counters are change-event counts, not business-row counts.

## 7. Verify DELETE

Run on the Source:

```sql
USE yak_e2e_realtime_source;

DELETE FROM e2e_realtime_user
WHERE id = 1;
```

Wait for CDC propagation, then run on the Target:

```sql
USE yak_e2e_realtime_target;

SELECT COUNT(*) AS row_count
FROM e2e_realtime_user
WHERE id = 1;
```

Expected:

```text
0
```

Expected counters after the DELETE is applied:

```text
readRows = 7
writeRows = 7
```

## 8. Verify Final Target State

Run:

```sql
USE yak_e2e_realtime_target;

SELECT id, name, balance, updated_at
FROM e2e_realtime_user
ORDER BY id;
```

Expected final business rows:

| id | name | balance |
| ---: | --- | ---: |
| 2 | Bobby | 250.00 |
| 3 | Carol | 300.75 |
| 4 | David | 400.25 |

Verify the final count:

```sql
SELECT COUNT(*) AS row_count
FROM e2e_realtime_user;
```

Expected:

```text
3
```

## 9. Stop the Realtime Instance

From the running Instance page, click:

```text
停止
```

Expected:

```text
RUNNING
   ↓
CANCELED
```

The realtime UI may present `CANCELED` as `已停止`.

This step verifies user-controlled stop only. It does not verify offset reuse on the next run; that belongs to a separate continuation E2E case.

## 10. Acceptance Checklist

- [ ] Source MySQL CDC prerequisites are satisfied.
- [ ] Source contains exactly the three initial seed rows before Task start.
- [ ] Target is empty before Task start.
- [ ] REALTIME Task saves successfully.
- [ ] Instance reaches `RUNNING`.
- [ ] Initial snapshot creates Target rows `1 / 2 / 3`.
- [ ] Snapshot counters reach `3 / 3`.
- [ ] Source INSERT of row `4` appears on Target.
- [ ] Counters reach `4 / 4` after INSERT.
- [ ] Source UPDATE of row `2` becomes `Bobby / 250.00` on Target.
- [ ] Counters reach `6 / 6` after UPDATE.
- [ ] Source DELETE of row `1` removes row `1` from Target.
- [ ] Counters reach `7 / 7` after DELETE.
- [ ] Final Target contains exactly rows `2 / 3 / 4`.
- [ ] Stop transitions the active Instance to `CANCELED / 已停止`.

Any unchecked item means this E2E case has not passed.

## 11. Cleanup

Stop the REALTIME Instance before dropping tables.

Then run:

```sql
DROP TABLE IF EXISTS yak_e2e_realtime_source.e2e_realtime_user;
DROP TABLE IF EXISTS yak_e2e_realtime_target.e2e_realtime_user;
```

Delete the E2E Task from Yak Ops if it is no longer needed.

Do not delete product-owned realtime state directories manually as part of this Golden Path. State lifecycle and continuation are validated by dedicated realtime continuation cases.
