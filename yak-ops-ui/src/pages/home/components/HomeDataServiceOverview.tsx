import {
  getDataServiceOverview,
  type DataServiceOverview,
} from '@/services/data-service';
import { history, useIntl } from '@umijs/max';
import { Activity, ChevronRight, Server } from 'lucide-react';
import { useEffect, useState } from 'react';

import { SectionHeader } from './homeAssetOverviewShared';

interface DataServiceOverviewState {
  data?: DataServiceOverview;
  loading: boolean;
  failed: boolean;
}

const formatMetric = (value: number | null | undefined, locale: string) =>
  value == null ? '--' : new Intl.NumberFormat(locale).format(value);

const formatRate = (data?: DataServiceOverview) =>
  !data || data.totalCalls <= 0 ? '--' : `${data.successRate.toFixed(1)}%`;

function useDataServiceOverview(): DataServiceOverviewState {
  const [state, setState] = useState<DataServiceOverviewState>({
    loading: true,
    failed: false,
  });

  useEffect(() => {
    let active = true;

    getDataServiceOverview('7d')
      .then((data) => {
        if (!active) return;
        if (!data) {
          setState({ loading: false, failed: true });
          return;
        }
        setState({ data, loading: false, failed: false });
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

export default function HomeDataServiceOverview() {
  const intl = useIntl();
  const state = useDataServiceOverview();
  const data = state.data;
  const topApi = data?.hotApis?.[0];

  return (
    <section className="flex h-[188px] min-w-0 flex-col rounded-[18px] border border-[#f0f1f3] bg-white px-5 pb-4 pt-4">
      <SectionHeader
        compact
        title={intl.formatMessage({ id: 'pages.home.dataService.title' })}
        onMore={() => history.push('/data-service/overview')}
      />

      <div className="mt-3 flex min-h-0 flex-1 flex-col justify-between">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[11px] bg-[#eef5ff] text-[#6490ee]">
            <Server size={18} strokeWidth={1.8} />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex items-baseline gap-2">
              <strong className="text-[25px] font-semibold leading-7 tracking-[-0.6px] text-[#30343d]">
                {formatMetric(data?.apiTotal, intl.locale)}
              </strong>
              <span className="text-[10px] text-[#969aa3]">
                {intl.formatMessage({ id: 'pages.home.dataService.metric.apiTotal' })}
              </span>
            </div>
            <div className="mt-1 grid grid-cols-3 gap-3 text-[9px] text-[#8f949d]">
              <span className="min-w-0 truncate">
                {intl.formatMessage({ id: 'pages.home.dataService.metric.running' })}{' '}
                <strong className="font-semibold text-[#555a64]">
                  {formatMetric(data?.runningApis, intl.locale)}
                </strong>
              </span>
              <span className="min-w-0 truncate">
                {intl.formatMessage({ id: 'pages.home.dataService.metric.successRate' })}{' '}
                <strong className="font-semibold text-[#555a64]">
                  {formatRate(data)}
                </strong>
              </span>
              <span className="min-w-0 truncate">
                {intl.formatMessage({ id: 'pages.home.dataService.metric.calls7d' })}{' '}
                <strong className="font-semibold text-[#555a64]">
                  {formatMetric(data?.totalCalls, intl.locale)}
                </strong>
              </span>
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={() => history.push('/data-service/overview')}
          className="group flex h-9 w-full items-center gap-2 rounded-[9px] border-0 bg-[#f8f9fb] px-3 text-left"
        >
          <Activity size={13} strokeWidth={1.8} className="shrink-0 text-[#7192d9]" />
          <span className="shrink-0 text-[10px] text-[#92969f]">
            {intl.formatMessage({ id: 'pages.home.dataService.mostCalled' })}
          </span>
          <span className="min-w-0 flex-1 truncate text-[10px] text-[#5f6570]">
            {topApi
              ? topApi.name || topApi.path || `API #${topApi.apiId}`
              : state.loading
                ? intl.formatMessage({ id: 'pages.home.dataService.loading' })
                : state.failed
                  ? intl.formatMessage({ id: 'pages.home.dataService.failed' })
                  : intl.formatMessage({ id: 'pages.home.dataService.noCalls' })}
          </span>
          {topApi ? (
            <strong className="shrink-0 text-[10px] font-semibold text-[#454a54]">
              {formatMetric(topApi.calls, intl.locale)}
            </strong>
          ) : null}
          <ChevronRight
            size={12}
            strokeWidth={1.8}
            className="shrink-0 text-[#b5b9c0] transition-transform group-hover:translate-x-0.5"
          />
        </button>
      </div>
    </section>
  );
}
