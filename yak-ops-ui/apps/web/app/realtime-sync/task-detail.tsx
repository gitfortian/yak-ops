import { DataSyncTaskDetailPage } from "@/app/data-sync/task-detail";

export function RealtimeSyncTaskDetailPage() {
  return (
    <DataSyncTaskDetailPage
      syncType="REALTIME"
      basePath="/realtime-sync"
      localScroll
    />
  );
}

export default RealtimeSyncTaskDetailPage;
