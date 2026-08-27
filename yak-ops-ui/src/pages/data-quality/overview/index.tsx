import { BRAND_CSS_VARIABLES, BRAND_THEME } from '@/styles/brand';
import { ConfigProvider, Popover } from 'antd';
import { Info } from 'lucide-react';
import QualityMetricSection from './components/QualityMetricSection';
import QualityRadarOverview from './components/QualityRadarOverview';
import {
  QUALITY_EXECUTION_TABS,
  QUALITY_ISSUE_TABS,
  QUALITY_METRIC_EXPLANATIONS,
} from './constants';
import { useQualityOverviewPage } from './hooks/useQualityOverviewPage';
import { formatPeriodText } from './utils';

const MetricExplanationPanel = () => (
  <div className="w-[420px] max-w-[calc(100vw-88px)] overflow-hidden rounded-xl bg-white">
    <div className="flex items-center gap-3 border-b border-solid border-[#eef0f2] px-4 py-3.5">
      <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-[var(--yak-brand-color-soft)] text-[var(--yak-brand-color)]">
        <Info size={15} />
      </span>
      <div className="min-w-0">
        <div className="text-[14px] font-semibold text-[#161823]">数据指标说明</div>
        <div className="mt-0.5 text-[11px] text-[#98a2b3]">质量总览统计口径与指标含义</div>
      </div>
    </div>

    <div className="max-h-[min(560px,68vh)] overflow-y-auto p-4">
      <div className="rounded-lg bg-[#f7f8fa] px-3.5 py-3 text-[11px] leading-5 text-[#667085]">
        默认统计到昨天，支持昨天、近 7 天、近 30 天及自定义日期范围；单次查询最长 90 天。
      </div>

      <div className="mt-3 grid grid-cols-2 gap-2.5">
        {QUALITY_METRIC_EXPLANATIONS.map(([label, description]) => (
          <div
            key={label}
            className="rounded-lg border border-solid border-[#eceef2] bg-white px-3 py-2.5"
          >
            <div className="text-[12px] font-semibold text-[#30343b]">{label}</div>
            <div className="mt-1 text-[11px] leading-[18px] text-[#8a9099]">{description}</div>
          </div>
        ))}
      </div>

      <div className="mt-3 rounded-lg border border-solid border-[#eceef2] px-3.5 py-3 text-[11px] leading-5 text-[#98a2b3]">
        雷达图仅对已经产生规则执行事实的质量维度计算通过率；没有执行事实的维度显示为“--”，不会用 0% 冒充真实质量得分。
      </div>
    </div>
  </div>
);

export default function DataQualityOverviewPage() {
  const overview = useQualityOverviewPage();

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div
        className="min-h-[calc(100vh-64px)] bg-[#f6f7f9] text-[#161823]"
        style={BRAND_CSS_VARIABLES}
      >
        <div className="mx-auto w-full max-w-[1900px] space-y-5 px-4 py-4 lg:px-5">
          <QualityRadarOverview
            periodText={formatPeriodText(overview.radarRange)}
            overview={overview.radarOverview}
            loading={overview.radarLoading}
          />

          <QualityMetricSection
            title="质量检测数据"
            tabs={QUALITY_EXECUTION_TABS}
            defaultTab="execution"
            section="quality"
            range={overview.qualityRange}
            overview={overview.qualityOverview}
            loading={overview.qualityLoading}
            onRangeChange={overview.setQualityRange}
          />

          <QualityMetricSection
            title="问题数据"
            tabs={QUALITY_ISSUE_TABS}
            defaultTab="issue"
            section="issue"
            range={overview.issueRange}
            overview={overview.issueOverview}
            loading={overview.issueLoading}
            onRangeChange={overview.setIssueRange}
          />
        </div>

        <Popover
          trigger="hover"
          placement="leftTop"
          arrow={false}
          mouseEnterDelay={0.12}
          mouseLeaveDelay={0.15}
          content={<MetricExplanationPanel />}
          overlayInnerStyle={{ padding: 0, borderRadius: 12 }}
        >
          <button
            type="button"
            aria-label="查看数据指标说明"
            className="fixed right-0 top-[42%] z-30 hidden -translate-y-1/2 flex-col items-center gap-1.5 rounded-l-lg border border-r-0 border-solid border-[#eceef2] bg-white px-2 py-3 text-[11px] font-medium text-[#667085] shadow-[0_4px_16px_rgba(16,24,40,0.05)] transition-[border-color,color,background-color] hover:border-[var(--yak-brand-color-border)] hover:bg-[#fffafb] hover:text-[var(--yak-brand-color)] 2xl:flex"
          >
            <Info size={13} />
            <span style={{ writingMode: 'vertical-rl' }} className="tracking-[0.12em]">
              数据指标说明
            </span>
          </button>
        </Popover>
      </div>
    </ConfigProvider>
  );
}
