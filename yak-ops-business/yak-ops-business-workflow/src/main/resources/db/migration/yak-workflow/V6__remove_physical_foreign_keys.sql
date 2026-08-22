-- Workflow schedule ownership is enforced by the application layer; trigger ledger remains historical.
SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_workflow_schedule'
      AND CONSTRAINT_NAME = 'fk_yak_workflow_schedule_workflow' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_workflow_schedule DROP FOREIGN KEY fk_yak_workflow_schedule_workflow', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;
