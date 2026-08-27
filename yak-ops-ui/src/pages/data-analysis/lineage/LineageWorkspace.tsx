import { BRAND_THEME } from '@/styles/brand';
import { history } from '@umijs/max';
import {
  Button,
  ConfigProvider,
  Empty,
  Input,
  Popover,
  Select,
  Spin,
  Tooltip,
  message,
} from 'antd';
import {
  ArrowUpRight,
  Boxes,
  ChevronLeft,
  ChevronRight,
  Filter,
  GitBranch,
  LocateFixed,
  PanelRightClose,
  PanelRightOpen,
  RefreshCw,
  Search,
  X,
} from 'lucide-react';
import type { PointerEvent as ReactPointerEvent } from 'react';
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
import { lineageAssetVisual, lineageRelationColor } from './visual';
import YakButton from '@/components/YakButton';

const DEFAULT_DEPTH = 3;
const SEARCH_LIMIT = 30;
const ALL_TYPES = [...LINEAGE_ASSET_TYPES];
const SELECTED_EDGE_COLOR = '#3F73C7';

const DEFAULT_LEFT_PANEL_WIDTH = 264;
const MIN_LEFT_PANEL_WIDTH = 220;
const MAX_LEFT_PANEL_WIDTH = 480;
const LEFT_PANEL_WIDTH_STORAGE_KEY = 'yak-lineage.left-panel-width';

const clampLeftPanelWidth = (value: number) =>
  Math.min(MAX_LEFT_PANEL_WIDTH, Math.max(MIN_LEFT_PANEL_WIDTH, value));

const initialLeftPanelWidth = () => {
  if (typeof window === 'undefined') return DEFAULT_LEFT_PANEL_WIDTH;

  const stored = Number(
    window.localStorage.getItem(LEFT_PANEL_WIDTH_STORAGE_KEY),
  );

  return Number.isFinite(stored) && stored > 0
    ? clampLeftPanelWidth(stored)
    : DEFAULT_LEFT_PANEL_WIDTH;
};

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

const AssetTypeLabel = ({ type }: { type: LineageAssetType }) => {
  const visual = lineageAssetVisual[type];
  return (
    <span className="inline-flex shrink-0 items-center gap-1.5 text-[11px] text-[#667085]">
      <span className="h-1.5 w-1.5 rounded-full" style={{ background: visual.accent }} />
      {assetTypeLabel[type]}
    </span>
  );
};

const DetailRow = ({ label, value }: { label: string; value: unknown }) => (
  <div className="grid grid-cols-[88px_minmax(0,1fr)] gap-3 border-b border-[#F0F1F3] py-2.5 text-[12px] last:border-b-0">
    <span className="text-[#8A94A3]">{label}</span>
    <span className="min-w-0 break-all text-[#344054]">{formatValue(value)}</span>
  </div>
);

const SectionTitle = ({ children }: { children: string }) => (
  <div className="mb-2 text-[12px] font-semibold text-[#475467]">{children}</div>
);

const ImpactValue = ({ label, value }: { label: string; value: number }) => (
  <div className="px-3 py-2.5">
    <div className="text-[11px] text-[#8A94A3]">{label}</div>
    <div className="mt-0.5 text-[17px] font-semibold text-[#344054]">{value}</div>
  </div>
);

const LineageEmptyIllustration = () => (
  <svg
    width="360"
    height="236"
    viewBox="0 0 360 236"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    className="select-none"
  >
    <defs>
      <linearGradient id="lineage-main-card" x1="111" y1="69" x2="241" y2="170">
        <stop stopColor="#FFFFFF" />
        <stop offset="1" stopColor="#F7F9FC" />
      </linearGradient>
      <linearGradient id="lineage-blue" x1="0" y1="0" x2="1" y2="1">
        <stop stopColor="#93C5FD" />
        <stop offset="1" stopColor="#4C78C9" />
      </linearGradient>
      <linearGradient id="lineage-purple" x1="0" y1="0" x2="1" y2="1">
        <stop stopColor="#C4B5FD" />
        <stop offset="1" stopColor="#8B5CF6" />
      </linearGradient>
      <linearGradient id="lineage-pink" x1="0" y1="0" x2="1" y2="1">
        <stop stopColor="#FDA4AF" />
        <stop offset="1" stopColor="#FE2C55" />
      </linearGradient>
      <linearGradient id="lineage-green" x1="0" y1="0" x2="1" y2="1">
        <stop stopColor="#86EFAC" />
        <stop offset="1" stopColor="#22A06B" />
      </linearGradient>
      <filter
        id="lineage-shadow"
        x="40"
        y="22"
        width="280"
        height="196"
        filterUnits="userSpaceOnUse"
      >
        <feDropShadow
          dx="0"
          dy="10"
          stdDeviation="13"
          floodColor="#1F2937"
          floodOpacity="0.08"
        />
      </filter>
      <filter
        id="lineage-small-shadow"
        x="0"
        y="0"
        width="360"
        height="236"
        filterUnits="userSpaceOnUse"
      >
        <feDropShadow
          dx="0"
          dy="6"
          stdDeviation="7"
          floodColor="#1F2937"
          floodOpacity="0.07"
        />
      </filter>
    </defs>

    {/* subtle dots */}
    <circle cx="47" cy="54" r="3.5" fill="#E1EAFE" />
    <circle cx="313" cy="56" r="4" fill="#FCE0E6" />
    <circle cx="58" cy="185" r="4" fill="#E9E2FF" />
    <circle cx="303" cy="181" r="3.5" fill="#D9F2E5" />

    {/* connectors */}
    <path
      d="M105 113C87 113 83 94 73 88"
      stroke="#D8E2F2"
      strokeWidth="2"
      strokeLinecap="round"
      strokeDasharray="4 5"
    />
    <path
      d="M174 68C174 53 156 47 148 42"
      stroke="#E0D8F7"
      strokeWidth="2"
      strokeLinecap="round"
      strokeDasharray="4 5"
    />
    <path
      d="M241 105C262 105 268 88 282 85"
      stroke="#F5D5DD"
      strokeWidth="2"
      strokeLinecap="round"
      strokeDasharray="4 5"
    />
    <path
      d="M200 167C204 184 223 190 233 194"
      stroke="#D6ECDF"
      strokeWidth="2"
      strokeLinecap="round"
      strokeDasharray="4 5"
    />

    {/* connector joints */}
    <circle cx="105" cy="113" r="4" fill="#FFFFFF" stroke="#AFC4E4" strokeWidth="2" />
    <circle cx="174" cy="68" r="4" fill="#FFFFFF" stroke="#BCAAE9" strokeWidth="2" />
    <circle cx="241" cy="105" r="4" fill="#FFFFFF" stroke="#E7A7B5" strokeWidth="2" />
    <circle cx="200" cy="167" r="4" fill="#FFFFFF" stroke="#9FD1B4" strokeWidth="2" />

    {/* main table node */}
    <g filter="url(#lineage-shadow)">
      <rect x="105" y="68" width="136" height="100" rx="18" fill="url(#lineage-main-card)" />
      <rect
        x="105.75"
        y="68.75"
        width="134.5"
        height="98.5"
        rx="17.25"
        stroke="#E5EAF0"
        strokeWidth="1.5"
      />

      <rect x="121" y="84" width="54" height="7" rx="3.5" fill="#DCE3EA" />
      <rect x="183" y="84" width="38" height="7" rx="3.5" fill="#EEF1F5" />

      <rect x="121" y="105" width="100" height="1.5" rx="0.75" fill="#EDF0F4" />
      <circle cx="127" cy="118" r="4" fill="#93C5FD" />
      <rect x="138" y="114.5" width="55" height="7" rx="3.5" fill="#E3E8EE" />
      <rect x="199" y="114.5" width="22" height="7" rx="3.5" fill="#F1F3F6" />

      <circle cx="127" cy="138" r="4" fill="#C4B5FD" />
      <rect x="138" y="134.5" width="42" height="7" rx="3.5" fill="#E3E8EE" />
      <rect x="186" y="134.5" width="35" height="7" rx="3.5" fill="#F1F3F6" />

      <circle cx="127" cy="154" r="4" fill="#86EFAC" />
      <rect x="138" y="150.5" width="67" height="7" rx="3.5" fill="#E3E8EE" />
    </g>

    {/* source database node */}
    <g filter="url(#lineage-small-shadow)">
      <rect x="39" y="62" width="54" height="54" rx="15" fill="#FFFFFF" />
      <rect x="39.75" y="62.75" width="52.5" height="52.5" rx="14.25" stroke="#E6EBF1" strokeWidth="1.5" />
      <ellipse cx="66" cy="77" rx="13" ry="5.5" fill="#DCEBFF" stroke="#8CB5EE" strokeWidth="1.4" />
      <path d="M53 77V94C53 97 59 99.5 66 99.5C73 99.5 79 97 79 94V77" stroke="#7EA9E5" strokeWidth="1.5" />
      <path d="M53 85C53 88 59 90.5 66 90.5C73 90.5 79 88 79 85" stroke="#A8C5EC" strokeWidth="1.3" />
    </g>

    {/* SQL task node */}
    <g filter="url(#lineage-small-shadow)">
      <rect x="124" y="19" width="50" height="42" rx="14" fill="#FFFFFF" />
      <rect x="124.75" y="19.75" width="48.5" height="40.5" rx="13.25" stroke="#E6EBF1" strokeWidth="1.5" />
      <path d="M144 31L138 40L144 49" stroke="url(#lineage-purple)" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M154 31L160 40L154 49" stroke="url(#lineage-purple)" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="149" cy="40" r="2.7" fill="#A78BFA" />
    </g>

    {/* chart node */}
    <g filter="url(#lineage-small-shadow)">
      <rect x="270" y="57" width="54" height="54" rx="15" fill="#FFFFFF" />
      <rect x="270.75" y="57.75" width="52.5" height="52.5" rx="14.25" stroke="#E6EBF1" strokeWidth="1.5" />
      <rect x="283" y="87" width="6" height="10" rx="3" fill="#FFD3DC" />
      <rect x="293" y="79" width="6" height="18" rx="3" fill="#FF9DB0" />
      <rect x="303" y="70" width="6" height="27" rx="3" fill="url(#lineage-pink)" />
    </g>

    {/* dashboard node */}
    <g filter="url(#lineage-small-shadow)">
      <rect x="214" y="181" width="58" height="42" rx="14" fill="#FFFFFF" />
      <rect x="214.75" y="181.75" width="56.5" height="40.5" rx="13.25" stroke="#E6EBF1" strokeWidth="1.5" />
      <rect x="226" y="192" width="14" height="8" rx="3" fill="#DDF4E7" />
      <rect x="245" y="192" width="14" height="8" rx="3" fill="#C9EAD8" />
      <rect x="226" y="204" width="14" height="7" rx="3" fill="#C9EAD8" />
      <rect x="245" y="204" width="14" height="7" rx="3" fill="url(#lineage-green)" opacity="0.78" />
    </g>
  </svg>
);

const LineageEmptyState = () => (
  <div className="flex h-full min-h-[560px] items-center justify-center px-8">
    <div className="flex max-w-[420px] flex-col items-center text-center">
      <LineageEmptyIllustration />
      <div className="-mt-1 text-[15px] font-semibold leading-6 text-[#30333B]">
        选择一个资产
      </div>
      <div className="mt-1.5 text-[12px] leading-5 text-[#8A94A3]">
        搜索后点击左侧结果，查看它的上下游关系。
      </div>
    </div>
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
  const [hasSearched, setHasSearched] = useState(false);
  const [leftPanelOpen, setLeftPanelOpen] = useState(true);
  const [leftPanelWidth, setLeftPanelWidth] = useState(initialLeftPanelWidth);
  const [rightPanelOpen, setRightPanelOpen] = useState(true);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [viewFilterDraft, setViewFilterDraft] = useState<{
    direction: LineageDirection;
    depth: number;
    visibleTypes: LineageAssetType[];
  }>({
    direction: 'BOTH',
    depth: DEFAULT_DEPTH,
    visibleTypes: [...ALL_TYPES],
  });

  const selectRoot = useCallback((asset: LineageAsset, syncUrl = true) => {
    setRootAsset(asset);
    setSelectedAsset(asset);
    setSelectedRelation(undefined);
    setRightPanelOpen(true);
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

  const runAssetSearch = useCallback(async () => {
    const keyword = searchKeyword.trim();
    if (!keyword && searchType === 'ALL') {
      setSearchResults([]);
      setHasSearched(false);
      return;
    }

    setSearching(true);
    setHasSearched(true);
    try {
      const values = await searchLineageAssets({
        keyword,
        assetType: searchType === 'ALL' ? undefined : searchType,
        limit: SEARCH_LIMIT,
      });
      setSearchResults(values);
    } catch {
      setSearchResults([]);
    } finally {
      setSearching(false);
    }
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
      const color = selected
        ? SELECTED_EDGE_COLOR
        : lineageRelationColor[relation.relationType];
      return {
        id: relation.id,
        source: relation.sourceAssetId,
        target: relation.targetAssetId,
        type: 'smoothstep',
        markerEnd: {
          type: MarkerType.ArrowClosed,
          width: selected ? 16 : 14,
          height: selected ? 16 : 14,
          color,
        },
        style: {
          stroke: color,
          strokeWidth: selected ? 2 : 1.25,
          opacity: selected ? 1 : 0.9,
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
  const graphKey = rootAsset
    ? `${rootAsset.id}:${depth}:${direction}:${visibleTypes.join(',')}`
    : 'empty';

  const advancedFilterCount =
    Number(direction !== 'BOTH') +
    Number(depth !== DEFAULT_DEPTH) +
    Number(visibleTypes.length !== ALL_TYPES.length);

  const handleAdvancedOpenChange = (open: boolean) => {
    setAdvancedOpen(open);
    if (open) {
      setViewFilterDraft({
        direction,
        depth,
        visibleTypes: [...visibleTypes],
      });
    }
  };

  const applyViewFilters = () => {
    setDirection(viewFilterDraft.direction);
    setDepth(viewFilterDraft.depth);
    setVisibleTypes([...viewFilterDraft.visibleTypes]);
    setAdvancedOpen(false);
  };

  const resetViewFilters = () => {
    const nextTypes = [...ALL_TYPES];
    setViewFilterDraft({
      direction: 'BOTH',
      depth: DEFAULT_DEPTH,
      visibleTypes: nextTypes,
    });
    setDirection('BOTH');
    setDepth(DEFAULT_DEPTH);
    setVisibleTypes(nextTypes);
  };

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

  const handleLeftPanelResizeStart = useCallback(
    (event: ReactPointerEvent) => {
      if (!leftPanelOpen) return;

      event.preventDefault();

      const startX = event.clientX;
      const startWidth = leftPanelWidth;
      const previousCursor = document.body.style.cursor;
      const previousUserSelect = document.body.style.userSelect;

      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';

      const handlePointerMove = (moveEvent: PointerEvent) => {
        setLeftPanelWidth(
          clampLeftPanelWidth(startWidth + moveEvent.clientX - startX),
        );
      };

      const finish = (upEvent: PointerEvent) => {
        const width = clampLeftPanelWidth(
          startWidth + upEvent.clientX - startX,
        );

        setLeftPanelWidth(width);
        window.localStorage.setItem(
          LEFT_PANEL_WIDTH_STORAGE_KEY,
          String(width),
        );

        document.body.style.cursor = previousCursor;
        document.body.style.userSelect = previousUserSelect;

        window.removeEventListener('pointermove', handlePointerMove);
        window.removeEventListener('pointerup', finish);
        window.removeEventListener('pointercancel', finish);
      };

      window.addEventListener('pointermove', handlePointerMove);
      window.addEventListener('pointerup', finish);
      window.addEventListener('pointercancel', finish);
    },
    [leftPanelOpen, leftPanelWidth],
  );

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="lineage-page flex h-[calc(100vh-64px)] min-h-[640px] flex-col overflow-hidden bg-white text-[#161823]">
        <header className="flex h-[58px] shrink-0 items-center gap-4 border-b border-[#E5E7EB] bg-white px-4">
          <div className="flex shrink-0 items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-[9px] bg-[#F3F6FC] text-[#4C78C9]">
              <GitBranch size={16} />
            </div>
            <div className="text-[16px] font-semibold leading-5 text-[#161823]">数据血缘</div>
          </div>

          <div className="ml-auto flex min-w-0 items-center gap-2 overflow-x-auto">
            <Input
              allowClear
              variant="filled"
              value={searchKeyword}
              prefix={<Search size={14} className="text-[#98A2B3]" />}
              placeholder="搜索资产"
              className="!h-9 !w-[250px] !min-w-[210px]"
              onChange={(event) => {
                setSearchKeyword(event.target.value);
                setHasSearched(false);
              }}
              onPressEnter={() => void runAssetSearch()}
            />

            <Select
              variant="filled"
              value={searchType}
              className="!h-9 !w-[132px] !min-w-[126px]"
              onChange={(value) => {
                setSearchType(value);
                setHasSearched(false);
              }}
              options={[
                { value: 'ALL', label: '全部类型' },
                ...LINEAGE_ASSET_TYPES.map((value) => ({
                  value,
                  label: assetTypeLabel[value],
                })),
              ]}
            />

            <Button
              size="small"
              className="!h-9 !px-4"
              onClick={() => void runAssetSearch()}
            >
              查询
            </Button>

            <Popover
              trigger="click"
              placement="bottomRight"
              open={advancedOpen}
              onOpenChange={handleAdvancedOpenChange}
              content={
                <div className="w-[400px]">
                  <div className="mb-4 text-[14px] font-semibold text-[#101828]">视图筛选</div>

                  <div className="grid grid-cols-2 gap-x-3 gap-y-4">
                    <div>
                      <div className="mb-1.5 text-[12px] text-[#667085]">方向</div>
                      <Select
                        variant="filled"
                        value={viewFilterDraft.direction}
                        className="w-full"
                        onChange={(value) =>
                          setViewFilterDraft((previous) => ({
                            ...previous,
                            direction: value as LineageDirection,
                          }))
                        }
                        options={[
                          { label: '上下游', value: 'BOTH' },
                          { label: '仅上游', value: 'UPSTREAM' },
                          { label: '仅下游', value: 'DOWNSTREAM' },
                        ]}
                      />
                    </div>

                    <div>
                      <div className="mb-1.5 text-[12px] text-[#667085]">深度</div>
                      <Select
                        variant="filled"
                        value={viewFilterDraft.depth}
                        className="w-full"
                        onChange={(value) =>
                          setViewFilterDraft((previous) => ({
                            ...previous,
                            depth: value,
                          }))
                        }
                        options={[1, 2, 3, 4, 5].map((value) => ({
                          value,
                          label: `${value} 跳`,
                        }))}
                      />
                    </div>

                    <div className="col-span-2">
                      <div className="mb-1.5 text-[12px] text-[#667085]">资产类型</div>
                      <Select
                        mode="multiple"
                        variant="filled"
                        value={viewFilterDraft.visibleTypes}
                        className="w-full"
                        maxTagCount={3}
                        maxTagPlaceholder={(omitted) => `+ ${omitted.length}`}
                        placeholder="选择资产类型"
                        onChange={(values) =>
                          setViewFilterDraft((previous) => ({
                            ...previous,
                            visibleTypes: values as LineageAssetType[],
                          }))
                        }
                        options={LINEAGE_ASSET_TYPES.map((value) => ({
                          value,
                          label: assetTypeLabel[value],
                        }))}
                      />
                    </div>
                  </div>

                  <div className="mt-5 flex items-center justify-end gap-2 border-t border-[#F0F0F0] pt-4">
                    <YakButton size="small" className="!h-8" onClick={resetViewFilters}>
                      重置
                    </YakButton>
                    <YakButton
                      danger
                      type="primary"
                      size="small"
                      className="!h-8"
                      onClick={applyViewFilters}
                    >
                      应用
                    </YakButton>
                  </div>
                </div>
              }
            >
              <Button
                size="small"
                icon={<Filter size={13} />}
                className={[
                  '!h-9 !px-3',
                  advancedFilterCount > 0
                    ? '!border-[#FFCCC7] !bg-[#FFF1F0] !text-[#FF4D4F]'
                    : '',
                ].join(' ')}
              >
                筛选
                {advancedFilterCount > 0 ? (
                  <span className="ml-1.5 inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-[#FF4D4F] px-1 text-[10px] leading-[18px] text-white">
                    {advancedFilterCount}
                  </span>
                ) : null}
              </Button>
            </Popover>

            <Tooltip title="刷新">
              <Button
                aria-label="刷新血缘"
                size="small"
                className="!h-9 !w-9 !px-0"
                icon={<RefreshCw size={14} />}
                loading={loading}
                disabled={!rootAsset}
                onClick={refresh}
              />
            </Tooltip>
          </div>
        </header>

        <div className="flex min-h-0 flex-1 overflow-hidden bg-white">
          <aside
            className="group relative shrink-0 overflow-hidden bg-white transition-[width] duration-200 ease-out"
            style={{ width: leftPanelOpen ? leftPanelWidth : 0 }}
          >
            <div
              className="flex h-full flex-col overflow-hidden"
              style={{ width: leftPanelWidth }}
            >
              <div className="flex h-11 shrink-0 items-center justify-between border-b border-[#F0F1F3] px-3">
                <div className="flex min-w-0 items-center gap-2">
                  <span className="text-[13px] font-semibold text-[#344054]">资产</span>
                  {hasSearched && !searching ? (
                    <span className="text-[10px] text-[#98A2B3]">{searchResults.length} 项</span>
                  ) : null}
                </div>
              </div>

              {rootAsset ? (
                <div className="shrink-0 border-b border-[#F0F1F3] px-3 py-3">
                  <div className="flex items-center gap-2">
                    <span className="inline-flex h-5 shrink-0 items-center rounded-full bg-[#F1F5FB] px-2 text-[10px] font-medium text-[#4C78C9]">
                      中心
                    </span>
                    <span
                      className="min-w-0 flex-1 truncate text-[12px] font-semibold text-[#344054]"
                      title={rootAsset.name}
                    >
                      {rootAsset.name}
                    </span>
                  </div>

                  <div className="mt-2">
                    <AssetTypeLabel type={rootAsset.assetType} />
                  </div>
                </div>
              ) : null}

              <div className="min-h-0 flex-1 overflow-y-auto p-2">
                {searching ? (
                  <div className="flex h-32 items-center justify-center">
                    <Spin size="small" />
                  </div>
                ) : searchResults.length ? (
                  <div className="space-y-1">
                    {searchResults.map((asset) => {
                      const active = rootAsset?.id === asset.id;

                      return (
                        <button
                          key={asset.id}
                          type="button"
                          onClick={() => selectRoot(asset)}
                          className={[
                            'w-full rounded-md border px-2.5 py-2.5 text-left transition-colors',
                            active
                              ? 'border-[#D9DCE3] bg-[#F8F9FB]'
                              : 'border-transparent hover:border-[#EAECF0] hover:bg-[#FAFAFB]',
                          ].join(' ')}
                        >
                          <div className="flex min-w-0 items-center gap-2">
                            <AssetTypeLabel type={asset.assetType} />
                            <span className="min-w-0 flex-1 truncate text-[12px] font-medium text-[#344054]">
                              {asset.name}
                            </span>
                          </div>

                          <div
                            className="mt-1 truncate text-[10px] text-[#98A2B3]"
                            title={assetLocation(asset)}
                          >
                            {assetLocation(asset)}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                ) : hasSearched ? (
                  <div className="flex h-40 flex-col items-center justify-center px-4 text-center">
                    <Boxes size={18} className="text-[#B7BEC8]" />
                    <div className="mt-2 text-[12px] font-medium text-[#667085]">
                      未找到资产
                    </div>
                    <div className="mt-1 text-[11px] text-[#A3AAB5]">
                      换个关键词试试
                    </div>
                  </div>
                ) : (
                  <div className="flex h-40 flex-col items-center justify-center px-4 text-center">
                    <Search size={18} className="text-[#B7BEC8]" />
                    <div className="mt-2 text-[12px] font-medium text-[#667085]">
                      搜索资产
                    </div>
                  </div>
                )}
              </div>
            </div>
          </aside>

          <div
            role="separator"
            aria-label="调整资产面板宽度"
            aria-orientation="vertical"
            onPointerDown={leftPanelOpen ? handleLeftPanelResizeStart : undefined}
            className={[
              'group relative z-20 w-3 shrink-0 self-stretch touch-none',
              leftPanelOpen ? 'cursor-col-resize' : 'cursor-default',
            ].join(' ')}
          >
            <div
              className={[
                'pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-[#dfe3e8]',
                'transition-[width,background-color] duration-150',
                leftPanelOpen
                  ? 'group-hover:w-[2px] group-hover:bg-[rgba(254,44,85,.55)] group-active:bg-[rgba(254,44,85,1)]'
                  : '',
              ].join(' ')}
            />

            <button
              type="button"
              aria-label={leftPanelOpen ? '收起资产面板' : '展开资产面板'}
              onPointerDown={(event) => event.stopPropagation()}
              onClick={() => setLeftPanelOpen((value) => !value)}
              className={[
                'absolute left-1/2 top-1/2 z-20 flex h-8 w-4 -translate-x-1/2 -translate-y-1/2',
                'items-center justify-center rounded-[3px] border border-[#dfe3e8] bg-white text-[#667085]',
                'shadow-[0_1px_2px_rgba(16,24,40,0.05)] transition-[color,border-color,box-shadow] duration-150',
                'hover:border-[#cfd4dc] hover:text-[#161823] focus:outline-none focus-visible:ring-2',
                'focus-visible:ring-[rgba(254,44,85,.16)]',
              ].join(' ')}
            >
              {leftPanelOpen ? (
                <ChevronLeft size={12} />
              ) : (
                <ChevronRight size={12} />
              )}
            </button>
          </div>

          <main className="relative min-w-0 flex-1 bg-white">
            {!rootAsset ? (
              <LineageEmptyState />
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
                    fitViewOptions={{ padding: 0.22, minZoom: 0.5, maxZoom: 1.08 }}
                    minZoom={0.22}
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
                    <Background gap={20} size={1} color="#E5E9EF" />
                    <Controls showInteractive={false} position="bottom-left" />
                  </ReactFlow>
                </div>
              </Spin>
            )}

            {loading && graph ? (
              <div className="pointer-events-none absolute right-3 top-3 z-20 flex items-center gap-2 border border-[#E4E7EC] bg-white px-2.5 py-1.5 text-[11px] text-[#667085]">
                <Spin size="small" />
                更新
              </div>
            ) : null}
          </main>

          {rootAsset ? (
            rightPanelOpen ? (
              <div className="relative w-[340px] shrink-0 border-l border-[#E5E7EB] bg-white transition-[width] duration-200">
                <Tooltip title="收起详情" placement="left">
                  <Button
                    type="default"
                    size="small"
                    className="absolute -left-3 top-3 z-30 !h-7 !w-7 !min-w-0 !bg-white !p-0 shadow-sm"
                    icon={<PanelRightClose size={13} />}
                    onClick={() => setRightPanelOpen(false)}
                  />
                </Tooltip>
                <aside className="h-full overflow-y-auto bg-white">
              {selectedRelation ? (
                <div>
                  <div className="sticky top-0 z-10 flex items-center justify-between border-b border-[#E5E7EB] bg-white px-4 py-3">
                    <div>
                      <div className="text-[13px] font-semibold text-[#344054]">关系详情</div>
                      <div className="mt-0.5 text-[11px] text-[#98A2B3]">
                        {relationTypeLabel[selectedRelation.relationType]}
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

                  <div className="px-4 py-4">
                    <div className="border border-[#E4E7EC] bg-[#FAFAFB] p-3">
                      <div className="truncate text-[12px] font-medium text-[#344054]">
                        {selectedRelationSource?.name || selectedRelation.sourceAssetId}
                      </div>
                      <div className="my-2 flex items-center gap-1.5 text-[11px] text-[#667085]">
                        <GitBranch size={11} className="text-[#4C78C9]" />
                        <span>{relationTypeLabel[selectedRelation.relationType]}</span>
                        <ChevronRight size={11} />
                      </div>
                      <div className="truncate text-[12px] font-medium text-[#344054]">
                        {selectedRelationTarget?.name || selectedRelation.targetAssetId}
                      </div>
                    </div>

                    <div className="mt-5">
                      <SectionTitle>关系信息</SectionTitle>
                      <div className="border-y border-[#E5E7EB]">
                        <DetailRow label="关系类型" value={relationTypeLabel[selectedRelation.relationType]} />
                        <DetailRow label="证据来源" value={selectedRelation.sourceType} />
                        <DetailRow label="来源 ID" value={selectedRelation.sourceId} />
                        <DetailRow label="版本" value={selectedRelation.version} />
                        <DetailRow label="可信度" value={selectedRelation.confidence} />
                        <DetailRow label="观测时间" value={formatTime(selectedRelation.observedAt)} />
                      </div>
                    </div>

                    {selectedRelation.expression ? (
                      <div className="mt-5">
                        <SectionTitle>表达式 / SQL</SectionTitle>
                        <pre className="max-h-[220px] overflow-auto whitespace-pre-wrap break-words border border-[#E4E7EC] bg-[#F8F9FB] p-3 text-[11px] leading-5 text-[#475467]">
                          {selectedRelation.expression}
                        </pre>
                      </div>
                    ) : null}

                    {relationPropertyEntries(selectedRelation).length ? (
                      <div className="mt-5">
                        <SectionTitle>关系属性</SectionTitle>
                        <div className="border-y border-[#E5E7EB]">
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
                  <div className="sticky top-0 z-10 border-b border-[#E5E7EB] bg-white px-4 py-3">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <AssetTypeLabel type={selectedAsset.assetType} />
                          {selectedAsset.id === graph?.root.id ? (
                            <span className="text-[10px] font-medium text-[#4C78C9]">中心资产</span>
                          ) : null}
                        </div>
                        <div
                          className="mt-1.5 truncate text-[14px] font-semibold text-[#161823]"
                          title={selectedAsset.name}
                        >
                          {selectedAsset.name}
                        </div>
                        <div className="mt-0.5 truncate font-mono text-[10px] text-[#98A2B3]">
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
                        type="link"
                        className="mt-2 h-auto p-0"
                        size="small"
                        icon={<ArrowUpRight size={13} />}
                        onClick={() => history.push(selectedBusinessLink.path)}
                      >
                        {selectedBusinessLink.label}
                      </Button>
                    ) : null}
                  </div>

                  <div className="px-4 py-4">
                    <SectionTitle>资产信息</SectionTitle>
                    <div className="border-y border-[#E5E7EB]">
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
                        <div className="grid grid-cols-3 border border-[#E5E7EB] bg-white">
                          <ImpactValue label="全部下游" value={impact.total} />
                          <ImpactValue label="SQL 任务" value={impact.byType.SQL_TASK} />
                          <ImpactValue label="Dataset" value={impact.byType.DATASET} />
                          <ImpactValue label="图表" value={impact.byType.CHART} />
                          <ImpactValue label="仪表盘" value={impact.byType.DASHBOARD} />
                          <ImpactValue label="数据表" value={impact.byType.TABLE} />
                        </div>
                      </div>
                    ) : null}

                    {assetPropertyEntries(selectedAsset).length ? (
                      <div className="mt-5">
                        <SectionTitle>资产属性</SectionTitle>
                        <div className="border-y border-[#E5E7EB]">
                          {assetPropertyEntries(selectedAsset).map(([key, value]) => (
                            <DetailRow key={key} label={key} value={value} />
                          ))}
                        </div>
                      </div>
                    ) : null}
                  </div>
                </div>
              ) : (
                <div className="flex h-full min-h-[420px] items-center justify-center px-8 text-center text-[12px] text-[#8A94A3]">
                  选择节点或连线查看详情
                </div>
              )}
                </aside>
              </div>
            ) : (
              <aside className="flex w-10 shrink-0 items-start justify-center border-l border-[#E5E7EB] bg-[#FAFBFC] pt-3 transition-[width] duration-200">
                <Tooltip title="展开详情" placement="left">
                  <Button
                    type="text"
                    size="small"
                    className="!h-7 !w-7 !min-w-0 !p-0"
                    icon={<PanelRightOpen size={14} />}
                    onClick={() => setRightPanelOpen(true)}
                  />
                </Tooltip>
              </aside>
            )
          ) : null}
        </div>

        <style>{`
          .lineage-page .lineage-graph-spinner,
          .lineage-page .lineage-graph-spinner > .ant-spin-container {
            height: 100%;
          }

          .lineage-page .ant-select-selector,
          .lineage-page .ant-input-affix-wrapper,
          .lineage-page .ant-btn {
            border-radius: 6px !important;
          }

          .lineage-page .react-flow__controls {
            overflow: hidden;
            border: 1px solid #d8dde5;
            border-radius: 4px;
            box-shadow: none;
          }
          .lineage-page .react-flow__controls-button {
            width: 28px;
            height: 28px;
            border-bottom-color: #eaecf0;
            background: #fff;
            color: #667085;
          }
          .lineage-page .react-flow__controls-button:hover {
            background: #f8f9fb;
            color: #344054;
          }

          .lineage-page .react-flow__edge {
            cursor: pointer;
          }
          .lineage-page .react-flow__edge-path {
            transition: stroke-width .15s ease;
          }
          .lineage-page .react-flow__edge:hover .react-flow__edge-path {
            stroke-width: 1.8px !important;
          }
          .lineage-page .react-flow__node {
            cursor: pointer;
            box-shadow: none !important;
          }
          .lineage-page .react-flow__node-lineage:hover > div {
            border-color: #8eacd9 !important;
          }
          .lineage-page .react-flow__pane {
            cursor: grab;
          }
          .lineage-page .react-flow__pane.dragging {
            cursor: grabbing;
          }
        `}</style>
      </div>
    </ConfigProvider>
  );
}