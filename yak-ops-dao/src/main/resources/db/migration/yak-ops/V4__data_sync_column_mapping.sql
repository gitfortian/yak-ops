-- Yak Ops v1.2.0 Draft Migration
--
-- Data Sync Column Mapping Contract + Persistence
--
-- This migration is unpublished v1.2 development history. It may be squashed
-- into the final v1.2 Release Migration before Release Freeze.

ALTER TABLE yak_ops_data_sync_task
    ADD COLUMN mapping_config LONGTEXT NULL
        COMMENT '任务级字段映射JSON；NULL表示使用大小写不敏感同名映射'
        AFTER auto_create_table;
