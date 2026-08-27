ALTER TABLE yak_quality_execution
  ADD INDEX idx_yak_quality_execution_queued (queued_at, id);
