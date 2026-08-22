CREATE TABLE IF NOT EXISTS yak_compute_environment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  engine_type VARCHAR(32) NOT NULL DEFAULT 'FLINK_CDC',
  deployment_mode VARCHAR(32) NOT NULL DEFAULT 'REMOTE',
  submitter_type VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
  config_json LONGTEXT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  is_default TINYINT(1) NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 1,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_compute_environment_name (name),
  KEY idx_compute_environment_default (is_default, enabled)
);
