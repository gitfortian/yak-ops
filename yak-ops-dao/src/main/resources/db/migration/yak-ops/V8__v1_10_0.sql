-- Yak Ops v1.10.0 incremental schema.
-- Metadata crawl upgrade: trigger provenance, an exclude pattern with its skip counter, the
-- retry lineage, and a distinct INTERRUPTED state for runs swept after an application restart.

ALTER TABLE yak_ops_data_map_crawl
    ADD COLUMN trigger_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'How the run was requested: MANUAL or SCHEDULED' AFTER failed_count,
    ADD COLUMN exclude_pattern VARCHAR(256) NULL
        COMMENT 'Java regex; matching tables are kept alive but not re-crawled' AFTER trigger_type,
    ADD COLUMN filtered_count INT NOT NULL DEFAULT 0
        COMMENT 'Tables skipped by the exclude pattern yet confirmed present' AFTER exclude_pattern,
    ADD COLUMN retry_of VARCHAR(64) NULL
        COMMENT 'Crawl whose failed tables this run re-attempts' AFTER filtered_count;

ALTER TABLE yak_ops_data_map_crawl DROP CHECK ck_data_map_crawl_status;

ALTER TABLE yak_ops_data_map_crawl ADD CONSTRAINT ck_data_map_crawl_status
    CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'INTERRUPTED'));
