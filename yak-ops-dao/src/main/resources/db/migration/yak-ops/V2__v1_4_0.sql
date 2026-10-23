-- Warehouse planning and data standards additions after the v1.3.0 fresh-install baseline.
-- v1.4.0 owns this forward-only migration; both features ship in the same release.

CREATE TABLE yak_ops_modeling_category (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable node identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the node and its parent references',
    name VARCHAR(128) NOT NULL COMMENT 'Business display name',
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Case-sensitive globally reserved planning code',
    owner_id VARCHAR(64) NOT NULL COMMENT 'Assigned user identity; retained after membership ends',
    description VARCHAR(500) NULL COMMENT 'Optional business description; null when absent',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic version incremented after every update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent field update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_category_workspace (workspace_id, id),
    KEY idx_modeling_category_code (workspace_id, code),
    CONSTRAINT ck_modeling_category_revision CHECK (revision > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspace-owned planning category';

CREATE TABLE yak_ops_modeling_domain (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable node identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the node and its parent references',
    category_id VARCHAR(64) NOT NULL COMMENT 'Immutable owning category in the same Workspace',
    name VARCHAR(128) NOT NULL COMMENT 'Business display name',
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Case-sensitive globally reserved planning code',
    owner_id VARCHAR(64) NOT NULL COMMENT 'Assigned user identity; retained after membership ends',
    description VARCHAR(500) NULL COMMENT 'Optional business description; null when absent',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic version incremented after every update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent field update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_domain_workspace (workspace_id, id),
    KEY idx_modeling_domain_code (workspace_id, code),
    CONSTRAINT ck_modeling_domain_revision CHECK (revision > 0),
    KEY idx_modeling_domain_parent (workspace_id, category_id),
    CONSTRAINT fk_modeling_domain_parent FOREIGN KEY (workspace_id, category_id)
        REFERENCES yak_ops_modeling_category (workspace_id, id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspace-owned planning domain';

CREATE TABLE yak_ops_modeling_process (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable node identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the node and its parent references',
    domain_id VARCHAR(64) NOT NULL COMMENT 'Immutable owning domain in the same Workspace',
    name VARCHAR(128) NOT NULL COMMENT 'Business display name',
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Case-sensitive globally reserved planning code',
    owner_id VARCHAR(64) NOT NULL COMMENT 'Assigned user identity; retained after membership ends',
    description VARCHAR(500) NULL COMMENT 'Optional business description; null when absent',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic version incremented after every update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent field update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_process_workspace (workspace_id, id),
    KEY idx_modeling_process_code (workspace_id, code),
    CONSTRAINT ck_modeling_process_revision CHECK (revision > 0),
    KEY idx_modeling_process_parent (workspace_id, domain_id),
    CONSTRAINT fk_modeling_process_parent FOREIGN KEY (workspace_id, domain_id)
        REFERENCES yak_ops_modeling_domain (workspace_id, id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspace-owned planning process';

CREATE TABLE yak_ops_modeling_code (
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Global planning code shared by all Workspaces and levels',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the reservation',
    node_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Immutable CATEGORY, DOMAIN or PROCESS level',
    node_id VARCHAR(64) NOT NULL COMMENT 'Node owning this reservation; lifecycle is atomic with the node',
    create_time DATETIME(3) NOT NULL COMMENT 'Reservation creation timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    PRIMARY KEY (code),
    UNIQUE KEY uk_modeling_code_node (node_kind, node_id),
    CONSTRAINT ck_modeling_code_kind CHECK (node_kind IN ('CATEGORY', 'DOMAIN', 'PROCESS')),
    CONSTRAINT ck_modeling_code_format CHECK (code REGEXP '^[a-z][a-z0-9_]*$')
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Globally unique planning codes, mutated with their owning nodes';

-- Data standards: per-Workspace catalog marker, directories and fields.

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


-- Data warehouse layers: five presets carry an immutable abbreviation, custom layers stay editable.

CREATE TABLE yak_ops_modeling_layer (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable layer identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the layer and its datasource binding',
    en_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Uppercase abbreviation unique inside the Workspace; immutable for presets',
    cn_name VARCHAR(64) NOT NULL COMMENT 'Business display name such as 贴源层',
    data_source_id VARCHAR(64) NULL COMMENT 'Bound physical datasource; required before the layer may be enabled',
    description VARCHAR(500) NULL COMMENT 'Optional business description; null when absent',
    enabled TINYINT NOT NULL DEFAULT 0 COMMENT '1 when dimension modeling may select this layer and publishing resolves its datasource',
    builtin TINYINT NOT NULL DEFAULT 0 COMMENT '1 for the five preset layers, which cannot be deleted',
    sort_no INT NOT NULL DEFAULT 1000 COMMENT 'Display order: presets use 10..50, custom layers append at 1000',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic version incremented after every accepted update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent accepted update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_layer_code (workspace_id, en_code),
    KEY idx_modeling_layer_datasource (data_source_id),
    KEY idx_modeling_layer_order (workspace_id, builtin, sort_no, id),
    CONSTRAINT fk_modeling_layer_workspace FOREIGN KEY (workspace_id)
        REFERENCES yak_ops_workspace (id) ON DELETE RESTRICT,
    CONSTRAINT fk_modeling_layer_datasource FOREIGN KEY (data_source_id)
        REFERENCES yak_ops_data_source (id) ON DELETE RESTRICT,
    CONSTRAINT ck_modeling_layer_code CHECK (en_code REGEXP '^[A-Z][A-Z0-9_]{0,31}$'),
    CONSTRAINT ck_modeling_layer_enabled CHECK (enabled IN (0,1)),
    CONSTRAINT ck_modeling_layer_builtin CHECK (builtin IN (0,1)),
    CONSTRAINT ck_modeling_layer_bound CHECK (enabled=0 OR data_source_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspace data warehouse layers bound to physical datasources';
