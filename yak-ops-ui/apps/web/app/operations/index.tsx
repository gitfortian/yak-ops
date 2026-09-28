import { PageHeader } from "@yak-ops/yak-ui";

import {
  DataSyncInstanceDetailPage,
  DataSyncInstances,
} from "@/app/data-sync/instance-runtime";
import type { DataSyncType } from "@/service/data-sync";

interface TaskOperationsPageProps {
  syncType: DataSyncType;
  title: string;
  description: string;
  basePath: string;
}

function TaskOperationsPage({
  syncType,
  title,
  description,
  basePath,
}: TaskOperationsPageProps) {
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
          <DataSyncInstances syncType={syncType} basePath={basePath} />
        </div>
      </div>
    </div>
  );
}

export function OfflineTaskOperationsPage() {
  return (
    <TaskOperationsPage
      syncType="OFFLINE"
      title="离线任务"
      description="查看离线同步任务的运行实例与执行状态"
      basePath="/operations/offline-tasks"
    />
  );
}

export function RealtimeTaskOperationsPage() {
  return (
    <TaskOperationsPage
      syncType="REALTIME"
      title="实时任务"
      description="查看实时同步任务的运行实例与运行状态"
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
      listPath="/operations/realtime-tasks"
    />
  );
}
