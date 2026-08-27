import { YakButton, YakEmpty } from '@/components/ui';
import { history } from '@umijs/max';
import { ArrowRight, CircleHelp } from 'lucide-react';
import type { QualityRadarMetric } from '../constants';

const RADAR_CENTER = 130;
const RADAR_RADIUS = 92;
const RADAR_ANGLES = [-90, -18, 54, 126, 198];

const pointAt = (radius: number, angle: number) => {
  const radians = (Math.PI / 180) * angle;
  return [
    RADAR_CENTER + Math.cos(radians) * radius,
    RADAR_CENTER + Math.sin(radians) * radius,
  ];
};

const polygonPoints = (radius: number) =>
  RADAR_ANGLES.map((angle) => pointAt(radius, angle).join(',')).join(' ');

const metricPositionClass = [
  'left-1/2 top-0 -translate-x-1/2',
  'right-0 top-[40%] -translate-y-1/2',
  'right-[13%] bottom-[34px]',
  'left-[13%] bottom-[34px]',
  'left-0 top-[40%] -translate-y-1/2',
];

interface QualityRadarOverviewProps {
  periodText: string;
  metrics: QualityRadarMetric[];
}

const QualityMetricCard = ({
  metric,
  active,
  position,
}: {
  metric: QualityRadarMetric;
  active?: boolean;
  position: string;
}) => (
  <div
    className={[
      'absolute z-10 min-w-[130px] rounded-lg bg-white px-3 py-2.5 shadow-[0_1px_2px_rgba(16,24,40,0.02)]',
      active
        ? 'border-2 border-solid border-[#4f7cff]'
        : 'border border-solid border-[#e5e7eb]',
      position,
    ].join(' ')}
  >
    <div className="text-[12px] font-semibold text-[#252a34]">
      {metric.label} {metric.value}
    </div>
    <div className="mt-1 text-[11px] leading-4 text-[#98a2b3]">
      {metric.caption}
    </div>
  </div>
);

const QualityRadar = () => (
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
    {RADAR_ANGLES.map((angle) => {
      const [x, y] = pointAt(RADAR_RADIUS, angle);
      return (
        <circle
          key={`node-${angle}`}
          cx={x}
          cy={y}
          r="4"
          fill="#fff"
          stroke="#d9dde4"
          strokeWidth="1.5"
        />
      );
    })}
    <circle
      cx={RADAR_CENTER}
      cy={RADAR_CENTER}
      r="4"
      fill="#fff"
      stroke="#4f7cff"
      strokeWidth="2"
    />
  </svg>
);

export default function QualityRadarOverview({
  periodText,
  metrics,
}: QualityRadarOverviewProps) {
  return (
    <section className="rounded-xl bg-white px-5 py-5 lg:px-6">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <h1 className="m-0 text-[18px] font-semibold text-[#161823]">
          质量总览
        </h1>
        <span className="inline-flex items-center gap-1 text-[11px] text-[#98a2b3]">
          <CircleHelp size={13} />
          {periodText}
        </span>
      </div>

      <div className="mt-4 grid gap-8 xl:grid-cols-[520px_minmax(0,1fr)]">
        <div className="min-w-0 overflow-x-auto">
          <div className="relative mx-auto h-[350px] min-w-[500px] max-w-[520px]">
            <QualityRadar />
            {metrics.map((metric, index) => (
              <QualityMetricCard
                key={metric.key}
                metric={metric}
                active={index === 0}
                position={metricPositionClass[index] ?? ''}
              />
            ))}
            <div className="absolute bottom-0 left-1/2 flex -translate-x-1/2 items-center gap-4 whitespace-nowrap text-[11px] text-[#667085]">
              <span className="flex items-center gap-1">
                <span className="h-1.5 w-1.5 rounded-full bg-[#4f7cff]" />
                当前指标
              </span>
              <span className="flex items-center gap-1">
                <span className="h-2 w-2 rounded-sm border border-solid border-[#d8dce3]" />
                健康目标
              </span>
            </div>
          </div>
        </div>

        <div className="min-w-0 pt-1">
          <h2 className="m-0 text-[16px] font-semibold text-[#161823]">
            质量分析
          </h2>
          <div className="mt-3 rounded-lg bg-[#f7f8fa] px-4 py-3 text-[12px] leading-6 text-[#7d8592]">
            当前暂无质量执行数据。完成监控和规则运行后，这里将结合完整性、唯一性、有效性、准确性和及时性等指标，给出质量表现分析与优化建议。
          </div>

          <div className="mt-4 flex items-center justify-between gap-4">
            <h3 className="m-0 text-[15px] font-semibold text-[#161823]">
              问题贡献 TOP3
            </h3>
            <YakButton
              type="text"
              size="small"
              className="!text-[12px] !text-[#667085]"
              onClick={() => history.push('/data-quality/execution')}
            >
              查看运行记录 <ArrowRight size={13} />
            </YakButton>
          </div>

          <div className="mt-2 flex min-h-[190px] items-center justify-center rounded-lg bg-[#f7f8fa]">
            <YakEmpty
              compact
              title="近 7 日暂无问题数据"
              description="完成一次质量监控后，这里将展示问题贡献最高的规则或数据对象"
            />
          </div>
        </div>
      </div>
    </section>
  );
}
