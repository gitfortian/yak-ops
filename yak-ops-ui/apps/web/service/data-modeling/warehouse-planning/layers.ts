import HttpUtils from "@/service/http/HttpUtils";
import { BizError } from "@/service/http/request";

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

export const getLayers = (signal?: AbortSignal) => HttpUtils.getData<Layer[]>(API, { signal });

export const getLayerDataSources = (signal?: AbortSignal) =>
  HttpUtils.getData<LayerDataSource[]>(`${API}/datasources`, { signal });

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
    ? HttpUtils.putData<Layer>(`${API}/${encodeURIComponent(layer.id)}`, {
        ...input,
        revision: layer.revision,
      })
    : HttpUtils.postData<Layer>(API, input);
}

export const deleteLayer = (layer: Layer) =>
  HttpUtils.deleteData<boolean>(
    `${API}/${encodeURIComponent(layer.id)}?revision=${layer.revision}`,
  );
