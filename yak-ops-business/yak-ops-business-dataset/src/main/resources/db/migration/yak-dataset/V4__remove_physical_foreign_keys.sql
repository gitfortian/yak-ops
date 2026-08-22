-- Dataset identity/version relationships are maintained by the application layer.
SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_dataset_version'
      AND CONSTRAINT_NAME = 'fk_yak_dataset_version_dataset' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_dataset_version DROP FOREIGN KEY fk_yak_dataset_version_dataset', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_dataset_field'
      AND CONSTRAINT_NAME = 'fk_yak_dataset_field_version' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_dataset_field DROP FOREIGN KEY fk_yak_dataset_field_version', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;
