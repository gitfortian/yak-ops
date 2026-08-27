-- Task Catalog is a project projection: project_id remains sourced from the publishing domain.
ALTER TABLE yak_task_asset
    ADD KEY idx_yak_task_asset_project_catalog (project_id, status, source, task_type, update_time);
