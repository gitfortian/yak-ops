-- Dimension modeling foundations after the v1.4.0 release migration.
-- v1.5.0 owns this forward-only migration; data marts and subjects ship together with
-- the concept dimensions and logical models that reference them.

-- The planning code registry stays the single global namespace; data marts and subjects
-- reserve their abbreviations there exactly like categories, domains and processes.
ALTER TABLE yak_ops_modeling_code DROP CHECK ck_modeling_code_kind;
ALTER TABLE yak_ops_modeling_code
    MODIFY node_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Immutable CATEGORY, DOMAIN, PROCESS, DATASET or SUBJECT level';
ALTER TABLE yak_ops_modeling_code
    ADD CONSTRAINT ck_modeling_code_kind
        CHECK (node_kind IN ('CATEGORY', 'DOMAIN', 'PROCESS', 'DATASET', 'SUBJECT'));

CREATE TABLE yak_ops_modeling_datamart (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable data mart identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the data mart and its subjects',
    name VARCHAR(128) NOT NULL COMMENT 'Business display name',
    en_name VARCHAR(128) NOT NULL COMMENT 'Business English name shown beside the reserved abbreviation',
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Case-sensitive abbreviation reserved in the global planning namespace',
    owner_id VARCHAR(64) NOT NULL COMMENT 'Assigned owner identity; retained after membership ends',
    description VARCHAR(500) NULL COMMENT 'Optional business description; null when absent',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic version incremented after every update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent field update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_datamart_workspace (workspace_id, id),
    UNIQUE KEY uk_modeling_datamart_code (workspace_id, code),
    CONSTRAINT ck_modeling_datamart_revision CHECK (revision > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspace-owned data mart grouping application-layer subjects';

CREATE TABLE yak_ops_modeling_subject (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable subject identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the subject and its data mart reference',
    datamart_id VARCHAR(64) NOT NULL COMMENT 'Immutable owning data mart in the same Workspace',
    name VARCHAR(128) NOT NULL COMMENT 'Business display name',
    en_name VARCHAR(128) NOT NULL COMMENT 'Business English name shown beside the reserved abbreviation',
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Case-sensitive abbreviation reserved in the global planning namespace',
    description VARCHAR(500) NULL COMMENT 'Optional business description; null when absent',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic version incremented after every update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent field update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_subject_workspace (workspace_id, id),
    UNIQUE KEY uk_modeling_subject_code (workspace_id, datamart_id, code),
    KEY idx_modeling_subject_parent (workspace_id, datamart_id),
    CONSTRAINT ck_modeling_subject_revision CHECK (revision > 0),
    CONSTRAINT fk_modeling_subject_parent FOREIGN KEY (workspace_id, datamart_id)
        REFERENCES yak_ops_modeling_datamart (workspace_id, id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspace-owned subject area inside one data mart';
