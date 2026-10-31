import HttpUtils from "@/service/http/HttpUtils";

import { governanceRequest } from "./request";
import type { GovernanceScan, GovernanceScanDetail } from "./types";

const API = "/api/v1/governance";

export const pageScans = (pageNo: number, pageSize: number, signal?: AbortSignal) =>
  governanceRequest(
    HttpUtils.getData<import("./types").Paging<GovernanceScan>>(
      `${API}/scans?pageNo=${pageNo}&pageSize=${pageSize}`,
      { signal, skipErrorHandler: true },
    ),
  );

export const getScan = (scanId: string, signal?: AbortSignal) =>
  governanceRequest(
    HttpUtils.getData<GovernanceScanDetail>(`${API}/scans/${encodeURIComponent(scanId)}`, {
      signal,
      skipErrorHandler: true,
    }),
  );

/** Returns 202 with the queued run; the result surfaces through the next overview poll. */
export const submitScan = (): Promise<GovernanceScan> =>
  governanceRequest(
    HttpUtils.postData<GovernanceScan>(`${API}/scans`, undefined, { skipErrorHandler: true }),
  );
