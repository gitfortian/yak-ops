import HttpUtils from "@/service/http/HttpUtils";
import { BizError } from "@/service/http/request";
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
export function standardFailureField(error: unknown) {
  if (!(error instanceof BizError)) return null;
  switch (error.code) {
    case 19101:
    case 19102:
      return "enCode";
    case 19105:
      return "ownerId";
    case 19106:
      return "lengthPrecision";
    case 19104:
      return "revision";
    default:
      return null;
  }
}
export const initializeCatalog = (signal?: AbortSignal) =>
  HttpUtils.postData<StandardCategory[]>(API + "/catalog/initialize", undefined, { signal });
export const getCategoryTree = (signal?: AbortSignal) =>
  HttpUtils.getData<StandardCategory[]>(API + "/categories/tree", { signal });
export const getOwners = (signal?: AbortSignal) =>
  HttpUtils.getData<OwnerOption[]>(API + "/owners", { signal });
export function getStandards(query: StandardQuery, signal?: AbortSignal) {
  const params = new URLSearchParams({
    pageNo: String(query.pageNo),
    pageSize: String(query.pageSize),
  });
  if (query.scope === "CATEGORY") params.set("categoryId", query.categoryId);
  else params.set("keyword", query.keyword);
  return HttpUtils.getData<StandardPage>(API + "/fields?" + params, { signal });
}
export function checkCodeUnique(enCode: string, excludeId?: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ enCode });
  if (excludeId) params.set("excludeId", excludeId);
  return HttpUtils.getData<{ available: boolean }>(API + "/fields/codes/availability?" + params, {
    signal,
  });
}
export const checkStandardReferenced = (id: string, signal?: AbortSignal) =>
  HttpUtils.getData<StandardReferences>(API + "/fields/" + encodeURIComponent(id) + "/references", {
    signal,
  });
export function saveStandard(draft: StandardDraft, input: StandardInput) {
  return draft.mode === "edit"
    ? HttpUtils.putData<SavedStandard>(API + "/fields/" + encodeURIComponent(draft.standard.id), {
        ...input,
        revision: draft.standard.revision,
      })
    : HttpUtils.postData<SavedStandard>(
        API + "/categories/" + encodeURIComponent(draft.category.id) + "/fields",
        input,
      );
}
export function saveCategory(draft: CategoryDraft, name: string) {
  return draft.mode === "rename"
    ? HttpUtils.putData<SavedStandard>(
        API + "/categories/" + encodeURIComponent(draft.category.id),
        { name, revision: draft.category.revision },
      )
    : HttpUtils.postData<SavedStandard>(API + "/categories", {
        parentId: draft.parent?.id ?? null,
        name,
      });
}
export function deleteStandardTarget(target: DeleteTarget) {
  const resource = target.kind === "category" ? "categories" : "fields";
  return HttpUtils.deleteData<boolean>(
    API +
      "/" +
      resource +
      "/" +
      encodeURIComponent(target.node.id) +
      "?revision=" +
      target.node.revision,
  );
}
