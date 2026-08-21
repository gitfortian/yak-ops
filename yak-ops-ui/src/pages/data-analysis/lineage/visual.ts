import type { LineageAssetType, LineageRelationType } from './types';

export interface LineageAssetVisual {
  accent: string;
  soft: string;
  softStrong: string;
  border: string;
  glow: string;
}

export const lineageAssetVisual: Record<LineageAssetType, LineageAssetVisual> = {
  TABLE: {
    accent: '#2563EB',
    soft: '#EFF6FF',
    softStrong: '#DBEAFE',
    border: '#BFDBFE',
    glow: 'rgba(37, 99, 235, 0.18)',
  },
  COLUMN: {
    accent: '#0891B2',
    soft: '#ECFEFF',
    softStrong: '#CFFAFE',
    border: '#A5F3FC',
    glow: 'rgba(8, 145, 178, 0.16)',
  },
  SQL_TASK: {
    accent: '#7C3AED',
    soft: '#F5F3FF',
    softStrong: '#EDE9FE',
    border: '#DDD6FE',
    glow: 'rgba(124, 58, 237, 0.18)',
  },
  DATASET: {
    accent: '#FE2C55',
    soft: '#FFF1F4',
    softStrong: '#FFE4EA',
    border: '#FFC2CF',
    glow: 'rgba(254, 44, 85, 0.18)',
  },
  DATASET_FIELD: {
    accent: '#DB2777',
    soft: '#FDF2F8',
    softStrong: '#FCE7F3',
    border: '#FBCFE8',
    glow: 'rgba(219, 39, 119, 0.16)',
  },
  CHART: {
    accent: '#EA580C',
    soft: '#FFF7ED',
    softStrong: '#FFEDD5',
    border: '#FED7AA',
    glow: 'rgba(234, 88, 12, 0.16)',
  },
  DASHBOARD: {
    accent: '#059669',
    soft: '#ECFDF5',
    softStrong: '#D1FAE5',
    border: '#A7F3D0',
    glow: 'rgba(5, 150, 105, 0.17)',
  },
};

export const lineageRelationColor: Record<LineageRelationType, string> = {
  READS_FROM: '#4F7FEA',
  WRITES_TO: '#7C3AED',
  DERIVES_FROM: '#E44767',
  CONSUMES: '#E87920',
  CONTAINS: '#0F9F8F',
};
