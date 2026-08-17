CREATE TABLE IF NOT EXISTS yak_system_env_var (
    var_key    VARCHAR(128) NOT NULL,
    var_value  TEXT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (var_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
