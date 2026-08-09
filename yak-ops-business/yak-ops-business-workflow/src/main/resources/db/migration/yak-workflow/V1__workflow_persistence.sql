CREATE TABLE IF NOT EXISTS yak_workflow_definition (
    id VARCHAR(80) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(32) NOT NULL,
    draft_revision BIGINT NOT NULL DEFAULT 1,
    latest_version_no INT NOT NULL DEFAULT 0,
    active_version_id VARCHAR(80) NULL,
    draft_json LONGTEXT NOT NULL,
    latest_execution_id VARCHAR(80) NULL,
    latest_execution_status VARCHAR(32) NULL,
    create_time DATETIME(3) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_yak_workflow_definition_status (status),
    KEY idx_yak_workflow_definition_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_workflow_version (
    id VARCHAR(80) NOT NULL,
    workflow_id VARCHAR(80) NULL,
    version_no INT NULL,
    version_kind VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    draft_revision BIGINT NULL,
    run_request_json LONGTEXT NULL,
    editor_meta_json LONGTEXT NULL,
    task_versions_json LONGTEXT NULL,
    engine_definition_json LONGTEXT NULL,
    create_time DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_workflow_version_no (workflow_id, version_no),
    KEY idx_yak_workflow_version_workflow (workflow_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_workflow_execution (
    id VARCHAR(80) NOT NULL,
    definition_id VARCHAR(80) NOT NULL,
    source_execution_id VARCHAR(80) NULL,
    status VARCHAR(32) NOT NULL,
    input_json LONGTEXT NOT NULL,
    scheduling_stopped TINYINT(1) NOT NULL DEFAULT 0,
    run_started_at DATETIME(3) NULL,
    paused_at DATETIME(3) NULL,
    paused_duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    ended_at DATETIME(3) NULL,
    workflow_name VARCHAR(200) NULL,
    workflow_version_id VARCHAR(80) NULL,
    workflow_version_no INT NULL,
    test_run TINYINT(1) NOT NULL DEFAULT 0,
    edge_count INT NOT NULL DEFAULT 0,
    workflow_timeout_seconds BIGINT NOT NULL DEFAULT 0,
    failure_strategy VARCHAR(64) NULL,
    runtime_metadata_json LONGTEXT NULL,
    PRIMARY KEY (id),
    KEY idx_yak_workflow_execution_status (status, updated_at),
    KEY idx_yak_workflow_execution_version (workflow_version_id),
    KEY idx_yak_workflow_execution_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_workflow_node_execution (
    id VARCHAR(80) NOT NULL,
    workflow_execution_id VARCHAR(80) NOT NULL,
    node_id VARCHAR(120) NOT NULL,
    failure_policy VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    output_json LONGTEXT NOT NULL,
    error_message TEXT NULL,
    failure_handled TINYINT(1) NOT NULL DEFAULT 0,
    downstream_continuation_allowed TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_workflow_node_execution (workflow_execution_id, node_id),
    KEY idx_yak_workflow_node_status (workflow_execution_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_workflow_node_attempt (
    id VARCHAR(80) NOT NULL,
    node_execution_id VARCHAR(80) NOT NULL,
    workflow_execution_id VARCHAR(80) NOT NULL,
    node_id VARCHAR(120) NOT NULL,
    attempt_no INT NOT NULL,
    available_at DATETIME(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    resume_target_status VARCHAR(32) NULL,
    started_at DATETIME(3) NULL,
    paused_at DATETIME(3) NULL,
    paused_duration_ms BIGINT NOT NULL DEFAULT 0,
    ended_at DATETIME(3) NULL,
    error_message TEXT NULL,
    failure_reason VARCHAR(64) NULL,
    external_execution_id VARCHAR(120) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_workflow_attempt_no (node_execution_id, attempt_no),
    KEY idx_yak_workflow_attempt_workflow (workflow_execution_id, status),
    KEY idx_yak_workflow_attempt_external (external_execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
