import { Alert, Button, Input } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";
import {
  AlertTriangle,
  ArrowRight,
  Clock3,
  Database,
  FileText,
  Search,
  Server,
  Users,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import { countLabel, getOverview, getOverviewFacets } from "@/service/data-map/catalog";
import { dataMapFailureMessage } from "@/service/data-map/request";

import ActivityFeed from "./activity-feed";
import DomainCards from "./domain-cards";
import HotTables from "./hot-tables";
import StatCard from "../shared/stat-card";
import DataMapEmptyState from "../shared/empty-state";
import { dataMapKeys } from "../shared/keys";
import { DATA_MAP_BASE } from "../shared/labels";
import { useDataMapScope } from "../shared/scope";

const percent = (covered: number, total: number) =>
  total > 0 ? `${Math.round((covered / total) * 100)}%` : "—";

/** 渐变探索横幅：全局搜索入口与热门资产快捷词。 */
function Hero({
  assetCount,
  datasourceCount,
  hotTables,
}: {
  assetCount: number | undefined;
  datasourceCount: number | undefined;
  hotTables: { id: string; name: string }[];
}) {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const focus = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        inputRef.current?.focus();
      }
    };
    window.addEventListener("keydown", focus);
    return () => window.removeEventListener("keydown", focus);
  }, []);

  const search = () => {
    const trimmed = keyword.trim();
    if (trimmed.length === 0) return;
    navigate(`${DATA_MAP_BASE}/catalog?keyword=${encodeURIComponent(trimmed)}`);
  };

  return (
    <section className="rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[image:var(--yak-datamap-hero-gradient)] px-7 pb-6 pt-7 shadow-[var(--yak-datamap-card-shadow)]">
      <h2 className="text-[17px] font-bold tracking-tight text-[var(--yak-components-panel-text)]">
        探索数据资产
      </h2>
      <p className="mt-1.5 text-xs text-[var(--yak-components-muted-text)]">
        已收录 {datasourceCount === undefined ? "—" : countLabel(datasourceCount)} 个数据源的{" "}
        {assetCount === undefined ? "—" : countLabel(assetCount)} 张表
      </p>
      <div className="relative mt-4 max-w-[640px]">
        <Search
          size={16}
          aria-hidden="true"
          className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-[var(--yak-components-input-icon)]"
        />
        <Input
          ref={inputRef}
          value={keyword}
          variant="outlined"
          className="h-11 rounded-[10px] border-[1.5px] border-[var(--yak-color-primary-ring)] pl-10 pr-16 shadow-[var(--yak-datamap-hero-search-shadow)]"
          placeholder="搜索表、字段、描述、标签，例如「订单金额」"
          aria-label="搜索资产"
          onChange={(event) => setKeyword(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") search();
          }}
        />
        <kbd className="pointer-events-none absolute right-3 top-1/2 flex -translate-y-1/2 items-center gap-0.5 rounded border border-[var(--yak-components-control-border)] px-1.5 py-0.5 text-[10px] text-[var(--yak-components-muted-text)]">
          Ctrl K
        </kbd>
      </div>
      {hotTables.length > 0 && (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <span className="text-xs text-[var(--yak-components-muted-text)]">热门搜索</span>
          {hotTables.slice(0, 4).map((table) => (
            <button
              key={table.id}
              type="button"
              onClick={() =>
                navigate(`${DATA_MAP_BASE}/catalog?keyword=${encodeURIComponent(table.name)}`)
              }
              className="inline-flex h-[26px] cursor-pointer items-center gap-1.5 rounded-full border border-[var(--yak-components-input-border)] bg-[var(--yak-datamap-surface)] px-2.5 text-xs text-[var(--yak-components-panel-text)] transition-colors hover:border-[var(--yak-color-primary)] hover:bg-[var(--yak-color-primary-subtle)] hover:text-[var(--yak-color-primary)]"
            >
              <span className="font-mono text-[11.5px]">{table.name}</span>
            </button>
          ))}
        </div>
      )}
    </section>
  );
}

export default function OverviewPage() {
  const navigate = useNavigate();
  const { scope, ready } = useDataMapScope();

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

      <Hero
        assetCount={data?.assetCount}
        datasourceCount={data?.managedDatasourceCount}
        hotTables={hotTables}
      />

      {overview.isSuccess && assetCount === 0 ? (
        <section className="mt-4 rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] shadow-[var(--yak-datamap-card-shadow)]">
          <div className="p-4">
            <DataMapEmptyState
              title="还没有采集任何资产"
              description="发起一次采集，把数据源中的表同步到资产目录。"
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
          </div>
        </section>
      ) : (
        <>
          <section className="mt-4 grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
            <StatCard
              icon={<Database size={15} aria-hidden="true" />}
              iconClassName="bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
              label="资产总数"
              value={data ? countLabel(assetCount) : "—"}
            />
            <StatCard
              icon={<Server size={15} aria-hidden="true" />}
              iconClassName="bg-[var(--yak-datamap-badge-cyan-bg)] text-[var(--yak-datamap-badge-cyan-text)]"
              label="已接入数据源"
              value={data ? countLabel(data.managedDatasourceCount) : "—"}
            />
            <StatCard
              icon={<FileText size={15} aria-hidden="true" />}
              iconClassName="bg-[var(--yak-datamap-badge-purple-bg)] text-[var(--yak-datamap-badge-purple-text)]"
              label="描述覆盖率"
              value={data ? percent(data.describedCount, assetCount) : "—"}
              hint={data ? `${data.describedCount} 张已填写` : undefined}
            />
            <StatCard
              icon={<Users size={15} aria-hidden="true" />}
              iconClassName="bg-[var(--yak-datamap-badge-gold-bg)] text-[var(--yak-datamap-badge-gold-text)]"
              label="归属覆盖率"
              value={data ? percent(data.ownedCount, assetCount) : "—"}
              hint={data ? `${data.ownedCount} 张已指派` : undefined}
            />
            <StatCard
              icon={<Clock3 size={15} aria-hidden="true" />}
              iconClassName="bg-[var(--yak-components-badge-success-bg)] text-[var(--yak-components-badge-success-text)]"
              label="近 7 天新增"
              value={data ? countLabel(data.newTablesLastSevenDays) : "—"}
            />
            <StatCard
              icon={<AlertTriangle size={15} aria-hidden="true" />}
              iconClassName="bg-[var(--yak-components-badge-warning-bg)] text-[var(--yak-components-badge-warning-text)]"
              label="未分类"
              value={data ? countLabel(data.unclassifiedCount) : "—"}
              hint={data && data.unclassifiedCount > 0 ? "待归类" : undefined}
              hintTone="down"
            />
          </section>

          <section className="mt-6">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold tracking-tight">按数据域浏览</h3>
            </div>
            <div className="mt-3">
              <DomainCards
                hotTables={hotTables}
                unclassifiedCount={data?.unclassifiedCount ?? 0}
                domains={facets.data?.domains ?? []}
                domainsLoading={facets.isPending}
              />
            </div>
          </section>

          <section className="mt-6 grid grid-cols-1 items-start gap-4 xl:grid-cols-[1.7fr_1fr]">
            <div className="overflow-hidden rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] shadow-[var(--yak-datamap-card-shadow)]">
              <div className="flex items-center gap-2 border-b border-[var(--yak-components-panel-border)] px-4 py-3">
                <h3 className="text-[13px] font-bold">热门资产</h3>
                <span className="rounded-full bg-[var(--yak-color-primary-subtle)] px-2 py-0.5 text-[11px] font-medium text-[var(--yak-color-primary)]">
                  按访问热度
                </span>
                <button
                  type="button"
                  onClick={() => navigate(`${DATA_MAP_BASE}/catalog`)}
                  className="ml-auto inline-flex cursor-pointer items-center gap-0.5 text-xs text-[var(--yak-components-muted-text)] transition-colors hover:text-[var(--yak-color-primary)]"
                >
                  查看全部
                  <ArrowRight size={12} aria-hidden="true" />
                </button>
              </div>
              <HotTables records={hotTables} loading={overview.isPending} />
            </div>

            <div className="overflow-hidden rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] shadow-[var(--yak-datamap-card-shadow)]">
              <div className="flex items-center gap-2 border-b border-[var(--yak-components-panel-border)] px-4 py-3">
                <h3 className="text-[13px] font-bold">平台动态</h3>
                <button
                  type="button"
                  onClick={() => navigate(`${DATA_MAP_BASE}/tasks`)}
                  className="ml-auto inline-flex cursor-pointer items-center gap-0.5 text-xs text-[var(--yak-components-muted-text)] transition-colors hover:text-[var(--yak-color-primary)]"
                >
                  全部
                  <ArrowRight size={12} aria-hidden="true" />
                </button>
              </div>
              <div className="px-4 py-3">
                <ActivityFeed />
              </div>
            </div>
          </section>
        </>
      )}
    </div>
  );
}
