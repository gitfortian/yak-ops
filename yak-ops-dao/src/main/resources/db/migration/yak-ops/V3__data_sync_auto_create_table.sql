-- Yak Ops v1.2.0 Draft Migration
--
-- Auto Create Table Runtime
--
-- This migration is unpublished v1.2 development history. It may be squashed
-- into the final v1.2 Release Migration before Release Freeze.

ALTER TABLE yak_ops_data_sync_task
    ADD COLUMN auto_create_table TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '目标表不存在时是否允许按LogicalTable自动建表：0否，1是'
        AFTER target_table;
