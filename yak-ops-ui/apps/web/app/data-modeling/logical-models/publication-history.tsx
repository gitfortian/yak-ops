import {
  Alert,
  Badge,
  Button,
  Empty,
  Spinner,
  Table,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";
import {
  classifyPublicationFailure,
  getPublications,
  reconcilePublication,
  refreshPublicationMetadata,
  PUBLICATION_STATUS_HINT,
  PUBLICATION_STATUS_LABEL,
  type PublicationRecord,
  type PublicationStatus,
} from "@/service/data-modeling/dimension-modeling/publication";

const STATUS_TONE: Record<PublicationStatus, "info" | "success" | "danger" | "warning"> = {
  PENDING: "info",
  SUCCEEDED: "success",
  FAILED: "danger",
  UNKNOWN: "warning",
};

const formatTime = (value: string | null) => (value ? value.replace("T", " ").slice(0, 19) : "-");

const address = (row: PublicationRecord) =>
  [row.database, row.schema].filter(Boolean).join(".") || "-";

interface Props {
  modelId: string;
  /** Bumped by the dialog after a publication, so the newest record appears without a reopen. */
  refreshKey: number;
}

/**
 * Publication history of one model.
 *
 * <p>Two states are actionable and nothing else is: an {@code UNKNOWN} record can be reconciled
 * against the real table, and a {@code SUCCEEDED} one can be re-collected. A {@code FAILED} record is
 * terminal — re-publishing is the way forward, so no retry button pretends otherwise.
 */
export default function PublicationHistory({ modelId, refreshKey }: Props) {
  const [items, setItems] = useState<PublicationRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState("");
  const [busyId, setBusyId] = useState("");
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setFailure("");
    void getPublications(modelId, controller.signal)
      .then((records) => {
        if (!active) return;
        setItems(records);
      })
      .catch((error) => {
        if (!active) return;
        const result = classifyPublicationFailure(error);
        setItems([]);
        setFailure(result.message ?? "发布历史加载失败，请稍后重试");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [modelId, refreshKey, reload]);

  const act = async (row: PublicationRecord, action: "reconcile" | "refresh") => {
    if (busyId) return;
    setBusyId(row.id);
    try {
      if (action === "reconcile") {
        const record = await reconcilePublication(row.id);
        if (record.status === "SUCCEEDED") toast.success("对账完成：目标表与模型一致");
        else toast.error(record.error ?? "对账完成：目标表与模型不一致");
      } else {
        const snapshot = await refreshPublicationMetadata(row.id);
        toast.success(
          `元数据已重新采集：${snapshot.tableCount} 张表 / ${snapshot.fieldCount} 个字段`,
        );
      }
      setReload((value) => value + 1);
    } catch (error) {
      toast.error(classifyPublicationFailure(error).message ?? "操作失败，请稍后重试");
    } finally {
      setBusyId("");
    }
  };

  const columns: TableColumns<PublicationRecord> = [
    {
      title: "状态",
      key: "status",
      width: 100,
      render: (_, row) =>
        row.status ? (
          <Badge tone={STATUS_TONE[row.status]}>{PUBLICATION_STATUS_LABEL[row.status]}</Badge>
        ) : (
          <Badge tone="neutral">未知状态</Badge>
        ),
    },
    {
      title: "目标地址",
      key: "target",
      width: 200,
      ellipsis: true,
      render: (_, row) => address(row),
    },
    { title: "表名", dataIndex: "table", width: 200, ellipsis: true },
    {
      title: "结果",
      key: "error",
      ellipsis: true,
      render: (_, row) => (
        <span
          className={row.error ? "text-[#b42318]" : "text-[#667475]"}
          title={row.error ?? PUBLICATION_STATUS_HINT[row.status ?? "PENDING"]}
        >
          {row.error ?? PUBLICATION_STATUS_HINT[row.status ?? "PENDING"]}
        </span>
      ),
    },
    {
      title: "发起时间",
      key: "createdTime",
      width: 168,
      render: (_, row) => formatTime(row.createdTime),
    },
    {
      title: "操作",
      key: "actions",
      width: 92,
      align: "center",
      render: (_, row) =>
        row.status === "UNKNOWN" ? (
          <Button
            size="small"
            variant="ghost"
            className="px-0.5 font-normal [color:var(--yak-color-primary)]"
            loading={busyId === row.id}
            onClick={() => void act(row, "reconcile")}
          >
            对账
          </Button>
        ) : row.status === "SUCCEEDED" ? (
          <Button
            size="small"
            variant="ghost"
            className="px-0.5 font-normal [color:var(--yak-color-primary)]"
            loading={busyId === row.id}
            onClick={() => void act(row, "refresh")}
          >
            重新采集
          </Button>
        ) : (
          <span className="text-xs text-[#98a2b3]">-</span>
        ),
    },
  ];

  if (loading)
    return (
      <div className="flex h-16 items-center justify-center">
        <Spinner />
      </div>
    );
  if (failure) return <Alert>{failure}</Alert>;
  if (items.length === 0) return <Empty description="还没有发布记录" />;
  return (
    <Table<PublicationRecord>
      rowKey="id"
      columns={columns}
      dataSource={items}
      size="small"
      bordered={false}
      scroll={{ x: 900 }}
      pagination={false}
      emptyText="还没有发布记录"
    />
  );
}
