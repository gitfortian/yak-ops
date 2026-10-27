import { Alert, Button, Table, type TableColumns } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";

import { getTableChanges } from "@/service/data-map/catalog";
import { dataMapFailureMessage } from "@/service/data-map/request";
import type { TableChange } from "@/service/data-map/types";

import { dataMapKeys } from "../../shared/keys";
import { CHANGE_TYPE_LABEL } from "../../shared/labels";
import { useDataMapScope } from "../../shared/scope";

const columns: TableColumns<TableChange> = [
  {
    key: "changeType",
    title: "变更类型",
    width: 170,
    render: (_value, record) => CHANGE_TYPE_LABEL[record.changeType] ?? record.changeType,
  },
  {
    key: "detail",
    title: "明细",
    dataIndex: "detail",
    minWidth: 320,
    render: (_value, record) => <span className="text-[13px]">{record.detail || "-"}</span>,
  },
  { key: "createdTime", title: "时间", dataIndex: "createdTime", width: 170 },
];

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

  return (
    <Table
      columns={columns}
      dataSource={changes.data ?? []}
      rowKey={(record) => `${record.changeType}-${record.createdTime}-${record.detail}`}
      size="medium"
      loading={changes.isPending}
      emptyText="暂无变更记录"
      pagination={false}
      scroll={{ x: 760 }}
    />
  );
}
