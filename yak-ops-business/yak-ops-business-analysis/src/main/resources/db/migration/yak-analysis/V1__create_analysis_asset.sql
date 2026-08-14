-- Reusable BI Analysis assets: ChartSpec belongs here; Dashboard only owns placement/reference.
CREATE TABLE IF NOT EXISTS yak_analysis (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    dataset_id BIGINT NOT NULL,
    chart_type VARCHAR(32) NOT NULL,
    query_spec_json JSON NOT NULL,
    visual_config_json JSON NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_yak_analysis_dataset_update (dataset_id, update_time),
    KEY idx_yak_analysis_chart_type (chart_type),
    CONSTRAINT fk_yak_analysis_dataset
        FOREIGN KEY (dataset_id) REFERENCES yak_dataset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
