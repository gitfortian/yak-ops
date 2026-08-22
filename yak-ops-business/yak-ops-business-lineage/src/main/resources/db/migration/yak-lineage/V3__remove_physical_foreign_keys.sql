-- Lineage graph relationships are intentionally logical so graph lifecycle stays business-controlled.
SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_metadata_asset'
      AND CONSTRAINT_NAME = 'fk_yak_metadata_asset_parent' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_metadata_asset DROP FOREIGN KEY fk_yak_metadata_asset_parent', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_metadata_relation'
      AND CONSTRAINT_NAME = 'fk_yak_metadata_relation_source' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_metadata_relation DROP FOREIGN KEY fk_yak_metadata_relation_source', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_metadata_relation'
      AND CONSTRAINT_NAME = 'fk_yak_metadata_relation_target' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_metadata_relation DROP FOREIGN KEY fk_yak_metadata_relation_target', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;
