import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, CircleDashed, LoaderCircle, ShieldAlert } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { listCrawls } from "@/service/data-map/crawl";

import {
  DATA_MAP_BASE,
  formatDuration,
  relativeTime,
  runDisplayStatus,
  RUN_STATUS_LABEL,
  type RunDisplayStatus,
} from "../shared/labels";
import { dataMapKeys } from "../shared/keys";
import { useDataMapScope } from "../shared/scope";

const FEED_LIMIT = 8;

const STATUS_ICON: Record<RunDisplayStatus, { Icon: typeof CheckCircle2; className: string }> = {
  SUCCEEDED: {
    Icon: CheckCircle2,
    className:
      "bg-[var(--yak-components-badge-success-bg)] text-[var(--yak-components-badge-success-text)]",
  },
  PARTIAL: {
    Icon: ShieldAlert,
    className:
      "bg-[var(--yak-components-badge-warning-bg)] text-[var(--yak-components-badge-warning-text)]",
  },
  FAILED: {
    Icon: AlertTriangle,
    className:
      "bg-[var(--yak-components-badge-danger-bg)] text-[var(--yak-components-badge-danger-text)]",
  },
  RUNNING: {
    Icon: LoaderCircle,
    className:
      "bg-[var(--yak-components-badge-info-bg)] text-[var(--yak-components-badge-info-text)]",
  },
  PENDING: {
    Icon: CircleDashed,
    className:
      "bg-[var(--yak-components-badge-neutral-bg)] text-[var(--yak-components-badge-neutral-text)]",
  },
  INTERRUPTED: {
    Icon: AlertTriangle,
    className:
      "bg-[var(--yak-components-badge-neutral-bg)] text-[var(--yak-components-badge-neutral-text)]",
  },
};

const line = (crawl: CrawlLine) => {
  const scope = crawl.taskName || crawl.database || "数据源";
  if (crawl.status === "FAILED" || crawl.status === "INTERRUPTED") {
    return `失败 ${crawl.failedCount} 张表`;
  }
  if (crawl.status === "PENDING" || crawl.status === "RUNNING") {
    return `${scope} 采集中`;
  }
  return `${scope} 采集完成 ${crawl.tableCount} 张表 · 新增 ${crawl.addedCount} · 耗时 ${formatDuration(crawl.durationSeconds)}`;
};

/** A crawl instance row of the feed, reduced to the fields the line and status render from. */
interface CrawlLine {
  status: RunDisplayStatus;
  taskName: string | null;
  database: string;
  tableCount: number;
  addedCount: number;
  failedCount: number;
  durationSeconds: number | null;
}

/** 平台动态：以采集实例为事件源，展示最近的元数据采集进展与结果。 */
export default function ActivityFeed() {
  const navigate = useNavigate();
  const { scope, ready } = useDataMapScope();
  const crawls = useQuery({
    queryKey: dataMapKeys.crawls(scope, { pageNo: 1, pageSize: FEED_LIMIT }),
    queryFn: ({ signal }) => listCrawls({ pageNo: 1, pageSize: FEED_LIMIT }, signal),
    enabled: ready,
  });

  return (
    <div>
      {crawls.isError && (
        <p className="text-xs text-[var(--yak-components-muted-text)]">采集动态加载失败</p>
      )}
      {crawls.isSuccess && crawls.data.bizData.length === 0 && (
        <p className="text-xs text-[var(--yak-components-muted-text)]">
          还没有采集记录，发起一次采集后这里会展示平台动态。
        </p>
      )}
      {(crawls.data?.bizData ?? []).slice(0, FEED_LIMIT).map((crawl) => {
        const status = runDisplayStatus(crawl.status, crawl.failedCount);
        if (!status) return null;
        const { Icon, className } = STATUS_ICON[status];
        return (
          <button
            key={crawl.id}
            type="button"
            onClick={() => navigate(`${DATA_MAP_BASE}/tasks`)}
            className="flex w-full cursor-pointer items-start gap-2.5 border-b border-[var(--yak-datamap-divider-soft)] py-2.5 text-left last:border-b-0"
          >
            <span
              className={`mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-md ${className}`}
            >
              <Icon
                size={12}
                aria-hidden="true"
                className={
                  status === "RUNNING" ? "animate-spin motion-reduce:animate-none" : undefined
                }
              />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-xs leading-5 text-[var(--yak-components-panel-text)]">
                <b className="font-semibold">{RUN_STATUS_LABEL[status]}</b> ·{" "}
                {line({ ...crawl, status })}
              </span>
              <span className="mt-0.5 block text-[11px] text-[var(--yak-components-muted-text)]">
                {relativeTime(crawl.createdTime)}
              </span>
            </span>
          </button>
        );
      })}
    </div>
  );
}
