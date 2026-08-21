-- A realtime task is now created before its pipeline is configured.
ALTER TABLE yak_realtime_job_definition MODIFY COLUMN spec_json LONGTEXT NULL;
