import {
  Alert,
  Badge,
  Button,
  Input,
  PageHeader,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import type {
  LogicalModel,
  ModelQuery,
  ModelType,
  StoragePolicy,
} from "@/service/data-modeling/dimension-modeling/types";
import ModelDeleteDialog from "./delete-dialog";
import { useModelOptions, useModelPage } from "./state";

const TYPE_FILTERS: { value: ModelType | "ALL"; label: string }[] = [
  { value: "ALL", label: "全部类型" },
  { value: "DWD", label: "明细表" },
  { value: "DIM", label: "维度表" },
];

const TYPE_TONE: Record<ModelType, "info" | "warning"> = { DWD: "info", DIM: "warning" };
const TYPE_LABEL: Record<ModelType, string> = { DWD: "明细表", DIM: "维度表" };
const STORAGE_LABEL: Record<StoragePolicy, string> = {
  DAILY_INCREMENTAL: "每日增量",
  DAILY_FULL: "每日全量",
};

/** Joins the scope that owns a model with the concept or process it hangs below. */
function ownershipLabel(row: LogicalModel) {
  const scope = row.domainName ?? row.datamartName;
  const detail = row.type === "DIM" ? row.dimensionName : row.processName;
  if (scope && detail) return `${scope} / ${detail}`;
  return scope ?? detail ?? "—";
}

export default function LogicalModelsPage() {
  const navigate = useNavigate();
  const options = useModelOptions();
  const [type, setType] = useState<ModelType | "ALL">("ALL");
  const [layerId, setLayerId] = useState("ALL");
  const [searchText, setSearchText] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [version, setVersion] = useState(0);
  const [deleting, setDeleting] = useState<LogicalModel | null>(null);
  const query = useMemo<ModelQuery>(
    () => ({ type, layerId: layerId === "ALL" ? "" : layerId, keyword, pageNo, pageSize }),
    [type, layerId, keyword, pageNo, pageSize],
  );
  const list = useModelPage(query, version);
  const ownerName = useMemo(() => {
    const names = new Map(options.owners.map((owner) => [owner.userId, owner.displayName]));
    return (id: string) => names.get(id) ?? id;
  }, [options.owners]);
  useEffect(() => {
    if (list.page && !list.loading) {
      const last = Math.max(1, list.page.pagination.pages);
      if (pageNo > last) setPageNo(last);
    }
  }, [list.page, list.loading, pageNo]);
  const columns: TableColumns<LogicalModel> = [
    { title: "表名", dataIndex: "name", width: 220, ellipsis: true },
    { title: "表中文名", dataIndex: "cnName", width: 180, ellipsis: true },
    {
      title: "类型",
      key: "type",
      width: 96,
      render: (_, row) =>
        row.type ? (
          <Badge tone={TYPE_TONE[row.type]}>{TYPE_LABEL[row.type]}</Badge>
        ) : (
          <Badge tone="danger">未知类型</Badge>
        ),
    },
    { title: "分层", dataIndex: "layerName", width: 120, ellipsis: true },
    {
      title: "归属",
      key: "ownership",
      width: 240,
      ellipsis: true,
      render: (_, row) => ownershipLabel(row),
    },
    {
      title: "存储策略",
      key: "storage",
      width: 110,
      render: (_, row) => STORAGE_LABEL[row.storagePolicy],
    },
    {
      title: "生命周期",
      key: "lifecycle",
      width: 100,
      render: (_, row) => `${row.lifecycleDays} 天`,
    },
    { title: "字段数", dataIndex: "fieldCount", width: 80, align: "right" },
    {
      title: "负责人",
      key: "owner",
      width: 140,
      ellipsis: true,
      render: (_, row) => ownerName(row.ownerId),
    },
    {
      title: "操作",
      key: "actions",
      width: 110,
      fixed: "right",
      align: "center",
      render: (_, row) => (
        <div className="flex items-center justify-center gap-2">
          <Button
            size="small"
            variant="ghost"
            className="px-0.5 font-normal [color:var(--yak-color-primary)] hover:[color:var(--yak-color-primary)]"
            onClick={() => navigate(`/data-modeling/logical-models/${encodeURIComponent(row.id)}`)}
          >
            编辑
          </Button>
          <Button
            size="small"
            variant="ghost"
            className="px-0.5 font-normal text-[#667085] hover:text-[#d92d20]"
            onClick={() => setDeleting(row)}
          >
            删除
          </Button>
        </div>
      ),
    },
  ];
  const layerItems = [
    { value: "ALL", label: "全部层级" },
    ...options.layers.map((layer) => ({
      value: layer.id,
      label: `${layer.enCode} · ${layer.cnName}`,
    })),
  ];
  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="逻辑模型"
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <div className="flex items-center gap-2">
            <Button
              size="small"
              disabled={list.loading}
              onClick={() => {
                options.reload();
                setVersion((value) => value + 1);
              }}
            >
              刷新
            </Button>
            <Button
              size="small"
              variant="primary"
              disabled={options.status !== "ready"}
              onClick={() => navigate("/data-modeling/logical-models/new")}
            >
              <Plus size={14} />
              新建模型
            </Button>
          </div>
        }
      />
      <div className="flex min-h-0 flex-1 flex-col gap-3 px-6 pb-4 pt-5 max-md:px-4">
        {options.status === "failed" && (
          <Alert>
            分层、业务分类或成员加载失败
            <Button size="small" onClick={options.reload}>
              重试
            </Button>
          </Alert>
        )}
        <div className="flex flex-wrap items-center gap-2">
          <div className="w-[136px]">
            <Select
              size="small"
              value={type}
              items={TYPE_FILTERS}
              onValueChange={(value) => {
                setType((value ?? "ALL") as ModelType | "ALL");
                setPageNo(1);
              }}
            >
              <SelectTrigger variant="outlined" aria-label="按类型筛选">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {TYPE_FILTERS.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    <SelectItemText>{item.label}</SelectItemText>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="w-[186px]">
            <Select
              size="small"
              value={layerId}
              items={layerItems}
              onValueChange={(value) => {
                setLayerId(value ?? "ALL");
                setPageNo(1);
              }}
            >
              <SelectTrigger variant="outlined" aria-label="按分层筛选">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {layerItems.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    <SelectItemText>{item.label}</SelectItemText>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <form
            className="flex max-w-full items-center gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              setKeyword(searchText.trim());
              setPageNo(1);
            }}
          >
            <Input
              size="small"
              variant="outlined"
              value={searchText}
              maxLength={128}
              aria-label="搜索表名或中文名"
              placeholder="表名或中文名"
              onChange={(event) => setSearchText(event.target.value)}
            />
            <Button size="small" type="submit">
              搜索
            </Button>
            {(searchText || keyword) && (
              <Button
                size="small"
                onClick={() => {
                  setSearchText("");
                  setKeyword("");
                  setPageNo(1);
                }}
              >
                清空
              </Button>
            )}
          </form>
        </div>
        {list.failed && (
          <Alert>
            逻辑模型加载失败
            <Button size="small" onClick={() => setVersion((value) => value + 1)}>
              重试
            </Button>
          </Alert>
        )}
        <div className="min-h-0 min-w-0 flex-1 overflow-auto">
          <Table<LogicalModel>
            rowKey="id"
            columns={columns}
            dataSource={list.failed ? [] : (list.page?.bizData ?? [])}
            loading={list.loading || options.status === "loading"}
            size="small"
            bordered={false}
            scroll={{ x: 1470 }}
            emptyText="暂无逻辑模型"
            pagination={{
              current: pageNo,
              pageSize,
              total: list.page?.pagination.total ?? 0,
              disabled: list.loading,
              showSizeChanger: true,
              pageSizeOptions: [20, 50, 100],
              onChange: (page, size) => {
                setPageSize(size);
                setPageNo(size === pageSize ? page : 1);
              },
            }}
          />
        </div>
      </div>
      {deleting && (
        <ModelDeleteDialog
          model={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={() => {
            setDeleting(null);
            setVersion((value) => value + 1);
          }}
        />
      )}
    </div>
  );
}
