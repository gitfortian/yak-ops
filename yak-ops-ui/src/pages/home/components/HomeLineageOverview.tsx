import YakOpsEmpty from '@/components/YakOpsEmpty';
import { history } from '@umijs/max';
import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import { Activity } from 'lucide-react';
import { useMemo } from 'react';

import { HomeEmptyState } from './HomeEmptyState';
import {
  assetTypeColor,
  compactName,
  formatMetric,
  type HomeAssetOverviewState,
  relativeTime,
  relationTypeLabel,
  SectionHeader,
} from './homeAssetOverviewShared';
import type { HomeLineageActivity } from './service';

function buildLineageGraphOption(
  state: HomeAssetOverviewState,
): EChartsOption {
  const nodes = state.data?.lineage?.nodes || [];
  const edges = state.data?.lineage?.edges || [];
  return {
    animationDuration: 520,
    animationDurationUpdate: 420,
    tooltip: {
      trigger: 'item',
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: false,
        draggable: false,
        data: nodes.map((node) => ({
          id: node.id,
          name: compactName(node.name),
          value: node.assetType,
          symbolSize: node.assetType === 'COLUMN' ? 28 : 36,
          itemStyle: {
            color: assetTypeColor(node.assetType),
            borderColor: '#ffffff',
            borderWidth: 2,
            shadowBlur: 8,
            shadowColor: 'rgba(31,35,41,0.10)',
          },
          label: {
            show: true,
            position: 'bottom',
            color: '#5f646e',
            fontSize: 9,
            distance: 5,
          },
        })),
        links: edges.map((edge) => ({
          id: edge.id,
          source: edge.sourceAssetId,
          target: edge.targetAssetId,
          value: relationTypeLabel(edge.relationType),
        })),
        force: {
          repulsion: 150,
          edgeLength: [70, 120],
          gravity: 0.08,
        },
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 6,
        lineStyle: {
          color: '#cbd2dc',
          width: 1.4,
          curveness: 0.08,
        },
        emphasis: {
          focus: 'adjacency',
        },
      },
    ],
  };
}

function LineagePreview({ state }: { state: HomeAssetOverviewState }) {
  const graphOption = useMemo(() => buildLineageGraphOption(state), [state]);
  const hasGraph = (state.data?.lineage?.nodes.length || 0) > 0;
  const unavailable = state.data?.lineage?.assetCount == null;

  return (
    <div className="relative h-[276px] overflow-hidden rounded-[14px] border border-[#eef0f3] bg-[#fafbfc]">
      <div className="absolute inset-0 opacity-[0.5] [background-image:radial-gradient(circle,#dfe2e7_0.7px,transparent_0.8px)] [background-size:12px_12px]" />
      {hasGraph ? (
        <ReactECharts
          option={graphOption}
          style={{ width: '100%', height: '276px' }}
        />
      ) : state.loading || state.failed || unavailable ? (
        <div className="relative flex h-full items-center justify-center text-[11px] text-[#a0a4ac]">
          {state.loading
            ? '血缘加载中...'
            : state.failed
              ? '血缘数据加载失败'
              : '血缘数据暂不可用'}
        </div>
      ) : (
        <div className="relative flex h-full items-center justify-center">
          <YakOpsEmpty
            width={160}
            height={108}
            title="暂无血缘关系"
            showCaption
          />
        </div>
      )}
      <div className="absolute bottom-3 left-3 rounded-full border border-[#e6e8ec] bg-white/90 px-2.5 py-1 text-[9px] text-[#999da5] shadow-sm backdrop-blur">
        最近血缘关系
      </div>
    </div>
  );
}

function LineageMetric({
  label,
  value,
}: {
  label: string;
  value?: number | null;
}) {
  return (
    <div>
      <div className="text-[10px] text-[#989ca4]">{label}</div>
      <strong className="mt-1 block text-[20px] font-semibold text-[#3c4049]">
        {formatMetric(value)}
      </strong>
    </div>
  );
}

function LineageUpdate({ item }: { item: HomeLineageActivity }) {
  return (
    <button
      type="button"
      onClick={() => history.push('/data-analysis/lineage')}
      className="group flex w-full items-center gap-2 border-0 bg-transparent p-0 text-left"
    >
      <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-[#8394e8]" />
      <span className="min-w-0 flex-1">
        <strong className="block truncate text-[11px] font-medium text-[#555a64]">
          {item.sourceName} → {item.targetName}
        </strong>
        <span className="mt-0.5 block text-[9px] text-[#a1a5ad]">
          {relationTypeLabel(item.relationType)}
        </span>
      </span>
      <span className="shrink-0 text-[9px] text-[#a1a5ad]">
        {relativeTime(item.occurredAt)}
      </span>
    </button>
  );
}

export function DataLineageOverview({
  state,
}: {
  state: HomeAssetOverviewState;
}) {
  const lineage = state.data?.lineage;
  const activities = lineage?.recentActivities || [];
  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <SectionHeader
        title="数据血缘"
        description="观察最近血缘关系与资产规模"
        onMore={() => history.push('/data-analysis/lineage')}
      />

      <div className="mt-5 grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_220px]">
        <LineagePreview state={state} />
        <div className="flex flex-col">
          <div className="grid grid-cols-2 gap-x-5 gap-y-5">
            <LineageMetric label="数据节点" value={lineage?.assetCount} />
            <LineageMetric label="血缘关系" value={lineage?.relationCount} />
            <LineageMetric label="今日更新" value={lineage?.todayUpdatedCount} />
            <LineageMetric label="数据集节点" value={lineage?.datasetAssetCount} />
          </div>

          <div className="mt-6 border-t border-[#eef0f3] pt-4">
            <div className="flex items-center gap-1.5 text-[11px] font-medium text-[#525761]">
              <Activity size={13} strokeWidth={1.8} />
              最近关系
            </div>
            {activities.length > 0 ? (
              <div className="mt-3 space-y-3">
                {activities.map((item) => (
                  <LineageUpdate key={item.id} item={item} />
                ))}
              </div>
            ) : state.loading || state.failed || lineage?.assetCount == null ? (
              <div className="flex min-h-[118px] items-center justify-center text-[10px] text-[#a0a4ac]">
                {state.loading
                  ? '数据加载中...'
                  : state.failed
                    ? '数据加载失败'
                    : '数据暂不可用'}
              </div>
            ) : (
              <HomeEmptyState
                icon={Activity}
                title="暂无最近关系"
                size="small"
                className="min-h-[118px]"
              />
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
