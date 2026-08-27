import { YakEmpty } from '@/components/ui';
import type {
  QualityOverviewTrendPoint,
  QualityOverviewView,
} from '@/services/data-quality';
import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import { useMemo } from 'react';
import type { OverviewSectionKind } from '../utils';
import { formatCount } from '../utils';

interface QualityTrendChartProps {
  overview?: QualityOverviewView;
  section: OverviewSectionKind;
  tabKey: string;
}

interface SeriesDefinition {
  label: string;
  color: string;
  value: (point: QualityOverviewTrendPoint) => number;
}

const seriesFor = (
  section: OverviewSectionKind,
  tabKey: string,
): SeriesDefinition[] => {
  if (section === 'issue') {
    return [
      { label: '未通过规则', color: '#fe2c55', value: (point) => point.failedRuleCount },
      { label: '异常规则', color: '#f59e0b', value: (point) => point.errorRuleCount },
    ];
  }
  if (tabKey === 'monitor') {
    return [
      { label: '活跃监控', color: '#4f7cff', value: (point) => point.activeMonitorCount },
      { label: '问题执行', color: '#fe2c55', value: (point) => point.issueExecutionCount },
    ];
  }
  if (tabKey === 'rule') {
    return [
      { label: '通过规则', color: '#4f7cff', value: (point) => point.passedRuleCount },
      {
        label: '问题规则',
        color: '#fe2c55',
        value: (point) => point.failedRuleCount + point.errorRuleCount,
      },
    ];
  }
  return [
    { label: '执行次数', color: '#4f7cff', value: (point) => point.executionCount },
    { label: '问题执行', color: '#fe2c55', value: (point) => point.issueExecutionCount },
  ];
};

const DimensionBars = ({ overview }: { overview?: QualityOverviewView }) => {
  const rows = (overview?.dimensions ?? []).filter((item) => item.issues > 0);
  const max = Math.max(1, ...rows.map((item) => item.issues));
  if (!rows.length) {
    return (
      <YakEmpty
        compact
        title="暂无维度问题分布"
        description="统计周期内出现质量问题后，这里会展示各质量维度的问题贡献"
      />
    );
  }
  return (
    <div className="mx-auto w-full max-w-[920px] space-y-4 px-6 py-8">
      {rows.map((item) => (
        <div key={item.dimension} className="grid grid-cols-[90px_minmax(0,1fr)_70px] items-center gap-3">
          <span className="truncate text-[12px] text-[#667085]">{item.dimension}</span>
          <div className="h-3 overflow-hidden rounded-full bg-[#f0f2f5]">
            <div
              className="h-full rounded-full bg-[#4f7cff] transition-[width] duration-300"
              style={{ width: `${Math.max(4, (item.issues / max) * 100)}%` }}
            />
          </div>
          <span className="text-right text-[12px] font-medium text-[#30343b]">
            {formatCount(item.issues)}
          </span>
        </div>
      ))}
    </div>
  );
};

export default function QualityTrendChart({
  overview,
  section,
  tabKey,
}: QualityTrendChartProps) {
  const trend = overview?.trend ?? [];
  const series = useMemo(() => seriesFor(section, tabKey), [section, tabKey]);

  if (section === 'issue' && tabKey === 'dimension') {
    return <DimensionBars overview={overview} />;
  }

  const values = trend.flatMap((point) => series.map((item) => item.value(point)));
  const hasData = values.some((value) => value > 0);
  if (!trend.length || !hasData) {
    return (
      <YakEmpty
        compact
        title={section === 'issue' ? '暂无问题趋势数据' : '暂无质量趋势数据'}
        description="当前统计周期没有可绘制的数据，切换时间范围后可重新查询"
      />
    );
  }

  const option: EChartsOption = {
    animationDuration: 260,
    grid: {
      left: 20,
      right: 20,
      top: 48,
      bottom: 20,
      containLabel: true,
    },
    legend: {
      top: 8,
      right: 12,
      itemWidth: 8,
      itemHeight: 8,
      icon: 'circle',
      textStyle: {
        color: '#667085',
        fontSize: 11,
      },
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#fff',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      padding: [10, 12],
      textStyle: {
        color: '#30343b',
        fontSize: 12,
      },
      extraCssText: 'box-shadow:0 6px 18px rgba(16,24,40,.08);border-radius:8px;',
      axisPointer: {
        type: 'line',
        lineStyle: {
          color: '#d9dde4',
          width: 1,
          type: 'dashed',
        },
      },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.map((point) => point.date),
      axisLine: {
        lineStyle: {
          color: '#e7e9ee',
        },
      },
      axisTick: {
        show: false,
      },
      axisLabel: {
        color: '#98a2b3',
        fontSize: 10,
        margin: 12,
        formatter: (value: string) => value.slice(5),
      },
    },
    yAxis: {
      type: 'value',
      min: 0,
      minInterval: 1,
      axisLine: {
        show: false,
      },
      axisTick: {
        show: false,
      },
      axisLabel: {
        color: '#a0a6af',
        fontSize: 10,
        margin: 12,
      },
      splitLine: {
        lineStyle: {
          color: '#eef0f3',
          width: 1,
        },
      },
    },
    series: series.map((item) => ({
      name: item.label,
      type: 'line',
      data: trend.map((point) => item.value(point)),
      smooth: false,
      showSymbol: true,
      symbol: 'circle',
      symbolSize: trend.length === 1 ? 8 : 6,
      lineStyle: {
        color: item.color,
        width: 2,
      },
      itemStyle: {
        color: '#fff',
        borderColor: item.color,
        borderWidth: 2,
      },
      emphasis: {
        focus: 'series',
        itemStyle: {
          color: '#fff',
          borderColor: item.color,
          borderWidth: 2,
        },
      },
    })),
  };

  return (
    <div className="min-h-[320px] px-3 pb-2 pt-2">
      <ReactECharts
        option={option}
        notMerge
        lazyUpdate
        style={{ width: '100%', height: 310 }}
        opts={{ renderer: 'svg' }}
      />
    </div>
  );
}
