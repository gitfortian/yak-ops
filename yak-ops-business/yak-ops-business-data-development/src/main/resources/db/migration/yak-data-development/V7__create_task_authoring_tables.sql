-- Stage 3: generic mutable drafts and immutable published revisions.
-- Task-specific content/config stays plugin-owned; data-development owns authoring lifecycle only.

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
