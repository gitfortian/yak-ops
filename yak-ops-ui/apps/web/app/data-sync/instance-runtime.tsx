import {
  Badge,
  Button,
  Input,
  PageHeader,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import {
  cancelDataSyncInstance,
  getDataSyncInstance,
  listDataSyncAttempts,
  listDataSyncInstances,
  type DataSyncAttemptRecord,
  type DataSyncInstanceRecord,
  type DataSyncInstanceStatus,
  type DataSyncType,
} from "@/service/data-sync";

import {
  DataSyncExecutionDetailContent,
  dataSyncDurationText,
  dataSyncInstanceStatusMeta,
  dataSyncTriggerText,
  isActiveDataSyncInstance,
} from "./execution-detail";

const PAGE_SIZE = 20;
const POLL_INTERVAL_MILLIS = 2000;

interface DataSyncInstancesProps {
  syncType: DataSyncType;
  basePath: string;
  taskId?: string;
}

export function DataSyncInstances({ syncType, basePath, taskId }: DataSyncInstancesProps) {
  const realtime = syncType === "REALTIME";
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
        syncType,
        keyword: keyword.trim() || undefined,
        status: status === "ALL" ? undefined : status,
      });
      setRecords(result?.bizData || []);
      setTotal(result?.pagination?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo, status, syncType, taskId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadInstances(), keyword.trim() ? 250 : 0);
    return () => window.clearTimeout(timer);
  }, [keyword, loadInstances]);

  useEffect(() => {
    if (!records.some(isActiveDataSyncInstance)) return;
    const timer = window.setInterval(() => void loadInstances(), POLL_INTERVAL_MILLIS);
    return () => window.clearInterval(timer);
  }, [loadInstances, records]);

  const cancel = async (record: DataSyncInstanceRecord) => {
    if (cancelingId) return;
    setCancelingId(record.id);
    try {
      await cancelDataSyncInstance(record.id);
      toast.success(realtime ? "实时同步实例已停止" : "同步实例已停止");
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
          onClick={() => navigate(`${basePath}/instances/${record.id}`)}
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
        return (
    <div className="min-h-full bg-[#f6f6f6] text-[#242731]">
      <PageHeader
        title={`${record.taskName} / ${record.id}`}
        description={`任务版本 v${record.taskVersion} · ${dataSyncTriggerText(record.triggerType)}`}
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <>
            {isActiveDataSyncInstance(record) ? (
              <Button
                size="small"
                variant="danger"
                loading={canceling}
                onClick={() => void cancel()}
              >
                停止
              </Button>
            ) : null}
            <Button size="small" onClick={() => navigate(resolvedListPath)}>
              返回实例列表
            </Button>
          </>
        }
      />

      <div className="px-6 pb-8 pt-5 max-md:px-4">
        <DataSyncExecutionDetailContent
          record={record}
          attempts={attempts}
          realtime={realtime}
          showRealtimeStopCaution
        />
      </div>
    </div>
  );
}
