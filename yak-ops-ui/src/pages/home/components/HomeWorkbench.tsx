import {
  homeQualityOverviewApi,
  type HomeQualityDimension,
  type HomeQualityIssue,
  type HomeQualityOverview,
} from '@/services/home';
import { BRAND_COLOR } from '@/styles/brand';
import { history, useIntl } from '@umijs/max';
import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import { useEffect, useMemo, useState } from 'react';

interface QualityOverviewState {
  data?: HomeQualityOverview;
  loading: boolean;
  failed: boolean;
}

interface RadarDimensionDefinition {
  labelZh: string;
  labelEn: string;
  aliases: string[];
}

const RADAR_DIMENSIONS: RadarDimensionDefinition[] = [
  {
    labelZh: '完整性',
    labelEn: 'Completeness',
    aliases: ['完整性', 'completeness'],
  },
  {
    labelZh: '唯一性',
    labelEn: 'Uniqueness',
    aliases: ['唯一性', 'uniqueness'],
  },
  {
    labelZh: '有效性',
    labelEn: 'Validity',
    aliases: ['有效性', 'validity'],
  },
  {
    labelZh: '准确性',
    labelEn: 'Accuracy',
    aliases: ['准确性', 'accuracy'],
  },
  {
    labelZh: '时效性',
    labelEn: 'Timeliness',
    aliases: ['时效性', '及时性', 'timeliness'],
  },
];

const formatMetric = (
  value: number | null | undefined,
  locale: string,
) => {
  if (value == null) return '--';

  return new Intl.NumberFormat(locale).format(value);
};

const formatRate = (value?: number | null) => {
  if (value == null) return '--';

  return value.toFixed(1);
};

const formatRateWithUnit = (value?: number | null) => {
  if (value == null) return '--';

  return `${value.toFixed(1)}%`;
};

function normalizeRadarDimensions(
  dimensions: HomeQualityDimension[],
  isChinese: boolean,
): HomeQualityDimension[] {
  return RADAR_DIMENSIONS.map((definition) => {
    const matched = dimensions.find((item) => {
      const value = item.dimension.toLowerCase();

      return definition.aliases.some(
        (alias) => alias.toLowerCase() === value,
      );
    });

    return {
      dimension: isChinese
        ? definition.labelZh
        : definition.labelEn,
      total: matched?.total ?? 0,
      issues: matched?.issues ?? 0,
      passRate: matched?.passRate ?? null,
    };
  });
}

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
          setState({
            loading: false,
            failed: true,
          });
          return;
        }

        setState({
          data: response.data,
          loading: false,
          failed: false,
        });
      })
      .catch(() => {
        if (!active) return;

        setState({
          loading: false,
          failed: true,
        });
      });

    return () => {
      active = false;
    };
  }, []);

  return state;
}

function relativeTime(
  value: string | null | undefined,
  isChinese: boolean,
) {
  if (!value) return '--';

  const timestamp = new Date(value).getTime();

  if (!Number.isFinite(timestamp)) {
    return value;
  }

  const diff = Math.max(0, Date.now() - timestamp);
  const minutes = Math.floor(diff / 60_000);

  if (minutes < 1) {
    return isChinese ? '刚刚' : 'Just now';
  }

  if (minutes < 60) {
    return isChinese
      ? `${minutes} 分钟前`
      : `${minutes}m ago`;
  }

  const hours = Math.floor(minutes / 60);

  if (hours < 24) {
    return isChinese
      ? `${hours} 小时前`
      : `${hours}h ago`;
  }

  const days = Math.floor(hours / 24);

  if (days < 7) {
    return isChinese
      ? `${days} 天前`
      : `${days}d ago`;
  }

  return new Date(timestamp).toLocaleDateString(
    isChinese ? 'zh-CN' : 'en-US',
    {
      month: '2-digit',
      day: '2-digit',
    },
  );
}

function getObjectLabel(issue: HomeQualityIssue) {
  return (
    issue.objectName ||
    issue.tableName ||
    issue.monitorName ||
    '--'
  );
}

function getHealthStatus(
  passRate: number | null | undefined,
  isChinese: boolean,
) {
  if (passRate == null) {
    return {
      label: isChinese
        ? '暂无质量数据'
        : 'No quality data',
      textClass: 'text-[#858b94]',
      dotClass: 'bg-[#c8ccd2]',
    };
  }

  if (passRate >= 95) {
    return {
      label: isChinese
        ? '整体质量健康'
        : 'Quality is healthy',
      textClass: 'text-[#34855d]',
      dotClass: 'bg-[#53a675]',
    };
  }

  if (passRate >= 80) {
    return {
      label: isChinese
        ? '部分指标需要关注'
        : 'Some metrics need attention',
      textClass: 'text-[#a76c24]',
      dotClass: 'bg-[#d99a45]',
    };
  }

  return {
    label: isChinese
      ? '存在较多质量问题'
      : 'Quality issues detected',
    textClass: 'text-[#c64e59]',
    dotClass: 'bg-[#df5a66]',
  };
}

function buildRadarOption(
  dimensions: HomeQualityDimension[],
): EChartsOption {
  const hasData = dimensions.some(
    (item) => item.passRate != null,
  );

  const dimensionMap = new Map(
    dimensions.map((item) => [
      item.dimension,
      item,
    ]),
  );

  return {
    animation: hasData,
    animationDuration: 700,
    animationEasing: 'cubicOut',

    tooltip: hasData
      ? {
          trigger: 'item',
          borderWidth: 0,
          backgroundColor: 'rgba(32, 35, 43, 0.92)',
          textStyle: {
            color: '#ffffff',
            fontSize: 12,
          },
          padding: [9, 12],
          formatter: () =>
            dimensions
              .map(
                (item) =>
                  `${item.dimension}&nbsp;&nbsp;${formatRateWithUnit(
                    item.passRate,
                  )}`,
              )
              .join('<br/>'),
        }
      : {
          show: false,
        },

    radar: {
      center: ['50%', '52%'],
      radius: '66%',
      splitNumber: 4,

      indicator: dimensions.map((item) => ({
        name: item.dimension,
        max: 100,
      })),

      axisName: {
        color: '#565c66',
        fontSize: 11,
        fontWeight: 500,
        lineHeight: 18,

        formatter: (name: string) => {
          const dimension =
            dimensionMap.get(name);

          return `{name|${name}}\n{value|${formatRateWithUnit(
            dimension?.passRate,
          )}}`;
        },

        rich: {
          name: {
            color: '#656b75',
            fontSize: 11,
            fontWeight: 500,
            lineHeight: 17,
          },

          value: {
            color: '#a0a5ad',
            fontSize: 9,
            fontWeight: 400,
            lineHeight: 14,
          },
        },
      },

      axisLine: {
        lineStyle: {
          color: '#e6e8ec',
          width: 1,
        },
      },

      splitLine: {
        lineStyle: {
          color: '#e5e8ec',
          width: 1,
        },
      },

      splitArea: {
        areaStyle: {
          color: [
            '#ffffff',
            '#fafbfc',
            '#ffffff',
            '#fafbfc',
          ],
        },
      },
    },

    series: hasData
      ? [
          {
            type: 'radar',

            symbol: 'circle',
            symbolSize: 4,

            lineStyle: {
              width: 2,
              color: BRAND_COLOR,
            },

            itemStyle: {
              color: BRAND_COLOR,
              borderColor: '#ffffff',
              borderWidth: 1.5,
            },

            areaStyle: {
              color: 'rgba(254, 44, 85, 0.09)',
            },

            data: [
              {
                value: dimensions.map(
                  (item) =>
                    item.passRate ?? 0,
                ),
                name: 'Quality',
              },
            ],
          },
        ]
      : [],
  };
}

function LoadingLines() {
  return (
    <div className="divide-y divide-[#eef0f2]">
      {[0, 1, 2, 3].map((item) => (
        <div
          key={item}
          className="flex h-[68px] animate-pulse items-center gap-3"
        >
          <div className="h-2 w-2 rounded-full bg-[#eceef1]" />

          <div className="min-w-0 flex-1">
            <div className="h-3 w-[42%] rounded bg-[#eceef1]" />
            <div className="mt-2 h-2.5 w-[62%] rounded bg-[#f1f2f4]" />
          </div>

          <div className="h-2.5 w-12 rounded bg-[#f1f2f4]" />
        </div>
      ))}
    </div>
  );
}

function EmptyIssues({
  failed,
  isChinese,
}: {
  failed: boolean;
  isChinese: boolean;
}) {
  return (
    <div className="flex min-h-[260px] flex-col items-center justify-center">
      <div className="text-[32px] font-light leading-none text-[#d6d9de]">
        —
      </div>

      <strong className="mt-3 text-[12px] font-medium text-[#646a74]">
        {failed
          ? isChinese
            ? '质量问题加载失败'
            : 'Failed to load issues'
          : isChinese
            ? '近 7 日暂无质量问题'
            : 'No quality issues in the last 7 days'}
      </strong>

      <span className="mt-1 text-[10px] text-[#a2a6ad]">
        {failed
          ? isChinese
            ? '请稍后刷新页面重试'
            : 'Please refresh and try again'
          : isChinese
            ? '当前数据质量状态良好'
            : 'Current data quality is healthy'}
      </span>
    </div>
  );
}

function QualityMetric({
  label,
  value,
  warning = false,
  locale,
}: {
  label: string;
  value?: number | null;
  warning?: boolean;
  locale: string;
}) {
  const hasWarning =
    warning && (value ?? 0) > 0;

  return (
    <div className="min-w-0">
      <div className="truncate text-[10px] leading-4 text-[#9398a1]">
        {label}
      </div>

      <strong
        className={`mt-0.5 block truncate text-[16px] font-semibold leading-6 tracking-[-0.3px] ${
          hasWarning
            ? 'text-[#d55360]'
            : 'text-[#343943]'
        }`}
      >
        {formatMetric(value, locale)}
      </strong>
    </div>
  );
}

function QualityRadar({
  state,
  isChinese,
  locale,
}: {
  state: QualityOverviewState;
  isChinese: boolean;
  locale: string;
}) {
  const data = state.data;

  const dimensions = useMemo(
    () =>
      normalizeRadarDimensions(
        data?.dimensions ?? [],
        isChinese,
      ),
    [data?.dimensions, isChinese],
  );

  const option = useMemo(
    () => buildRadarOption(dimensions),
    [dimensions],
  );

  const health = getHealthStatus(
    data?.passRate,
    isChinese,
  );

  return (
    <div className="flex min-w-0 flex-col lg:pr-7">
      <div className="flex items-start justify-between gap-5">
        <div>
          <div className="text-[11px] font-medium text-[#858b94]">
            {isChinese
              ? '综合通过率'
              : 'Overall pass rate'}
          </div>

          <div className="mt-1 flex items-baseline gap-1">
            <strong className="text-[34px] font-semibold leading-[40px] tracking-[-1px] text-[#262a32]">
              {state.loading
                ? '--'
                : formatRate(
                    data?.passRate,
                  )}
            </strong>

            {data?.passRate != null && (
              <span className="text-[13px] font-medium text-[#7d838c]">
                %
              </span>
            )}
          </div>

          <div
            className={`mt-1 flex items-center gap-1.5 text-[10px] font-medium ${health.textClass}`}
          >
            <span
              className={`h-1.5 w-1.5 rounded-full ${health.dotClass}`}
            />

            {state.loading
              ? isChinese
                ? '质量数据加载中'
                : 'Loading quality data'
              : state.failed
                ? isChinese
                  ? '质量数据加载失败'
                  : 'Failed to load quality data'
                : health.label}
          </div>
        </div>

        <span className="mt-1 shrink-0 rounded-full bg-[#f5f6f8] px-2.5 py-1 text-[9px] font-medium text-[#858b94]">
          {isChinese ? '近 7 日' : 'Last 7 days'}
        </span>
      </div>

      <div className="mt-1 min-h-[235px]">
        {state.failed ? (
          <div className="flex h-[235px] items-center justify-center text-[11px] text-[#9a9fa7]">
            {isChinese
              ? '暂无质量维度数据'
              : 'No dimension data'}
          </div>
        ) : (
          <ReactECharts
            option={option}
            notMerge
            lazyUpdate
            style={{
              width: '100%',
              height: 235,
            }}
          />
        )}
      </div>

      <div className="grid grid-cols-3 gap-5 border-t border-[#eceef1] pt-3.5">
        <QualityMetric
          label={
            isChinese
              ? '监控表'
              : 'Monitored tables'
          }
          value={data?.monitoredTableCount}
          locale={locale}
        />

        <QualityMetric
          label={
            isChinese
              ? '今日检测'
              : 'Checks today'
          }
          value={data?.todayExecutionCount}
          locale={locale}
        />

        <QualityMetric
          label={
            isChinese
              ? '启用规则'
              : 'Enabled rules'
          }
          value={data?.enabledRuleCount}
          locale={locale}
        />
      </div>
    </div>
  );
}

function IssueRow({
  issue,
  isChinese,
}: {
  issue: HomeQualityIssue;
  isChinese: boolean;
}) {
  const checkResult =
    issue.checkResult?.toUpperCase();

  const executionError =
    checkResult === 'ERROR';

  const statusLabel = executionError
    ? isChinese
      ? '执行异常'
      : 'Execution error'
    : isChinese
      ? '未通过'
      : 'Failed';

  return (
    <button
      type="button"
      onClick={() =>
        history.push(
          `/data-quality/execution/${encodeURIComponent(
            issue.executionNo,
          )}`,
        )
      }
      className="
        group
        flex
        w-full
        min-w-0
        items-center
        gap-3
        border-0
        bg-transparent
        py-[13px]
        text-left
      "
    >
      <span
        className={`h-8 w-[3px] shrink-0 rounded-full ${
          executionError
            ? 'bg-[#dfa04d]'
            : 'bg-[#df5d69]'
        }`}
      />

      <span className="min-w-0 flex-1">
        <span className="flex min-w-0 items-center gap-2">
          <strong className="min-w-0 truncate text-[12px] font-medium leading-5 text-[#373c45] transition-colors group-hover:text-[#20242b]">
            {issue.ruleName}
          </strong>

          <span className="shrink-0 rounded-full bg-[#f3f4f6] px-2 py-[2px] text-[9px] font-medium leading-4 text-[#737983]">
            {issue.dimension}
          </span>

          <span
            className={`hidden shrink-0 text-[9px] font-medium sm:inline ${
              executionError
                ? 'text-[#b97b2f]'
                : 'text-[#cf5863]'
            }`}
          >
            {statusLabel}
          </span>
        </span>

        <span className="mt-0.5 flex min-w-0 items-center gap-1.5 text-[10px] leading-4 text-[#9499a2]">
          <span className="truncate">
            {getObjectLabel(issue)}
          </span>

          {issue.columnName && (
            <>
              <span className="shrink-0 text-[#d1d4d9]">
                ·
              </span>

              <span className="truncate">
                {issue.columnName}
              </span>
            </>
          )}
        </span>
      </span>

      <span className="shrink-0 text-[9px] text-[#a0a5ad]">
        {relativeTime(
          issue.queuedAt,
          isChinese,
        )}
      </span>

      <span className="shrink-0 translate-x-[-2px] text-[17px] font-light leading-none text-[#c3c7cd] opacity-0 transition-all group-hover:translate-x-0 group-hover:opacity-100">
        ›
      </span>
    </button>
  );
}

function RecentIssues({
  state,
  isChinese,
  locale,
}: {
  state: QualityOverviewState;
  isChinese: boolean;
  locale: string;
}) {
  const data = state.data;

  const issues = (
    data?.recentIssues ?? []
  ).slice(0, 4);

  const issueCount =
    data?.recentIssueCount;

  return (
    <div className="flex min-w-0 flex-col lg:border-l lg:border-[#eceef1] lg:pl-7">
      <div className="flex shrink-0 items-start justify-between gap-5">
        <div>
          <div className="flex items-baseline gap-2">
            <h3 className="m-0 text-[15px] font-semibold tracking-[-0.2px] text-[#2b3038]">
              {isChinese
                ? '待处理问题'
                : 'Issues to review'}
            </h3>

            {(issueCount ?? 0) > 0 && (
              <strong className="text-[13px] font-semibold text-[#d6525f]">
                {formatMetric(
                  issueCount,
                  locale,
                )}
              </strong>
            )}
          </div>

        </div>

        <div className="flex shrink-0 gap-6">
          <div className="text-right">
            <div className="text-[9px] leading-4 text-[#a0a5ad]">
              {isChinese
                ? '今日问题表'
                : 'Issue tables'}
            </div>

            <strong
              className={`mt-0.5 block text-[15px] font-semibold leading-5 ${
                (data?.todayIssueTableCount ??
                  0) > 0
                  ? 'text-[#d6525f]'
                  : 'text-[#3f444d]'
              }`}
            >
              {formatMetric(
                data?.todayIssueTableCount,
                locale,
              )}
            </strong>
          </div>

          <div className="text-right">
            <div className="text-[9px] leading-4 text-[#a0a5ad]">
              {isChinese
                ? '近 7 日问题'
                : '7d issues'}
            </div>

            <strong
              className={`mt-0.5 block text-[15px] font-semibold leading-5 ${
                (issueCount ?? 0) > 0
                  ? 'text-[#d6525f]'
                  : 'text-[#3f444d]'
              }`}
            >
              {formatMetric(
                issueCount,
                locale,
              )}
            </strong>
          </div>
        </div>
      </div>

      <div className="mt-3 min-h-[268px] flex-1 border-t border-[#eceef1]">
        {state.loading ? (
          <LoadingLines />
        ) : issues.length > 0 ? (
          <div className="divide-y divide-[#eceef1]">
            {issues.map((issue) => (
              <IssueRow
                key={issue.id}
                issue={issue}
                isChinese={isChinese}
              />
            ))}
          </div>
        ) : (
          <EmptyIssues
            failed={state.failed}
            isChinese={isChinese}
          />
        )}
      </div>

      <div className="flex min-h-[30px] items-end justify-between border-t border-[#eceef1] pt-2.5">
        <span className="text-[9px] text-[#a5a9b0]">
          {(issueCount ?? 0) > issues.length
            ? isChinese
              ? `仅展示最近 ${issues.length} 条`
              : `Showing latest ${issues.length}`
            : ''}
        </span>

        <button
          type="button"
          onClick={() =>
            history.push(
              '/data-quality/overview',
            )
          }
          className="group flex items-center gap-1 border-0 bg-transparent p-0 text-[10px] font-medium text-[#747a84] transition-colors hover:text-[#2e333b]"
        >
          {isChinese
            ? '全部问题'
            : 'View all'}

          <span className="text-[13px] font-light transition-transform group-hover:translate-x-0.5">
            →
          </span>
        </button>
      </div>
    </div>
  );
}

function DataQualityPanel() {
  const intl = useIntl();

  const state =
    useQualityOverview();

  const isChinese =
    intl.locale
      .toLowerCase()
      .startsWith('zh');

  return (
    <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header className="flex items-start justify-between gap-6">
        <div>
          <h2 className="m-0 text-[18px] font-semibold tracking-[-0.35px] text-[#252932]">
            {isChinese
              ? '数据质量'
              : 'Data Quality'}
          </h2>

        </div>

        <button
          type="button"
          onClick={() =>
            history.push(
              '/data-quality/overview',
            )
          }
          className="group flex shrink-0 items-center gap-1 border-0 bg-transparent p-0 text-[10px] font-medium text-[#7a8089] transition-colors hover:text-[#30353d]"
        >
          {isChinese
            ? '查看质量中心'
            : 'Quality center'}

          <span className="text-[13px] font-light transition-transform group-hover:translate-x-0.5">
            →
          </span>
        </button>
      </header>

      <div className="mt-4 border-t border-[#eceef1] pt-4">
        <div className="grid min-w-0 grid-cols-1 gap-7 lg:grid-cols-[minmax(300px,0.82fr)_minmax(460px,1.18fr)] lg:gap-0">
          <QualityRadar
            state={state}
            isChinese={isChinese}
            locale={intl.locale}
          />

          <RecentIssues
            state={state}
            isChinese={isChinese}
            locale={intl.locale}
          />
        </div>
      </div>
    </section>
  );
}

/**
 * 首页主工作区。
 *
 * 当前首页不再展示“数据资产 / 数据交付”聚合入口，
 * 而是直接展示用户最需要关注的数据质量状态。
 *
 * 左侧回答：
 * 数据整体是否可信？
 *
 * 右侧回答：
 * 具体哪里出了问题？
 */
export function HomeWorkbenchMain() {
  return (
    <div className="min-w-0">
      <DataQualityPanel />
    </div>
  );
}

export default function HomeWorkbench() {
  return <HomeWorkbenchMain />;
}