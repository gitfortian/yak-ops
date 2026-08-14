import { AnalysisPreview } from '@/components/analysis/AnalysisPreview';
import { Empty, Tooltip } from 'antd';
import { Copy, GripVertical, Trash2 } from 'lucide-react';
import type {
  AnalysisAsset,
  AnalysisSelection,
  DashboardFilter,
  DashboardWidget,
  PublishedDataset,
} from './model';

export function WidgetShell({
  widget,
  analysis,
  dataset,
  runtimeFilters,
  selected,
  preview,
  onSelect,
  onDataSelect,
  onDuplicate,
  onDelete,
}: {
  widget: DashboardWidget;
  analysis?: AnalysisAsset;
  dataset?: PublishedDataset;
  runtimeFilters: DashboardFilter[];
  selected: boolean;
  preview: boolean;
  onSelect: () => void;
  onDataSelect: (selection: AnalysisSelection) => void;
  onDuplicate: () => void;
  onDelete: () => void;
}) {
  const spec = widget.analysisId ? analysis : widget.inlineAnalysis;
  const title = widget.analysisId
    ? analysis?.name ?? '历史图表'
    : widget.title ?? '未命名图表';

  return (
    <div
      onMouseDown={onSelect}
      className={[
        'group relative flex h-full min-h-0 flex-col overflow-hidden bg-white',
        preview
          ? 'border border-[#e7eaf0]'
          : selected
            ? 'border border-[var(--yak-brand-color)] shadow-[0_0_0_1px_var(--yak-brand-color-soft)]'
            : 'border border-[#e3e7ed] hover:border-[#cbd2dc]',
      ].join(' ')}
    >
      <div className="dashboard-widget__drag-handle flex h-9 shrink-0 cursor-move items-center border-b border-[#f0f2f5] px-3">
        {!preview ? <GripVertical size={13} className="mr-1 text-[#98a2b3]" /> : null}
        <span className="min-w-0 flex-1 truncate text-[12px] font-medium text-[#344054]">
          {title}
        </span>

        {!preview ? (
          <div
            className={[
              'flex items-center gap-0.5 transition-opacity',
              selected ? 'opacity-100' : 'opacity-0 group-hover:opacity-100',
            ].join(' ')}
          >
            <Tooltip title="复制">
              <button
                type="button"
                onMouseDown={(event) => event.stopPropagation()}
                onClick={(event) => {
                  event.stopPropagation();
                  onDuplicate();
                }}
                className="flex h-6 w-6 items-center justify-center border-0 bg-transparent text-[#667085] hover:bg-[#f5f6f7]"
              >
                <Copy size={12} />
              </button>
            </Tooltip>
            <Tooltip title="删除">
              <button
                type="button"
                onMouseDown={(event) => event.stopPropagation()}
                onClick={(event) => {
                  event.stopPropagation();
                  onDelete();
                }}
                className="flex h-6 w-6 items-center justify-center border-0 bg-transparent text-[#667085] hover:bg-[#fff1f2] hover:text-[#d92d20]"
              >
                <Trash2 size={12} />
              </button>
            </Tooltip>
          </div>
        ) : null}
      </div>

      <div className="min-h-0 flex-1 overflow-hidden">
        {spec ? (
          <AnalysisPreview
            spec={spec}
            dataset={dataset}
            runtimeFilters={runtimeFilters}
            onSelect={onDataSelect}
          />
        ) : (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="图表数据来源已失效"
            className="mt-8"
          />
        )}
      </div>
    </div>
  );
}
