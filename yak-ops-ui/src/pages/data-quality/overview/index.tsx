import { BRAND_THEME } from '@/styles/brand';
import { ConfigProvider } from 'antd';
import dayjs from 'dayjs';
import { Info } from 'lucide-react';
import QualityMetricSection from './components/QualityMetricSection';
import QualityRadarOverview from './components/QualityRadarOverview';
import {
  QUALITY_EXECUTION_TABS,
  QUALITY_ISSUE_TABS,
  QUALITY_RADAR_METRICS,
} from './constants';

export default function DataQualityOverviewPage() {
  const end = dayjs().subtract(1, 'day');
  const start = end.subtract(6, 'day');
  const overviewPeriod = `统计周期：${start.format('YYYY-MM-DD')} 至 ${end.format('YYYY-MM-DD')}（每日 12 点更新）`;
  const dailyPeriod = `统计周期：${end.format('YYYY-MM-DD')}（每日 10 点更新前一日数据）`;

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="min-h-[calc(100vh-64px)] bg-[#f6f7f9] text-[#161823]">
        <div className="mx-auto w-full max-w-[1900px] space-y-5 px-4 py-4 lg:px-5">
          <QualityRadarOverview periodText={overviewPeriod} metrics={QUALITY_RADAR_METRICS} />
          <QualityMetricSection
            title="质量检测数据"
            subtitle={dailyPeriod}
            tabs={QUALITY_EXECUTION_TABS}
            defaultTab="execution"
          />
          <QualityMetricSection
            title="问题数据"
            subtitle={overviewPeriod}
            tabs={QUALITY_ISSUE_TABS}
            defaultTab="issue"
          />
        </div>

        <button
          type="button"
          className="fixed right-3 top-1/2 z-30 hidden -translate-y-1/2 flex-col items-center gap-1 rounded-l-lg border border-r-0 border-solid border-[#eceef2] bg-white px-2 py-3 text-[11px] tracking-[0.16em] text-[#667085] shadow-[0_4px_16px_rgba(16,24,40,0.05)] 2xl:flex"
          title="当前为前端总览原型，指标口径将在数据接入阶段补充"
        >
          <Info size={13} />
          {'数据指标说明'.split('').map((char, index) => (
            <span key={`${char}-${index}`}>{char}</span>
          ))}
        </button>
      </div>
    </ConfigProvider>
  );
}
