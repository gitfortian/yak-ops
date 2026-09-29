import {
  Button,
  Field,
  FieldLabel,
  Input,
  Modal,
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
import { ArrowRight, Plus } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { listDataSources, type DataSourceRecord } from "@/service/datasource";
import {
  deleteDataSyncTask,
  listDataSyncTasks,
  publishDataSyncTask,
  unpublishDataSyncTask,
  type DataSyncTaskRecord,
  type DataSyncTaskStatus,
} from "@/service/data-sync";

import { DataSyncSearchableSelect } from "@/app/data-sync/searchable-select";
import {
  DATA_SYNC_TASK_STATUS_ITEMS,
  DataSyncTaskLifecycleActions,
  DataSyncTaskStatusBadge,
  useActiveTaskInstances,
} from "@/app/data-sync/task-lifecycle";

const PAGE_SIZE = 20;

interface CreateDraft {
  name: string;
  sourceDataSourceId: string;
  targetDataSourceId: string;
}

const emptyDraft = (): CreateDraft => ({
  name: "",
  sourceDataSourceId: "",
  targetDataSourceId: "",
});

const pathText = (database?: string, schema?: string, table?: string) =>
  [database, schema, table].filter(Boolean).join(".") || "-";

export function OfflineSyncPage() {
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    if (params.get("tab") !== "instances") return;

    const nextParams = new URLSearchParams({ tab: "instances" });
    const taskId = params.get("taskId");
    if (taskId) nextParams.set("taskId", taskId);
    navigate(`/operations/offline-tasks?${nextParams.toString()}`, { replace: true });
  }, [location.search, navigate]);

  const [records, setRecords] = useState<DataSyncTaskRecord[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [dataSourcesLoading, setDataSourcesLoading] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [taskStatus, setTaskStatus] = useState<DataSyncTaskStatus | "ALL">("ALL");
  const [actionKey, setActionKey] = useState<string>();
  const [createOpen, setCreateOpen] = useState(false);
  const [draft, setDraft] = useState<CreateDraft>(emptyDraft());
  const [pendingDelete, setPendingDelete] = useState<DataSyncTaskRecord>();
  const [deleting, setDeleting] = useState(false);

  const { activeByTask, refresh: refreshActiveInstances } = useActiveTaskInstances("OFFLINE", true);

  const dataSourceMap = useMemo(
    () => new Map(dataSources.flatMap((item) => (item.id ? [[item.id, item] as const] : []))),
    [dataSources],
  );
  const dataSourceOptions = useMemo(
    () =>
      dataSources.flatMap((item) =>
        item.id
          ? [
              {
                value: item.id,
                label: item.name || item.id,
                searchText: [item.dbType, item.database, item.schema].filter(Boolean).join(" "),
              },
            ]
          : [],
      ),
    [dataSources],
  );

  const loadDataSources = useCallback(async () => {
    setDataSourcesLoading(true);
    try {
      const result = await listDataSources({ pageNo: 1, pageSize: 200 });
      setDataSources(result?.bizData || []);
    } finally {
      setDataSourcesLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDataSources();
  }, [loadDataSources]);

  const loadTasks = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listDataSyncTasks({
        pageNo,
        pageSize: PAGE_SIZE,
        keyword: keyword.trim() || undefined,
        syncType: "OFFLINE",
        status: taskStatus === "ALL" ? undefined : taskStatus,
      });
      setRecords(result?.bizData || []);
      setTotal(result?.pagination?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo, taskStatus]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadTasks(), keyword.trim() ? 250 : 0);
    return () => window.clearTimeout(timer);
  }, [keyword, loadTasks]);

  const publishTask = async (record: DataSyncTaskRecord) => {
    if (actionKey) return;
    setActionKey(`${record.id}:publish`);
    try {
      await publishDataSyncTask(record.id);
      toast.success("同步任务已上线");
      await Promise.all([loadTasks(), refreshActiveInstances()]);
    } finally {
      setActionKey(undefined);
    }
  };

  const unpublishTask = async (record: DataSyncTaskRecord) => {
    if (actionKey) return;
    setActionKey(`${record.id}:unpublish`);
    try {
      await unpublishDataSyncTask(record.id);
      toast.success("同步任务已下线");
      await Promise.all([loadTasks(), refreshActiveInstances()]);
    } finally {
      setActionKey(undefined);
    }
  };

  const columns: TableColumns<DataSyncTaskRecord> = [
    {
      key: "name",
      title: "任务名称",
      minWidth: 200,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-[13px] font-medium text-[#252832]">{record.name}</div>
          <div className="mt-0.5 text-xs text-[#98a2b3]">v{record.definitionVersion}</div>
        </div>
      ),
    },
    {
      key: "route",
      title: "同步链路",
      minWidth: 430,
      render: (_value, record) => {
        const source = dataSourceMap.get(record.sourceDataSourceId);
        const target = dataSourceMap.get(record.targetDataSourceId);
        return (
          <div className="flex min-w-0 items-center gap-3 text-[13px]">
            <div className="min-w-0 flex-1">
              <div className="truncate font-medium text-[#344054]">
                {source?.name || "未知数据源"}
              </div>
              <div className="truncate text-xs text-[#667085]">
                {pathText(record.sourceDatabase, record.sourceSchema, record.sourceTable)}
              </div>
            </div>
            <ArrowRight size={15} className="shrink-0 text-[#98a2b3]" />
            <div className="min-w-0 flex-1">
              <div className="truncate font-medium text-[#344054]">
                {target?.name || "未知数据源"}
              </div>
              <div className="truncate text-xs text-[#667085]">
                {pathText(record.targetDatabase, record.targetSchema, record.targetTable)}
              </div>
            </div>
          </div>
        );
      },
    },
    {
      key: "status",
      title: "状态",
      width: 100,
      render: (_value, record) => <DataSyncTaskStatusBadge status={record.status} />,
    },
    {
      key: "updated",
      title: "更新时间",
      width: 170,
      render: (_value, record) => record.updateTime || "-",
    },
    {
      key: "actions",
      title: "操作",
      width: 260,
      align: "center",
      render: (_value, record) => (
        <DataSyncTaskLifecycleActions
          record={record}
          activeInstance={activeByTask.get(record.id)}
          actionKey={actionKey}
          onPublish={(value) => void publishTask(value)}
          onUnpublish={(value) => void unpublishTask(value)}
          onEdit={(value) => navigate(`/offline-sync/${value.id}`)}
          onInstances={(value) =>
            navigate(`/operations/offline-tasks?tab=instances&taskId=${value.id}`)
          }
          onDelete={setPendingDelete}
        />
      ),
    },
  ];

  const confirmDelete = async () => {
    if (!pendingDelete || deleting) return;
    setDeleting(true);
    try {
      await deleteDataSyncTask(pendingDelete.id);
      toast.success("同步任务已删除");
      setPendingDelete(undefined);
      await loadTasks();
    } finally {
      setDeleting(false);
    }
  };

  return (
    <>
      <div className="flex min-h-full flex-col bg-[#f6f6f6] text-[#242731]">
        <PageHeader title="离线同步" bordered className="bg-white px-6 max-md:px-4" />

        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <div className="flex shrink-0 flex-wrap items-center gap-2">
              <Button
                size="small"
                variant="primary"
                onClick={() => {
                  setDraft(emptyDraft());
                  setCreateOpen(true);
                }}
              >
                <Plus size={14} />
                新建同步任务
              </Button>
              <div className="w-[300px]">
                <Input
                  size="small"
                  variant="outlined"
                  value={keyword}
                  placeholder="搜索任务名称或表名"
                  onChange={(event) => {
                    setKeyword(event.target.value);
                    setPageNo(1);
                  }}
                />
              </div>
              <div className="w-[150px]">
                <Select
                  size="small"
                  items={DATA_SYNC_TASK_STATUS_ITEMS}
                  value={taskStatus}
                  onValueChange={(value) => {
                    setTaskStatus(String(value || "ALL") as DataSyncTaskStatus | "ALL");
                    setPageNo(1);
                  }}
                >
                  <SelectTrigger variant="outlined">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">
                      <SelectItemText>全部状态</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                    <SelectItem value="PUBLISHED">
                      <SelectItemText>已上线</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                    <SelectItem value="UNPUBLISHED">
                      <SelectItemText>已下线</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  </SelectContent>
                </Select>
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
                scroll={{ x: 1120 }}
                emptyText="还没有离线同步任务"
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
        </div>
      </div>

      <Modal
        open={createOpen}
        centered
        width={560}
        title="新建离线同步任务"
        onClose={() => setCreateOpen(false)}
        footer={
          <>
            <Button size="small" onClick={() => setCreateOpen(false)}>
              取消
            </Button>
            <Button
              size="small"
              variant="primary"
              disabled={
                !draft.name.trim() || !draft.sourceDataSourceId || !draft.targetDataSourceId
              }
              onClick={() => {
                setCreateOpen(false);
                navigate("/offline-sync/new", { state: { draft } });
              }}
            >
              下一步
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          <Field className="grid grid-cols-[110px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel required>名称</FieldLabel>
            <Input
              size="small"
              variant="outlined"
              value={draft.name}
              maxLength={128}
              placeholder="请输入同步任务名称"
              onChange={(event) => setDraft((value) => ({ ...value, name: event.target.value }))}
            />
          </Field>

          <Field className="grid grid-cols-[110px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel required>来源数据源</FieldLabel>
            <DataSyncSearchableSelect
              value={draft.sourceDataSourceId || null}
              options={dataSourceOptions}
              placeholder="请选择来源数据源"
              searchPlaceholder="搜索来源数据源"
              emptyText="暂无数据源"
              refreshing={dataSourcesLoading}
              onRefresh={loadDataSources}
              footer={
                <Button
                  size="small"
                  variant="ghost"
                  className="px-1 text-xs font-normal text-[var(--yak-color-primary)]"
                  onClick={() => navigate("/data-source?create=1")}
                >
                  <Plus size={14} />
                  新增数据源
                </Button>
              }
              onValueChange={(value) =>
                setDraft((current) => ({ ...current, sourceDataSourceId: value }))
              }
            />
          </Field>

          <Field className="grid grid-cols-[110px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel required>目标数据源</FieldLabel>
            <DataSyncSearchableSelect
              value={draft.targetDataSourceId || null}
              options={dataSourceOptions}
              placeholder="请选择目标数据源"
              searchPlaceholder="搜索目标数据源"
              emptyText="暂无数据源"
              refreshing={dataSourcesLoading}
              onRefresh={loadDataSources}
              footer={
                <Button
                  size="small"
                  variant="ghost"
                  className="px-1 text-xs font-normal text-[var(--yak-color-primary)]"
                  onClick={() => navigate("/data-source?create=1")}
                >
                  <Plus size={14} />
                  新增数据源
                </Button>
              }
              onValueChange={(value) =>
                setDraft((current) => ({ ...current, targetDataSourceId: value }))
              }
            />
          </Field>
        </div>
      </Modal>

      <Modal
        open={Boolean(pendingDelete)}
        centered
        width={420}
        title="删除同步任务"
        onClose={() => {
          if (!deleting) setPendingDelete(undefined);
        }}
        footer={
          <>
            <Button size="small" disabled={deleting} onClick={() => setPendingDelete(undefined)}>
              取消
            </Button>
            <Button
              size="small"
              variant="danger"
              loading={deleting}
              onClick={() => void confirmDelete()}
            >
              删除
            </Button>
          </>
        }
      >
        <div className="text-sm leading-6 text-[#667085]">
          确定删除任务“{pendingDelete?.name}”吗？历史任务实例不会在这里删除。
        </div>
      </Modal>
    </>
  );
}

export { OfflineSyncEditorPage } from "./editor";
export { OfflineSyncInstanceDetailPage } from "./instance-detail";
export default OfflineSyncPage;
