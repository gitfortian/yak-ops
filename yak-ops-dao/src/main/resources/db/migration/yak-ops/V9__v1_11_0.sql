-- Yak Ops v1.11.0: metadata collection tasks.
--
-- A task is the durable configuration of a metadata collection: its datasource namespace, owner,
-- schedule and exclusion rules. Runs stay in yak_ops_data_map_crawl and now carry the owning task,
-- so this table owns configuration and lifecycle while run history remains execution state.

CREATE TABLE yak_ops_data_map_crawl_task (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated task identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the task',
    name VARCHAR(128) NOT NULL COMMENT 'Operator-visible task name',
    datasource_id VARCHAR(64) NOT NULL COMMENT 'Datasource the task collects metadata from',
    database_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Catalog slot collected by the task',
    schema_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Schema slot collected by the task',
    owner_id VARCHAR(64) NOT NULL COMMENT 'Workspace member accountable for the task',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Lifecycle: DRAFT, RUNNING or OFFLINE',
    schedule_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Trigger mode: MANUAL or CRON',
    cron_expression VARCHAR(128) NULL
        COMMENT 'Spring six-field Cron expression; null for MANUAL tasks',
    next_fire_time DATETIME(3) NULL
        COMMENT 'Next scheduled trigger; null while not scheduled',
    exclude_pattern VARCHAR(256) NULL
        COMMENT 'Java regex of table names kept alive but not re-crawled',
    exclude_tables TEXT NOT NULL
        COMMENT 'JSON array of table names kept alive but not re-crawled',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent configuration write timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_map_crawl_task_workspace (workspace_id, id),
    KEY idx_data_map_crawl_task_due (status, next_fire_time),
    KEY idx_data_map_crawl_task_datasource (workspace_id, datasource_id, create_time),
    CONSTRAINT fk_data_map_crawl_task_datasource FOREIGN KEY (datasource_id)
        REFERENCES yak_ops_data_source (id) ON DELETE CASCADE,
    CONSTRAINT ck_data_map_crawl_task_status CHECK (status IN ('DRAFT', 'RUNNING', 'OFFLINE')),
    CONSTRAINT ck_data_map_crawl_task_schedule CHECK (schedule_type IN ('MANUAL', 'CRON'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Workspace-scoped metadata collection task configurations';

ALTER TABLE yak_ops_data_map_crawl
    ADD COLUMN task_id VARCHAR(64) NULL
        COMMENT 'Task configuration that produced this run; null for legacy standalone runs'
        AFTER workspace_id,
    ADD COLUMN exclude_tables TEXT NULL
        COMMENT 'JSON array snapshot of excluded table names taken at submission'
        AFTER exclude_pattern,
    ADD KEY idx_data_map_crawl_task (workspace_id, task_id, created_time),
    ADD CONSTRAINT fk_data_map_crawl_task FOREIGN KEY (task_id)
        REFERENCES yak_ops_data_map_crawl_task (id) ON DELETE CASCADE;
