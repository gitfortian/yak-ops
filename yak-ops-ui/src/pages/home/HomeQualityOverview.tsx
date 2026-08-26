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

const COUNT_FORMATTER = new Intl.NumberFormat('zh-CN');

const formatMetric = (value?: number | null) =>
  value == null ? '--' : COUNT_FORMATTER.format(value);

const formatRate = (value?: number | null) =>
  value == null ? '--' : value.toFixed(1);

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
          近 7 日规则执行健康度与最近质量问题
        </p>
      </div>

      <button
        type="button"
        onClick={() => history.push('/data-quality/table-config')}
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
    <div>
      <div className="text-[10px] leading-4 text-[#999da5]">{label}</div>
      <strong
        className={`mt-0.5 block text-[18px] font-semibold leading-6 ${
          warning && (value ?? 0) > 0 ? 'text-[#dc5964]' : 'text-[#40444d]'
        }`}
      >
        {formatMetric(value)}
      </strong>
    </div>
  );
}

function buildRadarOption(dimensions: HomeQualityDimension[]): EChartsOption {
  return {
    animation: true,
    animationDuration: 650,
    tooltip: {
      trigger: 'item',
      formatter: () =>
        dimensions
          .map((item) => `${item.dimension}：${formatRate(item.passRate)}%`)
          .join('<br/>'),
    },
    radar: {
      center: ['50%', '51%'],
      radius: '66%',
      splitNumber: 4,
      indicator: dimensions.map((item) => ({ name: item.dimension, max: 100 })),
      axisName: {
        color: '#7f848d',
        fontSize: 10,
      },
      axisLine: {
        lineStyle: {
          color: '#e3e6eb',
        },
      },
      splitLine: {
        lineStyle: {
          color: '#e9ebef',
        },
      },
      splitArea: {
        areaStyle: {
          color: ['#ffffff', '#fafbfc'],
        },
      },
    },
    series: [
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
              color: '#6685ed',
            },
            itemStyle: {
              color: '#6685ed',
            },
            areaStyle: {
              color: 'rgba(102,133,237,0.13)',
            },
          },
        ],
      },
    ],
  };
}

function DimensionBars({ dimensions }: { dimensions: HomeQualityDimension[] }) {
  return (
    <div className="flex h-[250px] flex-col justify-center gap-5 px-3">
      {dimensions.map((item) => (
        <div key={item.dimension}>
          <div className="flex items-center justify-between text-[10px]">
            <span className="text-[#777c85]">{item.dimension}</span>
            <strong className="font-semibold text-[#4c515a]">
              {formatRate(item.passRate)}%
            </strong>
          </div>
          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-[#eef0f4]">
            <div
              className="h-full rounded-full bg-[#7f94e8] transition-[width] duration-500"
              style={{ width: `${Math.max(0, Math.min(100, item.passRate ?? 0))}%` }}
            />
          </div>
          <div className="mt-1 text-[9px] text-[#a2a6ae]">
            {formatMetric(item.total)} 次规则检查 · {formatMetric(item.issues)} 项问题
          </div>
        </div>
      ))}
    </div>
  );
}

function DimensionHealth({ state }: { state: QualityOverviewState }) {
  const dimensions = (state.data?.dimensions || []).filter(
    (item) => item.passRate != null,
  );
  const option = useMemo(() => buildRadarOption(dimensions), [dimensions]);

  if (dimensions.length >= 3) {
    return (
      <div className="min-h-[250px]">
        <ReactECharts
          option={option}
          style={{ width: '100%', height: '260px' }}
        />
      </div>
    );
  }

  if (dimensions.length > 0) {
    return <DimensionBars dimensions={dimensions} />;
  }

  return (
    <div className="flex h-[250px] items-center justify-center text-[11px] text-[#a0a4ac]">
      {state.loading
        ? '质量维度加载中...'
        : state.failed
          ? '质量维度加载失败'
          : state.data?.passRate == null
            ? '近 7 日暂无规则执行数据'
            : '暂无质量维度数据'}
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
        <strong className="block truncate text-[12px] font-medium text-[#41454e]">
          {issue.ruleName}
        </strong>
        <span className="mt-1 block truncate text-[10px] text-[#9ca0a8]">
          {objectLabel(issue)} · {issue.dimension}
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
    <div className="min-w-0 border-t border-[#eef0f3] pt-4 lg:border-l lg:border-t-0 lg:pl-5 lg:pt-0">
      <div className="flex items-center justify-between gap-3">
        <strong className="text-[13px] font-semibold text-[#40444d]">
          最近问题
        </strong>
        <span className="flex shrink-0 items-center gap-1 text-[10px] text-[#a0a4ac]">
          <AlertTriangle
            size={11}
            strokeWidth={1.8}
            className="text-[#e46a73]"
          />
          {formatMetric(state.data?.recentIssueCount)} 项近 7 日问题
        </span>
      </div>

      {issues.length > 0 ? (
        <div className="mt-3 divide-y divide-[#f0f1f3]">
          {issues.map((issue) => (
            <RecentIssueRow key={issue.id} issue={issue} />
          ))}
        </div>
      ) : (
        <div className="flex min-h-[190px] items-center justify-center text-[10px] text-[#a0a4ac]">
          {state.loading
            ? '质量问题加载中...'
            : state.failed
              ? '质量数据加载失败'
              : state.data?.recentIssueCount == null
                ? '质量数据暂不可用'
                : '近 7 日暂无质量问题'}
        </div>
      )}
    </div>
  );
}

export default function QualityOverview() {
  const state = useQualityOverview();
  const data = state.data;
  const health = healthState(data?.passRate);

  return (
    <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <SectionHeader />

      <div className="mt-4 grid min-h-[300px] grid-cols-1 gap-5 lg:grid-cols-[180px_250px_minmax(0,1fr)]">
        <div className="flex flex-col justify-center">
          <div className="text-[11px] font-medium text-[#8f949d]">
            近 7 日规则通过率
          </div>

          <div className="mt-1 flex items-end gap-1">
            <strong className="text-[42px] font-semibold leading-[48px] tracking-[-1.5px] text-[#2f333c]">
              {formatRate(data?.passRate)}
            </strong>
            {data?.passRate != null ? (
              <span className="mb-1.5 text-[12px] text-[#9ca0a8]">%</span>
            ) : null}
          </div>

          <div
            className={`mt-1 flex items-center gap-1.5 text-[11px] font-medium ${health.className}`}
          >
            {health.icon}
            {state.loading
              ? '质量数据加载中...'
              : state.failed
                ? '质量数据加载失败'
                : health.label}
          </div>

          <div className="mt-6 grid grid-cols-2 gap-x-5 gap-y-4">
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

        <DimensionHealth state={state} />
        <RecentIssues state={state} />
      </div>
    </section>
  );
}
