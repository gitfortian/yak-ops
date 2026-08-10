CREATE TABLE IF NOT EXISTS yak_dev_sql_task (
    id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    data_source_id BIGINT NOT NULL,
    sql_text LONGTEXT NOT NULL,
    parameter_json LONGTEXT NOT NULL,
    draft_revision BIGINT NOT NULL DEFAULT 1,
    published_version_id BIGINT NULL,
    latest_version_no INT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_yak_dev_sql_task_datasource (data_source_id),
    KEY idx_yak_dev_sql_task_updated (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dev_sql_task_version (
    id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    data_source_id BIGINT NOT NULL,
    sql_snapshot LONGTEXT NOT NULL,
    parameter_snapshot_json LONGTEXT NOT NULL,
    content_digest VARCHAR(64) NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dev_sql_version (task_id, version_no),
    KEY idx_yak_dev_sql_version_datasource (data_source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dev_sql_task_execution (
    id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    task_version_id BIGINT NULL,
    task_version_no INT NULL,
    data_source_id BIGINT NOT NULL,
    sql_snapshot LONGTEXT NOT NULL,
    input_json LONGTEXT NOT NULL,
    idempotency_key VARCHAR(160) NULL,
    status VARCHAR(32) NOT NULL,
    affected_rows BIGINT NOT NULL DEFAULT 0,
    output_json LONGTEXT NULL,
    error_message TEXT NULL,
    create_time DATETIME(6) NOT NULL,
    start_time DATETIME(6) NULL,
    finish_time DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dev_sql_execution_idempotency (idempotency_key),
    KEY idx_yak_dev_sql_execution_task (task_id, create_time),
    KEY idx_yak_dev_sql_execution_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
