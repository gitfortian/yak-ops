-- Dashboard runtime interaction definitions are versioned with the composition snapshot.
CREATE TABLE IF NOT EXISTS yak_dashboard_filter (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dashboard_version_id BIGINT NOT NULL,
    filter_key VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    operator VARCHAR(32) NOT NULL,
    default_value_json JSON NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dashboard_filter_key (dashboard_version_id, filter_key),
    CONSTRAINT fk_yak_dashboard_filter_version
        FOREIGN KEY (dashboard_version_id) REFERENCES yak_dashboard_version(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dashboard_filter_binding (
    dashboard_version_id BIGINT NOT NULL,
    filter_key VARCHAR(64) NOT NULL,
    widget_key VARCHAR(64) NOT NULL,
    field_id VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (dashboard_version_id, filter_key, widget_key),
    CONSTRAINT fk_yak_dashboard_filter_binding_filter
        FOREIGN KEY (dashboard_version_id, filter_key)
        REFERENCES yak_dashboard_filter(dashboard_version_id, filter_key)
        ON DELETE CASCADE,
    CONSTRAINT fk_yak_dashboard_filter_binding_widget
        FOREIGN KEY (dashboard_version_id, widget_key)
        REFERENCES yak_dashboard_widget(dashboard_version_id, widget_key)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dashboard_interaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dashboard_version_id BIGINT NOT NULL,
    interaction_key VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    source_widget_key VARCHAR(64) NOT NULL,
    source_field_id VARCHAR(64) NOT NULL,
    target_filter_key VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dashboard_interaction_key (dashboard_version_id, interaction_key),
    KEY idx_yak_dashboard_interaction_source (dashboard_version_id, source_widget_key),
    CONSTRAINT fk_yak_dashboard_interaction_widget
        FOREIGN KEY (dashboard_version_id, source_widget_key)
        REFERENCES yak_dashboard_widget(dashboard_version_id, widget_key)
        ON DELETE CASCADE,
    CONSTRAINT fk_yak_dashboard_interaction_filter
        FOREIGN KEY (dashboard_version_id, target_filter_key)
        REFERENCES yak_dashboard_filter(dashboard_version_id, filter_key)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
