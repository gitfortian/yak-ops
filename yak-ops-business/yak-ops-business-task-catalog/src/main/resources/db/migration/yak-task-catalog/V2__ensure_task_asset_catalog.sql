-- Compatibility migration for environments that were previously baselined at V1.
-- The original V1 may have been skipped when the shared business schema was non-empty.

CREATE TABLE IF NOT EXISTS yak_task_asset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(32) NOT NULL,
    source_ref VARCHAR(128) NOT NULL,
    project_id BIGINT NULL,
    name VARCHAR(200) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_revision_id BIGINT NOT NULL,
    current_revision_no INT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_task_asset_source_ref (source, source_ref),
    KEY idx_yak_task_asset_catalog (status, source, task_type),
    KEY idx_yak_task_asset_project (project_id, status),
    KEY idx_yak_task_asset_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
