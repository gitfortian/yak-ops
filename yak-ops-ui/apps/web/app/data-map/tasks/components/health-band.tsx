import type { CrawlSummary } from "@/service/data-map/types";

import { formatDuration } from "../../shared/labels";

interface MetricProps {
  label: string;
  value: string;
  detail: string;
  tone?: "default" | "danger";
}

/**
 * One health metric, read-only: quality figures describe, they do not act. Only the failure
 * backlog below stays clickable, keeping the band's single call to action.
 */
function Metric({ label, value, detail, tone = "default" }: MetricProps) {
  return (
    <div className="rounded-[var(--yak-radius-control-medium)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-3.5 py-3 text-left">
      <div className="text-[11px] text-[var(--yak-components-muted-text)]">{label}</div>
      <div className="mt-1">
        <span
          className={`text-[20px] leading-6 font-semibold tabular-nums ${
            tone === "danger"
              ? "text-[var(--yak-components-badge-danger-text)]"
              : "text-[var(--yak-components-panel-text)]"
          }`}
        >
          {value}
        </span>
      </div>
      <div className="mt-1 truncate text-[11px] text-[var(--yak-components-muted-text)]">
        {detail}
      </div>
    </div>
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

/** Comment coverage of the active assets' columns, shown beside the run window. */
function coverageLabel(summary: CrawlSummary): string {
  if (summary.columnTotal <= 0) return "字段描述 -";
  return `字段描述 ${Math.round((summary.describedColumnCount / summary.columnTotal) * 100)}%`;
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
      <span className="shrink-0 max-md:basis-full">· {coverageLabel(summary)}</span>
    </div>
  );
}

interface Props {
  summary: CrawlSummary;
  onlyAbnormal: boolean;
  onToggleAbnormal: () => void;
}

/**
 * Quality-first band: how today's runs went, the seven-day success rate, and how long a healthy
 * run takes. Raw counts (tasks, datasources, assets) are not quality signals; they live in the
 * table summary and the drawer instead of taking a card each.
 */
export default function HealthBand({ summary, onlyAbnormal, onToggleAbnormal }: Props) {
  const todayFailed = summary.todayRunTotal - summary.todayRunSucceeded;
  const weekFailed = summary.weekRunTotal - summary.weekRunSucceeded;
  const successRate =
    summary.weekRunTotal > 0
      ? `${Math.round((summary.weekRunSucceeded / summary.weekRunTotal) * 100)}%`
      : "—";

  return (
    <div className="shrink-0 rounded-[var(--yak-radius-control-medium)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)]">
      <div className="grid grid-cols-4 gap-3 p-3 max-lg:grid-cols-2">
        <Metric
          label="今日运行"
          value={
            summary.todayRunTotal > 0
              ? `${summary.todayRunSucceeded}/${summary.todayRunTotal}`
              : "—"
          }
          detail={
            summary.todayRunTotal === 0
              ? "今天还没有运行"
              : todayFailed > 0
                ? `${todayFailed} 次失败`
                : "全部成功"
          }
          tone={todayFailed > 0 ? "danger" : "default"}
        />
        <Metric
          label="7 日成功率"
          value={successRate}
          detail={
            summary.weekRunTotal > 0
              ? `${summary.weekRunTotal} 次运行 · ${weekFailed} 次失败`
              : "近 7 天没有运行"
          }
        />
        <Metric
          label="平均耗时"
          value={summary.weekRunSucceeded > 0 ? formatDuration(summary.avgDurationSeconds) : "—"}
          detail={
            summary.p95DurationSeconds > 0
              ? `P95 ${formatDuration(summary.p95DurationSeconds)}`
              : "成功运行太少"
          }
        />
        <FailureMetric summary={summary} onlyAbnormal={onlyAbnormal} onToggle={onToggleAbnormal} />
      </div>
      <div className="border-t border-[var(--yak-components-panel-border)]">
        <RunWindow summary={summary} />
      </div>
    </div>
  );
}

/** The single actionable metric: pending failures filter the table when clicked. */
function FailureMetric({
  summary,
  onlyAbnormal,
  onToggle,
}: {
  summary: CrawlSummary;
  onlyAbnormal: boolean;
  onToggle: () => void;
}) {
  const className = [
    "rounded-[var(--yak-radius-control-medium)] border bg-[var(--yak-components-panel-bg)] px-3.5 py-3 text-left",
    "border-[var(--yak-components-panel-border)]",
    summary.failedTableCount > 0
      ? "border-l-2 border-l-[var(--yak-components-badge-danger-text)]"
      : "",
    summary.failedTableCount > 0
      ? "cursor-pointer transition-colors hover:border-[var(--yak-components-control-border)]"
      : "",
    onlyAbnormal ? "bg-[var(--yak-components-badge-danger-bg)]" : "",
  ]
    .filter(Boolean)
    .join(" ");

  const body = (
    <>
      <div className="text-[11px] text-[var(--yak-components-muted-text)]">待处理失败表</div>
      <div
        className={`mt-1 text-[20px] leading-6 font-semibold tabular-nums ${
          summary.failedTableCount > 0
            ? "text-[var(--yak-components-badge-danger-text)]"
            : "text-[var(--yak-components-panel-text)]"
        }`}
      >
        {summary.failedTableCount}
      </div>
      <div className="mt-1 truncate text-[11px] text-[var(--yak-components-muted-text)]">
        {summary.failedTableCount > 0 ? `${summary.abnormalTaskCount} 个任务 · 点击只看异常` : "无"}
      </div>
    </>
  );

  if (summary.failedTableCount === 0) return <div className={className}>{body}</div>;
  return (
    <button type="button" className={className} aria-pressed={onlyAbnormal} onClick={onToggle}>
      {body}
    </button>
  );
}
