import { Navigate, useParams } from "react-router-dom";

export function RealtimeSyncInstanceDetailPage() {
  const { id } = useParams<{ id: string }>();
  return (
    <Navigate
      replace
      to={id ? `/operations/realtime-tasks/instances/${id}` : "/operations/realtime-tasks"}
    />
  );
}

export default RealtimeSyncInstanceDetailPage;
