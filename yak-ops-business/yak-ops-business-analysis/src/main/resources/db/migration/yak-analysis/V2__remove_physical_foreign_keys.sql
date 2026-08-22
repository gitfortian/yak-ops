-- Yak Ops uses logical references at the application layer instead of database FK constraints.
SET @drop_fk_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = 'yak_analysis'
          AND CONSTRAINT_NAME = 'fk_yak_analysis_dataset'
          AND CONSTRAINT_TYPE = 'FOREIGN KEY'
    ),
    'ALTER TABLE yak_analysis DROP FOREIGN KEY fk_yak_analysis_dataset',
    'SELECT 1'
);
PREPARE drop_fk_stmt FROM @drop_fk_sql;
EXECUTE drop_fk_stmt;
DEALLOCATE PREPARE drop_fk_stmt;
