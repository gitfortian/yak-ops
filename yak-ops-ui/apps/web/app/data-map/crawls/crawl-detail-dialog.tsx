import { Alert, Badge, Button, Dialog, DialogContent, DialogTitle, Table } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";

import { getCrawl } from "@/service/data-map/crawl";
import { dataMapFailureMessage } from "@/service/data-map/request";
import type { CrawlFailure } from "@/service/data-map/types";

import { dataMapKeys } from "../shared/keys";
import { CRAWL_STATUS_LABEL, CRAWL_STATUS_TONE } from "../shared/labels";
import { useDataMapScope } from "../shared/scope";

interface Props {
  crawlId: string;
  onClose: () => void;
}

const isActive = (status: string | undefined) => status === "PENDING" || status === "RUNNING";

const ACTIVE_POLL_MS = 3000;

const Counter = ({ label, value }: { label: string; value: number | undefined }) => (
  <div className="flex flex-col gap-0.5">
    <span className="text-xs text-[var(--yak-components-muted-text)]">{label}</span>
    <span className="text-sm font-medium text-[var(--yak-components-panel-text)]">
      {value ?? "-"}
    </span>
  </div>
);

export default function CrawlDetailDialog({ crawlId, onClose }: Props) {
  const { scope } = useDataMapScope();
  const detail = useQuery({
    queryKey: dataMapKeys.crawl(scope, crawlId),
    queryFn: ({ signal }) => getCrawl(crawlId, signal),
    refetchInterval: (query) => (isActive(query.state.data?.crawl.status) ? ACTIVE_POLL_MS : false),
  });

  const crawl = detail.data?.crawl;
  const failures = detail.data?.failures ?? [];
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
            <div className="mt-4 grid grid-cols-3 gap-3">
              <Counter label="表" value={crawl.tableCount} />
              <Counter label="字段" value={crawl.columnCount} />
              <Counter label="失败表" value={crawl.failedCount} />
              <Counter label="新增" value={crawl.addedCount} />
              <Counter label="更新" value={crawl.updatedCount} />
              <Counter label="标记缺失" value={crawl.removedCount} />
            </div>

            <dl className="mt-4 space-y-1 text-xs text-[var(--yak-components-muted-text)]">
              <div className="flex gap-2">
                <dt>提交</dt>
                <dd>{crawl.createdTime}</dd>
              </div>
              <div className="flex gap-2">
                <dt>开始</dt>
                <dd>{crawl.startedTime || "-"}</dd>
              </div>
              <div className="flex gap-2">
                <dt>结束</dt>
                <dd>{crawl.finishedTime || "-"}</dd>
              </div>
            </dl>

            {crawl.error && <Alert className="mt-3">{crawl.error}</Alert>}
          </>
        )}

        {failures.length > 0 && (
          <div className="mt-4 max-h-64 overflow-auto">
            <Table<CrawlFailure>
              columns={failureColumns}
              dataSource={failures}
              rowKey="tableName"
              size="small"
              bordered
            />
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
