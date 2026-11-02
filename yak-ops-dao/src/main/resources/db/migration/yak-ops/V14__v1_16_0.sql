-- Semantic model edges and projection (FYOIP-21).
-- Three additions: model-to-model relations with ordered key pairs, business synonyms for
-- standards / model fields / metrics, and two analysis attributes (grain note, time role).
-- Key pairs reference field en_code values, never field row ids: ModelServiceImpl.update
-- replaces every field row on each save, so row ids are unstable while codes are the
-- stable business contract. A code that no longer resolves is a first-class broken state.

CREATE TABLE yak_ops_modeling_relation (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated relation identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning both models',
    name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Relation name, unique per Workspace',
    from_model_id VARCHAR(64) NOT NULL COMMENT 'Many side model; must not be a DIM model',
    to_model_id VARCHAR(64) NOT NULL COMMENT 'One side model; must be a DIM model',
    join_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'INNER or LEFT',
    description VARCHAR(500) NULL COMMENT 'Optional business description; null when absent',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic version incremented after every update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_relation_name (workspace_id, name),
    KEY idx_modeling_relation_from (workspace_id, from_model_id),
    KEY idx_modeling_relation_to (workspace_id, to_model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Model-to-model many-to-one relation; ordered keys live in yak_ops_modeling_relation_column';

CREATE TABLE yak_ops_modeling_relation_column (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated key-pair identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the relation',
    relation_id VARCHAR(64) NOT NULL COMMENT 'Owning relation',
    ordinal INT NOT NULL COMMENT 'Zero-based position; the from and to codes share it',
    from_field_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'en_code of the many-side model field',
    to_field_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'en_code of the one-side model field',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic version inherited from the owning relation update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_relation_column (relation_id, ordinal),
    CONSTRAINT fk_modeling_relation_column FOREIGN KEY (relation_id)
        REFERENCES yak_ops_modeling_relation (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Ordered join key pair of one relation; codes resolve against (model_id, en_code) at read time';

CREATE TABLE yak_ops_modeling_synonym (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated synonym identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the annotated object',
    target_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'STANDARD_FIELD, MODEL_FIELD or METRIC',
    target_id VARCHAR(148) NOT NULL COMMENT 'Identity of the annotated object; MODEL_FIELD composes modelId:fieldCode because field row ids are unstable',
    term VARCHAR(64) NOT NULL COMMENT 'Business synonym, unique per target',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic version incremented after every update',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent update timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_synonym_term (workspace_id, target_type, target_id, term),
    KEY idx_modeling_synonym_target (workspace_id, target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Business synonym of one standard field, model field or metric; replace-set semantics per target';

ALTER TABLE yak_ops_modeling_model
    ADD COLUMN grain_note VARCHAR(200) NULL COMMENT 'Declared meaning of one row; null when undeclared' AFTER description;

ALTER TABLE yak_ops_modeling_model_field
    ADD COLUMN is_time TINYINT NOT NULL DEFAULT 0 COMMENT 'Analysis time role used by time filtering and trend analysis, independent of data_type' AFTER is_partition;
