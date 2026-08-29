-- Project Space Stage 4: TaskAsset is a projection whose ownership comes from its source domain.
-- project_id remains nullable until all Task producers are migrated. project_scope_id keeps legacy
-- NULL projections globally unique while allowing the same source/source_ref identity per Project.

ALTER TABLE yak_task_asset
    ADD COLUMN project_scope_id BIGINT
        GENERATED ALWAYS AS (COALESCE(project_id, 0)) STORED
        COMMENT 'Transitional project identity key for nullable Project migration' AFTER project_id,
    DROP INDEX uk_yak_task_asset_source_ref,
    ADD UNIQUE KEY uk_yak_task_asset_project_source_ref
        (project_scope_id, source, source_ref);
