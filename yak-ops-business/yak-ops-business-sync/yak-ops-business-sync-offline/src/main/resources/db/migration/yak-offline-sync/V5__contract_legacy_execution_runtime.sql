-- Wave 6 contract migration: LOST is a legacy read alias only; persisted runtime state is UNKNOWN.
-- Wave 1 前 batch_id IS NULL 的 execution 保持原样作为只读历史，不尝试猜测 Batch identity。
UPDATE yak_offline_job_execution
SET status = 'UNKNOWN'
WHERE UPPER(status) = 'LOST';

UPDATE yak_offline_job_definition
SET last_job_status = 'UNKNOWN'
WHERE UPPER(last_job_status) = 'LOST';
