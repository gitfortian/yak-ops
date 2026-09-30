import { DataSyncInstanceDetailPage } from "@/app/data-sync/instance-runtime";

import { OfflineOperationsDashboard } from "./offline-dashboard";
import { RealtimeOperationsDashboard } from "./realtime-dashboard";

export function OfflineTaskOperationsPage() {
  return <OfflineOperationsDashboard />;
}

export function RealtimeTaskOperationsPage() {
  return <RealtimeOperationsDashboard />;
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
