-- Yak Ops v1.2.0 Release Migration
--
-- Upgrade path:
--   v1.0.0 / V1__baseline.sql
--       ↓
--   v1.1.0 / V2__v1_1_0.sql
--       ↓
--   v1.2.0 / V3__v1_2_0.sql
--
-- This release migration squashes the unpublished v1.2.0 draft migrations
-- previously developed as V3 and V4. SQL order and semantics are preserved.

-- ----------------------------------------------------------------
-- Data Sync Auto Create Table
-- ----------------------------------------------------------------

ALTER TABLE yak_ops_data_sync_task
    ADD COLUMN auto_create_table TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '目标表不存在时是否允许按LogicalTable自动建表：0否，1是'
        AFTER target_table;

-- ----------------------------------------------------------------
-- Data Sync Column Mapping
-- ----------------------------------------------------------------

ALTER TABLE yak_ops_data_sync_task
    ADD COLUMN mapping_config LONGTEXT NULL
        COMMENT '任务级字段映射JSON；NULL表示使用大小写不敏感同名映射'
        AFTER auto_create_table;
