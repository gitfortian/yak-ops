import { Navigate, Outlet, Route, Routes, useLocation, useNavigate } from "react-router-dom";

import { DataSourcePage } from "@/app/datasource/list/page";
import LoginPage from "@/app/login";
import { UserManagementPage } from "@/app/management/users/page";
import { WorkspaceManagementPage } from "@/app/management/workspaces/page";
import { DataSyncPage } from "@/app/data-sync/list/page";
import DataMapOverviewPage from "@/app/data-map/overview/page";
import DataMapCatalogPage from "@/app/data-map/catalog/page";
import DataMapTableDetailPage from "@/app/data-map/tables/detail/page";
import DataMapCrawlRunsPage from "@/app/data-map/crawls/page";
import DataMapTasksPage from "@/app/data-map/tasks/page";
import DataMapTaskEditorPage from "@/app/data-map/tasks/editor/page";
import DataMapPersonalPage from "@/app/data-map/personal/page";
import WarehousePlanningPage from "@/app/data-modeling/warehouse-planning";
import DataStandardsPage from "@/app/data-modeling/data-standards";
import MetricsPage from "@/app/data-modeling/metrics";
import DimensionModelingPage from "@/app/data-modeling/dimension-modeling";
import ReverseModelingPage from "@/app/data-modeling/dimension-modeling/reverse";
import LogicalModelEditorPage from "@/app/data-modeling/logical-models/editor";
import { DataSyncTaskEditorPage } from "@/app/data-sync/editor/page";
import { DataSyncTaskDetailPage } from "@/app/data-sync/detail";
import { DataSyncInstanceRedirect } from "./data-sync-instance-redirect";
import {
  OfflineTaskOperationsPage,
  RealtimeTaskOperationsPage,
} from "@/app/operations/dashboard/page";
import { useAuth } from "@/hooks/use-auth";

import AppLayout from "../layout/AppLayout";
import {
  DATA_GOVERNANCE_NAVIGATION,
  DATA_GOVERNANCE_PRODUCT_LABEL,
  DATA_INTEGRATION_NAVIGATION,
  DATA_INTEGRATION_PRODUCT_LABEL,
  DATA_MODELING_NAVIGATION,
  DATA_MODELING_PRODUCT_LABEL,
  MANAGEMENT_NAVIGATION,
  MANAGEMENT_PRODUCT_LABEL,
  OPERATIONS_NAVIGATION,
  OPERATIONS_PRODUCT_LABEL,
} from "../layout/navigation";
import AppLoading from "./AppLoading";
import { DEFAULT_AUTHENTICATED_PATH, resolveReturnTo } from "./authenticated-path";

function ProtectedRoute() {
  const { currentUser, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <AppLoading />;
  }

  if (!currentUser) {
    const returnTo = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate replace to={`/login?returnTo=${encodeURIComponent(returnTo)}`} />;
  }

  return <Outlet />;
}

function LoginRoute() {
  const { currentUser, loading, refreshCurrentUser } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  if (loading) {
    return <AppLoading />;
  }

  if (currentUser) {
    return <Navigate replace to={DEFAULT_AUTHENTICATED_PATH} />;
  }

  const handleAuthenticated = async () => {
    await refreshCurrentUser();
    const requested = new URLSearchParams(location.search).get("returnTo");
    navigate(resolveReturnTo(requested, window.location.origin), { replace: true });
  };

  return <LoginPage onAuthenticated={handleAuthenticated} />;
}

export default function AppRouter() {
  return (
    <Routes>
      <Route path="/login" element={<LoginRoute />} />

      <Route element={<ProtectedRoute />}>
        <Route
          element={
            <AppLayout
              productLabel={DATA_MODELING_PRODUCT_LABEL}
              productPath="/data-modeling/warehouse-planning"
              navigation={DATA_MODELING_NAVIGATION}
              workspaceScoped
            />
          }
        >
          <Route path="/data-modeling/warehouse-planning" element={<WarehousePlanningPage />} />
          <Route path="/data-modeling/data-standards" element={<DataStandardsPage />} />
          <Route path="/data-modeling/metrics" element={<MetricsPage />} />
          <Route path="/data-modeling/dimension-modeling" element={<DimensionModelingPage />} />
          <Route
            path="/data-modeling/dimension-modeling/reverse"
            element={<ReverseModelingPage />}
          />
          {/* The two list pages merged into the tree; stale links land on it instead of a blank route. */}
          <Route
            path="/data-modeling/dimensions"
            element={<Navigate replace to="/data-modeling/dimension-modeling" />}
          />
          <Route
            path="/data-modeling/logical-models"
            element={<Navigate replace to="/data-modeling/dimension-modeling" />}
          />
          <Route path="/data-modeling/logical-models/new" element={<LogicalModelEditorPage />} />
          <Route path="/data-modeling/logical-models/:id" element={<LogicalModelEditorPage />} />
        </Route>
        <Route
          element={
            <AppLayout
              productLabel={DATA_INTEGRATION_PRODUCT_LABEL}
              productPath="/data-source"
              navigation={DATA_INTEGRATION_NAVIGATION}
              workspaceScoped
            />
          }
        >
          <Route path="/data-source" element={<DataSourcePage />} />
          <Route path="/data-sync" element={<DataSyncPage />} />
          <Route path="/data-sync/new" element={<DataSyncTaskEditorPage />} />
          <Route path="/data-sync/:id/edit" element={<DataSyncTaskEditorPage />} />
          <Route path="/data-sync/:id/detail" element={<DataSyncTaskDetailPage />} />
        </Route>

        <Route
          element={
            <AppLayout
              productLabel={DATA_GOVERNANCE_PRODUCT_LABEL}
              productPath="/data-governance/data-map/overview"
              navigation={DATA_GOVERNANCE_NAVIGATION}
              workspaceScoped
            />
          }
        >
          <Route
            path="/data-governance/data-map"
            element={<Navigate replace to="/data-governance/data-map/overview" />}
          />
          <Route path="/data-governance/data-map/overview" element={<DataMapOverviewPage />} />
          <Route path="/data-governance/data-map/catalog" element={<DataMapCatalogPage />} />
          <Route
            path="/data-governance/data-map/tables/:tableId"
            element={<DataMapTableDetailPage />}
          />
          <Route path="/data-governance/data-map/tasks" element={<DataMapTasksPage />} />
          <Route path="/data-governance/data-map/tasks/new" element={<DataMapTaskEditorPage />} />
          <Route
            path="/data-governance/data-map/tasks/:taskId/edit"
            element={<DataMapTaskEditorPage />}
          />
          <Route path="/data-governance/data-map/crawls" element={<DataMapCrawlRunsPage />} />
          <Route path="/data-governance/data-map/personal" element={<DataMapPersonalPage />} />
        </Route>

        <Route
          element={
            <AppLayout
              productLabel={OPERATIONS_PRODUCT_LABEL}
              productPath="/operations/offline-tasks"
              navigation={OPERATIONS_NAVIGATION}
              workspaceScoped
            />
          }
        >
          <Route path="/operations" element={<Navigate replace to="/operations/offline-tasks" />} />
          <Route path="/operations/offline-tasks" element={<OfflineTaskOperationsPage />} />
          <Route
            path="/operations/offline-tasks/instances/:id"
            element={<DataSyncInstanceRedirect />}
          />
          <Route path="/operations/realtime-tasks" element={<RealtimeTaskOperationsPage />} />
          <Route
            path="/operations/realtime-tasks/instances/:id"
            element={<DataSyncInstanceRedirect />}
          />
        </Route>

        <Route
          element={
            <AppLayout
              productLabel={MANAGEMENT_PRODUCT_LABEL}
              productPath="/management/users"
              navigation={MANAGEMENT_NAVIGATION}
            />
          }
        >
          <Route path="/management" element={<Navigate replace to="/management/users" />} />
          <Route path="/management/users" element={<UserManagementPage />} />
          <Route path="/management/workspaces" element={<WorkspaceManagementPage />} />
        </Route>

        <Route path="/" element={<Navigate replace to={DEFAULT_AUTHENTICATED_PATH} />} />
        <Route path="*" element={<Navigate replace to={DEFAULT_AUTHENTICATED_PATH} />} />
      </Route>
    </Routes>
  );
}
