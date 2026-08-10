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
    UNIQUE KEY uk_yak_dev_node_sibling (directory_id, name),
    KEY idx_yak_dev_node_type (type, update_time),
    KEY idx_yak_dev_node_project (project_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO yak_dev_node (
    id,
    name,
    type,
    project_id,
    directory_id,
    configured,
    deleted,
    create_time,
    update_time
)
SELECT
    id,
    name,
    'SQL',
    project_id,
    COALESCE(directory_id, 0),
    1,
    deleted,
    create_time,
    update_time
FROM yak_dev_sql_task;
