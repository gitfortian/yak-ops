-- Stage 6.1 Project Space contract for Dataset-owned project columns.
-- Historical rows must have completed the Stage 6 ApplicationReady compatibility backfill first.
-- This migration performs no implicit backfill and intentionally fails fast if NULL ownership remains.
ALTER TABLE yak_dataset
    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID';

ALTER TABLE yak_dataset_query_performance
    MODIFY COLUMN project_id BIGINT NOT NULL;
