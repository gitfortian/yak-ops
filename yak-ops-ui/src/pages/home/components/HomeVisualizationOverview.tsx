import { listDigitalScreens, type DigitalScreenInstance } from '@/services/digital-screen';
import {
  fetchDashboardOverview,
  type DashboardOverview,
  type DashboardSummary,
} from '@/services/dashboard';
import { history, useIntl } from '@umijs/max';
import { ChevronRight, Clock3, LayoutDashboard, Monitor } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import {
  formatMetric,
  relativeTime,
  SectionHeader,
} from './homeAssetOverviewShared';

interface VisualizationState {
  dashboard?: DashboardOverview;
  screens?: DigitalScreenInstance[];
  dashboardLoading: boolean;
  screenLoading: boolean;
  dashboardFailed: boolean;
  screenFailed: boolean;
}

interface RecentVisualization {
  id: string;
  name: string;
  updatedAt?: string;
  path: string;
}

const timestamp = (value?: string) => {
  if (!value) return 0;
  const parsed = new Date(value).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
};

const dashboardItem = (value: DashboardSummary): RecentVisualization => ({
  id: `dashboard-${value.id}`,
  name: value.name,
  updatedAt: value.updateTime || value.publishedTime || value.createTime,
  path: value.publishedVersionId
    ? `/dashboard/${value.id}`
    : `/dashboard/${value.id}/edit`,
});

const screenItem = (value: DigitalScreenInstance): RecentVisualization => ({
  id: `screen-${value.id}`,
  name: value.name,
  updatedAt: value.updatedAt || value.publishedAt || value.createdAt,
  path:
    value.status === 'published'
      ? `/digital-screen/${value.id}`
      : `/digital-screen/${value.id}/edit`,
});

function useVisualizationOverview(): VisualizationState {
  const [state, setState] = useState<VisualizationState>({
    dashboardLoading: true,
    screenLoading: true,
    dashboardFailed: false,
    screenFailed: false,
  });

  useEffect(() => {
    let active = true;

    fetchDashboardOverview(4)
      .then((dashboard) => {
        if (!active) return;
        setState((current) => ({
          ...current,
          dashboard,
          dashboardLoading: false,
          dashboardFailed: false,
        }));
      })
      .catch(() => {
        if (!active) return;
        setState((current) => ({
          ...current,
          dashboardLoading: false,
          dashboardFailed: true,
        }));
      });

    listDigitalScreens()
      .then((screens) => {
        if (!active) return;
        setState((current) => ({
          ...current,
          screens,
          screenLoading: false,
          screenFailed: false,
        }));
      })
      .catch(() => {
        if (!active) return;
        setState((current) => ({
          ...current,
          screenLoading: false,
          screenFailed: true,
        }));
      });

    return () => {
      active = false;
    };
  }, []);

  return state;
}

export default function HomeVisualizationOverview() {
  const intl = useIntl();
  const state = useVisualizationOverview();
  const dashboardCount = state.dashboard?.dashboardCount;
  const screenCount = state.screens?.length;
  const publishedScreens = state.screens?.filter(
    (item) => item.status === 'published',
  ).length;
  const latest = useMemo(() => {
    const items = [
      ...(state.dashboard?.recentDashboards || []).map(dashboardItem),
      ...(state.screens || []).map(screenItem),
    ];
    return items.sort(
      (left, right) => timestamp(right.updatedAt) - timestamp(left.updatedAt),
    )[0];
  }, [state.dashboard, state.screens]);

  const total =
    dashboardCount == null && screenCount == null
      ? undefined
      : (dashboardCount ?? 0) + (screenCount ?? 0);
  const published =
    state.dashboard?.publishedDashboardCount == null && publishedScreens == null
      ? undefined
      : (state.dashboard?.publishedDashboardCount ?? 0) + (publishedScreens ?? 0);

  return (
    <section className="flex h-[188px] min-w-0 flex-col rounded-[18px] border border-[#f0f1f3] bg-white px-5 pb-4 pt-4">
      <SectionHeader
        title={intl.formatMessage({ id: 'pages.home.visualization.title' })}
        onMore={() => history.push('/dashboard')}
      />

      <div className="mt-3 flex min-h-0 flex-1 flex-col justify-between">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[11px] bg-[#f2f4fb] text-[#7783ad]">
            <LayoutDashboard size={18} strokeWidth={1.8} />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex items-baseline gap-2">
              <strong className="text-[25px] font-semibold leading-7 tracking-[-0.6px] text-[#30343d]">
                {formatMetric(total, intl.locale)}
              </strong>
              <span className="text-[10px] text-[#969aa3]">
                {intl.formatMessage({ id: 'pages.home.visualization.title' })}
              </span>
            </div>
            <div className="mt-1 flex items-center gap-3 text-[10px] text-[#8f949d]">
              <span className="flex items-center gap-1">
                <LayoutDashboard size={11} strokeWidth={1.8} />
                {formatMetric(dashboardCount, intl.locale)}
              </span>
              <span className="flex items-center gap-1">
                <Monitor size={11} strokeWidth={1.8} />
                {formatMetric(screenCount, intl.locale)}
              </span>
              <span>
                {intl.formatMessage({ id: 'pages.home.visualization.status.published' })}{' '}
                <strong className="font-semibold text-[#555a64]">
                  {formatMetric(published, intl.locale)}
                </strong>
              </span>
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={() => history.push(latest?.path || '/dashboard')}
          className="group flex h-9 w-full items-center gap-2 rounded-[9px] border-0 bg-[#f8f9fb] px-3 text-left"
        >
          <Clock3 size={13} strokeWidth={1.8} className="shrink-0 text-[#8995ad]" />
          <span className="min-w-0 flex-1 truncate text-[10px] text-[#747a84]">
            {latest?.name ||
              intl.formatMessage({
                id:
                  state.dashboardLoading || state.screenLoading
                    ? 'pages.home.visualization.loading'
                    : state.dashboardFailed && state.screenFailed
                      ? 'pages.home.visualization.failed'
                      : 'pages.home.visualization.empty',
              })}
          </span>
          {latest ? (
            <span className="shrink-0 text-[9px] text-[#a0a4ac]">
              {relativeTime(latest.updatedAt, intl.locale)}
            </span>
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
