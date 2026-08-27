-- Digital Screen PR 1 baseline: persist one mutable definition per screen.
-- Immutable published snapshots/version history are intentionally deferred to PR 2.
CREATE TABLE IF NOT EXISTS yak_digital_screen (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    template_id VARCHAR(128) NOT NULL,
    template_version INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    bindings_json JSON NOT NULL,
    published_time DATETIME(6) NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_yak_digital_screen_status_update (status, update_time),
    KEY idx_yak_digital_screen_template (template_id),
    KEY idx_yak_digital_screen_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
