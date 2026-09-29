import { Navigate, useParams } from "react-router-dom";

export function OfflineSyncInstanceDetailPage() {
  const { id } = useParams<{ id: string }>();
  return (
    <Navigate
      replace
      to={id ? `/operations/offline-tasks/instances/${id}` : "/operations/offline-tasks"}
    />
  );
}

export default OfflineSyncInstanceDetailPage;
