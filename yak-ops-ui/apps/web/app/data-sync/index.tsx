import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { Button, Modal, PageHeader } from "@yak-ops/yak-ui";

import { CreateSyncTaskDialog } from "./create-dialog";
import { ScheduleDialog } from "@/app/schedule/schedule-dialog";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { canCancelTaskInstance, canManageTaskDefinition } from "@/service/data-sync/execution";
import type { DataSyncTaskRecord } from "@/service/data-sync";

import { TaskFilters } from "./list/task-filters";
import { TaskTable } from "./list/task-table";
import { useTaskListFilters } from "./list/use-list-filters";
import {
  useTaskPage,
  useListDataSources,
  useListUpdateUsers,
  useListSchedules,
  useListInstances,
} from "./list/use-list-queries";
import { useTaskListActions, type TaskCommand } from "./list/use-task-actions";
import { listQueryKeys } from "./list/query-keys";

export function DataSyncPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const scope =
    currentUser?.id && currentWorkspace?.id ? currentUser.id + ":" + currentWorkspace.id : "";

  const filters = useTaskListFilters();
  const page = useTaskPage(scope, filters);
  const sources = useListDataSources(scope);
  const users = useListUpdateUsers(scope, page.records);
  const schedules = useListSchedules(scope, page.records);
  const instances = useListInstances(scope, page.records, page.safeToAct);
  const commands = useTaskListActions(scope);

  const [createOpen, setCreateOpen] = useState(
    new URLSearchParams(location.search).get("create") === "1",
  );
  const [scheduleTask, setScheduleTask] = useState<DataSyncTaskRecord>();
  const [pendingDelete, setPendingDelete] = useState<DataSyncTaskRecord>();

  const canManage = (task: DataSyncTaskRecord) =>
    page.safeToAct &&
    instances.ready &&
    canManageTaskDefinition(instances.ready, instances.activeByTask.get(task.id));

  const command = (value: TaskCommand) => {
    if (!scope || commands.pending || !page.safeToAct) return;
    if (value.kind === "schedule") {
      if (!schedules.schedules.has(value.task.id)) return;
    } else if (value.kind === "stop") {
      if (!instances.ready || !canCancelTaskInstance(value.instance.status)) return;
    } else if (!canManage(value.task)) {
      return;
    }
    commands.execute(value, {
      onSuccess: () => {
        if (value.kind === "delete") setPendingDelete(undefined);
      },
    });
  };

  const refreshSchedules = () =>
    void queryClient.invalidateQueries({ queryKey: listQueryKeys.schedules(scope) });

  return (
    <>
      <div className="flex min-h-full flex-col bg-[#f6f6f6] text-[#242731]">
        <PageHeader title="数据同步" bordered className="bg-white px-6 max-md:px-4" />
        <div className="flex min-h-0 min-w-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 min-w-0 flex-1 flex-col bg-white p-4">
            <TaskFilters
              filters={filters}
              keyword={filters.keywordInput}
              onKeywordChange={filters.setKeywordInput}
              onFilterChange={filters.update}
              onCreate={() => setCreateOpen(true)}
            />
            {page.error ? (
              <div className="mt-3 flex items-center gap-2 text-sm text-[#d92d20]">
                任务列表加载失败
                <Button size="small" onClick={page.retry}>
                  重试
                </Button>
              </div>
            ) : null}
            <div className="mt-4 min-h-0 min-w-0 flex-1">
              <TaskTable
                records={page.records}
                pageNo={filters.pageNo}
                total={page.total}
                loading={page.loading}
                data={{
                  dataSourceMap: sources.dataSourceMap,
                  dataSourcesLoading: sources.loading,
                  updateUserMap: users.users,
                  updateUsersLoading: users.loading,
                  schedules: {
                    records: schedules.schedules,
                    loading: schedules.loading,
                    error: schedules.error,
                  },
                  latestByTask: instances.latestByTask,
                  activeByTask: instances.activeByTask,
                  instancesLoading: instances.loading,
                  instancesReady: instances.ready,
                  actionKey: commands.actionKey,
                }}
                actions={{
                  configureSchedule: setScheduleTask,
                  toggleSchedule: (task, enabled) => {
                    const schedule = schedules.schedules.get(task.id);
                    if (schedule) command({ kind: "schedule", task, schedule, enabled });
                  },
                  command,
                  delete: (task) => {
                    if (canManage(task)) setPendingDelete(task);
                  },
                  page: (pageNo) => filters.update({ pageNo }),
                }}
              />
            </div>
          </div>
        </div>
      </div>
      {scheduleTask ? (
        <ScheduleDialog
          targetId={scheduleTask.id}
          published={
            (page.records.find((task) => task.id === scheduleTask.id) || scheduleTask).status ===
            "PUBLISHED"
          }
          open
          onClose={() => setScheduleTask(undefined)}
          onChanged={refreshSchedules}
          onViewInstance={(id) =>
            navigate(
              "/data-sync/" + scheduleTask.id + "/detail?executionId=" + encodeURIComponent(id),
            )
          }
        />
      ) : null}
      {createOpen ? (
        <CreateSyncTaskDialog
          onClose={() => {
            setCreateOpen(false);
            const query = new URLSearchParams(location.search);
            if (query.has("create")) {
              query.delete("create");
              navigate({ pathname: "/data-sync", search: query.toString() }, { replace: true });
            }
          }}
        />
      ) : null}
      <Modal
        open={Boolean(pendingDelete)}
        centered
        width={420}
        title="删除同步任务"
        onClose={() => {
          if (!commands.pending) setPendingDelete(undefined);
        }}
        footer={
          <>
            <Button
              size="small"
              disabled={commands.pending}
              onClick={() => setPendingDelete(undefined)}
            >
              取消
            </Button>
            <Button
              size="small"
              variant="danger"
              loading={commands.actionKey === (pendingDelete?.id || "") + ":delete"}
              onClick={() => {
                if (pendingDelete && canManage(pendingDelete)) {
                  command({ kind: "delete", task: pendingDelete });
                }
              }}
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

export default DataSyncPage;
