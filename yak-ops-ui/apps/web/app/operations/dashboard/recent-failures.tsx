import { Card, CardContent, CardHeader, Empty } from "@yak-ops/yak-ui";
import { ArrowUpRight } from "lucide-react";
import { Link } from "react-router-dom";
import type { DataSyncOperationsFailure } from "@/service/data-sync/operations";

export function OperationsRecentFailures({
  failures,
}: {
  failures: DataSyncOperationsFailure[];
}) {
  return (
    <Card>
      <CardHeader title="最近失败任务" />
      <CardContent className="p-0">
        {failures.length === 0 ? (
          <Empty title="暂无失败任务" />
        ) : (
          <div className="divide-y divide-[var(--yak-components-panel-border)]">
            {failures.map((failure) => (
              <Link
                key={failure.instanceId}
                to={`/data-sync/${encodeURIComponent(failure.definitionId)}/detail?executionId=${encodeURIComponent(failure.instanceId)}`}
                className="flex items-center justify-between gap-4 px-5 py-3 hover:bg-[var(--yak-color-hover)]"
              >
                <div className="min-w-0">
                  <div className="truncate text-sm font-medium text-[var(--yak-components-panel-text)]">
                    {failure.taskName || "历史任务"}
                  </div>
                  <div className="mt-1 truncate text-xs text-[var(--yak-components-muted-text)]">
                    {failure.createTime.replace("T", " ")} · {failure.failureCode || "FAILED"}
                  </div>
                </div>
                <ArrowUpRight size={16} className="shrink-0 text-[var(--yak-components-muted-text)]" />
              </Link>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
