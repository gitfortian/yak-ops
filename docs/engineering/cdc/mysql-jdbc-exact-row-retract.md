# JDBC Exact-Row RETRACT

An exact-row DELETE matches every projected physical before-image column. It removes exactly one occurrence even when identical duplicates exist. MySQL uses null-safe `<=>` and `LIMIT 1`; PostgreSQL and Oracle use their own single-row selection and null-safe comparison. Each DELETE must affect exactly one row or its containing transaction fails.

A complete UPDATE issues DELETE(before) and INSERT(after) within the same transaction. Incomplete, interleaved or mismatched CDC UPDATE images are rejected. Vendor target validation checks physical types, column order, uniqueness and unsafe side effects.

These internal JDBC row operations alone do **not** make Source-offset replay exactly once. Product keyless CDC uses the MySQL XA Sink with Checkpoint coordination. PostgreSQL/Oracle exact-row operations remain internal JDBC capabilities, not admitted keyless CDC product paths. Do not expose them as checkpoint-safe Sink protocols without separate recovery acceptance.
