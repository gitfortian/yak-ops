-- Dashboard is a stable asset. Manual saves create immutable DashboardVersion snapshots.
CREATE TABLE IF NOT EXISTS yak_dashboard (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    current_version_id BIGINT NULL,
    current_version_no INT NOT NULL DEFAULT 0,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_yak_dashboard_update (update_time),
    KEY idx_yak_dashboard_current_version (current_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dashboard_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dashboard_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    name_snapshot VARCHAR(200) NOT NULL,
    description_snapshot VARCHAR(2000) NULL,
    active_dataset_id BIGINT NULL,
    create_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dashboard_version_no (dashboard_id, version_no),
    CONSTRAINT fk_yak_dashboard_version_dashboard
        FOREIGN KEY (dashboard_id) REFERENCES yak_dashboard(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dashboard_widget (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dashboard_version_id BIGINT NOT NULL,
    widget_key VARCHAR(64) NOT NULL,
    analysis_id BIGINT NULL,
    title VARCHAR(200) NULL,
    inline_analysis_json JSON NULL,
    grid_x INT NOT NULL,
    grid_y INT NOT NULL,
    grid_w INT NOT NULL,
    grid_h INT NOT NULL,
    min_w INT NULL,
    min_h INT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dashboard_widget_key (dashboard_version_id, widget_key),
    KEY idx_yak_dashboard_widget_analysis (analysis_id),
    CONSTRAINT fk_yak_dashboard_widget_version
        FOREIGN KEY (dashboard_version_id) REFERENCES yak_dashboard_version(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_yak_dashboard_widget_analysis
        FOREIGN KEY (analysis_id) REFERENCES yak_analysis(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
