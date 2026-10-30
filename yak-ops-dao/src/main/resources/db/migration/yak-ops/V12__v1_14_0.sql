-- Yak Ops v1.14.0 incremental schema.
-- Asset tag dictionary (FYOIP-19): a key names a dimension, a value names a term inside it, and a
-- binding attaches one value to one catalog asset. The flat tag table and its association table are
-- migrated into one legacy key per Workspace and then dropped, so this release both reads and
-- writes only the dictionary. Delete protection is a constraint: a bound value rejects the delete
-- (ON DELETE RESTRICT) and a key delete cascades into that same rejection.

CREATE TABLE yak_ops_tag_key (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated key identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this key',
    name VARCHAR(64) NOT NULL COMMENT 'Workspace-unique dimension name',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent rename timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_key_name (workspace_id, name),
    UNIQUE KEY uk_tag_key_workspace (workspace_id, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Workspace-owned tag keys; one key is one filter dimension';

CREATE TABLE yak_ops_tag_value (
    id VARCHAR(64) NOT NULL COMMENT 'Application-generated value identity',
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this value',
    key_id VARCHAR(64) NOT NULL COMMENT 'Key this term belongs to',
    name VARCHAR(128) NOT NULL COMMENT 'Key-unique term name',
    create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
    update_time DATETIME(3) NOT NULL COMMENT 'Most recent rename timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by VARCHAR(64) NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_value_name (key_id, name),
    UNIQUE KEY uk_tag_value_workspace (workspace_id, id),
    KEY idx_tag_value_key (workspace_id, key_id),
    CONSTRAINT fk_tag_value_key FOREIGN KEY (workspace_id, key_id)
        REFERENCES yak_ops_tag_key (workspace_id, id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Terms of one key; deleting the key cascades to its values';

CREATE TABLE yak_ops_data_map_table_tag_value (
    workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the asset and the value',
    table_id VARCHAR(64) NOT NULL COMMENT 'Tagged catalog asset',
    tag_value_id VARCHAR(64) NOT NULL COMMENT 'Applied tag value',
    create_time DATETIME(3) NOT NULL COMMENT 'Binding timestamp',
    create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated binder identity for audit',
    PRIMARY KEY (workspace_id, table_id, tag_value_id),
    KEY idx_table_tag_value_usage (workspace_id, tag_value_id, create_time),
    CONSTRAINT fk_table_tag_value_table FOREIGN KEY (workspace_id, table_id)
        REFERENCES yak_ops_data_map_table (workspace_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_table_tag_value_value FOREIGN KEY (workspace_id, tag_value_id)
        REFERENCES yak_ops_tag_value (workspace_id, id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Asset-to-value bindings; a bound value cannot be deleted, an asset delete cascades';

-- Migration of the flat tags: one legacy key per Workspace that has at least one tag, with every
-- old tag name as one of its values and every old binding copied onto the matching value. The
-- identities are derived from the Workspace id, so the mapping is reproducible and never collides
-- with IdUtils.nextId(), which emits digits only. A Workspace without tags gets no legacy key.
INSERT INTO yak_ops_tag_key (id, workspace_id, name, create_time, update_time, create_by, update_by)
SELECT CONCAT('tklg', LEFT(SHA2(t.workspace_id, 256), 32)), t.workspace_id, 'legacy',
       MIN(t.create_time), MAX(t.update_time), 'system', 'system'
FROM yak_ops_data_map_tag t
GROUP BY t.workspace_id;

INSERT INTO yak_ops_tag_value (id, workspace_id, key_id, name, create_time, update_time, create_by, update_by)
SELECT CONCAT('tvlgb', LEFT(SHA2(CONCAT(t.workspace_id, ':', t.id), 256), 32)),
       t.workspace_id,
       CONCAT('tklg', LEFT(SHA2(t.workspace_id, 256), 32)),
       t.name, t.create_time, t.update_time, t.create_by, t.update_by
FROM yak_ops_data_map_tag t;

INSERT INTO yak_ops_data_map_table_tag_value (workspace_id, table_id, tag_value_id, create_time, create_by)
SELECT b.workspace_id, b.table_id,
       CONCAT('tvlgb', LEFT(SHA2(CONCAT(b.workspace_id, ':', b.tag_id), 256), 32)),
       b.create_time, b.create_by
FROM yak_ops_data_map_table_tag b;

DROP TABLE yak_ops_data_map_table_tag;
DROP TABLE yak_ops_data_map_tag;
