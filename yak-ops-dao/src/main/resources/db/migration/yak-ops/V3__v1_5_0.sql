CREATE TABLE yak_ops_standard_catalog (
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the standard catalog and its write lock',
    initialized_at DATETIME(3) NULL COMMENT 'Committed default-directory initialization time; null before initialization',
    create_time DATETIME(3) NOT NULL COMMENT 'Catalog creation timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated initializing user identity',
    PRIMARY KEY (workspace_id),
    CONSTRAINT fk_standard_catalog_workspace FOREIGN KEY (workspace_id)
        REFERENCES yak_ops_workspace (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='One-time standard initialization and per-Workspace transaction lock';

CREATE TABLE yak_ops_standard_category (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable category identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this category and its parent',
    parent_id VARCHAR(64) NULL COMMENT 'Immutable parent in the same Workspace; null identifies a root',
    name VARCHAR(128) NOT NULL COMMENT 'Business directory display name',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic revision advanced after renames',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent accepted update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_standard_category_workspace (workspace_id,id),
    KEY idx_standard_category_parent (workspace_id,parent_id),
    CONSTRAINT ck_standard_category_revision CHECK (revision>0),
    CONSTRAINT fk_standard_category_catalog FOREIGN KEY (workspace_id)
        REFERENCES yak_ops_standard_catalog (workspace_id) ON DELETE RESTRICT,
    CONSTRAINT fk_standard_category_parent FOREIGN KEY (workspace_id,parent_id)
        REFERENCES yak_ops_standard_category (workspace_id,id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Multi-level Workspace-owned standard directories';

CREATE TABLE yak_ops_standard_field (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable standard identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this standard and its category',
    category_id VARCHAR(64) NOT NULL COMMENT 'Immutable category in the same Workspace',
    cn_name VARCHAR(128) NOT NULL COMMENT 'Business field display name',
    en_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Globally unique code within standard fields',
    data_type VARCHAR(16) NOT NULL COMMENT 'Logical STRING, BIGINT, DECIMAL, DATE or TIMESTAMP type',
    length_precision VARCHAR(32) NULL COMMENT 'Optional logical length or decimal precision and scale',
    is_not_null TINYINT NOT NULL DEFAULT 0 COMMENT 'Whether the standard requires a non-null value',
    security_level VARCHAR(16) NOT NULL DEFAULT 'INTERNAL' COMMENT 'PUBLIC, INTERNAL, SENSITIVE or CONFIDENTIAL classification',
    owner_id VARCHAR(64) NOT NULL COMMENT 'Assigned user identity retained after membership ends',
    business_caliber VARCHAR(2000) NULL COMMENT 'Optional business meaning and computation definition',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic revision advanced after each accepted update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent accepted update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_standard_field_code (en_code),
    KEY idx_standard_field_category (workspace_id,category_id,update_time,id),
    KEY idx_standard_field_workspace_update (workspace_id,update_time,id),
    CONSTRAINT fk_standard_field_category FOREIGN KEY (workspace_id,category_id)
        REFERENCES yak_ops_standard_category (workspace_id,id) ON DELETE RESTRICT,
    CONSTRAINT ck_standard_field_revision CHECK (revision>0),
    CONSTRAINT ck_standard_field_code CHECK (en_code REGEXP '^[a-z][a-z0-9_]*$'),
    CONSTRAINT ck_standard_field_type CHECK (data_type IN ('STRING','BIGINT','DECIMAL','DATE','TIMESTAMP')),
    CONSTRAINT ck_standard_field_security CHECK (security_level IN ('PUBLIC','INTERNAL','SENSITIVE','CONFIDENTIAL')),
    CONSTRAINT ck_standard_field_not_null CHECK (is_not_null IN (0,1))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspace field standards with globally unique codes';
