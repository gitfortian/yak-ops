CREATE TABLE IF NOT EXISTS yak_dev_editor_setting (
    user_key VARCHAR(128) NOT NULL,
    setting_json LONGTEXT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (user_key),
    KEY idx_yak_dev_editor_setting_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
