-- Project Space expand migration for resource nodes.
-- Every node owns project_id because tree, path, download and delete queries run independently.
ALTER TABLE yak_ops_resource
    ADD COLUMN project_id BIGINT NULL COMMENT 'Yak Security Project ID' AFTER id,
    DROP INDEX uk_yak_resource_parent_name,
    ADD UNIQUE KEY uk_yak_resource_project_parent_name (project_id, parent_id, name),
    ADD KEY idx_yak_resource_project_path (project_id, full_path(255)),
    ADD KEY idx_yak_resource_project_parent_type (project_id, parent_id, node_type);
