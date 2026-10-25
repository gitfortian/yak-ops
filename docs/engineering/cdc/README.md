# CDC Engineering Notes

This directory preserves CDC implementation analyses formerly stored as individual YOIP documents. They are **historical engineering notes, not independent improvement proposals or design votes**. Technical details describe their point in time; current source, tests and released user documentation are authoritative.

Formal decisions and statuses are indexed in the [YOIP records](../../yoips/README.md).

| Historical note | Legacy link | Original change |
| --- | --- | --- |
| [CDC Source Boundaries](cdc-source-boundaries.md) | [duplicate YOIP-4](../../yoips/YOIP-4-cdc-source-boundaries.md) | [source](https://github.com/weifuwan/yak-ops/commit/9b7c73e3f93977c7bc3f02353c1ecc623e5ee726) |
| [MySQL CDC Keyless Snapshot Reliability Baseline](mysql-cdc-keyless-snapshot-baseline.md) | [former YOIP-7](../../yoips/YOIP-7-mysql-cdc-keyless-snapshot-baseline.md) | [source](https://github.com/weifuwan/yak-ops/commit/fa8b0ed242d5b9b2189222cb24a167ae1d9b7da3) |
| [MySQL CDC Typed Snapshot Checkpoint Compatibility](mysql-typed-checkpoint-recovery.md) | [former YOIP-8](../../yoips/YOIP-8-mysql-typed-checkpoint-recovery.md) | [source](https://github.com/weifuwan/yak-ops/commit/2601ff597ecd8adc9131fce4052f09b185afb447) |
| [CDC Chunk Key and JDBC Row Identity Acceptance](cdc-row-identity-acceptance.md) | [former YOIP-9](../../yoips/YOIP-9-cdc-row-identity-acceptance.md) | [source](https://github.com/weifuwan/yak-ops/commit/0e30a3079c33ca632bf1d3a4bd3f926dd560f008) |
| [Keyless CDC Admission Contract](keyless-cdc-admission-contract.md) | [former YOIP-10](../../yoips/YOIP-10-keyless-cdc-admission-contract.md) | [source](https://github.com/weifuwan/yak-ops/pull/1726) |
| [Typed Snapshot Split Assignment and Recovery](keyless-typed-chunk-assignment.md) | [former YOIP-11](../../yoips/YOIP-11-keyless-typed-chunk-assignment.md) | [source](https://github.com/weifuwan/yak-ops/pull/1727) |
| [Typed Snapshot JDBC Reader and Recovery Boundary](keyless-typed-snapshot-reader.md) | [former YOIP-12](../../yoips/YOIP-12-keyless-typed-snapshot-reader.md) | [source](https://github.com/weifuwan/yak-ops/pull/1728) |
| [Keyless Snapshot Backfill Integration](keyless-snapshot-backfill-integration.md) | [former YOIP-13](../../yoips/YOIP-13-keyless-snapshot-backfill-integration.md) | [source](https://github.com/weifuwan/yak-ops/pull/1729) |
| [Keyless Binlog Handoff and Consistent Snapshot Boundaries](keyless-binlog-handoff.md) | [former YOIP-14](../../yoips/YOIP-14-keyless-binlog-handoff.md) | [source](https://github.com/weifuwan/yak-ops/pull/1730) |
| [CDC Change Identity and Runtime Delivery](cdc-change-identity-delivery.md) | [former YOIP-15](../../yoips/YOIP-15-cdc-change-identity-delivery.md) | [source](https://github.com/weifuwan/yak-ops/pull/1731) |
| [MySQL JDBC Exact-Row RETRACT](mysql-jdbc-exact-row-retract.md) | [former YOIP-16](../../yoips/YOIP-16-mysql-jdbc-exact-row-retract.md) | [source](https://github.com/weifuwan/yak-ops/pull/1732) |
| [MySQL Keyless CDC Transactional Replay Protection](mysql-keyless-transactional-replay.md) | [former YOIP-17](../../yoips/YOIP-17-mysql-keyless-transactional-replay.md) | [source](https://github.com/weifuwan/yak-ops/pull/1733) |
| [PostgreSQL and Oracle Keyless Target Acceptance](postgres-oracle-keyless-target-acceptance.md) | [former YOIP-18](../../yoips/YOIP-18-postgres-oracle-keyless-target-acceptance.md) | [source](https://github.com/weifuwan/yak-ops/pull/1734) |
| [Keyless Repeated-Row Snapshot Checkpoint Compaction](keyless-large-duplicate-groups.md) | [former YOIP-19](../../yoips/YOIP-19-keyless-large-duplicate-groups.md) | [source](https://github.com/weifuwan/yak-ops/pull/1735) |
