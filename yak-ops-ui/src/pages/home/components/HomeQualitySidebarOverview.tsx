import {
  homeQualityOverviewApi,
  type HomeQualityIssue,
  type HomeQualityOverview,
} from '@/services/home';
import { history, useIntl } from '@umijs/max';
import {
  AlertTriangle,
  CheckCircle2,
  ChevronRight,
  ShieldCheck,
} from 'lucide-react';
import { useEffect, useState } from 'react';

import { SectionHeader } from './homeAssetOverviewShared';

interface QualitySidebarState {
  data?: HomeQualityOverview;
  loading: boolean;
  failed: boolean;
}

const formatMetric = (value: number | null | undefined, locale: string) =>
  value == null ? '--' : new Intl.NumberFormat(locale).format(value);

const formatRate = (value?: number | null) =>
  value == null ? '--' : value.toFixed(1);

function useQualitySidebarOverview(): QualitySidebarState {
  const [state, setState] = useState<QualitySidebarState>({
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

const objectLabel = (issue: HomeQualityIssue) =>
  issue.objectName || issue.tableName || issue.monitorName;

export default function HomeQualitySidebarOverview() {
  const intl = useIntl();
  const state = useQualitySidebarOverview();
  const data = state.data;
  const latestIssue = data?.recentIssues?.[0];
  const risky = (data?.recentIssueCount ?? 0) > 0;

  return (
    <section className="flex h-[188px] min-w-0 flex-col rounded-[18px] border border-[#f0f1f3] bg-white px-5 pb-4 pt-4">
      <SectionHeader
        compact
        title={intl.formatMessage({ id: 'pages.home.quality.title' })}
        onMore={() => history.push('/data-quality/overview')}
      />

      <div className="mt-3 flex min-h-0 flex-1 flex-col justify-between">
        <div className="flex items-center justify-between gap-4">
          <div className="flex min-w-0 items-center gap-3">
            <span
              className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-[11px] ${
                risky
                  ? 'bg-[#fff2f3] text-[#df5c68]'
                  : 'bg-[#eef8f2] text-[#43815f]'
              }`}
            >
              {risky ? (
                <AlertTriangle size={18} strokeWidth={1.8} />
              ) : (
                <ShieldCheck size={18} strokeWidth={1.8} />
              )}
            </span>
            <div className="min-w-0">
              <div className="flex items-baseline gap-1">
                <strong className="text-[25px] font-semibold leading-7 tracking-[-0.6px] text-[#30343d]">
                  {formatRate(data?.passRate)}
                </strong>
                {data?.passRate != null ? (
                  <span className="text-[10px] text-[#8f949d]">%</span>
                ) : null}
              </div>
              <span className="mt-0.5 block text-[10px] text-[#969aa3]">
                {intl.formatMessage({ id: 'pages.home.quality.overallPassRate' })}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-x-5 text-right">
            <div>
              <span className="block text-[9px] text-[#a0a4ac]">
                {intl.formatMessage({ id: 'pages.home.quality.metric.todayChecks' })}
              </span>
              <strong className="mt-1 block text-[14px] font-semibold text-[#454a53]">
                {formatMetric(data?.todayExecutionCount, intl.locale)}
              </strong>
            </div>
            <div>
              <span className="block text-[9px] text-[#a0a4ac]">
                {intl.formatMessage({ id: 'pages.home.quality.recentIssues' })}
              </span>
              <strong
                className={`mt-1 block text-[14px] font-semibold ${
                  risky ? 'text-[#d94d59]' : 'text-[#454a53]'
                }`}
              >
                {formatMetric(data?.recentIssueCount, intl.locale)}
              </strong>
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={() =>
            latestIssue
              ? history.push(
                  `/data-quality/execution/${encodeURIComponent(latestIssue.executionNo)}`,
                )
              : history.push('/data-quality/overview')
          }
          className="group flex h-9 w-full items-center gap-2 rounded-[9px] border-0 bg-[#f8f9fb] px-3 text-left"
        >
          {latestIssue ? (
            <AlertTriangle size={13} strokeWidth={1.8} className="shrink-0 text-[#df5c68]" />
          ) : (
            <CheckCircle2 size={13} strokeWidth={1.8} className="shrink-0 text-[#4f936d]" />
          )}
          <span className="min-w-0 flex-1 truncate text-[10px] text-[#747a84]">
            {latestIssue
              ? `${latestIssue.ruleName} · ${objectLabel(latestIssue)}`
              : state.loading
                ? intl.formatMessage({ id: 'pages.home.common.loading' })
                : state.failed
                  ? intl.formatMessage({ id: 'pages.home.common.loadFailed' })
                  : intl.formatMessage({ id: 'pages.home.quality.emptyIssues' })}
          </span>
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
