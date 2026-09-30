import { Button, PageHeader, Tabs, TabsList, TabsPanel, TabsTab } from "@yak-ops/yak-ui";
import { useNavigate, useSearchParams } from "react-router-dom";

import { DataSyncInstanceDetailPage, DataSyncInstances } from "@/app/data-sync/instance-runtime";
import { DataSyncTaskOperations } from "@/app/data-sync/task-operations";
import type { DataSyncType } from "@/service/data-sync";

import { OfflineOperationsDashboard } from "./offline-dashboard";

interface TaskOperationsPageProps {
  syncType: DataSyncType;
  title: string;
  description: string;
  basePath: string;
}

function TaskOperationsPage({ syncType, title, description, basePath }: TaskOperationsPageProps) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get("tab") === "instances" ? "instances" : "tasks";
  const taskId = searchParams.get("taskId") || undefined;

  return (
    <div className="flex min-h-full flex-col bg-[#f6f6f6] text-[#242731]">
      <PageHeader
        title={title}
        description={description}
        bordered
        className="bg-white px-6 max-md:px-4"
      />

      <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
        <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
          <Tabs
            value={activeTab}
            onValueChange={(value) => {
              if (value === "instances") setSearchParams({ tab: "instances" });
              else setSearchParams({});
            }}
            className="flex min-h-0 flex-1 flex-col"
          >
            <TabsList>
              <TabsTab value="tasks">任务</TabsTab>
              <TabsTab value="instances">运行实例</TabsTab>
            </TabsList>

            <TabsPanel value="tasks" className="flex min-h-0 flex-1 flex-col pt-4">
              <DataSyncTaskOperations
                syncType={syncType}
                onOpenInstances={(selectedTaskId) =>
                  setSearchParams({ tab: "instances", taskId: selectedTaskId })
                }
                onOpenInstanceDetail={(instanceId) =>
                  navigate(`${basePath}/instances/${instanceId}`)
                }
              />
            </TabsPanel>

            <TabsPanel value="instances" className="flex min-h-0 flex-1 flex-col pt-4">
              {taskId ? (
                <div className="mb-3 flex items-center gap-2 text-xs text-[#667085]">
                  <span>已按任务筛选：{taskId}</span>
                  <Button
                    size="small"
                    variant="ghost"
                    className="px-1 text-xs font-normal text-[var(--yak-color-primary)]"
                    onClick={() => setSearchParams({ tab: "instances" })}
                  >
                    清除
                  </Button>
                </div>
              ) : null}
              <DataSyncInstances syncType={syncType} basePath={basePath} taskId={taskId} />
            </TabsPanel>
          </Tabs>
        </div>
      </div>
    </div>
  );
}

export function OfflineTaskOperationsPage() {
  return <OfflineOperationsDashboard />;
}

export function RealtimeTaskOperationsPage() {
  return (
    <TaskOperationsPage
      syncType="REALTIME"
      title="实时任务"
      description="启动或停止实时同步任务，并查看运行实例与状态"
      basePath="/operations/realtime-tasks"
    />
  );
}

export function OfflineTaskOperationsInstanceDetailPage() {
  return (
    <DataSyncInstanceDetailPage
      syncType="OFFLINE"
      basePath="/operations/offline-tasks"
      listPath="/operations/offline-tasks"
    />
  );
}

export function RealtimeTaskOperationsInstanceDetailPage() {
  return (
    <DataSyncInstanceDetailPage
      syncType="REALTIME"
      basePath="/operations/realtime-tasks"
      listPath="/operations/realtime-tasks?tab=instances"
    />
  );
}
