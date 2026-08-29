-- Stage 5B Project Space contract for Data Development.
--
-- Historical databases must have already completed the existing ApplicationReady compatibility
-- backfill before this migration is deployed. This migration deliberately performs no ownership
-- inference and must fail fast when legacy NULL project rows still exist.

ALTER TABLE yak_dev_directory
    MODIFY COLUMN project_id BIGINT NOT NULL;

ALTER TABLE yak_dev_node
    MODIFY COLUMN project_id BIGINT NOT NULL;

ALTER TABLE yak_dev_task_execution
    MODIFY COLUMN project_id BIGINT NOT NULL;

ALTER TABLE yak_dev_lineage_outbox
    MODIFY COLUMN project_id BIGINT NOT NULL;
