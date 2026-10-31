import type { BadgeProps } from "@yak-ops/yak-ui";

import type {
  GovernanceDimension,
  GovernanceIssueEventType,
  GovernanceIssueStatus,
  GovernanceScanStatus,
  GovernanceSeverity,
} from "@/service/data-governance/asset-governance/types";

export const GOVERNANCE_BASE = "/data-governance/governance";

export const GOVERNANCE_DIMENSION_LABEL: Record<GovernanceDimension, string> = {
  DEV: "数据资产",
  COMPUTE: "计算任务",
};

export const GOVERNANCE_SEVERITY_LABEL: Record<GovernanceSeverity, string> = {
  HIGH: "高",
  MEDIUM: "中",
  LOW: "低",
};

export const GOVERNANCE_SEVERITY_TONE: Record<
  GovernanceSeverity,
  NonNullable<BadgeProps["tone"]>
> = {
  HIGH: "danger",
  MEDIUM: "warning",
  LOW: "neutral",
};

export const GOVERNANCE_STATUS_LABEL: Record<GovernanceIssueStatus, string> = {
  OPEN: "待处理",
  HANDLING: "处理中",
  RESOLVED: "已解决",
  IGNORED: "已忽略",
};

export const GOVERNANCE_STATUS_TONE: Record<
  GovernanceIssueStatus,
  NonNullable<BadgeProps["tone"]>
> = {
  OPEN: "danger",
  HANDLING: "warning",
  RESOLVED: "success",
  IGNORED: "neutral",
};

export const GOVERNANCE_SCAN_STATUS_LABEL: Record<GovernanceScanStatus, string> = {
  PENDING: "排队中",
  RUNNING: "扫描中",
  SUCCEEDED: "已完成",
  PARTIAL: "部分完成",
  FAILED: "失败",
  SKIPPED: "已跳过",
};

export const GOVERNANCE_SCAN_STATUS_TONE: Record<
  GovernanceScanStatus,
  NonNullable<BadgeProps["tone"]>
> = {
  PENDING: "neutral",
  RUNNING: "info",
  SUCCEEDED: "success",
  PARTIAL: "warning",
  FAILED: "danger",
  SKIPPED: "neutral",
};

export const GOVERNANCE_EVENT_LABEL: Record<GovernanceIssueEventType, string> = {
  CREATED: "登记问题",
  REOPENED: "重新打开",
  ASSIGNED: "指派",
  IGNORED: "忽略",
  UNIGNORED: "取消忽略",
  RESOLVED: "手动解决",
  AUTO_RESOLVED: "自动解决",
};

/** 前端持有规则的可读措辞；规则码与证据数值来自后端。 */
export const GOVERNANCE_RULE_LABEL: Record<string, string> = {
  DATA_MAP_TABLE_NO_OWNER: "数据表未指派负责人",
  DATA_MAP_TABLE_NO_DESCRIPTION: "数据表缺少描述",
  TASK_CONSECUTIVE_FAILURES: "任务连续失败",
  TASK_SLOW_RUNS: "任务运行过慢",
};

export const GOVERNANCE_REASON_LABEL: Record<string, string> = {
  DATA_MAP_BUSY: "数据地图采集进行中，本次扫描已跳过",
  APP_RESTART: "应用重启导致扫描中断",
};

export const governanceRuleLabel = (code: string) => GOVERNANCE_RULE_LABEL[code] ?? code;

/** 目标名是限定名；空段产生的开头分隔符不影响语义，展示时去掉。 */
export const governanceTargetLabel = (name: string) => name.replace(/^\.+/, "");

/** 证据句式由前端措辞：规则码决定句子，数值来自 Issue 证据列。 */
export function governanceEvidenceSummary(issue: {
  ruleCode: string;
  observedValue: number | null;
  thresholdValue: number | null;
  sampleCount: number | null;
  windowDays: number | null;
}): string {
  const observed = issue.observedValue ?? 0;
  const window = issue.windowDays ?? 7;
  switch (issue.ruleCode) {
    case "DATA_MAP_TABLE_NO_OWNER":
      return "负责人为空";
    case "DATA_MAP_TABLE_NO_DESCRIPTION":
      return "描述与引擎注释均为空";
    case "TASK_CONSECUTIVE_FAILURES":
      return `连续失败 ${observed} 次，阈值 ${issue.thresholdValue ?? 3} 次（近 ${window} 天定时实例 ${issue.sampleCount ?? 0} 个）`;
    case "TASK_SLOW_RUNS":
      return `平均耗时 ${Math.round(observed)} 秒，阈值 ${issue.thresholdValue ?? 7200} 秒（近 ${window} 天定时实例 ${issue.sampleCount ?? 0} 个）`;
    default:
      return `观测值 ${observed}，阈值 ${issue.thresholdValue ?? "-"}`;
  }
}
