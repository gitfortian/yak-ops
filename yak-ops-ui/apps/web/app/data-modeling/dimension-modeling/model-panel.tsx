import { Alert, Badge, Button, Empty, Spinner, Table, type TableColumns } from "@yak-ops/yak-ui";
import { useEffect, useState, type ReactNode } from "react";
import {
  PUBLICATION_STATUS_LABEL,
  type PublicationStatus,
} from "@/service/data-modeling/dimension-modeling/publication";
import {
  MODEL_TYPE_LABEL,
  type ModelType,
  type LogicalModel,
  type StoragePolicy,
} from "@/service/data-modeling/dimension-modeling/types";

const TYPE_TONE: Record<ModelType, "info" | "warning" | "success" | "neutral"> = {
  DWD: "info",
  DIM: "warning",
  DWS: "success",
  ADS: "neutral",
};
const PUBLICATION_TONE: Record<PublicationStatus, "info" | "success" | "danger" | "warning"> = {
  PENDING: "info",
  SUCCEEDED: "success",
  FAILED: "danger",
  UNKNOWN: "warning",
};
const STORAGE_LABEL: Record<StoragePolicy, string> = {
  DAILY_INCREMENTAL: "每日增量",
  DAILY_FULL: "每日全量",
};

interface Props {
  title: string;
  description: string;
  models: LogicalModel[];
  loading: boolean;
  truncated?: boolean;
  actions?: ReactNode;
  /** Set when the list is one server page, so paging is not applied twice. */
  paging?: {
    pageNo: number;
    pageSize: number;
    total: number;
    onChange: (pageNo: number, pageSize: number) => void;
  };
  /** Shown instead of the table when the list request failed. */
  error?: ReactNode;
  emptyText?: string;
  onEdit: (model: LogicalModel) => void;
  onDelete: (model: LogicalModel) => void;
  onPublish: (model: LogicalModel) => void;
}

export default function ModelPanel(props: Props) {
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const paging = props.paging;
  useEffect(() => {
    if (paging) return;
    const last = Math.max(1, Math.ceil(props.models.length / pageSize));
    if (pageNo > last) setPageNo(last);
  }, [props.models.length, pageSize, pageNo, paging]);
  const pageRows = paging
    ? props.models
    : props.models.slice((pageNo - 1) * pageSize, pageNo * pageSize);
  const current = paging ? paging.pageNo : pageNo;
  const size = paging ? paging.pageSize : pageSize;
  const total = paging ? paging.total : props.models.length;
  const columns: TableColumns<LogicalModel> = [
    { title: "表名", dataIndex: "name", width: 220, ellipsis: true },
    { title: "表中文名", dataIndex: "cnName", width: 180, ellipsis: true },
    {
      title: "类型",
      key: "type",
      width: 92,
      render: (_, row) =>
        row.type ? (
          <Badge tone={TYPE_TONE[row.type]}>{MODEL_TYPE_LABEL[row.type]}</Badge>
        ) : (
          <Badge tone="danger">未知类型</Badge>
        ),
    },
    { title: "分层", dataIndex: "layerName", width: 110, ellipsis: true },
    {
      title: "存储策略",
      key: "storage",
      width: 104,
      render: (_, row) => STORAGE_LABEL[row.storagePolicy],
    },
    { title: "字段数", dataIndex: "fieldCount", width: 76, align: "right" },
    {
      title: "发布状态",
      key: "publication",
      width: 96,
      render: (_, row) =>
        row.latestPublicationStatus ? (
          <Badge tone={PUBLICATION_TONE[row.latestPublicationStatus]}>
            {PUBLICATION_STATUS_LABEL[row.latestPublicationStatus]}
          </Badge>
        ) : (
          <span className="text-xs text-[#98a2b3]">未发布</span>
        ),
    },
    {
      title: "操作",
      key: "actions",
      width: 152,
      align: "center",
      render: (_, row) => (
        <div className="flex items-center justify-center gap-2">
          <Button
            size="small"
            variant="ghost"
            className="px-0.5 font-normal [color:var(--yak-color-primary)] hover:[color:var(--yak-color-primary)]"
            onClick={() => props.onEdit(row)}
          >
            编辑
          </Button>
          <Button
            size="small"
            variant="ghost"
            className="px-0.5 font-normal [color:var(--yak-color-primary)] hover:[color:var(--yak-color-primary)]"
            onClick={() => props.onPublish(row)}
          >
            发布
          </Button>
          <Button
            size="small"
            variant="ghost"
            className="px-0.5 font-normal text-[#667085] hover:text-[#d92d20]"
            onClick={() => props.onDelete(row)}
          >
            删除
          </Button>
        </div>
      ),
    },
  ];
  return (
    <section className="flex min-h-0 min-w-0 flex-1 flex-col gap-3">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="truncate text-sm font-medium">{props.title}</h2>
          <p className="mt-1 text-xs text-[#667475]">{props.description}</p>
        </div>
        {props.actions && <div className="flex flex-wrap items-center gap-2">{props.actions}</div>}
      </header>
      {props.truncated && (
        <p className="text-xs text-[#b54708]">
          模型数量超过单次加载上限，树中只显示最近 1000 个；右侧列表同样基于这批数据。
        </p>
      )}
      <div className="min-h-0 min-w-0 flex-1 overflow-auto">
        {props.loading ? (
          <div className="flex h-full items-center justify-center">
            <Spinner />
          </div>
        ) : props.error ? (
          <Alert>{props.error}</Alert>
        ) : props.models.length === 0 ? (
          <Empty description={props.emptyText ?? "该节点下还没有逻辑模型"} />
        ) : (
          <Table<LogicalModel>
            rowKey="id"
            columns={columns}
            dataSource={pageRows}
            size="small"
            bordered={false}
            scroll={{ x: 1036 }}
            emptyText="暂无逻辑模型"
            pagination={{
              current,
              pageSize: size,
              total,
              showSizeChanger: true,
              pageSizeOptions: [20, 50, 100],
              onChange: (page, nextSize) => {
                if (paging) {
                  paging.onChange(page, nextSize);
                  return;
                }
                setPageSize(nextSize);
                setPageNo(nextSize === pageSize ? page : 1);
              },
            }}
          />
        )}
      </div>
    </section>
  );
}
