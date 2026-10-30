import type { CrawlSummary } from "@/service/data-map/types";

interface MetricProps {
  label: string;
  value: string;
  unit?: string;
  detail: string;
  tone?: "default" | "danger";
  active?: boolean;
  onClick?: () => void;
}

/**
 * One health metric. Only the actionable metric is a button; the other three stay read-only so the
 * band keeps a single call to action.
 */
function Metric({ label, value, unit, detail, tone = "default", active, onClick }: MetricProps) {
  const className = [
    "rounded-[var(--yak-radius-control-medium)] border bg-[var(--yak-components-panel-bg)] px-3.5 py-3 text-left",
    "border-[var(--yak-components-panel-border)]",
    tone === "danger" ? "border-l-2 border-l-[var(--yak-components-badge-danger-text)]" : "",
    onClick
      ? "cursor-pointer transition-colors hover:border-[var(--yak-components-control-border)]"
      : "",
    active ? "bg-[var(--yak-components-badge-danger-bg)]" : "",
  ]
    .filter(Boolean)
    .join(" ");

  const body = (
    <>
      <div className="text-[11px] text-[var(--yak-components-muted-text)]">{label}</div>
      <div className="mt-1 flex items-baseline gap-1">
        <span
          className={`text-[20px] leading-6 font-semibold tabular-nums ${
            tone === "danger"
              ? "text-[var(--yak-components-badge-danger-text)]"
              : "text-[var(--yak-components-panel-text)]"
          }`}
        >
          {value}
        </span>
        {unit && (
          <span className="text-[11px] text-[var(--yak-components-muted-text)]">{unit}</span>
        )}
      </div>
      <div className="mt-1 truncate text-[11px] text-[var(--yak-components-muted-text)]">
        {detail}
      </div>
    </>
  );

  if (!onClick) return <div className={className}>{body}</div>;
  return (
    <button type="button" className={className} aria-pressed={active} onClick={onClick}>
      {body}
    </button>
  );
}

interface Outcome {
  key: string;
  label: string;
  count: number;
  color: string;
}

/**
 * One segment per outcome that actually occurred; zero-count outcomes are dropped, because a
 * legend naming states nobody experienced is noise.
 */
function outcomeSegments(summary: CrawlSummary): Outcome[] {
  return [
    {
      key: "succeeded",
      label: "成功",
      count: summary.runSucceeded,
      color: "var(--yak-components-badge-success-text)",
    },
    {
      key: "partial",
      label: "部分成功",
      count: summary.runPartial,
      color: "var(--yak-components-badge-warning-text)",
    },
    {
      key: "failed",
      label: "失败",
      count: summary.runFailed,
      color: "var(--yak-components-badge-danger-text)",
    },
    {
      key: "running",
      label: "采集中",
      count: summary.runRunning,
      color: "var(--yak-components-badge-info-text)",
    },
    {
      key: "pending",
      label: "排队中",
      count: summary.runPending,
      color: "var(--yak-components-badge-neutral-text)",
    },
    {
      key: "interrupted",
      label: "中断",
      count: summary.runInterrupted,
      color: "var(--yak-components-badge-neutral-text)",
    },
  ].filter((outcome) => outcome.count > 0);
}

/** The run window as a single quiet line: total, a proportion bar, and a per-outcome legend. */
function RunWindow({ summary }: { summary: CrawlSummary }) {
  const outcomes = outcomeSegments(summary);
  return (
    <div className="flex items-center gap-3 px-3.5 py-1.5 text-[11px] text-[var(--yak-components-muted-text)] max-md:flex-wrap">
      <span className="shrink-0">
        近 {summary.windowHours} 小时 {summary.runTotal} 次采集
      </span>
      {outcomes.length > 0 ? (
        <>
          <div
            className="flex h-1.5 min-w-24 flex-1 overflow-hidden rounded-full bg-[var(--yak-color-surface-muted)] max-md:basis-full"
            role="img"
            aria-label={`近 ${summary.windowHours} 小时采集结果分布`}
          >
            {outcomes.map((outcome) => (
              <div
                key={outcome.key}
                style={{
                  width: `${(outcome.count / summary.runTotal) * 100}%`,
                  background: outcome.color,
                }}
              />
            ))}
          </div>
          <span className="flex shrink-0 flex-wrap gap-x-3 gap-y-0.5">
            {outcomes.map((outcome) => (
              <span key={outcome.key}>
                <span style={{ color: outcome.color }}>{outcome.label}</span> {outcome.count}
              </span>
            ))}
          </span>
        </>
      ) : (
        <span>没有采集记录</span>
      )}
    </div>
  );
}

interface Props {
  summary: CrawlSummary;
  managedDatasources: number;
  datasourceTotal: number;
  assetCount: number;
  newTablesLastSevenDays: number;
  onlyAbnormal: boolean;
  onToggleAbnormal: () => void;
}

/**
 * Comment coverage of the active assets' columns. A workspace without assets shows a dash rather
 * than 100%, because "every column is described" would be a lie when there is nothing to describe.
 */
function coverageLabel(summary: CrawlSummary): string {
  if (summary.columnTotal <= 0) return "字段描述 -";
  return `字段描述 ${Math.round((summary.describedColumnCount / summary.columnTotal) * 100)}%`;
}

export default function HealthBand({
  summary,
  managedDatasources,
  datasourceTotal,
  assetCount,
  newTablesLastSevenDays,
  onlyAbnormal,
  onToggleAbnormal,
}: Props) {
  return (
    <div className="shrink-0 rounded-[var(--yak-radius-control-medium)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)]">
      <div className="grid grid-cols-4 gap-3 p-3 max-lg:grid-cols-2">
        <Metric
          label="采集任务"
          value={String(summary.taskTotal)}
          unit="个"
          detail={`${summary.onlineTaskCount} 个已上线`}
        />
        <Metric
          label="已接入数据源"
          value={String(managedDatasources)}
          unit={`/ ${datasourceTotal}`}
          detail="有采集产出的数据源"
        />
        <Metric
          label="目录资产"
          value={assetCount.toLocaleString()}
          unit="表"
          detail={`近 7 天 +${newTablesLastSevenDays} · ${coverageLabel(summary)}`}
        />
        <Metric
          label="待处理失败表"
          value={String(summary.failedTableCount)}
          detail={
            summary.failedTableCount > 0
              ? `${summary.abnormalTaskCount} 个任务 · 点击只看异常`
              : "无"
          }
          tone="danger"
          active={onlyAbnormal}
          onClick={summary.failedTableCount > 0 ? onToggleAbnormal : undefined}
        />
      </div>
      <div className="border-t border-[var(--yak-components-panel-border)]">
        <RunWindow summary={summary} />
      </div>
    </div>
  );
}
