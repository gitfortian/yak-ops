ALTER TABLE yak_dev_directory
    DROP INDEX uk_yak_dev_directory_sibling,
    DROP INDEX idx_yak_dev_directory_project_parent,
    DROP COLUMN project_id,
    ADD UNIQUE KEY uk_yak_dev_directory_sibling (parent_id, name),
    ADD KEY idx_yak_dev_directory_parent (parent_id);
