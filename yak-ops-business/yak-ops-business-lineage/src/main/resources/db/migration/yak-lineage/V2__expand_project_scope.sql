-- Project Space Stage 4 expand migration for Lineage projections.
-- Project ownership is propagated from producer/source truth, so project_id remains nullable until
-- Data Development / Dataset / Sync / Workflow producers have completed their own migration.
-- project_scope_id is a transitional database-only key: NULL project rows share scope 0, preserving
-- the old global asset identity while project-aware rows can reuse the same asset_key per Project.

ALTER TABLE yak_metadata_asset
    ADD COLUMN project_id BIGINT NULL COMMENT 'Yak Security Project ID from source truth' AFTER id,
    ADD COLUMN project_scope_id BIGINT
        GENERATED ALWAYS AS (COALESCE(project_id, 0)) STORED
        COMMENT 'Transitional project identity key for nullable Project migration' AFTER project_id,
    DROP INDEX uk_yak_metadata_asset_key,
    ADD UNIQUE KEY uk_yak_metadata_asset_project_key (project_scope_id, asset_key),
    ADD KEY idx_yak_metadata_asset_project_type (project_id, asset_type, update_time),
    ADD KEY idx_yak_metadata_asset_project_source (project_id, source_type, source_id);

ALTER TABLE yak_metadata_relation
    ADD COLUMN project_id BIGINT NULL COMMENT 'Project ID shared by source/target assets' AFTER id,
    ADD KEY idx_yak_metadata_relation_project_source (project_id, source_asset_id, relation_type),
    ADD KEY idx_yak_metadata_relation_project_target (project_id, target_asset_id, relation_type),
    ADD KEY idx_yak_metadata_relation_project_evidence (project_id, source_type, source_id);
