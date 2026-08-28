import YakOpsEmpty from '@/components/YakOpsEmpty';
import {
  homeQualityOverviewApi,
  type HomeQualityDimension,
  type HomeQualityIssue,
  type HomeQualityOverview,
} from '@/services/home';
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
      className: 'text-[#7f858e]',
      icon: null,
    };
  }

  if (passRate >= 95) {
    return {
      label: '整体质量健康',
      className: 'text-[#31865a]',
      icon: <CheckCircle2 size={14} strokeWidth={2} />,
    };
  }

  if (passRate >= 80) {
    return {
      label: '质量表现需关注',
      className: 'text-[#b87520]',
      icon: <AlertTriangle size={14} strokeWidth={1.9} />,
    };
  }

  return {
    label: '质量问题较多',
    className: 'text-[#d94d59]',
    icon: <AlertTriangle size={14} strokeWidth={1.9} />,
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
    <header className="flex items-center justify-between gap-4">
      <h2 className="m-0 text-xl font-semibold tracking-[-0.35px] text-[#20232b]">
        数据质量
      </h2>

      <button
        type="button"
        onClick={() => history.push('/data-quality/overview')}
        className="flex shrink-0 items-center gap-0.5 border-0 bg-transparent p-0 text-[13px] font-medium text-[#656b75] transition-colors hover:text-[#20232b]"
      >
        查看更多
        <ChevronRight size={15} strokeWidth={1.9} />
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
      <div className="truncate text-[11px] font-medium leading-4 text-[#747b85]">
        {label}
      </div>
      <strong
        className={`mt-1 block text-[18px] font-semibold leading-6 ${
          warning && (value ?? 0) > 0 ? 'text-[#d94d59]' : 'text-[#343943]'
        }`}
      >
        {formatMetric(value)}
      </strong>
    </div>
  );
}

function buildRadarOption(dimensions: HomeQualityDimension[]): EChartsOption {
  const hasCompleteRadar = dimensions.every((item) => item.passRate != null);
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
      center: ['50%', '52%'],
      radius: '72%',
      splitNumber: 4,
      indicator: dimensions.map((item) => ({
        name: item.dimension,
        max: 100,
      })),
      axisName: {
        color: '#414751',
        fontSize: 12,
        fontWeight: 500,
        lineHeight: 18,
        formatter: (name: string) => {
          const dimension = dimensionMap.get(name);
          return `${name}\n${formatRateWithUnit(dimension?.passRate)}`;
        },
      },
      axisLine: {
        lineStyle: {
          color: '#d9dde4',
        },
      },
      splitLine: {
        lineStyle: {
          color: '#dfe3e9',
        },
      },
      splitArea: {
        areaStyle: {
          color: ['#ffffff', '#f8f9fb'],
        },
      },
    },
    series: hasCompleteRadar
      ? [
          {
            type: 'radar',
            symbol: 'circle',
            symbolSize: 5,
            data: [
              {
                value: dimensions.map((item) => item.passRate ?? 0),
                name: '规则通过率',
                lineStyle: {
                  width: 2.2,
                  color: BRAND_COLOR,
                },
                itemStyle: {
                  color: BRAND_COLOR,
                },
                areaStyle: {
                  color: 'rgba(254,44,85,0.1)',
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
  const showEmpty = !state.loading && !state.failed && data?.passRate == null;

  return (
    <div className="min-w-0 rounded-[16px] border border-[#eef0f3] bg-[#fafbfc] px-4 pb-4 pt-4">
      <div className="flex items-start justify-between gap-4">
        <strong className="text-[15px] font-semibold leading-6 text-[#252a33]">
          质量维度
        </strong>

        <div className="shrink-0 text-right">
          <div className="flex items-end justify-end gap-1">
            <strong className="text-[28px] font-semibold leading-8 tracking-[-0.7px] text-[#252a33]">
              {formatRate(data?.passRate)}
            </strong>
            {data?.passRate != null ? (
              <span className="mb-0.5 text-[11px] font-medium text-[#747b85]">
                %
              </span>
            ) : null}
          </div>

          <div
            className={`mt-1 flex items-center justify-end gap-1 text-[11px] font-medium ${health.className}`}
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

      <div className="min-h-[270px]">
        {showEmpty ? (
          <div className="flex h-[278px] items-center justify-center">
            <YakOpsEmpty
              width={160}
              height={108}
              title="暂无质量执行数据"
              showCaption
            />
          </div>
        ) : (
          <ReactECharts
            option={option}
            notMerge
            style={{ width: '100%', height: '278px' }}
          />
        )}
      </div>

      <div className="grid grid-cols-4 gap-3 border-t border-[#e6e9ee] pt-3.5">
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
      className="group flex w-full items-center gap-3 border-0 bg-transparent py-3.5 text-left"
    >
      <span
        className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-[9px] ${
          isError
            ? 'bg-[#fff4e8] text-[#d98932]'
            : 'bg-[#fff2f3] text-[#e35d69]'
        }`}
      >
        <AlertTriangle size={15} strokeWidth={1.9} />
      </span>

      <span className="min-w-0 flex-1">
        <span className="flex min-w-0 items-center gap-2">
          <strong className="truncate text-[13px] font-medium text-[#343943]">
            {issue.ruleName}
          </strong>
          <span className="shrink-0 rounded-full bg-[#eceff3] px-2 py-0.5 text-[10px] font-medium text-[#666d78]">
            {issue.dimension}
          </span>
        </span>

        <span className="mt-1 block truncate text-[11px] text-[#7f858f]">
          {objectLabel(issue)}
          {issue.columnName ? ` · ${issue.columnName}` : ''}
        </span>
      </span>

      <span className="shrink-0 text-[11px] text-[#858b94]">
        {relativeTime(issue.queuedAt)}
      </span>

      <ChevronRight
        size={14}
        strokeWidth={1.9}
        className="shrink-0 text-[#a7acb4] transition-transform group-hover:translate-x-0.5"
      />
    </button>
  );
}

function RecentIssues({ state }: { state: QualityOverviewState }) {
  const issues = state.data?.recentIssues ?? [];

  return (
    <div className="min-w-0 rounded-[16px] border border-[#eef0f3] bg-[#fafbfc] px-4 pb-4 pt-4">
      <div className="flex items-center justify-between gap-3">
        <strong className="text-[15px] font-semibold leading-6 text-[#252a33]">
          最近问题
        </strong>

        <span className="flex shrink-0 items-center gap-1 text-[11px] font-medium text-[#747b85]">
          <AlertTriangle
            size={12}
            strokeWidth={1.9}
            className="text-[#e35d69]"
          />
          {formatMetric(state.data?.recentIssueCount)} 项
        </span>
      </div>

      {issues.length > 0 ? (
        <div className="mt-2 divide-y divide-[#e7eaee]">
          {issues.map((issue) => (
            <RecentIssueRow key={issue.id} issue={issue} />
          ))}
        </div>
      ) : state.loading || state.failed || state.data?.recentIssueCount == null ? (
        <div className="flex min-h-[318px] items-center justify-center text-[11px] text-[#858b94]">
          {state.loading
            ? '质量问题加载中...'
            : state.failed
              ? '质量数据加载失败'
              : '质量数据暂不可用'}
        </div>
      ) : (
        <div className="flex min-h-[318px] items-center justify-center">
          <YakOpsEmpty
            width={170}
            height={114}
            title="近 7 日暂无质量问题"
            showCaption
          />
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

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-[minmax(380px,0.96fr)_minmax(0,1.04fr)]">
        <QualityRadarPanel state={state} />
        <RecentIssues state={state} />
      </div>
    </section>
  );
}
