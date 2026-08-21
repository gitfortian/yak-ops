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
import { lineageAssetVisual } from './visual';

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
  const visual = lineageAssetVisual[asset.assetType];
  const emphasized = root || selected;

  return (
    <div
      className="group relative flex h-[86px] w-[260px] items-center gap-3.5 overflow-hidden rounded-[12px] border bg-white px-3.5 py-3 transition-[transform,box-shadow,border-color] duration-200"
      style={{
        borderColor: emphasized ? visual.accent : '#E4E8EE',
        boxShadow: emphasized
          ? `0 12px 30px -18px ${visual.glow}, 0 4px 12px -8px ${visual.glow}`
          : '0 4px 14px -12px rgba(15, 23, 42, 0.28)',
      }}
    >
      <div
        className="absolute inset-y-0 left-0 w-[4px]"
        style={{ background: visual.accent }}
      />
      <div
        className="pointer-events-none absolute inset-x-0 top-0 h-14 opacity-70"
        style={{
          background: `linear-gradient(180deg, ${visual.soft} 0%, rgba(255,255,255,0) 100%)`,
        }}
      />

      <Handle
        type="target"
        position={Position.Left}
        style={{
          width: 8,
          height: 8,
          border: `2px solid ${visual.accent}`,
          background: '#fff',
          left: -5,
        }}
      />

      <div
        className="relative z-[1] flex h-11 w-11 shrink-0 items-center justify-center rounded-[10px] border"
        style={{
          color: visual.accent,
          background: visual.softStrong,
          borderColor: visual.border,
        }}
      >
        <Icon size={19} strokeWidth={1.8} />
      </div>

      <div className="relative z-[1] min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span
            className="min-w-0 flex-1 truncate text-[13px] font-semibold text-[#182230]"
            title={asset.name}
          >
            {asset.name}
          </span>
          {root ? (
            <span
              className="shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold"
              style={{ color: visual.accent, background: visual.softStrong }}
            >
              当前
            </span>
          ) : null}
        </div>

        <div className="mt-1.5 flex min-w-0 items-center gap-1.5">
          <span
            className="shrink-0 rounded-[5px] border px-1.5 py-0.5 text-[10px] font-semibold"
            style={{
              color: visual.accent,
              background: visual.soft,
              borderColor: visual.border,
            }}
          >
            {assetTypeLabel[asset.assetType]}
          </span>
          <span
            className="min-w-0 flex-1 truncate text-[11px] text-[#7A8493]"
            title={subtitle(asset)}
          >
            {subtitle(asset)}
          </span>
        </div>
      </div>

      <Handle
        type="source"
        position={Position.Right}
        style={{
          width: 8,
          height: 8,
          border: `2px solid ${visual.accent}`,
          background: '#fff',
          right: -5,
        }}
      />
    </div>
  );
}
