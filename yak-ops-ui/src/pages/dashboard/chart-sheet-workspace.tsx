import { AnalysisPreview } from '@/components/analysis/AnalysisPreview';
import { Empty } from 'antd';
import { BarChart3, Database } from 'lucide-react';
import { ChartEditor } from './chart-editor';
import type {
  AnalysisAsset,
  DashboardFilter,
  DashboardGlobalFilter,
  DashboardInlineAnalysisSpec,
  DashboardInteraction,
  DashboardWidget,
  PublishedDataset,
} from './model';

export function DashboardChartSheetWorkspace({
  currentDashboardId,
  widget,
  datasets,
  analyses,
  globalFilters,
  interactions,
  runtimeFilters,
  updateWidget,
  updateInlineAnalysis,
  updateInteractions,
  changeDataset,
  detachAnalysis,
  onDone,
}: {
  currentDashboardId: string;
  widget: DashboardWidget;
  datasets: PublishedDataset[];
  analyses: AnalysisAsset[];
  globalFilters: DashboardGlobalFilter[];
  interactions: DashboardInteraction[];
  runtimeFilters: DashboardFilter[];
  updateWidget: (patch: Partial<DashboardWidget>) => void;
  updateInlineAnalysis: (patch: Partial<DashboardInlineAnalysisSpec>) => void;
  updateInteractions: (interactions: DashboardInteraction[]) => void;
  changeDataset: (datasetId: string) => void;
  detachAnalysis: () => void;
  onDone: () => void;
}) {
  const analysis = widget.analysisId
    ? analyses.find((item) => item.id === widget.analysisId)
    : undefined;
  const spec = widget.analysisId ? analysis : widget.inlineAnalysis;
  const dataset = spec
    ? datasets.find((item) => item.id === spec.datasetId)
    : undefined;
  const title = widget.analysisId
    ? analysis?.name ?? '历史图表'
    : widget.title?.trim() || '未命名图表';

  return (
    <div className="chart-sheet-workspace flex min-h-0 flex-1 overflow-hidden bg-[#f3f4f6]">
      <ChartEditor
        currentDashboardId={currentDashboardId}
        widget={widget}
        datasets={datasets}
        analyses={analyses}
        globalFilters={globalFilters}
        interactions={interactions}
        updateWidget={updateWidget}
        updateInlineAnalysis={updateInlineAnalysis}
        updateInteractions={updateInteractions}
        changeDataset={changeDataset}
        detachAnalysis={detachAnalysis}
        close={onDone}
      />

      <main className="min-w-0 flex-1 overflow-auto bg-[#f3f4f6]">
        <div className="flex min-h-full p-5 2xl:p-6">
          <div className="mx-auto flex min-h-[560px] w-full max-w-[1320px] flex-1 flex-col">
            <div className="flex h-9 shrink-0 items-center justify-between gap-4 px-1">
              <div className="flex min-w-0 items-center gap-2">
                <BarChart3 size={14} className="shrink-0 text-[#667085]" />
                <span className="truncate text-[13px] font-semibold text-[#344054]">
                  {title}
                </span>
              </div>
              <div className="flex shrink-0 items-center gap-1.5 text-[10px] text-[#98a2b3]">
                <Database size={11} />
                <span className="max-w-[220px] truncate">
                  {dataset?.name ?? '数据来源不可用'}
                </span>
              </div>
            </div>

            <div className="mt-2 min-h-0 flex-1 overflow-hidden rounded-[10px] border border-[#e1e4e8] bg-white">
              {spec && dataset ? (
                <AnalysisPreview
                  spec={spec}
                  dataset={dataset}
                  runtimeFilters={runtimeFilters}
                  className="h-full min-h-[500px] p-4"
                />
              ) : (
                <div className="flex min-h-[500px] items-center justify-center">
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={spec ? '图表数据来源已失效' : '图表配置不可用'}
                  />
                </div>
              )}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
