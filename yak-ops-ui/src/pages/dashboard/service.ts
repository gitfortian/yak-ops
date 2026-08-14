import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';
import type {
  DatasetField,
  DatasetFieldType,
  DatasetQueryPayload,
  DatasetQueryResult,
  PublishedDataset,
} from './model';

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

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

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

export const fetchDashboardDatasets = async (): Promise<PublishedDataset[]> => {
  const list = unwrap(
    await HttpUtils.get<DatasetSummaryWire[]>(DATASET_API),
    '查询 Dataset 列表失败',
  );
  const online = (list || []).filter((dataset) => dataset.status === 'ONLINE' && dataset.currentVersionId);
  const details = await Promise.all(
    online.map(async (dataset) => unwrap(
      await HttpUtils.get<DatasetDetailWire>(`${DATASET_API}/${dataset.id}`),
      `查询 Dataset ${dataset.name} 详情失败`,
    )),
  );
  return details.map(toDataset);
};

export const queryDashboardDataset = async (
  datasetId: string,
  payload: DatasetQueryPayload,
): Promise<DatasetQueryResult> => unwrap(
  await HttpUtils.post<DatasetQueryResult>(`${DATASET_API}/${datasetId}/query`, payload),
  'Dataset 查询失败',
);
