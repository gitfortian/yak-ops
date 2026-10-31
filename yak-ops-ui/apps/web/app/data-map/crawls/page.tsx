import {
  Alert,
  Card,
  PageHeader,
  Pagination,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  toast,
} from "@yak-ops/yak-ui";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { listCrawls } from "@/service/data-map/crawl";
import { dataMapFailureMessage } from "@/service/data-map/request";
import { retryTaskRun } from "@/service/data-map/task";
import type { Crawl, CrawlQuery, CrawlStatus, CrawlTrigger } from "@/service/data-map/types";

import DataMapEmptyState from "../shared/empty-state";
import { dataMapKeys } from "../shared/keys";
import { useDatasourceOptions } from "../shared/lookups";
import { useDataMapScope } from "../shared/scope";
import TaskTabs from "../tasks/components/task-tabs";
import RunTable from "./run-table";

const PAGE_SIZE = 20;
const ACTIVE_POLL_MS = 5000;
const DEFAULT_DAYS = 7;

/**
 * Status options of the feed. `PARTIAL` is not a stored status: the backend reports a run with
 * failed tables as `SUCCEEDED`, so the option maps to "succeeded and has failures".
 */
const OUTCOMES: {
  value: string;
  label: string;
  status?: CrawlStatus;
  failed?: boolean;
}[] = [
  { value: "", label: "全部状态" },
  { value: "SUCCESS", label: "成功", status: "SUCCEEDED", failed: false },
  { value: "PARTIAL", label: "部分成功", status: "SUCCEEDED", failed: true },
  { value: "FAILED", label: "失败", status: "FAILED" },
  { value: "RUNNING", label: "采集中", status: "RUNNING" },
  { value: "PENDING", label: "排队中", status: "PENDING" },
  { value: "INTERRUPTED", label: "异常中断", status: "INTERRUPTED" },
];

/** `0` means "no lower bound": an empty value would fall back to the default range. */
const RANGES = [
  { value: "1", label: "近 24 小时" },
  { value: String(DEFAULT_DAYS), label: "近 7 天" },
  { value: "30", label: "近 30 天" },
  { value: "0", label: "全部时间" },
];

const TRIGGERS = [
  { value: "", label: "全部触发" },
  { value: "MANUAL", label: "手动" },
  { value: "SCHEDULED", label: "定时" },
];

const readPageNo = (value: string | null) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1;
};

export default function CrawlRunsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const { scope, ready } = useDataMapScope();
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const pageNo = readPageNo(searchParams.get("pageNo"));
  const outcomeValue = searchParams.get("outcome") ?? "";
  const outcome = OUTCOMES.find((option) => option.value === outcomeValue);
  const rangeValue = searchParams.get("days") ?? String(DEFAULT_DAYS);
  const range = RANGES.find((option) => option.value === rangeValue) ?? RANGES[1];
  const datasourceFilter = searchParams.get("ds") ?? "";
  const triggerValue = searchParams.get("trigger") ?? "";

  const datasources = useDatasourceOptions();
  const datasourceNames = useMemo(
    () => new Map(datasources.options.map((item) => [item.id, item.name])),
    [datasources.options],
  );

  const query: CrawlQuery = {
    pageNo,
    pageSize: PAGE_SIZE,
    status: outcome?.status,
    failed: outcome?.failed,
    triggerType: triggerValue === "" ? undefined : (triggerValue as CrawlTrigger),
    days: range.value === "0" ? undefined : Number(range.value),
    datasourceId: datasourceFilter || undefined,
  };

  const feed = useQuery({
    queryKey: dataMapKeys.crawls(scope, query),
    queryFn: ({ signal }) => listCrawls(query, signal),
    enabled: ready,
    placeholderData: keepPreviousData,
    refetchInterval: (result) =>
      (result.state.data?.bizData ?? []).some(
        (crawl) => crawl.status === "RUNNING" || crawl.status === "PENDING",
      )
        ? ACTIVE_POLL_MS
        : false,
  });

  const records = feed.data?.bizData ?? [];
  const total = feed.data?.pagination?.total ?? 0;
  const hasFilters =
    outcomeValue.length > 0 ||
    range.value !== String(DEFAULT_DAYS) ||
    datasourceFilter.length > 0 ||
    triggerValue.length > 0;

  const writeParams = (patch: Record<string, string>, resetPage = true) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(patch).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });
    if (resetPage) next.delete("pageNo");
    setSearchParams(next, { replace: true });
  };

  const retry = useMutation({
    mutationFn: (crawl: Crawl) => retryTaskRun(crawl.taskId ?? "", crawl.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "crawls"] });
      void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "tasks"] });
      toast.success("已提交重试，仅重新采集失败表");
    },
    onError: (error) => toast.error(dataMapFailureMessage(error, "重试提交失败，请稍后重试")),
  });

  const summary = hasFilters
    ? `${range.label}筛出 ${total} 次实例`
    : `${range.label}共 ${total} 次实例`;

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="元数据采集"
        description="每次采集新增了什么、失败在哪张表、是否需要重试。"
        bordered
        className="bg-[var(--yak-color-background)] px-6 max-md:px-4"
      />

      <div className="flex min-h-0 flex-1 flex-col px-6 pb-4 pt-4 max-md:px-4">
        {feed.isError && (
          <Alert className="mb-3 shrink-0">
            {dataMapFailureMessage(feed.error, "实例记录加载失败，请稍后重试。")}
          </Alert>
        )}

        <Card className="flex min-h-0 flex-1 flex-col">
          <TaskTabs active="runs" />

          <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-[var(--yak-components-control-border)] px-4 py-2.5">
            <Select
              size="small"
              items={OUTCOMES}
              value={outcomeValue}
              onValueChange={(next) => writeParams({ outcome: next ?? "" })}
            >
              <SelectTrigger variant="outlined" className="w-[132px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {OUTCOMES.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    <SelectItemText>{option.label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select
              size="small"
              items={RANGES}
              value={rangeValue}
              onValueChange={(next) => writeParams({ days: next ?? "" })}
            >
              <SelectTrigger variant="outlined" className="w-[132px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {RANGES.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    <SelectItemText>{option.label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select
              size="small"
              items={TRIGGERS}
              value={triggerValue}
              onValueChange={(next) => writeParams({ trigger: next ?? "" })}
            >
              <SelectTrigger variant="outlined" className="w-[118px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {TRIGGERS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    <SelectItemText>{option.label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select
              size="small"
              items={[
                { value: "", label: "全部数据源" },
                ...datasources.options.map((option) => ({ value: option.id, label: option.name })),
              ]}
              value={datasourceFilter}
              onValueChange={(next) => writeParams({ ds: next ?? "" })}
            >
              <SelectTrigger variant="outlined" className="w-[170px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">
                  <SelectItemText>全部数据源</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
                {datasources.options.map((option) => (
                  <SelectItem key={option.id} value={option.id}>
                    <SelectItemText>{option.name}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <span className="ml-auto text-xs text-[var(--yak-components-muted-text)]">
              {summary}
            </span>
          </div>

          <div className="min-h-0 flex-1 overflow-auto">
            {feed.isPending ? (
              <div className="py-16 text-center text-sm text-[var(--yak-components-muted-text)]">
                正在加载实例记录…
              </div>
            ) : records.length === 0 ? (
              <DataMapEmptyState
                title={hasFilters ? "没有符合条件的实例" : "还没有采集实例"}
                description={
                  hasFilters
                    ? "换个状态或时间范围试试。"
                    : "任务上线后按调度执行，或在任务列表里手动采集一次。"
                }
                action={
                  hasFilters ? (
                    <button
                      type="button"
                      className="text-xs text-[var(--yak-color-primary)] hover:underline"
                      onClick={() =>
                        writeParams({
                          outcome: "",
                          days: String(DEFAULT_DAYS),
                          ds: "",
                          trigger: "",
                        })
                      }
                    >
                      清除筛选
                    </button>
                  ) : undefined
                }
              />
            ) : (
              <RunTable
                records={records}
                datasourceNames={datasourceNames}
                expandedId={expandedId}
                onToggle={(crawlId) =>
                  setExpandedId((current) => (current === crawlId ? null : crawlId))
                }
                retryingId={retry.isPending ? (retry.variables?.id ?? null) : null}
                onRetry={(crawl) => retry.mutate(crawl)}
              />
            )}
          </div>

          {total > PAGE_SIZE && (
            <div className="shrink-0 border-t border-[var(--yak-components-panel-border)] px-4 py-2.5">
              <Pagination
                page={pageNo}
                pageSize={PAGE_SIZE}
                total={total}
                onChange={(nextPage) =>
                  writeParams({ pageNo: nextPage > 1 ? String(nextPage) : "" }, false)
                }
              />
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
