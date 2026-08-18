import { AnalysisPreview } from '@/components/analysis/AnalysisPreview';
import { Dropdown, Empty, Tooltip } from 'antd';
import {
  ChevronRight,
  Copy,
  GripVertical,
  MoreHorizontal,
  RotateCcw,
  Trash2,
  X,
} from 'lucide-react';
import type {
  AnalysisAsset,
  AnalysisSelection,
  DashboardDrillStep,
  DashboardFilter,
  DashboardInlineAnalysisSpec,
  DashboardWidget,
  PublishedDataset,
} from './model';

export function WidgetShell({
  widget,
  analysis,
  runtimeSpec,
  dataset,
  runtimeFilters,
  drillPath,
  activeSelection,
  selected,
  preview,
  onSelect,
  onDataSelect,
  onClearSelection,
  onDrillBack,
  onDuplicate,
  onDelete,
}: {
  widget: DashboardWidget;
  analysis?: AnalysisAsset;
  runtimeSpec?: DashboardInlineAnalysisSpec | AnalysisAsset;
  dataset?: PublishedDataset;
  runtimeFilters: DashboardFilter[];
  drillPath: DashboardDrillStep[];
  activeSelection?: AnalysisSelection;
  selected: boolean;
  preview: boolean;
  onSelect: () => void;
  onDataSelect: (selection: AnalysisSelection) => void;
  onClearSelection: () => void;
  onDrillBack: (depth: number) => void;
  onDuplicate: () => void;
  onDelete: () => void;
}) {
  const spec = runtimeSpec ?? (widget.analysisId ? analysis : widget.inlineAnalysis);
  const title = widget.analysisId
    ? analysis?.name ?? '历史图表'
    : widget.title ?? '未命名图表';

  return (
    <div
      onMouseDown={onSelect}
      className={[
        'group relative flex h-full min-h-0 flex-col overflow-hidden rounded-[9px] bg-white transition-[border-color,box-shadow,transform] duration-150',
        preview
          ? activeSelection
            ? 'border border-[var(--yak-brand-color)] shadow-[0_0_0_2px_var(--yak-brand-color-soft),0_4px_12px_rgba(16,24,40,.05)]'
            : 'border border-[#e7e9ed] shadow-[0_1px_2px_rgba(16,24,40,.03)]'
          : selected
            ? 'border border-[var(--yak-brand-color)] shadow-[0_0_0_2px_var(--yak-brand-color-soft),0_4px_12px_rgba(16,24,40,.05)]'
            : 'border border-[#e4e7ec] shadow-[0_1px_2px_rgba(16,24,40,.025)] hover:border-[#d6dae0] hover:shadow-[0_4px_12px_rgba(16,24,40,.055)]',
      ].join(' ')}
    >
      <div className="dashboard-widget__drag-handle flex h-10 shrink-0 cursor-move items-center px-3.5">
        {!preview ? (
          <GripVertical
            size={13}
            className={[
              'mr-1.5 text-[#a8adb5] transition-opacity duration-150',
              selected ? 'opacity-100' : 'opacity-0 group-hover:opacity-100',
            ].join(' ')}
          />
        ) : null}
        <span className="min-w-0 flex-1 truncate text-[12px] font-semibold text-[#344054]">
          {title}
        </span>

        {preview && activeSelection ? (
          <button
            type="button"
            title={activeSelection.label}
            className="ml-2 flex max-w-[190px] shrink-0 items-center gap-1 rounded-[5px] border border-[var(--yak-brand-color-border)] bg-[var(--yak-brand-color-soft)] px-1.5 py-1 text-[9px] text-[#475467]"
            onMouseDown={(event) => event.stopPropagation()}
            onClick={(event) => {
              event.stopPropagation();
              onClearSelection();
            }}
          >
            <span className="truncate">已选 · {String(activeSelection.value)}</span>
            <X size={9} className="shrink-0 text-[#7a818c]" />
          </button>
        ) : null}

        {!preview ? (
          <div
            className={[
              'flex items-center transition-opacity duration-150',
              selected ? 'opacity-100' : 'opacity-0 group-hover:opacity-100',
            ].join(' ')}
          >
            <Dropdown
              trigger={['click']}
              placement="bottomRight"
              menu={{
                items: [
                  {
                    key: 'duplicate',
                    label: '复制组件',
                    icon: <Copy size={13} />,
                  },
                  { type: 'divider' },
                  {
                    key: 'delete',
                    label: '删除组件',
                    icon: <Trash2 size={13} />,
                    danger: true,
                  },
                ],
                onClick: ({ key, domEvent }) => {
                  domEvent.stopPropagation();
                  if (key === 'duplicate') onDuplicate();
                  if (key === 'delete') onDelete();
                },
              }}
            >
              <Tooltip title="更多操作">
                <button
                  type="button"
                  aria-label="组件操作"
                  onMouseDown={(event) => event.stopPropagation()}
                  onClick={(event) => event.stopPropagation()}
                  className="flex h-7 w-7 items-center justify-center rounded-[6px] border-0 bg-transparent text-[#7a818c] transition-colors hover:bg-[#f5f6f7] hover:text-[#344054]"
                >
                  <MoreHorizontal size={14} />
                </button>
              </Tooltip>
            </Dropdown>
          </div>
        ) : null}
      </div>

      {drillPath.length ? (
        <div
          className="mx-3 flex h-7 shrink-0 items-center gap-0.5 rounded-[6px] bg-[#f7f8fa] px-2 text-[9px] text-[#667085]"
          onMouseDown={(event) => event.stopPropagation()}
        >
          <button
            type="button"
            className="flex h-5 items-center gap-1 rounded-[4px] border-0 bg-transparent px-1 text-[#667085] hover:bg-[#eceef1] hover:text-[#344054]"
            onClick={(event) => {
              event.stopPropagation();
              onDrillBack(0);
            }}
          >
            <RotateCcw size={9} />
            全部
          </button>
          {drillPath.map((step, index) => (
            <span key={`${step.field}-${index}`} className="flex min-w-0 items-center gap-0.5">
              <ChevronRight size={9} className="shrink-0 text-[#b1b6bf]" />
              <button
                type="button"
                className="max-w-[120px] truncate rounded-[4px] border-0 bg-transparent px-1 py-0.5 text-[#475467] hover:bg-[#eceef1]"
                title={step.label}
                onClick={(event) => {
                  event.stopPropagation();
                  onDrillBack(index + 1);
                }}
              >
                {step.label}
              </button>
            </span>
          ))}
        </div>
      ) : null}

      <div className="min-h-0 flex-1 overflow-hidden px-1 pb-1">
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
