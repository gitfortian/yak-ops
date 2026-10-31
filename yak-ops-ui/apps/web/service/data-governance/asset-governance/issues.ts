import HttpUtils from "@/service/http/HttpUtils";

import { governanceRequest } from "./request";
import type { GovernanceIssue, GovernanceIssueDetail, GovernanceIssueEvent } from "./types";

const API = "/api/v1/governance";

const withQuery = (path: string, parameters: object) => {
  const search = new URLSearchParams();
  Object.entries(parameters).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    search.set(key, String(value));
  });
  const query = search.toString();
  return query ? `${path}?${query}` : path;
};

export interface GovernanceIssuePageQuery {
  status?: string;
  severity?: string;
  dimension?: string;
  ruleCode?: string;
  ownerId?: string;
  targetType?: string;
  targetId?: string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export const pageIssues = (query: GovernanceIssuePageQuery, signal?: AbortSignal) =>
  governanceRequest(
    HttpUtils.getData<import("./types").Paging<GovernanceIssue>>(
      withQuery(`${API}/issues`, query),
      { signal, skipErrorHandler: true },
    ),
  );

export const getIssue = (issueId: string, signal?: AbortSignal) =>
  governanceRequest(
    HttpUtils.getData<GovernanceIssueDetail>(`${API}/issues/${encodeURIComponent(issueId)}`, {
      signal,
      skipErrorHandler: true,
    }),
  );

export const pageIssueEvents = (
  issueId: string,
  pageNo: number,
  pageSize: number,
  signal?: AbortSignal,
) =>
  governanceRequest(
    HttpUtils.getData<import("./types").Paging<GovernanceIssueEvent>>(
      `${API}/issues/${encodeURIComponent(issueId)}/events?pageNo=${pageNo}&pageSize=${pageSize}`,
      { signal, skipErrorHandler: true },
    ),
  );

export type GovernanceBatchCommand = "assign" | "resolve" | "reopen" | "ignore" | "unignore";

export interface GovernanceBatchInput {
  issueIds: string[];
  ownerId?: string;
  ignoreUntil?: string;
  note?: string;
}

/** One transaction per command; the server rejects invalid transitions atomically. */
export const submitBatch = (
  command: GovernanceBatchCommand,
  input: GovernanceBatchInput,
): Promise<number> =>
  governanceRequest(
    HttpUtils.postData<number>(`${API}/issues/${command}`, input, { skipErrorHandler: true }),
  );
