-- Dashboard relationships remain indexed logical references; lifecycle cleanup is application-owned.
SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_dashboard_version'
      AND CONSTRAINT_NAME = 'fk_yak_dashboard_version_dashboard' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_dashboard_version DROP FOREIGN KEY fk_yak_dashboard_version_dashboard', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_dashboard_widget'
      AND CONSTRAINT_NAME = 'fk_yak_dashboard_widget_version' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_dashboard_widget DROP FOREIGN KEY fk_yak_dashboard_widget_version', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_dashboard_widget'
      AND CONSTRAINT_NAME = 'fk_yak_dashboard_widget_analysis' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_dashboard_widget DROP FOREIGN KEY fk_yak_dashboard_widget_analysis', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_dashboard_filter'
      AND CONSTRAINT_NAME = 'fk_yak_dashboard_filter_version' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_dashboard_filter DROP FOREIGN KEY fk_yak_dashboard_filter_version', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_dashboard_filter_binding'
      AND CONSTRAINT_NAME = 'fk_yak_dashboard_filter_binding_filter' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_dashboard_filter_binding DROP FOREIGN KEY fk_yak_dashboard_filter_binding_filter', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_dashboard_filter_binding'
      AND CONSTRAINT_NAME = 'fk_yak_dashboard_filter_binding_widget' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_dashboard_filter_binding DROP FOREIGN KEY fk_yak_dashboard_filter_binding_widget', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_dashboard_interaction'
      AND CONSTRAINT_NAME = 'fk_yak_dashboard_interaction_widget' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_dashboard_interaction DROP FOREIGN KEY fk_yak_dashboard_interaction_widget', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'yak_dashboard_interaction'
      AND CONSTRAINT_NAME = 'fk_yak_dashboard_interaction_filter' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
), 'ALTER TABLE yak_dashboard_interaction DROP FOREIGN KEY fk_yak_dashboard_interaction_filter', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql; EXECUTE drop_fk_stmt; DEALLOCATE PREPARE drop_fk_stmt;
