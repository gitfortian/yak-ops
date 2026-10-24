import HttpUtils from "@/service/http/HttpUtils";
import { BizError, handleAuthenticationFailure } from "@/service/http/request";
import type {
  ConceptDimension,
  DimensionFailure,
  DimensionFilter,
  DimensionInput,
  LogicalModel,
  ModelFailure,
  ModelInput,
  ModelPage,
  ModelQuery,
} from "./types";

const API = "/api/v1/data-modeling/dimension-modeling";

// Pages and dialogs render these failures beside their controls; authentication keeps the transport's handling.
async function modelingRequest<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof BizError && (error.code === 401 || error.code === 2001)) {
      handleAuthenticationFailure();
    }
    throw error;
  }
}

const message = (error: unknown) =>
  error instanceof BizError && error.message ? error.message : "操作失败，请稍后重试";

/** Maps stable model error codes to the section the user must correct. */
export function classifyModelFailure(error: unknown): ModelFailure {
  if (!(error instanceof BizError)) return { kind: "unknown", message: "网络异常，请稍后重试" };
  switch (error.code) {
    case 19504:
      return { kind: "conflict" };
    case 19506:
    case 19511:
      return { kind: "field", field: "fields", message: message(error) };
    case 19507:
      return { kind: "field", field: "ownership", message: message(error) };
    case 19508:
    case 19512:
      return { kind: "field", field: "nameSuffix", message: message(error) };
    case 19509:
      return { kind: "field", field: "layer", message: message(error) };
    case 19510:
      return { kind: "unknown-type" };
    case 10010:
      return { kind: "missing" };
    default:
      return { kind: "unknown", message: message(error) };
  }
}

/** Maps stable dimension error codes to the control the user must correct. */
export function classifyDimensionFailure(error: unknown): DimensionFailure {
  if (!(error instanceof BizError)) return { kind: "unknown", message: "网络异常，请稍后重试" };
  switch (error.code) {
    case 19501:
    case 19502:
      return { kind: "field", field: "code", message: message(error) };
    case 19505:
      return { kind: "field", field: "scope", message: message(error) };
    case 19504:
      return { kind: "conflict" };
    case 19503:
      return { kind: "referenced" };
    case 10010:
      return { kind: "missing" };
    default:
      return { kind: "unknown", message: message(error) };
  }
}

export function getModels(query: ModelQuery, signal?: AbortSignal) {
  const params = new URLSearchParams({
    pageNo: String(query.pageNo),
    pageSize: String(query.pageSize),
  });
  if (query.type !== "ALL") params.set("type", query.type);
  if (query.layerId) params.set("layerId", query.layerId);
  if (query.keyword) params.set("keyword", query.keyword);
  if (query.ownerId) params.set("ownerId", query.ownerId);
  if (query.domainId) params.set("domainId", query.domainId);
  if (query.datamartId) params.set("datamartId", query.datamartId);
  return modelingRequest(
    HttpUtils.getData<ModelPage>(`${API}/models?${params}`, { signal, skipErrorHandler: true }),
  );
}

export const getModel = (id: string, signal?: AbortSignal) =>
  modelingRequest(
    HttpUtils.getData<LogicalModel>(`${API}/models/${encodeURIComponent(id)}`, {
      signal,
      skipErrorHandler: true,
    }),
  );

const CATALOG_PAGE_SIZE = 100;
const CATALOG_PAGE_LIMIT = 10;

export interface ModelCatalog {
  items: LogicalModel[];
  truncated: boolean;
}

/** Reads every model in pages so the tree can group them without a request per node. */
export async function getModelCatalog(signal?: AbortSignal): Promise<ModelCatalog> {
  const items: LogicalModel[] = [];
  for (let pageNo = 1; pageNo <= CATALOG_PAGE_LIMIT; pageNo += 1) {
    const page = await getModels(
      { type: "ALL", layerId: "", keyword: "", pageNo, pageSize: CATALOG_PAGE_SIZE },
      signal,
    );
    items.push(...page.bizData);
    if (pageNo >= page.pagination.pages) return { items, truncated: false };
  }
  return { items, truncated: true };
}

export function checkModelName(
  layerId: string,
  nameSuffix: string,
  excludeId?: string,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({ layerId, nameSuffix });
  if (excludeId) params.set("excludeId", excludeId);
  return modelingRequest(
    HttpUtils.getData<boolean>(`${API}/models/name-availability?${params}`, {
      signal,
      skipErrorHandler: true,
    }),
  );
}

/** Creates or replaces a model; a replacement carries the revision the editor observed. */
export function saveModel(id: string | null, input: ModelInput, revision?: number) {
  return id
    ? modelingRequest(
        HttpUtils.putData<LogicalModel>(
          `${API}/models/${encodeURIComponent(id)}`,
          { ...input, revision },
          { skipErrorHandler: true },
        ),
      )
    : modelingRequest(
        HttpUtils.postData<LogicalModel>(`${API}/models`, input, { skipErrorHandler: true }),
      );
}

export const deleteModel = (model: LogicalModel) =>
  modelingRequest(
    HttpUtils.deleteData<boolean>(
      `${API}/models/${encodeURIComponent(model.id)}?revision=${model.revision}`,
      undefined,
      { skipErrorHandler: true },
    ),
  );

export function getDimensions(filter: DimensionFilter, signal?: AbortSignal) {
  const params = new URLSearchParams();
  if (filter.scope !== "ALL") params.set("scope", filter.scope);
  if (filter.scopeId) {
    if (filter.scope === "PUBLIC") params.set("domainId", filter.scopeId);
    else if (filter.scope === "APPLICATION") params.set("datamartId", filter.scopeId);
  }
  const suffix = params.toString();
  return modelingRequest(
    HttpUtils.getData<ConceptDimension[]>(`${API}/dimensions${suffix ? `?${suffix}` : ""}`, {
      signal,
      skipErrorHandler: true,
    }),
  );
}

export function saveDimension(existing: ConceptDimension | null, input: DimensionInput) {
  return existing
    ? modelingRequest(
        HttpUtils.putData<ConceptDimension>(
          `${API}/dimensions/${encodeURIComponent(existing.id)}`,
          { ...input, revision: existing.revision },
          { skipErrorHandler: true },
        ),
      )
    : modelingRequest(
        HttpUtils.postData<ConceptDimension>(`${API}/dimensions`, input, {
          skipErrorHandler: true,
        }),
      );
}

export const deleteDimension = (dimension: ConceptDimension) =>
  modelingRequest(
    HttpUtils.deleteData<boolean>(
      `${API}/dimensions/${encodeURIComponent(dimension.id)}?revision=${dimension.revision}`,
      undefined,
      { skipErrorHandler: true },
    ),
  );
