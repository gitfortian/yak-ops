import {
  Alert,
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
  type BadgeProps,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { ArrowRight } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import {
  cancelDataSyncInstance,
  getDataSyncInstance,
  listDataSyncInstances,
  type DataSyncInstanceRecord,
  type DataSyncInstanceStatus,
  type DataSyncType,
} from "@/service/data-sync";

const PAGE_SIZE = 20;
const POLL_INTERVAL_MILLIS = 2000;

const isActive = (record?: DataSyncInstanceRecord) =>
  record?.status === "PENDING" || record?.status === "RUNNING";

const statusMeta = (
  status: DataSyncInstanceStatus,
  realtime: boolean,
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
      return { label: realtime ? "已停止" : "已取消", tone: "neutral" };
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

const pathText = (database?: string, schema?: string, table?: string) =>
  [database, schema, table].filter(Boolean).join(".") || "-";

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
    if (!records.some(isActive)) return;
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
        const meta = statusMeta(record.status, realtime);
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
      key: "readRows",
      title: realtime ? "读取事件" : "读取",
      width: 110,
      align: "right",
      render: (_value, record) => (record.readRows ?? 0).toLocaleString(),
    },
    {
      key: "writeRows",
      title: realtime ? "写入事件" : "写入",
      width: 110,
      align: "right",
      render: (_value, record) => (record.writeRows ?? 0).toLocaleString(),
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
        isActive(record) ? (
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
            onClick={() => navigate(`${basePath}/instances/${record.id}`)}
          >
            详情
          </Button>
        ),
    },
  ];

  const canceledLabel = realtime ? "已停止" : "已取消";
  const statusOptions: Array<[DataSyncInstanceStatus | "ALL", string]> = [
    ["ALL", "全部状态"],
    ["PENDING", "等待"],
    ["RUNNING", "运行中"],
    ["SUCCEEDED", "成功"],
    ["FAILED", "失败"],
    ["CANCELED", canceledLabel],
    ["LOST", "已丢失"],
  ];
  const statusItems = Object.fromEntries(statusOptions);

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
            items={statusItems}
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
              {statusOptions.map(([value, label]) => (
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
          scroll={{ x: 1260 }}
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

interface DataSyncInstanceDetailPageProps {
  syncType: DataSyncType;
  basePath: string;
  listPath?: string;
}

export function DataSyncInstanceDetailPage({
  syncType,
  basePath,
  listPath,
}: DataSyncInstanceDetailPageProps) {
  const realtime = syncType === "REALTIME";
  const resolvedListPath = listPath ?? `${basePath}?tab=instances`;
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [record, setRecord] = useState<DataSyncInstanceRecord>();
  const [loading, setLoading] = useState(true);
  const [canceling, setCanceling] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    const value = await getDataSyncInstance(id);
    if (value.syncType !== syncType) {
      toast.error("实例类型与当前页面不匹配");
      navigate(resolvedListPath, { replace: true });
      return;
    }
    setRecord(value);
    setLoading(false);
  }, [id, navigate, resolvedListPath, syncType]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!isActive(record)) return;
    const timer = window.setInterval(() => void load(), POLL_INTERVAL_MILLIS);
    return () => window.clearInterval(timer);
  }, [load, record]);

  const cancel = async () => {
    if (!record || canceling) return;
    setCanceling(true);
    try {
      setRecord(await cancelDataSyncInstance(record.id));
    } finally {
      setCanceling(false);
    }
  };

  if (loading || !record) {
    return <div className="p-8 text-sm text-[#667085]">正在加载同步实例...</div>;
  }

  const meta = statusMeta(record.status, realtime);
  const snapshot = record.definitionSnapshot;
  const readLabel = realtime ? "读取事件" : "读取";
  const writeLabel = realtime ? "写入事件" : "写入";

  return (
    <div className="min-h-full bg-[#f6f6f6] text-[#242731]">
      <PageHeader
        title={`${record.taskName} / ${record.id}`}
        description={`任务版本 v${record.taskVersion} · ${
          record.triggerType === "MANUAL" ? "手动运行" : record.triggerType
        }`}
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <>
            {isActive(record) ? (
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

      <div className="space-y-4 px-6 pb-8 pt-5 max-md:px-4">
        {realtime && isActive(record) ? (
          <Alert>停止后将从最近 Checkpoint 续跑，少量未确认事件可能重复消费。</Alert>
        ) : null}

        <section className="rounded-lg border border-[#e6e8eb] bg-white p-5">
          <div className="flex flex-wrap items-center gap-3">
            <Badge tone={meta.tone}>{meta.label}</Badge>
            <span className="text-sm text-[#667085]">
              {record.startTime || record.createTime || "-"} → {record.finishTime || "运行中"}
            </span>
          </div>

          <div className="mt-5 grid grid-cols-3 gap-4 max-md:grid-cols-1">
            {[
              [readLabel, String(record.readRows ?? 0)],
              [writeLabel, String(record.writeRows ?? 0)],
              ["状态", meta.label],
            ].map(([label, value]) => (
              <div key={label} className="rounded-lg bg-[#fafafa] px-4 py-3">
                <div className="text-xs text-[#98a2b3]">{label}</div>
                <div className="mt-1 text-lg font-semibold text-[#344054]">{value}</div>
              </div>
            ))}
          </div>

          {realtime ? (
            <div className="mt-3 text-xs leading-5 text-[#98a2b3]">
              实时指标统计 YakFlow 变更事件；UPDATE 会产生 UPDATE_BEFORE 与 UPDATE_AFTER 两个事件。
            </div>
          ) : null}
        </section>

        {snapshot ? (
          <section className="rounded-lg border border-[#e6e8eb] bg-white">
            <h2 className="border-b border-[#eef0f3] bg-[#fafafa] px-4 py-2.5 text-sm font-semibold text-[#344054]">
              任务配置快照
            </h2>
            <div className="p-5">
              <div className="flex items-center gap-4">
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium text-[#344054]">
                    {snapshot.source.dataSourceName || snapshot.source.dataSourceId}
                  </div>
                  <div className="mt-1 text-xs text-[#667085]">
                    {pathText(
                      snapshot.source.database,
                      snapshot.source.schema,
                      snapshot.source.table,
                    )}
                  </div>
                </div>
                <ArrowRight size={18} className="shrink-0 text-[#98a2b3]" />
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium text-[#344054]">
                    {snapshot.target.dataSourceName || snapshot.target.dataSourceId}
                  </div>
                  <div className="mt-1 text-xs text-[#667085]">
                    {pathText(
                      snapshot.target.database,
                      snapshot.target.schema,
                      snapshot.target.table,
                    )}
                  </div>
                </div>
              </div>

              {snapshot.runtimeConfig ? (
                <div className="mt-5 grid grid-cols-4 gap-3 text-xs max-lg:grid-cols-2">
                  <div>Fetch Size：{snapshot.runtimeConfig.fetchSize}</div>
                  <div>读取批次：{snapshot.runtimeConfig.readBatchSize}</div>
                  <div>写入批次：{snapshot.runtimeConfig.writeBatchSize}</div>
                  <div>超时：{snapshot.runtimeConfig.timeoutSeconds}s</div>
                </div>
              ) : null}

              {snapshot.realtimeConfig ? (
                <div className="mt-5 grid grid-cols-5 gap-3 text-xs max-xl:grid-cols-3 max-lg:grid-cols-2">
                  <div>Checkpoint：{snapshot.realtimeConfig.checkpointIntervalSeconds}s</div>
                  <div>CDC 队列：{snapshot.realtimeConfig.queueCapacity}</div>
                  <div>读取批次：{snapshot.realtimeConfig.pollBatchSize}</div>
                  <div>写入批次：{snapshot.realtimeConfig.writeBatchSize}</div>
                  <div>超时：{snapshot.realtimeConfig.timeoutSeconds}s</div>
                </div>
              ) : null}
            </div>
          </section>
        ) : null}

        {record.errorMessage ? (
          <section className="rounded-lg border border-[#fecdca] bg-[#fffbfa] p-4">
            <div className="text-sm font-medium text-[#b42318]">失败原因</div>
            <div className="mt-2 whitespace-pre-wrap break-words text-sm text-[#667085]">
              {record.errorMessage}
            </div>
          </section>
        ) : null}
      </div>
    </div>
  );
}
