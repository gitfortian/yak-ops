import { Button, Select, Spin } from 'antd';
import { Database, Plus, RefreshCw } from 'lucide-react';
import { CHART_META } from './helpers';
import type { ChartType, PublishedDataset } from './model';

export function LeftPanel({
  datasets,
  activeDataset,
  datasetsLoading,
  datasetsError,
  onDatasetChange,
  onRefreshDatasets,
  onAddChart,
}: {
  datasets: PublishedDataset[];
  activeDataset?: PublishedDataset;
  datasetsLoading: boolean;
  datasetsError: string;
  onDatasetChange: (datasetId: string) => void;
  onRefreshDatasets: () => void;
  onAddChart: (type: ChartType) => void;
}) {
  return (
    <aside className="flex w-[244px] shrink-0 flex-col border-r border-[#e5e7eb] bg-white">
      <div className="border-b border-[#edf0f3] p-3">
        <div className="mb-2 flex items-center justify-between gap-2 text-[12px] font-semibold text-[#344054]">
          <span className="flex items-center gap-2">
            <Database size={14} />
            数据集
          </span>
          <Button
            type="text"
            size="small"
            icon={<RefreshCw size={12} />}
            loading={datasetsLoading}
            onClick={onRefreshDatasets}
          />
        </div>

        <Select
          size="small"
          className="w-full"
          loading={datasetsLoading}
          placeholder="选择已发布 Dataset"
          value={activeDataset?.id}
          onChange={onDatasetChange}
          options={datasets.map((item) => ({ label: item.name, value: item.id }))}
          notFoundContent={datasetsLoading ? <Spin size="small" /> : '暂无 ONLINE Dataset'}
        />

        {activeDataset ? (
          <div className="mt-2 px-0.5 text-[10px] leading-4 text-[#98a2b3]">
            <div className="truncate">{activeDataset.sourceTaskName || '未标记来源任务'}</div>
            <div className="mt-0.5 truncate">
              {activeDataset.fields.length} 个字段 · DV{activeDataset.currentVersionNo ?? '-'}
            </div>
          </div>
        ) : datasetsError ? (
          <div className="mt-2 text-[10px] leading-4 text-[#98a2b3]">
            {datasetsError}
          </div>
        ) : null}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto p-3">
        <div className="mb-2">
          <div className="text-[12px] font-semibold text-[#344054]">添加图表</div>
          <div className="mt-0.5 text-[10px] leading-4 text-[#98a2b3]">
            选择类型后添加到画布，数据与样式在图表配置中完成
          </div>
        </div>

        <div className="space-y-1">
          {(Object.keys(CHART_META) as ChartType[]).map((type) => (
            <button
              key={type}
              type="button"
              disabled={!activeDataset}
              onClick={() => onAddChart(type)}
              className="group flex h-11 w-full items-center gap-2.5 rounded-[5px] border-0 bg-transparent px-2 text-left enabled:hover:bg-[#f5f6f7] disabled:cursor-not-allowed disabled:opacity-40"
            >
              <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[4px] bg-[#f5f6f7] text-[#667085] group-hover:bg-white">
                {CHART_META[type].icon}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-[11px] font-medium text-[#344054]">
                  {CHART_META[type].label}
                </span>
                <span className="mt-0.5 block truncate text-[9px] text-[#98a2b3]">
                  {CHART_META[type].description}
                </span>
              </span>
              <Plus size={12} className="shrink-0 text-[#b0b7c3]" />
            </button>
          ))}
        </div>

        {!activeDataset && !datasetsLoading ? (
          <div className="mt-4 border-t border-[#edf0f3] pt-3 text-[10px] leading-4 text-[#98a2b3]">
            先选择一个已上线的数据集，再开始搭建仪表盘。
          </div>
        ) : null}
      </div>
    </aside>
  );
}
