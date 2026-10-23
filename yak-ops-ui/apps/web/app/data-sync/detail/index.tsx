import { Button, PageHeader, Spinner } from "@yak-ops/yak-ui";
import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ScheduleDialog } from "@/app/schedule/schedule-dialog";
import { useTaskSchedules } from "@/app/schedule/use-task-schedules";
import { canManageTaskDefinition } from "@/service/data-sync/execution";
import { useActiveTaskInstances } from "../task-lifecycle";
import { ExecutionSidebar } from "./execution-sidebar";
import { TaskSummary } from "./task-summary";
import { ExecutionTabs } from "./execution-tabs";
import { useTaskInformation, useDetailScope } from "./use-task-information";
import { useExecutionHistory } from "./use-execution-history";
import { useExecutionActions } from "./use-execution-actions";

export function DataSyncTaskDetailPage() {
  const basePath = "/data-sync";
  const localScroll = true;
  const { id: taskId } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const scope = useDetailScope();
  const { task, taskLoading, dataSources, updatedBy } = useTaskInformation(taskId, scope);
  const realtime = task?.syncType === "REALTIME";
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [historyScheduleId, setHistoryScheduleId] = useState<string>();
  const schedules = useTaskSchedules(!realtime && taskId ? [taskId] : []);
  const hasEnabledSchedule = Boolean(taskId && schedules.records.get(taskId)?.enabled);
  const activeTaskIds = useMemo(() => (taskId ? [taskId] : []), [taskId]);
  const {
    activeByTask,
    ready: instancesReady,
    refresh: refreshActiveInstances,
  } = useActiveTaskInstances(Boolean(taskId), activeTaskIds);
  const activeInstance = taskId ? activeByTask.get(taskId) : undefined;
  const canStart = canManageTaskDefinition(instancesReady, activeInstance);
  const {
    executionId,
    activeTab,
    instances,
    instancesLoading,
    status,
    pageNo,
    total,
    selectedInstance,
    selectedLoading,
    autoRefresh,
    setAutoRefresh,
    refreshing,
    loadInstances,
    refreshExecution,
    selectInstance,
    changeTab,
    changePage,
    changeStatus,
    selectSubmitted,
    refreshAfterCancel,
    listError,
  } = useExecutionHistory(scope, taskId, activeInstance, hasEnabledSchedule);
  const { run, cancel, copyTaskId, running, canceling } = useExecutionActions({
    taskId,
    task,
    canStart,
    selectedInstance,
    refreshActiveInstances,
    refreshAfterCancel,
    selectSubmitted,
  });

  if (taskLoading || !task) {
    return (
      <div className="flex h-full items-center justify-center bg-white">
        <Spinner size="large" label="加载任务详情" />
      </div>
    );
  }

  return (
    <div
      className={
        localScroll
          ? "flex h-full min-h-0 flex-col overflow-hidden bg-white text-[#242731] md:flex-row"
          : "flex min-h-full flex-col bg-white text-[#242731] md:flex-row"
      }
    >
      <ExecutionSidebar
        realtime={realtime}
        status={status}
        onStatusChange={changeStatus}
        instances={instances}
        instancesLoading={instancesLoading}
        listError={listError}
        selectedId={executionId}
        onSelect={selectInstance}
        total={total}
        pageNo={pageNo}
        onPageChange={changePage}
        onRefresh={() => void loadInstances()}
      />
      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <PageHeader
          title={task.name}
          bordered
          className="shrink-0 bg-white px-6 max-md:px-4"
          extra={
            <>
              {!realtime ? (
                <Button
                  size="small"
                  onClick={() => {
                    setHistoryScheduleId(undefined);
                    setScheduleOpen(true);
                  }}
                >
                  调度
                </Button>
              ) : null}
              {task.status === "PUBLISHED" ? (
                !activeInstance ? (
                  <Button
                    size="small"
                    loading={running}
                    disabled={!canStart}
                    onClick={() => void run()}
                  >
                    {realtime ? "启动" : "运行"}
                  </Button>
                ) : null
              ) : (
                <Button
                  size="small"
                  disabled={!canStart}
                  onClick={() => navigate(basePath + "/" + task.id + "/edit")}
                >
                  编辑
                </Button>
              )}
              <Button size="small" onClick={() => navigate(basePath)}>
                返回任务列表
              </Button>
            </>
          }
        />

        <div className="min-h-0 flex-1 overflow-y-auto px-6 pb-5 pt-5 max-md:px-4">
          <div className="space-y-8">
            <TaskSummary
              task={task}
              updatedBy={updatedBy}
              dataSources={dataSources}
              onCopyTaskId={() => void copyTaskId()}
            />
            <ExecutionTabs
              activeTab={activeTab}
              onChangeTab={changeTab}
              selectedInstance={selectedInstance}
              selectedLoading={selectedLoading}
              instanceCount={instances.length}
              autoRefresh={autoRefresh}
              onAutoRefreshChange={setAutoRefresh}
              refreshing={refreshing}
              onRefresh={() => void refreshExecution()}
              canceling={canceling}
              onCancel={() => void cancel()}
              onViewScheduleHistory={(id) => {
                setHistoryScheduleId(id);
                setScheduleOpen(true);
              }}
            />
          </div>
        </div>
      </div>
      {!realtime ? (
        <ScheduleDialog
          targetId={task.id}
          published={task.status === "PUBLISHED"}
          open={scheduleOpen}
          historyScheduleId={historyScheduleId}
          onClose={() => setScheduleOpen(false)}
          onChanged={schedules.refresh}
          onViewInstance={(id) => {
            selectSubmitted(id);
          }}
        />
      ) : null}
    </div>
  );
}
