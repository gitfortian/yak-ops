import {
  Alert,
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Table,
  toast,
} from "@yak-ops/yak-ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RotateCcw } from "lucide-react";

import { getCrawl, submitCrawl } from "@/service/data-map/crawl";
import { dataMapFailureMessage } from "@/service/data-map/request";
import type { CrawlFailure } from "@/service/data-map/types";

import { dataMapKeys } from "../shared/keys";
import {
  CRAWL_STATUS_LABEL,
  CRAWL_STATUS_TONE,
  CRAWL_TRIGGER_LABEL,
  formatDuration,
} from "../shared/labels";
import { useDataMapScope } from "../shared/scope";

interface Props {
  crawlId: string;
  onClose: () => void;
}

const isActive = (status: string | undefined) => status === "PENDING" || status === "RUNNING";

const ACTIVE_POLL_MS = 3000;

/**
 * Renders one metric cell of the result grid.
 *
 * <p>Label and value share a row, right-aligned and tabular, so six numbers read as one block
 * instead of six stacked pairs.
 */
const Metric = ({ label, value }: { label: string; value: number | undefined }) => (
  <div className="flex items-baseline justify-between gap-2 bg-white px-3 py-2">
    <dt className="text-xs text-[var(--yak-components-muted-text)]">{label}</dt>
    <dd className="text-sm font-medium tabular-nums text-[var(--yak-components-panel-text)]">
      {value ?? "-"}
    </dd>
  </div>
);

export default function CrawlDetailDialog({ crawlId, onClose }: Props) {
  const { scope } = useDataMapScope();
  const queryClient = useQueryClient();
  const detail = useQuery({
    queryKey: dataMapKeys.crawl(scope, crawlId),
    queryFn: ({ signal }) => getCrawl(crawlId, signal),
    refetchInterval: (query) => (isActive(query.state.data?.crawl.status) ? ACTIVE_POLL_MS : false),
  });

  const crawl = detail.data?.crawl;
  const failures = detail.data?.failures ?? [];

  /**
   * A retry submits a fresh run scoped to this run's failures; the dialog closes so the operator
   * watches the new row instead of a stale one, and the mutation's pending flag guards double clicks.
   */
  const retry = useMutation({
    mutationFn: () => {
      const source = detail.data?.crawl;
      if (!source) throw new Error("source run is unavailable");
      return submitCrawl({
        datasourceId: source.datasourceId,
        database: source.database,
        schema: source.schema || undefined,
        retryOf: source.id,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "crawls"] });
      toast.success("已发起失败表重试");
      onClose();
    },
  });

  const failureColumns = [
    { key: "tableName", title: "表名", dataIndex: "tableName" as const, width: 200 },
    { key: "error", title: "错误", dataIndex: "error" as const, ellipsis: true },
    { key: "createdTime", title: "时间", dataIndex: "createdTime" as const, width: 170 },
  ];

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="w-[640px]">
        <DialogTitle className="flex items-center gap-2 text-sm font-medium">
          采集详情
          {crawl && (
            <Badge tone={CRAWL_STATUS_TONE[crawl.status]}>{CRAWL_STATUS_LABEL[crawl.status]}</Badge>
          )}
        </DialogTitle>

        {detail.isError && (
          <Alert className="mt-3">
            {dataMapFailureMessage(detail.error, "采集详情加载失败")}
            <Button size="small" className="ml-3" onClick={() => void detail.refetch()}>
              重试
            </Button>
          </Alert>
        )}

        {crawl && (
          <>
            <div className="mt-4 grid grid-cols-3 gap-px overflow-hidden rounded border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-border)]">
              <Metric label="表" value={crawl.tableCount} />
              <Metric label="字段" value={crawl.columnCount} />
              <Metric label="失败表" value={crawl.failedCount} />
              <Metric label="新增" value={crawl.addedCount} />
              <Metric label="更新" value={crawl.updatedCount} />
              <Metric label="标记缺失" value={crawl.removedCount} />
            </div>

            {crawl.filteredCount > 0 && (
              <div className="mt-2.5 text-xs text-[var(--yak-components-muted-text)]">
                本次过滤了 {crawl.filteredCount} 张表
                {crawl.excludePattern ? `（正则 ${crawl.excludePattern}）` : ""}
                ，这些表保持正常状态、字段未重新采集。
              </div>
            )}

            <dl className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-xs text-[var(--yak-components-muted-text)]">
              <div className="flex gap-2">
                <dt>触发方式</dt>
                <dd className="text-[var(--yak-components-panel-text)]">
                  {CRAWL_TRIGGER_LABEL[crawl.triggerType] ?? crawl.triggerType}
                </dd>
              </div>
              <div className="flex gap-2">
                <dt>耗时</dt>
                <dd className="tabular-nums text-[var(--yak-components-panel-text)]">
                  {formatDuration(crawl.durationSeconds)}
                </dd>
              </div>
              <div className="flex gap-2">
                <dt>提交人</dt>
                <dd className="text-[var(--yak-components-panel-text)]">{crawl.createBy}</dd>
              </div>
              <div className="flex gap-2">
                <dt>提交</dt>
                <dd>{crawl.createdTime}</dd>
              </div>
              <div className="flex gap-2">
                <dt>结束</dt>
                <dd>{crawl.finishedTime || "-"}</dd>
              </div>
            </dl>

            {crawl.error && <Alert className="mt-3">{crawl.error}</Alert>}
          </>
        )}

        {crawl && crawl.failedCount > 0 && (
          <div className="mt-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <span className="text-[13px] font-medium text-[var(--yak-components-panel-text)]">
                本次采集有 {crawl.failedCount} 张表失败
              </span>
              <Button
                size="small"
                variant="primary"
                loading={retry.isPending}
                disabled={retry.isPending || isActive(crawl.status)}
                onClick={() => retry.mutate()}
              >
                <RotateCcw size={13} aria-hidden="true" />
                重试失败表
              </Button>
            </div>
            <div className="mt-2 max-h-64 overflow-auto">
              <Table<CrawlFailure>
                columns={failureColumns}
                dataSource={failures}
                rowKey="tableName"
                size="small"
                bordered
              />
            </div>
            {retry.isError && (
              <Alert className="mt-2">
                {dataMapFailureMessage(retry.error, "重试提交失败，请稍后再试")}
              </Alert>
            )}
          </div>
        )}

        <div className="mt-5 flex justify-end">
          <Button size="small" onClick={onClose}>
            关闭
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
