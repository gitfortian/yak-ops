-- Yak Ops v1.13.0 incremental schema.
-- Logical model publication closed loop: per-environment layer targets, engine-specific physical
-- table properties on the model, and execution evidence (operation, duration, log, diff) on every
-- publication record.

-- One physical target per layer per publication environment. The publish dialog resolves the
-- datasource and the target database from this binding, so the user picks an environment only.
CREATE TABLE yak_ops_modeling_layer_target (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the binding',
    layer_id VARCHAR(64) NOT NULL COMMENT 'Layer this target belongs to',
    environment VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'DEVELOP or PROD',
    data_source_id VARCHAR(64) NOT NULL COMMENT 'Datasource used to reach the target',
    physical_database VARCHAR(128) NOT NULL COMMENT 'Default target database resolved for publications',
    physical_schema VARCHAR(128) NULL COMMENT 'Default target schema; null where the engine has none',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Last update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Creator identity',
    update_by VARCHAR(64) NOT NULL COMMENT 'Last updater identity',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_layer_target (workspace_id, layer_id, environment),
    KEY idx_modeling_layer_target_layer (workspace_id, layer_id),
    CONSTRAINT fk_modeling_layer_target_layer FOREIGN KEY (layer_id)
        REFERENCES yak_ops_modeling_layer (id) ON DELETE CASCADE,
    CONSTRAINT fk_modeling_layer_target_datasource FOREIGN KEY (data_source_id)
        REFERENCES yak_ops_data_source (id) ON DELETE RESTRICT,
    CONSTRAINT ck_modeling_layer_target_environment CHECK (environment IN ('DEVELOP', 'PROD'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT 'Per-environment physical target bound to one warehouse layer';

-- Engine-specific physical table properties (Doris bucket/replica/data model today), stored as a
-- JSON object and validated against the target dialect at publication time.
ALTER TABLE yak_ops_modeling_model
    ADD COLUMN physical_properties VARCHAR(2000) NULL
        COMMENT 'Engine-specific physical table properties as a JSON object; null when unset'
        AFTER lifecycle_days;

-- Execution evidence for one publication attempt.
ALTER TABLE yak_ops_modeling_model_publication
    ADD COLUMN environment VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'DEVELOP or PROD; null for records written before the column existed' AFTER datasource_revision,
    ADD COLUMN operation VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'CREATE, ALTER or NONE; null for records written before the column existed' AFTER environment,
    ADD COLUMN duration_ms BIGINT NULL
        COMMENT 'Execution duration in milliseconds, observed read included' AFTER operation,
    ADD COLUMN execution_log MEDIUMTEXT NULL
        COMMENT 'Ordered execution log: statements, per-statement durations, stack traces' AFTER duration_ms,
    ADD COLUMN changes_json MEDIUMTEXT NULL
        COMMENT 'Schema diff snapshot serialized as JSON; empty list for a create' AFTER execution_log,
    ADD CONSTRAINT ck_modeling_publication_environment CHECK (
        environment IS NULL OR environment IN ('DEVELOP', 'PROD')),
    ADD CONSTRAINT ck_modeling_publication_operation CHECK (
        operation IS NULL OR operation IN ('CREATE', 'ALTER', 'NONE'));
