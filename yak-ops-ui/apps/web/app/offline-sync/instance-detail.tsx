import { DataSyncInstanceDetailPage } from "@/app/data-sync/instance-runtime";

export function OfflineSyncInstanceDetailPage() {
  return <DataSyncInstanceDetailPage syncType="OFFLINE" basePath="/offline-sync" />;
}

export default OfflineSyncInstanceDetailPage;
