-- Asset comments: threaded discussion on data map assets (FYOIP-20).
-- Two tables: the comment thread itself and a per-user read marker.

CREATE TABLE yak_ops_data_map_comment (
    id             VARCHAR(64)  NOT NULL,
    workspace_id   VARCHAR(64)  NOT NULL,
    target_type    VARCHAR(32)  NOT NULL,
    target_id      VARCHAR(64)  NOT NULL,
    parent_id      VARCHAR(64)  NULL,
    body           TEXT         NOT NULL,
    author_id      VARCHAR(64)  NOT NULL,
    author_name    VARCHAR(128) NOT NULL,
    mention_ids    VARCHAR(1024) NULL,
    reply_count    INT          NOT NULL DEFAULT 0,
    create_time    DATETIME(3)  NOT NULL,
    update_time    DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_dmc_target (workspace_id, target_type, target_id, create_time),
    KEY idx_dmc_parent (parent_id),
    CONSTRAINT fk_dmc_parent FOREIGN KEY (parent_id)
        REFERENCES yak_ops_data_map_comment (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Asset comment thread; top-level comments anchor to (target_type, target_id), replies point at a parent.';

CREATE TABLE yak_ops_data_map_comment_read (
    id             VARCHAR(64)  NOT NULL,
    workspace_id   VARCHAR(64)  NOT NULL,
    target_id      VARCHAR(64)  NOT NULL,
    user_id        VARCHAR(64)  NOT NULL,
    last_read_time DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dmcr_user (workspace_id, target_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Per-user read marker for asset comment threads; one row per (asset, user).';
