# OFFLINE-001: MySQL → MySQL / APPEND

## Verification Goal

Verify the complete Yak Ops OFFLINE synchronization Golden Path:

```text
MySQL Source
    ↓
Yak Ops Offline Sync
    ↓
YakFlow Local Execution Engine
    ↓
MySQL Target
```

This case proves:

- Source / Target Datasource selection works.
- Catalog table discovery and automatic same-name field mapping work.
- A saved OFFLINE Task can be started from the UI.
- APPEND preserves existing Target rows.
- The Instance reaches `SUCCEEDED`.
- Persisted `readRows` / `writeRows` match the transferred Source rows.
- Target business data is correct.

## Preconditions

- Yak Ops is running.
- A non-production MySQL environment is available.
- Two Yak Ops Datasource resources are available:
  - one bound to `yak_e2e_source`.
  - one bound to `yak_e2e_target`.
- Both Datasources pass connection validation.

The two Datasources may point to the same MySQL server, but they must use different databases for this case.

## 1. Prepare Source

Run on the MySQL server:

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_source;
USE yak_e2e_source;

DROP TABLE IF EXISTS e2e_offline_user_append;

CREATE TABLE e2e_offline_user_append (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO e2e_offline_user_append (id, name, balance, created_at) VALUES
(1, 'Alice', 100.50, '2026-09-28 10:00:00'),
(2, 'Bob',   200.00, '2026-09-28 10:01:00'),
(3, 'Carol', 300.75, '2026-09-28 10:02:00');

SELECT id, name, balance, created_at
FROM e2e_offline_user_append
ORDER BY id;
```

Expected Source rows:

| id | name | balance |
| ---: | --- | ---: |
| 1 | Alice | 100.50 |
| 2 | Bob | 200.00 |
| 3 | Carol | 300.75 |

Expected Source count:

```text
3
```

## 2. Prepare Target

Run on the MySQL server:

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_target;
USE yak_e2e_target;

DROP TABLE IF EXISTS e2e_offline_user_append;

CREATE TABLE e2e_offline_user_append (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO e2e_offline_user_append (id, name, balance, created_at) VALUES
(100, 'Existing', 999.99, '2026-09-28 09:00:00');

SELECT id, name, balance, created_at
FROM e2e_offline_user_append
ORDER BY id;
```

Expected Target rows before synchronization:

| id | name | balance |
| ---: | --- | ---: |
| 100 | Existing | 999.99 |

The `id = 100` row is deliberate. It proves APPEND does not clear existing Target data.

## 3. Create the Offline Sync Task

Open Yak Ops:

```text
数据集成
  ↓
离线同步
  ↓
新建任务
```

Configure:

### Basic Information

```text
Task Name: e2e_offline_mysql_append
Sync Type: OFFLINE
```

### Datasource

Source:

```text
Datasource: MySQL Datasource bound to yak_e2e_source
```

Target:

```text
Datasource: MySQL Datasource bound to yak_e2e_target
```

### Data Source

Select:

```text
Table: e2e_offline_user_append
```

### Data Target

Select:

```text
Table: e2e_offline_user_append
Write Mode: APPEND
```

### Field Mapping

Confirm that the automatic mapping is compatible for all fields:

```text
id         → id
name       → name
balance    → balance
created_at → created_at
```

Do not continue if the mapping preview reports incompatibility.

### Runtime

Use the normal defaults for the Golden Path.

Keep:

```text
sourceParallelism = 1
splitSize = empty / disabled
```

This case verifies the basic product path, not split / parallel Reader behavior.

## 4. Start the Task

Click:

```text
保存并运行
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
   ↓
SUCCEEDED
```

`PENDING` and `RUNNING` may be short-lived for this small data set.

Open the Instance detail and verify the final state:

```text
Status: SUCCEEDED
readRows: 3
writeRows: 3
```

## 5. Verify Target Result

Run:

```sql
USE yak_e2e_target;

SELECT id, name, balance, created_at
FROM e2e_offline_user_append
ORDER BY id;
```

Expected result:

| id | name | balance |
| ---: | --- | ---: |
| 1 | Alice | 100.50 |
| 2 | Bob | 200.00 |
| 3 | Carol | 300.75 |
| 100 | Existing | 999.99 |

Verify the count:

```sql
SELECT COUNT(*) AS row_count
FROM e2e_offline_user_append;
```

Expected:

```text
4
```

Verify Source rows and Target rows match for the synchronized IDs:

```sql
SELECT id, name, balance, created_at
FROM e2e_offline_user_append
WHERE id IN (1, 2, 3)
ORDER BY id;
```

Expected:

```text
All three Source rows are present with the same field values.
```

## 6. Acceptance Checklist

- [ ] Source table contains exactly 3 seed rows before execution.
- [ ] Target table contains the existing `id = 100` row before execution.
- [ ] Source / Target Datasources and tables can be selected from the UI.
- [ ] Automatic field mapping is compatible.
- [ ] Task saves successfully.
- [ ] Manual execution creates an Instance.
- [ ] Instance finishes as `SUCCEEDED`.
- [ ] Final `readRows = 3`.
- [ ] Final `writeRows = 3`.
- [ ] Target contains Source rows `1 / 2 / 3`.
- [ ] Target still contains existing row `100`.
- [ ] Target row count is exactly `4`.

Any unchecked item means this E2E case has not passed.

## 7. Cleanup

After verification:

```sql
DROP TABLE IF EXISTS yak_e2e_source.e2e_offline_user_append;
DROP TABLE IF EXISTS yak_e2e_target.e2e_offline_user_append;
```

Delete the E2E Task from Yak Ops if it is no longer needed.

Historical Instance records may remain according to the Data Sync product lifecycle contract.
