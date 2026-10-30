import { Badge, Button } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";
import { ChevronDown, ChevronRight, RotateCcw } from "lucide-react";
import { Fragment } from "react";

import { getCrawl } from "@/service/data-map/crawl";
import { dataMapFailureMessage } from "@/service/data-map/request";
import type { Crawl } from "@/service/data-map/types";

import { dataMapKeys } from "../shared/keys";
import {
  clockLabel,
  CRAWL_TRIGGER_LABEL,
  formatDuration,
  locationLabel,
  relativeTime,
  RUN_STATUS_LABEL,
  RUN_STATUS_TONE,
  runDisplayStatus,
} from "../shared/labels";
import { useDataMapScope } from "../shared/scope";

const isActive = (status: string) => status === "PENDING" || status === "RUNNING";

function Produced({ crawl }: { crawl: Crawl }) {
  if (isActive(crawl.status)) {
    return <span className="text-xs text-[var(--yak-components-muted-text)]">进行中</span>;
  }
  if (crawl.status === "FAILED") {
    return <span className="text-xs text-[var(--yak-components-muted-text)]">未产出</span>;
  }
  const parts = [
    {
      label: "+",
      value: crawl.addedCount,
      tone: "text-[var(--yak-components-badge-success-text)]",
    },
    { label: "~", value: crawl.updatedCount, tone: "" },
    { label: "-", value: crawl.removedCount, tone: "" },
  ];
  return (
    <span className="inline-flex items-baseline gap-2 font-mono text-[11px] tabular-nums">
      {parts.map((part) => (
        <span key={part.label} className={part.tone}>
          {part.label}
          {part.value}
        </span>
      ))}
      <span
        className={
          crawl.failedCount > 0
            ? "text-[var(--yak-components-badge-danger-text)]"
            : "text-[var(--yak-components-muted-text)]"
        }
      >
        失败 {crawl.failedCount}
      </span>
    </span>
  );
}

/**
 * Failure list of one expanded run. It is fetched on demand, because the feed does not carry the
 * per-table error text and most rows are never opened.
 */
function RunFailures({ crawl }: { crawl: Crawl }) {
  const { scope, ready } = useDataMapScope();
  const detail = useQuery({
    queryKey: dataMapKeys.crawl(scope, crawl.id),
    queryFn: ({ signal }) => getCrawl(crawl.id, signal),
    enabled: ready,
    retry: false,
  });

  if (crawl.status === "FAILED" && crawl.failedCount === 0) {
    return (
      <div className="px-4 py-3 text-xs leading-5">
        <div className="text-[var(--yak-components-muted-text)]">
          实例级失败 · 未产出任何资产变更
        </div>
        <div className="mt-1 text-[var(--yak-components-badge-danger-text)]">
          {crawl.error ?? "未记录失败原因"}
        </div>
      </div>
    );
  }

  if (detail.isPending) {
    return (
      <div className="px-4 py-3 text-xs text-[var(--yak-components-muted-text)]">
        正在读取失败表…
      </div>
    );
  }

  if (detail.isError) {
    return (
      <div className="flex items-center gap-2 px-4 py-3 text-xs text-[var(--yak-components-muted-text)]">
        {dataMapFailureMessage(detail.error, "失败表加载失败")}
        <button
          type="button"
          className="text-[var(--yak-color-primary)] hover:underline"
          onClick={() => void detail.refetch()}
        >
          重试
        </button>
      </div>
    );
  }

  const failures = detail.data.failures;
  return (
    <div className="px-4 py-3">
      <div className="mb-1.5 text-[11px] text-[var(--yak-components-muted-text)]">
        仅显示前 200 张失败表 · 本次不做缺失判定
      </div>
      <div className="max-h-64 overflow-auto rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-panel-border)]">
        {failures.map((failure) => (
          <div
            key={`${failure.tableName}-${failure.createdTime}`}
            className="flex gap-3 border-b border-[var(--yak-components-panel-border)] px-2.5 py-1.5 text-[11px] last:border-b-0"
          >
            <span className="w-56 shrink-0 truncate font-mono">{failure.tableName}</span>
            <span className="min-w-0 flex-1 text-[var(--yak-components-muted-text)]">
              {failure.error}
            </span>
          </div>
        ))}
        {failures.length === 0 && (
          <div className="px-2.5 py-2 text-[11px] text-[var(--yak-components-muted-text)]">
            没有可展示的失败表记录。
          </div>
        )}
      </div>
    </div>
  );
}

interface Props {
  records: Crawl[];
  datasourceNames: Map<string, string>;
  expandedId: string | null;
  onToggle: (crawlId: string) => void;
  retryingId: string | null;
  onRetry: (crawl: Crawl) => void;
}

export default function RunTable({
  records,
  datasourceNames,
  expandedId,
  onToggle,
  retryingId,
  onRetry,
}: Props) {
  return (
    <table className="w-full table-fixed border-collapse text-xs">
      <colgroup>
        <col style={{ width: "12%" }} />
        <col style={{ width: "18%" }} />
        <col style={{ width: "20%" }} />
        <col style={{ width: "6%" }} />
        <col style={{ width: "9%" }} />
        <col style={{ width: "15%" }} />
        <col style={{ width: "7%" }} />
        <col style={{ width: "13%" }} />
      </colgroup>
      <thead>
        <tr className="border-b border-[var(--yak-components-control-border)] text-left text-[11px] text-[var(--yak-components-muted-text)]">
          <th className="px-3 py-2 font-normal">提交时间</th>
          <th className="px-3 py-2 font-normal">任务</th>
          <th className="px-3 py-2 font-normal">采集范围</th>
          <th className="px-3 py-2 font-normal">触发</th>
          <th className="px-3 py-2 font-normal">状态</th>
          <th className="px-3 py-2 font-normal">产出</th>
          <th className="px-3 py-2 font-normal">耗时</th>
          <th className="px-3 py-2" />
        </tr>
      </thead>
      <tbody>
        {records.map((crawl) => {
          const shown = runDisplayStatus(crawl.status, crawl.failedCount);
          const expandable = crawl.failedCount > 0 || crawl.status === "FAILED";
          const expanded = expandedId === crawl.id;
          // Retry re-enumerates the failed tables of this run, so it only exists when the run has
          // some. A run-level failure is re-run from the task instead.
          const canRetry =
            Boolean(crawl.taskId) && crawl.failedCount > 0 && !isActive(crawl.status);
          return (
            <Fragment key={crawl.id}>
              <tr className="border-b border-[var(--yak-components-panel-border)] align-middle hover:bg-[var(--yak-color-hover)]">
                <td className="px-3 py-2">
                  <div className="text-[var(--yak-components-panel-text)]">
                    {clockLabel(crawl.createdTime)}
                  </div>
                  <div className="text-[11px] text-[var(--yak-components-muted-text)]">
                    {relativeTime(crawl.createdTime)}
                  </div>
                </td>
                <td className="px-3 py-2">
                  <div className="truncate text-[var(--yak-components-panel-text)]">
                    {crawl.taskName ?? "手动提交"}
                  </div>
                  {crawl.retryOf && (
                    <div className="text-[11px] text-[var(--yak-components-muted-text)]">重试</div>
                  )}
                </td>
                <td className="px-3 py-2">
                  <div className="truncate text-[var(--yak-components-panel-text)]">
                    {datasourceNames.get(crawl.datasourceId) ?? crawl.datasourceId}
                  </div>
                  <div className="truncate text-[11px] text-[var(--yak-components-muted-text)]">
                    {locationLabel(crawl.database, crawl.schema)}
                  </div>
                </td>
                <td className="px-3 py-2 text-[var(--yak-components-muted-text)]">
                  {CRAWL_TRIGGER_LABEL[crawl.triggerType]}
                </td>
                <td className="px-3 py-2">
                  {shown && <Badge tone={RUN_STATUS_TONE[shown]}>{RUN_STATUS_LABEL[shown]}</Badge>}
                </td>
                <td className="px-3 py-2">
                  <Produced crawl={crawl} />
                </td>
                <td className="px-3 py-2 text-[var(--yak-components-muted-text)]">
                  {formatDuration(crawl.durationSeconds)}
                </td>
                <td className="px-3 py-2">
                  <div className="flex items-center justify-end gap-1">
                    {canRetry && (
                      <Button
                        size="small"
                        variant="ghost"
                        loading={retryingId === crawl.id}
                        onClick={() => onRetry(crawl)}
                      >
                        <RotateCcw size={12} aria-hidden="true" />
                        重试
                      </Button>
                    )}
                    {expandable && (
                      <button
                        type="button"
                        aria-expanded={expanded}
                        aria-label={`展开失败表 ${crawl.failedCount} 张`}
                        className="inline-flex items-center gap-1 rounded-[var(--yak-radius-control-small)] px-1.5 py-1 text-[11px] text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-components-menu-item-hover)] hover:text-[var(--yak-components-panel-text)]"
                        onClick={() => onToggle(crawl.id)}
                      >
                        {expanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
                        失败表 {crawl.failedCount}
                      </button>
                    )}
                  </div>
                </td>
              </tr>
              {expanded && (
                <tr className="border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)]">
                  <td colSpan={8} className="p-0">
                    <RunFailures crawl={crawl} />
                  </td>
                </tr>
              )}
            </Fragment>
          );
        })}
      </tbody>
    </table>
  );
}
