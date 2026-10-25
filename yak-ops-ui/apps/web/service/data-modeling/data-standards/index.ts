import HttpUtils from "@/service/http/HttpUtils";
import { BizError, handleAuthenticationFailure } from "@/service/http/request";
import type {
  CategoryDraft,
  DeleteTarget,
  KindQuery,
  MeasureUnitCategory,
  OwnerOption,
  Paged,
  SavedStandard,
  StandardCategory,
  StandardDraft,
  StandardInput,
  StandardKind,
  StandardLookup,
  StandardLookupInput,
  StandardMeasureUnit,
  StandardMeasureUnitInput,
  StandardNamingEntry,
  StandardNamingEntryInput,
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
  | {
      kind: "field";
      field: "enCode" | "ownerId" | "lengthPrecision" | "parentStandardId";
    }
  | { kind: "conflict" }
  | { kind: "missing" }
  | { kind: "forbidden" }
  | { kind: "values" }
  | { kind: "kindLocked" }
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
    case 19109:
      return { kind: "field", field: "parentStandardId" };
    case 19110:
      return { kind: "values" };
    case 19111:
      return { kind: "kindLocked" };
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

function pageParams(query: KindQuery) {
  const params = new URLSearchParams({
    pageNo: String(query.pageNo),
    pageSize: String(query.pageSize),
  });
  if (query.scope === "CATEGORY") params.set("categoryId", query.categoryId);
  else if (query.keyword) params.set("keyword", query.keyword);
  return params;
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
export function checkReferences(
  kind: DeleteTarget["kind"],
  id: string,
  signal?: AbortSignal,
): Promise<StandardReferences> {
  const resource =
    kind === "field"
      ? "fields"
      : kind === "lookup"
        ? "lookups"
        : kind === "unit"
          ? "measure-units"
          : "naming-entries";
  return standardRequest(
    HttpUtils.getData<StandardReferences>(
      API + "/" + resource + "/" + encodeURIComponent(id) + "/references",
      { signal, skipErrorHandler: true },
    ),
  );
}
export const checkStandardReferenced = (id: string, signal?: AbortSignal) =>
  checkReferences("field", id, signal);
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
export function saveCategory(
  draft: CategoryDraft,
  input: { name: string; nodeType?: string; standardKind?: StandardKind | null },
) {
  return standardRequest(
    draft.mode === "rename"
      ? HttpUtils.putData<SavedStandard>(
          API + "/categories/" + encodeURIComponent(draft.category.id),
          { ...input, revision: draft.category.revision },
          { skipErrorHandler: true },
        )
      : HttpUtils.postData<SavedStandard>(
          API + "/categories",
          { ...input, parentId: draft.parent?.id ?? null },
          { skipErrorHandler: true },
        ),
  );
}

export function getLookups(query: KindQuery, signal?: AbortSignal) {
  return standardRequest(
    HttpUtils.getData<Paged<StandardLookup>>(API + "/lookups?" + pageParams(query), {
      signal,
      skipErrorHandler: true,
    }),
  );
}
export const getLookup = (id: string, signal?: AbortSignal) =>
  standardRequest(
    HttpUtils.getData<StandardLookup>(API + "/lookups/" + encodeURIComponent(id), {
      signal,
      skipErrorHandler: true,
    }),
  );
export function saveLookup(
  draft: { mode: "create"; categoryId: string } | { mode: "edit"; lookup: StandardLookup },
  input: StandardLookupInput,
) {
  return standardRequest(
    draft.mode === "edit"
      ? HttpUtils.putData<SavedStandard>(
          API + "/lookups/" + encodeURIComponent(draft.lookup.id),
          { ...input, revision: draft.lookup.revision },
          { skipErrorHandler: true },
        )
      : HttpUtils.postData<SavedStandard>(
          API + "/categories/" + encodeURIComponent(draft.categoryId) + "/lookups",
          input,
          { skipErrorHandler: true },
        ),
  );
}

export function getMeasureUnits(
  category: MeasureUnitCategory | null,
  keyword: string,
  pageNo: number,
  pageSize: number,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) });
  if (category) params.set("category", category);
  if (keyword) params.set("keyword", keyword);
  return standardRequest(
    HttpUtils.getData<Paged<StandardMeasureUnit>>(API + "/measure-units?" + params, {
      signal,
      skipErrorHandler: true,
    }),
  );
}
export function saveMeasureUnit(
  draft: { mode: "create" } | { mode: "edit"; unit: StandardMeasureUnit },
  input: StandardMeasureUnitInput,
) {
  return standardRequest(
    draft.mode === "edit"
      ? HttpUtils.putData<SavedStandard>(
          API + "/measure-units/" + encodeURIComponent(draft.unit.id),
          { ...input, revision: draft.unit.revision },
          { skipErrorHandler: true },
        )
      : HttpUtils.postData<SavedStandard>(API + "/measure-units", input, {
          skipErrorHandler: true,
        }),
  );
}

export function getNamingEntries(
  keyword: string,
  pageNo: number,
  pageSize: number,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) });
  if (keyword) params.set("keyword", keyword);
  return standardRequest(
    HttpUtils.getData<Paged<StandardNamingEntry>>(API + "/naming-entries?" + params, {
      signal,
      skipErrorHandler: true,
    }),
  );
}
export function saveNamingEntry(
  draft: { mode: "create" } | { mode: "edit"; entry: StandardNamingEntry },
  input: StandardNamingEntryInput,
) {
  return standardRequest(
    draft.mode === "edit"
      ? HttpUtils.putData<SavedStandard>(
          API + "/naming-entries/" + encodeURIComponent(draft.entry.id),
          { ...input, revision: draft.entry.revision },
          { skipErrorHandler: true },
        )
      : HttpUtils.postData<SavedStandard>(API + "/naming-entries", input, {
          skipErrorHandler: true,
        }),
  );
}

const RESOURCE: Record<Exclude<DeleteTarget["kind"], "category">, string> = {
  field: "fields",
  lookup: "lookups",
  unit: "measure-units",
  naming: "naming-entries",
};
export function deleteStandardTarget(target: DeleteTarget) {
  const resource = target.kind === "category" ? "categories" : RESOURCE[target.kind];
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
