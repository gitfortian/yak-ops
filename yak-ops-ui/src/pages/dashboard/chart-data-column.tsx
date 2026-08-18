import type { AnalysisSpec } from '@/components/analysis/model';
import { Select } from 'antd';
import { Database } from 'lucide-react';
import { ChartFieldPanel } from './chart-field-panel';
import type { PublishedDataset } from './model';

export function ChartDataColumn({
  dataset,
  datasets,
  spec,
  editable,
  onDatasetChange,
  onSpecPatch,
}: {
  dataset?: PublishedDataset;
  datasets: PublishedDataset[];
  spec?: AnalysisSpec;
  editable: boolean;
  onDatasetChange?: (datasetId: string) => void;
  onSpecPatch?: (patch: Partial<AnalysisSpec>) => void;
}) {
  return (
    <section className="chart-data-column flex w-[244px] shrink-0 flex-col border-r border-[#e3e6ea] bg-white">
      <div className="shrink-0 border-b border-[#eceef1] px-3 py-3">
        <Select
          showSearch
          size="small"
          variant="filled"
          value={dataset?.id}
          disabled={!editable}
          optionFilterProp="label"
          className="w-full"
          placeholder="选择数据集"
          suffixIcon={<Database size={12} className="text-[#8e95a0]" />}
          options={datasets.map((item) => ({
            label: item.name,
            value: item.id,
          }))}
          onChange={onDatasetChange}
        />
        <div className="mt-1.5 truncate px-0.5 text-[9px] text-[#98a2b3]">
          {dataset ? `数据目录 · ${dataset.name}` : '当前数据来源不可用，可重新选择'}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-hidden">
        <ChartFieldPanel
          dataset={dataset}
          spec={spec}
          editable={editable && Boolean(dataset)}
          onSpecPatch={onSpecPatch}
        />
      </div>

      <style>{`
        .chart-data-column > .min-h-0 > section {
          height: 100%;
          border-right: 0 !important;
        }
        .chart-data-column > .min-h-0 > section > div:first-child {
          display: none;
        }
      `}</style>
    </section>
  );
}
