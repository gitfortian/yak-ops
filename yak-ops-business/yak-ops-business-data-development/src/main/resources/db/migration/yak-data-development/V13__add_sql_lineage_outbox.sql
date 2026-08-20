CREATE TABLE IF NOT EXISTS yak_dev_lineage_outbox (
    task_id CHAR(36) NOT NULL,
    node_id BIGINT NOT NULL,
    revision_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_time DATETIME(6) NOT NULL,
    last_error VARCHAR(2000) NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_yak_dev_lineage_outbox_revision (node_id, revision_id),
    KEY idx_yak_dev_lineage_outbox_due (status, next_attempt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
