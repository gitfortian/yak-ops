ALTER TABLE yak_ops_task_instance
    ADD COLUMN definition_name VARCHAR(128) NULL
    COMMENT 'Definition display name pinned at Task admission; nullable for historical runs'
    AFTER definition_version;

-- Existing runs predate the pinned-name column. Their original names cannot be
-- reconstructed after a rename; populate the best-known name for compatibility.
UPDATE yak_ops_task_instance AS instance
JOIN yak_ops_task_definition AS definition
  ON definition.id = instance.definition_id
 AND definition.workspace_id = instance.workspace_id
SET instance.definition_name = definition.name
WHERE instance.definition_name IS NULL;
