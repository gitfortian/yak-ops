# v1.2 Column Mapping 手工 E2E

状态：已定义，未执行

本目录只验证 Column Mapping 在真实产品路径中的用户可见行为，不复制自动化数据库矩阵。

自动化证据负责：

```text
OfflineSyncJdbcAcceptanceIT
  → MySQL Source → MySQL / PostgreSQL / Oracle
  → 字段子集 + 重排 + 改名

MySqlCdcIntegrationIT
  → MySQL CDC → MySQL / PostgreSQL / Oracle
  → PK 改名 + Snapshot + INSERT / UPDATE / DELETE
```

手工场景选择 PostgreSQL 作为代表性异构目标，验证 UI 配置、后端 Preview、运行与最终数据库结果。

| ID | 场景 | 目的 |
| --- | --- | --- |
| MAPPING-001 | [离线 MySQL → PostgreSQL 字段映射](01-offline-mysql-to-postgresql.md) | 验证字段子集、重排、改名、自动建表与最终数据。 |
| MAPPING-002 | [实时 MySQL CDC → PostgreSQL 主键改名](02-realtime-mysql-cdc-to-postgresql.md) | 验证 PK 改名后 Snapshot、INSERT、UPDATE、DELETE。 |

说明：

- 本目录只定义可重复步骤，不记录 PASS。
- v1.2 Scope Freeze 已将 MAPPING-001 / MAPPING-002 纳入 Required Manual E2E；正式执行结果由后续 v1.2 Release Readiness / Evidence 记录。
- MAPPING-001 已补齐 DDL / Comment 与 AUTO Runtime 配置快照检查；MAPPING-002 已补齐 Auto Create、任务列表停止 / 再次启动与 CDC state 续传检查。用户主动 Stop 后按钮应回到“启动”；`desiredState=RUNNING` 但无 Active Execution 时的“重新启动”差异状态由自动 Contract / Acceptance 覆盖。
- 不声明 exactly-once。
