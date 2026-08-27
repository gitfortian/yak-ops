import { YakEmpty } from '@/components/ui';
import type {
  QualityOverviewTrendPoint,
  QualityOverviewView,
} from '@/services/data-quality';
import { useEffect, useMemo, useRef, useState } from 'react';
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

const DEFAULT_WIDTH = 1040;
const MIN_CHART_WIDTH = 640;
const HEIGHT = 300;
const PADDING = { left: 54, right: 24, top: 28, bottom: 42 };

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
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const [chartWidth, setChartWidth] = useState(DEFAULT_WIDTH);
  const [activeIndex, setActiveIndex] = useState<number>();
  const trend = overview?.trend ?? [];
  const series = useMemo(() => seriesFor(section, tabKey), [section, tabKey]);

  useEffect(() => {
    const container = chartContainerRef.current;
    if (!container) return;

    const syncWidth = () => {
      setChartWidth(Math.max(MIN_CHART_WIDTH, Math.floor(container.clientWidth)));
    };

    syncWidth();
    if (typeof ResizeObserver === 'undefined') return;

    const observer = new ResizeObserver(syncWidth);
    observer.observe(container);
    return () => observer.disconnect();
  }, []);

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

  const plotWidth = chartWidth - PADDING.left - PADDING.right;
  const chartHeight = HEIGHT - PADDING.top - PADDING.bottom;
  const maxValue = Math.max(1, ...values);
  const xAt = (index: number) =>
    PADDING.left + (trend.length === 1 ? plotWidth / 2 : (index / (trend.length - 1)) * plotWidth);
  const yAt = (value: number) => PADDING.top + chartHeight - (value / maxValue) * chartHeight;
  const pointsFor = (item: SeriesDefinition) =>
    trend.map((point, index) => `${xAt(index)},${yAt(item.value(point))}`).join(' ');
  const tickIndexes = trend.length <= 8
    ? trend.map((_, index) => index)
    : [0, Math.floor((trend.length - 1) / 3), Math.floor(((trend.length - 1) * 2) / 3), trend.length - 1];
  const active = activeIndex === undefined ? undefined : trend[activeIndex];

  return (
    <div className="relative min-h-[320px] px-4 pb-2 pt-4" onMouseLeave={() => setActiveIndex(undefined)}>
      <div className="mb-1 flex justify-end gap-4 pr-3 text-[11px] text-[#667085]">
        {series.map((item) => (
          <span key={item.label} className="inline-flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full" style={{ background: item.color }} />
            {item.label}
          </span>
        ))}
      </div>
      <div ref={chartContainerRef} className="w-full">
        <svg
          viewBox={`0 0 ${chartWidth} ${HEIGHT}`}
          className="h-[285px] w-full overflow-visible"
          preserveAspectRatio="none"
        >
          {[0, 0.25, 0.5, 0.75, 1].map((ratio) => {
            const y = PADDING.top + chartHeight * ratio;
            const label = Math.round(maxValue * (1 - ratio));
            return (
              <g key={ratio}>
                <line x1={PADDING.left} y1={y} x2={chartWidth - PADDING.right} y2={y} stroke="#eef0f3" />
                <text x={PADDING.left - 10} y={y + 4} textAnchor="end" fontSize="10" fill="#a0a6af">
                  {label}
                </text>
              </g>
            );
          })}
          {series.map((item) => (
            <polyline
              key={item.label}
              points={pointsFor(item)}
              fill="none"
              stroke={item.color}
              strokeWidth="2.2"
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          ))}
          {tickIndexes.map((index) => (
            <text
              key={`tick-${index}`}
              x={xAt(index)}
              y={HEIGHT - 14}
              textAnchor="middle"
              fontSize="10"
              fill="#98a2b3"
            >
              {trend[index].date.slice(5)}
            </text>
          ))}
          {trend.map((point, index) => {
            const half = trend.length === 1 ? plotWidth / 2 : plotWidth / Math.max(2, trend.length - 1) / 2;
            return (
              <rect
                key={point.date}
                x={Math.max(PADDING.left, xAt(index) - half)}
                y={PADDING.top}
                width={Math.min(plotWidth, half * 2)}
                height={chartHeight}
                fill="transparent"
                onMouseEnter={() => setActiveIndex(index)}
              />
            );
          })}
          {activeIndex !== undefined
            ? series.map((item) => (
                <circle
                  key={`active-${item.label}`}
                  cx={xAt(activeIndex)}
                  cy={yAt(item.value(trend[activeIndex]))}
                  r="4"
                  fill="#fff"
                  stroke={item.color}
                  strokeWidth="2"
                />
              ))
            : null}
        </svg>
      </div>

      {active && activeIndex !== undefined ? (
        <div
          className="pointer-events-none absolute top-12 z-10 min-w-[150px] rounded-md border border-solid border-[#e5e7eb] bg-white px-3 py-2 text-[11px] shadow-[0_6px_18px_rgba(16,24,40,0.08)]"
          style={{ left: `${Math.min(82, Math.max(8, (xAt(activeIndex) / chartWidth) * 100))}%` }}
        >
          <div className="mb-1.5 font-medium text-[#30343b]">{active.date}</div>
          {series.map((item) => (
            <div key={item.label} className="flex items-center justify-between gap-6 py-0.5 text-[#667085]">
              <span>{item.label}</span>
              <strong className="font-semibold text-[#30343b]">{formatCount(item.value(active))}</strong>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}
