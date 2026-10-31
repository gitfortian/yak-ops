import { Plus, Users } from "lucide-react";
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

const percent = (covered: number, total: number) =>
  total > 0 ? Math.round((covered / total) * 100) : 0;

const CoverageBar = ({ label, value }: { label: string; value: number }) => (
  <div>
    <div className="flex items-center justify-between text-[11px] text-[var(--yak-components-muted-text)]">
      <span>{label}</span>
      <span className="font-medium text-[var(--yak-components-panel-text)]">{value}%</span>
    </div>
    <div className="mt-1 h-1.5 rounded-full bg-[var(--yak-components-table-border)]">
      <div
        className="h-1.5 rounded-full bg-[var(--yak-color-primary)]"
        style={{ width: `${value}%` }}
      />
    </div>
  </div>
);

/** Domain cards of the explore home; every card deep-links into the catalog facet. */
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
            className="h-[132px] animate-pulse rounded-[var(--yak-radius-control-medium)] bg-[var(--yak-components-skeleton-bg)]"
          />
        ))}
      {!domainsLoading &&
        domains.map((domain) => (
          <button
            key={domain.facetKey}
            type="button"
            onClick={() => navigate(`${DATA_MAP_BASE}/catalog?domainId=${domain.facetKey}`)}
            className="cursor-pointer rounded-[var(--yak-radius-control-medium)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4 text-left transition-shadow hover:shadow-[var(--yak-components-panel-shadow)]"
          >
            <div className="flex items-center gap-2.5">
              <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
                <Users size={15} aria-hidden="true" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-semibold">
                  {domainNames.get(domain.facetKey) ?? "未命名数据域"}
                </div>
                <div className="mt-0.5 text-xs text-[var(--yak-components-muted-text)]">
                  {domain.assetCount} 个资产
                </div>
              </div>
            </div>
            <div className="mt-3 space-y-2">
              <CoverageBar
                label="描述覆盖"
                value={percent(domain.describedCount, domain.assetCount)}
              />
              <CoverageBar label="归属覆盖" value={percent(domain.ownedCount, domain.assetCount)} />
            </div>
            {hotByDomain.has(domain.facetKey) && (
              <div className="mt-3 truncate rounded-md border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-table-header-bg)] px-2 py-1 text-[11px] text-[var(--yak-components-muted-text)]">
                热门：{hotByDomain.get(domain.facetKey)}
              </div>
            )}
          </button>
        ))}
      {!domainsLoading && unclassifiedCount > 0 && (
        <button
          type="button"
          onClick={() => navigate(`${DATA_MAP_BASE}/catalog?unclassified=1`)}
          className="cursor-pointer rounded-[var(--yak-radius-control-medium)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4 text-left transition-shadow hover:shadow-[var(--yak-components-panel-shadow)]"
        >
          <div className="text-sm font-semibold">未分类</div>
          <div className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
            {unclassifiedCount} 个资产还没有归属数据域
          </div>
          <div className="mt-3 text-xs text-[var(--yak-color-primary)]">去归类 →</div>
        </button>
      )}
      <Link
        to="/data-modeling/warehouse-planning"
        className="flex min-h-[96px] items-center justify-center gap-1.5 rounded-[var(--yak-radius-control-medium)] border border-dashed border-[var(--yak-components-control-border)] text-sm text-[var(--yak-components-muted-text)] hover:border-[var(--yak-color-primary)] hover:text-[var(--yak-color-primary)]"
      >
        <Plus size={14} aria-hidden="true" />
        规划数据域
      </Link>
    </div>
  );
}
