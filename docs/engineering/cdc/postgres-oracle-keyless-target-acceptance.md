# PostgreSQL and Oracle Exact-Row RETRACT

These dialects provide internal JDBC physical-row retraction, not an admitted keyless CDC product execution protocol.

| Target | One-row match | Nullable values |
| --- | --- | --- |
| PostgreSQL | subquery-selected `ctid` with `LIMIT 1 FOR UPDATE` | `IS NOT DISTINCT FROM` |
| Oracle | subquery-selected `ROWID` with `ROWNUM = 1` | `DECODE(column, ?, 1, 0) = 1` |

Physical row locators are used only within a DELETE statement and are never persisted as business row identity. Every DELETE must match exactly one physical row. Invalid row counts abort and roll back the transaction.

PostgreSQL verifies heap/RLS, triggers, rules, keylessness, matching decimal and binary types. Oracle verifies physical owner, NUMBER precision/scale, nullability, keylessness and excludes unverified RAW comparisons. Their integration tests cover duplicates, UPDATE, rollback and unsafe target rejection.

These direct row operations do not protect against replay after an independent Source checkpoint. Public keyless CDC remains limited to a checkpoint-coordinated MySQL XA Sink. PostgreSQL/Oracle admission requires a separately proven commit protocol.
