import { Alert, Badge, Button } from "@yak-ops/yak-ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Activity, AlertTriangle, ShieldCheck, Timer, TriangleAlert } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";

import { getHealth, getOverview } from "@/service/data-governance/asset-governance/overview";
import { getScan, pageScans, submitScan } from "@/service/data-governance/asset-governance/scans";
import {
  governanceFailureMessage,
  isScanBusy,
} from "@/service/data-governance/asset-governance/request";
import type {
  GovernanceDimensionScore,
  GovernanceScan,
} from "@/service/data-governance/asset-governance/types";
import { relativeTime } from "@/app/data-map/shared/labels";

import {
  GOVERNANCE_DIMENSION_LABEL,
  GOVERNANCE_REASON_LABEL,
  GOVERNANCE_RULE_LABEL,
  GOVERNANCE_SCAN_STATUS_LABEL,
  GOVERNANCE_SCAN_STATUS_TONE,
} from "../labels";
import HealthTrend, { HealthTrendLegend } from "../health-trend";
import ScanDetailDialog from "../scan-detail-dialog";

const RANGE_OPTIONS = [30, 90] as const;

function scoreText(dimension: Pick<GovernanceDimensionScore, "evaluated" | "score">) {
  return dimension.evaluated && dimension.score != null
    ? Number(dimension.score).toFixed(1)
    : "N/A";
}

function DimensionCard({
  dimension,
}: {
  dimension: Pick<
    GovernanceDimensionScore,
    | "dimensionCode"
    | "score"
    | "evaluated"
    | "baseCount"
    | "weightedIssueCount"
    | "highCount"
    | "mediumCount"
    | "lowCount"
  >;
}) {
  const evaluated = dimension.evaluated && dimension.score != null;
  const counts = [
    { tone: "danger" as const, label: "高", value: dimension.highCount ?? 0 },
    { tone: "warning" as const, label: "中", value: dimension.mediumCount ?? 0 },
    { tone: "neutral" as const, label: "低", value: dimension.lowCount ?? 0 },
  ];
  return (
    <div className="rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-5 shadow-[var(--yak-datamap-card-shadow)]">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-[var(--yak-components-panel-text)]">
          {GOVERNANCE_DIMENSION_LABEL[dimension.dimensionCode]}
        </h3>
        <span className="text-[11px] text-[var(--yak-components-muted-text)]">
          基数 {dimension.baseCount ?? 0}
        </span>
      </div>
      <div className="mt-2 flex items-end gap-3">
        <span
          className={`text-[32px] font-bold leading-9 tracking-tight [font-variant-numeric:tabular-nums] ${
            evaluated
              ? "text-[var(--yak-components-panel-text)]"
              : "text-[var(--yak-components-muted-text)]"
          }`}
        >
          {scoreText(dimension)}
        </span>
        {evaluated && (
          <span className="pb-1.5 text-[11px] text-[var(--yak-components-muted-text)]">
            加权问题 {Number(dimension.weightedIssueCount ?? 0).toFixed(0)}
          </span>
        )}
      </div>
      {!evaluated && (
        <p className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
          该维度暂无可评估的基数
        </p>
      )}
      <div className="mt-3 flex flex-wrap items-center gap-1.5">
        {counts.map((count) => (
          <Badge key={count.label} tone={count.tone}>
            {count.label} {count.value}
          </Badge>
        ))}
      </div>
    </div>
  );
}

export default function GovernanceOverviewPage() {
  const queryClient = useQueryClient();
  const [rangeDays, setRangeDays] = useState<(typeof RANGE_OPTIONS)[number]>(30);
  const [scanId, setScanId] = useState<string | null>(null);
  const [scanError, setScanError] = useState("");

  const overview = useQuery({
    queryKey: ["governance", "overview"],
    queryFn: ({ signal }) => getOverview(signal),
  });

  const range = useMemo(() => {
    const to = new Date();
    const from = new Date(to.getTime() - (rangeDays - 1) * 86400000);
    return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
  }, [rangeDays]);

  const health = useQuery({
    queryKey: ["governance", "health", range.from, range.to],
    queryFn: ({ signal }) => getHealth(range.from, range.to, signal),
  });

  const scans = useQuery({
    queryKey: ["governance", "scans", 1, 5],
    queryFn: ({ signal }) => pageScans(1, 5, signal),
    refetchInterval: (query) =>
      (query.state.data?.bizData ?? []).some(
        (run) => run.status === "PENDING" || run.status === "RUNNING",
      )
        ? 3000
        : false,
  });

  /** 轮询发现最新一次运行从活跃变为终态时，刷新得分与计数。 */
  const lastRunStatus = scans.data?.bizData[0]?.status;
  const previousStatus = useRef(lastRunStatus);
  useEffect(() => {
    if (
      previousStatus.current != null &&
      previousStatus.current !== lastRunStatus &&
      lastRunStatus !== "PENDING" &&
      lastRunStatus !== "RUNNING"
    ) {
      void queryClient.invalidateQueries({ queryKey: ["governance", "overview"] });
      void queryClient.invalidateQueries({ queryKey: ["governance", "health"] });
    }
    previousStatus.current = lastRunStatus;
  }, [lastRunStatus, queryClient]);

  const scanDetail = useQuery({
    queryKey: ["governance", "scan", scanId],
    queryFn: ({ signal }) => getScan(scanId!, signal),
    enabled: Boolean(scanId),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["governance"] });
  };

  const requestScan = useMutation({
    mutationFn: submitScan,
    onSuccess: () => {
      setScanError("");
      invalidate();
    },
    onError: (error) => {
      setScanError(
        isScanBusy(error)
          ? "已有扫描在进行中，请稍后再试。"
          : governanceFailureMessage(error, "扫描提交失败，请稍后重试"),
      );
    },
  });

  const counts = overview.data?.issueCounts;
  const overall = overview.data?.overall ?? null;
  const runs = scans.data?.bizData ?? [];

  return (
    <div className="h-full min-h-0 overflow-auto bg-[var(--yak-color-background)] px-6 py-5 max-md:px-4">
      {overview.isError && (
        <Alert className="mb-4">
          {governanceFailureMessage(overview.error, "治理总览加载失败，请稍后重试。")}
          <Button size="small" className="ml-3" onClick={() => void overview.refetch()}>
            重试
          </Button>
        </Alert>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-bold tracking-tight">治理总览</h2>
          <p className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
            {overview.data?.snapshotDate
              ? `快照日期 ${overview.data.snapshotDate} · 公式 ${overview.data.formulaVersion ?? "-"}`
              : "尚未生成健康分快照，完成一次扫描后开始计分"}
          </p>
        </div>
        <Button
          size="small"
          variant="primary"
          loading={requestScan.isPending}
          onClick={() => requestScan.mutate()}
        >
          立即扫描
        </Button>
      </div>

      {scanError && <Alert className="mt-4">{scanError}</Alert>}

      <section className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-3">
        <div className="rounded-[var(--yak-radius-lg)] border border-[var(--yak-color-primary-ring)] bg-[image:var(--yak-datamap-hero-gradient)] p-5 shadow-[var(--yak-datamap-card-shadow)]">
          <div className="flex items-center gap-2">
            <ShieldCheck size={15} aria-hidden="true" className="text-[var(--yak-color-primary)]" />
            <h3 className="text-sm font-bold">综合健康分</h3>
          </div>
          <div className="mt-2 flex items-end gap-3">
            <span
              className={`text-[40px] font-bold leading-10 tracking-tight [font-variant-numeric:tabular-nums] ${
                overall?.evaluated
                  ? "text-[var(--yak-color-primary)]"
                  : "text-[var(--yak-components-muted-text)]"
              }`}
            >
              {scoreText(overall ?? { evaluated: false, score: null })}
            </span>
            {overall?.evaluated && (
              <span className="pb-1 text-xs text-[var(--yak-components-muted-text)]">
                覆盖 {overview.data?.evaluatedDimensionCount ?? 0}/2 个维度
              </span>
            )}
          </div>
          <div className="mt-3 flex flex-wrap gap-1.5">
            {(overview.data?.dimensions ?? []).map((dimension) => (
              <span
                key={dimension.dimensionCode}
                className="rounded-full bg-[var(--yak-color-primary-subtle)] px-2.5 py-0.5 text-[11px] text-[var(--yak-color-primary)]"
              >
                {GOVERNANCE_DIMENSION_LABEL[dimension.dimensionCode]} {scoreText(dimension)}
              </span>
            ))}
          </div>
        </div>

        {(overview.data?.dimensions ?? []).map((dimension) => (
          <DimensionCard key={dimension.dimensionCode} dimension={dimension} />
        ))}
        {(overview.data?.dimensions.length ?? 0) === 0 && (
          <>
            {["DEV", "COMPUTE"].map((code) => (
              <div
                key={code}
                className="rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-5 shadow-[var(--yak-datamap-card-shadow)]"
              >
                <h3 className="text-sm font-bold">{GOVERNANCE_DIMENSION_LABEL[code as "DEV"]}</h3>
                <span className="mt-2 block text-[32px] font-bold leading-9 text-[var(--yak-components-muted-text)]">
                  N/A
                </span>
                <p className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
                  该维度暂无可评估的基数
                </p>
              </div>
            ))}
          </>
        )}
      </section>

      <section className="mt-4 grid grid-cols-2 gap-3 md:grid-cols-5">
        {[
          { label: "问题总数", value: counts?.total ?? 0, icon: Activity },
          { label: "待处理", value: counts?.open ?? 0, icon: AlertTriangle },
          { label: "处理中", value: counts?.handling ?? 0, icon: Timer },
          { label: "已忽略", value: counts?.ignored ?? 0, icon: TriangleAlert },
          { label: "已解决", value: counts?.resolved ?? 0, icon: ShieldCheck },
        ].map((item) => {
          const Icon = item.icon;
          return (
            <div
              key={item.label}
              className="flex items-center gap-3 rounded-[var(--yak-radius-md)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-3.5 shadow-[var(--yak-datamap-card-shadow)]"
            >
              <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
                <Icon size={14} aria-hidden="true" />
              </span>
              <span>
                <span className="block text-lg font-bold leading-5 text-[var(--yak-components-panel-text)] [font-variant-numeric:tabular-nums]">
                  {item.value}
                </span>
                <span className="text-[11px] text-[var(--yak-components-muted-text)]">
                  {item.label}
                </span>
              </span>
            </div>
          );
        })}
      </section>

      <section className="mt-6 grid grid-cols-1 items-start gap-4 xl:grid-cols-[1.7fr_1fr]">
        <div className="rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-4 shadow-[var(--yak-datamap-card-shadow)]">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-[13px] font-bold">健康分趋势</h3>
            <div className="flex items-center gap-3">
              <HealthTrendLegend />
              <span className="flex overflow-hidden rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-control-border)]">
                {RANGE_OPTIONS.map((option) => (
                  <button
                    key={option}
                    type="button"
                    aria-pressed={rangeDays === option}
                    onClick={() => setRangeDays(option)}
                    className={`h-7 cursor-pointer px-2.5 text-xs transition-colors ${
                      rangeDays === option
                        ? "bg-[var(--yak-color-primary-subtle)] font-medium text-[var(--yak-color-primary)]"
                        : "bg-[var(--yak-datamap-surface)] text-[var(--yak-components-muted-text)]"
                    }`}
                  >
                    {option} 天
                  </button>
                ))}
              </span>
            </div>
          </div>
          <div className="mt-2">
            {health.isError ? (
              <p className="py-10 text-center text-xs text-[var(--yak-components-muted-text)]">
                趋势加载失败
              </p>
            ) : (health.data?.points.length ?? 0) === 0 ? (
              <p className="py-10 text-center text-xs text-[var(--yak-components-muted-text)]">
                暂无快照，完成一次扫描后开始记录趋势
              </p>
            ) : (
              <HealthTrend
                points={health.data?.points ?? []}
                from={health.data?.from ?? range.from}
                to={health.data?.to ?? range.to}
              />
            )}
          </div>
        </div>

        <div className="overflow-hidden rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] shadow-[var(--yak-datamap-card-shadow)]">
          <div className="border-b border-[var(--yak-components-panel-border)] px-4 py-3">
            <h3 className="text-[13px] font-bold">扫描记录</h3>
          </div>
          <div className="divide-y divide-[var(--yak-datamap-divider-soft)] px-4">
            {runs.length === 0 && !scans.isPending && (
              <p className="py-6 text-center text-xs text-[var(--yak-components-muted-text)]">
                暂无扫描记录
              </p>
            )}
            {runs.map((run: GovernanceScan) => (
              <button
                key={run.id}
                type="button"
                onClick={() => setScanId(run.id)}
                className="flex w-full cursor-pointer items-center gap-2.5 py-2.5 text-left transition-colors hover:bg-[var(--yak-color-hover)]"
              >
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-2">
                    <Badge tone={GOVERNANCE_SCAN_STATUS_TONE[run.status]}>
                      {GOVERNANCE_SCAN_STATUS_LABEL[run.status]}
                    </Badge>
                    {run.reasonCode && (
                      <span className="truncate text-[11px] text-[var(--yak-components-muted-text)]">
                        {GOVERNANCE_REASON_LABEL[run.reasonCode] ?? run.reasonCode}
                      </span>
                    )}
                  </span>
                  <span className="mt-0.5 block text-[11px] text-[var(--yak-components-muted-text)]">
                    新增 {run.issueCreatedCount} · 重开 {run.issueReopenedCount} · 解决{" "}
                    {run.issueResolvedCount} · {relativeTime(run.createTime)}
                  </span>
                </span>
              </button>
            ))}
          </div>
        </div>
      </section>

      {(overview.data?.ruleCounts ?? []).length > 0 && (
        <section className="mt-6">
          <h3 className="text-[13px] font-bold">按规则分布</h3>
          <div className="mt-3 flex flex-wrap gap-2">
            {(overview.data?.ruleCounts ?? []).map((ruleCount) => (
              <Link
                key={ruleCount.ruleCode}
                to={`/data-governance/governance/issues?ruleCode=${ruleCount.ruleCode}`}
                className="inline-flex items-center gap-2 rounded-full border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] px-3 py-1.5 text-xs text-[var(--yak-components-panel-text)] shadow-[var(--yak-datamap-card-shadow)] transition-colors hover:border-[var(--yak-color-primary)] hover:text-[var(--yak-color-primary)]"
              >
                {GOVERNANCE_RULE_LABEL[ruleCount.ruleCode] ?? ruleCount.ruleCode}
                <b className="text-[var(--yak-color-primary)] [font-variant-numeric:tabular-nums]">
                  {ruleCount.count}
                </b>
              </Link>
            ))}
          </div>
        </section>
      )}

      {scanId && (
        <ScanDetailDialog
          detail={scanDetail.data}
          onClose={() => {
            setScanId(null);
            invalidate();
          }}
        />
      )}
    </div>
  );
}
