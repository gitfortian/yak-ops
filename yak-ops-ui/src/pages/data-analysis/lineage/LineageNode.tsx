import {
  BarChart3,
  Braces,
  Database,
  FileCode2,
  LayoutDashboard,
  Rows3,
  TableProperties,
} from 'lucide-react';
import { Handle, Position, type NodeProps } from 'reactflow';
import { assetTypeLabel, type LineageAsset } from './types';

export interface LineageNodeData {
  asset: LineageAsset;
  root: boolean;
}

const iconByType = {
  TABLE: TableProperties,
  COLUMN: Braces,
  SQL_TASK: FileCode2,
  DATASET: Database,
  DATASET_FIELD: Rows3,
  CHART: BarChart3,
  DASHBOARD: LayoutDashboard,
};

const subtitle = (asset: LineageAsset) => {
  if (asset.assetType === 'COLUMN') {
    return [asset.tableName, asset.columnName].filter(Boolean).join('.') || asset.sourceType || '字段';
  }
  if (asset.assetType === 'TABLE') {
    return [asset.databaseName, asset.schemaName, asset.tableName]
      .filter(Boolean)
      .join('.') || asset.sourceType || '数据表';
  }
  if (asset.assetType === 'DATASET_FIELD') return asset.sourceType || 'Dataset 字段';
  if (asset.assetType === 'SQL_TASK') return '数据开发';
  if (asset.assetType === 'CHART') return 'Analysis';
  if (asset.assetType === 'DASHBOARD') return '数据消费';
  return asset.sourceType || assetTypeLabel[asset.assetType];
};

export default function LineageNode({ data, selected }: NodeProps<LineageNodeData>) {
  const { asset, root } = data;
  const Icon = iconByType[asset.assetType];
  return (
    <div
      className="relative flex h-[74px] w-[238px] items-center gap-3 rounded-[8px] border bg-white px-3"
      style={{
        borderColor: root
          ? 'rgba(254,44,85,.35)'
          : selected
            ? '#aeb4bd'
            : '#e2e5e9',
        boxShadow: 'none',
        background: root ? 'rgba(254,44,85,.025)' : '#fff',
      }}
    >
      <Handle
        type="target"
        position={Position.Left}
        style={{
          width: 7,
          height: 7,
          border: '1px solid #c7ccd4',
          background: '#fff',
        }}
      />
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[7px] bg-[#f3f4f6] text-[#5f6670]">
        <Icon size={17} strokeWidth={1.7} />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="min-w-0 flex-1 truncate text-[13px] font-semibold text-[#161823]" title={asset.name}>
            {asset.name}
          </span>
          {root ? (
            <span className="shrink-0 rounded-[4px] bg-[rgba(254,44,85,.07)] px-1.5 py-0.5 text-[10px] font-medium text-[rgba(254,44,85,.82)]">
              当前
            </span>
          ) : null}
        </div>
        <div className="mt-1 flex min-w-0 items-center gap-1.5">
          <span className="shrink-0 rounded-[4px] bg-[#f5f6f7] px-1.5 py-0.5 text-[10px] font-medium text-[#667085]">
            {assetTypeLabel[asset.assetType]}
          </span>
          <span className="min-w-0 flex-1 truncate text-[11px] text-[#8a8f99]" title={subtitle(asset)}>
            {subtitle(asset)}
          </span>
        </div>
      </div>
      <Handle
        type="source"
        position={Position.Right}
        style={{
          width: 7,
          height: 7,
          border: '1px solid #c7ccd4',
          background: '#fff',
        }}
      />
    </div>
  );
}
