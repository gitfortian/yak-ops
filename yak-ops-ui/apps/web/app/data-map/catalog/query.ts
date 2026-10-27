import { useCallback } from "react";
import { useSearchParams } from "react-router-dom";

export interface CatalogQuery {
  keyword: string;
  datasourceId: string;
  database: string;
  domainId: string;
  tagId: string;
  unclassified: boolean;
  includeMissing: boolean;
  pageNo: number;
  pageSize: number;
}

export const DEFAULT_CATALOG_PAGE_SIZE = 20;

const NUMBER_PARAM = /^\d+$/;

const positive = (value: string | null, fallback: number) => {
  if (!value || !NUMBER_PARAM.test(value)) return fallback;
  const parsed = Number(value);
  return parsed > 0 ? parsed : fallback;
};

const readQuery = (params: URLSearchParams): CatalogQuery => ({
  keyword: params.get("keyword") ?? "",
  datasourceId: params.get("datasourceId") ?? "",
  database: params.get("database") ?? "",
  domainId: params.get("domainId") ?? "",
  tagId: params.get("tagId") ?? "",
  unclassified: params.get("unclassified") === "1",
  includeMissing: params.get("includeMissing") === "1",
  pageNo: positive(params.get("pageNo"), 1),
  pageSize: positive(params.get("pageSize"), DEFAULT_CATALOG_PAGE_SIZE),
});

const writeQuery = (base: URLSearchParams, merged: CatalogQuery) => {
  const next = new URLSearchParams(base);
  const assign = (key: string, value: string) => {
    if (value) next.set(key, value);
    else next.delete(key);
  };
  assign("keyword", merged.keyword.trim());
  assign("datasourceId", merged.datasourceId);
  assign("database", merged.database);
  assign("domainId", merged.domainId);
  assign("tagId", merged.tagId);
  assign("unclassified", merged.unclassified ? "1" : "");
  assign("includeMissing", merged.includeMissing ? "1" : "");
  assign("pageNo", merged.pageNo > 1 ? String(merged.pageNo) : "");
  assign("pageSize", merged.pageSize !== DEFAULT_CATALOG_PAGE_SIZE ? String(merged.pageSize) : "");
  return next;
};

/**
 * Catalog filters live in the URL so a filtered view can be shared, and so the detail page links
 * back to the exact list the user left. `update` is stable: debounced writers depend on it.
 */
export function useCatalogQuery() {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = readQuery(searchParams);

  const update = useCallback(
    (patch: Partial<CatalogQuery>) => {
      setSearchParams(
        (previous) => {
          const merged = { ...readQuery(previous), ...patch };
          const filtersChanged = Object.keys(patch).some(
            (key) => key !== "pageNo" && key !== "pageSize",
          );
          if (filtersChanged) merged.pageNo = 1;
          return writeQuery(previous, merged);
        },
        { replace: true },
      );
    },
    [setSearchParams],
  );

  return { query, update };
}
