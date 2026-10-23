import { CollapseSection } from "@yak-ops/yak-ui";
import { useNavigate } from "react-router-dom";
import type { DataSyncDraft } from "@/service/data-sync";
import type { DataSourceRecord } from "@/service/datasource";
import { DataSourceEndpointCard } from "./data-source-endpoint-card";
import type { EditorForm } from "./types";

interface Props {
  id?: string;
  draft: DataSyncDraft;
  form: EditorForm;
  sourceDataSources: DataSourceRecord[];
  targetDataSources: DataSourceRecord[];
  dataSourcesLoading: boolean;
  loadDataSources: () => Promise<void>;
  requestConnectionChange: (side: "source" | "target", value: string) => void;
}
export function DataSourcesSection({
  id,
  draft,
  form,
  sourceDataSources,
  targetDataSources,
  dataSourcesLoading,
  loadDataSources,
  requestConnectionChange,
}: Props) {
  const navigate = useNavigate();
  return (
    <CollapseSection id="datasource" title="数据源">
      <div className="grid grid-cols-2 gap-3 max-lg:grid-cols-1">
        <DataSourceEndpointCard
          title="来源"
          dataSourceType={draft.sourceType}
          dataSources={sourceDataSources}
          dataSourceId={form.sourceDataSourceId}
          refreshing={dataSourcesLoading}
          onRefresh={loadDataSources}
          onCreateDataSource={() =>
            navigate(
              "/data-source?create=1&returnTo=" +
                encodeURIComponent(id ? `/data-sync/${id}/edit` : "/data-sync/new"),
            )
          }
          onDataSourceChange={(value) => requestConnectionChange("source", value)}
        />
        <DataSourceEndpointCard
          title="去向"
          dataSourceType={draft.targetType}
          dataSources={targetDataSources}
          dataSourceId={form.targetDataSourceId}
          refreshing={dataSourcesLoading}
          onRefresh={loadDataSources}
          onCreateDataSource={() =>
            navigate(
              "/data-source?create=1&returnTo=" +
                encodeURIComponent(id ? `/data-sync/${id}/edit` : "/data-sync/new"),
            )
          }
          onDataSourceChange={(value) => requestConnectionChange("target", value)}
        />
      </div>
    </CollapseSection>
  );
}
