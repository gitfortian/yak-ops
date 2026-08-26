import type { Moment } from 'moment';
import type { Key, ReactNode } from 'react';

export interface OfflineSyncPaginationState {
  current: number;
  pageSize: number;
  total: number;
}

export interface OfflineSyncSearchState {
  jobName?: string;
  id?: string;
  status?: string;
  sourceType?: string;
  sinkType?: string;
  sourceTable?: string;
  sinkTable?: string;
  createTime?: Moment[];
}

export type OfflineSyncSearchField = keyof OfflineSyncSearchState;

export interface OfflineSyncConnectorOption {
  value: string;
  label: ReactNode;
  connectorType?: string;
  pluginName?: string;
}

export type OfflineSyncSelectedRowKeys = Key[];
