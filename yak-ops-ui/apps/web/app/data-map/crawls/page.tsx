import {
  Alert,
  Badge,
  Button,
  PageHeader,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";

import { countLabel } from "@/service/data-map/catalog";
import { listCrawls } from "@/service/data-map/crawl";
import { dataMapFailureMessage } from "@/service/data-map/request";
import type { Crawl } from "@/service/data-map/types";

import { dataMapKeys } from "../shared/keys";
import { CRAWL_STATUS_LABEL, CRAWL_STATUS_TONE, locationLabel } from "../shared/labels";
import { useDatasourceOptions } from "../shared/lookups";
import { useDataMapScope } from "../shared/scope";
import CrawlDetailDialog from "./crawl-detail-dialog";
import CrawlDialog from "./crawl-dialog";

const DEFAULT_PAGE_SIZE = 20;
const ACTIVE_POLL_MS = 3000;

const isActive = (status: string) => status === "PENDING" || status === "RUNNING";

const readPageNo = (value: string | null) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1;
};

export default function CrawlsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { scope, ready } = useDataMapScope();
  const datasourceId = searchParams.get("datasourceId") ?? "";
  const pageNo = readPageNo(searchParams.get("pageNo"));
  const createOpen = searchParams.get("create") === "1";
  const [detailId, setDetailId] = useState("");

  const datasources = useDatasourceOptions();
  const datasourceNames = new Map(datasources.options.map((item) => [item.id, item.name]));

  const write = (patch: { datasourceId?: string; pageNo?: number; create?: boolean }) => {
    const next = new URLSearchParams(searchParams);
    const nextDatasource = patch.datasourceId ?? datasourceId;
    if (nextDatasource) next.set("datasourceId", nextDatasource);
    else next.delete("datasourceId");

    const nextPage = patch.pageNo ?? (patch.datasourceId === undefined ? pageNo : 1);
    if (nextPage > 1) next.set("pageNo", String(nextPage));
    else next.delete("pageNo");

    const nextCreate = patch.create ?? createOpen;
    if (nextCreate) next.set("create", "1");
    else next.delete("create");
    setSearchParams(next, { replace: true });
  };

  const page = useQuery({
    queryKey: dataMapKeys.crawls(scope, datasourceId, pageNo, DEFAULT_PAGE_SIZE),
    queryFn: ({ signal }) =>
      listCrawls(
        { datasourceId: datasourceId || undefined, pageNo, pageSize: DEFAULT_PAGE_SIZE },
        signal,
      ),
    enabled: ready,
    placeholderData: keepPreviousData,
    refetchInterval: (query) =>
      (query.state.data?.bizData ?? []).some((crawl) => isActive(crawl.status))
        ? ACTIVE_POLL_MS
        : false,
  });

  const records = page.data?.bizData ?? [];
  const total = page.data?.pagination?.total ?? 0;

  const columns: TableColumns<Crawl> = [
    {
      key: "datasource",
      title: "数据源",
      width: 180,
      ellipsis: true,
      render: (_value, record) => datasourceNames.get(record.datasourceId) ?? record.datasourceId,
    },
    {
      key: "scope",
      title: "采集范围",
      minWidth: 160,
      ellipsis: true,
      render: (_value, record) => locationLabel(record.database, record.schema),
    },
    {
      key: "status",
      title: "状态",
      width: 100,
      render: (_value, record) => (
        <Badge tone={CRAWL_STATUS_TONE[record.status]}>{CRAWL_STATUS_LABEL[record.status]}</Badge>
      ),
    },
    {
      key: "tables",
      title: "表 / 字段",
      width: 110,
      render: (_value, record) => `${record.tableCount} / ${record.columnCount}`,
    },
    {
      key: "changes",
      title: "变更",
      minWidth: 170,
      render: (_value, record) =>
        `新增 ${record.addedCount} · 更新 ${record.updatedCount} · 缺失 ${record.removedCount}`,
    },
    {
      key: "failed",
      title: "失败表",
      width: 90,
      render: (_value, record) =>
        record.failedCount > 0 ? (
          <Badge tone="warning">{record.failedCount}</Badge>
        ) : (
          <span className="text-[var(--yak-components-muted-text)]">0</span>
        ),
    },
    { key: "createdBy", title: "提交人", dataIndex: "createBy", width: 140, ellipsis: true },
    {
      key: "createdTime",
      title: "提交时间",
      dataIndex: "createdTime",
      width: 170,
    },
    {
      key: "actions",
      title: "操作",
      width: 80,
      align: "center",
      render: (_value, record) => (
        <Button size="small" variant="ghost" onClick={() => setDetailId(record.id)}>
          详情
        </Button>
      ),
    },
  ];

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="采集任务"
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <Button size="small" variant="primary" onClick={() => write({ create: true })}>
            提交采集
          </Button>
        }
      />

      <div className="flex min-h-0 flex-1 flex-col px-6 pb-4 pt-5 max-md:px-4">
        <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
          <section className="flex shrink-0 flex-wrap items-center gap-2">
            <Select
              size="small"
              items={[
                { value: "", label: "全部数据源" },
                ...datasources.options.map((item) => ({ value: item.id, label: item.name })),
              ]}
              value={datasourceId}
              onValueChange={(value) => write({ datasourceId: value ?? "" })}
            >
              <SelectTrigger variant="outlined" className="w-[220px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">
                  <SelectItemText>全部数据源</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
                {datasources.options.map((item) => (
                  <SelectItem key={item.id} value={item.id}>
                    <SelectItemText>{item.name}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <span className="text-xs text-[var(--yak-components-muted-text)]">
              共 {countLabel(total)} 次采集
            </span>
            {page.isError && (
              <Button size="small" onClick={() => void page.refetch()}>
                重试
              </Button>
            )}
          </section>

          {page.isError && (
            <Alert className="mt-2">{dataMapFailureMessage(page.error, "采集任务加载失败")}</Alert>
          )}

          <div className="mt-3 min-h-0 flex-1 overflow-auto">
            <Table
              columns={columns}
              dataSource={records}
              rowKey="id"
              size="medium"
              loading={page.isFetching}
              emptyText="还没有采集任务"
              rowHoverable
              scroll={{ x: 1220 }}
              pagination={{
                current: pageNo,
                pageSize: DEFAULT_PAGE_SIZE,
                total,
                onChange: (nextPage) => write({ pageNo: nextPage }),
              }}
            />
          </div>
        </div>
      </div>

      {createOpen && (
        <CrawlDialog
          onClose={() => write({ create: false })}
          onSubmitted={() => write({ create: false })}
        />
      )}

      {detailId && <CrawlDetailDialog crawlId={detailId} onClose={() => setDetailId("")} />}
    </div>
  );
}
