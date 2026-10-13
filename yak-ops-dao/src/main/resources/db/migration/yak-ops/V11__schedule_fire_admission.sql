-- Quartz schema layout: quartz-scheduler/quartz v2.5.2, tables_mysql_innodb.sql.
-- Apache License 2.0. Table names are isolated under YAK_QRTZ_; destructive setup statements are omitted.
-- CRON_EXPRESSION is widened to 256 to retain the public schedule expression length.

ALTER TABLE yak_ops_task_instance
  ADD COLUMN schedule_id VARCHAR(64) NULL COMMENT 'Originating schedule; null for historical or non-scheduled runs',
  ADD COLUMN schedule_revision BIGINT NULL COMMENT 'Fired registration revision; null when provenance is absent',
  ADD COLUMN scheduled_time DATETIME(3) NULL COMMENT 'Logical UTC occurrence, separate from execution start time',
  ADD KEY idx_ops_task_instance_schedule (workspace_id, schedule_id, scheduled_time),
  ADD CONSTRAINT ck_ops_task_schedule_provenance CHECK (
    (schedule_id IS NULL AND schedule_revision IS NULL AND scheduled_time IS NULL) OR
    (schedule_id IS NOT NULL AND schedule_revision IS NOT NULL AND schedule_revision >= 1 AND scheduled_time IS NOT NULL));

CREATE TABLE yak_ops_schedule_fire (
  id VARCHAR(64) NOT NULL COMMENT 'Stable final occurrence result ID',
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this result and target',
  schedule_id VARCHAR(64) NOT NULL COMMENT 'Originating schedule identity retained after deletion',
  schedule_revision BIGINT NOT NULL COMMENT 'Immutable fired registration revision',
  target_type VARCHAR(32) NOT NULL COMMENT 'Historical root target category',
  target_id VARCHAR(64) NOT NULL COMMENT 'Historical target Definition identity for authorization',
  scheduled_time DATETIME(3) NOT NULL COMMENT 'Logical UTC occurrence at millisecond precision',
  fire_time DATETIME(3) NOT NULL COMMENT 'Actual UTC callback time, not part of idempotency',
  resolved_time DATETIME(3) NOT NULL COMMENT 'UTC final submission resolution time',
  request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Stable request key in the SCHEDULE namespace',
  outcome VARCHAR(32) NOT NULL COMMENT 'ADMITTED, SKIPPED, REJECTED, or SUBMISSION_FAILED; not Task status',
  reason VARCHAR(64) NULL COMMENT 'Safe non-admission reason; null for ADMITTED',
  instance_id VARCHAR(64) NULL COMMENT 'Admitted Task Instance; null for non-admission',
  create_time DATETIME(3) NOT NULL COMMENT 'Creation audit time',
  update_time DATETIME(3) NOT NULL COMMENT 'Result persistence audit time',
  create_by VARCHAR(64) NOT NULL COMMENT 'Authorizing or configuration-owning actor',
  update_by VARCHAR(64) NOT NULL COMMENT 'Result audit actor',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_schedule_fire_occurrence (workspace_id, schedule_id, schedule_revision, scheduled_time),
  UNIQUE KEY uk_ops_schedule_fire_request (workspace_id, request_id),
  KEY idx_ops_schedule_fire_history (workspace_id, schedule_id, scheduled_time, id),
  CONSTRAINT ck_ops_schedule_fire_revision CHECK (schedule_revision >= 1),
  CONSTRAINT ck_ops_schedule_fire_outcome CHECK (outcome IN ('ADMITTED','SKIPPED','REJECTED','SUBMISSION_FAILED')),
  CONSTRAINT ck_ops_schedule_fire_instance CHECK (
    (outcome = 'ADMITTED' AND instance_id IS NOT NULL AND reason IS NULL) OR
    (outcome <> 'ADMITTED' AND instance_id IS NULL AND reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Final schedule submission results; execution belongs to Task Instances';

CREATE TABLE YAK_QRTZ_JOB_DETAILS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  JOB_NAME VARCHAR(190) NOT NULL COMMENT 'Engine job identity',
  JOB_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine job group',
  DESCRIPTION VARCHAR(250) NULL COMMENT 'Optional engine description',
  JOB_CLASS_NAME VARCHAR(250) NOT NULL COMMENT 'Quartz job class',
  IS_DURABLE VARCHAR(1) NOT NULL COMMENT 'Retain job when no triggers remain',
  IS_NONCONCURRENT VARCHAR(1) NOT NULL COMMENT 'Serialize notification jobs by JobKey',
  IS_UPDATE_DATA VARCHAR(1) NOT NULL COMMENT 'Whether the job persists changed data',
  REQUESTS_RECOVERY VARCHAR(1) NOT NULL COMMENT 'Engine recovery request flag',
  JOB_DATA BLOB NULL COMMENT 'String-only registration properties; no credentials or serialized beans',
  PRIMARY KEY (SCHED_NAME,JOB_NAME,JOB_GROUP)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz job details; engine-owned state';

CREATE TABLE YAK_QRTZ_TRIGGERS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  TRIGGER_NAME VARCHAR(190) NOT NULL COMMENT 'Engine trigger identity',
  TRIGGER_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine trigger group',
  JOB_NAME VARCHAR(190) NOT NULL COMMENT 'Engine job identity',
  JOB_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine job group',
  DESCRIPTION VARCHAR(250) NULL COMMENT 'Optional engine description',
  NEXT_FIRE_TIME BIGINT NULL COMMENT 'Next epoch-millisecond time; null when exhausted',
  PREV_FIRE_TIME BIGINT NULL COMMENT 'Previous epoch-millisecond time',
  PRIORITY INTEGER NULL COMMENT 'Engine acquisition priority',
  TRIGGER_STATE VARCHAR(16) NOT NULL COMMENT 'Engine trigger acquisition state',
  TRIGGER_TYPE VARCHAR(8) NOT NULL COMMENT 'Quartz trigger subtype',
  START_TIME BIGINT NOT NULL COMMENT 'First eligible epoch-millisecond time',
  END_TIME BIGINT NULL COMMENT 'Optional last eligible epoch-millisecond time',
  CALENDAR_NAME VARCHAR(190) NULL COMMENT 'Optional calendar identity',
  MISFIRE_INSTR SMALLINT NULL COMMENT 'Quartz subtype misfire instruction',
  JOB_DATA BLOB NULL COMMENT 'String-only registration properties; no credentials or serialized beans',
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME,JOB_NAME,JOB_GROUP) REFERENCES YAK_QRTZ_JOB_DETAILS(SCHED_NAME,JOB_NAME,JOB_GROUP)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz triggers; engine-owned state';

CREATE TABLE YAK_QRTZ_SIMPLE_TRIGGERS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  TRIGGER_NAME VARCHAR(190) NOT NULL COMMENT 'Engine trigger identity',
  TRIGGER_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine trigger group',
  REPEAT_COUNT BIGINT NOT NULL COMMENT 'Quartz repeat count',
  REPEAT_INTERVAL BIGINT NOT NULL COMMENT 'Quartz repeat interval in milliseconds',
  TIMES_TRIGGERED BIGINT NOT NULL COMMENT 'Engine delivery count',
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP) REFERENCES YAK_QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz simple triggers; engine-owned state';

CREATE TABLE YAK_QRTZ_CRON_TRIGGERS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  TRIGGER_NAME VARCHAR(190) NOT NULL COMMENT 'Engine trigger identity',
  TRIGGER_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine trigger group',
  CRON_EXPRESSION VARCHAR(256) NOT NULL COMMENT 'Quartz Cron expression, matching the schedule API length',
  TIME_ZONE_ID VARCHAR(80) NULL COMMENT 'Named Cron time zone',
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP) REFERENCES YAK_QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz cron triggers; engine-owned state';

CREATE TABLE YAK_QRTZ_SIMPROP_TRIGGERS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  TRIGGER_NAME VARCHAR(190) NOT NULL COMMENT 'Engine trigger identity',
  TRIGGER_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine trigger group',
  STR_PROP_1 VARCHAR(512) NULL COMMENT 'Optional simple trigger string property',
  STR_PROP_2 VARCHAR(512) NULL COMMENT 'Optional simple trigger string property',
  STR_PROP_3 VARCHAR(512) NULL COMMENT 'Optional simple trigger string property',
  INT_PROP_1 INT NULL COMMENT 'Optional simple trigger integer property',
  INT_PROP_2 INT NULL COMMENT 'Optional simple trigger integer property',
  LONG_PROP_1 BIGINT NULL COMMENT 'Optional simple trigger long property',
  LONG_PROP_2 BIGINT NULL COMMENT 'Optional simple trigger long property',
  DEC_PROP_1 NUMERIC(13,4) NULL COMMENT 'Optional simple trigger decimal property',
  DEC_PROP_2 NUMERIC(13,4) NULL COMMENT 'Optional simple trigger decimal property',
  BOOL_PROP_1 VARCHAR(1) NULL COMMENT 'Optional simple trigger boolean property',
  BOOL_PROP_2 VARCHAR(1) NULL COMMENT 'Optional simple trigger boolean property',
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP) REFERENCES YAK_QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz simprop triggers; engine-owned state';

CREATE TABLE YAK_QRTZ_BLOB_TRIGGERS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  TRIGGER_NAME VARCHAR(190) NOT NULL COMMENT 'Engine trigger identity',
  TRIGGER_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine trigger group',
  BLOB_DATA BLOB NULL COMMENT 'Quartz binary trigger state, unused by Cron registrations',
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP) REFERENCES YAK_QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz blob triggers; engine-owned state';

CREATE TABLE YAK_QRTZ_CALENDARS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  CALENDAR_NAME VARCHAR(190) NOT NULL COMMENT 'Quartz calendar identity',
  CALENDAR BLOB NOT NULL COMMENT 'Quartz calendar state',
  PRIMARY KEY (SCHED_NAME,CALENDAR_NAME)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz calendars; engine-owned state';

CREATE TABLE YAK_QRTZ_PAUSED_TRIGGER_GRPS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  TRIGGER_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine trigger group',
  PRIMARY KEY (SCHED_NAME,TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz paused trigger grps; engine-owned state';

CREATE TABLE YAK_QRTZ_FIRED_TRIGGERS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  ENTRY_ID VARCHAR(95) NOT NULL COMMENT 'Quartz callback identifier, not business idempotency',
  TRIGGER_NAME VARCHAR(190) NOT NULL COMMENT 'Engine trigger identity',
  TRIGGER_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine trigger group',
  INSTANCE_NAME VARCHAR(190) NOT NULL COMMENT 'Quartz owner identity',
  FIRED_TIME BIGINT NOT NULL COMMENT 'Actual epoch-millisecond firing time',
  SCHED_TIME BIGINT NOT NULL COMMENT 'Scheduled epoch-millisecond firing time',
  PRIORITY INTEGER NOT NULL COMMENT 'Quartz priority',
  STATE VARCHAR(16) NOT NULL COMMENT 'Engine firing state',
  JOB_NAME VARCHAR(190) NULL COMMENT 'Optional owning job identity',
  JOB_GROUP VARCHAR(190) NULL COMMENT 'Optional owning job group',
  IS_NONCONCURRENT VARCHAR(1) NULL COMMENT 'Engine concurrency flag',
  REQUESTS_RECOVERY VARCHAR(1) NULL COMMENT 'Engine recovery flag',
  PRIMARY KEY (SCHED_NAME,ENTRY_ID)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz fired triggers; engine-owned state';

CREATE TABLE YAK_QRTZ_SCHEDULER_STATE (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  INSTANCE_NAME VARCHAR(190) NOT NULL COMMENT 'Quartz instance identity',
  LAST_CHECKIN_TIME BIGINT NOT NULL COMMENT 'Engine check-in epoch milliseconds',
  CHECKIN_INTERVAL BIGINT NOT NULL COMMENT 'Engine check-in interval',
  PRIMARY KEY (SCHED_NAME,INSTANCE_NAME)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz scheduler state; engine-owned state';

CREATE TABLE YAK_QRTZ_LOCKS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  LOCK_NAME VARCHAR(40) NOT NULL COMMENT 'Quartz store lock identity',
  PRIMARY KEY (SCHED_NAME,LOCK_NAME)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz locks; engine-owned state';

CREATE INDEX IDX_YAK_QRTZ_J_REQ_RECOVERY ON YAK_QRTZ_JOB_DETAILS(SCHED_NAME,REQUESTS_RECOVERY);
CREATE INDEX IDX_YAK_QRTZ_J_GRP ON YAK_QRTZ_JOB_DETAILS(SCHED_NAME,JOB_GROUP);
CREATE INDEX IDX_YAK_QRTZ_T_J ON YAK_QRTZ_TRIGGERS(SCHED_NAME,JOB_NAME,JOB_GROUP);
CREATE INDEX IDX_YAK_QRTZ_T_JG ON YAK_QRTZ_TRIGGERS(SCHED_NAME,JOB_GROUP);
CREATE INDEX IDX_YAK_QRTZ_T_C ON YAK_QRTZ_TRIGGERS(SCHED_NAME,CALENDAR_NAME);
CREATE INDEX IDX_YAK_QRTZ_T_G ON YAK_QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_GROUP);
CREATE INDEX IDX_YAK_QRTZ_T_STATE ON YAK_QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_STATE);
CREATE INDEX IDX_YAK_QRTZ_T_N_STATE ON YAK_QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP,TRIGGER_STATE);
CREATE INDEX IDX_YAK_QRTZ_T_N_G_STATE ON YAK_QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_GROUP,TRIGGER_STATE);
CREATE INDEX IDX_YAK_QRTZ_T_NEXT_FIRE_TIME ON YAK_QRTZ_TRIGGERS(SCHED_NAME,NEXT_FIRE_TIME);
CREATE INDEX IDX_YAK_QRTZ_T_NFT_ST ON YAK_QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_STATE,NEXT_FIRE_TIME);
CREATE INDEX IDX_YAK_QRTZ_T_NFT_MISFIRE ON YAK_QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME);
CREATE INDEX IDX_YAK_QRTZ_T_NFT_ST_MISFIRE ON YAK_QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME,TRIGGER_STATE);
CREATE INDEX IDX_YAK_QRTZ_T_NFT_ST_MISFIRE_GRP ON YAK_QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME,TRIGGER_GROUP,TRIGGER_STATE);
CREATE INDEX IDX_YAK_QRTZ_FT_TRIG_INST_NAME ON YAK_QRTZ_FIRED_TRIGGERS(SCHED_NAME,INSTANCE_NAME);
CREATE INDEX IDX_YAK_QRTZ_FT_INST_JOB_REQ_RCVRY ON YAK_QRTZ_FIRED_TRIGGERS(SCHED_NAME,INSTANCE_NAME,REQUESTS_RECOVERY);
CREATE INDEX IDX_YAK_QRTZ_FT_J_G ON YAK_QRTZ_FIRED_TRIGGERS(SCHED_NAME,JOB_NAME,JOB_GROUP);
CREATE INDEX IDX_YAK_QRTZ_FT_JG ON YAK_QRTZ_FIRED_TRIGGERS(SCHED_NAME,JOB_GROUP);
CREATE INDEX IDX_YAK_QRTZ_FT_T_G ON YAK_QRTZ_FIRED_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP);
CREATE INDEX IDX_YAK_QRTZ_FT_TG ON YAK_QRTZ_FIRED_TRIGGERS(SCHED_NAME,TRIGGER_GROUP);
