import type { CatalogDatasetSourceType, CatalogDatasetStatus } from '@/services/data-analysis';
import type { DataNode } from 'antd/es/tree';

export type CatalogTreeNodeKind = 'root' | 'directory' | 'dataset' | 'ungrouped';
export type CatalogStatusFilter = 'ALL' | CatalogDatasetStatus;
export type CatalogSourceTypeFilter = 'ALL' | CatalogDatasetSourceType;
export type CatalogDetailTab = 'fields' | 'versions' | 'overview' | 'lineage';

export interface CatalogTreeNode extends DataNode {
  key: string;
  title: string;
  kind: CatalogTreeNodeKind;
  datasetId?: string;
  directoryId?: string;
  datasetCount?: number;
  searchText?: string;
  children?: CatalogTreeNode[];
}
