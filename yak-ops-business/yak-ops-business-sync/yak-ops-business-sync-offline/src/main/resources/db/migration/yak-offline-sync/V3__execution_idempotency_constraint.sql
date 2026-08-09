ALTER TABLE yak_offline_job_execution
    ADD UNIQUE KEY uk_yak_offline_execution_idempotency (idempotency_key);
