import HttpUtils from "@/service/http/HttpUtils";

import { dataMapRequest } from "./request";
import type { Paging, TableSummary } from "./types";

const API = "/api/v1/data-map";

export const listFavorites = (pageNo: number, pageSize: number, signal?: AbortSignal) =>
  dataMapRequest(
    HttpUtils.getData<Paging<TableSummary>>(
      `${API}/favorites?pageNo=${pageNo}&pageSize=${pageSize}`,
      {
        signal,
        skipErrorHandler: true,
      },
    ),
  );

export const listRecent = (pageNo: number, pageSize: number, signal?: AbortSignal) =>
  dataMapRequest(
    HttpUtils.getData<Paging<TableSummary>>(`${API}/recent?pageNo=${pageNo}&pageSize=${pageSize}`, {
      signal,
      skipErrorHandler: true,
    }),
  );

export const addFavorite = (tableId: string) =>
  dataMapRequest(
    HttpUtils.postData<boolean>(
      `${API}/tables/${encodeURIComponent(tableId)}/favorites`,
      undefined,
      { skipErrorHandler: true },
    ),
  );

export const removeFavorite = (tableId: string) =>
  dataMapRequest(
    HttpUtils.deleteData<boolean>(
      `${API}/tables/${encodeURIComponent(tableId)}/favorites`,
      undefined,
      { skipErrorHandler: true },
    ),
  );

/** Records one intentional asset visit; reads of the detail payload do not count. */
export const recordView = (tableId: string) =>
  dataMapRequest(
    HttpUtils.postData<boolean>(`${API}/tables/${encodeURIComponent(tableId)}/views`, undefined, {
      skipErrorHandler: true,
    }),
  );
