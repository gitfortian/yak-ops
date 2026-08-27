import { YakButton, YakEmpty, YakTab } from '@/components/ui';
import { CalendarDays, CircleHelp, Download } from 'lucide-react';
import { Segmented, Tooltip, message } from 'antd';
import dayjs from 'dayjs';
import { useMemo, useState } from 'react';
import type { QualityOverviewTab } from '../constants';

type PeriodKey = 'yesterday' | '7d' | '30d';

interface QualityMetricSectionProps {
  title: string;
  subtitle: string;
  tabs: QualityOverviewTab[];
  defaultTab: string;
}

const periodOptions = [
  { label: '昨天', value: 'yesterday' },
  { label: '近7天', value: '7d' },
  { label: '近30天', value: '30d' },
] as const;

const resolveDateRange = (period: PeriodKey) => {
  const end = dayjs().subtract(1, 'day');
  if (period === 'yesterday') return [end, end] as const;
  if (period === '7d') return [end.subtract(6, 'day'), end] as const;
  return [end.subtract(29, 'day'), end] as const;
};

const MetricStrip = ({ tab }: { tab: QualityOverviewTab }) => (
  <div className="overflow-x-auto border-y border-solid border-[#eceef2]">
    <div
      className="grid min-w-max"
      style={{
        gridTemplateColumns: `repeat(${tab.metrics.length}, minmax(150px, 1fr))`,
      }}
    >
      {tab.metrics.map((metric, index) => (
        <div
          key={metric.label}
          className={[
            'min-h-[86px] bg-[#fafafa] px-4 py-3',
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

export default function QualityMetricSection({
  title,
  subtitle,
  tabs,
  defaultTab,
}: QualityMetricSectionProps) {
  const [activeTab, setActiveTab] = useState(defaultTab);
  const [period, setPeriod] = useState<PeriodKey>('yesterday');

  const currentTab = useMemo(
    () => tabs.find((tab) => tab.key === activeTab) ?? tabs[0],
    [activeTab, tabs],
  );
  const range = resolveDateRange(period);
  const rangeLabel = `${range[0].format('MM.DD')}-${range[1].format('MM.DD')}`;

  return (
    <section className="rounded-xl bg-white px-5 pb-6 pt-5 lg:px-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <h2 className="m-0 text-[18px] font-semibold text-[#161823]">
              {title}
            </h2>
            <span className="text-[11px] text-[#98a2b3]">{subtitle}</span>
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
            value={period}
            options={periodOptions.map((item) => ({ ...item }))}
            onChange={(value) => setPeriod(value as PeriodKey)}
            className="!bg-[#f4f5f7]"
          />
          <YakButton
            size="small"
            icon={<CalendarDays size={14} />}
            onClick={() => message.info('日期范围选择将在真实数据接入阶段开放')}
          >
            {rangeLabel}
          </YakButton>
          <YakButton
            size="small"
            icon={<Download size={14} />}
            onClick={() => message.info('导出能力将在后端数据接入后开放')}
          >
            导出数据
          </YakButton>
        </div>
      </div>

      <MetricStrip tab={currentTab} />

      <div className="flex min-h-[320px] items-center justify-center border-x border-b border-solid border-[#eceef2] bg-white">
        <YakEmpty
          compact
          title={currentTab.emptyTitle}
          description={currentTab.emptyDescription}
        />
      </div>
    </section>
  );
}
