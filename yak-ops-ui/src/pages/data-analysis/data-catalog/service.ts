import type { AnalysisAsset } from '@/components/analysis/model';
import { fetchAnalyses } from '@/components/analysis/analysis-service';
import {
  listDevelopmentDirectories,
  listDevelopmentNodes,
  listDevelopmentReleases,
} from '@/pages/data-development/service';
import type {
  DevelopmentDirectory,
  DevelopmentNode,
  DevelopmentReleaseSummary,
} from '@/pages/data-development/types';
import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

const DATASET_API = '/api/v1/datasets';
const RELEASE_PAGE_SIZE = 100;

type WireId = string | number;

export type CatalogDatasetStatus = 'ONLINE' | 'OFFLINE';
export type CatalogDatasetSourceType = 'QUERY_REVISION' | 'TABLE' | 'VIEW';
export type CatalogDatasetFieldRole = 'DIMENSION' | 'MEASURE';
export type CatalogDatasetFieldType =
  | 'STRING'
  | 'NUMBER'
  | 'DATE'
  | 'DATETIME'
  | 'BOOLEAN'
  | 'UNKNOWN';

interface DatasetSummaryWire {
  id: WireId;
  name: string;
  description?: string | null;
  status: CatalogDatasetStatus;
  currentVersionId?: WireId | null;
  createTime?: string;
  updateTime?: string;
}

interface DatasetVersionWire {
  id: WireId;
  datasetId: WireId;
  versionNo: number;
  sourceType: CatalogDatasetSourceType;
  sourceTaskAssetId: WireId;
  sourceTaskRevisionId: WireId;
  sourceTaskRevisionNo: number;
  schemaSnapshot?: string | null;
  createTime?: string;
}

interface DatasetFieldWire {
  fieldId: string;
  versionId: WireId;
  physicalName: string;
  displayName: string;
  dataType: CatalogDatasetFieldType;
  nullable: boolean;
  description?: string | null;
  defaultRole: CatalogDatasetFieldRole;
  sortOrder: number;
}

interface DatasetDetailWire {
  dataset: DatasetSummaryWire;
  currentVersion?: DatasetVersionWire | null;
  versions?: DatasetVersionWire[];
  fields?: DatasetFieldWire[];
}

export interface CatalogDirectory {
  id: string;
  parentId?: string;
  name: string;
  path: string;
}

export interface CatalogDatasetVersion {
  id: string;
  versionNo: number;
  sourceType: CatalogDatasetSourceType;
  sourceTaskAssetId: string;
  sourceTaskRevisionId: string;
  sourceTaskRevisionNo: number;
  createTime?: string;
}

export interface CatalogDatasetField {
  fieldId: string;
  physicalName: string;
  displayName: string;
  dataType: CatalogDatasetFieldType;
  nullable: boolean;
  description?: string;
  defaultRole: CatalogDatasetFieldRole;
  sortOrder: number;
}

export interface CatalogDataset {
  id: string;
  name: string;
  description: string;
  status: CatalogDatasetStatus;
  currentVersionId?: string;
  currentVersion?: CatalogDatasetVersion;
  versions: CatalogDatasetVersion[];
  fields: CatalogDatasetField[];
  createTime?: string;
  updateTime?: string;
  analysisCount: number;
  sourceTaskName?: string;
  sourceNodeId?: string;
  directoryId?: string;
  directoryPath?: string;
}

export interface CatalogWorkspace {
  datasets: CatalogDataset[];
  directories: CatalogDirectory[];
}

interface DevelopmentTopology {
  directories: DevelopmentDirectory[];
  nodes: DevelopmentNode[];
  releases: DevelopmentReleaseSummary[];
}

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const toVersion = (value: DatasetVersionWire): CatalogDatasetVersion => ({
  id: String(value.id),
  versionNo: value.versionNo,
  sourceType: value.sourceType,
  sourceTaskAssetId: String(value.sourceTaskAssetId),
  sourceTaskRevisionId: String(value.sourceTaskRevisionId),
  sourceTaskRevisionNo: value.sourceTaskRevisionNo,
  createTime: value.createTime,
});

const toField = (value: DatasetFieldWire): CatalogDatasetField => ({
  fieldId: value.fieldId,
  physicalName: value.physicalName,
  displayName: value.displayName || value.physicalName,
  dataType: value.dataType,
  nullable: value.nullable,
  description: value.description || undefined,
  defaultRole: value.defaultRole,
  sortOrder: value.sortOrder,
});

const analysisCounts = (analyses: AnalysisAsset[]) => {
  const counts = new Map<string, number>();
  analyses.forEach((analysis) => {
    const datasetId = String(analysis.datasetId);
    counts.set(datasetId, (counts.get(datasetId) || 0) + 1);
  });
  return counts;
};

const fetchAllSqlReleases = async (): Promise<DevelopmentReleaseSummary[]> => {
  const first = unwrap(
    await listDevelopmentReleases({
      pageNo: 1,
      pageSize: RELEASE_PAGE_SIZE,
      status: 'ALL',
      taskType: 'SQL',
    }),
    '查询 SQL 发布资产失败',
  );
  const firstRecords = first.records || [];
  const pages = Math.ceil((first.total || firstRecords.length) / RELEASE_PAGE_SIZE);
  if (pages <= 1) return firstRecords;

  const remaining = await Promise.all(
    Array.from({ length: pages - 1 }, (_, index) => index + 2).map(async (pageNo) => {
      const page = unwrap(
        await listDevelopmentReleases({
          pageNo,
          pageSize: RELEASE_PAGE_SIZE,
          status: 'ALL',
          taskType: 'SQL',
        }),
        `查询 SQL 发布资产第 ${pageNo} 页失败`,
      );
      return page.records || [];
    }),
  );
  return [firstRecords, ...remaining].flat();
};

const fetchDevelopmentTopology = async (): Promise<DevelopmentTopology> => {
  const [directoryResponse, nodeResponse, releases] = await Promise.all([
    listDevelopmentDirectories(),
    listDevelopmentNodes(),
    fetchAllSqlReleases(),
  ]);
  return {
    directories: unwrap(directoryResponse, '查询数据开发目录失败') || [],
    nodes: unwrap(nodeResponse, '查询数据开发节点失败') || [],
    releases,
  };
};

const catalogDirectories = (values: DevelopmentDirectory[]): CatalogDirectory[] => values.map((value) => ({
  id: String(value.id),
  parentId: value.parentId == null ? undefined : String(value.parentId),
  name: value.name,
  path: value.path,
}));

const toDataset = (
  detail: DatasetDetailWire,
  counts: Map<string, number>,
  topology?: DevelopmentTopology,
): CatalogDataset => {
  const currentVersion = detail.currentVersion ? toVersion(detail.currentVersion) : undefined;
  const assetId = currentVersion?.sourceTaskAssetId;
  const release = assetId
    ? topology?.releases.find((item) => String(item.assetId) === assetId)
    : undefined;
  const node = release
    ? topology?.nodes.find((item) => String(item.id) === String(release.nodeId))
    : undefined;
  const directoryId = node?.directoryId == null ? undefined : String(node.directoryId);
  const directory = directoryId
    ? topology?.directories.find((item) => String(item.id) === directoryId)
    : undefined;

  return {
    id: String(detail.dataset.id),
    name: detail.dataset.name,
    description: detail.dataset.description || '',
    status: detail.dataset.status,
    currentVersionId: detail.dataset.currentVersionId == null
      ? undefined
      : String(detail.dataset.currentVersionId),
    currentVersion,
    versions: (detail.versions || []).map(toVersion),
    fields: (detail.fields || []).map(toField),
    createTime: detail.dataset.createTime,
    updateTime: detail.dataset.updateTime,
    analysisCount: counts.get(String(detail.dataset.id)) || 0,
    sourceTaskName: release?.taskName,
    sourceNodeId: release ? String(release.nodeId) : undefined,
    directoryId,
    directoryPath: directory?.path,
  };
};

export const fetchCatalogWorkspace = async (): Promise<CatalogWorkspace> => {
  const summaries = unwrap(
    await HttpUtils.get<DatasetSummaryWire[]>(DATASET_API),
    '查询 Dataset 列表失败',
  ) || [];

  const [detailResults, analysesResult, topologyResult] = await Promise.all([
    Promise.allSettled(
      summaries.map((dataset) => HttpUtils.get<DatasetDetailWire>(`${DATASET_API}/${dataset.id}`)),
    ),
    fetchAnalyses().catch(() => []),
    fetchDevelopmentTopology().catch(() => undefined),
  ]);
  const counts = analysisCounts(analysesResult);

  const details = detailResults.flatMap((result, index) => {
    if (result.status !== 'fulfilled') return [];
    try {
      return [unwrap(result.value, `查询 Dataset ${summaries[index].name} 详情失败`)];
    } catch {
      return [];
    }
  });

  if (!details.length && summaries.length) {
    throw new Error('Dataset 列表已返回，但详情读取失败');
  }

  return {
    datasets: details.map((detail) => toDataset(detail, counts, topologyResult)),
    directories: catalogDirectories(topologyResult?.directories || []),
  };
};

export const fetchCatalogDatasets = async (): Promise<CatalogDataset[]> => (
  await fetchCatalogWorkspace()
).datasets;

export const onlineCatalogDataset = async (datasetId: string): Promise<void> => {
  unwrap(
    await HttpUtils.post<DatasetDetailWire>(`${DATASET_API}/${datasetId}/online`, {}),
    '上线 Dataset 失败',
  );
};

export const offlineCatalogDataset = async (datasetId: string): Promise<void> => {
  unwrap(
    await HttpUtils.post<DatasetDetailWire>(`${DATASET_API}/${datasetId}/offline`, {}),
    '下线 Dataset 失败',
  );
};
