import {
  Badge,
  Button,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  toast,
  type BadgeProps,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import {
  cancelDataSyncInstance,
  listDataSyncInstances,
  type DataSyncInstanceRecord,
  type DataSyncInstanceStatus,
} from "@/service/data-sync";

const PAGE_SIZE = 20;

const statusMeta = (
  status: DataSyncInstanceStatus,
): { label: string; tone: BadgeProps["tone"] } => {
  switch (status) {
    case "PENDING":
      return { label: "等待", tone: "neutral" };
    case "RUNNING":
      return { label: "运行中", tone: "info" };
    case "SUCCEEDED":
      return { label: "成功", tone: "success" };
    case "FAILED":
      return { label: "失败", tone: "danger" };
    case "CANCELED":
      return { label: "已取消", tone: "neutral" };
    case "LOST":
      return { label: "已丢失", tone: "warning" };
    default:
      return { label: status || "-", tone: "neutral" };
  }
};

const durationText = (record: DataSyncInstanceRecord) => {
  if (!record.startTime) return "-";
  const start = Date.parse(record.startTime);
  const end = record.finishTime ? Date.parse(record.finishTime) : Date.now();
  if (Number.isNaN(start) || Number.isNaN(end)) return "-";
  const seconds = Math.max(0, Math.floor((end - start) / 1000));
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  return `${minutes}m ${seconds % 60}s`;
};

interface OfflineSyncInstancesProps {
  taskId?: string;
}

export function OfflineSyncInstances({ taskId }: OfflineSyncInstancesProps) {
  const navigate = useNavigate();
  const [records, setRecords] = useState<DataSyncInstanceRecord[]>([]);
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<DataSyncInstanceStatus | "ALL">("ALL");
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [cancelingId, setCancelingId] = useState<string>();

  const loadInstances = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listDataSyncInstances({
        pageNo,
        pageSize: PAGE_SIZE,
        taskId,
        keyword: keyword.trim() || undefined,
        status: status === "ALL" ? undefined : status,
      });
      setRecords(result?.bizData || []);
      setTotal(result?.pagination?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo, status, taskId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadInstances(), keyword.trim() ? 250 : 0);
    return () => window.clearTimeout(timer);
  }, [keyword, loadInstances]);

  useEffect(() => {
    if (!records.some((record) => record.status === "PENDING" || record.status === "RUNNING")) {
      return;
    }
    const timer = window.setInterval(() => void loadInstances(), 1500);
    return () => window.clearInterval(timer);
  }, [loadInstances, records]);

  const cancel = async (record: DataSyncInstanceRecord) => {
    if (cancelingId) return;
    setCancelingId(record.id);
    try {
      await cancelDataSyncInstance(record.id);
      toast.success("同步实例已停止");
      await loadInstances();
    } finally {
      setCancelingId(undefined);
    }
  };

  const columns: TableColumns<DataSyncInstanceRecord> = [
    {
      key: "id",
      title: "实例 ID",
      minWidth: 180,
      render: (_value, record) => (
        <button
          type="button"
          className="cursor-pointer border-0 bg-transparent p-0 text-left text-[13px] text-[var(--yak-color-primary)]"
          onClick={() => navigate(`/offline-sync/instances/${record.id}`)}
        >
          {record.id}
        </button>
      ),
    },
    {
      key: "task",
      title: "任务",
      minWidth: 220,
      render: (_value, record) => (
        <div>
          <div className="text-[13px] font-medium text-[#344054]">{record.taskName}</div>
          <div className="text-xs text-[#98a2b3]">v{record.taskVersion}</div>
        </div>
      ),
    },
    {
      key: "status",
      title: "状态",
      width: 110,
      render: (_value, record) => {
        const meta = statusMeta(record.status);
        return <Badge tone={meta.tone}>{meta.label}</Badge>;
      },
    },
    {
      key: "triggerType",
      title: "启动方式",
      width: 100,
      render: (_value, record) => (record.triggerType === "MANUAL" ? "手动" : record.triggerType),
    },
    {
      key: "startTime",
      title: "开始时间",
      width: 180,
      render: (_value, record) => record.startTime || record.createTime || "-",
    },
    {
      key: "duration",
      title: "耗时",
      width: 100,
      render: (_value, record) => durationText(record),
    },
    {
      key: "actions",
      title: "操作",
      width: 130,
      align: "center",
      render: (_value, record) =>
        record.status === "PENDING" || record.status === "RUNNING" ? (
          <Button
            size="small"
            variant="ghost"
            loading={cancelingId === record.id}
            className="px-1 text-xs font-normal text-[#d92d20]"
            onClick={() => void cancel(record)}
          >
            停止
          </Button>
        ) : (
          <Button
            size="small"
            variant="ghost"
            className="px-1 text-xs font-normal text-[#667085] hover:text-[var(--yak-color-primary)]"
            onClick={() => navigate(`/offline-sync/instances/${record.id}`)}
          >
            详情
          </Button>
        ),
    },
  ];

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex shrink-0 flex-wrap items-center gap-2">
        <div className="w-[300px]">
          <Input
            size="small"
            variant="outlined"
            value={keyword}
            placeholder="搜索任务名称"
            onChange={(event) => {
              setKeyword(event.target.value);
              setPageNo(1);
            }}
          />
        </div>
        <div className="w-[150px]">
          <Select
            size="small"
            value={status}
            onValueChange={(value) => {
              setStatus(String(value || "ALL") as DataSyncInstanceStatus | "ALL");
              setPageNo(1);
            }}
          >
            <SelectTrigger variant="outlined">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {[
                ["ALL", "全部状态"],
                ["PENDING", "等待"],
                ["RUNNING", "运行中"],
                ["SUCCEEDED", "成功"],
                ["FAILED", "失败"],
                ["CANCELED", "已取消"],
                ["LOST", "已丢失"],
              ].map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  <SelectItemText>{label}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="mt-4 min-h-0 flex-1">
        <Table<DataSyncInstanceRecord>
          className="min-h-full"
          columns={columns}
          dataSource={records}
          rowKey="id"
          loading={loading}
          bordered
          size="medium"
          scroll={{ x: 1040 }}
          emptyText={taskId ? "这个任务还没有运行实例" : "还没有同步实例"}
          pagination={
            total > 0
              ? {
                  current: pageNo,
                  pageSize: PAGE_SIZE,
                  total,
                  disabled: loading,
                  onChange: (page) => setPageNo(page),
                }
              : false
          }
        />
      </div>
    </div>
  );
}

export { statusMeta };
