import {
  BRAND_COLOR,
  BRAND_COLOR_SOFT,
  BRAND_THEME,
} from '@/styles/brand';
import { history } from '@umijs/max';
import {
  Button,
  ConfigProvider,
  Empty,
  Select,
  Segmented,
  Spin,
  Tooltip,
  message,
} from 'antd';
import {
  ArrowUpRight,
  Boxes,
  ChevronRight,
  GitBranch,
  Layers3,
  LocateFixed,
  Network,
  RefreshCw,
  Search,
  Sparkles,
  X,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import ReactFlow, {
  Background,
  Controls,
  MarkerType,
  MiniMap,
  type Edge,
  type Node,
} from 'reactflow';
import 'reactflow/dist/style.css';
import LineageNode, { type LineageNodeData } from './LineageNode';
import { buildLineageView, downstreamImpact } from './graph-layout';
import {
  fetchLineageAssetByKey,
  fetchLineageGraph,
  searchLineageAssets,
} from './service';
import {
  LINEAGE_ASSET_TYPES,
  assetTypeLabel,
  relationTypeLabel,
  type LineageAsset,
  type LineageAssetType,
  type LineageDirection,
  type LineageGraph,
  type LineageRelation,
} from './types';
import { lineageAssetVisual, lineageRelationColor } from './visual';

const DEFAULT_DEPTH = 3;
const SEARCH_LIMIT = 30;
const ALL_TYPES = [...LINEAGE_ASSET_TYPES];

const nodeTypes = { lineage: LineageNode };

const formatTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date).replaceAll('/', '-');
};

const formatValue = (value: unknown) => {
  if (value == null) return '-';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
};

const assetLocation = (asset: LineageAsset) => [
  asset.databaseName,
  asset.schemaName,
  asset.tableName,
  asset.columnName,
].filter(Boolean).join('.') || '-';

const businessLink = (asset: LineageAsset): { label: string; path: string } | undefined => {
  if (asset.assetType === 'SQL_TASK' && asset.sourceId) {
    return {
      label: '打开开发任务',
      path: `/data-development/task/${encodeURIComponent(asset.sourceId)}`,
    };
  }
  if (asset.assetType === 'DASHBOARD' && asset.sourceId) {
    return {
      label: '打开仪表盘',
      path: `/dashboard/${encodeURIComponent(asset.sourceId)}`,
    };
  }
  if (asset.assetType === 'DATASET' || asset.assetType === 'DATASET_FIELD') {
    return { label: '打开数据目录', path: '/data-analysis/data-catalog' };
  }
  if (asset.assetType === 'CHART') {
    return { label: '打开仪表盘', path: '/dashboard' };
  }
  if (asset.assetType === 'TABLE' || asset.assetType === 'COLUMN') {
    return { label: '打开数据源', path: '/data-source' };
  }
  return undefined;
};

const assetPropertyEntries = (asset?: LineageAsset) => Object.entries(asset?.properties || {})
  .filter(([, value]) => value !== undefined && value !== null)
  .slice(0, 18);

const relationPropertyEntries = (relation?: LineageRelation) => Object.entries(relation?.properties || {})
  .filter(([, value]) => value !== undefined && value !== null)
  .slice(0, 18);

const AssetTypePill = ({
  type,
  compact = false,
}: {
  type: LineageAssetType;
  compact?: boolean;
}) => {
  const visual = lineageAssetVisual[type];
  return (
    <span
      className={[
        'inline-flex shrink-0 items-center rounded-full border font-semibold',
        compact ? 'gap-1 px-1.5 py-0.5 text-[10px]' : 'gap-1.5 px-2 py-1 text-[11px]',
      ].join(' ')}
      style={{
        color: visual.accent,
        background: visual.soft,
        borderColor: visual.border,
      }}
    >
      <span
        className={compact ? 'h-1.5 w-1.5 rounded-full' : 'h-2 w-2 rounded-full'}
        style={{ background: visual.accent }}
      />
      {assetTypeLabel[type]}
    </span>
  );
};

const DetailRow = ({ label, value }: { label: string; value: unknown }) => (
  <div className="grid grid-cols-[92px_minmax(0,1fr)] gap-3 border-b border-[#F0F2F5] py-2.5 text-[12px] last:border-b-0">
    <span className="text-[#8A94A3]">{label}</span>
    <span className="min-w-0 break-all font-medium text-[#3C4655]">
      {formatValue(value)}
    </span>
  </div>
);

const ImpactChip = ({
  label,
  value,
  type,
}: {
  label: string;
  value: number;
  type?: LineageAssetType;
}) => {
  const visual = type ? lineageAssetVisual[type] : undefined;
  return (
    <span
      className="inline-flex h-8 items-center gap-2 rounded-full border px-3 text-[12px]"
      style={{
        color: visual?.accent || '#526071',
        background: visual?.soft || '#F8FAFC',
        borderColor: visual?.border || '#E2E8F0',
      }}
    >
      <span>{label}</span>
      <strong className="font-bold text-[#182230]">{value}</strong>
    </span>
  );
};

const SectionTitle = ({ children }: { children: React.ReactNode }) => (
  <div className="mb-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-[#8A94A3]">
    {children}
  </div>
);

export default function LineagePage() {
  const [rootAsset, setRootAsset] = useState<LineageAsset>();
  const [graph, setGraph] = useState<LineageGraph>();
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [depth, setDepth] = useState(DEFAULT_DEPTH);
  const [direction, setDirection] = useState<LineageDirection>('BOTH');
  const [visibleTypes, setVisibleTypes] = useState<LineageAssetType[]>(ALL_TYPES);
  const [selectedAsset, setSelectedAsset] = useState<LineageAsset>();
  const [selectedRelation, setSelectedRelation] = useState<LineageRelation>();

  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchType, setSearchType] = useState<'ALL' | LineageAssetType>('ALL');
  const [searchResults, setSearchResults] = useState<LineageAsset[]>([]);
  const [searching, setSearching] = useState(false);

  const selectRoot = useCallback((asset: LineageAsset, syncUrl = true) => {
    setRootAsset(asset);
    setSelectedAsset(asset);
    setSelectedRelation(undefined);
    setLoadError('');
    if (syncUrl) {
      history.replace(`/data-analysis/lineage?assetKey=${encodeURIComponent(asset.assetKey)}`);
    }
  }, []);

  const loadAssetByKey = useCallback(async (assetKey: string, syncUrl = true) => {
    setLoading(true);
    setLoadError('');
    try {
      const asset = await fetchLineageAssetByKey(assetKey);
      selectRoot(asset, syncUrl);
    } catch (error) {
      const text = error instanceof Error ? error.message : '查询血缘资产失败';
      setLoadError(text);
      message.error(text);
    } finally {
      setLoading(false);
    }
  }, [selectRoot]);

  useEffect(() => {
    const assetKey = new URLSearchParams(window.location.search).get('assetKey');
    if (assetKey) void loadAssetByKey(assetKey, false);
  }, [loadAssetByKey]);

  useEffect(() => {
    if (!rootAsset) {
      setGraph(undefined);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setLoadError('');
    void fetchLineageGraph(rootAsset.id, depth)
      .then((value) => {
        if (cancelled) return;
        setGraph(value);
        setSelectedAsset((current) => {
          if (!current || current.id === rootAsset.id) return value.root;
          return value.nodes.find((item) => item.id === current.id) || value.root;
        });
        setSelectedRelation((current) =>
          current ? value.relations.find((item) => item.id === current.id) : undefined,
        );
      })
      .catch((error) => {
        if (cancelled) return;
        const text = error instanceof Error ? error.message : '加载血缘图失败';
        setGraph(undefined);
        setLoadError(text);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [depth, rootAsset]);

  useEffect(() => {
    const keyword = searchKeyword.trim();
    if (!keyword && searchType === 'ALL') {
      setSearchResults([]);
      setSearching(false);
      return;
    }
    let cancelled = false;
    const timer = window.setTimeout(() => {
      setSearching(true);
      void searchLineageAssets({
        keyword,
        assetType: searchType === 'ALL' ? undefined : searchType,
        limit: SEARCH_LIMIT,
      })
        .then((values) => {
          if (!cancelled) setSearchResults(values);
        })
        .catch(() => {
          if (!cancelled) setSearchResults([]);
        })
        .finally(() => {
          if (!cancelled) setSearching(false);
        });
    }, 220);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [searchKeyword, searchType]);

  const view = useMemo(() => {
    if (!graph) return undefined;
    return buildLineageView(graph, direction, new Set(visibleTypes));
  }, [direction, graph, visibleTypes]);

  const impact = useMemo(
    () => (graph ? downstreamImpact(graph) : undefined),
    [graph],
  );

  const flowNodes = useMemo<Array<Node<LineageNodeData>>>(() => (
    view?.nodes.map(({ asset, position }) => ({
      id: asset.id,
      type: 'lineage',
      position,
      draggable: false,
      selectable: true,
      selected: selectedAsset?.id === asset.id && !selectedRelation,
      data: { asset, root: asset.id === graph?.root.id },
    })) || []
  ), [graph?.root.id, selectedAsset?.id, selectedRelation, view?.nodes]);

  const flowEdges = useMemo<Edge[]>(() => (
    view?.relations.map((relation) => {
      const selected = selectedRelation?.id === relation.id;
      const color = lineageRelationColor[relation.relationType];
      return {
        id: relation.id,
        source: relation.sourceAssetId,
        target: relation.targetAssetId,
        type: 'smoothstep',
        animated: selected,
        markerEnd: {
          type: MarkerType.ArrowClosed,
          width: selected ? 17 : 14,
          height: selected ? 17 : 14,
          color,
        },
        style: {
          stroke: color,
          strokeWidth: selected ? 2.4 : 1.5,
          opacity: selected ? 1 : 0.58,
        },
        data: { relation },
      };
    }) || []
  ), [selectedRelation?.id, view?.relations]);

  const nodeById = useMemo(
    () => new Map((graph?.nodes || []).map((asset) => [asset.id, asset])),
    [graph?.nodes],
  );
  const selectedRelationSource = selectedRelation
    ? nodeById.get(selectedRelation.sourceAssetId)
    : undefined;
  const selectedRelationTarget = selectedRelation
    ? nodeById.get(selectedRelation.targetAssetId)
    : undefined;
  const selectedBusinessLink = selectedAsset ? businessLink(selectedAsset) : undefined;
  const selectedAssetVisual = selectedAsset
    ? lineageAssetVisual[selectedAsset.assetType]
    : undefined;
  const selectedRelationColor = selectedRelation
    ? lineageRelationColor[selectedRelation.relationType]
    : '#64748B';
  const graphKey = rootAsset
    ? `${rootAsset.id}:${depth}:${direction}:${visibleTypes.join(',')}`
    : 'empty';

  const refresh = () => {
    if (!rootAsset) return;
    setLoading(true);
    setLoadError('');
    void fetchLineageGraph(rootAsset.id, depth)
      .then((value) => {
        setGraph(value);
        setSelectedAsset(value.root);
        setSelectedRelation(undefined);
      })
      .catch((error) => {
        setLoadError(error instanceof Error ? error.message : '加载血缘图失败');
      })
      .finally(() => setLoading(false));
  };

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="lineage-page flex h-full min-h-[600px] flex-col overflow-hidden bg-[#F6F8FC] text-[#182230]">
        <header
          className="shrink-0 border-b border-[#E7EAF0] bg-white px-5 pb-4 pt-4"
          style={{
            backgroundImage: 'linear-gradient(180deg, #FFFFFF 0%, #FBFCFF 100%)',
          }}
        >
          <div className="flex min-w-0 items-center gap-3">
            <div
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[13px] text-white shadow-[0_8px_24px_-12px_rgba(254,44,85,.8)]"
              style={{
                background: 'linear-gradient(135deg, #FE2C55 0%, #A855F7 100%)',
              }}
            >
              <Network size={20} strokeWidth={1.9} />
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h1 className="m-0 text-[18px] font-bold leading-6 text-[#182230]">
                  数据血缘
                </h1>
                <span className="rounded-full bg-[#F3E8FF] px-2 py-0.5 text-[10px] font-semibold text-[#7C3AED]">
                  Lineage
                </span>
              </div>
              <div className="mt-0.5 text-[12px] text-[#7A8493]">
                从数据来源到消费端，追踪资产依赖、变更影响和流转路径
              </div>
            </div>
            {rootAsset ? (
              <div className="ml-auto hidden items-center gap-2 xl:flex">
                <span className="text-[11px] text-[#8A94A3]">当前中心</span>
                <AssetTypePill type={rootAsset.assetType} compact />
                <span className="max-w-[220px] truncate text-[12px] font-semibold text-[#344054]">
                  {rootAsset.name}
                </span>
              </div>
            ) : null}
          </div>

          <div className="mt-4 flex min-w-0 flex-wrap items-center gap-2 rounded-[14px] border border-[#E4E8F0] bg-[#F8FAFD] p-2 shadow-[inset_0_1px_0_rgba(255,255,255,.75)]">
            <Select
              showSearch
              allowClear
              value={undefined}
              searchValue={searchKeyword}
              filterOption={false}
              loading={searching}
              placeholder={rootAsset ? `搜索并切换中心资产，当前：${rootAsset.name}` : '搜索名称、表名或 assetKey'}
              className="w-[390px]"
              suffixIcon={<Search size={14} className="text-[#8A94A3]" />}
              onSearch={setSearchKeyword}
              onClear={() => {
                setSearchKeyword('');
                setSearchResults([]);
              }}
              onChange={(assetKey) => {
                const asset = searchResults.find((item) => item.assetKey === assetKey);
                setSearchKeyword('');
                if (asset) selectRoot(asset);
                else if (assetKey) void loadAssetByKey(String(assetKey));
              }}
              options={searchResults.map((asset) => ({
                value: asset.assetKey,
                label: (
                  <div className="flex min-w-0 items-center gap-2 py-1">
                    <AssetTypePill type={asset.assetType} compact />
                    <span className="min-w-0 flex-1 truncate text-[13px] font-medium text-[#344054]">
                      {asset.name}
                    </span>
                  </div>
                ),
              }))}
              notFoundContent={searching ? <Spin size="small" /> : '输入关键词定位资产'}
            />

            <Select
              value={searchType}
              className="w-[126px]"
              onChange={setSearchType}
              options={[
                { value: 'ALL', label: '全部类型' },
                ...LINEAGE_ASSET_TYPES.map((value) => ({
                  value,
                  label: assetTypeLabel[value],
                })),
              ]}
            />

            <div className="mx-1 h-5 w-px bg-[#DDE2EA]" />

            <Segmented
              size="middle"
              value={direction}
              onChange={(value) => setDirection(value as LineageDirection)}
              options={[
                { label: '上下游', value: 'BOTH' },
                { label: '仅上游', value: 'UPSTREAM' },
                { label: '仅下游', value: 'DOWNSTREAM' },
              ]}
            />

            <Select
              value={depth}
              className="w-[96px]"
              onChange={setDepth}
              options={[1, 2, 3, 4, 5].map((value) => ({
                value,
                label: `${value} 跳`,
              }))}
            />

            <Tooltip title="刷新血缘">
              <Button
                aria-label="刷新血缘"
                icon={<RefreshCw size={14} />}
                loading={loading}
                disabled={!rootAsset}
                onClick={refresh}
              />
            </Tooltip>
          </div>

          {rootAsset ? (
            <div className="mt-3 flex min-w-0 flex-wrap items-center gap-2">
              <div className="mr-1 flex min-w-0 items-center gap-2 rounded-full border border-[#E4E8F0] bg-white py-1 pl-2 pr-3 shadow-[0_4px_14px_-12px_rgba(15,23,42,.35)]">
                <span
                  className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full"
                  style={{
                    color: lineageAssetVisual[rootAsset.assetType].accent,
                    background: lineageAssetVisual[rootAsset.assetType].softStrong,
                  }}
                >
                  <LocateFixed size={12} />
                </span>
                <span className="shrink-0 text-[11px] text-[#8A94A3]">中心资产</span>
                <span className="max-w-[220px] truncate text-[12px] font-semibold text-[#344054]">
                  {rootAsset.name}
                </span>
                <ChevronRight size={12} className="shrink-0 text-[#B5BDC8]" />
                <span className="max-w-[260px] truncate font-mono text-[10px] text-[#98A2B3]">
                  {rootAsset.assetKey}
                </span>
              </div>

              <ImpactChip label="下游" value={impact?.total || 0} />
              <ImpactChip label="Dataset" value={impact?.byType.DATASET || 0} type="DATASET" />
              <ImpactChip label="图表" value={impact?.byType.CHART || 0} type="CHART" />
              <ImpactChip label="仪表盘" value={impact?.byType.DASHBOARD || 0} type="DASHBOARD" />
            </div>
          ) : null}
        </header>

        <div className="min-h-0 flex-1 p-3">
          <div className="flex h-full min-h-0 overflow-hidden rounded-[18px] border border-[#E2E7EF] bg-white shadow-[0_12px_40px_-28px_rgba(15,23,42,.45)]">
            <main
              className="relative min-w-0 flex-1 overflow-hidden"
              style={{
                background: 'linear-gradient(180deg, #F8FAFF 0%, #F5F7FB 100%)',
              }}
            >
              {rootAsset ? (
                <>
                  <div className="absolute left-4 top-4 z-10 flex max-w-[calc(100%-32px)] items-center gap-2 rounded-[12px] border border-[#DDE3EC] bg-white/95 p-2 shadow-[0_10px_28px_-20px_rgba(15,23,42,.45)] backdrop-blur-sm">
                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[8px] bg-[#F3E8FF] text-[#7C3AED]">
                      <Layers3 size={14} />
                    </div>
                    <span className="shrink-0 text-[11px] font-semibold text-[#667085]">
                      显示资产
                    </span>
                    <Select
                      mode="multiple"
                      size="small"
                      value={visibleTypes}
                      className="w-[330px]"
                      maxTagCount="responsive"
                      onChange={(values) => setVisibleTypes(values as LineageAssetType[])}
                      options={LINEAGE_ASSET_TYPES.map((value) => ({
                        value,
                        label: assetTypeLabel[value],
                      }))}
                    />
                    <Button
                      type="text"
                      size="small"
                      disabled={visibleTypes.length === ALL_TYPES.length}
                      onClick={() => setVisibleTypes(ALL_TYPES)}
                    >
                      全部
                    </Button>
                  </div>

                  <div className="absolute right-4 top-4 z-10 hidden items-center gap-2 rounded-full border border-[#E2E7EF] bg-white/90 px-3 py-1.5 text-[11px] text-[#7A8493] shadow-[0_8px_24px_-20px_rgba(15,23,42,.45)] backdrop-blur-sm 2xl:flex">
                    <Sparkles size={12} className="text-[#7C3AED]" />
                    单击查看详情 · 双击节点设为中心
                  </div>
                </>
              ) : null}

              {!rootAsset ? (
                <div className="flex h-full min-h-[560px] items-center justify-center px-6">
                  <div className="relative w-full max-w-[560px] overflow-hidden rounded-[24px] border border-[#E1E6EF] bg-white px-8 py-10 text-center shadow-[0_28px_70px_-46px_rgba(30,41,59,.55)]">
                    <div className="pointer-events-none absolute -left-16 -top-16 h-40 w-40 rounded-full bg-[#EEF4FF] blur-2xl" />
                    <div className="pointer-events-none absolute -bottom-20 -right-10 h-44 w-44 rounded-full bg-[#FCE7F3] blur-2xl" />
                    <div
                      className="relative mx-auto flex h-16 w-16 items-center justify-center rounded-[18px] text-white shadow-[0_14px_34px_-16px_rgba(124,58,237,.65)]"
                      style={{
                        background: 'linear-gradient(135deg, #2563EB 0%, #7C3AED 52%, #FE2C55 100%)',
                      }}
                    >
                      <Boxes size={28} strokeWidth={1.65} />
                    </div>
                    <div className="relative mt-5 text-[18px] font-bold text-[#182230]">
                      从一个资产开始探索完整数据链路
                    </div>
                    <div className="relative mx-auto mt-2 max-w-[430px] text-[13px] leading-6 text-[#7A8493]">
                      在顶部搜索数据表、SQL 任务、Dataset、图表或仪表盘，系统会自动展开它的上下游依赖关系。
                    </div>
                    <div className="relative mt-5 flex flex-wrap justify-center gap-2">
                      {(['TABLE', 'SQL_TASK', 'DATASET', 'CHART', 'DASHBOARD'] as LineageAssetType[]).map((type) => (
                        <AssetTypePill key={type} type={type} />
                      ))}
                    </div>
                    <div className="relative mt-6 inline-flex items-center gap-2 rounded-full bg-[#F8FAFC] px-3 py-1.5 text-[11px] text-[#8A94A3]">
                      <Search size={12} />
                      从顶部搜索框定位中心资产
                    </div>
                  </div>
                </div>
              ) : loadError && !graph ? (
                <div className="flex h-full min-h-[560px] items-center justify-center px-6">
                  <div className="rounded-[18px] border border-[#F1D4DA] bg-white p-8 shadow-[0_18px_45px_-36px_rgba(254,44,85,.45)]">
                    <Empty description={loadError}>
                      <Button type="primary" onClick={refresh}>重新加载</Button>
                    </Empty>
                  </div>
                </div>
              ) : (
                <Spin spinning={loading && !graph} wrapperClassName="lineage-graph-spinner">
                  <div className="h-full min-h-[560px] w-full">
                    <ReactFlow
                      key={graphKey}
                      nodes={flowNodes}
                      edges={flowEdges}
                      nodeTypes={nodeTypes}
                      fitView
                      fitViewOptions={{ padding: 0.24, minZoom: 0.5, maxZoom: 1.05 }}
                      minZoom={0.22}
                      maxZoom={1.7}
                      nodesDraggable={false}
                      nodesConnectable={false}
                      elementsSelectable
                      proOptions={{ hideAttribution: true }}
                      onNodeClick={(_, node) => {
                        setSelectedAsset(node.data.asset);
                        setSelectedRelation(undefined);
                      }}
                      onNodeDoubleClick={(_, node) => selectRoot(node.data.asset)}
                      onEdgeClick={(_, edge) => {
                        const relation = view?.relations.find((item) => item.id === edge.id);
                        if (!relation) return;
                        setSelectedRelation(relation);
                        setSelectedAsset(undefined);
                      }}
                      onPaneClick={() => {
                        if (graph) setSelectedAsset(graph.root);
                        setSelectedRelation(undefined);
                      }}
                    >
                      <Background gap={22} size={1} color="#D9E1EC" />
                      <Controls showInteractive={false} position="bottom-left" />
                      <MiniMap
                        position="bottom-right"
                        pannable
                        zoomable
                        nodeColor={(node) => {
                          const asset = node.data?.asset as LineageAsset | undefined;
                          return asset
                            ? lineageAssetVisual[asset.assetType].accent
                            : '#94A3B8';
                        }}
                        nodeStrokeColor="#FFFFFF"
                        nodeStrokeWidth={4}
                        maskColor="rgba(238, 242, 247, 0.72)"
                      />
                    </ReactFlow>
                  </div>
                </Spin>
              )}

              {loading && graph ? (
                <div className="pointer-events-none absolute bottom-4 left-1/2 z-20 flex -translate-x-1/2 items-center gap-2 rounded-full border border-[#DDE3EC] bg-white/95 px-3 py-1.5 text-[11px] text-[#667085] shadow-[0_10px_30px_-20px_rgba(15,23,42,.5)] backdrop-blur-sm">
                  <Spin size="small" />
                  正在更新血缘关系
                </div>
              ) : null}
            </main>

            <aside
              className="w-[360px] shrink-0 overflow-y-auto border-l border-[#E3E8EF]"
              style={{
                background: 'linear-gradient(180deg, #FFFFFF 0%, #FBFCFE 100%)',
              }}
            >
              {selectedRelation ? (
                <div>
                  <div className="sticky top-0 z-10 border-b border-[#EDF0F4] bg-white/95 px-4 py-4 backdrop-blur-md">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2.5">
                        <div
                          className="flex h-9 w-9 items-center justify-center rounded-[10px] text-white"
                          style={{ background: selectedRelationColor }}
                        >
                          <GitBranch size={16} />
                        </div>
                        <div>
                          <div className="text-[14px] font-bold text-[#182230]">关系详情</div>
                          <div className="mt-0.5 text-[10px] uppercase tracking-[0.08em] text-[#98A2B3]">
                            Evidence / Provenance
                          </div>
                        </div>
                      </div>
                      <Button
                        type="text"
                        size="small"
                        icon={<X size={14} />}
                        onClick={() => {
                          setSelectedRelation(undefined);
                          if (graph) setSelectedAsset(graph.root);
                        }}
                      />
                    </div>
                  </div>

                  <div className="p-4">
                    <div
                      className="mb-4 rounded-[14px] border p-3.5"
                      style={{
                        borderColor: `${selectedRelationColor}35`,
                        background: `${selectedRelationColor}0D`,
                      }}
                    >
                      <div className="truncate text-[12px] font-semibold text-[#344054]">
                        {selectedRelationSource?.name || selectedRelation.sourceAssetId}
                      </div>
                      <div className="my-2 flex items-center gap-1.5 text-[11px] font-semibold" style={{ color: selectedRelationColor }}>
                        <span className="h-1.5 w-1.5 rounded-full" style={{ background: selectedRelationColor }} />
                        <span>{relationTypeLabel[selectedRelation.relationType]}</span>
                        <ChevronRight size={11} />
                      </div>
                      <div className="truncate text-[12px] font-semibold text-[#344054]">
                        {selectedRelationTarget?.name || selectedRelation.targetAssetId}
                      </div>
                    </div>

                    <SectionTitle>关系信息</SectionTitle>
                    <div className="rounded-[12px] border border-[#E8ECF2] bg-white px-3">
                      <DetailRow label="关系类型" value={relationTypeLabel[selectedRelation.relationType]} />
                      <DetailRow label="证据来源" value={selectedRelation.sourceType} />
                      <DetailRow label="来源 ID" value={selectedRelation.sourceId} />
                      <DetailRow label="版本" value={selectedRelation.version} />
                      <DetailRow label="可信度" value={selectedRelation.confidence} />
                      <DetailRow label="观测时间" value={formatTime(selectedRelation.observedAt)} />
                    </div>

                    {selectedRelation.expression ? (
                      <div className="mt-5">
                        <SectionTitle>表达式 / SQL</SectionTitle>
                        <pre className="max-h-[220px] overflow-auto whitespace-pre-wrap break-words rounded-[12px] border border-[#E8ECF2] bg-[#F7F9FC] p-3 text-[11px] leading-5 text-[#475467]">
                          {selectedRelation.expression}
                        </pre>
                      </div>
                    ) : null}

                    {relationPropertyEntries(selectedRelation).length ? (
                      <div className="mt-5">
                        <SectionTitle>关系属性</SectionTitle>
                        <div className="rounded-[12px] border border-[#E8ECF2] bg-white px-3">
                          {relationPropertyEntries(selectedRelation).map(([key, value]) => (
                            <DetailRow key={key} label={key} value={value} />
                          ))}
                        </div>
                      </div>
                    ) : null}
                  </div>
                </div>
              ) : selectedAsset ? (
                <div>
                  <div
                    className="sticky top-0 z-10 border-b bg-white/95 px-4 py-4 backdrop-blur-md"
                    style={{ borderColor: selectedAssetVisual?.border || '#EDF0F4' }}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="mb-2 flex items-center gap-2">
                          <AssetTypePill type={selectedAsset.assetType} />
                          {selectedAsset.id === graph?.root.id ? (
                            <span
                              className="rounded-full px-2 py-1 text-[10px] font-semibold"
                              style={{ background: BRAND_COLOR_SOFT, color: BRAND_COLOR }}
                            >
                              当前中心
                            </span>
                          ) : null}
                        </div>
                        <div
                          className="truncate text-[16px] font-bold text-[#182230]"
                          title={selectedAsset.name}
                        >
                          {selectedAsset.name}
                        </div>
                        <div className="mt-1 truncate font-mono text-[10px] text-[#98A2B3]">
                          {selectedAsset.assetKey}
                        </div>
                      </div>

                      {selectedAsset.id !== graph?.root.id ? (
                        <Tooltip title="以此资产重新加载上下游">
                          <Button
                            size="small"
                            icon={<LocateFixed size={13} />}
                            onClick={() => selectRoot(selectedAsset)}
                          >
                            设为中心
                          </Button>
                        </Tooltip>
                      ) : null}
                    </div>

                    {selectedBusinessLink ? (
                      <Button
                        className="mt-3 w-full"
                        size="small"
                        icon={<ArrowUpRight size={13} />}
                        onClick={() => history.push(selectedBusinessLink.path)}
                        style={selectedAssetVisual ? {
                          color: selectedAssetVisual.accent,
                          borderColor: selectedAssetVisual.border,
                          background: selectedAssetVisual.soft,
                        } : undefined}
                      >
                        {selectedBusinessLink.label}
                      </Button>
                    ) : null}
                  </div>

                  <div className="p-4">
                    <SectionTitle>资产信息</SectionTitle>
                    <div className="rounded-[12px] border border-[#E8ECF2] bg-white px-3">
                      <DetailRow label="assetKey" value={selectedAsset.assetKey} />
                      <DetailRow label="来源类型" value={selectedAsset.sourceType} />
                      <DetailRow label="来源 ID" value={selectedAsset.sourceId} />
                      <DetailRow label="数据源" value={selectedAsset.dataSourceId} />
                      <DetailRow label="物理位置" value={assetLocation(selectedAsset)} />
                      <DetailRow label="更新时间" value={formatTime(selectedAsset.updateTime)} />
                    </div>

                    {selectedAsset.id === graph?.root.id && impact ? (
                      <div className="mt-5">
                        <div className="mb-2 flex items-center justify-between">
                          <SectionTitle>下游影响</SectionTitle>
                          <span className="mb-2 text-[10px] text-[#98A2B3]">当前 {depth} 跳</span>
                        </div>
                        <div className="grid grid-cols-2 gap-2">
                          {[
                            { label: '全部下游', value: impact.total },
                            { label: 'SQL 任务', value: impact.byType.SQL_TASK, type: 'SQL_TASK' as LineageAssetType },
                            { label: 'Dataset', value: impact.byType.DATASET, type: 'DATASET' as LineageAssetType },
                            { label: '图表', value: impact.byType.CHART, type: 'CHART' as LineageAssetType },
                            { label: '仪表盘', value: impact.byType.DASHBOARD, type: 'DASHBOARD' as LineageAssetType },
                            { label: '数据表', value: impact.byType.TABLE, type: 'TABLE' as LineageAssetType },
                          ].map((item) => {
                            const visual = item.type ? lineageAssetVisual[item.type] : undefined;
                            return (
                              <div
                                key={item.label}
                                className="rounded-[12px] border px-3 py-2.5"
                                style={{
                                  borderColor: visual?.border || '#E3E8EF',
                                  background: visual?.soft || '#F8FAFC',
                                }}
                              >
                                <div
                                  className="text-[11px] font-medium"
                                  style={{ color: visual?.accent || '#7A8493' }}
                                >
                                  {item.label}
                                </div>
                                <div className="mt-1 text-[20px] font-bold text-[#182230]">
                                  {item.value}
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    ) : null}

                    {assetPropertyEntries(selectedAsset).length ? (
                      <div className="mt-5">
                        <SectionTitle>资产属性</SectionTitle>
                        <div className="rounded-[12px] border border-[#E8ECF2] bg-white px-3">
                          {assetPropertyEntries(selectedAsset).map(([key, value]) => (
                            <DetailRow key={key} label={key} value={value} />
                          ))}
                        </div>
                      </div>
                    ) : null}
                  </div>
                </div>
              ) : (
                <div className="flex h-full min-h-[420px] items-center justify-center px-8 text-center">
                  <div className="max-w-[250px]">
                    <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-[14px] bg-[#EEF2FF] text-[#6366F1]">
                      <GitBranch size={20} />
                    </div>
                    <div className="mt-4 text-[13px] font-semibold text-[#475467]">
                      选择节点或关系
                    </div>
                    <div className="mt-1.5 text-[11px] leading-5 text-[#98A2B3]">
                      单击节点查看资产信息，单击连线查看关系证据与 SQL 表达式。
                    </div>
                  </div>
                </div>
              )}
            </aside>
          </div>
        </div>

        <style>{`
          .lineage-page .lineage-graph-spinner,
          .lineage-page .lineage-graph-spinner > .ant-spin-container { height: 100%; }

          .lineage-page .react-flow__controls {
            overflow: hidden;
            border: 1px solid #dce3ec;
            border-radius: 10px;
            background: rgba(255, 255, 255, 0.96);
            box-shadow: 0 10px 28px -20px rgba(15, 23, 42, 0.5);
          }
          .lineage-page .react-flow__controls-button {
            width: 30px;
            height: 30px;
            border-bottom-color: #edf0f4;
            background: #fff;
            color: #667085;
          }
          .lineage-page .react-flow__controls-button:hover {
            background: #f7f9fc;
            color: #344054;
          }

          .lineage-page .react-flow__minimap {
            overflow: hidden;
            border: 1px solid #dce3ec;
            border-radius: 12px;
            background: rgba(255, 255, 255, 0.95);
            box-shadow: 0 12px 30px -22px rgba(15, 23, 42, 0.55);
          }

          .lineage-page .react-flow__edge { cursor: pointer; }
          .lineage-page .react-flow__edge-path { transition: stroke-width .18s ease, opacity .18s ease; }
          .lineage-page .react-flow__edge:hover .react-flow__edge-path { opacity: 1 !important; stroke-width: 2.1px !important; }
          .lineage-page .react-flow__node { cursor: pointer; }
          .lineage-page .react-flow__node-lineage:hover > div {
            transform: translateY(-2px);
            box-shadow: 0 16px 34px -22px rgba(15, 23, 42, .5) !important;
          }
          .lineage-page .react-flow__pane { cursor: grab; }
          .lineage-page .react-flow__pane.dragging { cursor: grabbing; }

          .lineage-page .ant-segmented {
            background: #eef2f7;
          }
          .lineage-page .ant-segmented-item-selected {
            box-shadow: 0 2px 8px -6px rgba(15, 23, 42, .45);
          }
        `}</style>
      </div>
    </ConfigProvider>
  );
}
