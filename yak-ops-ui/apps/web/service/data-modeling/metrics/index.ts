import HttpUtils from "@/service/http/HttpUtils";
import { BizError, handleAuthenticationFailure } from "@/service/http/request";
import type {
  MetricAtomic,
  MetricAtomicInput,
  MetricComposite,
  MetricCompositeInput,
  MetricDeleteTarget,
  MetricDerived,
  MetricDerivedInput,
  MetricModifier,
  MetricModifierInput,
  MetricReferences,
  MetricTimePeriod,
  MetricTimePeriodInput,
  Paged,
  SavedMetric,
} from "./types";

const API = "/api/v1/data-modeling/metrics";

// Pages and dialogs render these failures beside their controls; authentication keeps the transport's handling.
async function metricRequest<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof BizError && (error.code === 401 || error.code === 2001)) {
      handleAuthenticationFailure();
    }
    throw error;
  }
}

export type MetricFailure =
  | { kind: "field"; field: "enCode" | "ownerId" }
  | { kind: "duplicate" }
  | { kind: "conflict" }
  | { kind: "missing" }
  | { kind: "forbidden" }
  | { kind: "atomic" }
  | { kind: "derived" }
  | { kind: "expression" }
  | { kind: "referenced" }
  | { kind: "unknown"; message: string };

/** Classifies a rejected request into the surface that must explain it. */
export function classifyMetricFailure(error: unknown): MetricFailure {
  if (!(error instanceof BizError)) return { kind: "unknown", message: "网络异常，请稍后重试" };
  switch (error.code) {
    case 19301:
      return { kind: "field", field: "enCode" };
    case 19304:
      return { kind: "field", field: "ownerId" };
    case 19302:
      return { kind: "duplicate" };
    case 19303:
      return { kind: "conflict" };
    case 19305:
      return { kind: "atomic" };
    case 19306:
      return { kind: "derived" };
    case 19307:
      return { kind: "expression" };
    case 19308:
      return { kind: "referenced" };
    case 10010:
      return { kind: "missing" };
    case 42002:
      return { kind: "forbidden" };
    default:
      return { kind: "unknown", message: error.message || "操作失败，请稍后重试" };
  }
}

export type MetricQuery = {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  domainId?: string;
  processId?: string;
};

function pageParams(query: MetricQuery) {
  const params = new URLSearchParams({
    pageNo: String(query.pageNo),
    pageSize: String(query.pageSize),
  });
  if (query.keyword) params.set("keyword", query.keyword);
  if (query.domainId) params.set("domainId", query.domainId);
  if (query.processId) params.set("processId", query.processId);
  return params;
}

const idPath = (id: string) => encodeURIComponent(id);

// Time periods -----------------------------------------------------------------------------------------------
export const getTimePeriods = (query: MetricQuery, signal?: AbortSignal) =>
  metricRequest(
    HttpUtils.getData<Paged<MetricTimePeriod>>(API + "/time-periods?" + pageParams(query), {
      signal,
      skipErrorHandler: true,
    }),
  );
export function checkPeriodCode(enCode: string, excludeId?: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ enCode });
  if (excludeId) params.set("excludeId", excludeId);
  return metricRequest(
    HttpUtils.getData<{ available: boolean }>(API + "/time-periods/codes/availability?" + params, {
      signal,
      skipErrorHandler: true,
    }),
  );
}
export function savePeriod(
  draft: { mode: "create" } | { mode: "edit"; node: MetricTimePeriod },
  input: MetricTimePeriodInput,
) {
  return metricRequest(
    draft.mode === "edit"
      ? HttpUtils.putData<SavedMetric>(
          API + "/time-periods/" + idPath(draft.node.id),
          {
            ...input,
            revision: draft.node.revision,
          },
          { skipErrorHandler: true },
        )
      : HttpUtils.postData<SavedMetric>(API + "/time-periods", input, { skipErrorHandler: true }),
  );
}

// Modifiers ---------------------------------------------------------------------------------------------------
export const getModifiers = (query: MetricQuery, signal?: AbortSignal) =>
  metricRequest(
    HttpUtils.getData<Paged<MetricModifier>>(API + "/modifiers?" + pageParams(query), {
      signal,
      skipErrorHandler: true,
    }),
  );
export function checkModifierCode(enCode: string, excludeId?: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ enCode });
  if (excludeId) params.set("excludeId", excludeId);
  return metricRequest(
    HttpUtils.getData<{ available: boolean }>(API + "/modifiers/codes/availability?" + params, {
      signal,
      skipErrorHandler: true,
    }),
  );
}
export function saveModifier(
  draft: { mode: "create" } | { mode: "edit"; node: MetricModifier },
  input: MetricModifierInput,
) {
  return metricRequest(
    draft.mode === "edit"
      ? HttpUtils.putData<SavedMetric>(
          API + "/modifiers/" + idPath(draft.node.id),
          {
            ...input,
            revision: draft.node.revision,
          },
          { skipErrorHandler: true },
        )
      : HttpUtils.postData<SavedMetric>(API + "/modifiers", input, { skipErrorHandler: true }),
  );
}

// Atomic metrics ----------------------------------------------------------------------------------------------
export const getAtomics = (query: MetricQuery, signal?: AbortSignal) =>
  metricRequest(
    HttpUtils.getData<Paged<MetricAtomic>>(API + "/atomic-metrics?" + pageParams(query), {
      signal,
      skipErrorHandler: true,
    }),
  );
export function checkAtomicCode(enCode: string, excludeId?: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ enCode });
  if (excludeId) params.set("excludeId", excludeId);
  return metricRequest(
    HttpUtils.getData<{ available: boolean }>(
      API + "/atomic-metrics/codes/availability?" + params,
      { signal, skipErrorHandler: true },
    ),
  );
}
export function saveAtomic(
  draft: { mode: "create" } | { mode: "edit"; node: MetricAtomic },
  input: MetricAtomicInput,
) {
  return metricRequest(
    draft.mode === "edit"
      ? HttpUtils.putData<SavedMetric>(
          API + "/atomic-metrics/" + idPath(draft.node.id),
          {
            ...input,
            revision: draft.node.revision,
          },
          { skipErrorHandler: true },
        )
      : HttpUtils.postData<SavedMetric>(API + "/atomic-metrics", input, {
          skipErrorHandler: true,
        }),
  );
}

// Derived metrics ---------------------------------------------------------------------------------------------
export const getDerived = (query: MetricQuery, signal?: AbortSignal) =>
  metricRequest(
    HttpUtils.getData<Paged<MetricDerived>>(API + "/derived-metrics?" + pageParams(query), {
      signal,
      skipErrorHandler: true,
    }),
  );
export function checkDerivedAvailability(
  atomicMetricId: string,
  periodId: string,
  modifierIds: string[],
  excludeId?: string,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({ atomicMetricId, periodId });
  for (const modifierId of modifierIds) params.append("modifierIds", modifierId);
  if (excludeId) params.set("excludeId", excludeId);
  return metricRequest(
    HttpUtils.getData<{ available: boolean }>(
      API + "/derived-metrics/codes/availability?" + params,
      { signal, skipErrorHandler: true },
    ),
  );
}
export function saveDerived(
  draft: { mode: "create" } | { mode: "edit"; node: MetricDerived },
  input: MetricDerivedInput,
) {
  return metricRequest(
    draft.mode === "edit"
      ? HttpUtils.putData<SavedMetric>(
          API + "/derived-metrics/" + idPath(draft.node.id),
          {
            ...input,
            revision: draft.node.revision,
          },
          { skipErrorHandler: true },
        )
      : HttpUtils.postData<SavedMetric>(API + "/derived-metrics", input, {
          skipErrorHandler: true,
        }),
  );
}

// Composite metrics -------------------------------------------------------------------------------------------
export const getComposites = (query: MetricQuery, signal?: AbortSignal) =>
  metricRequest(
    HttpUtils.getData<Paged<MetricComposite>>(API + "/composite-metrics?" + pageParams(query), {
      signal,
      skipErrorHandler: true,
    }),
  );
export function checkCompositeCode(enCode: string, excludeId?: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ enCode });
  if (excludeId) params.set("excludeId", excludeId);
  return metricRequest(
    HttpUtils.getData<{ available: boolean }>(
      API + "/composite-metrics/codes/availability?" + params,
      { signal, skipErrorHandler: true },
    ),
  );
}
export function saveComposite(
  draft: { mode: "create" } | { mode: "edit"; node: MetricComposite },
  input: MetricCompositeInput,
) {
  return metricRequest(
    draft.mode === "edit"
      ? HttpUtils.putData<SavedMetric>(
          API + "/composite-metrics/" + idPath(draft.node.id),
          {
            ...input,
            revision: draft.node.revision,
          },
          { skipErrorHandler: true },
        )
      : HttpUtils.postData<SavedMetric>(API + "/composite-metrics", input, {
          skipErrorHandler: true,
        }),
  );
}

// Deletion and references -------------------------------------------------------------------------------------
const RESOURCE: Record<MetricDeleteTarget["kind"], string> = {
  period: "time-periods",
  modifier: "modifiers",
  atomic: "atomic-metrics",
  derived: "derived-metrics",
  composite: "composite-metrics",
};
export function deleteMetric(target: MetricDeleteTarget) {
  return metricRequest(
    HttpUtils.deleteData<boolean>(
      API +
        "/" +
        RESOURCE[target.kind] +
        "/" +
        idPath(target.node.id) +
        "?revision=" +
        target.node.revision,
      undefined,
      { skipErrorHandler: true },
    ),
  );
}
/** Reads the delete dialog's reference panel; a composite stores none. */
export function getMetricReferences(
  kind: MetricDeleteTarget["kind"],
  id: string,
  signal?: AbortSignal,
): Promise<MetricReferences | null> {
  if (kind === "composite") return Promise.resolve(null);
  return metricRequest(
    HttpUtils.getData<MetricReferences>(
      API + "/" + RESOURCE[kind] + "/" + idPath(id) + "/references",
      { signal, skipErrorHandler: true },
    ),
  );
}
