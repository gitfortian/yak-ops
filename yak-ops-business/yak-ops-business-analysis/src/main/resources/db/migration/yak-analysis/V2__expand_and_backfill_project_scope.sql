-- Project Space expand and deterministic backfill for Analysis roots.
-- Ownership is inherited only from the already-projectized Dataset source of truth.
ALTER TABLE yak_analysis
    ADD COLUMN project_id BIGINT NULL COMMENT 'Yak Security Project ID' AFTER id,
    ADD KEY idx_yak_analysis_project_update (project_id, update_time, id),
    ADD KEY idx_yak_analysis_project_dataset (project_id, dataset_id);

UPDATE yak_analysis a
JOIN yak_dataset d ON d.id = a.dataset_id
SET a.project_id = d.project_id
WHERE a.project_id IS NULL;
