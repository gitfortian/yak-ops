-- Contract phase: operators must explicitly assign any historical Digital Screen rows.
-- Existing unowned rows intentionally make this migration fail instead of falling back globally.
ALTER TABLE yak_digital_screen
    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID';
