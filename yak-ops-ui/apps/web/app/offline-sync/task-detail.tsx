import { DataSyncTaskDetailPage } from "@/app/data-sync/task-detail";

export function OfflineSyncTaskDetailPage() {
  return (
    <DataSyncTaskDetailPage
      syncType="OFFLINE"
      basePath="/offline-sync"
      localScroll
    />
  );
}

export default OfflineSyncTaskDetailPage;
