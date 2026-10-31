import { Alert, Button, PageHeader, Pagination } from "@yak-ops/yak-ui";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { LayoutGrid, List, X } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import {
  countLabel,
  getOverview,
  getOverviewFacets,
  searchTables,
} from "@/service/data-map/catalog";
import { dataMapFailureMessage, isSearchTooFrequent } from "@/service/data-map/request";
import type { TableQuery } from "@/service/data-map/types";

import AssetCards from "./asset-cards";
import BrowsePanel, { type BrowseSelection } from "./browse-panel";
import CatalogFilters from "./filters";
import { useCatalogQuery } from "./query";
import AssetTable from "../shared/asset-table";
import DataMapEmptyState from "../shared/empty-state";
import { dataMapKeys } from "../shared/keys";
import { DATA_MAP_BASE } from "../shared/labels";
import {
  useDatabases,
  useDatasourceOptions,
  useDomainTaxonomy,
  useOwnerNames,
  useOwnerOptions,
  useTagDictionary,
} from "../shared/lookups";
import { useDataMapScope } from "../shared/scope";

/** Typing pauses never cost a scan; only a pause or Enter submits, as section 6 requires. */
const SEARCH_DEBOUNCE_MS = 500;

const browseSelectionOf = (query: {
  domainId: string;
  datasourceId: string;
  unclassified: boolean;
  tagValueIds: string[];
}): BrowseSelection => {
  if (query.domainId) return { kind: "domain", id: query.domainId };
  if (query.datasourceId) return { kind: "datasource", id: query.datasourceId };
  if (query.unclassified) return { kind: "unclassified" };
  if (query.tagValueIds.length > 0) return { kind: "tagValue", id: query.tagValueIds[0] };
  return { kind: "all" };
};

export default function CatalogPage() {
  const navigate = useNavigate();
  const { query, update } = useCatalogQuery();
  const { scope, ready } = useDataMapScope();
  const [draft, setDraft] = useState(query.keyword);

  const taxonomy = useDomainTaxonomy();
  const datasources = useDatasourceOptions();
  const tagDictionary = useTagDictionary();
  const ownerOptions = useOwnerOptions();
  const { names: ownerNames } = useOwnerNames();
  const databases = useDatabases(query.datasourceId);
  const facets = useQuery({
    queryKey: dataMapKeys.overviewFacets(scope),
    queryFn: ({ signal }) => getOverviewFacets(signal),
    enabled: ready,
  });
  /** Shared with the explore home, so this read is served from cache after the first visit. */
  const overview = useQuery({
    queryKey: dataMapKeys.overview(scope),
    queryFn: ({ signal }) => getOverview(signal),
    enabled: ready,
    staleTime: 60_000,
  });

  useEffect(() => {
    if (draft.trim() === query.keyword) return;
    const timer = window.setTimeout(() => update({ keyword: draft.trim() }), SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [draft, query.keyword, update]);

  const tableQuery: TableQuery = {
    keyword: query.keyword || undefined,
    datasourceId: query.datasourceId || undefined,
    database: query.database || undefined,
    domainId: query.domainId || undefined,
    unclassified: query.unclassified || undefined,
    ownerId: query.ownerId || undefined,
    tagValueIds: query.tagValueIds.length > 0 ? query.tagValueIds : undefined,
    includeMissing: query.includeMissing || undefined,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  };

  const page = useQuery({
    queryKey: dataMapKeys.tables(scope, tableQuery),
    queryFn: ({ signal }) => searchTables(tableQuery, signal),
    enabled: ready,
    placeholderData: keepPreviousData,
  });

  const records = page.data?.bizData ?? [];
  const total = page.data?.pagination?.total ?? 0;
  /**
   * Empty is decided by the page contents, not by the count alone: a page can be empty while the
   * total is not (last row of a page removed), and that case must not leave a bare table header.
   */
  const isEmpty = page.isSuccess && !page.isPlaceholderData && records.length === 0;

  useEffect(() => {
    if (!page.isSuccess || page.isPlaceholderData) return;
    if (records.length === 0 && total > 0 && query.pageNo > 1) {
      update({ pageNo: query.pageNo - 1 });
    }
  }, [page.isSuccess, page.isPlaceholderData, records.length, total, query.pageNo, update]);

  const hasActiveFilters = Boolean(
    query.keyword ||
    query.datasourceId ||
    query.database ||
    query.domainId ||
    query.unclassified ||
    query.ownerId ||
    query.tagValueIds.length > 0 ||
    query.includeMissing,
  );

  const selectBrowse = (selection: BrowseSelection) => {
    switch (selection.kind) {
      case "all":
        update({
          domainId: "",
          unclassified: false,
          datasourceId: "",
          database: "",
          tagValueIds: [],
        });
        return;
      case "unclassified":
        update({ domainId: "", unclassified: true, datasourceId: "" });
        return;
      case "domain":
        update({ domainId: selection.id, unclassified: false, datasourceId: "" });
        return;
      case "datasource":
        update({ datasourceId: selection.id, domainId: "", unclassified: false, database: "" });
        return;
      case "tagValue":
        update({ tagValueIds: [selection.id], domainId: "", unclassified: false });
        return;
    }
  };

  const reset = () => {
    setDraft("");
    update({
      keyword: "",
      datasourceId: "",
      database: "",
      domainId: "",
      unclassified: false,
      ownerId: "",
      tagValueIds: [],
      includeMissing: false,
    });
  };

  const datasourceName = (id: string) =>
    datasources.options.find((option) => option.id === id)?.name ?? id;

  const activeFacets: { key: string; label: string; onRemove: () => void }[] = [];
  if (query.keyword) {
    activeFacets.push({
      key: "keyword",
      label: `关键词：${query.keyword}`,
      onRemove: () => {
        setDraft("");
        update({ keyword: "" });
      },
    });
  }
  if (query.domainId) {
    const label = taxonomy.domainNames.get(query.domainId) ?? query.domainId;
    activeFacets.push({
      key: "domain",
      label: `数据域：${label}`,
      onRemove: () => update({ domainId: "" }),
    });
  }
  if (query.unclassified) {
    activeFacets.push({
      key: "unclassified",
      label: "未分类",
      onRemove: () => update({ unclassified: false }),
    });
  }
  if (query.datasourceId) {
    activeFacets.push({
      key: "datasource",
      label: `数据源：${datasourceName(query.datasourceId)}`,
      onRemove: () => update({ datasourceId: "", database: "" }),
    });
  }
  if (query.database) {
    activeFacets.push({
      key: "database",
      label: `库：${query.database}`,
      onRemove: () => update({ database: "" }),
    });
  }
  if (query.ownerId) {
    activeFacets.push({
      key: "owner",
      label: `负责人：${ownerNames.get(query.ownerId) ?? query.ownerId}`,
      onRemove: () => update({ ownerId: "" }),
    });
  }
  query.tagValueIds.forEach((valueId) => {
    const label = tagDictionary.data
      ?.flatMap((key) => key.values)
      .find((value) => value.id === valueId)?.name;
    activeFacets.push({
      key: `tag-${valueId}`,
      label: `标签：${label ?? valueId}`,
      onRemove: () => update({ tagValueIds: query.tagValueIds.filter((id) => id !== valueId) }),
    });
  });
  if (query.includeMissing) {
    activeFacets.push({
      key: "includeMissing",
      label: "包含已缺失",
      onRemove: () => update({ includeMissing: false }),
    });
  }

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--yak-color-background)]">
      <PageHeader
        title="资产目录"
        className="px-6 pb-5 pt-5 max-md:px-4"
        extra={
          <Button size="small" onClick={() => navigate(`${DATA_MAP_BASE}/tasks/new`)}>
            发起采集
          </Button>
        }
      />

      <div className="flex min-h-0 flex-1 gap-4 px-6 pb-4 max-md:px-4">
        <div className="hidden w-56 shrink-0 lg:block">
          <BrowsePanel
            categories={taxonomy.categories}
            taxonomyLoading={taxonomy.isPending}
            datasources={datasources.options}
            tagDictionary={tagDictionary.data ?? []}
            domainFacets={facets.data?.domains ?? []}
            datasourceFacets={facets.data?.datasources ?? []}
            selected={browseSelectionOf(query)}
            unclassifiedCount={overview.data?.unclassifiedCount ?? 0}
            onSelect={selectBrowse}
          />
        </div>

        <div className="flex min-h-0 flex-1 flex-col gap-3">
          <div className="rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-3.5 shadow-[var(--yak-datamap-card-shadow)]">
            <CatalogFilters
              keyword={draft}
              datasourceId={query.datasourceId}
              database={query.database}
              ownerId={query.ownerId}
              tagValueIds={query.tagValueIds}
              includeMissing={query.includeMissing}
              datasources={datasources.options}
              databases={databases.data ?? []}
              tagDictionary={tagDictionary.data ?? []}
              ownerOptions={ownerOptions.data ?? []}
              hasActiveFilters={hasActiveFilters}
              onKeywordChange={setDraft}
              onKeywordCommit={() => update({ keyword: draft.trim() })}
              onDatasourceChange={(value) => update({ datasourceId: value, database: "" })}
              onDatabaseChange={(value) => update({ database: value })}
              onOwnerChange={(value) => update({ ownerId: value })}
              onTagValuesChange={(value) => update({ tagValueIds: value })}
              onIncludeMissingChange={(value) => update({ includeMissing: value })}
              onReset={reset}
            />

            <div className="mt-2.5 flex flex-wrap items-center gap-2">
              {activeFacets.map((facet) => (
                <button
                  key={facet.key}
                  type="button"
                  onClick={facet.onRemove}
                  className="flex cursor-pointer items-center gap-1 rounded-full border border-[var(--yak-color-primary-subtle)] bg-[var(--yak-color-primary-subtle)] px-2.5 py-0.5 text-xs text-[var(--yak-color-primary)] hover:opacity-80"
                >
                  {facet.label}
                  <X size={11} aria-hidden="true" />
                </button>
              ))}
              {activeFacets.length > 0 && (
                <button
                  type="button"
                  onClick={reset}
                  className="cursor-pointer text-xs text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-panel-text)]"
                >
                  清空筛选
                </button>
              )}
              <span className="ml-auto flex items-center gap-3 text-xs text-[var(--yak-components-muted-text)]">
                共 <b className="text-[var(--yak-components-panel-text)]">{countLabel(total)}</b>{" "}
                个资产
                <span className="flex overflow-hidden rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-control-border)]">
                  <button
                    type="button"
                    title="列表视图"
                    aria-pressed={query.view === "list"}
                    onClick={() => update({ view: "list" })}
                    className={`flex h-7 w-8 cursor-pointer items-center justify-center ${
                      query.view === "list"
                        ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
                        : "bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-muted-text)]"
                    }`}
                  >
                    <List size={13} aria-hidden="true" />
                  </button>
                  <button
                    type="button"
                    title="卡片视图"
                    aria-pressed={query.view === "cards"}
                    onClick={() => update({ view: "cards" })}
                    className={`flex h-7 w-8 cursor-pointer items-center justify-center border-l border-[var(--yak-components-control-border)] ${
                      query.view === "cards"
                        ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
                        : "bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-muted-text)]"
                    }`}
                  >
                    <LayoutGrid size={13} aria-hidden="true" />
                  </button>
                </span>
              </span>
            </div>
          </div>

          {page.isError && (
            <Alert>
              {isSearchTooFrequent(page.error)
                ? "搜索过于频繁，请稍后再试。"
                : dataMapFailureMessage(page.error, "资产加载失败，请稍后重试。")}
            </Alert>
          )}

          <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] shadow-[var(--yak-datamap-card-shadow)]">
            <div className="min-h-0 flex-1 overflow-auto p-3.5">
              {isEmpty ? (
                hasActiveFilters ? (
                  <DataMapEmptyState
                    title="没有匹配的资产"
                    description="调整条件或清空筛选后重试。"
                    action={
                      <Button size="small" onClick={reset}>
                        清空筛选
                      </Button>
                    }
                  />
                ) : (
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
                )
              ) : query.view === "cards" ? (
                <>
                  <AssetCards
                    records={records}
                    loading={page.isFetching && page.isPlaceholderData}
                  />
                  {!isEmpty && total > query.pageSize && (
                    <div className="mt-3 flex justify-end border-t border-[var(--yak-components-panel-border)] pt-3">
                      <Pagination
                        page={query.pageNo}
                        pageSize={query.pageSize}
                        total={total}
                        onChange={(pageNo, pageSize) => update({ pageNo, pageSize })}
                      />
                    </div>
                  )}
                </>
              ) : (
                <AssetTable
                  records={records}
                  loading={page.isFetching}
                  pagination={{
                    pageNo: query.pageNo,
                    pageSize: query.pageSize,
                    total,
                    onPageChange: (pageNo, pageSize) => update({ pageNo, pageSize }),
                  }}
                />
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
