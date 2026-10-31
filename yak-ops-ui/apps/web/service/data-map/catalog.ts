import HttpUtils from "@/service/http/HttpUtils";

import { dataMapRequest } from "./request";
import type {
  CuratedUpdate,
  Overview,
  OverviewFacets,
  Paging,
  Preview,
  TableChange,
  TableDetail,
  TableQuery,
  TableSummary,
} from "./types";

const API = "/api/v1/data-map";

/** The server caps every asset count at 1000, so a total is rendered as a lower bound. */
const COUNT_CAP = 1000;

export const countLabel = (total: number) => (total >= COUNT_CAP ? `${COUNT_CAP}+` : String(total));

const withQuery = (path: string, parameters: object) => {
  const search = new URLSearchParams();
  Object.entries(parameters).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    // A repeated parameter, such as the tag value filter, is appended once per element.
    if (Array.isArray(value)) {
      value.forEach((item) => {
        if (item !== undefined && item !== null && item !== "") search.append(key, String(item));
      });
      return;
    }
    search.set(key, String(value));
  });
  const query = search.toString();
  return query ? `${path}?${query}` : path;
};

export const getOverview = (signal?: AbortSignal) =>
  dataMapRequest(
    HttpUtils.getData<Overview>(`${API}/overview`, { signal, skipErrorHandler: true }),
  );

export const getOverviewFacets = (signal?: AbortSignal) =>
  dataMapRequest(
    HttpUtils.getData<OverviewFacets>(`${API}/overview/facets`, {
      signal,
      skipErrorHandler: true,
    }),
  );

export const searchTables = (query: TableQuery, signal?: AbortSignal) =>
  dataMapRequest(
    HttpUtils.getData<Paging<TableSummary>>(withQuery(`${API}/tables`, query), {
      signal,
      skipErrorHandler: true,
    }),
  );

export const getTable = (tableId: string, signal?: AbortSignal) =>
  dataMapRequest(
    HttpUtils.getData<TableDetail>(`${API}/tables/${encodeURIComponent(tableId)}`, {
      signal,
      skipErrorHandler: true,
    }),
  );

export const updateTable = (tableId: string, input: CuratedUpdate) =>
  dataMapRequest(
    HttpUtils.putData<TableDetail>(`${API}/tables/${encodeURIComponent(tableId)}`, input, {
      skipErrorHandler: true,
    }),
  );

export const getTableChanges = (tableId: string, signal?: AbortSignal) =>
  dataMapRequest(
    HttpUtils.getData<TableChange[]>(`${API}/tables/${encodeURIComponent(tableId)}/changes`, {
      signal,
      skipErrorHandler: true,
    }),
  );

export const getTablePreview = (tableId: string, signal?: AbortSignal) =>
  dataMapRequest(
    HttpUtils.getData<Preview>(`${API}/tables/${encodeURIComponent(tableId)}/preview`, {
      signal,
      skipErrorHandler: true,
    }),
  );
