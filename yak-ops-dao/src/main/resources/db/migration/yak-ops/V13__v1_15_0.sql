-- Asset comments: threaded discussion on data map assets (FYOIP-20).
-- Two tables: the comment thread itself and a per-user read marker. The body is plain text;
-- @tokens inside it are a frontend rendering concern, so no mention column exists.

CREATE TABLE yak_ops_data_map_comment (
    id             VARCHAR(64)  NOT NULL COMMENT 'Application-generated comment identity',
    workspace_id   VARCHAR(64)  NOT NULL COMMENT 'Workspace owning this comment',
    target_type    VARCHAR(32)  NOT NULL COMMENT 'Kind of anchored asset; DATA_MAP_TABLE in this release',
    target_id      VARCHAR(64)  NOT NULL COMMENT 'Identity of the anchored data map asset',
    parent_id      VARCHAR(64)  NULL COMMENT 'Parent comment id; NULL for top-level comments',
    body           TEXT         NOT NULL COMMENT 'Plain-text comment body with @tokens rendered client-side',
    author_id      VARCHAR(64)  NOT NULL COMMENT 'Author user identity',
    author_name    VARCHAR(128) NOT NULL COMMENT 'Denormalized author name so a rename does not rewrite history',
    reply_count    INT          NOT NULL DEFAULT 0 COMMENT 'Direct reply count maintained by the service',
    create_time    DATETIME(3)  NOT NULL COMMENT 'Creation timestamp',
    update_time    DATETIME(3)  NOT NULL COMMENT 'Most recent reply-count change timestamp',
    create_by      VARCHAR(64)  NOT NULL COMMENT 'Authenticated creator identity for audit',
    update_by      VARCHAR(64)  NOT NULL COMMENT 'Authenticated updater identity for audit',
    PRIMARY KEY (id),
    KEY idx_dmc_target (workspace_id, target_type, target_id, create_time),
    KEY idx_dmc_parent (parent_id),
    CONSTRAINT fk_dmc_parent FOREIGN KEY (parent_id)
        REFERENCES yak_ops_data_map_comment (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Asset comment thread; top-level comments anchor to (target_type, target_id), replies point at a parent';

CREATE TABLE yak_ops_data_map_comment_read (
    id             VARCHAR(64)  NOT NULL COMMENT 'Application-generated marker identity',
    workspace_id   VARCHAR(64)  NOT NULL COMMENT 'Workspace owning this marker',
    target_id      VARCHAR(64)  NOT NULL COMMENT 'Identity of the anchored data map asset',
    user_id        VARCHAR(64)  NOT NULL COMMENT 'Reader user identity; also the row creator',
    last_read_time DATETIME(3)  NOT NULL COMMENT 'Newest comment creation time the reader has seen',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dmcr_user (workspace_id, target_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Per-user read marker for asset comment threads; one row per (asset, user)';
