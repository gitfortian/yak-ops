import { Badge, Button, Input, Table, toast, type TableColumns } from "@yak-ops/yak-ui";
import { useCallback, useEffect, useState } from "react";

import { useActiveTaskInstances } from "@/app/data-sync/task-lifecycle";
import {
  cancelDataSyncInstance,
  listDataSyncTasks,
  runDataSyncTask,
  type DataSyncTaskRecord,
  type DataSyncType,
} from "@/service/data-sync";

const PAGE_SIZE = 20;

interface DataSyncTaskOperationsProps {
  syncType: DataSyncType;
  onOpenInstances: (taskId: string) => void;
  onOpenInstanceDetail: (instanceId: string) => void;
}

export function DataSyncTaskOperations({
  syncType,
  onOpenInstances,
  onOpenInstanceDetail,
}: DataSyncTaskOperationsProps) {
  const realtime = syncType === "REALTIME";
  const [records, setRecords] = useState<DataSyncTaskRecord[]>([]);
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [actionKey, setActionKey] = useState<string>();
  const { activeByTask, refresh: refreshActiveInstances } = useActiveTaskInstances(syncType, true);

  const loadTasks = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listDataSyncTasks({
        pageNo,
        pageSize: PAGE_SIZE,
        keyword: keyword.trim() || undefined,
        syncType,
        status: "PUBLISHED",
      });
      setRecords(result?.bizData || []);
      setTotal(result?.pagination?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo, syncType]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadTasks(), keyword.trim() ? 250 : 0);
    return () => window.clearTimeout(timer);
  }, [keyword, loadTasks]);

  const runTask = async (record: DataSyncTaskRecord) => {
    if (actionKey) return;
    setActionKey(`${record.id}:run`);
    try {
      const instance = await runDataSyncTask(record.id);
      toast.success(realtime ? "实时同步任务已启动" : "同步任务已启动");
      await refreshActiveInstances();
      onOpenInstanceDetail(instance.id);
    } finally {
      setActionKey(undefined);
    }
  };

  const stopTask = async (record: DataSyncTaskRecord) => {
    const activeInstance = activeByTask.get(record.id);
    if (!activeInstance || actionKey) return;

    setActionKey(`${record.id}:stop`);
    try {
      await cancelDataSyncInstance(activeInstance.id);
      toast.success(realtime ? "实时同步实例已停止" : "同步实例已停止");
      await refreshActiveInstances();
    } finally {
      setActionKey(undefined);
    }
  };

  const columns: TableColumns<DataSyncTaskRecord> = [
    {
      key: "name",
      title: "任务名称",
      minWidth: 220,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-[13px] font-medium text-[#252832]">{record.name}</div>
          <div className="mt-0.5 text-xs text-[#98a2b3]">v{record.definitionVersion}</div>
        </div>
      ),
    },
    {
      key: "runtimeStatus",
      title: "运行状态",
      width: 120,
      render: (_value, record) => {
        const activeInstance = activeByTask.get(record.id);
        if (!activeInstance) return <Badge tone="neutral">空闲</Badge>;
        if (activeInstance.status === "PENDING") return <Badge tone="neutral">等待</Badge>;
        return <Badge tone="info">运行中</Badge>;
      },
    },
    {
      key: "updated",
      title: "更新时间",
      width: 180,
      render: (_value, record) => record.updateTime || "-",
    },
    {
      key: "actions",
      title: "操作",
      width: 170,
      align: "center",
      render: (_value, record) => {
        const activeInstance = activeByTask.get(record.id);
        const actionLoading = activeInstance
          ? actionKey === `${record.id}:stop`
          : actionKey === `${record.id}:run`;

        return (
          <div className="flex items-center justify-center gap-1">
            <Button
              variant="ghost"
              size="small"
              loading={actionLoading}
              disabled={Boolean(actionKey) && !actionLoading}
              className={
                activeInstance
                  ? "px-1 text-xs font-normal text-[#d92d20]"
                  : "px-1 text-xs font-normal text-[var(--yak-color-primary)]"
              }
              onClick={() => {
                if (activeInstance) void stopTask(record);
                else void runTask(record);
              }}
            >
              {activeInstance ? "停止" : realtime ? "启动" : "运行"}
            </Button>
            <span className="h-3 w-px bg-[#e4e7ec]" />
            <Button
              variant="ghost"
              size="small"
              disabled={Boolean(actionKey)}
              className="px-1 text-xs font-normal text-[#667085] hover:text-[var(--yak-color-primary)]"
              onClick={() => onOpenInstances(record.id)}
            >
              实例
            </Button>
          </div>
        );
      },
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
      </div>

      <div className="mt-4 min-h-0 flex-1">
        <Table<DataSyncTaskRecord>
          className="min-h-full"
          columns={columns}
          dataSource={records}
          rowKey="id"
          loading={loading}
          bordered
          size="medium"
          scroll={{ x: 760 }}
          emptyText={realtime ? "暂无已上线实时同步任务" : "暂无已上线离线同步任务"}
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

export default DataSyncTaskOperations;
