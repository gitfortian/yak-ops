-- Yak Ops v1.7.0: complete data standards (YOIP-13).
--
-- One change here is a relaxation rather than an addition: `uk_standard_field_code` was
-- `(en_code)` alone, so two Workspaces could not both define `member_id`. Every other modeling
-- table is Workspace-scoped, and the three new kinds are too, so the key is replaced instead of
-- propagated. A value that was globally unique is also unique inside its own Workspace, so the
-- replacement cannot conflict on existing rows.
--
-- Two container concepts live on `yak_ops_standard_category` rather than in a new table: a node is
-- either a `DIRECTORY` or a `STANDARD_SET`, and a set declares which kind of record it holds. A
-- directory carries no kind. The lock on a set's kind is derived from its record count rather than
-- stored, so a set that is emptied again becomes switchable; that rule spans rows and stays in the
-- service, while the CHECK below only constrains the two stored spellings.
--
-- Measurement units deliberately have no tree -- their taxonomy is the closed list of five
-- categories -- and naming entries are flat, so neither table carries `category_id`. Field
-- standards and lookup tables share the existing tree because both are "records under a
-- directory".
--
-- Every added column on an existing table is nullable or defaulted, so no row has to be rewritten
-- to stay valid. `naming_root_word_id` is the only new column that is ever backfilled, and only
-- when an operator enables a naming rule on that layer.

ALTER TABLE yak_ops_standard_category
    ADD COLUMN node_type VARCHAR(16) NOT NULL DEFAULT 'DIRECTORY'
        COMMENT 'Container shape: DIRECTORY or STANDARD_SET' AFTER parent_id,
    ADD COLUMN standard_kind VARCHAR(16) NULL
        COMMENT 'Kind a STANDARD_SET holds: FIELD or LOOKUP; null on a DIRECTORY' AFTER node_type,
    ADD CONSTRAINT ck_standard_category_node_type CHECK (node_type IN ('DIRECTORY', 'STANDARD_SET')),
    ADD CONSTRAINT ck_standard_category_kind CHECK (
        standard_kind IS NULL OR standard_kind IN ('FIELD', 'LOOKUP'));

ALTER TABLE yak_ops_standard_category
    COMMENT = 'Workspace-owned standard directories and standard sets';

CREATE TABLE yak_ops_standard_lookup (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable lookup-table identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this lookup table and its category',
    category_id VARCHAR(64) NOT NULL COMMENT 'Immutable directory or standard set in the same Workspace',
    en_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Workspace-unique code inside lookup tables',
    cn_name VARCHAR(128) NOT NULL COMMENT 'Business display name',
    en_name VARCHAR(128) NOT NULL COMMENT 'English display name',
    description VARCHAR(500) NULL COMMENT 'Optional business description',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic revision advanced after each accepted update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent accepted update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_standard_lookup_code (workspace_id, en_code),
    UNIQUE KEY uk_standard_lookup_workspace (workspace_id, id),
    KEY idx_standard_lookup_category (workspace_id, category_id, update_time, id),
    CONSTRAINT fk_standard_lookup_category FOREIGN KEY (workspace_id, category_id)
        REFERENCES yak_ops_standard_category (workspace_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_standard_lookup_revision CHECK (revision > 0),
    CONSTRAINT ck_standard_lookup_code CHECK (en_code REGEXP '^[a-z][a-z0-9_]*$')
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspace enumerated value ranges referenced by standards and model fields';

CREATE TABLE yak_ops_standard_lookup_value (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable value identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the parent lookup table',
    lookup_id VARCHAR(64) NOT NULL COMMENT 'Parent lookup table; values cascade with it',
    ordinal INT NOT NULL COMMENT 'One-based position assigned from the submitted order',
    value_code VARCHAR(64) NOT NULL COMMENT 'Code of one enumerated value; unique inside its table',
    cn_name VARCHAR(128) NOT NULL COMMENT 'Chinese display name of the value',
    en_name VARCHAR(128) NOT NULL COMMENT 'English display name of the value',
    meaning VARCHAR(500) NULL COMMENT 'Optional business meaning of the value',
    PRIMARY KEY (id),
    UNIQUE KEY uk_standard_lookup_value_code (lookup_id, value_code),
    UNIQUE KEY uk_standard_lookup_value_ordinal (lookup_id, ordinal),
    CONSTRAINT fk_standard_lookup_value_lookup FOREIGN KEY (workspace_id, lookup_id)
        REFERENCES yak_ops_standard_lookup (workspace_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_standard_lookup_value_ordinal CHECK (ordinal > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Ordered values of one lookup table, replaced as a whole';

CREATE TABLE yak_ops_standard_measure_unit (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable unit identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this unit',
    category VARCHAR(16) NOT NULL COMMENT 'CURRENCY, QUANTIFIER, RATIO, RANK or TIME',
    en_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Workspace-unique abbreviation shown next to a bound field',
    en_name VARCHAR(128) NOT NULL COMMENT 'English name, such as meter',
    cn_name VARCHAR(128) NOT NULL COMMENT 'Chinese name, such as 米',
    description VARCHAR(500) NULL COMMENT 'Optional business description',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic revision advanced after each accepted update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent accepted update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_standard_unit_code (workspace_id, en_code),
    UNIQUE KEY uk_standard_unit_workspace (workspace_id, id),
    KEY idx_standard_unit_category (workspace_id, category, update_time, id),
    CONSTRAINT ck_standard_unit_revision CHECK (revision > 0),
    CONSTRAINT ck_standard_unit_code CHECK (en_code REGEXP '^[a-z][a-z0-9_]*$'),
    CONSTRAINT ck_standard_unit_category CHECK (
        category IN ('CURRENCY', 'QUANTIFIER', 'RATIO', 'RANK', 'TIME'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspace measurement units with a closed category list';

CREATE TABLE yak_ops_standard_naming_entry (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable entry identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this naming entry',
    en_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Workspace-unique root-word abbreviation matched by a table-name rule',
    en_name VARCHAR(128) NOT NULL COMMENT 'English name of the root word',
    cn_name VARCHAR(128) NOT NULL COMMENT 'Chinese name of the root word',
    description VARCHAR(500) NULL COMMENT 'Optional business description',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic revision advanced after each accepted update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent accepted update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_standard_naming_code (workspace_id, en_code),
    UNIQUE KEY uk_standard_naming_workspace (workspace_id, id),
    KEY idx_standard_naming_update (workspace_id, update_time, id),
    CONSTRAINT ck_standard_naming_revision CHECK (revision > 0),
    CONSTRAINT ck_standard_naming_code CHECK (en_code REGEXP '^[a-z][a-z0-9_]*$')
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flat Workspace naming dictionary consumed by layer table-name rules';

ALTER TABLE yak_ops_standard_field
    ADD COLUMN default_value VARCHAR(2048) NULL
        COMMENT 'Descriptive business preset; never generated as a DDL DEFAULT clause' AFTER business_caliber,
    ADD COLUMN parent_standard_id VARCHAR(64) NULL
        COMMENT 'Optional parent standard in the same Workspace; records specialization only'
        AFTER default_value,
    ADD COLUMN lookup_id VARCHAR(64) NULL COMMENT 'Optional bound lookup table' AFTER parent_standard_id,
    ADD COLUMN measure_unit_id VARCHAR(64) NULL COMMENT 'Optional bound measurement unit' AFTER lookup_id,
    ADD KEY idx_standard_field_parent (workspace_id, parent_standard_id),
    ADD KEY idx_standard_field_lookup (workspace_id, lookup_id),
    ADD KEY idx_standard_field_unit (workspace_id, measure_unit_id),
    ADD CONSTRAINT fk_standard_field_parent FOREIGN KEY (workspace_id, parent_standard_id)
        REFERENCES yak_ops_standard_field (workspace_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_standard_field_lookup FOREIGN KEY (workspace_id, lookup_id)
        REFERENCES yak_ops_standard_lookup (workspace_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_standard_field_unit FOREIGN KEY (workspace_id, measure_unit_id)
        REFERENCES yak_ops_standard_measure_unit (workspace_id, id) ON DELETE RESTRICT;

ALTER TABLE yak_ops_standard_field
    MODIFY COLUMN en_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Workspace-unique code within standard fields',
    DROP INDEX uk_standard_field_code,
    ADD UNIQUE KEY uk_standard_field_code (workspace_id, en_code);

ALTER TABLE yak_ops_standard_field
    COMMENT = 'Workspace field standards with Workspace-unique codes, parent and binding references';

ALTER TABLE yak_ops_modeling_model_field
    ADD COLUMN lookup_id VARCHAR(64) NULL
        COMMENT 'Optional lookup table bound to the field; overridden by a bound field standard'
        AFTER standard_id,
    ADD COLUMN measure_unit_id VARCHAR(64) NULL
        COMMENT 'Optional measurement unit annotation; never changes the field type'
        AFTER lookup_id,
    ADD KEY idx_model_field_lookup (workspace_id, lookup_id),
    ADD KEY idx_model_field_unit (workspace_id, measure_unit_id),
    ADD CONSTRAINT fk_model_field_lookup FOREIGN KEY (workspace_id, lookup_id)
        REFERENCES yak_ops_standard_lookup (workspace_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_model_field_unit FOREIGN KEY (workspace_id, measure_unit_id)
        REFERENCES yak_ops_standard_measure_unit (workspace_id, id) ON DELETE RESTRICT;

ALTER TABLE yak_ops_modeling_model
    ADD COLUMN naming_root_word_id VARCHAR(64) NULL
        COMMENT 'Root word the table name was composed from under an enabled layer rule' AFTER name,
    ADD KEY idx_modeling_model_naming_root (workspace_id, naming_root_word_id),
    ADD CONSTRAINT fk_modeling_model_naming_root FOREIGN KEY (workspace_id, naming_root_word_id)
        REFERENCES yak_ops_standard_naming_entry (workspace_id, id) ON DELETE RESTRICT;

ALTER TABLE yak_ops_modeling_layer
    ADD COLUMN naming_rule_enabled TINYINT NOT NULL DEFAULT 0
        COMMENT 'Whether model suffixes of this layer must carry a dictionary root word and process'
        AFTER enabled,
    ADD CONSTRAINT ck_modeling_layer_naming_rule CHECK (naming_rule_enabled IN (0, 1));
