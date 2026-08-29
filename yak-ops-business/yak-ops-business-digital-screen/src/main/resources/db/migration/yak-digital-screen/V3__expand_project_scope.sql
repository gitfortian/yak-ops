-- Project Space expand migration for Digital Screen aggregate roots.
-- Published versions continue to inherit ownership through screen_id.
ALTER TABLE yak_digital_screen
    ADD COLUMN project_id BIGINT NULL COMMENT 'Yak Security Project ID' AFTER id,
    ADD KEY idx_yak_digital_screen_project_status_update (
        project_id, status, update_time, id
    );

-- Historical Digital Screen bindings are template-defined opaque JSON and do not provide a
-- trustworthy, universal owner. This migration intentionally performs no guessed backfill.
