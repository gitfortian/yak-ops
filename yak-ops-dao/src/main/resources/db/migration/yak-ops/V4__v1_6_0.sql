-- Yak Ops v1.6.0: physical publishing and metadata collection for logical models.
--
-- Additive only: four new tables, no column added to, removed from or rewritten on
-- `yak_ops_modeling_model` or `yak_ops_modeling_model_field`, so existing logical models are
-- untouched until a user publishes one.
--
-- Two naming notes, both deliberate:
--   * Audit columns are `created_time` / `create_by` rather than the `create_time` / `update_time`
--     pair of `BaseEntity`, because a publication and a metadata snapshot are append-only records:
--     they are written once and finished once, never updated in place.
--   * `database_name` / `schema_name` rather than `database` / `schema`, which are keywords on the
--     engines this metadata database may run on.

CREATE TABLE yak_ops_modeling_model_publication (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable publication identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the publication record',
    model_id VARCHAR(64) NULL COMMENT 'Published logical model; nulled when the model is deleted',
    datasource_id VARCHAR(64) NOT NULL COMMENT 'Datasource used to reach the physical target',
    datasource_revision BIGINT NOT NULL COMMENT 'Datasource revision pinned for this execution',
    physical_database VARCHAR(128) NOT NULL COMMENT 'Explicit target database chosen by the user',
    physical_schema VARCHAR(128) NULL COMMENT 'Explicit target schema; null where the engine has none',
    physical_table VARCHAR(128) NOT NULL COMMENT 'Physical table name: the model name, never renamed',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'PENDING, SUCCEEDED, FAILED or UNKNOWN',
    ddl MEDIUMTEXT NOT NULL COMMENT 'Generated DDL script attempted by this publication',
    error VARCHAR(1000) NULL COMMENT 'Failure or mismatch detail; null while pending and on success',
    created_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    finished_time DATETIME(3) NULL COMMENT 'Terminal state timestamp; null while pending',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated publisher identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_publication_workspace (workspace_id, id),
    KEY idx_modeling_publication_model (workspace_id, model_id, created_time),
    KEY idx_modeling_publication_target (workspace_id, datasource_id, physical_table),
    CONSTRAINT fk_modeling_publication_model FOREIGN KEY (model_id)
        REFERENCES yak_ops_modeling_model (id) ON DELETE SET NULL,
    CONSTRAINT ck_modeling_publication_status CHECK (
        status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    CONSTRAINT ck_modeling_publication_revision CHECK (datasource_revision > 0)
) COMMENT 'Immutable record of one physical publication attempt';

CREATE TABLE yak_ops_modeling_metadata_collection (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated collection identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the snapshot',
    publication_id VARCHAR(64) NOT NULL COMMENT 'Publication this snapshot describes',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SUCCEEDED or FAILED',
    table_count INT NOT NULL DEFAULT 0 COMMENT 'Collected tables; zero when the collection failed',
    field_count INT NOT NULL DEFAULT 0 COMMENT 'Collected columns across all tables',
    error VARCHAR(1000) NULL COMMENT 'Collection failure detail; null on success',
    created_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    finished_time DATETIME(3) NULL COMMENT 'Terminal state timestamp; null while running',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated collector identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_collection_workspace (workspace_id, id),
    KEY idx_modeling_collection_publication (workspace_id, publication_id, created_time),
    CONSTRAINT fk_modeling_collection_publication FOREIGN KEY (publication_id)
        REFERENCES yak_ops_modeling_model_publication (id) ON DELETE CASCADE,
    CONSTRAINT ck_modeling_collection_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_modeling_collection_counts CHECK (table_count >= 0 AND field_count >= 0)
) COMMENT 'One metadata collection run for a publication; refresh replaces it';

CREATE TABLE yak_ops_modeling_metadata_table (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated metadata table identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the snapshot',
    collection_id VARCHAR(64) NOT NULL COMMENT 'Collection run this row belongs to',
    datasource_id VARCHAR(64) NOT NULL COMMENT 'Datasource the table was read through',
    database_name VARCHAR(128) NOT NULL COMMENT 'Observed physical database',
    schema_name VARCHAR(128) NULL COMMENT 'Observed physical schema; null where the engine has none',
    name VARCHAR(128) NOT NULL COMMENT 'Observed physical table name',
    type VARCHAR(32) NULL COMMENT 'Observed table type reported by the driver',
    remarks VARCHAR(500) NULL COMMENT 'Observed table comment',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_metadata_table_workspace (workspace_id, id),
    KEY idx_modeling_metadata_table_collection (workspace_id, collection_id),
    CONSTRAINT fk_modeling_metadata_table_collection FOREIGN KEY (collection_id)
        REFERENCES yak_ops_modeling_metadata_collection (id) ON DELETE CASCADE
) COMMENT 'One collected physical table inside a metadata snapshot';

CREATE TABLE yak_ops_modeling_metadata_field (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated metadata column identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the snapshot',
    table_id VARCHAR(64) NOT NULL COMMENT 'Collected table this column belongs to',
    ordinal INT NOT NULL COMMENT 'One-based column order reported by the driver',
    name VARCHAR(128) NOT NULL COMMENT 'Observed physical column name',
    type_name VARCHAR(128) NOT NULL COMMENT 'Observed native type name',
    jdbc_type INT NOT NULL COMMENT 'Observed java.sql.Types value',
    size INT NULL COMMENT 'Observed column size, when the driver reports one',
    scale INT NULL COMMENT 'Observed decimal scale, when the driver reports one',
    nullable BIT(1) NOT NULL COMMENT 'Whether the column accepts nulls',
    primary_key BIT(1) NOT NULL COMMENT 'Whether the column participates in the primary key',
    primary_key_position INT NULL COMMENT 'Position inside the primary key; null when not a key column',
    remarks VARCHAR(500) NULL COMMENT 'Observed column comment',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_metadata_field_workspace (workspace_id, id),
    KEY idx_modeling_metadata_field_table (workspace_id, table_id, ordinal),
    CONSTRAINT fk_modeling_metadata_field_table FOREIGN KEY (table_id)
        REFERENCES yak_ops_modeling_metadata_table (id) ON DELETE CASCADE,
    CONSTRAINT ck_modeling_metadata_field_ordinal CHECK (ordinal > 0)
) COMMENT 'One collected physical column inside a metadata snapshot';
