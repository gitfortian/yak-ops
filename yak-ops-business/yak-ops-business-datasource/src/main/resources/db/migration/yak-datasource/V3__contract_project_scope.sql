-- Project Space Stage 2.1 contract migration.
--
-- Deployment precondition: the Stage 2 application backfill must have completed successfully.
-- This migration intentionally performs no implicit backfill and never guesses a default Project ID.
-- If legacy NULL rows still exist, MySQL rejects the NOT NULL ALTER and the deployment stops.

ALTER TABLE yak_ops_data_source
    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID';

ALTER TABLE yak_ops_sql_execution
    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID';
