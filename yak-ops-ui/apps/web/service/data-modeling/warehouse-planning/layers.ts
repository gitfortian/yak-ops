import HttpUtils from "@/service/http/HttpUtils";
import { BizError, handleAuthenticationFailure } from "@/service/http/request";

const API = "/api/v1/data-modeling/warehouse-planning/layers";

export interface Layer {
  id: string;
  enCode: string;
  cnName: string;
  dataSourceId: string | null;
  dataSourceName: string | null;
  description: string | null;
  enabled: boolean;
  builtin: boolean;
  namingRuleEnabled: boolean;
  sortNo: number;
  revision: number;
}

export interface LayerInput {
  cnName: string;
  enCode: string;
  dataSourceId: string | null;
  description: string | null;
  enabled: boolean;
}

export interface LayerDataSource {
  id: string;
  name: string;
  dbType: string;
  environment: string | null;
  connStatus: string | null;
}

export type LayerFailureField = "enCode" | "revision" | "dataSourceId";

/**
 * Keeps the session-expiry redirect that the shared handler would otherwise perform, and leaves
 * every other failure to the caller so one failure renders one message instead of a toast plus
 * an inline alert.
 */
async function layerRequest<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof BizError && (error.code === 401 || error.code === 2001)) {
      handleAuthenticationFailure();
    }
    throw error;
  }
}

/** Maps stable layer error codes to the field the user must correct. */
export function layerFailureField(error: unknown): LayerFailureField | null {
  if (!(error instanceof BizError)) return null;
  switch (error.code) {
    case 19202:
      return "enCode";
    case 19205:
      return "revision";
    case 19206:
    case 19207:
      return "dataSourceId";
    default:
      return null;
  }
}

/** Returns the server business message for layer failures, or a generic fallback. */
export function layerFailureMessage(error: unknown) {
  return error instanceof BizError && error.message ? error.message : "操作失败，请稍后重试";
}

/** Reports whether a resolved connection status means publishing through this datasource will fail. */
export function isDatasourceUnavailable(source: LayerDataSource | undefined) {
  return source?.connStatus === "DISCONNECTED";
}

export const getLayers = (signal?: AbortSignal) =>
  layerRequest(HttpUtils.getData<Layer[]>(API, { signal, skipErrorHandler: true }));

export const getLayerDataSources = (signal?: AbortSignal) =>
  layerRequest(
    HttpUtils.getData<LayerDataSource[]>(`${API}/datasources`, { signal, skipErrorHandler: true }),
  );

/** One per-environment physical target bound to a layer. */
export interface LayerTarget {
  id: string;
  layerId: string;
  environment: "DEVELOP" | "PROD";
  dataSourceId: string;
  dataSourceName: string | null;
  database: string;
  schema: string | null;
}

/** One submitted binding; {@code schema} is null for engines that have no separate schema. */
export interface LayerTargetInput {
  environment: string;
  dataSourceId: string;
  database: string;
  schema: string | null;
}

export const getLayerTargets = (layerId: string, signal?: AbortSignal) =>
  layerRequest(
    HttpUtils.getData<LayerTarget[]>(`${API}/${encodeURIComponent(layerId)}/targets`, {
      signal,
      skipErrorHandler: true,
    }),
  );

export function saveLayerTargets(layerId: string, targets: LayerTargetInput[]) {
  return layerRequest(
    HttpUtils.putData<LayerTarget[]>(
      `${API}/${encodeURIComponent(layerId)}/targets`,
      { targets },
      {
        skipErrorHandler: true,
      },
    ),
  );
}

export function checkLayerCode(enCode: string, excludeId?: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ enCode });
  if (excludeId) query.set("excludeId", excludeId);
  return HttpUtils.getData<{ available: boolean }>(`${API}/codes/availability?${query}`, {
    signal,
    skipErrorHandler: true,
  });
}

export function saveLayer(layer: Layer | null, input: LayerInput) {
  return layer
    ? layerRequest(
        HttpUtils.putData<Layer>(
          `${API}/${encodeURIComponent(layer.id)}`,
          { ...input, revision: layer.revision },
          { skipErrorHandler: true },
        ),
      )
    : layerRequest(HttpUtils.postData<Layer>(API, input, { skipErrorHandler: true }));
}

export const deleteLayer = (layer: Layer) =>
  layerRequest(
    HttpUtils.deleteData<boolean>(
      `${API}/${encodeURIComponent(layer.id)}?revision=${layer.revision}`,
      undefined,
      { skipErrorHandler: true },
    ),
  );

export interface NamingViolation {
  modelId: string;
  name: string;
  cnName: string;
  type: string;
  processCode: string | null;
}

export interface LayerNamingRule {
  layerId: string;
  enabled: boolean;
  revision: number;
  violations: NamingViolation[];
}

/** Reads one layer's naming rule together with the models that currently violate it. */
export const getNamingRule = (layerId: string, signal?: AbortSignal) =>
  layerRequest(
    HttpUtils.getData<LayerNamingRule>(`${API}/${encodeURIComponent(layerId)}/naming-rule`, {
      signal,
      skipErrorHandler: true,
    }),
  );

export function updateNamingRule(
  layerId: string,
  input: { enabled: boolean; acknowledgeViolations: boolean; revision: number },
) {
  return layerRequest(
    HttpUtils.putData<LayerNamingRule>(`${API}/${encodeURIComponent(layerId)}/naming-rule`, input, {
      skipErrorHandler: true,
    }),
  );
}
