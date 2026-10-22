import HttpUtils from "@/service/http/HttpUtils";
import { BizError, handleAuthenticationFailure } from "@/service/http/request";
import type {
  CategoryDraft,
  DeleteTarget,
  OwnerOption,
  SavedStandard,
  StandardCategory,
  StandardDraft,
  StandardInput,
  StandardPage,
  StandardQuery,
  StandardReferences,
} from "./types";

const API = "/api/v1/data-modeling/data-standards";

// Pages and dialogs render these failures beside their controls; authentication keeps the transport's handling.
async function standardRequest<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof BizError && (error.code === 401 || error.code === 2001)) {
      handleAuthenticationFailure();
    }
    throw error;
  }
}

export type StandardFailure =
  | { kind: "field"; field: "enCode" | "ownerId" | "lengthPrecision" }
  | { kind: "conflict" }
  | { kind: "missing" }
  | { kind: "forbidden" }
  | { kind: "unknown"; message: string };

/** Classifies a rejected request into the surface that must explain it. */
export function classifyStandardFailure(error: unknown): StandardFailure {
  if (!(error instanceof BizError)) return { kind: "unknown", message: "网络异常，请稍后重试" };
  switch (error.code) {
    case 19101:
    case 19102:
      return { kind: "field", field: "enCode" };
    case 19105:
      return { kind: "field", field: "ownerId" };
    case 19106:
      return { kind: "field", field: "lengthPrecision" };
    case 19104:
      return { kind: "conflict" };
    case 10010:
      return { kind: "missing" };
    case 42002:
      return { kind: "forbidden" };
    default:
      return { kind: "unknown", message: error.message || "操作失败，请稍后重试" };
  }
}

export const initializeCatalog = (signal?: AbortSignal) =>
  standardRequest(
    HttpUtils.postData<StandardCategory[]>(API + "/catalog/initialize", undefined, {
      signal,
      skipErrorHandler: true,
    }),
  );
export const getCategoryTree = (signal?: AbortSignal) =>
  standardRequest(
    HttpUtils.getData<StandardCategory[]>(API + "/categories/tree", {
      signal,
      skipErrorHandler: true,
    }),
  );
export const getOwners = (signal?: AbortSignal) =>
  standardRequest(
    HttpUtils.getData<OwnerOption[]>(API + "/owners", { signal, skipErrorHandler: true }),
  );
export function getStandards(query: StandardQuery, signal?: AbortSignal) {
  const params = new URLSearchParams({
    pageNo: String(query.pageNo),
    pageSize: String(query.pageSize),
  });
  if (query.scope === "CATEGORY") params.set("categoryId", query.categoryId);
  else params.set("keyword", query.keyword);
  return standardRequest(
    HttpUtils.getData<StandardPage>(API + "/fields?" + params, { signal, skipErrorHandler: true }),
  );
}
export function checkCodeUnique(enCode: string, excludeId?: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ enCode });
  if (excludeId) params.set("excludeId", excludeId);
  return standardRequest(
    HttpUtils.getData<{ available: boolean }>(API + "/fields/codes/availability?" + params, {
      signal,
      skipErrorHandler: true,
    }),
  );
}
export const checkStandardReferenced = (id: string, signal?: AbortSignal) =>
  standardRequest(
    HttpUtils.getData<StandardReferences>(
      API + "/fields/" + encodeURIComponent(id) + "/references",
      {
        signal,
        skipErrorHandler: true,
      },
    ),
  );
export function saveStandard(draft: StandardDraft, input: StandardInput) {
  return standardRequest(
    draft.mode === "edit"
      ? HttpUtils.putData<SavedStandard>(
          API + "/fields/" + encodeURIComponent(draft.standard.id),
          { ...input, revision: draft.standard.revision },
          { skipErrorHandler: true },
        )
      : HttpUtils.postData<SavedStandard>(
          API + "/categories/" + encodeURIComponent(draft.category.id) + "/fields",
          input,
          { skipErrorHandler: true },
        ),
  );
}
export function saveCategory(draft: CategoryDraft, name: string) {
  return standardRequest(
    draft.mode === "rename"
      ? HttpUtils.putData<SavedStandard>(
          API + "/categories/" + encodeURIComponent(draft.category.id),
          { name, revision: draft.category.revision },
          { skipErrorHandler: true },
        )
      : HttpUtils.postData<SavedStandard>(
          API + "/categories",
          { parentId: draft.parent?.id ?? null, name },
          { skipErrorHandler: true },
        ),
  );
}
export function deleteStandardTarget(target: DeleteTarget) {
  const resource = target.kind === "category" ? "categories" : "fields";
  return standardRequest(
    HttpUtils.deleteData<boolean>(
      API +
        "/" +
        resource +
        "/" +
        encodeURIComponent(target.node.id) +
        "?revision=" +
        target.node.revision,
      undefined,
      { skipErrorHandler: true },
    ),
  );
}
