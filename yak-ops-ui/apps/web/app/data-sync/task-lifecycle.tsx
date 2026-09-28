import { Badge, Button, type BadgeProps } from "@yak-ops/yak-ui";
import { Fragment, useCallback, useEffect, useState } from "react";

import {
  listDataSyncInstances,
  type DataSyncInstanceRecord,
  type DataSyncTaskRecord,
  type DataSyncTaskStatus,
  type DataSyncType,
} from "@/service/data-sync";

const POLL_INTERVAL_MILLIS = 2000;

export const DATA_SYNC_TASK_STATUS_ITEMS: Record<DataSyncTaskStatus | "ALL", string> = {
  ALL: "全部状态",
  PUBLISHED: "已上线",
  UNPUBLISHED: "已下线",
};

export const isPublishedTask = (record: DataSyncTaskRecord) => record.status === "PUBLISHED";

export const taskStatusMeta = (
  status: DataSyncTaskStatus | string,
): { label: string; tone: BadgeProps["tone"] } =>
  status === "PUBLISHED"
    ? { label: "已上线", tone: "success" }
    : { label: "已下线", tone: "neutral" };

export function DataSyncTaskStatusBadge({ status }: { status: DataSyncTaskStatus | string }) {
  const meta = taskStatusMeta(status);
  return <Badge tone={meta.tone}>{meta.label}</Badge>;
}

export function useActiveTaskInstances(syncType: DataSyncType, enabled: boolean) {
  const [activeByTask, setActiveByTask] = useState<Map<string, DataSyncInstanceRecord>>(new Map());

  const refresh = useCallback(async () => {
    if (!enabled) {
      setActiveByTask(new Map<string, DataSyncInstanceRecord>());
      return;
    }

    const [pending, running] = await Promise.all([
      listDataSyncInstances({
        pageNo: 1,
        pageSize: 200,
        syncType,
        status: "PENDING",
      }),
      listDataSyncInstances({
        pageNo: 1,
        pageSize: 200,
        syncType,
        status: "RUNNING",
      }),
    ]);

    const next = new Map<string, DataSyncInstanceRecord>();
    [...(pending?.bizData || []), ...(running?.bizData || [])].forEach((record) => {
      if (!next.has(record.taskId)) next.set(record.taskId, record);
    });
    setActiveByTask(next);
  }, [enabled, syncType]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!enabled || activeByTask.size === 0) return;
    const timer = window.setInterval(() => void refresh(), POLL_INTERVAL_MILLIS);
    return () => window.clearInterval(timer);
  }, [activeByTask.size, enabled, refresh]);

  return { activeByTask, refresh };
}

interface LifecycleAction {
  key: string;
  label: string;
  className: string;
  loading?: boolean;
  onClick: () => void;
}

interface DataSyncTaskLifecycleActionsProps {
  record: DataSyncTaskRecord;
  activeInstance?: DataSyncInstanceRecord;
  runLabel: string;
  actionKey?: string;
  onPublish: (record: DataSyncTaskRecord) => void;
  onUnpublish: (record: DataSyncTaskRecord) => void;
  onRun: (record: DataSyncTaskRecord) => void;
  onStop: (instance: DataSyncInstanceRecord) => void;
  onEdit: (record: DataSyncTaskRecord) => void;
  onInstances: (record: DataSyncTaskRecord) => void;
  onDelete: (record: DataSyncTaskRecord) => void;
}

export function DataSyncTaskLifecycleActions({
  record,
  activeInstance,
  runLabel,
  actionKey,
  onPublish,
  onUnpublish,
  onRun,
  onStop,
  onEdit,
  onInstances,
  onDelete,
}: DataSyncTaskLifecycleActionsProps) {
  const published = isPublishedTask(record);
  const actions: LifecycleAction[] = published
    ? activeInstance
      ? [
          {
            key: "stop",
            label: "停止",
            className: "text-[#d92d20]",
            loading: actionKey === `${record.id}:stop`,
            onClick: () => onStop(activeInstance),
          },
          {
            key: "instances",
            label: "实例",
            className: "text-[#667085] hover:text-[var(--yak-color-primary)]",
            onClick: () => onInstances(record),
          },
        ]
      : [
          {
            key: "run",
            label: runLabel,
            className: "text-[var(--yak-color-primary)]",
            loading: actionKey === `${record.id}:run`,
            onClick: () => onRun(record),
          },
          {
            key: "unpublish",
            label: "下线",
            className: "text-[#667085] hover:text-[var(--yak-color-primary)]",
            loading: actionKey === `${record.id}:unpublish`,
            onClick: () => onUnpublish(record),
          },
          {
            key: "instances",
            label: "实例",
            className: "text-[#667085] hover:text-[var(--yak-color-primary)]",
            onClick: () => onInstances(record),
          },
        ]
    : [
        {
          key: "publish",
          label: "上线",
          className: "text-[var(--yak-color-primary)]",
          loading: actionKey === `${record.id}:publish`,
          onClick: () => onPublish(record),
        },
        {
          key: "edit",
          label: "编辑",
          className: "text-[#667085] hover:text-[var(--yak-color-primary)]",
          onClick: () => onEdit(record),
        },
        {
          key: "instances",
          label: "实例",
          className: "text-[#667085] hover:text-[var(--yak-color-primary)]",
          onClick: () => onInstances(record),
        },
        {
          key: "delete",
          label: "删除",
          className: "text-[#667085] hover:text-[#d92d20]",
          onClick: () => onDelete(record),
        },
      ];

  return (
    <div className="flex items-center justify-center gap-1">
      {actions.map((action, index) => (
        <Fragment key={action.key}>
          {index > 0 ? <span className="h-3 w-px bg-[#e4e7ec]" /> : null}
          <Button
            variant="ghost"
            size="small"
            loading={action.loading}
            disabled={Boolean(actionKey) && !action.loading}
            className={`px-1 text-xs font-normal ${action.className}`}
            onClick={action.onClick}
          >
            {action.label}
          </Button>
        </Fragment>
      ))}
    </div>
  );
}
