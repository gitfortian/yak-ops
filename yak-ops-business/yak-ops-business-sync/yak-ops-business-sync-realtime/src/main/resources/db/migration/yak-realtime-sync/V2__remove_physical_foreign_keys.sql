-- Realtime-sync references stay indexed while validation and delete semantics live in services/DAOs.
SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_realtime_job_definition'
      AND CONSTRAINT_NAME = 'fk_realtime_definition_environment' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_realtime_job_definition DROP FOREIGN KEY fk_realtime_definition_environment', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_realtime_job_deployment'
      AND CONSTRAINT_NAME = 'fk_realtime_deployment_definition' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_realtime_job_deployment DROP FOREIGN KEY fk_realtime_deployment_definition', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_realtime_job_event'
      AND CONSTRAINT_NAME = 'fk_realtime_event_definition' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_realtime_job_event DROP FOREIGN KEY fk_realtime_event_definition', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;
