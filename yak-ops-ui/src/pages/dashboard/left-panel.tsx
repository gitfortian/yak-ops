import type { AnalysisAsset } from '@/components/analysis/model';
import { history } from '@umijs/max';
import { Button, Empty, Select, Spin } from 'antd';
import { Database, FileBarChart, Hash, Layers3, Plus, RefreshCw } from 'lucide-react';
import { CHART_META, FIELD_DRAG_MIME } from './helpers';
import type { ChartType, DashboardWidget, DatasetField, PublishedDataset } from './model';

function FieldRow({ field, onAdd }: { field: DatasetField; onAdd: () => void }) {
  const isMetric = field.role === 'metric';
  return (
    <div
      draggable
      onDragStart={(event) => {
        event.dataTransfer.effectAllowed = 'copy';
        event.dataTransfer.setData(FIELD_DRAG_MIME, JSON.stringify({ field: field.key, role: field.role }));
      }}
      className="group flex h-8 cursor-grab items-center gap-2 rounded-[4px] px-2 text-[12px] text-[#475467] hover:bg-[#f5f6f7] active:cursor-grabbing"
      title={field.description}
    >
      <span className={isMetric ? 'text-[#667085]' : 'text-[#98a2b3]'}>
        {isMetric ? <Hash size={13} /> : <Layers3 size={13} />}
      </span>
      <span className="min-w-0 flex-1 truncate">{field.label}</span>
      <span className="text-[10px] text-[#b0b7c3]">{field.dataType}</span>
      <button
        type="button"
        aria-label={`添加${field.label}`}
        onClick={onAdd}
        className="flex h-5 w-5 items-center justify-center border-0 bg-transparent text-[#98a2b3] opacity-0 group-hover:opacity-100 hover:text-[#344054]"
      >
        <Plus size={12} />
      </button>
    </div>
  );
}

export function LeftPanel({
  datasets,
  activeDataset,
  datasetsLoading,
  datasetsError,
  analyses,
  analysesLoading,
  analysesError,
  selectedWidget,
  onDatasetChange,
  onRefreshDatasets,
  onRefreshAnalyses,
  onAddChart,
  onAddAnalysis,
  onAddField,
}: {
  datasets: PublishedDataset[];
  activeDataset?: PublishedDataset;
  datasetsLoading: boolean;
  datasetsError: string;
  analyses: AnalysisAsset[];
  analysesLoading: boolean;
  analysesError: string;
  selectedWidget?: DashboardWidget;
  onDatasetChange: (datasetId: string) => void;
  onRefreshDatasets: () => void;
  onRefreshAnalyses: () => void;
  onAddChart: (type: ChartType) => void;
  onAddAnalysis: (analysisId: string) => void;
  onAddField: (field: DatasetField) => void;
}) {
  const dimensions = activeDataset?.fields.filter((field) => field.role === 'dimension') ?? [];
  const metrics = activeDataset?.fields.filter((field) => field.role === 'metric') ?? [];
  const datasetMap = new Map(datasets.map((item) => [item.id, item]));

  return (
    <aside className="flex w-[276px] shrink-0 flex-col border-r border-[#e5e7eb] bg-white">
      <div className="border-b border-[#edf0f3] p-3">
        <div className="mb-2 flex items-center justify-between gap-2 text-[12px] font-semibold text-[#344054]">
          <span className="flex items-center gap-2"><Database size={14} /> 数据集</span>
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
          <div className="mt-2 rounded-[4px] bg-[#f7f8fa] px-2.5 py-2 text-[10px] leading-4 text-[#667085]">
            <div className="flex items-center justify-between gap-2">
              <span className="truncate">来源：{activeDataset.sourceTaskName}</span>
              <span className="shrink-0 text-[#98a2b3]">DV{activeDataset.currentVersionNo ?? '-'}</span>
            </div>
            <div className="mt-0.5 truncate text-[#98a2b3]">
              {activeDataset.fields.length} 个字段 · {activeDataset.updatedAt || '暂无更新时间'}
            </div>
          </div>
        ) : datasetsError ? (
          <div className="mt-2 rounded-[4px] bg-[#f7f8fa] px-2.5 py-2 text-[10px] leading-4 text-[#98a2b3]">
            {datasetsError}
          </div>
        ) : null}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-3 py-2">
        <div className="mb-2 flex items-center justify-between border-b border-[#edf0f3] pb-2 pt-1">
          <span className="flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-wide text-[#98a2b3]">
            <FileBarChart size={12} /> 复用分析
          </span>
          <div className="flex items-center gap-0.5">
            <Button type="text" size="small" icon={<RefreshCw size={11} />} loading={analysesLoading} onClick={onRefreshAnalyses} />
            <Button type="link" size="small" className="px-1 text-[10px]" onClick={() => history.push('/data-analysis/chart-analysis')}>管理</Button>
          </div>
        </div>
        {analysesError ? <div className="mb-2 text-[10px] text-[#b42318]">{analysesError}</div> : null}
        {!analysesLoading && !analyses.length ? (
          <div className="mb-3 rounded-[4px] border border-dashed border-[#d8dde6] px-2.5 py-2 text-[10px] leading-4 text-[#98a2b3]">
            暂无 Analysis，可在图表分析中创建，也可先新建图表再保存为 Analysis。
          </div>
        ) : null}
        <div className="space-y-1">
          {analyses.map((analysis) => {
            const source = datasetMap.get(analysis.datasetId);
            const usable = Boolean(source);
            return (
              <div key={analysis.id} className="group flex items-center gap-2 rounded-[4px] px-2 py-1.5 hover:bg-[#f7f8fa]">
                <FileBarChart size={12} className="shrink-0 text-[#667085]" />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-[11px] text-[#475467]">{analysis.name}</div>
                  <div className="truncate text-[9px] text-[#a0a7b2]">{source?.name ?? 'Dataset 不可用'}</div>
                </div>
                <button
                  type="button"
                  disabled={!usable}
                  aria-label={`添加${analysis.name}`}
                  onClick={() => onAddAnalysis(analysis.id)}
                  className="flex h-5 w-5 items-center justify-center border-0 bg-transparent text-[#98a2b3] opacity-0 enabled:group-hover:opacity-100 enabled:hover:text-[#344054] disabled:cursor-not-allowed"
                >
                  <Plus size={12} />
                </button>
              </div>
            );
          })}
        </div>

        <div className="mb-1 mt-4 flex items-center justify-between border-t border-[#edf0f3] px-1 pt-4 text-[10px] font-medium uppercase tracking-wide text-[#98a2b3]">
          <span>维度</span><span>{dimensions.length}</span>
        </div>
        {!activeDataset && !datasetsLoading ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="先发布一个 Dataset" className="my-5" />
        ) : null}
        {dimensions.map((field) => <FieldRow key={field.key} field={field} onAdd={() => onAddField(field)} />)}

        <div className="mb-1 mt-4 flex items-center justify-between px-1 text-[10px] font-medium uppercase tracking-wide text-[#98a2b3]">
          <span>指标</span><span>{metrics.length}</span>
        </div>
        {metrics.map((field) => <FieldRow key={field.key} field={field} onAdd={() => onAddField(field)} />)}

        <div className="mb-2 mt-5 border-t border-[#edf0f3] pt-4 text-[10px] font-medium uppercase tracking-wide text-[#98a2b3]">
          新建图表
        </div>
        <div className="grid grid-cols-2 gap-2">
          {(Object.keys(CHART_META) as ChartType[]).map((type) => (
            <button
              key={type}
              type="button"
              disabled={!activeDataset}
              onClick={() => onAddChart(type)}
              className="flex min-h-[62px] flex-col items-start justify-center rounded-[5px] border border-[#e5e7eb] bg-white px-3 text-left enabled:hover:border-[#cbd2dc] enabled:hover:bg-[#fafbfc] disabled:cursor-not-allowed disabled:opacity-40"
            >
              <span className="mb-1 text-[#475467]">{CHART_META[type].icon}</span>
              <span className="text-[11px] font-medium text-[#344054]">{CHART_META[type].label}</span>
            </button>
          ))}
        </div>
        {!selectedWidget && activeDataset ? (
          <div className="mt-4 rounded-[4px] border border-dashed border-[#d8dde6] px-3 py-2 text-[10px] leading-4 text-[#98a2b3]">
            可直接复用 Analysis，也可新建临时图表；临时图表可以一键沉淀为可复用分析资产。
          </div>
        ) : null}
      </div>
    </aside>
  );
}
