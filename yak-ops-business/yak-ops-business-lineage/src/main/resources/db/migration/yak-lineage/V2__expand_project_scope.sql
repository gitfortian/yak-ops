-- Project Space Stage 4 expand migration for Lineage projections.
-- Project ownership is propagated from producer/source truth, so columns remain nullable until
-- Data Development / Dataset / Sync / Workflow producers have completed their own migration.

ALTER TABLE yak_metadata_asset
    ADD COLUMN project_id BIGINT NULL COMMENT 'Yak Security Project ID from source truth' AFTER id,
    DROP INDEX uk_yak_metadata_asset_key,
    ADD UNIQUE KEY uk_yak_metadata_asset_project_key (project_id, asset_key),
    ADD KEY idx_yak_metadata_asset_project_type (project_id, asset_type, update_time),
    ADD KEY idx_yak_metadata_asset_project_source (project_id, source_type, source_id);

ALTER TABLE yak_metadata_relation
    ADD COLUMN project_id BIGINT NULL COMMENT 'Project ID shared by source/target assets' AFTER id,
    ADD KEY idx_yak_metadata_relation_project_source (project_id, source_asset_id, relation_type),
    ADD KEY idx_yak_metadata_relation_project_target (project_id, target_asset_id, relation_type),
    ADD KEY idx_yak_metadata_relation_project_evidence (project_id, source_type, source_id);
