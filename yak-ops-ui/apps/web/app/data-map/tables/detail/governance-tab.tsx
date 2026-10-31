import { Alert, Badge, Spinner } from "@yak-ops/yak-ui";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";

import { relativeTime } from "@/app/data-map/shared/labels";
import { pageIssues } from "@/service/data-governance/asset-governance/issues";
import { governanceFailureMessage } from "@/service/data-governance/asset-governance/request";

import {
  governanceEvidenceSummary,
  governanceRuleLabel,
  GOVERNANCE_STATUS_LABEL,
  GOVERNANCE_STATUS_TONE,
} from "../../../data-governance/asset-governance/labels";

interface Props {
  tableId: string;
}

const PAGE_SIZE = 20;

/** 资产详情的治理页签：只读展示该资产的治理问题，编辑发生在治理模块。 */
export default function GovernanceTab({ tableId }: Props) {
  const issues = useQuery({
    queryKey: ["governance", "issues", { targetType: "DATA_MAP_TABLE", targetId: tableId }],
    queryFn: ({ signal }) =>
      pageIssues(
        { targetType: "DATA_MAP_TABLE", targetId: tableId, pageNo: 1, pageSize: PAGE_SIZE },
        signal,
      ),
    placeholderData: keepPreviousData,
  });

  if (issues.isPending) {
    return (
      <div className="flex items-center gap-2 py-10 text-xs text-[var(--yak-components-muted-text)]">
        <Spinner size="small" />
        正在加载治理问题
      </div>
    );
  }

  if (issues.isError) {
    return (
      <Alert>{governanceFailureMessage(issues.error, "治理问题加载失败，请稍后重试。")}</Alert>
    );
  }

  const records = issues.data.bizData;

  if (records.length === 0) {
    return (
      <p className="py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
        该资产当前没有治理问题
      </p>
    );
  }

  return (
    <div className="space-y-2.5">
      <p className="text-xs text-[var(--yak-components-muted-text)]">
        共 {issues.data.pagination.total} 个问题；指派、解决与忽略在
        <Link
          to="/data-governance/governance/issues"
          className="mx-1 [color:var(--yak-color-primary)] hover:underline"
        >
          治理问题
        </Link>
        中处理。
      </p>
      {records.map((issue) => (
        <Link
          key={issue.id}
          to={`/data-governance/governance/issues/${issue.id}`}
          className="block rounded-[var(--yak-radius-md)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-collapse-section-bg)] px-3.5 py-3 transition-colors hover:border-[var(--yak-color-primary-ring)]"
        >
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={GOVERNANCE_STATUS_TONE[issue.status]}>
              {GOVERNANCE_STATUS_LABEL[issue.status]}
            </Badge>
            <span className="text-[13px] font-medium text-[var(--yak-components-panel-text)]">
              {governanceRuleLabel(issue.ruleCode)}
            </span>
            <span className="text-[11px] text-[var(--yak-components-muted-text)]">
              最近发现 {relativeTime(issue.lastDetectedAt)}
            </span>
          </div>
          <p className="mt-1.5 text-xs leading-5 text-[var(--yak-components-muted-text)]">
            {governanceEvidenceSummary(issue)}
          </p>
        </Link>
      ))}
    </div>
  );
}
