import type { DataSyncSolutionType, DataSyncTaskStatus } from "@/service/data-sync";

export interface TaskListFilters {
  pageNo: number;
  keyword: string;
  solutionType: DataSyncSolutionType | "ALL";
  status: DataSyncTaskStatus | "ALL";
}

const SOLUTIONS = new Set(["SINGLE_TABLE_OFFLINE", "SINGLE_TABLE_REALTIME", "DATABASE_OFFLINE", "DATABASE_REALTIME"]);

export function parseTaskListFilters(search: URLSearchParams): TaskListFilters {
  const rawPage = Number(search.get("page"));
  const value = search.get("solutionType");
  const status = search.get("status");
  return {
    pageNo: Number.isSafeInteger(rawPage) && rawPage > 0 ? rawPage : 1,
    keyword: search.get("keyword")?.trim() || "",
    solutionType: value && SOLUTIONS.has(value) ? (value as DataSyncSolutionType) : "ALL",
    status: status === "PUBLISHED" || status === "UNPUBLISHED" ? status : "ALL",
  };
}

export function updateTaskListFilters(current: URLSearchParams, patch: Partial<TaskListFilters>): URLSearchParams {
  const next = new URLSearchParams(current);
  const state = { ...parseTaskListFilters(current), ...patch };
  if (state.pageNo > 1) next.set("page", String(state.pageNo));
  else next.delete("page");
  if (state.keyword.trim()) next.set("keyword", state.keyword.trim());
  else next.delete("keyword");
  if (state.solutionType !== "ALL") next.set("solutionType", state.solutionType);
  else next.delete("solutionType");
  if (state.status !== "ALL") next.set("status", state.status);
  else next.delete("status");
  return next;
}
