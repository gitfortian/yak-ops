import { Badge, Table, type TableColumns } from "@yak-ops/yak-ui";

import { getRules } from "@/service/data-governance/asset-governance/overview";
import { governanceFailureMessage } from "@/service/data-governance/asset-governance/request";
import type { GovernanceRule } from "@/service/data-governance/asset-governance/types";

import { useQuery } from "@tanstack/react-query";
import { Alert } from "@yak-ops/yak-ui";
import {
  GOVERNANCE_DIMENSION_LABEL,
  GOVERNANCE_SEVERITY_LABEL,
  GOVERNANCE_SEVERITY_TONE,
} from "../labels";

const thresholdText = (thresholds: Record<string, number>) =>
  Object.entries(thresholds)
    .map(([key, value]) => `${key}=${value}`)
    .join(" · ");

const columns: TableColumns<GovernanceRule> = [
  {
    key: "code",
    title: "规则",
    minWidth: 220,
    render: (_value, record) => (
      <div className="min-w-0">
        <div className="truncate text-[13px] font-medium text-[var(--yak-components-panel-text)]">
          {record.code}
        </div>
        <div className="mt-0.5 text-[11px] text-[var(--yak-components-muted-text)]">
          版本 v{record.version}
        </div>
      </div>
    ),
  },
  {
    key: "dimension",
    title: "维度",
    width: 110,
    render: (_value, record) => GOVERNANCE_DIMENSION_LABEL[record.dimension],
  },
  {
    key: "severity",
    title: "严重度",
    width: 90,
    render: (_value, record) => (
      <Badge tone={GOVERNANCE_SEVERITY_TONE[record.severity]}>
        {GOVERNANCE_SEVERITY_LABEL[record.severity]}
      </Badge>
    ),
  },
  { key: "weight", title: "权重", width: 70, align: "right" },
  {
    key: "targetType",
    title: "目标类型",
    width: 130,
    render: (_value, record) =>
      record.targetType === "DATA_MAP_TABLE" ? "数据地图表" : "任务定义",
  },
  {
    key: "thresholds",
    title: "固定阈值",
    minWidth: 180,
    render: (_value, record) => (
      <span className="font-mono text-xs text-[var(--yak-components-muted-text)]">
        {thresholdText(record.thresholds) || "-"}
      </span>
    ),
  },
];

/** 规则为本版本编译进产品的策略，只读展示；无启停开关与阈值编辑。 */
export default function GovernanceRulesPage() {
  const rules = useQuery({
    queryKey: ["governance", "rules"],
    queryFn: ({ signal }) => getRules(signal),
  });

  return (
    <div className="h-full min-h-0 overflow-auto bg-[var(--yak-color-background)] px-6 py-5 max-md:px-4">
      <h2 className="text-base font-bold tracking-tight">治理规则</h2>
      <p className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
        规则由产品编译并始终生效；严重度与权重决定健康分的扣减。
      </p>

      {rules.isError && (
        <Alert className="mt-4">
          {governanceFailureMessage(rules.error, "规则加载失败，请稍后重试。")}
        </Alert>
      )}

      <div className="mt-4 overflow-hidden rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] shadow-[var(--yak-datamap-card-shadow)]">
        <Table
          columns={columns}
          dataSource={rules.data ?? []}
          rowKey="code"
          size="medium"
          loading={rules.isPending}
          emptyText="暂无规则"
          pagination={false}
          scroll={{ x: 860 }}
        />
      </div>
    </div>
  );
}
