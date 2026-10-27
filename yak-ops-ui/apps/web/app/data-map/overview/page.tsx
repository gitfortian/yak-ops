import { Alert, Button, PageHeader } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";

import { countLabel, getOverview } from "@/service/data-map/catalog";
import { dataMapFailureMessage } from "@/service/data-map/request";

import AssetTable from "../shared/asset-table";
import { dataMapKeys } from "../shared/keys";
import { DATA_MAP_BASE } from "../shared/labels";
import { useDataMapScope } from "../shared/scope";

const CARD_CLASS =
  "flex min-w-0 flex-col gap-1 border border-[var(--yak-components-panel-border)] bg-white px-4 py-3";

const StatCard = ({ label, value }: { label: string; value: number | undefined }) => (
  <div className={CARD_CLASS}>
    <span className="text-xs text-[var(--yak-components-muted-text)]">{label}</span>
    <span className="text-xl font-semibold text-[var(--yak-components-panel-text)]">
      {value === undefined ? "—" : countLabel(value)}
    </span>
  </div>
);

export default function OverviewPage() {
  const navigate = useNavigate();
  const { scope, ready } = useDataMapScope();
  const overview = useQuery({
    queryKey: dataMapKeys.overview(scope),
    queryFn: ({ signal }) => getOverview(signal),
    enabled: ready,
  });

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="数据地图概述"
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <Button size="small" onClick={() => navigate(`${DATA_MAP_BASE}/crawls`)}>
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

        <section className="flex min-h-0 flex-1 flex-col bg-white p-4">
          <h2 className="mb-3 text-[13px] font-medium text-[var(--yak-components-panel-text)]">
            热门资产
          </h2>
          <AssetTable
            records={overview.data?.hotTables ?? []}
            loading={overview.isPending}
            emptyText={
              overview.isSuccess && (overview.data?.assetCount ?? 0) === 0 ? (
                <div className="flex flex-col items-center gap-3">
                  <span>尚未采集任何资产</span>
                  <Button
                    size="small"
                    variant="primary"
                    onClick={() => navigate(`${DATA_MAP_BASE}/crawls?create=1`)}
                  >
                    提交采集
                  </Button>
                </div>
              ) : (
                "暂无访问记录"
              )
            }
          />
        </section>
      </div>
    </div>
  );
}
