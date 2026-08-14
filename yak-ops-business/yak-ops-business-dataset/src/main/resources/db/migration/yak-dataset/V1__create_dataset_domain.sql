-- Stage 1 BI foundation: Dataset is a data contract, independent from executable TaskAsset state.
CREATE TABLE IF NOT EXISTS yak_dataset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    status VARCHAR(32) NOT NULL,
    current_version_id BIGINT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_yak_dataset_status_update (status, update_time),
    KEY idx_yak_dataset_current_version (current_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dataset_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_task_asset_id BIGINT NOT NULL,
    source_task_revision_id BIGINT NOT NULL,
    source_task_revision_no INT NOT NULL,
    schema_snapshot JSON NULL,
    create_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dataset_version_no (dataset_id, version_no),
    KEY idx_yak_dataset_version_source (source_task_asset_id, source_task_revision_id),
    CONSTRAINT fk_yak_dataset_version_dataset
        FOREIGN KEY (dataset_id) REFERENCES yak_dataset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dataset_field (
    field_id VARCHAR(64) NOT NULL,
    version_id BIGINT NOT NULL,
    physical_name VARCHAR(128) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    nullable TINYINT(1) NOT NULL DEFAULT 1,
    description VARCHAR(1000) NULL,
    default_role VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (version_id, field_id),
    UNIQUE KEY uk_yak_dataset_field_physical_name (version_id, physical_name),
    KEY idx_yak_dataset_field_role (version_id, default_role),
    CONSTRAINT fk_yak_dataset_field_version
        FOREIGN KEY (version_id) REFERENCES yak_dataset_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
