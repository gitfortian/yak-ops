-- Data Service Node authoring stays inside the Data Development domain.
-- Runtime deployment remains owned by the Data Service module.

CREATE TABLE IF NOT EXISTS yak_dev_data_service_draft (
    node_id BIGINT NOT NULL,
    definition_json LONGTEXT NOT NULL,
    draft_revision BIGINT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (node_id),
    KEY idx_yak_dev_data_service_draft_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dev_data_service_revision (
    id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    source_draft_revision BIGINT NOT NULL,
    definition_json LONGTEXT NOT NULL,
    checksum CHAR(64) NOT NULL,
    create_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dev_data_service_revision_node_no (node_id, revision_no),
    KEY idx_yak_dev_data_service_revision_node_time (node_id, create_time),
    KEY idx_yak_dev_data_service_revision_checksum (node_id, checksum)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
