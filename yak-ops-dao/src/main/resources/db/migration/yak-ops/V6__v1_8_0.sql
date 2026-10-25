-- Yak Ops v1.8.0: the metric module (YOIP-14).
--
-- Five kinds live under 数据建模 → 数据指标, all Workspace-scoped like every modeling table. Three
-- are owned records with an accountable owner (atomic, derived, composite); two are governed
-- dictionaries without an owner (modifiers anchored to a data domain, time periods flat). None of
-- the five hangs under the standard category tree: metrics are organized by kind and filter, not
-- by user-created directories.
--
-- A derived metric has no name of its own. Its code and name are composed from the parts --
-- period, then modifiers in submitted order, then atomic metric -- and stored as columns, so
-- uniqueness is enforced on something stored and model fields cite the values that were checked.
-- Identical parts always collide: the composition, not a free-text name, keeps the caliber
-- registry free of duplicates.
--
-- A composite metric stores an arithmetic expression over derived-metric codes plus the references
-- the parser resolved. The expression is design-time data: nothing evaluates it and it produces no
-- data type. References point at derived metrics only, so the graph stays a two-level DAG without
-- a cycle check.
--
-- Every added column on an existing table is nullable, so no row has to be rewritten to stay
-- valid. The one cross-module change is `metric_id` on `yak_ops_modeling_model_field`: an
-- advisory, delete-protected link to the derived metric a DWS or ADS field was imported from.

CREATE TABLE yak_ops_modeling_metric_time_period (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable period identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this period',
    en_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Workspace-unique abbreviation composed into derived-metric codes',
    cn_name VARCHAR(128) NOT NULL COMMENT 'Chinese name composed into derived-metric names',
    description VARCHAR(500) NULL COMMENT 'Optional business description',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic revision advanced after each accepted update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent accepted update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_metric_period_code (workspace_id, en_code),
    UNIQUE KEY uk_metric_period_workspace (workspace_id, id),
    KEY idx_metric_period_update (workspace_id, update_time, id),
    CONSTRAINT ck_metric_period_revision CHECK (revision > 0),
    CONSTRAINT ck_metric_period_code CHECK (en_code REGEXP '^[a-z][a-z0-9_]*$')
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flat Workspace time periods composed into derived metrics';

CREATE TABLE yak_ops_modeling_metric_modifier (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable modifier identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this modifier',
    domain_id VARCHAR(64) NOT NULL COMMENT 'Data domain of the same Workspace that anchors the modifier',
    en_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Workspace-unique abbreviation composed into derived-metric codes',
    cn_name VARCHAR(128) NOT NULL COMMENT 'Chinese name composed into derived-metric names',
    description VARCHAR(500) NULL COMMENT 'Optional business description',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic revision advanced after each accepted update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent accepted update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_metric_modifier_code (workspace_id, en_code),
    UNIQUE KEY uk_metric_modifier_workspace (workspace_id, id),
    KEY idx_metric_modifier_domain (workspace_id, domain_id, update_time, id),
    CONSTRAINT fk_metric_modifier_domain FOREIGN KEY (workspace_id, domain_id)
        REFERENCES yak_ops_modeling_domain (workspace_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_metric_modifier_revision CHECK (revision > 0),
    CONSTRAINT ck_metric_modifier_code CHECK (en_code REGEXP '^[a-z][a-z0-9_]*$')
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspace modifiers anchored to data domains and composed into derived metrics';

CREATE TABLE yak_ops_modeling_metric_atomic (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable atomic-metric identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this metric',
    process_id VARCHAR(64) NOT NULL COMMENT 'Business process of the same Workspace that produces the measure',
    en_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Workspace-unique code inside atomic metrics',
    cn_name VARCHAR(128) NOT NULL COMMENT 'Statistical caliber display name',
    data_type VARCHAR(16) NOT NULL COMMENT 'Logical type the measure returns: STRING, BIGINT, DECIMAL, DATE or TIMESTAMP',
    aggregate_type VARCHAR(16) NOT NULL COMMENT 'Aggregate that is part of the caliber: SUM, COUNT, AVG, MAX or MIN',
    measure_unit_id VARCHAR(64) NULL COMMENT 'Optional bound measurement unit annotation; never changes the type',
    owner_id VARCHAR(64) NOT NULL COMMENT 'Accountable owner; an active member of the Workspace',
    description VARCHAR(500) NULL COMMENT 'Optional business description',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic revision advanced after each accepted update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent accepted update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_metric_atomic_code (workspace_id, en_code),
    UNIQUE KEY uk_metric_atomic_workspace (workspace_id, id),
    KEY idx_metric_atomic_process (workspace_id, process_id, update_time, id),
    KEY idx_metric_atomic_unit (workspace_id, measure_unit_id),
    CONSTRAINT fk_metric_atomic_process FOREIGN KEY (workspace_id, process_id)
        REFERENCES yak_ops_modeling_process (workspace_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_metric_atomic_unit FOREIGN KEY (workspace_id, measure_unit_id)
        REFERENCES yak_ops_standard_measure_unit (workspace_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_metric_atomic_revision CHECK (revision > 0),
    CONSTRAINT ck_metric_atomic_code CHECK (en_code REGEXP '^[a-z][a-z0-9_]*$'),
    CONSTRAINT ck_metric_atomic_data_type CHECK (
        data_type IN ('STRING', 'BIGINT', 'DECIMAL', 'DATE', 'TIMESTAMP')),
    CONSTRAINT ck_metric_atomic_aggregate CHECK (aggregate_type IN ('SUM', 'COUNT', 'AVG', 'MAX', 'MIN'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspace atomic metrics carrying the statistical caliber of one business process';

CREATE TABLE yak_ops_modeling_metric_derived (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable derived-metric identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this metric',
    en_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Composed code: period, then modifier abbreviations in order, then atomic code',
    cn_name VARCHAR(128) NOT NULL COMMENT 'Composed name: period, then modifier names, then atomic name',
    atomic_metric_id VARCHAR(64) NOT NULL COMMENT 'Atomic metric of the same Workspace carrying the caliber',
    period_id VARCHAR(64) NOT NULL COMMENT 'Time period of the same Workspace fixing the window',
    owner_id VARCHAR(64) NOT NULL COMMENT 'Accountable owner; an active member of the Workspace',
    description VARCHAR(500) NULL COMMENT 'Optional business description',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic revision advanced after each accepted update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent accepted update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_metric_derived_code (workspace_id, en_code),
    UNIQUE KEY uk_metric_derived_workspace (workspace_id, id),
    KEY idx_metric_derived_atomic (workspace_id, atomic_metric_id),
    KEY idx_metric_derived_period (workspace_id, period_id),
    CONSTRAINT fk_metric_derived_atomic FOREIGN KEY (workspace_id, atomic_metric_id)
        REFERENCES yak_ops_modeling_metric_atomic (workspace_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_metric_derived_period FOREIGN KEY (workspace_id, period_id)
        REFERENCES yak_ops_modeling_metric_time_period (workspace_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_metric_derived_revision CHECK (revision > 0),
    CONSTRAINT ck_metric_derived_code CHECK (en_code REGEXP '^[a-z][a-z0-9_]*$')
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspace derived metrics composed from one atomic metric, one period and ordered modifiers';

CREATE TABLE yak_ops_modeling_metric_derived_modifier (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable link identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the parent derived metric',
    derived_id VARCHAR(64) NOT NULL COMMENT 'Parent derived metric; links cascade with it',
    modifier_id VARCHAR(64) NOT NULL COMMENT 'Modifier referenced by the composition',
    ordinal INT NOT NULL COMMENT 'One-based position assigned from the submitted order',
    PRIMARY KEY (id),
    UNIQUE KEY uk_metric_derived_modifier_ordinal (derived_id, ordinal),
    UNIQUE KEY uk_metric_derived_modifier_link (derived_id, modifier_id),
    CONSTRAINT fk_metric_derived_modifier_derived FOREIGN KEY (workspace_id, derived_id)
        REFERENCES yak_ops_modeling_metric_derived (workspace_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_metric_derived_modifier_modifier FOREIGN KEY (workspace_id, modifier_id)
        REFERENCES yak_ops_modeling_metric_modifier (workspace_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_metric_derived_modifier_ordinal CHECK (ordinal > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Ordered modifier links of one derived metric, replaced as a whole';

CREATE TABLE yak_ops_modeling_metric_composite (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable composite-metric identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this metric',
    en_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Workspace-unique code inside composite metrics',
    cn_name VARCHAR(128) NOT NULL COMMENT 'Display name',
    expression VARCHAR(2048) NOT NULL COMMENT 'Arithmetic expression over derived-metric codes, stored as submitted',
    owner_id VARCHAR(64) NOT NULL COMMENT 'Accountable owner; an active member of the Workspace',
    description VARCHAR(500) NULL COMMENT 'Optional business description',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic revision advanced after each accepted update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent accepted update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_metric_composite_code (workspace_id, en_code),
    UNIQUE KEY uk_metric_composite_workspace (workspace_id, id),
    KEY idx_metric_composite_update (workspace_id, update_time, id),
    CONSTRAINT ck_metric_composite_revision CHECK (revision > 0),
    CONSTRAINT ck_metric_composite_code CHECK (en_code REGEXP '^[a-z][a-z0-9_]*$')
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspace composite metrics storing design-time arithmetic over derived metrics';

CREATE TABLE yak_ops_modeling_metric_composite_ref (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated immutable link identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the parent composite metric',
    composite_id VARCHAR(64) NOT NULL COMMENT 'Parent composite metric; links cascade with it',
    derived_metric_id VARCHAR(64) NOT NULL COMMENT 'Derived metric referenced by the expression',
    PRIMARY KEY (id),
    UNIQUE KEY uk_metric_composite_ref_link (composite_id, derived_metric_id),
    CONSTRAINT fk_metric_composite_ref_composite FOREIGN KEY (workspace_id, composite_id)
        REFERENCES yak_ops_modeling_metric_composite (workspace_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_metric_composite_ref_derived FOREIGN KEY (workspace_id, derived_metric_id)
        REFERENCES yak_ops_modeling_metric_derived (workspace_id, id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Resolved derived-metric references of one composite expression, replaced as a whole';

ALTER TABLE yak_ops_modeling_model_field
    ADD COLUMN metric_id VARCHAR(64) NULL
        COMMENT 'Optional derived metric the field was imported from; advisory and delete-protected'
        AFTER measure_unit_id,
    ADD KEY idx_model_field_metric (workspace_id, metric_id),
    ADD CONSTRAINT fk_model_field_metric FOREIGN KEY (workspace_id, metric_id)
        REFERENCES yak_ops_modeling_metric_derived (workspace_id, id) ON DELETE RESTRICT;
