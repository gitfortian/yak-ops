import { Alert, Button, Spinner, Table, type TableColumns } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";

import { getTablePreview } from "@/service/data-map/catalog";
import { dataMapFailureMessage, isPreviewBusy } from "@/service/data-map/request";

import { dataMapKeys } from "../../shared/keys";
import { useDataMapScope } from "../../shared/scope";

type PreviewRow = Record<string, string>;

const ROW_KEY = "__rowKey";
const ROW_CAP = 100;

/** Preview is read on demand: a detail visit must not sample the user's data. */
export default function PreviewTab({ tableId }: { tableId: string }) {
  const { scope } = useDataMapScope();
  const preview = useQuery({
    queryKey: dataMapKeys.preview(scope, tableId),
    queryFn: ({ signal }) => getTablePreview(tableId, signal),
    enabled: false,
    retry: false,
    gcTime: 0,
  });

  const data = preview.data;
  const columns: TableColumns<PreviewRow> = (data?.columns ?? []).map((column, index) => ({
    key: `c${index}`,
    title: (
      <span className="flex flex-col">
        <span className="truncate text-xs font-medium">{column.name}</span>
        <span className="truncate text-[11px] font-normal text-[var(--yak-components-muted-text)]">
          {column.typeName}
        </span>
      </span>
    ),
    dataIndex: `c${index}`,
    minWidth: 140,
    ellipsis: true,
  }));
  const rows: PreviewRow[] =
    data?.rows.map((row, rowIndex) => {
      const record: PreviewRow = { [ROW_KEY]: String(rowIndex) };
      row.forEach((cell, index) => {
        record[`c${index}`] = cell;
      });
      return record;
    }) ?? [];

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3">
        <Button
          size="small"
          variant="primary"
          loading={preview.isFetching}
          onClick={() => void preview.refetch()}
        >
          {data ? "重新预览" : "加载预览"}
        </Button>
        {data && (
          <span className="text-xs text-[var(--yak-components-muted-text)]">
            {data.rows.length} 行
            {data.truncated === "SIZE"
              ? " · 样本因响应体积达到上限被截断"
              : ` · 最多展示 ${ROW_CAP} 行`}
          </span>
        )}
      </div>

      {preview.isError && (
        <Alert>
          {isPreviewBusy(preview.error)
            ? "当前预览查询过多，请稍后重试。"
            : dataMapFailureMessage(preview.error, "预览加载失败，请稍后重试。")}
        </Alert>
      )}

      {data && <Alert>{data.notice}</Alert>}

      {preview.isFetching && !data && (
        <div className="flex items-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
          <Spinner size="small" />
          正在读取样本
        </div>
      )}

      {data && (
        <Table
          columns={columns}
          dataSource={rows}
          rowKey={ROW_KEY}
          size="small"
          bordered
          pagination={false}
          emptyText="该表没有数据"
          scroll={{ x: Math.max(columns.length * 140, 480) }}
        />
      )}
    </div>
  );
}
