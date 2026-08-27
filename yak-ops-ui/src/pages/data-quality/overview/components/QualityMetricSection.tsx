import { YakButton, YakTab } from '@/components/ui';
import type { QualityOverviewView } from '@/services/data-quality';
import { DatePicker, Segmented, Spin, Tooltip, message } from 'antd';
import dayjs from 'dayjs';
import { CalendarDays, CircleHelp, Download } from 'lucide-react';
import { useMemo, useState } from 'react';
import type { QualityOverviewTabDefinition } from '../constants';
import {
  buildMetrics,
  formatPeriodText,
  rangeKey,
  resolvePresetRange,
  toPickerRange,
  type OverviewDateRange,
  type OverviewPeriodKey,
  type OverviewSectionKind,
} from '../utils';
import QualityTrendChart from './QualityTrendChart';

const { RangePicker } = DatePicker;

interface QualityMetricSectionProps {
  title: string;
  tabs: QualityOverviewTabDefinition[];
  defaultTab: string;
  section: OverviewSectionKind;
  range: OverviewDateRange;
  overview?: QualityOverviewView;
  loading?: boolean;
  onRangeChange: (range: OverviewDateRange) => void;
}

const periodOptions = [
  { label: '昨天', value: 'yesterday' },
  { label: '近7天', value: '7d' },
  { label: '近30天', value: '30d' },
] as const;

const presetForRange = (range: OverviewDateRange): OverviewPeriodKey | undefined =>
  periodOptions.find((item) => rangeKey(resolvePresetRange(item.value)) === rangeKey(range))?.value;

const compactRangeText = (range: OverviewDateRange) =>
  `${dayjs(range.startDate).format('MM.DD')}-${dayjs(range.endDate).format('MM.DD')}`;

const MetricStrip = ({ metrics }: { metrics: ReturnType<typeof buildMetrics> }) => (
  <div className="overflow-x-auto border-y border-solid border-[#eceef2]">
    <div
      className="grid min-w-max"
      style={{ gridTemplateColumns: `repeat(${metrics.length}, minmax(150px, 1fr))` }}
    >
      {metrics.map((metric, index) => (
        <div
          key={metric.label}
          className={[
            'min-h-[86px] bg-[#fafafa] px-4 py-3 transition-colors',
            index ? 'border-l border-solid border-[#eceef2]' : '',
          ].join(' ')}
        >
          <div className="flex items-center gap-1 text-[12px] font-medium text-[#4b5563]">
            <span>{metric.label}</span>
            {metric.tooltip ? (
              <Tooltip title={metric.tooltip}>
                <CircleHelp size={12} className="text-[#a6acb5]" />
              </Tooltip>
            ) : null}
          </div>
          <div className="mt-2 text-[18px] font-semibold leading-6 text-[#161823]">
            {metric.value}
          </div>
        </div>
      ))}
    </div>
  </div>
);

const exportOverviewCsv = (title: string, overview: QualityOverviewView) => {
  const rows = [
    ['日期', '执行次数', '活跃监控', '执行规则', '通过规则', '未通过规则', '异常规则', '问题执行', '通过率', '问题率', '平均耗时(ms)'],
    ...overview.trend.map((point) => [
      point.date,
      point.executionCount,
      point.activeMonitorCount,
      point.executedRuleCount,
      point.passedRuleCount,
      point.failedRuleCount,
      point.errorRuleCount,
      point.issueExecutionCount,
      point.passRate ?? '',
      point.issueRate ?? '',
      point.averageDurationMs ?? '',
    ]),
  ];
  const csv = `\uFEFF${rows.map((row) => row.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(',')).join('\n')}`;
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `${title}-${overview.rangeStart}-${overview.rangeEnd}.csv`;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
};

export default function QualityMetricSection({
  title,
  tabs,
  defaultTab,
  section,
  range,
  overview,
  loading = false,
  onRangeChange,
}: QualityMetricSectionProps) {
  const [activeTab, setActiveTab] = useState(defaultTab);
  const metrics = useMemo(
    () => buildMetrics(section, activeTab, overview),
    [activeTab, overview, section],
  );
  const selectedPreset = presetForRange(range);

  return (
    <section className="rounded-xl bg-white px-5 pb-6 pt-5 lg:px-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <h2 className="m-0 text-[18px] font-semibold text-[#161823]">{title}</h2>
            <span className="text-[11px] text-[#98a2b3]">{formatPeriodText(range)}</span>
          </div>
          <YakTab
            activeKey={activeTab}
            onChange={setActiveTab}
            className="mt-2 [&_.ant-tabs-nav]:!mb-0"
            items={tabs.map((tab) => ({ key: tab.key, label: tab.label }))}
          />
        </div>

        <div className="flex flex-wrap items-center justify-end gap-2">
          <Segmented
            size="small"
            value={selectedPreset}
            options={periodOptions.map((item) => ({ ...item }))}
            onChange={(value) => onRangeChange(resolvePresetRange(value as OverviewPeriodKey))}
            className={[
              '!h-8 !rounded-lg !bg-[#f3f4f6] !p-1',
              '[&_.ant-segmented-group]:!h-6 [&_.ant-segmented-group]:!gap-1',
              '[&_.ant-segmented-item]:!min-w-[68px] [&_.ant-segmented-item]:!rounded-md [&_.ant-segmented-item]:!text-[13px] [&_.ant-segmented-item]:!font-medium [&_.ant-segmented-item]:!text-[#667085]',
              '[&_.ant-segmented-item-label]:!min-h-0 [&_.ant-segmented-item-label]:!px-3 [&_.ant-segmented-item-label]:!leading-6',
              '[&_.ant-segmented-thumb]:!rounded-md [&_.ant-segmented-thumb]:!bg-white [&_.ant-segmented-thumb]:!shadow-[0_1px_3px_rgba(16,24,40,0.08)]',
              '[&_.ant-segmented-item-selected]:!bg-white [&_.ant-segmented-item-selected]:!font-semibold [&_.ant-segmented-item-selected]:!text-[#161823] [&_.ant-segmented-item-selected]:!shadow-[0_1px_3px_rgba(16,24,40,0.08)]',
            ].join(' ')}
          />

          <div className="group relative h-8 w-[142px] shrink-0">
            <div className="pointer-events-none flex h-8 items-center justify-center gap-2 rounded-lg bg-[#f3f4f6] px-3 text-[13px] font-medium text-[#161823] transition-colors group-hover:bg-[#e9eaed] group-focus-within:ring-2 group-focus-within:ring-[rgba(254,44,85,0.12)]">
              <CalendarDays size={15} strokeWidth={1.8} />
              <span>{compactRangeText(range)}</span>
            </div>
            <RangePicker
              size="small"
              value={toPickerRange(range)}
              format="MM.DD"
              allowClear={false}
              disabledDate={(current) => current.isAfter(dayjs().subtract(1, 'day'), 'day')}
              onChange={(value) => {
                const start = value?.[0];
                const end = value?.[1];
                if (!start || !end) return;
                if (end.diff(start, 'day') > 89) {
                  message.warning('质量总览单次最多查询 90 天');
                  return;
                }
                onRangeChange({
                  startDate: start.format('YYYY-MM-DD'),
                  endDate: end.format('YYYY-MM-DD'),
                });
              }}
              className="!absolute !inset-0 !h-8 !w-full !cursor-pointer !opacity-0"
            />
          </div>

          <YakButton
            size="small"
            icon={<Download size={14} />}
            className="!h-8 !rounded-lg !border-0 !bg-[#f3f4f6] !px-3 !text-[13px] !font-semibold !text-[#161823] !shadow-none hover:!bg-[#e9eaed]"
            onClick={() => {
              if (!overview) {
                message.info('当前统计周期暂无可导出的质量数据');
                return;
              }
              exportOverviewCsv(title, overview);
            }}
          >
            导出数据
          </YakButton>
        </div>
      </div>

      <MetricStrip metrics={metrics} />

      <div className="min-h-[340px] border-x border-b border-solid border-[#eceef2] bg-white">
        <Spin spinning={loading}>
          <QualityTrendChart overview={overview} section={section} tabKey={activeTab} />
        </Spin>
      </div>
    </section>
  );
}
