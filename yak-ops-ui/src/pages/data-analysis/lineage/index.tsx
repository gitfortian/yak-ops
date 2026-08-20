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
  LocateFixed,
  RefreshCw,
  Search,
  X,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import ReactFlow, {
  Background,
  Controls,
  MarkerType,
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

const DEFAULT_DEPTH = 3;
const SEARCH_LIMIT = 30;
const ALL_TYPES = [...LINEAGE_ASSET_TYPES];

const nodeTypes = { lineage: LineageNode };

const formatTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
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
    return { label: '打开开发任务', path: `/data-development/task/${encodeURIComponent(asset.sourceId)}` };
  }
  if (asset.assetType === 'DASHBOARD' && asset.sourceId) {
    return { label: '打开仪表盘', path: `/dashboard/${encodeURIComponent(asset.sourceId)}` };
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

const DetailRow = ({ label, value }: { label: string; value: unknown }) => (
  <div className="grid grid-cols-[92px_minmax(0,1fr)] gap-3 py-2 text-[12px]">
    <span className="text-[#8a8f99]">{label}</span>
    <span className="min-w-0 break-all text-[#344054]">{formatValue(value)}</span>
  </div>
);

const ImpactChip = ({ label, value }: { label: string; value: number }) => (
  <span className="inline-flex h-7 items-center gap-1.5 rounded-[6px] border border-[#e5e7eb] bg-white px-2.5 text-[12px] text-[#667085]">
    <span>{label}</span>
    <strong className="font-semibold text-[#344054]">{value}</strong>
  </span>
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
    view?.relations.map((relation) => ({
      id: relation.id,
      source: relation.sourceAssetId,
      target: relation.targetAssetId,
      type: 'smoothstep',
      markerEnd: {
        type: MarkerType.ArrowClosed,
        width: 14,
        height: 14,
        color: selectedRelation?.id === relation.id ? BRAND_COLOR : '#b9bec6',
      },
      style: {
        stroke: selectedRelation?.id === relation.id ? BRAND_COLOR : '#c9cdd3',
        strokeWidth: selectedRelation?.id === relation.id ? 1.6 : 1.15,
      },
      data: { relation },
    })) || []
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
      .catch((error) => setLoadError(error instanceof Error ? error.message : '加载血缘图失败'))
      .finally(() => setLoading(false));
  };

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="lineage-page flex h-full min-h-[560px] flex-col overflow-hidden bg-white text-[#161823]">
        <header className="shrink-0 border-b border-[#e5e7eb] px-4 py-3">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex shrink-0 items-center gap-2 pr-2">
              <GitBranch size={17} className="text-[#475467]" />
              <span className="text-[15px] font-semibold">数据血缘</span>
            </div>

            <Select
              showSearch
              allowClear
              value={undefined}
              searchValue={searchKeyword}
              filterOption={false}
              loading={searching}
              placeholder={rootAsset ? `当前：${rootAsset.name}` : '搜索名称、表名或 assetKey'}
              className="w-[320px]"
              suffixIcon={<Search size={14} className="text-[#8a8f99]" />}
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
                  <div className="flex min-w-0 items-center gap-2 py-0.5">
                    <span className="shrink-0 rounded-[4px] bg-[#f3f4f6] px-1.5 py-0.5 text-[10px] text-[#667085]">
                      {assetTypeLabel[asset.assetType]}
                    </span>
                    <span className="min-w-0 flex-1 truncate text-[13px] text-[#344054]">{asset.name}</span>
                  </div>
                ),
              }))}
              notFoundContent={searching ? <Spin size="small" /> : '输入关键词定位资产'}
            />

            <Select
              value={searchType}
              className="w-[118px]"
              onChange={setSearchType}
              options={[
                { value: 'ALL', label: '全部类型' },
                ...LINEAGE_ASSET_TYPES.map((value) => ({ value, label: assetTypeLabel[value] })),
              ]}
            />

            <div className="h-5 w-px bg-[#e5e7eb]" />

            <Segmented
              size="small"
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
            <div className="mt-2 flex min-w-0 items-center gap-2 text-[12px]">
              <span className="shrink-0 text-[#8a8f99]">当前资产</span>
              <span className="truncate font-medium text-[#344054]">{rootAsset.name}</span>
              <ChevronRight size={12} className="shrink-0 text-[#c0c4ca]" />
              <span className="shrink-0 rounded-[4px] bg-[#f4f5f7] px-1.5 py-0.5 text-[10px] text-[#667085]">
                {assetTypeLabel[rootAsset.assetType]}
              </span>
              <span className="min-w-0 truncate font-mono text-[11px] text-[#98a2b3]">{rootAsset.assetKey}</span>
              <div className="ml-auto flex shrink-0 items-center gap-1.5">
                <ImpactChip label="下游" value={impact?.total || 0} />
                <ImpactChip label="Dataset" value={impact?.byType.DATASET || 0} />
                <ImpactChip label="图表" value={impact?.byType.CHART || 0} />
                <ImpactChip label="仪表盘" value={impact?.byType.DASHBOARD || 0} />
              </div>
            </div>
          ) : null}
        </header>

        <div className="flex min-h-0 flex-1 overflow-hidden">
          <main className="relative min-w-0 flex-1 bg-[#f8f9fb]">
            <div className="absolute left-3 top-3 z-10 flex items-center gap-2 rounded-[7px] border border-[#e2e5e9] bg-white px-2 py-1.5">
              <span className="text-[11px] text-[#8a8f99]">显示</span>
              <Select
                mode="multiple"
                size="small"
                value={visibleTypes}
                className="w-[310px]"
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

            {!rootAsset ? (
              <div className="flex h-full min-h-[560px] items-center justify-center">
                <Empty
                  image={<div className="mx-auto flex h-12 w-12 items-center justify-center rounded-[10px] bg-[#eef0f3] text-[#667085]"><Boxes size={22} /></div>}
                  description={(
                    <div>
                      <div className="text-[14px] font-medium text-[#344054]">选择一个资产开始查看血缘</div>
                      <div className="mt-1 text-[12px] text-[#8a8f99]">可搜索数据表、SQL 任务、Dataset、图表或仪表盘</div>
                    </div>
                  )}
                />
              </div>
            ) : loadError && !graph ? (
              <div className="flex h-full min-h-[560px] items-center justify-center">
                <Empty description={loadError}>
                  <Button onClick={refresh}>重新加载</Button>
                </Empty>
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
                    fitViewOptions={{ padding: 0.22, minZoom: 0.55, maxZoom: 1.1 }}
                    minZoom={0.25}
                    maxZoom={1.6}
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
                    <Background gap={18} size={1} color="#e3e6ea" />
                    <Controls showInteractive={false} position="bottom-left" />
                  </ReactFlow>
                </div>
              </Spin>
            )}
          </main>

          <aside className="w-[330px] shrink-0 overflow-y-auto border-l border-[#e5e7eb] bg-white">
            {selectedRelation ? (
              <div>
                <div className="sticky top-0 z-10 flex items-center justify-between border-b border-[#eef0f2] bg-white px-4 py-3">
                  <div>
                    <div className="text-[13px] font-semibold text-[#161823]">关系详情</div>
                    <div className="mt-0.5 text-[11px] text-[#8a8f99]">Evidence / Provenance</div>
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
                <div className="px-4 py-2">
                  <div className="mb-2 rounded-[7px] border border-[#e5e7eb] bg-[#fafbfc] p-3">
                    <div className="truncate text-[12px] font-medium text-[#344054]">{selectedRelationSource?.name || selectedRelation.sourceAssetId}</div>
                    <div className="my-1.5 flex items-center gap-1 text-[11px] text-[#8a8f99]">
                      <span>{relationTypeLabel[selectedRelation.relationType]}</span>
                      <ChevronRight size={11} />
                    </div>
                    <div className="truncate text-[12px] font-medium text-[#344054]">{selectedRelationTarget?.name || selectedRelation.targetAssetId}</div>
                  </div>
                  <DetailRow label="关系类型" value={relationTypeLabel[selectedRelation.relationType]} />
                  <DetailRow label="证据来源" value={selectedRelation.sourceType} />
                  <DetailRow label="来源 ID" value={selectedRelation.sourceId} />
                  <DetailRow label="版本" value={selectedRelation.version} />
                  <DetailRow label="可信度" value={selectedRelation.confidence} />
                  <DetailRow label="观测时间" value={formatTime(selectedRelation.observedAt)} />
                  {selectedRelation.expression ? (
                    <div className="mt-3 border-t border-[#eef0f2] pt-3">
                      <div className="mb-2 text-[11px] font-medium text-[#667085]">表达式 / SQL</div>
                      <pre className="max-h-[180px] overflow-auto whitespace-pre-wrap break-words rounded-[7px] bg-[#f6f7f8] p-3 text-[11px] leading-5 text-[#475467]">{selectedRelation.expression}</pre>
                    </div>
                  ) : null}
                  {relationPropertyEntries(selectedRelation).length ? (
                    <div className="mt-3 border-t border-[#eef0f2] pt-2">
                      <div className="py-1 text-[11px] font-medium text-[#667085]">关系属性</div>
                      {relationPropertyEntries(selectedRelation).map(([key, value]) => (
                        <DetailRow key={key} label={key} value={value} />
                      ))}
                    </div>
                  ) : null}
                </div>
              </div>
            ) : selectedAsset ? (
              <div>
                <div className="sticky top-0 z-10 border-b border-[#eef0f2] bg-white px-4 py-3">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="truncate text-[14px] font-semibold text-[#161823]" title={selectedAsset.name}>{selectedAsset.name}</div>
                      <div className="mt-1 flex items-center gap-1.5">
                        <span className="rounded-[4px] bg-[#f4f5f7] px-1.5 py-0.5 text-[10px] text-[#667085]">{assetTypeLabel[selectedAsset.assetType]}</span>
                        {selectedAsset.id === graph?.root.id ? (
                          <span className="rounded-[4px] px-1.5 py-0.5 text-[10px]" style={{ background: BRAND_COLOR_SOFT, color: BRAND_COLOR }}>当前中心</span>
                        ) : null}
                      </div>
                    </div>
                    <div className="flex shrink-0 gap-1">
                      {selectedAsset.id !== graph?.root.id ? (
                        <Tooltip title="以此资产重新加载上下游">
                          <Button size="small" icon={<LocateFixed size={13} />} onClick={() => selectRoot(selectedAsset)}>设为中心</Button>
                        </Tooltip>
                      ) : null}
                    </div>
                  </div>
                  {selectedBusinessLink ? (
                    <Button
                      className="mt-3 w-full"
                      size="small"
                      icon={<ArrowUpRight size={13} />}
                      onClick={() => history.push(selectedBusinessLink.path)}
                    >
                      {selectedBusinessLink.label}
                    </Button>
                  ) : null}
                </div>

                <div className="px-4 py-2">
                  <DetailRow label="assetKey" value={selectedAsset.assetKey} />
                  <DetailRow label="来源类型" value={selectedAsset.sourceType} />
                  <DetailRow label="来源 ID" value={selectedAsset.sourceId} />
                  <DetailRow label="数据源" value={selectedAsset.dataSourceId} />
                  <DetailRow label="物理位置" value={assetLocation(selectedAsset)} />
                  <DetailRow label="更新时间" value={formatTime(selectedAsset.updateTime)} />

                  {selectedAsset.id === graph?.root.id && impact ? (
                    <div className="mt-3 border-t border-[#eef0f2] pt-3">
                      <div className="mb-2 flex items-center gap-1.5 text-[11px] font-medium text-[#667085]">
                        <GitBranch size={12} /> 下游影响（当前 {depth} 跳）
                      </div>
                      <div className="grid grid-cols-2 gap-2">
                        {[
                          ['全部下游', impact.total],
                          ['SQL 任务', impact.byType.SQL_TASK],
                          ['Dataset', impact.byType.DATASET],
                          ['图表', impact.byType.CHART],
                          ['仪表盘', impact.byType.DASHBOARD],
                          ['数据表', impact.byType.TABLE],
                        ].map(([label, value]) => (
                          <div key={String(label)} className="rounded-[7px] border border-[#e8eaed] bg-[#fafbfc] px-3 py-2">
                            <div className="text-[11px] text-[#8a8f99]">{label}</div>
                            <div className="mt-0.5 text-[17px] font-semibold text-[#344054]">{value}</div>
                          </div>
                        ))}
                      </div>
                    </div>
                  ) : null}

                  {assetPropertyEntries(selectedAsset).length ? (
                    <div className="mt-3 border-t border-[#eef0f2] pt-2">
                      <div className="py-1 text-[11px] font-medium text-[#667085]">资产属性</div>
                      {assetPropertyEntries(selectedAsset).map(([key, value]) => (
                        <DetailRow key={key} label={key} value={value} />
                      ))}
                    </div>
                  ) : null}
                </div>
              </div>
            ) : (
              <div className="flex h-full min-h-[420px] items-center justify-center px-8 text-center text-[12px] text-[#8a8f99]">
                点击节点查看资产详情，点击连线查看关系证据
              </div>
            )}
          </aside>
        </div>

        <style>{`
          .lineage-page .lineage-graph-spinner,
          .lineage-page .lineage-graph-spinner > .ant-spin-container { height: 100%; }
          .lineage-page .react-flow__controls {
            border: 1px solid #e1e4e8;
            border-radius: 7px;
            box-shadow: none;
            overflow: hidden;
          }
          .lineage-page .react-flow__controls-button {
            width: 28px;
            height: 28px;
            border-bottom-color: #edf0f2;
            background: #fff;
            color: #667085;
          }
          .lineage-page .react-flow__controls-button:hover { background: #f6f7f8; }
          .lineage-page .react-flow__edge { cursor: pointer; }
          .lineage-page .react-flow__node { cursor: pointer; }
          .lineage-page .react-flow__pane { cursor: grab; }
          .lineage-page .react-flow__pane.dragging { cursor: grabbing; }
        `}</style>
      </div>
    </ConfigProvider>
  );
}
