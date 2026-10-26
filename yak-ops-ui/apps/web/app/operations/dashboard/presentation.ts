import type { EChartsCoreOption } from "echarts/core";
import type { DataSyncType } from "@/service/data-sync";
import type {
  DataSyncOperationsRange,
  DataSyncOperationsSummary,
  DataSyncOperationsTrendPoint,
} from "@/service/data-sync/operations";

export const OPERATIONS_RANGES: Record<DataSyncOperationsRange, string> = {
  TODAY: "今天",
  LAST_7_DAYS: "最近 7 天",
  LAST_30_DAYS: "最近 30 天",
};

export function defaultOperationsRange(syncType: DataSyncType): DataSyncOperationsRange {
  return syncType === "REALTIME" ? "TODAY" : "LAST_7_DAYS";
}

export function resolveOperationsRange(
  value: string | null,
  syncType: DataSyncType,
): DataSyncOperationsRange {
  return value === "TODAY" || value === "LAST_7_DAYS" || value === "LAST_30_DAYS"
    ? value
    : defaultOperationsRange(syncType);
}

export function operationsSummaryCards(summary: DataSyncOperationsSummary, syncType: DataSyncType) {
  return syncType === "REALTIME"
    ? [
        { label: "启动次数", value: summary.executionCount },
        { label: "正在运行", value: summary.runningCount },
        { label: "失败次数", value: summary.failedCount },
        { label: "已停止", value: summary.canceledCount },
      ]
    : [
        { label: "执行总数", value: summary.executionCount },
        { label: "成功次数", value: summary.succeededCount },
        { label: "失败次数", value: summary.failedCount },
        { label: "正在运行", value: summary.runningCount },
      ];
}

export function operationsStatusDistribution(
  summary: DataSyncOperationsSummary,
  syncType: DataSyncType,
) {
  const pending = Math.max(
    0,
    summary.executionCount - summary.succeededCount - summary.failedCount - summary.canceledCount,
  );
  return [
    { name: "成功", value: summary.succeededCount },
    { name: "失败", value: summary.failedCount },
    { name: syncType === "REALTIME" ? "已停止" : "已取消", value: summary.canceledCount },
    { name: "处理中", value: pending },
  ].filter((item) => item.value > 0);
}

export function operationsTrendOption(
  trend: DataSyncOperationsTrendPoint[],
  syncType: DataSyncType,
  range: DataSyncOperationsRange,
): EChartsCoreOption {
  const realtime = syncType === "REALTIME";
  return {
    tooltip: { trigger: "axis" },
    legend: { bottom: 0 },
    grid: { top: 20, left: 12, right: 14, bottom: 48, containLabel: true },
    xAxis: {
      type: "category",
      data: trend.map((item) =>
        range === "TODAY" ? item.bucket.slice(11, 16) : item.bucket.slice(5),
      ),
      axisTick: { show: false },
      axisLabel: { color: "#98a2b3" },
    },
    yAxis: {
      type: "value",
      minInterval: 1,
      axisLabel: { color: "#98a2b3" },
      splitLine: { lineStyle: { color: "#f0f1f3" } },
    },
    series: realtime
      ? [
          {
            name: "启动次数",
            type: "line",
            smooth: true,
            data: trend.map((item) => item.executionCount),
          },
          { name: "失败", type: "bar", data: trend.map((item) => item.failedCount) },
        ]
      : [
          {
            name: "成功",
            type: "bar",
            stack: "outcome",
            data: trend.map((item) => item.succeededCount),
          },
          {
            name: "失败",
            type: "bar",
            stack: "outcome",
            data: trend.map((item) => item.failedCount),
          },
          {
            name: "取消",
            type: "bar",
            stack: "outcome",
            data: trend.map((item) => item.canceledCount),
          },
        ],
  };
}

export function operationsDistributionOption(
  summary: DataSyncOperationsSummary,
  syncType: DataSyncType,
): EChartsCoreOption {
  return {
    tooltip: { trigger: "item" },
    legend: { bottom: 0 },
    series: [
      {
        name: "状态分布",
        type: "pie",
        radius: ["48%", "70%"],
        center: ["50%", "43%"],
        label: { show: false },
        data: operationsStatusDistribution(summary, syncType),
      },
    ],
  };
}
