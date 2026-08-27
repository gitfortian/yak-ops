import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';
import type {
  DatasetField,
  DatasetFieldType,
  DatasetQueryPayload,
  DatasetQueryResult,
  PublishedDataset,
} from './types';

const DATASET_API = '/api/v1/datasets';

interface DatasetSummaryWire {
  id: string;
  name: string;
  description?: string | null;
  status: 'ONLINE' | 'OFFLINE';
  currentVersionId?: string | null;
  createTime?: string;
  updateTime?: string;
}

interface DatasetVersionWire {
  id: string;
  datasetId: string;
  versionNo: number;
  sourceType: 'QUERY_REVISION' | 'TABLE' | 'VIEW';
  sourceTaskAssetId: string;
  sourceTaskRevisionId: string;
  sourceTaskRevisionNo: number;
  schemaSnapshot?: string;
  createTime?: string;
}

interface DatasetFieldWire {
  fieldId: string;
  versionId: string;
  physicalName: string;
  displayName: string;
  dataType: 'STRING' | 'NUMBER' | 'DATE' | 'DATETIME' | 'BOOLEAN' | 'UNKNOWN';
  nullable: boolean;
  description?: string | null;
  defaultRole: 'DIMENSION' | 'MEASURE';
  sortOrder: number;
}

interface DatasetDetailWire {
  dataset: DatasetSummaryWire;
  currentVersion?: DatasetVersionWire | null;
  versions: DatasetVersionWire[];
  fields: DatasetFieldWire[];
}

export interface DatasetRequestOptions {
  /** Allows callers to cancel stale Dataset requests without changing the backend contract. */
  signal?: AbortSignal;
}

export type DatasetQueryOptions = DatasetRequestOptions;

export interface PublishedDatasetBatchResult {
  datasets: PublishedDataset[];
  errors: Record<string, string>;
}

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const requestOptions = (options?: DatasetRequestOptions) => (
  options?.signal ? { signal: options.signal } : undefined
);

const fieldType = (value: DatasetFieldWire['dataType']): DatasetFieldType => {
  switch (value) {
    case 'STRING': return 'string';
    case 'NUMBER': return 'number';
    case 'DATE': return 'date';
    case 'DATETIME': return 'datetime';
    case 'BOOLEAN': return 'boolean';
    default: return 'unknown';
  }
};

const toField = (field: DatasetFieldWire): DatasetField => ({
  key: field.fieldId,
  label: field.displayName || field.physicalName,
  physicalName: field.physicalName,
  dataType: fieldType(field.dataType),
  role: field.defaultRole === 'MEASURE' ? 'metric' : 'dimension',
  nullable: field.nullable,
  description: field.description || undefined,
});

const toDataset = (detail: DatasetDetailWire): PublishedDataset => {
  const version = detail.currentVersion;
  return {
    id: String(detail.dataset.id),
    name: detail.dataset.name,
    description: detail.dataset.description || '',
    status: detail.dataset.status,
    sourceTaskId: version ? String(version.sourceTaskAssetId) : '',
    sourceTaskName: version
      ? `SQL TaskAsset #${version.sourceTaskAssetId} · V${version.sourceTaskRevisionNo}`
      : '尚未绑定版本',
    currentVersionNo: version?.versionNo,
    updatedAt: detail.dataset.updateTime || detail.dataset.createTime || '',
    fields: (detail.fields || []).map(toField),
  };
};

const fetchDatasetDetail = async (
  datasetId: string,
  fallback: string,
  options?: DatasetRequestOptions,
) => unwrap(
  await HttpUtils.get<DatasetDetailWire>(
    `${DATASET_API}/${datasetId}`,
    requestOptions(options),
  ),
  fallback,
);

export const listPublishedDatasets = async (): Promise<PublishedDataset[]> => {
  const list = unwrap(
    await HttpUtils.get<DatasetSummaryWire[]>(DATASET_API),
    '查询 Dataset 列表失败',
  );
  const online = (list || []).filter((dataset) => dataset.status === 'ONLINE' && dataset.currentVersionId);
  if (!online.length) return [];
  const details = await Promise.allSettled(
    online.map((dataset) => fetchDatasetDetail(
      String(dataset.id),
      `查询 Dataset ${dataset.name} 详情失败`,
    )),
  );
  const available = details
    .filter((item): item is PromiseFulfilledResult<DatasetDetailWire> => item.status === 'fulfilled')
    .map((item) => toDataset(item.value));
  if (!available.length) {
    const rejected = details.find((item): item is PromiseRejectedResult => item.status === 'rejected');
    throw rejected?.reason instanceof Error ? rejected.reason : new Error('读取 Dataset 详情失败');
  }
  return available;
};

/** Fetches one currently published Dataset without first loading the entire Dataset catalog. */
export const getPublishedDataset = async (
  datasetId: string,
  options?: DatasetRequestOptions,
): Promise<PublishedDataset> => {
  const detail = await fetchDatasetDetail(datasetId, `查询 Dataset ${datasetId} 详情失败`, options);
  if (detail.dataset.status !== 'ONLINE' || !detail.currentVersion) {
    throw new Error(`Dataset ${datasetId} 当前未发布`);
  }
  return toDataset(detail);
};

/**
 * Viewer-oriented tolerant lookup: keep healthy Dataset metadata available while surfacing
 * failures for the specific ids that could not be resolved.
 */
export const resolvePublishedDatasetsByIds = async (
  datasetIds: string[],
  options?: DatasetRequestOptions,
): Promise<PublishedDatasetBatchResult> => {
  const uniqueIds = [...new Set(datasetIds.filter(Boolean))];
  if (!uniqueIds.length) return { datasets: [], errors: {} };

  const details = await Promise.allSettled(
    uniqueIds.map((datasetId) => getPublishedDataset(datasetId, options)),
  );
  const datasets: PublishedDataset[] = [];
  const errors: Record<string, string> = {};

  details.forEach((result, index) => {
    const datasetId = uniqueIds[index];
    if (result.status === 'fulfilled') {
      datasets.push(result.value);
      return;
    }
    errors[datasetId] = result.reason instanceof Error
      ? result.reason.message
      : `Dataset ${datasetId} 元数据加载失败`;
  });

  return { datasets, errors };
};

/** Compatibility convenience for callers that only need successfully resolved metadata. */
export const getPublishedDatasetsByIds = async (
  datasetIds: string[],
  options?: DatasetRequestOptions,
): Promise<PublishedDataset[]> => {
  const result = await resolvePublishedDatasetsByIds(datasetIds, options);
  if (!result.datasets.length && Object.keys(result.errors).length) {
    throw new Error(Object.values(result.errors)[0] || '读取大屏绑定 Dataset 失败');
  }
  return result.datasets;
};

export const queryDataset = async (
  datasetId: string,
  payload: DatasetQueryPayload,
  options?: DatasetQueryOptions,
): Promise<DatasetQueryResult> => unwrap(
  await HttpUtils.post<DatasetQueryResult>(
    `${DATASET_API}/${datasetId}/query`,
    payload,
    requestOptions(options),
  ),
  'Dataset 查询失败',
);

// Compatibility aliases for the Analysis module while callers migrate to the Dataset service vocabulary.
export const fetchAnalysisDatasets = listPublishedDatasets;
export const queryAnalysisDataset = queryDataset;
