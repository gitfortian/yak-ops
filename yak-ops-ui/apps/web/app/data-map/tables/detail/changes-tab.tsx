import { Alert, Badge, Button, Empty, Spinner } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";

import { getTableChanges } from "@/service/data-map/catalog";
import { dataMapFailureMessage } from "@/service/data-map/request";
import type { TableChange } from "@/service/data-map/types";

import { dataMapKeys } from "../../shared/keys";
import { CHANGE_TYPE_LABEL } from "../../shared/labels";
import { useDataMapScope } from "../../shared/scope";

/** Additions read as growth, removals as loss, every other change as a modification. */
const toneOf = (changeType: string) => {
  if (changeType === "COLUMN_ADDED") return "success" as const;
  if (changeType === "COLUMN_REMOVED") return "danger" as const;
  return "warning" as const;
};

const DOT_CLASS: Record<"success" | "danger" | "warning", string> = {
  success: "bg-[var(--yak-components-badge-success-text)]",
  danger: "bg-[var(--yak-components-badge-danger-text)]",
  warning: "bg-[var(--yak-components-badge-warning-text)]",
};

/** Crawl-detected changes, newest first; the server returns the most recent hundred. */
export default function ChangesTab({ tableId }: { tableId: string }) {
  const { scope } = useDataMapScope();
  const changes = useQuery({
    queryKey: dataMapKeys.changes(scope, tableId),
    queryFn: ({ signal }) => getTableChanges(tableId, signal),
  });

  if (changes.isError) {
    return (
      <Alert>
        {dataMapFailureMessage(changes.error, "变更记录加载失败")}
        <Button size="small" className="ml-3" onClick={() => void changes.refetch()}>
          重试
        </Button>
      </Alert>
    );
  }

  if (changes.isPending) {
    return (
      <div className="flex items-center gap-2 py-10 text-xs text-[var(--yak-components-muted-text)]">
        <Spinner size="small" />
        正在加载变更记录
      </div>
    );
  }

  const records = changes.data ?? [];
  if (records.length === 0) {
    return (
      <Empty
        className="min-h-32 rounded border border-dashed border-[var(--yak-components-panel-border)]"
        description="暂无变更记录。第一次采集只登记资产，之后的采集若发现字段或注释变化，会按时间记录在这里。"
      />
    );
  }

  return (
    <ol className="m-0 list-none p-0">
      {records.map((change: TableChange, index) => {
        const tone = toneOf(change.changeType);
        return (
          <li
            key={`${change.changeType}-${change.createdTime}-${index}`}
            className="relative flex gap-3 pb-5 last:pb-0"
          >
            {index < records.length - 1 && (
              <span
                aria-hidden="true"
                className="absolute top-4 left-[5px] h-full w-px bg-[var(--yak-components-panel-border)]"
              />
            )}
            <span
              aria-hidden="true"
              className={`relative z-10 mt-1.5 size-2.5 shrink-0 rounded-full ${DOT_CLASS[tone]}`}
            />
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <Badge tone={tone}>
                  {CHANGE_TYPE_LABEL[change.changeType] ?? change.changeType}
                </Badge>
                <span className="text-xs tabular-nums text-[var(--yak-components-muted-text)]">
                  {change.createdTime}
                </span>
              </div>
              <p className="mt-1 text-sm leading-5 text-[var(--yak-components-panel-text)]">
                {change.detail || "-"}
              </p>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
