import { listDigitalScreens, type DigitalScreenInstance } from '@/services/digital-screen';
import {
  getDataServiceOverview,
  type DataServiceOverview,
} from '@/services/data-service';
import {
  fetchDashboardOverview,
  type DashboardOverview,
} from '@/services/dashboard';
import {
  homeQualityOverviewApi,
  type HomeQualityOverview,
} from '@/services/home';
import { history, useIntl } from '@umijs/max';
import {
  ChevronRight,
  Database,
  GitBranch,
  LayoutDashboard,
  Server,
  ShieldCheck,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { useEffect, useMemo, useState } from 'react';

import { useHomeAssetOverview } from './HomeAssetOverview';
import { formatMetric } from './homeAssetOverviewShared';

interface QualityState {
  data?: HomeQualityOverview;
  loading: boolean;
  failed: boolean;
}

interface DeliveryState {
  dashboard?: DashboardOverview;
  screens?: DigitalScreenInstance[];
  service?: DataServiceOverview;
}

const rateText = (value?: number | null) =>
  value == null ? '--' : `${value.toFixed(1)}%`;

function useQualityOverview(): QualityState {
  const [state, setState] = useState<QualityState>({
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

function useDeliveryOverview(): DeliveryState {
  const [state, setState] = useState<DeliveryState>({});

  useEffect(() => {
    let active = true;

    fetchDashboardOverview(1)
      .then((dashboard) => {
        if (active) setState((current) => ({ ...current, dashboard }));
      })
      .catch(() => undefined);

    listDigitalScreens()
      .then((screens) => {
        if (active) setState((current) => ({ ...current, screens }));
      })
      .catch(() => undefined);

    getDataServiceOverview('7d')
      .then((service) => {
        if (active) setState((current) => ({ ...current, service }));
      })
      .catch(() => undefined);

    return () => {
      active = false;
    };
  }, []);

  return state;
}

function PanelHeader({
  title,
  onMore,
}: {
  title: string;
  onMore: () => void;
}) {
  const intl = useIntl();

  return (
    <header className="flex shrink-0 items-center justify-between gap-4">
      <h2 className="text-[17px] font-semibold tracking-[-0.25px] text-[#292d35]">
        {title}
      </h2>
      <button
        type="button"
        onClick={onMore}
        className="flex items-center gap-0.5 border-0 bg-transparent p-0 text-[11px] text-[#858a93] transition-colors hover:text-[#343842]"
      >
        {intl.formatMessage({ id: 'pages.home.common.viewMore' })}
        <ChevronRight size={13} strokeWidth={1.8} />
      </button>
    </header>
  );
}

type TileTone = 'blue' | 'purple' | 'green' | 'amber';

const TONE_CLASS: Record<TileTone, string> = {
  blue: 'bg-[#eef4ff] text-[#5e82d9]',
  purple: 'bg-[#f3f0ff] text-[#7b6fca]',
  green: 'bg-[#eef8f2] text-[#4b8c68]',
  amber: 'bg-[#fff7ea] text-[#ba7a2a]',
};

function SummaryTile({
  icon,
  title,
  value,
  meta,
  path,
  tone,
  attention = false,
}: {
  icon: ReactNode;
  title: string;
  value: string;
  meta: string;
  path: string;
  tone: TileTone;
  attention?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={() => history.push(path)}
      className="group flex min-w-0 items-center gap-3 border-0 bg-transparent px-4 py-3 text-left transition-colors hover:bg-[#f8f9fb]"
    >
      <span
        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-[11px] ${TONE_CLASS[tone]}`}
      >
        {icon}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[11px] font-medium text-[#70757f]">
          {title}
        </span>
        <span className="mt-0.5 flex min-w-0 items-baseline gap-2">
          <strong className="shrink-0 text-[23px] font-semibold leading-7 tracking-[-0.55px] text-[#30343d]">
            {value}
          </strong>
          <span
            className={`min-w-0 truncate text-[10px] ${
              attention ? 'font-medium text-[#d65361]' : 'text-[#9a9ea6]'
            }`}
          >
            {meta}
          </span>
        </span>
      </span>
      <ChevronRight
        size={13}
        strokeWidth={1.8}
        className="shrink-0 text-[#c0c4ca] opacity-0 transition-all group-hover:translate-x-0.5 group-hover:opacity-100"
      />
    </button>
  );
}

function DataAssetPanel() {
  const intl = useIntl();
  const asset = useHomeAssetOverview();
  const quality = useQualityOverview();
  const dataset = asset.data?.dataset;
  const lineage = asset.data?.lineage;
  const issueCount = quality.data?.recentIssueCount;
  const isChinese = intl.locale.toLowerCase().startsWith('zh');

  const qualityMeta = quality.loading
    ? intl.formatMessage({ id: 'pages.home.common.loading' })
    : quality.failed
      ? intl.formatMessage({ id: 'pages.home.common.loadFailed' })
      : (issueCount ?? 0) > 0
        ? isChinese
          ? `${formatMetric(issueCount, intl.locale)} 项待处理`
          : `${formatMetric(issueCount, intl.locale)} to review`
        : isChinese
          ? '运行健康'
          : 'Healthy';

  const datasetToday =
    dataset?.todayCreatedCount == null
      ? '--'
      : `+${formatMetric(dataset.todayCreatedCount, intl.locale)}`;

  return (
    <section className="flex h-full min-h-[164px] flex-col rounded-[20px] border border-[#f0f1f3] bg-white px-5 pb-3 pt-4">
      <PanelHeader
        title={isChinese ? '数据资产' : 'Data Assets'}
        onMore={() => history.push('/data-analysis/data-catalog')}
      />

      <div className="mt-2 grid min-h-0 flex-1 grid-cols-1 divide-y divide-[#eef0f3] sm:grid-cols-3 sm:divide-x sm:divide-y-0">
        <SummaryTile
          icon={<Database size={18} strokeWidth={1.8} />}
          title={intl.formatMessage({ id: 'pages.home.dataset.title' })}
          value={formatMetric(dataset?.datasetCount, intl.locale)}
          meta={`${intl.formatMessage({ id: 'pages.home.dataset.todayCreated' })} ${datasetToday}`}
          path="/data-analysis/data-catalog"
          tone="blue"
        />
        <SummaryTile
          icon={<GitBranch size={18} strokeWidth={1.8} />}
          title={intl.formatMessage({ id: 'pages.home.lineage.title' })}
          value={formatMetric(lineage?.relationCount, intl.locale)}
          meta={`${intl.formatMessage({ id: 'pages.home.lineage.metric.todayUpdated' })} ${formatMetric(lineage?.todayUpdatedCount, intl.locale)}`}
          path="/data-analysis/lineage"
          tone="purple"
        />
        <SummaryTile
          icon={<ShieldCheck size={18} strokeWidth={1.8} />}
          title={intl.formatMessage({ id: 'pages.home.quality.title' })}
          value={rateText(quality.data?.passRate)}
          meta={qualityMeta}
          path="/data-quality/overview"
          tone={(issueCount ?? 0) > 0 ? 'amber' : 'green'}
          attention={(issueCount ?? 0) > 0}
        />
      </div>
    </section>
  );
}

function DataDeliveryPanel() {
  const intl = useIntl();
  const state = useDeliveryOverview();
  const isChinese = intl.locale.toLowerCase().startsWith('zh');

  const publishedScreens = state.screens?.filter(
    (item) => item.status === 'published',
  ).length;
  const visualizationTotal = useMemo(() => {
    const dashboardCount = state.dashboard?.dashboardCount;
    const screenCount = state.screens?.length;
    if (dashboardCount == null && screenCount == null) return undefined;
    return (dashboardCount ?? 0) + (screenCount ?? 0);
  }, [state.dashboard?.dashboardCount, state.screens]);
  const publishedTotal =
    state.dashboard?.publishedDashboardCount == null && publishedScreens == null
      ? undefined
      : (state.dashboard?.publishedDashboardCount ?? 0) +
        (publishedScreens ?? 0);

  const serviceRate =
    !state.service || state.service.totalCalls <= 0
      ? '--'
      : `${state.service.successRate.toFixed(1)}%`;

  const visualizationMeta = isChinese
    ? `已发布 ${formatMetric(publishedTotal, intl.locale)}`
    : `${formatMetric(publishedTotal, intl.locale)} published`;
  const serviceMeta = isChinese
    ? `${formatMetric(state.service?.runningApis, intl.locale)} 运行中 · ${serviceRate} 成功率`
    : `${formatMetric(state.service?.runningApis, intl.locale)} running · ${serviceRate} success`;

  return (
    <section className="flex h-full min-h-[164px] flex-col rounded-[20px] border border-[#f0f1f3] bg-white px-5 pb-3 pt-4">
      <PanelHeader
        title={isChinese ? '数据交付' : 'Data Delivery'}
        onMore={() => history.push('/dashboard')}
      />

      <div className="mt-2 grid min-h-0 flex-1 grid-cols-1 divide-y divide-[#eef0f3] sm:grid-cols-2 sm:divide-x sm:divide-y-0">
        <SummaryTile
          icon={<LayoutDashboard size={18} strokeWidth={1.8} />}
          title={intl.formatMessage({ id: 'pages.home.visualization.title' })}
          value={formatMetric(visualizationTotal, intl.locale)}
          meta={visualizationMeta}
          path="/dashboard"
          tone="purple"
        />
        <SummaryTile
          icon={<Server size={18} strokeWidth={1.8} />}
          title={intl.formatMessage({ id: 'pages.home.dataService.title' })}
          value={formatMetric(state.service?.apiTotal, intl.locale)}
          meta={serviceMeta}
          path="/data-service/overview"
          tone="blue"
        />
      </div>
    </section>
  );
}

/**
 * 首页主工作区的轻量能力概览。
 *
 * 数据资产聚合数据集、血缘和质量；数据交付聚合可视化和数据服务。
 * 两个模块只保留首页判断所需的核心数字，详细信息留在对应业务页面。
 */
export function HomeWorkbenchMain() {
  return (
    <div className="grid min-w-0 gap-4 xl:min-h-0 xl:flex-1 xl:grid-rows-2">
      <DataAssetPanel />
      <DataDeliveryPanel />
    </div>
  );
}

export default function HomeWorkbench() {
  return <HomeWorkbenchMain />;
}
