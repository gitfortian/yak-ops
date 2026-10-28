import { Alert, Button, Card, CardContent, CardHeader, PageHeader } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";

import { countLabel, getOverview } from "@/service/data-map/catalog";
import { dataMapFailureMessage } from "@/service/data-map/request";

import AssetTable from "../shared/asset-table";
import DataMapEmptyState from "../shared/empty-state";
import { dataMapKeys } from "../shared/keys";
import { DATA_MAP_BASE } from "../shared/labels";
import { useDataMapScope } from "../shared/scope";

const StatCard = ({ label, value }: { label: string; value: number | undefined }) => (
  <Card>
    <CardContent className="p-4">
      <div className="text-xs text-[var(--yak-components-muted-text)]">{label}</div>
      <div className="mt-1.5 text-xl font-semibold text-[var(--yak-components-panel-text)]">
        {value === undefined ? "—" : countLabel(value)}
      </div>
    </CardContent>
  </Card>
);

export default function OverviewPage() {
  const navigate = useNavigate();
  const { scope, ready } = useDataMapScope();
  const overview = useQuery({
    queryKey: dataMapKeys.overview(scope),
    queryFn: ({ signal }) => getOverview(signal),
    enabled: ready,
  });

  const assetCount = overview.data?.assetCount ?? 0;
  const hotTables = overview.data?.hotTables ?? [];
  /** Zero hot assets drops the grid entirely, so the empty state is not painted under a header. */
  const hotTablesEmpty = overview.isSuccess && hotTables.length === 0;

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="数据地图概述"
        bordered
        className="bg-[var(--yak-color-background)] px-6 max-md:px-4"
        extra={
          <Button size="small" onClick={() => navigate(`${DATA_MAP_BASE}/tasks`)}>
            采集任务
          </Button>
        }
      />

      <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-auto px-6 pb-4 pt-5 max-md:px-4">
        {overview.isError && (
          <Alert>
            {dataMapFailureMessage(overview.error, "总览加载失败，请稍后重试。")}
            <Button size="small" className="ml-3" onClick={() => void overview.refetch()}>
              重试
            </Button>
          </Alert>
        )}

        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <StatCard label="资产总数" value={overview.data?.assetCount} />
          <StatCard label="已管理数据源" value={overview.data?.managedDatasourceCount} />
          <StatCard label="近 7 天新增" value={overview.data?.newTablesLastSevenDays} />
          <StatCard label="未分类" value={overview.data?.unclassifiedCount} />
        </div>

        <Card className="flex min-h-0 flex-1 flex-col">
          <CardHeader title="热门资产" />
          {hotTablesEmpty ? (
            <CardContent>
              {assetCount === 0 ? (
                <DataMapEmptyState
                  title="还没有采集任何资产"
                  description="发起一次采集，把数据源中的表和字段同步到资产目录，这里会显示访问最多的表。"
                  action={
                    <Button
                      size="small"
                      variant="primary"
                      onClick={() => navigate(`${DATA_MAP_BASE}/tasks/new`)}
                    >
                      发起采集
                    </Button>
                  }
                />
              ) : (
                <DataMapEmptyState
                  title="暂无访问记录"
                  description="打开任意资产详情后，访问次数最多的表会出现在这里。"
                  action={
                    <Button size="small" onClick={() => navigate(`${DATA_MAP_BASE}/catalog`)}>
                      去资产目录
                    </Button>
                  }
                />
              )}
            </CardContent>
          ) : (
            <AssetTable records={hotTables} loading={overview.isPending} />
          )}
        </Card>
      </div>
    </div>
  );
}
