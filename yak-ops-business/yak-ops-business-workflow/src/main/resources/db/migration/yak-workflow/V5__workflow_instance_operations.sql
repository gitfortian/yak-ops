ALTER TABLE yak_workflow_backfill
    ADD COLUMN operation_type VARCHAR(32) NOT NULL DEFAULT 'BACKFILL' AFTER status,
    ADD COLUMN source_execution_id VARCHAR(80) NULL AFTER operation_type,
    ADD KEY idx_yak_workflow_backfill_operation (operation_type, create_time),
    ADD KEY idx_yak_workflow_backfill_source_execution (source_execution_id);
