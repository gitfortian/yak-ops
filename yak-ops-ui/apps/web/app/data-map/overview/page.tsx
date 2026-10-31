import { Alert, Button, Card, CardContent, CardHeader, Input } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { countLabel, getOverview, getOverviewFacets } from "@/service/data-map/catalog";
import { dataMapFailureMessage } from "@/service/data-map/request";

import ActivityFeed from "./activity-feed";
import DomainCards from "./domain-cards";
import HotTables from "./hot-tables";
import DataMapEmptyState from "../shared/empty-state";
import { dataMapKeys } from "../shared/keys";
import { DATA_MAP_BASE } from "../shared/labels";
import { useDataMapScope } from "../shared/scope";

const percent = (covered: number, total: number) =>
  total > 0 ? `${Math.round((covered / total) * 100)}%` : "—";

const KpiCard = ({ label, value, hint }: { label: string; value: string; hint?: string }) => (
  <Card>
    <CardContent className="p-4">
      <div className="text-xs text-[var(--yak-components-muted-text)]">{label}</div>
      <div className="mt-1.5 text-xl font-semibold text-[var(--yak-components-panel-text)]">
        {value}
      </div>
      {hint && (
        <div className="mt-0.5 truncate text-[11px] text-[var(--yak-components-muted-text)]">
          {hint}
        </div>
      )}
    </CardContent>
  </Card>
);

export default function OverviewPage() {
  const navigate = useNavigate();
  const { scope, ready } = useDataMapScope();
  const [keyword, setKeyword] = useState("");

  const overview = useQuery({
    queryKey: dataMapKeys.overview(scope),
    queryFn: ({ signal }) => getOverview(signal),
    enabled: ready,
  });
  const facets = useQuery({
    queryKey: dataMapKeys.overviewFacets(scope),
    queryFn: ({ signal }) => getOverviewFacets(signal),
    enabled: ready,
  });

  const data = overview.data;
  const assetCount = data?.assetCount ?? 0;
  const hotTables = data?.hotTables ?? [];
  const catalogLink = (query: string) => `${DATA_MAP_BASE}/catalog${query}`;

  const search = () => {
    const trimmed = keyword.trim();
    if (trimmed.length === 0) return;
    navigate(catalogLink(`?keyword=${encodeURIComponent(trimmed)}`));
  };

  return (
    <div className="h-full min-h-0 overflow-auto bg-[var(--yak-color-background)] px-6 py-5 max-md:px-4">
      {overview.isError && (
        <Alert className="mb-4">
          {dataMapFailureMessage(overview.error, "总览加载失败，请稍后重试。")}
          <Button size="small" className="ml-3" onClick={() => void overview.refetch()}>
            重试
          </Button>
        </Alert>
      )}

      <Card>
        <CardContent className="p-6">
          <h2 className="text-base font-bold">探索数据资产</h2>
          <p className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
            已收录 {data ? countLabel(data.managedDatasourceCount) : "—"} 个数据源的{" "}
            {data ? countLabel(assetCount) : "—"} 张表，支持按表名、字段、描述与标签搜索
          </p>
          <div className="relative mt-4 max-w-2xl">
            <Search
              size={15}
              aria-hidden="true"
              className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-input-icon)]"
            />
            <Input
              value={keyword}
              variant="outlined"
              size="large"
              className="h-11 pl-9 shadow-sm"
              placeholder="搜索表、字段、描述、标签，例如「订单金额」"
              aria-label="搜索资产"
              onChange={(event) => setKeyword(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") search();
              }}
            />
          </div>
          {hotTables.length > 0 && (
            <div className="mt-2.5 flex flex-wrap items-center gap-2">
              <span className="text-xs text-[var(--yak-components-muted-text)]">热门资产</span>
              {hotTables.slice(0, 4).map((table) => (
                <button
                  key={table.id}
                  type="button"
                  onClick={() =>
                    navigate(catalogLink(`?keyword=${encodeURIComponent(table.name)}`))
                  }
                  className="cursor-pointer rounded-full border border-[var(--yak-components-control-border)] px-2.5 py-0.5 text-xs text-[var(--yak-components-panel-text)] hover:border-[var(--yak-color-primary)] hover:text-[var(--yak-color-primary)]"
                >
                  {table.name}
                </button>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {overview.isSuccess && assetCount === 0 ? (
        <Card className="mt-4">
          <CardContent>
            <DataMapEmptyState
              title="还没有采集任何资产"
              description="发起一次采集，把数据源中的表和字段同步到资产目录，这里会展示覆盖概况与热门资产。"
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
          </CardContent>
        </Card>
      ) : (
        <>
          <div className="mt-4 grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
            <KpiCard label="资产总数" value={data ? countLabel(assetCount) : "—"} />
            <KpiCard
              label="已接入数据源"
              value={data ? countLabel(data.managedDatasourceCount) : "—"}
            />
            <KpiCard
              label="描述覆盖率"
              value={data ? percent(data.describedCount, assetCount) : "—"}
              hint={data ? `${data.describedCount} 张已填写` : undefined}
            />
            <KpiCard
              label="归属覆盖率"
              value={data ? percent(data.ownedCount, assetCount) : "—"}
              hint={data ? `${data.ownedCount} 张已指派` : undefined}
            />
            <KpiCard
              label="近 7 天新增"
              value={data ? countLabel(data.newTablesLastSevenDays) : "—"}
            />
            <KpiCard label="未分类" value={data ? countLabel(data.unclassifiedCount) : "—"} />
          </div>

          <h3 className="text-sm font-semibold">按数据域浏览</h3>
          <div className="mt-3">
            <DomainCards
              hotTables={hotTables}
              unclassifiedCount={data?.unclassifiedCount ?? 0}
              domains={facets.data?.domains ?? []}
              domainsLoading={facets.isPending}
            />
          </div>

          <div className="mt-6 grid grid-cols-1 items-start gap-4 xl:grid-cols-[1.7fr_1fr]">
            <Card>
              <CardHeader title="热门资产" />
              <CardContent className="p-0 pb-2">
                <HotTables records={hotTables} loading={overview.isPending} />
              </CardContent>
            </Card>
            <Card>
              <CardHeader
                title="平台动态"
                extra={
                  <Button
                    size="small"
                    variant="ghost"
                    onClick={() => navigate(`${DATA_MAP_BASE}/tasks`)}
                  >
                    全部
                  </Button>
                }
              />
              <CardContent>
                <ActivityFeed />
              </CardContent>
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
