import { YakButton, YakEmpty } from '@/components/ui';
import type { QualityOverviewView } from '@/services/data-quality';
import { BRAND_CSS_VARIABLES } from '@/styles/brand';
import { history } from '@umijs/max';
import { Spin } from 'antd';
import { ArrowRight, CircleHelp } from 'lucide-react';
import { useMemo, useState } from 'react';
import { QUALITY_RADAR_DIMENSIONS } from '../constants';
import { findDimension, formatCount, formatRate } from '../utils';

const RADAR_CENTER = 130;
const RADAR_RADIUS = 92;
const RADAR_ANGLES = [-90, -18, 54, 126, 198];

const pointAt = (radius: number, angle: number) => {
  const radians = (Math.PI / 180) * angle;
  return [
    RADAR_CENTER + Math.cos(radians) * radius,
    RADAR_CENTER + Math.sin(radians) * radius,
  ] as const;
};

const polygonPoints = (radius: number) =>
  RADAR_ANGLES.map((angle) => pointAt(radius, angle).join(',')).join(' ');

const metricPositionClass = [
  'left-1/2 top-0 -translate-x-1/2',
  'right-0 top-[40%] -translate-y-1/2',
  'right-[13%] bottom-[36px]',
  'left-[13%] bottom-[36px]',
  'left-0 top-[40%] -translate-y-1/2',
];

interface QualityRadarOverviewProps {
  periodText: string;
  overview?: QualityOverviewView;
  loading?: boolean;
}

interface RadarMetric {
  key: string;
  label: string;
  description: string;
  passRate?: number;
  total: number;
  issues: number;
}

const QualityMetricCard = ({
  metric,
  active,
  position,
  onActivate,
}: {
  metric: RadarMetric;
  active: boolean;
  position: string;
  onActivate: () => void;
}) => (
  <button
    type="button"
    onClick={onActivate}
    className={[
      'absolute z-10 min-w-[136px] rounded-lg bg-white px-3 py-2.5 text-left',
      'transition-[border-color] duration-150',
      'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--yak-brand-color-outline)]',
      active
        ? 'border border-solid border-[var(--yak-brand-color)]'
        : 'border border-solid border-[#e5e7eb] hover:border-[var(--yak-brand-color-border)]',
      position,
    ].join(' ')}
  >
    <div className="text-[12px] font-semibold text-[#252a34]">
      {metric.label} {formatRate(metric.passRate)}
    </div>
    <div className="mt-1 text-[11px] leading-4 text-[#98a2b3]">
      {metric.total > 0
        ? `执行 ${formatCount(metric.total)} 条 · 问题 ${formatCount(metric.issues)}`
        : '暂无执行数据'}
    </div>
  </button>
);

const QualityRadar = ({
  metrics,
  onActivate,
}: {
  metrics: RadarMetric[];
  onActivate: (key: string) => void;
}) => {
  const hasCompleteRadar = metrics.every(
    (metric) => metric.passRate !== undefined && metric.passRate !== null,
  );
  const dataPoints = hasCompleteRadar
    ? metrics
        .map((metric, index) => {
          const rate = Math.max(0, Math.min(100, metric.passRate ?? 0));
          return pointAt((RADAR_RADIUS * rate) / 100, RADAR_ANGLES[index]).join(',');
        })
        .join(' ')
    : '';

  return (
    <svg
      viewBox="0 0 260 260"
      aria-label="数据质量五维雷达图"
      className="absolute left-1/2 top-[47%] h-[250px] w-[250px] -translate-x-1/2 -translate-y-1/2"
    >
      {[1, 0.8, 0.6, 0.4, 0.2].map((ratio) => (
        <polygon
          key={ratio}
          points={polygonPoints(RADAR_RADIUS * ratio)}
          fill="none"
          stroke="#e8ebf0"
          strokeWidth="1"
        />
      ))}
      {RADAR_ANGLES.map((angle) => {
        const [x, y] = pointAt(RADAR_RADIUS, angle);
        return (
          <line
            key={angle}
            x1={RADAR_CENTER}
            y1={RADAR_CENTER}
            x2={x}
            y2={y}
            stroke="#eef0f3"
            strokeWidth="1"
          />
        );
      })}

      {hasCompleteRadar ? (
        <polygon
          points={dataPoints}
          fill="var(--yak-brand-color-soft)"
          stroke="var(--yak-brand-color)"
          strokeWidth="2"
          strokeLinejoin="round"
        />
      ) : null}

      {metrics.map((metric, index) => {
        const [targetX, targetY] = pointAt(RADAR_RADIUS, RADAR_ANGLES[index]);
        const hasRate = metric.passRate !== undefined && metric.passRate !== null;
        const rate = Math.max(0, Math.min(100, metric.passRate ?? 0));
        const [dataX, dataY] = pointAt((RADAR_RADIUS * rate) / 100, RADAR_ANGLES[index]);
        return (
          <g
            key={metric.key}
            role="button"
            tabIndex={0}
            aria-label={`${metric.label} ${formatRate(metric.passRate)}`}
            onClick={() => onActivate(metric.key)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onActivate(metric.key);
              }
            }}
            className="cursor-pointer"
          >
            <circle
              cx={targetX}
              cy={targetY}
              r={4}
              fill="#fff"
              stroke="#d9dde4"
              strokeWidth={1.5}
            />
            {hasRate ? (
              <circle
                cx={dataX}
                cy={dataY}
                r={3.5}
                fill="#fff"
                stroke="#d9dde4"
                strokeWidth="1.5"
              />
            ) : null}
          </g>
        );
      })}
    </svg>
  );
};

const buildAnalysis = (overview: QualityOverviewView | undefined, active: RadarMetric) => {
  const summary = overview?.summary;
  if (!summary || summary.executedRuleCount <= 0) {
    return '当前统计周期暂无已完成的质量规则执行数据。完成监控运行后，这里会根据真实执行结果分析各质量维度。';
  }
  const overall = summary.passRate;
  const activeRate = active.passRate;
  const overallText = overall === undefined ? '暂无整体通过率' : `整体通过率 ${overall.toFixed(1)}%`;
  const activeText = activeRate === undefined
    ? `${active.label}暂无可计算通过率的数据`
    : `${active.label}通过率 ${activeRate.toFixed(1)}%，共执行 ${formatCount(active.total)} 条规则，其中 ${formatCount(active.issues)} 条存在问题`;
  if ((overall ?? 0) >= 95) {
    return `${overallText}，当前整体质量表现稳定。${activeText}。`;
  }
  if ((overall ?? 0) >= 80) {
    return `${overallText}，整体质量基本稳定，但仍存在需要关注的质量问题。${activeText}。`;
  }
  return `${overallText}，当前质量风险较高，建议优先处理问题贡献较高的维度和规则。${activeText}。`;
};

export default function QualityRadarOverview({
  periodText,
  overview,
  loading = false,
}: QualityRadarOverviewProps) {
  const metrics = useMemo<RadarMetric[]>(
    () => QUALITY_RADAR_DIMENSIONS.map((definition) => {
      const dimension = findDimension(overview?.dimensions, definition.aliases);
      return {
        key: definition.key,
        label: definition.label,
        description: definition.description,
        passRate: dimension?.passRate,
        total: dimension?.total ?? 0,
        issues: dimension?.issues ?? 0,
      };
    }),
    [overview?.dimensions],
  );
  const [activeKey, setActiveKey] = useState(QUALITY_RADAR_DIMENSIONS[0].key);
  const activeMetric = metrics.find((metric) => metric.key === activeKey) ?? metrics[0];
  const contributors = overview?.issueContributors ?? [];

  return (
    <section
      className="rounded-xl bg-white px-5 py-5 lg:px-6"
      style={BRAND_CSS_VARIABLES}
    >
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <h1 className="m-0 text-[18px] font-semibold text-[#161823]">质量总览</h1>
        <span className="inline-flex items-center gap-1 text-[11px] text-[#98a2b3]">
          <CircleHelp size={13} />
          {periodText}
        </span>
      </div>

      <Spin spinning={loading}>
        <div className="mt-4 grid gap-8 xl:grid-cols-[520px_minmax(0,1fr)]">
          <div className="min-w-0 overflow-x-auto">
            <div className="relative mx-auto h-[350px] min-w-[500px] max-w-[520px]">
              <QualityRadar metrics={metrics} onActivate={setActiveKey} />
              {metrics.map((metric, index) => (
                <QualityMetricCard
                  key={metric.key}
                  metric={metric}
                  active={metric.key === activeKey}
                  position={metricPositionClass[index] ?? ''}
                  onActivate={() => setActiveKey(metric.key)}
                />
              ))}
              <div className="absolute bottom-0 left-1/2 flex -translate-x-1/2 items-center gap-4 whitespace-nowrap text-[11px] text-[#667085]">
                <span className="flex items-center gap-1">
                  <span className="h-1.5 w-1.5 rounded-full bg-[var(--yak-brand-color)]" />
                  当前通过率
                </span>
                <span className="flex items-center gap-1">
                  <span className="h-2 w-2 rounded-sm border border-solid border-[#d8dce3]" />
                  100% 健康边界
                </span>
              </div>
            </div>
          </div>

          <div className="min-w-0 pt-1">
            <h2 className="m-0 text-[16px] font-semibold text-[#161823]">质量分析</h2>
            <div className="mt-3 rounded-lg bg-[#f7f8fa] px-4 py-3 text-[12px] leading-6 text-[#7d8592]">
              <span className="font-medium text-[#4b5563]">{activeMetric.label}：</span>
              {buildAnalysis(overview, activeMetric)}
            </div>

            <div className="mt-4 flex items-center justify-between gap-4">
              <h3 className="m-0 text-[15px] font-semibold text-[#161823]">问题贡献 TOP3</h3>
              <YakButton
                type="text"
                size="small"
                className="!text-[12px] !text-[#667085]"
                onClick={() => history.push('/data-quality/execution')}
              >
                查看运行记录 <ArrowRight size={13} />
              </YakButton>
            </div>

            <div className="mt-2 min-h-[190px] rounded-lg bg-[#f7f8fa] p-4">
              {contributors.length ? (
                <div className="space-y-3">
                  {contributors.map((item, index) => (
                    <button
                      key={item.dimension}
                      type="button"
                      onClick={() => history.push('/data-quality/execution')}
                      className="grid w-full grid-cols-[28px_minmax(0,1fr)_76px] items-center gap-3 rounded-md border-0 bg-transparent px-2 py-2 text-left transition-colors hover:bg-white"
                    >
                      <span className="flex h-6 w-6 items-center justify-center rounded-md bg-white text-[11px] font-semibold text-[#667085] shadow-[0_1px_2px_rgba(16,24,40,0.04)]">
                        {index + 1}
                      </span>
                      <span className="min-w-0">
                        <span className="flex items-center justify-between gap-3 text-[12px]">
                          <strong className="truncate font-medium text-[#30343b]">{item.dimension}</strong>
                          <span className="shrink-0 text-[#98a2b3]">{formatRate(item.ratio)}</span>
                        </span>
                        <span className="mt-1 block h-1.5 overflow-hidden rounded-full bg-[#e8ebef]">
                          <span
                            className="block h-full rounded-full bg-[var(--yak-brand-color)]"
                            style={{ width: `${Math.max(4, Math.min(100, item.ratio ?? 0))}%` }}
                          />
                        </span>
                      </span>
                      <span className="text-right text-[12px] font-medium text-[#30343b]">
                        {formatCount(item.issues)} 条
                      </span>
                    </button>
                  ))}
                </div>
              ) : (
                <YakEmpty
                  compact
                  title="近 7 日暂无问题数据"
                  description="统计周期内出现未通过或异常规则后，这里将展示问题贡献最高的质量维度"
                />
              )}
            </div>
          </div>
        </div>
      </Spin>
    </section>
  );
}
