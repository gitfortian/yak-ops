import { useMemo } from "react";

import type { GovernanceHealthPoint } from "@/service/data-governance/asset-governance/types";

interface Series {
  key: string;
  label: string;
  color: string;
}

const SERIES: Series[] = [
  { key: "OVERALL", label: "综合", color: "var(--yak-color-primary)" },
  { key: "DEV", label: "数据资产", color: "var(--yak-datamap-badge-purple-text)" },
  { key: "COMPUTE", label: "计算任务", color: "var(--yak-datamap-badge-cyan-text)" },
];

const WIDTH = 720;
const HEIGHT = 200;
const PADDING = { top: 14, right: 12, bottom: 24, left: 34 };

interface Props {
  points: GovernanceHealthPoint[];
  from: string;
  to: string;
}

/** 90 天健康分趋势：缺失的快照日渲染为断口，而不是当作 0 分。 */
export default function HealthTrend({ points, from, to }: Props) {
  const days = useMemo(() => buildDayAxis(from, to), [from, to]);
  const byKey = useMemo(() => {
    const map = new Map<string, number>();
    points.forEach((point) => {
      if (point.score != null) map.set(`${point.dimensionCode}@${point.snapshotDate}`, point.score);
    });
    return map;
  }, [points]);

  const innerW = WIDTH - PADDING.left - PADDING.right;
  const innerH = HEIGHT - PADDING.top - PADDING.bottom;
  const x = (index: number) =>
    PADDING.left + (days.length <= 1 ? innerW / 2 : (index / (days.length - 1)) * innerW);
  const y = (score: number) => PADDING.top + (1 - score / 100) * innerH;

  return (
    <svg
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      className="h-[200px] w-full"
      role="img"
      aria-label="健康分趋势"
    >
      {[0, 50, 100].map((score) => (
        <g key={score}>
          <line
            x1={PADDING.left}
            x2={WIDTH - PADDING.right}
            y1={y(score)}
            y2={y(score)}
            stroke="var(--yak-components-control-border)"
            strokeDasharray={score === 0 ? undefined : "3 4"}
          />
          <text
            x={PADDING.left - 6}
            y={y(score) + 3}
            textAnchor="end"
            fontSize="10"
            fill="var(--yak-components-muted-text)"
          >
            {score}
          </text>
        </g>
      ))}

      {days.length > 1 &&
        days.map((_, index) =>
          index % 7 === 0 ? (
            <text
              key={index}
              x={x(index)}
              y={HEIGHT - 8}
              textAnchor="middle"
              fontSize="10"
              fill="var(--yak-components-muted-text)"
            >
              {days[index].slice(5)}
            </text>
          ) : null,
        )}

      {SERIES.map((series) => {
        const segments: string[] = [];
        let current: string[] = [];
        days.forEach((day, index) => {
          const score = byKey.get(`${series.key}@${day}`);
          if (score == null) {
            if (current.length > 1) segments.push(current.join(" "));
            current = [];
            return;
          }
          current.push(`${x(index).toFixed(1)},${y(score).toFixed(1)}`);
        });
        if (current.length > 1) segments.push(current.join(" "));
        return segments.map((segment, segmentIndex) => (
          <polyline
            key={`${series.key}-${segmentIndex}`}
            points={segment}
            fill="none"
            stroke={series.color}
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        ));
      })}
    </svg>
  );
}

function Legend() {
  return (
    <div className="flex items-center gap-4">
      {SERIES.map((series) => (
        <span
          key={series.key}
          className="inline-flex items-center gap-1.5 text-xs text-[var(--yak-components-muted-text)]"
        >
          <span className="h-0.5 w-4 rounded-full" style={{ background: series.color }} />
          {series.label}
        </span>
      ))}
    </div>
  );
}

export { Legend as HealthTrendLegend };

/** Contains every date from the first to the last point, so the axis has no holes. */
function buildDayAxis(from: string, to: string): string[] {
  const days: string[] = [];
  const start = new Date(`${from}T00:00:00`);
  const end = new Date(`${to}T00:00:00`);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || start > end) return days;
  for (let time = start.getTime(); time <= end.getTime(); time += 86400000) {
    days.push(new Date(time).toISOString().slice(0, 10));
  }
  return days;
}
