import YakOpsEmpty from '@/components/YakOpsEmpty';
import type { HomeDataCenterOverview } from '@/services/home';
import { useMemo } from 'react';

import type { HomeDataCenterPeriodKey } from '../../types';
import { toOverviewMetrics } from '../../utils/homeDataCenter';
import { OverviewMetrics } from './OverviewMetrics';
import { TrendChart } from './TrendChart';

interface OverviewPanelProps {
  overview?: HomeDataCenterOverview;
  periodKey: HomeDataCenterPeriodKey;
  periodLabel: string;
  loading: boolean;
  failed: boolean;
}

export function OverviewPanel({
  overview,
  periodKey,
  periodLabel,
  loading,
  failed,
}: OverviewPanelProps) {
  const overviewMetrics = useMemo(
    () => toOverviewMetrics(overview, periodKey),
    [overview, periodKey],
  );
  const trendLabels = overview?.trend?.labels || [];
  const trendValues = overview?.trend?.values || [];
  const hasTrendData = trendValues.some((value) => value > 0);

  return (
    <div>
      <div className="mt-2 flex items-center justify-end gap-1.5 text-[12px] text-[#7f848e]">
        <span className="h-2 w-2 rounded-full bg-[#5b8cff]" />
        运行次数
      </div>
      {loading || failed ? (
        <div className="flex h-[152px] items-center justify-center text-[12px] text-[#9da1a8]">
          {loading ? '运行数据加载中...' : '运行数据加载失败'}
        </div>
      ) : hasTrendData ? (
        <TrendChart
          key={`trend-${periodKey}`}
          values={trendValues}
          labels={trendLabels}
          name="运行次数"
        />
      ) : (
        <div className="flex h-[152px] items-center justify-center">
          <YakOpsEmpty
            width={120}
            height={80}
            title={`${periodLabel}暂无运行数据`}
            showCaption
          />
        </div>
      )}
      <OverviewMetrics metrics={overviewMetrics} />
    </div>
  );
}
