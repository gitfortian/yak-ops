import { BRAND_THEME } from '@/styles/brand';
import { ConfigProvider, Drawer } from 'antd';
import { Info } from 'lucide-react';
import { useState } from 'react';
import QualityMetricSection from './components/QualityMetricSection';
import QualityRadarOverview from './components/QualityRadarOverview';
import {
  QUALITY_EXECUTION_TABS,
  QUALITY_ISSUE_TABS,
  QUALITY_METRIC_EXPLANATIONS,
} from './constants';
import { useQualityOverviewPage } from './hooks/useQualityOverviewPage';
import { formatPeriodText } from './utils';

export default function DataQualityOverviewPage() {
  const overview = useQualityOverviewPage();
  const [explanationOpen, setExplanationOpen] = useState(false);

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="min-h-[calc(100vh-64px)] bg-[#f6f7f9] text-[#161823]">
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
            onRefresh={() => void overview.refresh(overview.qualityRange)}
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
            onRefresh={() => void overview.refresh(overview.issueRange)}
          />
        </div>

        <button
          type="button"
          onClick={() => setExplanationOpen(true)}
          className="fixed right-3 top-1/2 z-30 hidden -translate-y-1/2 flex-col items-center gap-1 rounded-l-lg border border-r-0 border-solid border-[#eceef2] bg-white px-2 py-3 text-[11px] tracking-[0.16em] text-[#667085] shadow-[0_4px_16px_rgba(16,24,40,0.05)] transition-colors hover:text-[#30343b] 2xl:flex"
          title="查看数据质量总览指标口径"
        >
          <Info size={13} />
          {'数据指标说明'.split('').map((char, index) => (
            <span key={`${char}-${index}`}>{char}</span>
          ))}
        </button>

        <Drawer
          open={explanationOpen}
          title="数据指标说明"
          width={420}
          onClose={() => setExplanationOpen(false)}
        >
          <div className="rounded-lg bg-[#f7f8fa] px-4 py-3 text-[12px] leading-6 text-[#667085]">
            质量总览基于数据质量执行事实进行聚合。默认统计到昨天，支持昨天、近 7 天、近 30 天及自定义日期范围；单次查询最长 90 天。
          </div>
          <div className="mt-4 divide-y divide-[#eef0f2]">
            {QUALITY_METRIC_EXPLANATIONS.map(([label, description]) => (
              <div key={label} className="py-3">
                <div className="text-[13px] font-medium text-[#30343b]">{label}</div>
                <div className="mt-1 text-[12px] leading-5 text-[#8a9099]">{description}</div>
              </div>
            ))}
          </div>
          <div className="mt-4 rounded-lg border border-solid border-[#eceef2] px-4 py-3 text-[11px] leading-5 text-[#98a2b3]">
            雷达图仅对已经产生规则执行事实的质量维度计算通过率。没有执行事实的维度显示为“--”，不会用 0% 冒充真实质量得分。
          </div>
        </Drawer>
      </div>
    </ConfigProvider>
  );
}
