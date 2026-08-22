ALTER TABLE yak_realtime_job_deployment
  ADD COLUMN runtime_job_name VARCHAR(128) NULL AFTER idempotency_key,
  ADD UNIQUE KEY uk_realtime_runtime_job_name(runtime_job_name);

-- Existing deployments intentionally remain NULL. Only submissions created after this migration
-- have a deterministic runtime identity and can be recovered automatically after an uncertain CLI
-- result without guessing from the Flink job list.
