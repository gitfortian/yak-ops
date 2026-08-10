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

ALTER TABLE yak_dev_sql_task
    ADD COLUMN directory_id BIGINT NULL AFTER project_id,
    ADD KEY idx_yak_dev_sql_task_directory (directory_id);
