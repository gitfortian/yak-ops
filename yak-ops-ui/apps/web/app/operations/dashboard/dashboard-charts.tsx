import { Card, CardContent, CardHeader, Empty } from "@yak-ops/yak-ui";
import { RefreshCw } from "lucide-react";
import { useMemo } from "react";
import type { DataSyncType } from "@/service/data-sync";
import type {
  DataSyncOperationsRange,
  DataSyncOperationsSummary,
  DataSyncOperationsTrendPoint,
} from "@/service/data-sync/operations";
import { EChart } from "./echart";
import { operationsDistributionOption, operationsTrendOption } from "./presentation";

export function OperationsCharts({
  summary,
  trend,
  range,
  syncType,
  refreshing,
}: {
  summary: DataSyncOperationsSummary;
  trend: DataSyncOperationsTrendPoint[];
  range: DataSyncOperationsRange;
  syncType: DataSyncType;
  refreshing: boolean;
}) {
  const hasExecutions = summary.executionCount > 0;
  const trendOption = useMemo(
    () => operationsTrendOption(trend, syncType, range),
    [trend, syncType, range],
  );
  const distributionOption = useMemo(
    () => operationsDistributionOption(summary, syncType),
    [summary, syncType],
  );
  return (
    <div className="grid grid-cols-2 gap-4 max-lg:grid-cols-1">
      <Card>
        <CardHeader
          title={syncType === "REALTIME" ? "启动与失败趋势" : "执行结果趋势"}
          extra={
            refreshing ? (
              <RefreshCw size={14} className="animate-spin" aria-label="正在刷新" />
            ) : null
          }
        />
        <CardContent>
          {hasExecutions ? (
            <EChart option={trendOption} ariaLabel="执行结果趋势" />
          ) : (
            <Empty title="暂无执行数据" className="min-h-64" />
          )}
        </CardContent>
      </Card>
      <Card>
        <CardHeader title="运行状态分布" />
        <CardContent>
          {hasExecutions ? (
            <EChart option={distributionOption} ariaLabel="运行状态分布" />
          ) : (
            <Empty title="暂无执行数据" className="min-h-64" />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
