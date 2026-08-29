-- Project Space Stage 3.1.1 contract migration.
-- Stage 3.1 ApplicationReady backfill must have completed before this migration reaches a legacy database.
-- This migration intentionally performs no implicit backfill: remaining NULL rows must fail fast.

ALTER TABLE yak_ops_resource
    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID';
