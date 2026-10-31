import { Layers, Plus, TrendingUp, Users } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";

import type { FacetSummary, TableSummary } from "@/service/data-map/types";

import { DATA_MAP_BASE } from "../shared/labels";
import { useDomainTaxonomy } from "../shared/lookups";

interface Props {
  hotTables: TableSummary[];
  unclassifiedCount: number;
  domains: FacetSummary[];
  domainsLoading: boolean;
}

const ICON_TONES = [
  "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]",
  "bg-[var(--yak-datamap-badge-purple-bg)] text-[var(--yak-datamap-badge-purple-text)]",
  "bg-[var(--yak-datamap-badge-cyan-bg)] text-[var(--yak-datamap-badge-cyan-text)]",
  "bg-[var(--yak-datamap-badge-gold-bg)] text-[var(--yak-datamap-badge-gold-text)]",
];

const percent = (covered: number, total: number) =>
  total > 0 ? Math.round((covered / total) * 100) : 0;

function CoverageBar({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <div className="flex items-center justify-between text-[11px]">
        <span className="text-[var(--yak-components-muted-text)]">{label}</span>
        <span className="font-semibold text-[var(--yak-components-panel-text)] [font-variant-numeric:tabular-nums]">
          {value}%
        </span>
      </div>
      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-[var(--yak-datamap-progress-track)]">
        <div
          className="h-1.5 rounded-full bg-[var(--yak-color-primary)] transition-[width] duration-300"
          style={{ width: `${value}%` }}
        />
      </div>
    </div>
  );
}

/** 数据域卡片：资产规模、覆盖进度与热门资产，点击进入目录的对应分面。 */
export default function DomainCards({
  hotTables,
  unclassifiedCount,
  domains,
  domainsLoading,
}: Props) {
  const navigate = useNavigate();
  const { domainNames } = useDomainTaxonomy();

  const hotByDomain = new Map<string, string>();
  hotTables.forEach((table) => {
    if (table.domainId && !hotByDomain.has(table.domainId)) {
      hotByDomain.set(table.domainId, table.name);
    }
  });

  return (
    <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
      {domainsLoading &&
        [0, 1, 2].map((key) => (
          <div
            key={key}
            className="h-[172px] animate-pulse rounded-[var(--yak-radius-md)] bg-[var(--yak-components-skeleton-bg)]"
          />
        ))}
      {!domainsLoading &&
        domains.map((domain, index) => {
          const name = domainNames.get(domain.facetKey) ?? "未命名数据域";
          const hot = hotByDomain.get(domain.facetKey);
          return (
            <button
              key={domain.facetKey}
              type="button"
              onClick={() => navigate(`${DATA_MAP_BASE}/catalog?domainId=${domain.facetKey}`)}
              className="cursor-pointer rounded-[var(--yak-radius-md)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-4 text-left shadow-[var(--yak-datamap-card-shadow)] transition-all duration-200 hover:-translate-y-0.5 hover:border-[var(--yak-color-primary-ring)] hover:shadow-[var(--yak-datamap-card-shadow-hover)]"
            >
              <div className="flex items-center gap-2.5">
                <span
                  className={`flex size-8 shrink-0 items-center justify-center rounded-lg ${ICON_TONES[index % ICON_TONES.length]}`}
                >
                  <Layers size={15} aria-hidden="true" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-bold">{name}</span>
                  <span className="mt-0.5 block text-[11px] text-[var(--yak-components-muted-text)]">
                    {domain.assetCount} 个资产
                  </span>
                </span>
              </div>
              <div className="mt-3.5 space-y-2.5">
                <CoverageBar
                  label="描述覆盖"
                  value={percent(domain.describedCount, domain.assetCount)}
                />
                <CoverageBar
                  label="归属覆盖"
                  value={percent(domain.ownedCount, domain.assetCount)}
                />
              </div>
              {hot && (
                <div className="mt-3 flex items-center gap-1.5 truncate rounded-md border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-collapse-section-bg)] px-2 py-1.5 text-[11px] text-[var(--yak-components-muted-text)]">
                  <TrendingUp
                    size={11}
                    aria-hidden="true"
                    className="shrink-0 text-[var(--yak-components-badge-warning-text)]"
                  />
                  <span className="truncate">
                    热门：
                    <b className="font-mono font-semibold text-[var(--yak-components-panel-text)]">
                      {hot}
                    </b>
                  </span>
                </div>
              )}
            </button>
          );
        })}
      {!domainsLoading && unclassifiedCount > 0 && (
        <button
          type="button"
          onClick={() => navigate(`${DATA_MAP_BASE}/catalog?unclassified=1`)}
          className="cursor-pointer rounded-[var(--yak-radius-md)] border border-[var(--yak-datamap-badge-gold-border)] bg-[var(--yak-datamap-badge-gold-bg)] p-4 text-left transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[var(--yak-datamap-card-shadow-hover)]"
        >
          <div className="flex items-center gap-2.5">
            <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-[var(--yak-components-badge-warning-bg)] text-[var(--yak-components-badge-warning-text)]">
              <Users size={15} aria-hidden="true" />
            </span>
            <span>
              <span className="block text-sm font-bold text-[var(--yak-components-panel-text)]">
                未分类
              </span>
              <span className="mt-0.5 block text-[11px] text-[var(--yak-components-muted-text)]">
                {unclassifiedCount} 个资产待归类
              </span>
            </span>
          </div>
          <div className="mt-3 text-xs font-medium text-[var(--yak-datamap-badge-gold-text)]">
            去归类 →
          </div>
        </button>
      )}
      <Link
        to="/data-modeling/warehouse-planning"
        className="flex min-h-[96px] items-center justify-center gap-1.5 rounded-[var(--yak-radius-md)] border border-dashed border-[var(--yak-components-control-border)] text-sm text-[var(--yak-components-muted-text)] transition-colors hover:border-[var(--yak-color-primary)] hover:bg-[var(--yak-color-primary-subtle)] hover:text-[var(--yak-color-primary)]"
      >
        <Plus size={14} aria-hidden="true" />
        规划数据域
      </Link>
    </div>
  );
}
