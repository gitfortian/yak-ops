-- Warehouse planning additions after the v1.3.0 fresh-install baseline.
-- The next feature release owns this forward-only migration.

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
