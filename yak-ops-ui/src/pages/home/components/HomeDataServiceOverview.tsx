import YakOpsEmpty from '@/components/YakOpsEmpty';
import { history } from '@umijs/max';
import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import { useEffect, useMemo, useState } from 'react';

import {
  fetchDataServiceOverview,
  type DataServiceOverview,
} from '../../data-service/overview/overview-service';
import { SectionHeader } from './homeAssetOverviewShared';

interface DataServiceOverviewState {
  data?: DataServiceOverview;
  loading: boolean;
  failed: boolean;
}

const COUNT_FORMATTER = new Intl.NumberFormat('zh-CN');

const formatMetric = (value?: number | null) =>
  value == null ? '--' : COUNT_FORMATTER.format(value);

const formatRate = (data?: DataServiceOverview) =>
  !data || data.totalCalls <= 0 ? '--' : `${data.successRate.toFixed(1)}%`;

function useDataServiceOverview(): DataServiceOverviewState {
  const [state, setState] = useState<DataServiceOverviewState>({
    loading: true,
    failed: false,
  });

  useEffect(() => {
    let active = true;
    fetchDataServiceOverview('7d')
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

function buildTrendOption(data?: DataServiceOverview): EChartsOption {
  const trend = data?.trend || [];
  return {
    animation: true,
    animationDuration: 520,
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#fff',
      borderColor: '#e8ebef',
      textStyle: { color: '#4b5059', fontSize: 10 },
    },
    grid: { top: 10, left: 8, right: 8, bottom: 22, containLabel: false },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.map((item) => item.time),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        interval: 3,
        color: '#a0a4ac',
        fontSize: 9,
        formatter: (value: string) => value.slice(0, 5),
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { show: false },
      splitLine: { lineStyle: { color: '#f1f3f6' } },
    },
    series: [
      {
        name: '调用量',
        type: 'line',
        smooth: 0.38,
        symbol: 'none',
        data: trend.map((item) => item.calls),
        lineStyle: { width: 2, color: '#6490ee' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(100,144,238,0.18)' },
              { offset: 1, color: 'rgba(100,144,238,0.01)' },
            ],
          },
        },
      },
    ],
  };
}

function OverviewMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 px-4 first:pl-0 last:pr-0">
      <div className="truncate text-[11px] text-[#92969f]">{label}</div>
      <strong className="mt-1 block truncate text-[24px] font-semibold tracking-[-0.6px] text-[#30343d]">
        {value}
      </strong>
    </div>
  );
}

function TrendEmpty({ state }: { state: DataServiceOverviewState }) {
  if (state.loading || state.failed) {
    return (
      <div className="flex h-[126px] items-center justify-center text-[11px] text-[#a0a4ac]">
        {state.loading ? '数据加载中...' : '数据服务概览加载失败'}
      </div>
    );
  }

  return (
    <div className="flex h-[126px] items-center justify-center">
      <YakOpsEmpty
        width={116}
        height={78}
        title="近 7 日暂无 API 调用"
        showCaption
      />
    </div>
  );
}

export default function HomeDataServiceOverview() {
  const state = useDataServiceOverview();
  const data = state.data;
  const trendOption = useMemo(() => buildTrendOption(data), [data]);
  const hasCalls = (data?.totalCalls || 0) > 0;
  const topApi = data?.hotApis?.[0];

  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <SectionHeader
        title="数据服务"
        description="近 7 日真实 API 调用与运行状态"
        onMore={() => history.push('/data-service/overview')}
      />

      <div className="mt-5">
        <div className="grid grid-cols-2 divide-x divide-[#eef0f3] lg:grid-cols-4">
          <OverviewMetric label="API 总数" value={formatMetric(data?.apiTotal)} />
          <OverviewMetric label="运行中" value={formatMetric(data?.runningApis)} />
          <OverviewMetric label="近 7 日调用" value={formatMetric(data?.totalCalls)} />
          <OverviewMetric label="成功率" value={formatRate(data)} />
        </div>

        <div className="mt-5 flex items-center justify-between gap-3 text-[10px] text-[#9da1a9]">
          <span>调用趋势</span>
          {data ? (
            <span className="truncate text-right">
              平均耗时 {formatMetric(data.averageDurationMs)} ms · 失败 {formatMetric(data.failureCalls)} 次
            </span>
          ) : (
            <span>{state.failed ? '暂不可用' : '--'}</span>
          )}
        </div>

        <div className="mt-1 h-[126px]">
          {hasCalls ? (
            <ReactECharts
              option={trendOption}
              style={{ width: '100%', height: '126px' }}
              notMerge
              lazyUpdate
            />
          ) : (
            <TrendEmpty state={state} />
          )}
        </div>

        <div className="mt-2 flex min-h-[30px] items-center justify-between gap-3 border-t border-[#f0f1f3] pt-3 text-[10px]">
          <span className="shrink-0 text-[#9da1a9]">调用最多</span>
          {topApi ? (
            <button
              type="button"
              onClick={() => history.push('/data-service/overview')}
              className="min-w-0 border-0 bg-transparent p-0 text-right text-[#646a74] transition-colors hover:text-[#343842]"
            >
              <span className="block truncate">
                {topApi.name || topApi.path || `API #${topApi.apiId}`}
                <strong className="ml-1 font-semibold text-[#454a54]">
                  {formatMetric(topApi.calls)} 次
                </strong>
              </span>
            </button>
          ) : (
            <span className="truncate text-right text-[#a0a4ac]">
              {state.loading ? '加载中...' : state.failed ? '暂不可用' : '暂无调用'}
            </span>
          )}
        </div>
      </div>
    </section>
  );
}
