-- Stage 2: separate editable draft pointer from reader-facing published pointer.
ALTER TABLE yak_dashboard
    ADD COLUMN published_version_id BIGINT NULL AFTER current_version_no,
    ADD COLUMN published_version_no INT NOT NULL DEFAULT 0 AFTER published_version_id,
    ADD COLUMN published_time DATETIME(6) NULL AFTER published_version_no,
    ADD KEY idx_yak_dashboard_published_version (published_version_id);

-- Existing Dashboard versions were previously both the editable and reader-facing state.
-- Backfill them as published so the migration does not silently unpublish existing assets.
UPDATE yak_dashboard
SET published_version_id = current_version_id,
    published_version_no = current_version_no,
    published_time = update_time
WHERE current_version_id IS NOT NULL
  AND published_version_id IS NULL;
