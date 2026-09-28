import { DataSyncInstanceDetailPage } from "@/app/data-sync/instance-runtime";

export function RealtimeSyncInstanceDetailPage() {
  return <DataSyncInstanceDetailPage syncType="REALTIME" basePath="/realtime-sync" />;
}

export default RealtimeSyncInstanceDetailPage;
