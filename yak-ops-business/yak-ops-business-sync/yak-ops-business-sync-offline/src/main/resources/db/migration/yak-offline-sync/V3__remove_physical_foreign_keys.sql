-- Offline-sync history is cleaned explicitly by the application when a definition is deleted.
SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_offline_job_execution'
      AND CONSTRAINT_NAME = 'fk_yak_offline_execution_definition' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_offline_job_execution DROP FOREIGN KEY fk_yak_offline_execution_definition', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_offline_execution_event'
      AND CONSTRAINT_NAME = 'fk_yak_offline_event_execution' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_offline_execution_event DROP FOREIGN KEY fk_yak_offline_event_execution', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;
