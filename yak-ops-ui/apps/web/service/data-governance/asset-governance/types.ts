/** Shared shapes of the asset governance HTTP contract. Non-null-absent fields follow the
 * server's non-null JSON inclusion: optional here means the server omitted it. */
import type { Paging } from "@/service/data-map/types";

export type GovernanceDimension = "DEV" | "COMPUTE";
export type GovernanceSeverity = "HIGH" | "MEDIUM" | "LOW";
export type GovernanceIssueStatus = "OPEN" | "HANDLING" | "RESOLVED" | "IGNORED";
export type GovernanceScanStatus =
  | "PENDING"
  | "RUNNING"
  | "SUCCEEDED"
  | "PARTIAL"
  | "FAILED"
  | "SKIPPED";
export type GovernanceTargetType = "DATA_MAP_TABLE" | "TASK_DEFINITION";

export interface GovernanceDimensionScore {
  dimensionCode: GovernanceDimension;
  score: number | null;
  baseCount: number | null;
  weightedIssueCount: number | null;
  issueCount: number | null;
  highCount: number | null;
  mediumCount: number | null;
  lowCount: number | null;
  evaluated: boolean;
}

export interface GovernanceIssueCounts {
  total: number;
  open: number;
  handling: number;
  ignored: number;
  resolved: number;
  high: number;
  medium: number;
  low: number;
}

export interface GovernanceRuleCount {
  ruleCode: string;
  count: number;
}

export interface GovernanceScan {
  id: string;
  triggerType: "SCHEDULED" | "MANUAL";
  status: GovernanceScanStatus;
  ruleCount: number;
  succeededRuleCount: number;
  failedRuleCount: number;
  issueCreatedCount: number;
  issueReopenedCount: number;
  issueResolvedCount: number;
  reasonCode: string | null;
  startTime: string | null;
  finishTime: string | null;
  createTime: string;
  createBy: string;
  durationMillis: number | null;
}

export interface GovernanceOverview {
  dimensions: GovernanceDimensionScore[];
  overall: GovernanceDimensionScore | null;
  evaluatedDimensionCount: number;
  snapshotDate: string | null;
  formulaVersion: string | null;
  issueCounts: GovernanceIssueCounts;
  ruleCounts: GovernanceRuleCount[];
  lastScan: GovernanceScan | null;
}

export interface GovernanceRule {
  code: string;
  version: number;
  dimension: GovernanceDimension;
  severity: GovernanceSeverity;
  weight: number;
  targetType: GovernanceTargetType;
  thresholds: Record<string, number>;
}

export interface GovernanceIssue {
  id: string;
  ruleCode: string;
  ruleVersion: number;
  dimension: GovernanceDimension;
  severity: GovernanceSeverity;
  targetType: GovernanceTargetType;
  targetId: string;
  targetName: string;
  status: GovernanceIssueStatus;
  ownerId: string | null;
  ignoreUntil: string | null;
  resolvedAt: string | null;
  resolvedBy: string | null;
  resolution: "USER" | "AUTO" | null;
  observedValue: number | null;
  thresholdValue: number | null;
  sampleCount: number | null;
  windowDays: number | null;
  firstDetectedAt: string;
  lastDetectedAt: string;
  lastScanId: string | null;
  revision: number;
}

export interface GovernanceIssueDetail {
  issue: GovernanceIssue;
  rule: GovernanceRule;
  events: GovernanceIssueEvent[];
}

export type GovernanceIssueEventType =
  | "CREATED"
  | "REOPENED"
  | "ASSIGNED"
  | "IGNORED"
  | "UNIGNORED"
  | "RESOLVED"
  | "AUTO_RESOLVED";

export interface GovernanceIssueEvent {
  id: string;
  eventType: GovernanceIssueEventType;
  fromStatus: string | null;
  toStatus: string | null;
  ownerId: string | null;
  ignoreUntil: string | null;
  note: string | null;
  actorId: string;
  createTime: string;
}

export interface GovernanceIssueQuery {
  status?: GovernanceIssueStatus;
  severity?: GovernanceSeverity;
  dimension?: GovernanceDimension;
  ruleCode?: string;
  ownerId?: string;
  targetType?: GovernanceTargetType;
  targetId?: string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface GovernanceHealthPoint {
  snapshotDate: string;
  dimensionCode: string;
  score: number | null;
  baseCount: number | null;
  weightedIssueCount: number | null;
  issueCount: number | null;
  highCount: number | null;
  mediumCount: number | null;
  lowCount: number | null;
}

export interface GovernanceHealth {
  from: string;
  to: string;
  points: GovernanceHealthPoint[];
}

export interface GovernanceRuleResult {
  ruleCode: string;
  ruleVersion: number;
  status: "RUNNING" | "SUCCEEDED" | "FAILED";
  findingCount: number;
  issueCreatedCount: number;
  issueReopenedCount: number;
  issueResolvedCount: number;
  reasonCode: string | null;
  startTime: string | null;
  finishTime: string | null;
}

export interface GovernanceScanDetail {
  scan: GovernanceScan;
  rules: GovernanceRuleResult[];
}

export interface GovernanceIssueBatch {
  issueIds: string[];
  ownerId?: string;
  ignoreUntil?: string;
  note?: string;
}

export type { Paging };
