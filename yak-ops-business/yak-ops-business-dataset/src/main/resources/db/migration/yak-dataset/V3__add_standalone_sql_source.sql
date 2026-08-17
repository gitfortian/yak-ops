-- Dataset nodes own their SQL directly instead of binding to published SQL TaskAssets.
ALTER TABLE yak_dataset_version
    ADD COLUMN data_source_id VARCHAR(128) NULL AFTER source_task_revision_no,
    ADD COLUMN sql_content LONGTEXT NULL AFTER data_source_id;

CREATE INDEX idx_yak_dataset_version_datasource
    ON yak_dataset_version (data_source_id);
