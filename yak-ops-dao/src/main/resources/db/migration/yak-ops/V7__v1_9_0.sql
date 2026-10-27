-- Yak Ops v1.9.0: the data map metadata catalog (FYOIP-15).
--
-- An asset is one physical table or view observed in a datasource namespace. It keeps one
-- identity across crawls so a description, an owner, a domain and tags stay attached to it;
-- it survives the deletion of whatever created it; and it is updated in place. The catalog is
-- therefore current state, and stays separate from the immutable per-publication snapshots of
-- FYOIP-11.
--
-- Observed and curated columns never share a writer. `table_type`, `remarks`, `status`,
-- `first_seen_time` and `last_seen_time` are written only by the crawler; `description`,
-- `owner_id` and `domain_id` are written only by the curation endpoint. There is no merge rule
-- and no revision column: a crawl must not invalidate an open edit form for a field it cannot
-- reach.
--
-- The identity key is `(workspace_id, datasource_id, database_name, schema_name, name)`, with
-- both namespace columns NOT NULL DEFAULT ''. `schema_name` cannot be nullable: InnoDB treats
-- NULLs as distinct in a unique index, so two rows for the same MySQL table would both be
-- insertable and every crawl would duplicate every table.
--
-- `active_scope` exists only to make "one active run per datasource" a constraint instead of a
-- convention. It is 1 while a run is PENDING or RUNNING and NULL once terminal, so the unique
-- key admits exactly one active run per datasource while finished runs coexist.
--
-- Every table carries ENGINE=InnoDB and utf8mb4_unicode_ci explicitly, and no existing table is
-- altered. The catalog starts empty, so no backfill is needed.

CREATE TABLE yak_ops_data_map_crawl (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable crawl-run identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this run',
    datasource_id VARCHAR(64) NOT NULL COMMENT 'Datasource whose namespace is enumerated',
    datasource_revision BIGINT NOT NULL COMMENT 'Datasource binding revision pinned at submission',
    database_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Catalog slot of the crawled namespace',
    schema_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Schema slot of the crawled namespace',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Run state: PENDING, RUNNING, SUCCEEDED or FAILED',
    active_scope TINYINT NULL
        COMMENT '1 while PENDING or RUNNING, NULL once terminal; makes one active run per datasource a key',
    table_count INT NOT NULL DEFAULT 0 COMMENT 'Tables observed by this run',
    column_count INT NOT NULL DEFAULT 0 COMMENT 'Columns observed by this run',
    added_count INT NOT NULL DEFAULT 0 COMMENT 'Assets first inserted by this run',
    updated_count INT NOT NULL DEFAULT 0 COMMENT 'Existing assets refreshed by this run',
    removed_count INT NOT NULL DEFAULT 0 COMMENT 'Assets marked MISSING by this run',
    failed_count INT NOT NULL DEFAULT 0 COMMENT 'Tables whose columns could not be read',
    error VARCHAR(1000) NULL COMMENT 'Run-level failure text; null on success',
    created_time DATETIME(3) NOT NULL COMMENT 'Submission timestamp',
    started_time DATETIME(3) NULL COMMENT 'Claim timestamp; null while pending',
    finished_time DATETIME(3) NULL COMMENT 'Terminal timestamp; null while active',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated submitter identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_map_crawl_workspace (workspace_id, id),
    UNIQUE KEY uk_data_map_crawl_active (workspace_id, datasource_id, active_scope),
    KEY idx_data_map_crawl_created (workspace_id, datasource_id, created_time),
    CONSTRAINT fk_data_map_crawl_datasource FOREIGN KEY (datasource_id)
        REFERENCES yak_ops_data_source (id) ON DELETE CASCADE,
    CONSTRAINT ck_data_map_crawl_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Workspace-scoped data map crawl runs';

CREATE TABLE yak_ops_data_map_crawl_failure (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated failure identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the run',
    crawl_id VARCHAR(64) NOT NULL COMMENT 'Run that observed the failure',
    table_name VARCHAR(128) NOT NULL COMMENT 'Table whose metadata could not be read',
    error VARCHAR(1000) NOT NULL COMMENT 'Per-table failure text',
    created_time DATETIME(3) NOT NULL COMMENT 'Observation timestamp',
    PRIMARY KEY (id),
    KEY idx_data_map_crawl_failure_run (crawl_id, created_time),
    CONSTRAINT fk_data_map_crawl_failure_run FOREIGN KEY (crawl_id)
        REFERENCES yak_ops_data_map_crawl (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only per-table crawl failures';

CREATE TABLE yak_ops_data_map_table (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated asset identity, stable across crawls',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this asset',
    datasource_id VARCHAR(64) NOT NULL COMMENT 'Datasource the asset was observed through',
    database_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Catalog slot of the physical identity',
    schema_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Schema slot of the physical identity',
    name VARCHAR(128) NOT NULL COMMENT 'Physical table or view name, unqualified',
    table_type VARCHAR(32) NULL COMMENT 'Engine-reported TABLE_TYPE; crawler-owned',
    remarks VARCHAR(500) NULL COMMENT 'Engine-reported table comment; crawler-owned',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Presence: ACTIVE or MISSING; crawler-owned',
    first_seen_time DATETIME(3) NOT NULL COMMENT 'First complete run that observed this name',
    last_seen_time DATETIME(3) NOT NULL COMMENT 'Most recent run that observed this name',
    description VARCHAR(2000) NULL COMMENT 'User-maintained business description; curation-owned',
    owner_id VARCHAR(64) NULL COMMENT 'Accountable Workspace member; curation-owned',
    domain_id VARCHAR(64) NULL COMMENT 'Optional data domain of 数仓规划; curation-owned',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent write timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_map_table_identity (workspace_id, datasource_id, database_name, schema_name, name),
    UNIQUE KEY uk_data_map_table_workspace (workspace_id, id),
    KEY idx_data_map_table_name (workspace_id, name),
    KEY idx_data_map_table_domain (workspace_id, domain_id, update_time, id),
    KEY idx_data_map_table_first_seen (workspace_id, first_seen_time),
    CONSTRAINT fk_data_map_table_datasource FOREIGN KEY (datasource_id)
        REFERENCES yak_ops_data_source (id) ON DELETE CASCADE,
    CONSTRAINT fk_data_map_table_domain FOREIGN KEY (domain_id)
        REFERENCES yak_ops_modeling_domain (id) ON DELETE SET NULL,
    CONSTRAINT ck_data_map_table_status CHECK (status IN ('ACTIVE', 'MISSING'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Current-state catalog of physical tables and views';

CREATE TABLE yak_ops_data_map_column (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated column identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the asset',
    table_id VARCHAR(64) NOT NULL COMMENT 'Asset this column belongs to',
    ordinal INT NOT NULL COMMENT 'Engine-reported ordinal position, from one',
    name VARCHAR(128) NOT NULL COMMENT 'Column name',
    type_name VARCHAR(128) NOT NULL COMMENT 'Engine-reported native type name',
    jdbc_type INT NOT NULL COMMENT 'java.sql.Types code',
    size INT NULL COMMENT 'Declared or reported column size',
    scale INT NULL COMMENT 'Declared or reported numeric scale',
    nullable BIT(1) NOT NULL COMMENT 'Whether the column admits nulls',
    primary_key BIT(1) NOT NULL COMMENT 'Whether the column participates in the primary key',
    primary_key_position INT NULL COMMENT 'Ordinal inside the declared primary key',
    remarks VARCHAR(500) NULL COMMENT 'Engine-reported column comment',
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_map_column_ordinal (table_id, ordinal),
    KEY idx_data_map_column_name (workspace_id, name),
    CONSTRAINT fk_data_map_column_table FOREIGN KEY (table_id)
        REFERENCES yak_ops_data_map_table (id) ON DELETE CASCADE,
    CONSTRAINT ck_data_map_column_ordinal CHECK (ordinal > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Current-state columns of a catalog asset, replaced per table';

CREATE TABLE yak_ops_data_map_table_change (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated change identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the asset',
    table_id VARCHAR(64) NOT NULL COMMENT 'Asset the change was detected on',
    change_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'COLUMN_ADDED, COLUMN_REMOVED, COLUMN_TYPE_CHANGED, COLUMN_NULLABILITY_CHANGED, PRIMARY_KEY_CHANGED or TABLE_COMMENT_CHANGED',
    detail VARCHAR(1000) NOT NULL COMMENT 'Human-readable description of what changed',
    created_time DATETIME(3) NOT NULL COMMENT 'Detection timestamp',
    PRIMARY KEY (id),
    KEY idx_data_map_table_change_time (workspace_id, table_id, created_time),
    CONSTRAINT fk_data_map_table_change_table FOREIGN KEY (table_id)
        REFERENCES yak_ops_data_map_table (id) ON DELETE CASCADE,
    CONSTRAINT ck_data_map_table_change_type CHECK (change_type IN (
        'COLUMN_ADDED', 'COLUMN_REMOVED', 'COLUMN_TYPE_CHANGED',
        'COLUMN_NULLABILITY_CHANGED', 'PRIMARY_KEY_CHANGED', 'TABLE_COMMENT_CHANGED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only crawl-detected changes per asset';

CREATE TABLE yak_ops_data_map_tag (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated tag identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this tag',
    name VARCHAR(64) NOT NULL COMMENT 'Workspace-unique tag name',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent rename timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_map_tag_name (workspace_id, name),
    UNIQUE KEY uk_data_map_tag_workspace (workspace_id, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Workspace-owned asset tags';

CREATE TABLE yak_ops_data_map_table_tag (
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the asset and tag',
    table_id VARCHAR(64) NOT NULL COMMENT 'Tagged asset',
    tag_id VARCHAR(64) NOT NULL COMMENT 'Applied tag',
    create_time DATETIME(3) NOT NULL COMMENT 'Association timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated tagger identity for audit',
    PRIMARY KEY (table_id, tag_id),
    KEY idx_data_map_table_tag_tag (workspace_id, tag_id),
    CONSTRAINT fk_data_map_table_tag_table FOREIGN KEY (table_id)
        REFERENCES yak_ops_data_map_table (id) ON DELETE CASCADE,
    CONSTRAINT fk_data_map_table_tag_tag FOREIGN KEY (tag_id)
        REFERENCES yak_ops_data_map_tag (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Asset-to-tag associations; a crawler never writes here';

CREATE TABLE yak_ops_data_map_favorite (
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the member and asset',
    user_id VARCHAR(64) NOT NULL COMMENT 'Member who favourited the asset',
    table_id VARCHAR(64) NOT NULL COMMENT 'Favourited asset',
    created_time DATETIME(3) NOT NULL COMMENT 'Favourite timestamp',
    PRIMARY KEY (workspace_id, user_id, table_id),
    KEY idx_data_map_favorite_time (workspace_id, user_id, created_time),
    CONSTRAINT fk_data_map_favorite_table FOREIGN KEY (table_id)
        REFERENCES yak_ops_data_map_table (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Per-member favourite assets';

CREATE TABLE yak_ops_data_map_view (
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the member and asset',
    user_id VARCHAR(64) NOT NULL COMMENT 'Member who visited the asset',
    table_id VARCHAR(64) NOT NULL COMMENT 'Visited asset',
    view_count BIGINT NOT NULL DEFAULT 1 COMMENT 'Bounded visit counter for this member and asset',
    first_viewed_time DATETIME(3) NOT NULL COMMENT 'First recorded visit',
    last_viewed_time DATETIME(3) NOT NULL COMMENT 'Most recent recorded visit',
    PRIMARY KEY (workspace_id, user_id, table_id),
    KEY idx_data_map_view_recent (workspace_id, user_id, last_viewed_time),
    KEY idx_data_map_view_table (workspace_id, table_id),
    CONSTRAINT fk_data_map_view_table FOREIGN KEY (table_id)
        REFERENCES yak_ops_data_map_table (id) ON DELETE CASCADE,
    CONSTRAINT ck_data_map_view_count CHECK (view_count > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Per-member visit counters used by recent assets and the hot list';
