-- Wave 4 data migration: make persisted BatchExecution status reflect the latest bound Attempt.
-- No schema change is required; this backfills Wave 2/3 batches that were created as PENDING
-- before Batch status became runtime truth.
UPDATE yak_offline_batch_execution batch
JOIN yak_offline_job_execution latest
  ON latest.batch_id = batch.id
LEFT JOIN yak_offline_job_execution newer
  ON newer.batch_id = latest.batch_id
 AND (
      newer.attempt_no > latest.attempt_no
      OR (newer.attempt_no = latest.attempt_no AND newer.id > latest.id)
 )
SET batch.status = CASE
      WHEN UPPER(latest.status) IN ('CREATED', 'SUBMITTED', 'QUEUED', 'RUNNING') THEN 'RUNNING'
      WHEN UPPER(latest.status) IN ('UNKNOWN', 'LOST') THEN 'UNKNOWN'
      WHEN UPPER(latest.status) IN ('SUCCEEDED', 'FINISHED', 'COMPLETED') THEN 'SUCCEEDED'
      WHEN UPPER(latest.status) IN ('CANCELED', 'CANCELLED', 'CANCELING', 'CANCELLING') THEN 'CANCELED'
      WHEN UPPER(latest.status) = 'FAILED' AND latest.next_retry_time IS NOT NULL THEN 'WAITING_RETRY'
      WHEN UPPER(latest.status) = 'FAILED' THEN 'FAILED'
      ELSE batch.status
    END,
    batch.update_time = CURRENT_TIMESTAMP(3)
WHERE newer.id IS NULL;
