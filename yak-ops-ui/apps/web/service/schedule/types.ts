/** Task scheduling intent is independent of publication and physical execution. */
export interface ScheduleTimeRule {
  cronExpression: string;
  timeZone: string;
}

export interface ScheduleSummary extends ScheduleTimeRule {
  id: string;
  targetType: "TASK";
  targetId: string;
  enabled: boolean;
  revision: number;
  registrationStatus: "PENDING" | "APPLIED" | "ERROR";
  registrationReason?: string | null;
  registrationObservedTime?: string | null;
  nextFireTime?: string | null;
  exhausted: boolean;
}

export interface ScheduleRecord extends ScheduleSummary {
  schedulable: boolean;
  eligibilityReason?: string | null;
}

export type ScheduleFireOutcome = "ADMITTED" | "SKIPPED" | "REJECTED" | "SUBMISSION_FAILED";

export interface ScheduleFireRecord {
  id: string;
  scheduleId: string;
  scheduleRevision: number;
  scheduledTime: string;
  fireTime: string;
  resolvedTime: string;
  outcome: ScheduleFireOutcome;
  reason: string | null;
  instanceId: string | null;
}

export interface SchedulePage<T> {
  bizData: T[];
  pagination: { pageNo: number; pageSize: number; total: number; pages: number };
}

export interface SchedulePreviewRecord {
  after: string;
  nextFireTimes: string[];
}

export interface ScheduleFireQuery {
  pageNo: number;
  pageSize: number;
  outcome?: ScheduleFireOutcome;
  from?: string;
  until?: string;
}
