-- Lightweight canvas document for data-development DAG topology and layout.
CREATE TABLE IF NOT EXISTS yak_dev_graph (
    project_id BIGINT NOT NULL DEFAULT 0,
    graph_json LONGTEXT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
