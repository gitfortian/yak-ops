import { Alert, Button, Card, PageHeader } from "@yak-ops/yak-ui";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { countLabel, searchTables } from "@/service/data-map/catalog";
import { dataMapFailureMessage, isSearchTooFrequent } from "@/service/data-map/request";
import type { TableQuery } from "@/service/data-map/types";

import AssetTable from "../shared/asset-table";
import DataMapEmptyState from "../shared/empty-state";
import { dataMapKeys } from "../shared/keys";
import { DATA_MAP_BASE } from "../shared/labels";
import {
  useDatabases,
  useDatasourceOptions,
  useDomainTaxonomy,
  useTagOptions,
} from "../shared/lookups";
import { useDataMapScope } from "../shared/scope";
import DomainTree, { type DomainSelection } from "./domain-tree";
import CatalogFilters from "./filters";
import { useCatalogQuery } from "./query";

/** Typing pauses never cost a scan; only a pause or Enter submits, as section 6 requires. */
const SEARCH_DEBOUNCE_MS = 500;

const selectionOf = (domainId: string, unclassified: boolean): DomainSelection => {
  if (domainId) return { kind: "domain", id: domainId };
  return unclassified ? { kind: "unclassified" } : { kind: "all" };
};

export default function CatalogPage() {
  const navigate = useNavigate();
  const { query, update } = useCatalogQuery();
  const { scope, ready } = useDataMapScope();
  const [draft, setDraft] = useState(query.keyword);

  const taxonomy = useDomainTaxonomy();
  const datasources = useDatasourceOptions();
  const tags = useTagOptions();
  const databases = useDatabases(query.datasourceId);

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
    tagId: query.tagId || undefined,
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
    query.tagId,
  );

  const selectDomain = (selection: DomainSelection) => {
    if (selection.kind === "all") update({ domainId: "", unclassified: false });
    else if (selection.kind === "unclassified") update({ domainId: "", unclassified: true });
    else update({ domainId: selection.id, unclassified: false });
  };

  const reset = () => {
    setDraft("");
    update({
      keyword: "",
      datasourceId: "",
      database: "",
      domainId: "",
      unclassified: false,
      tagId: "",
    });
  };

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="资产目录"
        bordered
        className="bg-[var(--yak-color-background)] px-6 max-md:px-4"
        extra={
          <Button size="small" onClick={() => navigate(`${DATA_MAP_BASE}/tasks/new`)}>
            发起采集
          </Button>
        }
      />

      <div className="flex min-h-0 flex-1 gap-4 px-6 pb-4 pt-5 max-md:px-4">
        <Card className="hidden w-56 shrink-0 lg:block">
          <div className="overflow-auto p-3">
            <DomainTree
              categories={taxonomy.categories}
              loading={taxonomy.isPending}
              selected={selectionOf(query.domainId, query.unclassified)}
              onSelect={selectDomain}
            />
          </div>
        </Card>

        <Card className="flex min-h-0 flex-1 flex-col p-4">
          <CatalogFilters
            keyword={draft}
            datasourceId={query.datasourceId}
            database={query.database}
            tagId={query.tagId}
            includeMissing={query.includeMissing}
            datasources={datasources.options}
            databases={databases.data ?? []}
            tags={tags.data ?? []}
            hasActiveFilters={hasActiveFilters}
            onKeywordChange={setDraft}
            onKeywordCommit={() => update({ keyword: draft.trim() })}
            onDatasourceChange={(value) => update({ datasourceId: value, database: "" })}
            onDatabaseChange={(value) => update({ database: value })}
            onTagChange={(value) => update({ tagId: value })}
            onIncludeMissingChange={(value) => update({ includeMissing: value })}
            onReset={reset}
          />

          <div className="mt-3 flex items-center justify-between gap-2">
            <span className="text-xs text-[var(--yak-components-muted-text)]">
              共 {countLabel(total)} 个资产
            </span>
            {page.isError && (
              <Button size="small" onClick={() => void page.refetch()}>
                重试
              </Button>
            )}
          </div>

          {page.isError && (
            <Alert className="mt-2">
              {isSearchTooFrequent(page.error)
                ? "搜索过于频繁，请稍后再试。"
                : dataMapFailureMessage(page.error, "资产加载失败，请稍后重试。")}
            </Alert>
          )}

          <div className="mt-2 min-h-0 flex-1 overflow-auto">
            {isEmpty ? (
              hasActiveFilters ? (
                <DataMapEmptyState
                  title="没有匹配的资产"
                  description="当前筛选条件下没有资产。调整条件或清空筛选后重试。"
                  action={
                    <Button size="small" onClick={reset}>
                      清空筛选
                    </Button>
                  }
                />
              ) : (
                <DataMapEmptyState
                  title="还没有采集任何资产"
                  description="发起一次采集，把数据源中的表和字段同步到资产目录。采集完成后这里会显示每一张表。"
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
        </Card>
      </div>
    </div>
  );
}
