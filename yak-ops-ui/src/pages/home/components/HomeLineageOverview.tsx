import { history, useIntl } from '@umijs/max';
import { Activity, ChevronRight, GitBranch } from 'lucide-react';

import {
  formatMetric,
  type HomeAssetOverviewState,
  relativeTime,
  relationTypeLabel,
  SectionHeader,
  type HomeLineageRelationKey,
} from './homeAssetOverviewShared';

export function DataLineageOverview({
  state,
}: {
  state: HomeAssetOverviewState;
}) {
  const intl = useIntl();
  const lineage = state.data?.lineage;
  const latest = lineage?.recentActivities?.[0];
  const resolveRelation = (key: HomeLineageRelationKey) =>
    intl.formatMessage({ id: `pages.home.lineage.relation.${key}` });

  return (
    <section className="flex h-[188px] min-w-0 flex-col rounded-[18px] border border-[#f0f1f3] bg-white px-5 pb-4 pt-4">
      <SectionHeader
        title={intl.formatMessage({ id: 'pages.home.lineage.title' })}
        onMore={() => history.push('/data-analysis/lineage')}
      />

      <div className="mt-3 flex min-h-0 flex-1 flex-col justify-between">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[11px] bg-[#f2f4fb] text-[#7082d7]">
            <GitBranch size={18} strokeWidth={1.8} />
          </span>
          <div className="min-w-0">
            <div className="flex items-baseline gap-2">
              <strong className="text-[25px] font-semibold leading-7 tracking-[-0.6px] text-[#30343d]">
                {formatMetric(lineage?.relationCount, intl.locale)}
              </strong>
              <span className="text-[10px] text-[#969aa3]">
                {intl.formatMessage({ id: 'pages.home.lineage.metric.relations' })}
              </span>
            </div>
            <div className="mt-1 flex items-center gap-3 text-[10px] text-[#8f949d]">
              <span>
                {intl.formatMessage({ id: 'pages.home.lineage.metric.nodes' })}{' '}
                <strong className="font-semibold text-[#555a64]">
                  {formatMetric(lineage?.assetCount, intl.locale)}
                </strong>
              </span>
              <span>
                {intl.formatMessage({ id: 'pages.home.lineage.metric.todayUpdated' })}{' '}
                <strong className="font-semibold text-[#555a64]">
                  {formatMetric(lineage?.todayUpdatedCount, intl.locale)}
                </strong>
              </span>
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={() => history.push('/data-analysis/lineage')}
          className="group flex h-9 w-full items-center gap-2 rounded-[9px] border-0 bg-[#f8f9fb] px-3 text-left"
        >
          <Activity size={13} strokeWidth={1.8} className="shrink-0 text-[#8995cd]" />
          <span className="min-w-0 flex-1 truncate text-[10px] text-[#747a84]">
            {latest
              ? `${latest.sourceName} → ${latest.targetName} · ${relationTypeLabel(
                  latest.relationType,
                  resolveRelation,
                )}`
              : state.loading
                ? intl.formatMessage({ id: 'pages.home.common.loading' })
                : state.failed
                  ? intl.formatMessage({ id: 'pages.home.common.loadFailed' })
                  : intl.formatMessage({ id: 'pages.home.lineage.emptyRecent' })}
          </span>
          {latest ? (
            <span className="shrink-0 text-[9px] text-[#a0a4ac]">
              {relativeTime(latest.occurredAt, intl.locale)}
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
