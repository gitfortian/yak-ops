import { Database, Users } from "lucide-react";
import { useState } from "react";

import type { FacetSummary } from "@/service/data-map/types";
import type { TagKey } from "@/service/tags";

import type { DatasourceOption, DomainCategory } from "../shared/lookups";

export type BrowseTab = "domains" | "datasources" | "tags";

export type BrowseSelection =
  | { kind: "all" }
  | { kind: "unclassified" }
  | { kind: "domain"; id: string }
  | { kind: "datasource"; id: string }
  | { kind: "tagValue"; id: string };

interface Props {
  categories: DomainCategory[];
  taxonomyLoading: boolean;
  datasources: DatasourceOption[];
  tagDictionary: TagKey[];
  domainFacets: FacetSummary[];
  datasourceFacets: FacetSummary[];
  selected: BrowseSelection;
  unclassifiedCount: number;
  onSelect: (selection: BrowseSelection) => void;
}

const TABS: { key: BrowseTab; label: string }[] = [
  { key: "domains", label: "数据域" },
  { key: "datasources", label: "数据源" },
  { key: "tags", label: "标签" },
];

const entryClass = (active: boolean) =>
  `flex w-full cursor-pointer items-center gap-1.5 rounded-md px-2 py-1.5 text-left text-[13px] transition-colors ${
    active
      ? "bg-[var(--yak-color-primary-subtle)] font-medium text-[var(--yak-color-primary)]"
      : "text-[var(--yak-components-panel-text)] hover:bg-[var(--yak-color-hover)]"
  }`;

const countBadge = (count: number | undefined) => (
  <span className="ml-auto shrink-0 text-[11px] text-[var(--yak-components-muted-text)]">
    {count === undefined ? "" : count}
  </span>
);

/** The catalog browse panel: domain taxonomy, datasources and the tag dictionary with real counts. */
export default function BrowsePanel({
  categories,
  taxonomyLoading,
  datasources,
  tagDictionary,
  domainFacets,
  datasourceFacets,
  selected,
  unclassifiedCount,
  onSelect,
}: Props) {
  const [tab, setTab] = useState<BrowseTab>("domains");
  const domainCount = (id: string) =>
    domainFacets.find((facet) => facet.facetKey === id)?.assetCount;
  const datasourceCount = (id: string) =>
    datasourceFacets.find((facet) => facet.facetKey === id)?.assetCount;

  return (
    <div className="overflow-hidden rounded-[var(--yak-radius-control-medium)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)]">
      <div className="flex border-b border-[var(--yak-components-control-border)]">
        {TABS.map((entry) => (
          <button
            key={entry.key}
            type="button"
            onClick={() => setTab(entry.key)}
            aria-current={tab === entry.key ? true : undefined}
            className={`flex-1 cursor-pointer border-b-2 py-2.5 text-xs transition-colors ${
              tab === entry.key
                ? "border-[var(--yak-color-primary)] font-semibold text-[var(--yak-color-primary)]"
                : "border-transparent text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-panel-text)]"
            }`}
          >
            {entry.label}
          </button>
        ))}
      </div>

      <div className="max-h-[calc(100vh-220px)] space-y-0.5 overflow-auto p-2">
        {tab === "domains" && (
          <>
            <button
              type="button"
              className={entryClass(selected.kind === "all")}
              onClick={() => onSelect({ kind: "all" })}
            >
              <Database
                size={13}
                className="shrink-0 text-[var(--yak-color-primary)]"
                aria-hidden="true"
              />
              全部资产
              {countBadge(
                domainFacets.reduce((sum, facet) => sum + facet.assetCount, 0) + unclassifiedCount,
              )}
            </button>
            <button
              type="button"
              className={entryClass(selected.kind === "unclassified")}
              onClick={() => onSelect({ kind: "unclassified" })}
            >
              <span className="min-w-0 truncate">未分类</span>
              {countBadge(unclassifiedCount)}
            </button>
            {taxonomyLoading && (
              <p className="px-2 py-1 text-xs text-[var(--yak-components-muted-text)]">加载中</p>
            )}
            {categories.map((category) => (
              <section key={category.id} className="pt-1.5">
                <p className="truncate px-2 pb-1 text-[11px] text-[var(--yak-components-muted-text)]">
                  {category.name}
                </p>
                {category.domains.map((domain) => (
                  <button
                    key={domain.id}
                    type="button"
                    className={entryClass(selected.kind === "domain" && selected.id === domain.id)}
                    onClick={() => onSelect({ kind: "domain", id: domain.id })}
                  >
                    <Users
                      size={13}
                      className="shrink-0 text-[var(--yak-color-primary)]"
                      aria-hidden="true"
                    />
                    <span className="min-w-0 truncate">{domain.name}</span>
                    {countBadge(domainCount(domain.id))}
                  </button>
                ))}
              </section>
            ))}
          </>
        )}

        {tab === "datasources" && (
          <>
            <button
              type="button"
              className={entryClass(selected.kind === "all")}
              onClick={() => onSelect({ kind: "all" })}
            >
              全部数据源
              {countBadge(datasourceFacets.reduce((sum, facet) => sum + facet.assetCount, 0))}
            </button>
            {datasources.map((datasource) => (
              <button
                key={datasource.id}
                type="button"
                className={entryClass(
                  selected.kind === "datasource" && selected.id === datasource.id,
                )}
                onClick={() => onSelect({ kind: "datasource", id: datasource.id })}
              >
                <Database
                  size={13}
                  className="shrink-0 text-[var(--yak-color-primary)]"
                  aria-hidden="true"
                />
                <span className="min-w-0 truncate">{datasource.name}</span>
                {countBadge(datasourceCount(datasource.id))}
              </button>
            ))}
          </>
        )}

        {tab === "tags" && (
          <>
            {tagDictionary.length === 0 && (
              <p className="px-2 py-1 text-xs text-[var(--yak-components-muted-text)]">
                还没有标签键，请先到标签管理创建。
              </p>
            )}
            {tagDictionary.map((key) => (
              <section key={key.id} className="pt-1.5">
                <p className="truncate px-2 pb-1 text-[11px] text-[var(--yak-components-muted-text)]">
                  {key.name}
                </p>
                {key.values.map((value) => (
                  <button
                    key={value.id}
                    type="button"
                    className={entryClass(selected.kind === "tagValue" && selected.id === value.id)}
                    onClick={() => onSelect({ kind: "tagValue", id: value.id })}
                  >
                    <span className="min-w-0 truncate">{value.name}</span>
                    {countBadge(value.usageCount)}
                  </button>
                ))}
              </section>
            ))}
          </>
        )}
      </div>
    </div>
  );
}
