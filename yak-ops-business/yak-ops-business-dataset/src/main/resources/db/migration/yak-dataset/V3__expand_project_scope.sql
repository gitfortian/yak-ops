-- Project Space expand migration for Dataset aggregate roots.
-- DatasetVersion and DatasetField continue to inherit ownership through dataset_id.
ALTER TABLE yak_dataset
    ADD COLUMN project_id BIGINT NULL COMMENT 'Yak Security Project ID' AFTER id,
    ADD KEY idx_yak_dataset_project_status_update (project_id, status, update_time),
    ADD KEY idx_yak_dataset_project_development_node (project_id, development_node_id);
