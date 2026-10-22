import { Alert, Button, Spinner, Table, type TableColumns } from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";
import { getAssociatedModels } from "@/service/data-modeling/warehouse-planning";
import type {
  AssociatedModels,
  LogicalModelSummary,
} from "@/service/data-modeling/warehouse-planning/types";

const columns: TableColumns<LogicalModelSummary> = [
  { title: "逻辑模型名称", dataIndex: "name" },
  { title: "英文编码", dataIndex: "code" },
];
export default function ProcessAssets({ processId }: { processId: string }) {
  const [data, setData] = useState<AssociatedModels | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setFailed(false);
    void getAssociatedModels(processId, pageNo, pageSize, controller.signal)
      .then((value) => {
        if (active) setData(value);
      })
      .catch(() => {
        if (active) setFailed(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [processId, pageNo, pageSize, version]);
  return (
    <section className="space-y-3">
      <h3>关联逻辑模型</h3>
      {loading ? (
        <Spinner />
      ) : failed ? (
        <Alert>
          关联逻辑模型加载失败
          <Button size="small" onClick={() => setVersion((value) => value + 1)}>
            重试
          </Button>
        </Alert>
      ) : data?.availability === "NOT_ENABLED" ? (
        <p className="text-sm">逻辑模型尚未开放</p>
      ) : data?.availability === "AVAILABLE" ? (
        <Table
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={data.page.bizData}
          pagination={{
            current: pageNo,
            pageSize,
            total: data.page.pagination.total,
            onChange: (page, size) => {
              setPageNo(page);
              setPageSize(size);
            },
          }}
          emptyText="暂无关联逻辑模型"
        />
      ) : null}
    </section>
  );
}
