import { history, useIntl } from '@umijs/max';
import { Boxes, ChevronRight, Database, Table2 } from 'lucide-react';

import {
  formatMetric,
  type HomeAssetOverviewState,
  relativeTime,
  SectionHeader,
} from './homeAssetOverviewShared';
import type { HomeAssetDatasetItem } from './service';

function datasetStatusLabel(
  status: string | undefined,
  formatMessage: (id: string) => string,
) {
  const normalized = status?.toUpperCase();
  if (normalized === 'ONLINE') {
    return formatMessage('pages.home.dataset.status.online');
  }
  if (normalized === 'OFFLINE') {
    return formatMessage('pages.home.dataset.status.offline');
  }
  return status || formatMessage('pages.home.dataset.status.unknown');
}

function DatasetRow({ item }: { item: HomeAssetDatasetItem }) {
  const intl = useIntl();

  return (
    <button
      type="button"
      onClick={() => history.push('/data-analysis/data-catalog')}
      className="group flex w-full items-center gap-3 rounded-[8px] border-0 bg-transparent px-2 py-2 text-left transition-colors hover:bg-[#f7f8fa]"
    >
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] bg-[#f1f4f8] text-[#69717e]">
        <Table2 size={15} strokeWidth={1.8} />
      </span>
      <span className="min-w-0 flex-1">
        <strong className="block truncate text-[12px] font-medium leading-5 text-[#3d414a]">
          {item.name}
        </strong>
        <span className="mt-0.5 block truncate text-[10px] leading-4 text-[#9ca0a8]">
          {datasetStatusLabel(item.status, (id) => intl.formatMessage({ id }))}
        </span>
      </span>
      <span className="shrink-0 text-[10px] text-[#a0a4ac]">
        {relativeTime(item.updatedAt, intl.locale)}
      </span>
      <ChevronRight
        size={13}
        strokeWidth={1.8}
        className="shrink-0 text-[#c0c4cb] transition-transform group-hover:translate-x-0.5"
      />
    </button>
  );
}

function Metric({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value?: number | null;
}) {
  const intl = useIntl();

  return (
    <div className="flex min-w-0 items-center gap-3">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] bg-[#f3f5fb] text-[#6c7fd3]">
        {icon}
      </span>
      <span className="min-w-0">
        <span className="block truncate text-[10px] text-[#969aa3]">{label}</span>
        <strong className="mt-0.5 block text-[18px] font-semibold leading-6 text-[#343842]">
          {formatMetric(value, intl.locale)}
        </strong>
      </span>
    </div>
  );
}

export function DatasetOverview({ state }: { state: HomeAssetOverviewState }) {
  const intl = useIntl();
  const dataset = state.data?.dataset;
  const items = dataset?.recentDatasets?.slice(0, 4) ?? [];

  return (
    <section className="rounded-[20px] border border-[#f0f1f3] bg-white px-5 pb-5 pt-5">
      <SectionHeader
        title={intl.formatMessage({ id: 'pages.home.dataset.title' })}
        onMore={() => history.push('/data-analysis/data-catalog')}
      />

      <div className="mt-4 grid grid-cols-1 gap-5 lg:grid-cols-[290px_minmax(0,1fr)] lg:gap-6">
        <div className="rounded-[14px] bg-[#f8f9fb] px-4 py-4">
          <div className="flex items-end justify-between gap-4 border-b border-[#eceef2] pb-3">
            <div>
              <span className="text-[10px] text-[#92969f]">
                {intl.formatMessage({ id: 'pages.home.dataset.metric.dataset' })}
              </span>
              <div className="mt-1 flex items-baseline gap-2">
                <strong className="text-[30px] font-semibold leading-8 tracking-[-0.8px] text-[#2f333c]">
                  {formatMetric(dataset?.datasetCount, intl.locale)}
                </strong>
                <span className="text-[10px] text-[#9da1a9]">
                  {intl.formatMessage({ id: 'pages.home.dataset.unit' })}
                </span>
              </div>
            </div>
            <div className="text-right">
              <span className="text-[10px] text-[#92969f]">
                {intl.formatMessage({ id: 'pages.home.dataset.todayCreated' })}
              </span>
              <strong className="mt-1 block text-[17px] font-semibold text-[#343842]">
                +{formatMetric(dataset?.todayCreatedCount, intl.locale)}
              </strong>
            </div>
          </div>

          <div className="mt-4 grid grid-cols-2 gap-4">
            <Metric
              icon={<Table2 size={16} strokeWidth={1.8} />}
              label={intl.formatMessage({ id: 'pages.home.dataset.metric.lineageTable' })}
              value={dataset?.tableAssetCount}
            />
            <Metric
              icon={<Boxes size={16} strokeWidth={1.8} />}
              label={intl.formatMessage({ id: 'pages.home.dataset.metric.lineageColumn' })}
              value={dataset?.columnAssetCount}
            />
          </div>
        </div>

        <div className="min-w-0">
          <div className="flex items-center justify-between gap-3 pb-2">
            <strong className="text-[12px] font-semibold text-[#454a53]">
              {intl.formatMessage({ id: 'pages.home.dataset.recentUpdated' })}
            </strong>
            <span className="text-[10px] text-[#9da1a9]">
              {intl.formatMessage({ id: 'pages.home.dataset.byUpdatedAt' })}
            </span>
          </div>

          {items.length > 0 ? (
            <div className="grid grid-cols-1 gap-x-3 sm:grid-cols-2">
              {items.map((item) => (
                <DatasetRow key={item.id} item={item} />
              ))}
            </div>
          ) : (
            <button
              type="button"
              onClick={() => history.push('/data-analysis/data-catalog')}
              className="flex h-[108px] w-full items-center justify-center rounded-[12px] border border-dashed border-[#e3e6eb] bg-[#fafbfc] text-[11px] text-[#9da1a8]"
            >
              {state.loading
                ? intl.formatMessage({ id: 'pages.home.common.loading' })
                : state.failed
                  ? intl.formatMessage({ id: 'pages.home.common.loadFailed' })
                  : intl.formatMessage({ id: 'pages.home.dataset.empty' })}
            </button>
          )}
        </div>
      </div>
    </section>
  );
}
