CREATE TABLE IF NOT EXISTS yak_realtime_job_definition (
 id BIGINT NOT NULL AUTO_INCREMENT, job_name VARCHAR(200) NOT NULL, description VARCHAR(1000), spec_json LONGTEXT NOT NULL,
 release_state VARCHAR(16) NOT NULL DEFAULT 'DRAFT', desired_state VARCHAR(16) NOT NULL DEFAULT 'STOPPED', observed_state VARCHAR(32) NOT NULL DEFAULT 'STOPPED',
 definition_version INT NOT NULL DEFAULT 1, config_digest CHAR(64), last_error TEXT, create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_realtime_name(job_name), KEY idx_realtime_states(desired_state,observed_state));
CREATE TABLE IF NOT EXISTS yak_realtime_job_deployment (
 id BIGINT NOT NULL AUTO_INCREMENT, definition_id BIGINT NOT NULL, definition_version INT NOT NULL, spec_snapshot_json LONGTEXT NOT NULL, pipeline_yaml LONGTEXT NOT NULL, config_digest CHAR(64) NOT NULL,
 idempotency_key VARCHAR(128) NOT NULL, gateway_job_id VARCHAR(128), runtime_version VARCHAR(64), status VARCHAR(32) NOT NULL, result_uncertain TINYINT(1) NOT NULL DEFAULT 0, error_message TEXT,
 create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), PRIMARY KEY(id), UNIQUE KEY uk_realtime_idempotency(idempotency_key), KEY idx_realtime_deployment_definition(definition_id,id), CONSTRAINT fk_realtime_deployment_definition FOREIGN KEY(definition_id) REFERENCES yak_realtime_job_definition(id) ON DELETE CASCADE);
CREATE TABLE IF NOT EXISTS yak_realtime_job_event (
 id BIGINT NOT NULL AUTO_INCREMENT, definition_id BIGINT NOT NULL, deployment_id BIGINT, event_type VARCHAR(64) NOT NULL, from_state VARCHAR(32), to_state VARCHAR(32), message TEXT, payload_json LONGTEXT,
 create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY(id), KEY idx_realtime_event_definition(definition_id,id), CONSTRAINT fk_realtime_event_definition FOREIGN KEY(definition_id) REFERENCES yak_realtime_job_definition(id) ON DELETE CASCADE);
