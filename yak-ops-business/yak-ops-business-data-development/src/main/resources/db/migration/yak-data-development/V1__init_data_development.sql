-- Data Development baseline schema.
-- Consolidated from historical V1-V7 migrations for fresh installations.
-- This file represents the final schema state after V7.

CREATE TABLE IF NOT EXISTS yak_dev_directory (
    id BIGINT NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(128) NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dev_directory_sibling (parent_id, name),
    KEY idx_yak_dev_directory_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dev_node (
    id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(32) NOT NULL,
    project_id BIGINT NULL,
    directory_id BIGINT NOT NULL DEFAULT 0,
    configured TINYINT(1) NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_yak_dev_node_directory (directory_id, name),
    KEY idx_yak_dev_node_type (type, update_time),
    KEY idx_yak_dev_node_project (project_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dev_task_draft (
    node_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    schema_version INT NOT NULL,
    content LONGTEXT NOT NULL,
    config_json LONGTEXT NOT NULL,
    draft_revision BIGINT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (node_id),
    KEY idx_yak_dev_task_draft_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dev_task_revision (
    id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    source_draft_revision BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    schema_version INT NOT NULL,
    content LONGTEXT NOT NULL,
    config_json LONGTEXT NOT NULL,
    checksum CHAR(64) NOT NULL,
    create_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dev_task_revision_node_no (node_id, revision_no),
    KEY idx_yak_dev_task_revision_node_time (node_id, create_time),
    KEY idx_yak_dev_task_revision_checksum (node_id, checksum)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
