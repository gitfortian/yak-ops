import { listTaskCatalogAssets } from '@/services/taskCatalog';
import { Input, Popover, Spin } from 'antd';
import type { PopoverProps } from 'antd';
import { Search } from 'lucide-react';
import type { ReactElement } from 'react';
import { useEffect, useMemo, useState } from 'react';
import WorkflowNodeIcon from './node/icons/WorkflowNodeIcon';
import { taskCatalogOption } from './taskOptions';
import type { WorkflowCanvasTaskOption } from './types';

export type WorkflowTaskCategory = 'sync' | 'development' | 'quality';

interface WorkflowTaskPickerProps {
  options: WorkflowCanvasTaskOption[];
  onSelect: (taskId: string) => void;
  children: ReactElement;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  placement?: PopoverProps['placement'];
  disabled?: boolean;
  defaultCategory?: WorkflowTaskCategory;
}

const CATEGORY_META: Array<{ key: WorkflowTaskCategory; label: string }> = [
  { key: 'sync', label: '数据同步' },
  { key: 'development', label: '数据开发' },
  { key: 'quality', label: '数据质量' },
];

const resolveTaskCategory = (option: WorkflowCanvasTaskOption): WorkflowTaskCategory => {
  if (option.typeLabel.includes('质量')) return 'quality';
  if (option.typeLabel.includes('同步')) return 'sync';
  if (option.typeLabel.includes('开发')) return 'development';

  const normalized = (option.taskType || option.typeLabel || '').trim().toUpperCase();
  if (
    normalized.includes('QUALITY')
    || normalized.includes('CHECK')
    || normalized.includes('VALIDATE')
    || normalized.includes('VERIFY')
  ) return 'quality';
  if (
    normalized.includes('SYNC')
    || normalized.includes('CDC')
    || normalized.includes('REPLICATION')
  ) return 'sync';
  return 'development';
};

const resolveIconTaskType = (option: WorkflowCanvasTaskOption) => {
  if (option.taskType) return option.taskType;
  if (resolveTaskCategory(option) === 'sync') return 'SYNC';
  return undefined;
};

const createSearchState = (): Record<WorkflowTaskCategory, string> => ({
  sync: '',
  development: '',
  quality: '',
});

const WorkflowTaskPicker = ({
  options,
  onSelect,
  children,
  open: controlledOpen,
  onOpenChange,
  placement = 'rightTop',
  disabled = false,
  defaultCategory = 'sync',
}: WorkflowTaskPickerProps) => {
  const [innerOpen, setInnerOpen] = useState(false);
  const [activeCategory, setActiveCategory] = useState<WorkflowTaskCategory>(defaultCategory);
  const [searchText, setSearchText] = useState(createSearchState);
  const [catalogOptions, setCatalogOptions] = useState<WorkflowCanvasTaskOption[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(false);
  const open = controlledOpen ?? innerOpen;

  const handleOpenChange = (nextOpen: boolean) => {
    if (nextOpen && disabled) return;
    if (controlledOpen === undefined) setInnerOpen(nextOpen);
    onOpenChange?.(nextOpen);
  };

  useEffect(() => {
    if (!open) return;
    let active = true;
    setCatalogLoading(true);
    void listTaskCatalogAssets({ source: 'DATA_DEVELOPMENT', status: 'ONLINE' })
      .then((assets) => {
        if (active) setCatalogOptions(assets.map(taskCatalogOption));
      })
      .catch(() => {
        if (active) setCatalogOptions([]);
      })
      .finally(() => {
        if (active) setCatalogLoading(false);
      });
    return () => { active = false; };
  }, [open]);

  const allOptions = useMemo(() => {
    const merged = new Map<string, WorkflowCanvasTaskOption>();
    options.forEach((option) => merged.set(option.id, option));
    catalogOptions.forEach((option) => merged.set(option.id, option));
    return Array.from(merged.values());
  }, [catalogOptions, options]);

  const groupedOptions = useMemo(() => {
    const grouped: Record<WorkflowTaskCategory, WorkflowCanvasTaskOption[]> = {
      sync: [],
      development: [],
      quality: [],
    };
    allOptions.forEach((option) => grouped[resolveTaskCategory(option)].push(option));
    return grouped;
  }, [allOptions]);

  const activeMeta = CATEGORY_META.find((item) => item.key === activeCategory) ?? CATEGORY_META[0];
  const keyword = searchText[activeCategory].trim().toLowerCase();
  const filteredOptions = groupedOptions[activeCategory].filter((option) =>
    !keyword || option.label.toLowerCase().includes(keyword));

  const content = (
    <div
      className="w-[400px] min-w-0 overflow-hidden rounded-xl border border-[#e4e7ec] bg-white shadow-[0_12px_32px_rgba(22,24,35,.14)]"
      onClick={(event) => event.stopPropagation()}
    >
      <div className="flex h-9 items-end gap-0.5 bg-[#f5f6f7] px-1 pt-1">
        {CATEGORY_META.map((item) => {
          const active = item.key === activeCategory;
          return (
            <button
              key={item.key}
              type="button"
              className={[
                'relative flex h-8 items-center rounded-t-lg border-0 px-3 text-[12px] font-medium transition-colors',
                active
                  ? 'bg-white text-[#fe2c55]'
                  : 'bg-transparent text-[#667085] hover:text-[#344054]',
              ].join(' ')}
              onClick={() => setActiveCategory(item.key)}
            >
              {item.label}
              {active ? <span className="absolute inset-x-3 bottom-0 h-0.5 rounded-full bg-[#fe2c55]" /> : null}
            </button>
          );
        })}
      </div>

      <div className="p-2">
        <Input
          autoFocus
          allowClear
          value={searchText[activeCategory]}
          variant="filled"
          prefix={<Search size={14} className="text-[#98a2b3]" />}
          placeholder={`搜索${activeMeta.label}任务...`}
          className="h-8 rounded-lg"
          onChange={(event) => {
            const value = event.target.value;
            setSearchText((current) => ({ ...current, [activeCategory]: value }));
          }}
          onClick={(event) => event.stopPropagation()}
          onKeyDown={(event) => event.stopPropagation()}
        />
      </div>

      <div className="border-t border-[#f0f1f3]">
        <div className="max-h-[420px] overflow-y-auto p-1">
          {catalogLoading && activeCategory === 'development' && !filteredOptions.length ? (
            <div className="flex h-16 items-center justify-center"><Spin size="small" /></div>
          ) : filteredOptions.length ? filteredOptions.map((option) => (
            <button
              key={option.id}
              type="button"
              className="flex h-10 w-full items-center rounded-lg border-0 bg-transparent px-3 text-left transition-colors hover:bg-[#f5f6f7] focus:bg-[#f5f6f7] focus:outline-none"
              onClick={() => {
                onSelect(option.id);
                handleOpenChange(false);
              }}
            >
              <WorkflowNodeIcon taskType={resolveIconTaskType(option)} size="xs" />
              <span className="ml-2 min-w-0 flex-1 truncate text-[13px] font-medium text-[#475467]">
                {option.label}
              </span>
              {option.meta ? (
                <span className="ml-3 shrink-0 text-[10px] font-medium text-[#98a2b3]">{option.meta}</span>
              ) : null}
            </button>
          )) : (
            <div className="flex h-16 items-center justify-center text-[11px] text-[#98a2b3]">
              {keyword ? '没有匹配的任务' : `暂无${activeMeta.label}任务`}
            </div>
          )}
        </div>
      </div>
    </div>
  );

  return (
    <Popover
      trigger="click"
      arrow={false}
      placement={placement}
      open={open}
      onOpenChange={handleOpenChange}
      content={content}
      overlayInnerStyle={{ padding: 0, background: 'transparent', boxShadow: 'none' }}
    >
      {children}
    </Popover>
  );
};

export default WorkflowTaskPicker;
