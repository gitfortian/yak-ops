-- SQL 专业配置暂时回退，数据开发当前仅保留目录与开发节点元数据。
-- 历史 V1~V5 不改写，避免已执行环境出现 Flyway checksum / missing migration 问题。

UPDATE yak_dev_node
SET configured = 0
WHERE type = 'SQL';

DROP TABLE IF EXISTS yak_dev_sql_task_execution;
DROP TABLE IF EXISTS yak_dev_sql_task_version;
DROP TABLE IF EXISTS yak_dev_sql_task;
