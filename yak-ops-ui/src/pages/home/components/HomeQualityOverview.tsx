import { BRAND_COLOR } from '@/styles/brand';
import { history } from '@umijs/max';
import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import {
  AlertTriangle,
  CheckCircle2,
  ChevronRight,
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import {
  homeQualityOverviewApi,
  type HomeQualityDimension,
  type HomeQualityIssue,
  type HomeQualityOverview,
} from './service';

interface QualityOverviewState {
  data?: HomeQualityOverview;
  loading: boolean;
  failed: boolean;
}

interface RadarDimensionDefinition {
  label: string;
  aliases: string[];
}

const COUNT_FORMATTER = new Intl.NumberFormat('zh-CN');
const RADAR_DIMENSIONS: RadarDimensionDefinition[] = [
  { label: '完整性', aliases: ['完整性'] },
  { label: '唯一性', aliases: ['唯一性'] },
  { label: '有效性', aliases: ['有效性'] },
  { label: '准确性', aliases: ['准确性'] },
  { label: '时效性', aliases: ['时效性', '及时性'] },
];

const formatMetric = (value?: number | null) =>
  value == null ? '--' : COUNT_FORMATTER.format(value);

const formatRate = (value?: number | null) =>
  value == null ? '--' : value.toFixed(1);

const formatRateWithUnit = (value?: number | null) =>
  value == null ? '--' : `${formatRate(value)}%`;

const relativeTime = (value?: string | null) => {
  if (!value) return '--';
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return value;
  const minutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} 天前`;
  return new Date(timestamp).toLocaleDateString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  });
};

const objectLabel = (issue: HomeQualityIssue) =>
  issue.objectName || issue.tableName || issue.monitorName;

const healthState = (passRate?: number | null) => {
  if (passRate == null) {
    return {
      label: '暂无质量执行数据',
      className: 'text-[#92969f]',
      icon: null,
    };
  }
  if (passRate >= 95) {
    return {
      label: '整体质量健康',
      className: 'text-[#3c9766]',
      icon: <CheckCircle2 size={13} strokeWidth={2} />,
    };
  }
  if (passRate >= 80) {
    return {
      label: '质量表现需关注',
      className: 'text-[#c4842c]',
      icon: <AlertTriangle size={13} strokeWidth={1.9} />,
    };
  }
  return {
    label: '质量问题较多',
    className: 'text-[#dc5964]',
    icon: <AlertTriangle size={13} strokeWidth={1.9} />,
  };
};

const normalizeRadarDimensions = (
  dimensions: HomeQualityDimension[],
): HomeQualityDimension[] =>
  RADAR_DIMENSIONS.map((definition) => {
    const matched = dimensions.find((item) =>
      definition.aliases.includes(item.dimension),
    );
    return {
      dimension: definition.label,
      total: matched?.total ?? 0,
      issues: matched?.issues ?? 0,
      passRate: matched?.passRate ?? null,
    };
  });

function useQualityOverview(): QualityOverviewState {
  const [state, setState] = useState<QualityOverviewState>({
    loading: true,
    failed: false,
  });

  useEffect(() => {
    let active = true;
    homeQualityOverviewApi
      .overview()
      .then((response) => {
        if (!active) return;
        if (!response.data) {
          setState({ loading: false, failed: true });
          return;
        }
        setState({ data: response.data, loading: false, failed: false });
      })
      .catch(() => {
        if (active) setState({ loading: false, failed: true });
      });

    return () => {
      active = false;
    };
  }, []);

  return state;
}

function SectionHeader() {
  return (
    <header className="flex items-start justify-between gap-4">
      <div className="min-w-0">
        <h2 className="text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          数据质量
        </h2>
        <p className="mt-1 text-[12px] leading-5 text-[#92969f]">
          近 7 日质量维度健康度与最近质量问题
        </p>
      </div>

      <button
        type="button"
        onClick={() => history.push('/data-quality/overview')}
        className="mt-0.5 flex shrink-0 items-center gap-0.5 border-0 bg-transparent p-0 text-[12px] text-[#747982] transition-colors hover:text-[#252832]"
      >
        查看更多
        <ChevronRight size={14} strokeWidth={1.8} />
      </button>
    </header>
  );
}

function QualityMetric({
  label,
  value,
  warning = false,
}: {
  label: string;
  value?: number | null;
  warning?: boolean;
}) {
  return (
    <div className="min-w-0">
      <div className="truncate text-[10px] leading-4 text-[#999da5]">{label}</div>
      <strong
        className={`mt-0.5 block text-[16px] font-semibold leading-6 ${
          warning && (value ?? 0) > 0 ? 'text-[#dc5964]' : 'text-[#40444d]'
        }`}
      >
        {formatMetric(value)}
      </strong>
    </div>
  );
}

function buildRadarOption(dimensions: HomeQualityDimension[]): EChartsOption {
  const hasCompleteRadar = dimensions.every((item) => item.passRate != null);
  const coveredDimensionCount = dimensions.filter(
    (item) => item.passRate != null,
  ).length;
  const dimensionMap = new Map(
    dimensions.map((item) => [item.dimension, item]),
  );

  return {
    animation: hasCompleteRadar,
    animationDuration: 650,
    tooltip: hasCompleteRadar
      ? {
          trigger: 'item',
          formatter: () =>
            dimensions
              .map(
                (item) =>
                  `${item.dimension}：${formatRateWithUnit(item.passRate)}`,
              )
              .join('<br/>'),
        }
      : { show: false },
    radar: {
      center: ['50%', '51%'],
      radius: '64%',
      splitNumber: 4,
      indicator: dimensions.map((item) => ({ name: item.dimension, max: 100 })),
      axisName: {
        color: '#747982',
        fontSize: 10,
        lineHeight: 15,
        formatter: (name: string) => {
          const dimension = dimensionMap.get(name);
          return `${name}\n${formatRateWithUnit(dimension?.passRate)}`;
        },
      },
      axisLine: {
        lineStyle: {
          color: '#e2e5ea',
        },
      },
      splitLine: {
        lineStyle: {
          color: '#e8ebef',
        },
      },
      splitArea: {
        areaStyle: {
          color: ['#ffffff', '#fafbfc'],
        },
      },
    },
    graphic: hasCompleteRadar
      ? undefined
      : [
          {
            type: 'text',
            left: 'center',
            top: '48%',
            style: {
              text:
                coveredDimensionCount > 0
                  ? `已覆盖 ${coveredDimensionCount}/5 维`
                  : '暂无维度数据',
              fill: '#a0a4ac',
              fontSize: 10,
              textAlign: 'center',
            },
          },
        ],
    series: hasCompleteRadar
      ? [
          {
            type: 'radar',
            symbol: 'circle',
            symbolSize: 4,
            data: [
              {
                value: dimensions.map((item) => item.passRate ?? 0),
                name: '规则通过率',
                lineStyle: {
                  width: 2,
                  color: BRAND_COLOR,
                },
                itemStyle: {
                  color: BRAND_COLOR,
                },
                areaStyle: {
                  color: 'rgba(254,44,85,0.08)',
                },
              },
            ],
          },
        ]
      : [],
  };
}

function QualityRadarPanel({ state }: { state: QualityOverviewState }) {
  const data = state.data;
  const health = healthState(data?.passRate);
  const dimensions = useMemo(
    () => normalizeRadarDimensions(data?.dimensions ?? []),
    [data?.dimensions],
  );
  const option = useMemo(() => buildRadarOption(dimensions), [dimensions]);

  return (
    <div className="min-w-0 rounded-[16px] bg-[#fafbfc] px-4 pb-3 pt-3.5">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <strong className="block text-[13px] font-semibold text-[#40444d]">
            质量维度
          </strong>
          <span className="mt-1 block text-[10px] text-[#9ca0a8]">
            五维规则通过率
          </span>
        </div>

        <div className="shrink-0 text-right">
          <div className="flex items-end justify-end gap-1">
            <strong className="text-[24px] font-semibold leading-7 tracking-[-0.6px] text-[#30343b]">
              {formatRate(data?.passRate)}
            </strong>
            {data?.passRate != null ? (
              <span className="mb-0.5 text-[10px] text-[#9ca0a8]">%</span>
            ) : null}
          </div>
          <div
            className={`mt-1 flex items-center justify-end gap-1 text-[10px] font-medium ${health.className}`}
          >
            {health.icon}
            {state.loading
              ? '加载中...'
              : state.failed
                ? '加载失败'
                : health.label}
          </div>
        </div>
      </div>

      <div className="min-h-[220px]">
        <ReactECharts
          option={option}
          notMerge
          style={{ width: '100%', height: '230px' }}
        />
      </div>

      <div className="grid grid-cols-4 gap-3 border-t border-[#eceef2] pt-3">
        <QualityMetric label="监控表" value={data?.monitoredTableCount} />
        <QualityMetric label="今日检测" value={data?.todayExecutionCount} />
        <QualityMetric
          label="问题表"
          value={data?.todayIssueTableCount}
          warning
        />
        <QualityMetric label="启用规则" value={data?.enabledRuleCount} />
      </div>
    </div>
  );
}

function RecentIssueRow({ issue }: { issue: HomeQualityIssue }) {
  const isError = issue.checkResult?.toUpperCase() === 'ERROR';
  return (
    <button
      type="button"
      onClick={() =>
        history.push(
          `/data-quality/execution/${encodeURIComponent(issue.executionNo)}`,
        )
      }
      className="group flex w-full items-center gap-3 border-0 bg-transparent py-3 text-left"
    >
      <span
        className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] ${
          isError
            ? 'bg-[#fff4e8] text-[#d98932]'
            : 'bg-[#fff2f3] text-[#e35d69]'
        }`}
      >
        <AlertTriangle size={14} strokeWidth={1.8} />
      </span>

      <span className="min-w-0 flex-1">
        <span className="flex min-w-0 items-center gap-2">
          <strong className="truncate text-[12px] font-medium text-[#41454e]">
            {issue.ruleName}
          </strong>
          <span className="shrink-0 rounded-full bg-[#f0f2f5] px-2 py-0.5 text-[9px] text-[#7e838c]">
            {issue.dimension}
          </span>
        </span>
        <span className="mt-1 block truncate text-[10px] text-[#9ca0a8]">
          {objectLabel(issue)}
          {issue.columnName ? ` · ${issue.columnName}` : ''}
        </span>
      </span>

      <span className="shrink-0 text-[10px] text-[#9ca0a8]">
        {relativeTime(issue.queuedAt)}
      </span>

      <ChevronRight
        size={13}
        strokeWidth={1.8}
        className="shrink-0 text-[#b8bbc1] transition-transform group-hover:translate-x-0.5"
      />
    </button>
  );
}

function RecentIssues({ state }: { state: QualityOverviewState }) {
  const issues = state.data?.recentIssues || [];
  return (
    <div className="min-w-0 rounded-[16px] bg-[#fafbfc] px-4 pb-3 pt-3.5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <strong className="block text-[13px] font-semibold text-[#40444d]">
            最近问题
          </strong>
          <span className="mt-1 block text-[10px] text-[#9ca0a8]">
            最近规则执行中发现的质量问题
          </span>
        </div>
        <span className="mt-0.5 flex shrink-0 items-center gap-1 text-[10px] text-[#a0a4ac]">
          <AlertTriangle
            size={11}
            strokeWidth={1.8}
            className="text-[#e46a73]"
          />
          {formatMetric(state.data?.recentIssueCount)} 项
        </span>
      </div>

      {issues.length > 0 ? (
        <div className="mt-2 divide-y divide-[#eceef2]">
          {issues.map((issue) => (
            <RecentIssueRow key={issue.id} issue={issue} />
          ))}
        </div>
      ) : (
        <div className="flex min-h-[250px] flex-col items-center justify-center text-center">
          {state.loading ? (
            <span className="text-[10px] text-[#a0a4ac]">质量问题加载中...</span>
          ) : state.failed ? (
            <span className="text-[10px] text-[#a0a4ac]">质量数据加载失败</span>
          ) : state.data?.recentIssueCount == null ? (
            <span className="text-[10px] text-[#a0a4ac]">质量数据暂不可用</span>
          ) : (
            <>
              <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[#edf8f1] text-[#4b9a6d]">
                <CheckCircle2 size={17} strokeWidth={1.9} />
              </span>
              <strong className="mt-3 text-[12px] font-medium text-[#5d626b]">
                近 7 日暂无质量问题
              </strong>
              <span className="mt-1 text-[10px] text-[#a0a4ac]">
                当前规则执行结果保持健康
              </span>
            </>
          )}
        </div>
      )}
    </div>
  );
}

export default function QualityOverview() {
  const state = useQualityOverview();

  return (
    <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <SectionHeader />

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-[minmax(320px,0.92fr)_minmax(0,1.08fr)]">
        <QualityRadarPanel state={state} />
        <RecentIssues state={state} />
      </div>
    </section>
  );
}
