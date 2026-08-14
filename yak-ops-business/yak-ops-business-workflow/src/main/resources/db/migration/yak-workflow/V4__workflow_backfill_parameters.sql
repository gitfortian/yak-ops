CREATE TABLE IF NOT EXISTS yak_workflow_backfill (
    id VARCHAR(80) NOT NULL,
    workflow_id VARCHAR(80) NOT NULL,
    workflow_version_id VARCHAR(80) NOT NULL,
    workflow_version_no INT NOT NULL,
    schedule_id VARCHAR(80) NOT NULL,
    schedule_name VARCHAR(100) NOT NULL,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL,
    start_business_date DATE NOT NULL,
    end_business_date DATE NOT NULL,
    cron_expression VARCHAR(160) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    execution_strategy VARCHAR(32) NOT NULL,
    schedule_input_json TEXT NULL,
    input_json TEXT NULL,
    total_count INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_yak_workflow_backfill_workflow (workflow_id, create_time),
    KEY idx_yak_workflow_backfill_schedule (schedule_id, create_time),
    KEY idx_yak_workflow_backfill_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE yak_workflow_schedule_trigger
    ADD COLUMN backfill_id VARCHAR(80) NULL AFTER workflow_id,
    ADD COLUMN dedupe_key VARCHAR(320) NULL AFTER trigger_id,
    ADD COLUMN business_date DATE NULL AFTER actual_fire_time;

-- Stage 4 的 schedule + plannedFireTime 唯一键只能表达“正常计划幂等”，
-- Stage 5 需要允许不同 Backfill 批次对同一个逻辑时间重新执行。
-- 先为历史 Ledger 生成与 Java 侧完全一致的 SCHEDULE dedupe key，再替换唯一索引。
UPDATE yak_workflow_schedule_trigger
SET dedupe_key = CONCAT(
    schedule_id,
    '|SCHEDULE|',
    CAST(ROUND(UNIX_TIMESTAMP(planned_fire_time) * 1000) AS CHAR))
WHERE dedupe_key IS NULL;

ALTER TABLE yak_workflow_schedule_trigger
    MODIFY COLUMN dedupe_key VARCHAR(320) NOT NULL,
    DROP INDEX uk_yak_workflow_schedule_trigger_plan,
    ADD UNIQUE KEY uk_yak_workflow_schedule_trigger_dedupe (dedupe_key),
    ADD KEY idx_yak_workflow_schedule_trigger_backfill (backfill_id, status, planned_fire_time),
    ADD KEY idx_yak_workflow_schedule_trigger_business_date (workflow_id, business_date);
