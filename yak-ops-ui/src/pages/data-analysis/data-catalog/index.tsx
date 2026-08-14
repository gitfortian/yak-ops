import { BRAND_THEME } from '@/styles/brand';
import type { DataNode } from 'antd/es/tree';
import type { ColumnsType } from 'antd/es/table';
import {
  Button,
  ConfigProvider,
  Empty,
  Input,
  message,
  Pagination,
  Popconfirm,
  Select,
  Spin,
  Table,
  Tag,
  Tooltip,
  Tree,
} from 'antd';
import {
  BarChart3,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Database,
  Folder,
  GitBranch,
  RefreshCw,
  Rows3,
  Search,
  Sigma,
  TableProperties,
} from 'lucide-react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  fetchCatalogWorkspace,
  offlineCatalogDataset,
  onlineCatalogDataset,
  type CatalogDataset,
  type CatalogDatasetFieldRole,
  type CatalogDatasetSourceType,
  type CatalogDatasetStatus,
  type CatalogDirectory,
} from './service';

const DEFAULT_LEFT_WIDTH = 286;
const MIN_LEFT_WIDTH = 220;
const MAX_LEFT_WIDTH = 480;
const LEFT_WIDTH_STORAGE_KEY = 'yak-data-catalog.left-width';
const ROOT_KEY = 'catalog:root';
const UNGROUPED_KEY = 'catalog:ungrouped';

const sourceTypeLabel: Record<CatalogDatasetSourceType, string> = {
  QUERY_REVISION: 'SQL 查询',
  TABLE: '数据表',
  VIEW: '视图',
};

const roleLabel: Record<CatalogDatasetFieldRole, string> = {
  DIMENSION: '维度',
  MEASURE: '指标',
};

const fieldTypeLabel: Record<string, string> = {
  STRING: '文本',
  NUMBER: '数值',
  DATE: '日期',
  DATETIME: '时间',
  BOOLEAN: '布尔',
  UNKNOWN: '未知',
};

type CatalogTreeNodeKind = 'root' | 'directory' | 'dataset' | 'ungrouped';

interface CatalogTreeNode extends DataNode {
  key: string;
  title: string;
  kind: CatalogTreeNodeKind;
  datasetId?: string;
  directoryId?: string;
  datasetCount?: number;
  searchText?: string;
  children?: CatalogTreeNode[];
}

const clampLeftWidth = (value: number) =>
  Math.min(MAX_LEFT_WIDTH, Math.max(MIN_LEFT_WIDTH, value));

const initialLeftWidth = () => {
  if (typeof window === 'undefined') return DEFAULT_LEFT_WIDTH;
  const stored = Number(window.localStorage.getItem(LEFT_WIDTH_STORAGE_KEY));
  return Number.isFinite(stored) && stored > 0
    ? clampLeftWidth(stored)
    : DEFAULT_LEFT_WIDTH;
};

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
    hour12: false,
  }).format(date).replaceAll('/', '-');
};

const schemaSummary = (dataset: CatalogDataset) => {
  const dimensions = dataset.fields.filter(
    (field) => field.defaultRole === 'DIMENSION',
  ).length;
  const metrics = dataset.fields.filter(
    (field) => field.defaultRole === 'MEASURE',
  ).length;
  return { fields: dataset.fields.length, dimensions, metrics };
};

const directoryAncestors = (
  directoryId: string | undefined,
  directoryMap: Map<string, CatalogDirectory>,
) => {
  const values: CatalogDirectory[] = [];
  const visited = new Set<string>();
  let current = directoryId ? directoryMap.get(directoryId) : undefined;
  while (current && !visited.has(current.id)) {
    visited.add(current.id);
    values.unshift(current);
    current = current.parentId ? directoryMap.get(current.parentId) : undefined;
  }
  return values;
};

const buildCatalogTree = (
  datasets: CatalogDataset[],
  directories: CatalogDirectory[],
): CatalogTreeNode[] => {
  const directoryMap = new Map(
    directories.map((directory) => [directory.id, directory]),
  );
  const relevantDirectoryIds = new Set<string>();

  datasets.forEach((dataset) => {
    directoryAncestors(dataset.directoryId, directoryMap).forEach((directory) => {
      relevantDirectoryIds.add(directory.id);
    });
  });

  const datasetNode = (dataset: CatalogDataset): CatalogTreeNode => ({
    key: `dataset:${dataset.id}`,
    title: dataset.name,
    kind: 'dataset',
    datasetId: dataset.id,
    isLeaf: true,
    searchText: [
      dataset.name,
      dataset.description,
      dataset.sourceTaskName || '',
      dataset.directoryPath || '',
      ...dataset.fields.flatMap((field) => [field.displayName, field.physicalName]),
    ].join(' '),
  });

  const buildDirectory = (directory: CatalogDirectory): CatalogTreeNode => {
    const childDirectories = directories
      .filter(
        (candidate) =>
          candidate.parentId === directory.id
          && relevantDirectoryIds.has(candidate.id),
      )
      .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
      .map(buildDirectory);
    const directDatasets = datasets
      .filter((dataset) => dataset.directoryId === directory.id)
      .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
      .map(datasetNode);
    const children = [...childDirectories, ...directDatasets];
    const datasetCount = children.reduce(
      (total, child) =>
        total + (child.kind === 'dataset' ? 1 : child.datasetCount || 0),
      0,
    );
    return {
      key: `directory:${directory.id}`,
      title: directory.name,
      kind: 'directory',
      directoryId: directory.id,
      datasetCount,
      searchText: `${directory.name} ${directory.path}`,
      children,
    };
  };

  const topDirectories = directories
    .filter(
      (directory) => !directory.parentId && relevantDirectoryIds.has(directory.id),
    )
    .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
    .map(buildDirectory);
  const rootDatasets = datasets
    .filter((dataset) => dataset.sourceNodeId && !dataset.directoryId)
    .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
    .map(datasetNode);
  const unmappedDatasets = datasets
    .filter((dataset) => !dataset.sourceNodeId)
    .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
    .map(datasetNode);

  const children: CatalogTreeNode[] = [...topDirectories, ...rootDatasets];
  if (unmappedDatasets.length) {
    children.push({
      key: UNGROUPED_KEY,
      title: '未分组',
      kind: 'ungrouped',
      datasetCount: unmappedDatasets.length,
      searchText: '未分组',
      children: unmappedDatasets,
    });
  }

  return [
    {
      key: ROOT_KEY,
      title: '全部数据集',
      kind: 'root',
      datasetCount: datasets.length,
      searchText: '全部数据集',
      children,
    },
  ];
};

const filterTree = (
  nodes: CatalogTreeNode[],
  keyword: string,
): CatalogTreeNode[] => {
  const normalized = keyword.trim().toLowerCase();
  if (!normalized) return nodes;

  return nodes.flatMap((node) => {
    const selfMatches = `${node.title} ${node.searchText || ''}`
      .toLowerCase()
      .includes(normalized);
    if (selfMatches) return [node];
    const children = node.children ? filterTree(node.children, normalized) : [];
    return children.length ? [{ ...node, children }] : [];
  });
};

const flattenTree = (nodes: CatalogTreeNode[]) => {
  const map = new Map<string, CatalogTreeNode>();
  const visit = (values: CatalogTreeNode[]) =>
    values.forEach((value) => {
      map.set(String(value.key), value);
      if (value.children) visit(value.children);
    });
  visit(nodes);
  return map;
};

const DataCatalogPage = () => {
  const [datasets, setDatasets] = useState<CatalogDataset[]>([]);
  const [directories, setDirectories] = useState<CatalogDirectory[]>([]);
  const [selectedKey, setSelectedKey] = useState(ROOT_KEY);
  const [treeKeyword, setTreeKeyword] = useState('');
  const [listKeyword, setListKeyword] = useState('');
  const [status, setStatus] = useState<'ALL' | CatalogDatasetStatus>('ALL');
  const [sourceType, setSourceType] = useState<
    'ALL' | CatalogDatasetSourceType
  >('ALL');
  const [detailTab, setDetailTab] = useState<
    'fields' | 'versions' | 'overview'
  >('fields');
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [statusUpdatingId, setStatusUpdatingId] = useState('');
  const [leftWidth, setLeftWidth] = useState(initialLeftWidth);
  const [leftCollapsed, setLeftCollapsed] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const workspace = await fetchCatalogWorkspace();
      setDatasets(workspace.datasets);
      setDirectories(workspace.directories);
      setSelectedKey((value) =>
        value.startsWith('dataset:')
          && !workspace.datasets.some((item) => `dataset:${item.id}` === value)
          ? ROOT_KEY
          : value,
      );
    } catch (error) {
      const text = error instanceof Error ? error.message : '加载数据目录失败';
      setLoadError(text);
      setDatasets([]);
      setDirectories([]);
      setSelectedKey(ROOT_KEY);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const treeData = useMemo(
    () => buildCatalogTree(datasets, directories),
    [datasets, directories],
  );
  const visibleTreeData = useMemo(
    () => filterTree(treeData, treeKeyword),
    [treeData, treeKeyword],
  );
  const treeNodeMap = useMemo(() => flattenTree(treeData), [treeData]);
  const selectedNode = treeNodeMap.get(selectedKey) ?? treeNodeMap.get(ROOT_KEY);
  const selectedDataset =
    selectedNode?.kind === 'dataset'
      ? datasets.find((dataset) => dataset.id === selectedNode.datasetId)
      : undefined;

  const scopeDatasets = useMemo(() => {
    if (!selectedNode || selectedNode.kind === 'root') return datasets;
    if (selectedNode.kind === 'ungrouped') {
      return datasets.filter((dataset) => !dataset.sourceNodeId);
    }
    if (selectedNode.kind !== 'directory' || !selectedNode.directoryId) return [];

    const includedDirectoryIds = new Set<string>([selectedNode.directoryId]);
    let changed = true;
    while (changed) {
      changed = false;
      directories.forEach((directory) => {
        if (
          directory.parentId
          && includedDirectoryIds.has(directory.parentId)
          && !includedDirectoryIds.has(directory.id)
        ) {
          includedDirectoryIds.add(directory.id);
          changed = true;
        }
      });
    }
    return datasets.filter((dataset) =>
      dataset.directoryId ? includedDirectoryIds.has(dataset.directoryId) : false,
    );
  }, [datasets, directories, selectedNode]);

  const filteredDatasets = useMemo(() => {
    const normalized = listKeyword.trim().toLowerCase();
    return scopeDatasets.filter((dataset) => {
      if (status !== 'ALL' && dataset.status !== status) return false;
      if (sourceType !== 'ALL' && dataset.currentVersion?.sourceType !== sourceType) {
        return false;
      }
      if (!normalized) return true;
      return [
        dataset.name,
        dataset.description,
        dataset.sourceTaskName || '',
        dataset.directoryPath || '',
        dataset.currentVersion?.sourceTaskAssetId || '',
        ...dataset.fields.flatMap((field) => [
          field.displayName,
          field.physicalName,
          field.description || '',
        ]),
      ].some((value) => value.toLowerCase().includes(normalized));
    });
  }, [listKeyword, scopeDatasets, sourceType, status]);

  const pagedDatasets = useMemo(() => {
    const start = (current - 1) * pageSize;
    return filteredDatasets.slice(start, start + pageSize);
  }, [current, filteredDatasets, pageSize]);

  useEffect(() => {
    const lastPage = Math.max(1, Math.ceil(filteredDatasets.length / pageSize));
    if (current > lastPage) setCurrent(lastPage);
  }, [current, filteredDatasets.length, pageSize]);

  const scopeTitle =
    selectedNode?.kind === 'directory' || selectedNode?.kind === 'ungrouped'
      ? selectedNode.title
      : '全部数据集';

  const updateDatasetStatus = async (dataset: CatalogDataset) => {
    setStatusUpdatingId(dataset.id);
    try {
      if (dataset.status === 'ONLINE') {
        await offlineCatalogDataset(dataset.id);
        message.success(`Dataset「${dataset.name}」已下线`);
      } else {
        await onlineCatalogDataset(dataset.id);
        message.success(`Dataset「${dataset.name}」已上线`);
      }
      await load();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : '更新 Dataset 状态失败',
      );
    } finally {
      setStatusUpdatingId('');
    }
  };

  const selectDataset = (dataset: CatalogDataset) => {
    setSelectedKey(`dataset:${dataset.id}`);
    setDetailTab('fields');
  };

  const resetFilters = () => {
    setListKeyword('');
    setStatus('ALL');
    setSourceType('ALL');
    setCurrent(1);
  };

  const handleResizeStart = useCallback(
    (event: ReactPointerEvent) => {
      if (leftCollapsed) return;
      event.preventDefault();
      const startX = event.clientX;
      const startWidth = leftWidth;
      const previousCursor = document.body.style.cursor;
      const previousUserSelect = document.body.style.userSelect;
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';

      const handlePointerMove = (moveEvent: PointerEvent) => {
        setLeftWidth(clampLeftWidth(startWidth + moveEvent.clientX - startX));
      };
      const finish = (upEvent: PointerEvent) => {
        const width = clampLeftWidth(startWidth + upEvent.clientX - startX);
        setLeftWidth(width);
        window.localStorage.setItem(LEFT_WIDTH_STORAGE_KEY, String(width));
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
    [leftCollapsed, leftWidth],
  );

  const columns: ColumnsType<CatalogDataset> = [
    {
      title: '数据集',
      dataIndex: 'name',
      width: 290,
      render: (_, record) => (
        <div className="flex min-w-0 items-center gap-2 py-1">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[5px] bg-[#f4f5f7] text-[#667085]">
            <TableProperties size={15} />
          </div>
          <div className="min-w-0 flex-1">
            <button
              type="button"
              className="max-w-full truncate border-0 bg-transparent p-0 text-left text-[13px] font-medium text-[#344054] hover:text-[#161823] hover:underline"
              onClick={(event) => {
                event.stopPropagation();
                selectDataset(record);
              }}
            >
              {record.name}
            </button>
            <div className="mt-0.5 truncate text-[11px] text-[#98a2b3]">
              {record.description || record.sourceTaskName || '暂无描述'}
            </div>
          </div>
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: CatalogDatasetStatus) => (
        <Tag
          bordered={false}
          className={
            value === 'ONLINE'
              ? 'm-0 bg-[#f1f3f5] text-[#344054]'
              : 'm-0 bg-[#f6f6f7] text-[#98a2b3]'
          }
        >
          {value === 'ONLINE' ? '已上线' : '已下线'}
        </Tag>
      ),
    },
    {
      title: '来源',
      width: 210,
      render: (_, record) =>
        record.currentVersion ? (
          <div className="min-w-0">
            <div className="truncate text-[12px] text-[#475467]">
              {record.sourceTaskName
                || `TaskAsset #${record.currentVersion.sourceTaskAssetId}`}
            </div>
            <div className="mt-0.5 text-[11px] text-[#98a2b3]">
              {sourceTypeLabel[record.currentVersion.sourceType]} · SQL V
              {record.currentVersion.sourceTaskRevisionNo}
            </div>
          </div>
        ) : (
          <span className="text-[11px] text-[#98a2b3]">尚无当前版本</span>
        ),
    },
    {
      title: 'Schema',
      width: 145,
      render: (_, record) => {
        const summary = schemaSummary(record);
        return (
          <div className="text-[11px] text-[#667085]">
            <div>{summary.fields} 个字段</div>
            <div className="mt-0.5 text-[#98a2b3]">
              {summary.dimensions} 维度 · {summary.metrics} 指标
            </div>
          </div>
        );
      },
    },
    {
      title: '版本',
      width: 92,
      render: (_, record) => (
        <div>
          <div className="text-[12px] font-medium text-[#344054]">
            {record.currentVersion ? `DV${record.currentVersion.versionNo}` : '-'}
          </div>
          <div className="mt-0.5 text-[10px] text-[#98a2b3]">
            {record.versions.length} 个版本
          </div>
        </div>
      ),
    },
    {
      title: '消费',
      width: 105,
      render: (_, record) => (
        <div className="flex items-center gap-1 text-[11px] text-[#667085]">
          <BarChart3 size={12} /> {record.analysisCount} Analysis
        </div>
      ),
    },
    {
      title: '更新时间',
      width: 150,
      render: (_, record) => (
        <div className="flex items-center gap-1 text-[11px] text-[#667085]">
          <Clock3 size={11} /> {formatTime(record.updateTime || record.createTime)}
        </div>
      ),
    },
    {
      title: '操作',
      fixed: 'right',
      width: 170,
      render: (_, record) => (
        <div className="flex items-center gap-1">
          <Tooltip
            title={
              record.status === 'ONLINE'
                ? '使用当前 Dataset 创建分析'
                : 'Dataset 上线后才能创建 Analysis'
            }
          >
            <Button
              type="link"
              size="small"
              disabled={record.status !== 'ONLINE' || !record.currentVersion}
              href={
                record.status === 'ONLINE' && record.currentVersion
                  ? `/data-analysis/chart-analysis?datasetId=${encodeURIComponent(record.id)}`
                  : undefined
              }
              onClick={(event) => event.stopPropagation()}
            >
              创建分析
            </Button>
          </Tooltip>
          <Popconfirm
            title={
              record.status === 'ONLINE'
                ? '确认下线这个 Dataset？'
                : '确认上线这个 Dataset？'
            }
            description={
              record.status === 'ONLINE'
                ? '下线后现有 Analysis 将无法继续查询。'
                : '上线后可继续用于图表分析和仪表盘。'
            }
            okText="确认"
            cancelText="取消"
            onConfirm={() => void updateDatasetStatus(record)}
          >
            <Button
              type="text"
              size="small"
              loading={statusUpdatingId === record.id}
              onClick={(event) => event.stopPropagation()}
            >
              {record.status === 'ONLINE' ? '下线' : '上线'}
            </Button>
          </Popconfirm>
        </div>
      ),
    },
  ];

  const renderTreeTitle = (rawNode: DataNode) => {
    const node = rawNode as CatalogTreeNode;
    const dataset = node.datasetId
      ? datasets.find((item) => item.id === node.datasetId)
      : undefined;
    const icon =
      node.kind === 'dataset' ? (
        <TableProperties size={13} className="shrink-0 text-[#98a2b3]" />
      ) : node.kind === 'root' ? (
        <Database size={13} className="shrink-0 text-[#667085]" />
      ) : (
        <Folder size={13} className="shrink-0 text-[#98a2b3]" />
      );

    return (
      <div className="flex min-w-0 flex-1 items-center gap-2" title={node.title}>
        {icon}
        <span
          className={[
            'min-w-0 flex-1 truncate text-[13px] leading-8',
            node.kind === 'dataset'
              ? 'font-normal text-[#344054]'
              : 'font-medium text-[#1f2937]',
          ].join(' ')}
        >
          {node.title}
        </span>
        {dataset ? (
          <span
            className={[
              'h-1.5 w-1.5 shrink-0 rounded-full',
              dataset.status === 'ONLINE' ? 'bg-[#667085]' : 'bg-[#d0d5dd]',
            ].join(' ')}
          />
        ) : typeof node.datasetCount === 'number' ? (
          <span className="shrink-0 text-[10px] text-[#98a2b3]">
            {node.datasetCount}
          </span>
        ) : null}
      </div>
    );
  };

  const renderDirectoryView = () => (
    <main className="flex min-w-0 flex-1 flex-col overflow-hidden px-4 py-3">
      <div className="shrink-0 border-b border-[#eceef0] pb-2">
        <div className="flex min-w-0 flex-nowrap items-center gap-3 overflow-x-auto">
          <div className="flex min-w-0 shrink-0 items-center gap-2">
            <span className="max-w-[260px] truncate text-[13px] font-semibold text-[#30323b]">
              {scopeTitle}
            </span>
            <span className="text-[11px] text-[#98a2b3]">
              {scopeDatasets.length} 个 Dataset
            </span>
          </div>

          <div className="ml-auto flex shrink-0 items-center gap-2">
            <Input
              allowClear
              variant="filled"
              value={listKeyword}
              prefix={<Search size={14} className="text-[#98a2b3]" />}
              placeholder="搜索名称、字段、来源"
              className="w-[220px]"
              onChange={(event) => {
                setListKeyword(event.target.value);
                setCurrent(1);
              }}
            />
            <Select
              variant="filled"
              value={status}
              className="w-[112px]"
              onChange={(value) => {
                setStatus(value);
                setCurrent(1);
              }}
              options={[
                { label: '全部状态', value: 'ALL' },
                { label: '已上线', value: 'ONLINE' },
                { label: '已下线', value: 'OFFLINE' },
              ]}
            />
            <Select
              variant="filled"
              value={sourceType}
              className="w-[116px]"
              onChange={(value) => {
                setSourceType(value);
                setCurrent(1);
              }}
              options={[
                { label: '全部来源', value: 'ALL' },
                { label: 'SQL 查询', value: 'QUERY_REVISION' },
                { label: '数据表', value: 'TABLE' },
                { label: '视图', value: 'VIEW' },
              ]}
            />
            <Button onClick={resetFilters}>重置</Button>
            <Button
              aria-label="刷新"
              icon={<RefreshCw size={14} />}
              loading={loading}
              onClick={() => void load()}
            />
          </div>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-auto pt-2">
        {loadError ? (
          <div className="flex h-full items-center justify-center">
            <Empty description={loadError}>
              <Button onClick={() => void load()}>重新加载</Button>
            </Empty>
          </div>
        ) : (
          <Table<CatalogDataset>
            rowKey="id"
            size="small"
            bordered
            pagination={false}
            loading={loading}
            columns={columns}
            dataSource={pagedDatasets}
            scroll={{ x: 1200 }}
            locale={{ emptyText: '当前目录暂无 Dataset' }}
            onRow={(record) => ({
              onClick: () => selectDataset(record),
              style: { cursor: 'pointer' },
            })}
          />
        )}
      </div>

      <div className="flex shrink-0 justify-end border-t border-[#f0f2f5] pt-3">
        <Pagination
          size="small"
          current={current}
          pageSize={pageSize}
          total={filteredDatasets.length}
          showSizeChanger
          showTotal={(total) => `共 ${total} 条`}
          onChange={(nextCurrent, nextPageSize) => {
            setCurrent(nextPageSize === pageSize ? nextCurrent : 1);
            setPageSize(nextPageSize);
          }}
        />
      </div>
    </main>
  );

  const renderDatasetDetail = (dataset: CatalogDataset) => {
    const summary = schemaSummary(dataset);
    const detailTabs = [
      { key: 'fields', label: `字段信息 ${summary.fields}` },
      { key: 'versions', label: `版本历史 ${dataset.versions.length}` },
      { key: 'overview', label: '基本信息' },
    ] as const;

    const fieldColumns: ColumnsType<CatalogDataset['fields'][number]> = [
      {
        title: '字段名称',
        dataIndex: 'displayName',
        width: 180,
        render: (value: string, record) => (
          <div>
            <div className="text-[12px] font-medium text-[#344054]">{value}</div>
            <div className="mt-0.5 text-[10px] text-[#98a2b3]">
              {record.physicalName}
            </div>
          </div>
        ),
      },
      {
        title: '类型',
        dataIndex: 'dataType',
        width: 90,
        render: (value: string) => (
          <span className="text-[11px] text-[#667085]">
            {fieldTypeLabel[value] || value}
          </span>
        ),
      },
      {
        title: '角色',
        dataIndex: 'defaultRole',
        width: 90,
        render: (value: CatalogDatasetFieldRole) => (
          <span className="inline-flex items-center gap-1 text-[11px] text-[#667085]">
            {value === 'MEASURE' ? <Sigma size={11} /> : <Rows3 size={11} />}
            {roleLabel[value]}
          </span>
        ),
      },
      {
        title: '可空',
        dataIndex: 'nullable',
        width: 70,
        render: (value: boolean) => (
          <span className="text-[11px] text-[#667085]">{value ? '是' : '否'}</span>
        ),
      },
      {
        title: '描述',
        dataIndex: 'description',
        render: (value?: string) => (
          <span className="text-[11px] text-[#667085]">{value || '-'}</span>
        ),
      },
    ];

    const versionColumns: ColumnsType<CatalogDataset['versions'][number]> = [
      {
        title: 'Dataset 版本',
        dataIndex: 'versionNo',
        width: 120,
        render: (value: number) => (
          <span className="text-[12px] font-medium text-[#344054]">DV{value}</span>
        ),
      },
      {
        title: '来源类型',
        dataIndex: 'sourceType',
        width: 120,
        render: (value: CatalogDatasetSourceType) => (
          <span className="text-[11px] text-[#667085]">
            {sourceTypeLabel[value]}
          </span>
        ),
      },
      {
        title: '来源任务',
        render: (_, record) => (
          <div>
            <div className="text-[11px] text-[#475467]">
              {dataset.sourceTaskName || `TaskAsset #${record.sourceTaskAssetId}`}
            </div>
            <div className="mt-0.5 text-[10px] text-[#98a2b3]">
              SQL V{record.sourceTaskRevisionNo}
            </div>
          </div>
        ),
      },
      {
        title: '发布时间',
        dataIndex: 'createTime',
        width: 165,
        render: (value?: string) => (
          <span className="text-[11px] text-[#667085]">{formatTime(value)}</span>
        ),
      },
      {
        title: '状态',
        width: 90,
        render: (_, record) =>
          record.id === dataset.currentVersionId ? (
            <Tag
              bordered={false}
              className="m-0 bg-[#f1f3f5] text-[10px] text-[#475467]"
            >
              当前版本
            </Tag>
          ) : (
            <span className="text-[10px] text-[#98a2b3]">历史版本</span>
          ),
      },
    ];

    return (
      <main className="flex min-w-0 flex-1 flex-col overflow-hidden px-4 py-3">
        <div className="flex shrink-0 items-center gap-3 border-b border-[#eceef0] pb-2">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[6px] bg-[#f4f5f7] text-[#667085]">
            <TableProperties size={17} />
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <span className="truncate text-[14px] font-semibold text-[#30323b]">
                {dataset.name}
              </span>
              <Tag
                bordered={false}
                className="m-0 bg-[#f1f3f5] text-[10px] text-[#667085]"
              >
                {dataset.status === 'ONLINE' ? '已上线' : '已下线'}
              </Tag>
              {dataset.currentVersion ? (
                <span className="text-[10px] text-[#98a2b3]">
                  DV{dataset.currentVersion.versionNo}
                </span>
              ) : null}
            </div>
            <div className="mt-0.5 truncate text-[11px] text-[#98a2b3]">
              {dataset.description || dataset.sourceTaskName || '暂无描述'}
            </div>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            <Button href="/data-development/releases" icon={<GitBranch size={13} />}>
              发布中心
            </Button>
            <Popconfirm
              title={
                dataset.status === 'ONLINE'
                  ? '确认下线这个 Dataset？'
                  : '确认上线这个 Dataset？'
              }
              description={
                dataset.status === 'ONLINE'
                  ? '下线后现有 Analysis 将无法继续查询。'
                  : '上线后可继续用于图表分析和仪表盘。'
              }
              okText="确认"
              cancelText="取消"
              onConfirm={() => void updateDatasetStatus(dataset)}
            >
              <Button loading={statusUpdatingId === dataset.id}>
                {dataset.status === 'ONLINE' ? '下线' : '上线'}
              </Button>
            </Popconfirm>
            <Button
              type="primary"
              icon={<BarChart3 size={13} />}
              disabled={dataset.status !== 'ONLINE' || !dataset.currentVersion}
              href={
                dataset.status === 'ONLINE' && dataset.currentVersion
                  ? `/data-analysis/chart-analysis?datasetId=${encodeURIComponent(dataset.id)}`
                  : undefined
              }
            >
              创建分析
            </Button>
          </div>
        </div>

        <div className="flex h-10 shrink-0 items-end border-b border-[#eceef0]">
          {detailTabs.map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => setDetailTab(tab.key)}
              className={[
                'relative h-10 border-0 bg-transparent px-3 text-[12px]',
                detailTab === tab.key
                  ? 'font-medium text-[#30323b]'
                  : 'text-[#7b808a] hover:text-[#475467]',
              ].join(' ')}
            >
              {tab.label}
              {detailTab === tab.key ? (
                <span className="absolute inset-x-3 bottom-0 h-[2px] bg-[#30323b]" />
              ) : null}
            </button>
          ))}
        </div>

        <div className="flex min-h-0 flex-1 overflow-hidden pt-2">
          <section className="min-w-0 flex-1 overflow-auto pr-3">
            {detailTab === 'fields' ? (
              <Table
                rowKey="fieldId"
                size="small"
                bordered
                pagination={false}
                columns={fieldColumns}
                dataSource={dataset.fields}
                scroll={{ x: 760 }}
                locale={{ emptyText: '当前 Dataset 暂无字段' }}
              />
            ) : detailTab === 'versions' ? (
              <Table
                rowKey="id"
                size="small"
                bordered
                pagination={false}
                columns={versionColumns}
                dataSource={[...dataset.versions].sort(
                  (left, right) => right.versionNo - left.versionNo,
                )}
                scroll={{ x: 760 }}
                locale={{ emptyText: '暂无 DatasetVersion' }}
              />
            ) : (
              <div className="max-w-[980px] py-1">
                <div className="grid grid-cols-4 gap-3">
                  {[
                    { label: '字段', value: summary.fields, icon: Rows3 },
                    { label: '维度', value: summary.dimensions, icon: Rows3 },
                    { label: '指标', value: summary.metrics, icon: Sigma },
                    {
                      label: 'Analysis',
                      value: dataset.analysisCount,
                      icon: BarChart3,
                    },
                  ].map((item) => {
                    const Icon = item.icon;
                    return (
                      <div key={item.label} className="border border-[#e5e7eb] p-3">
                        <div className="flex items-center gap-1.5 text-[10px] text-[#98a2b3]">
                          <Icon size={11} /> {item.label}
                        </div>
                        <div className="mt-2 text-[20px] font-semibold text-[#344054]">
                          {item.value}
                        </div>
                      </div>
                    );
                  })}
                </div>
                <div className="mt-3 border border-[#e5e7eb] p-4">
                  <div className="text-[12px] font-medium text-[#344054]">
                    Dataset 描述
                  </div>
                  <div className="mt-2 text-[11px] leading-5 text-[#667085]">
                    {dataset.description || '暂无描述'}
                  </div>
                </div>
              </div>
            )}
          </section>

          <aside className="w-[248px] shrink-0 overflow-y-auto border-l border-[#e5e7eb] pl-4">
            <div className="text-[12px] font-medium text-[#344054]">
              Dataset 信息
            </div>
            <div className="mt-4 space-y-4 text-[11px]">
              <div>
                <div className="text-[#98a2b3]">状态</div>
                <div className="mt-1 text-[#475467]">
                  {dataset.status === 'ONLINE' ? '已上线' : '已下线'}
                </div>
              </div>
              <div>
                <div className="text-[#98a2b3]">所属目录</div>
                <div className="mt-1 break-all text-[#475467]">
                  {dataset.directoryPath
                    || (dataset.sourceNodeId ? '根目录' : '未分组')}
                </div>
              </div>
              <div>
                <div className="text-[#98a2b3]">来源任务</div>
                <div className="mt-1 break-all text-[#475467]">
                  {dataset.sourceTaskName || '-'}
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <div className="text-[#98a2b3]">TaskAsset</div>
                  <div className="mt-1 text-[#475467]">
                    {dataset.currentVersion
                      ? `#${dataset.currentVersion.sourceTaskAssetId}`
                      : '-'}
                  </div>
                </div>
                <div>
                  <div className="text-[#98a2b3]">SQL 版本</div>
                  <div className="mt-1 text-[#475467]">
                    {dataset.currentVersion
                      ? `V${dataset.currentVersion.sourceTaskRevisionNo}`
                      : '-'}
                  </div>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <div className="text-[#98a2b3]">当前版本</div>
                  <div className="mt-1 text-[#475467]">
                    {dataset.currentVersion
                      ? `DV${dataset.currentVersion.versionNo}`
                      : '-'}
                  </div>
                </div>
                <div>
                  <div className="text-[#98a2b3]">版本数</div>
                  <div className="mt-1 text-[#475467]">
                    {dataset.versions.length}
                  </div>
                </div>
              </div>
              <div>
                <div className="text-[#98a2b3]">更新时间</div>
                <div className="mt-1 text-[#475467]">
                  {formatTime(dataset.updateTime || dataset.createTime)}
                </div>
              </div>
              <div>
                <div className="text-[#98a2b3]">创建时间</div>
                <div className="mt-1 text-[#475467]">
                  {formatTime(dataset.createTime)}
                </div>
              </div>
            </div>
          </aside>
        </div>
      </main>
    );
  };

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="flex h-[calc(100vh-64px)] min-h-[640px] flex-col overflow-hidden bg-white text-[#161823]">
        <header className="shrink-0 border-b border-[#e8e9ec] px-5 py-3">
          <h1 className="m-0 text-[22px] font-semibold leading-8 text-[#161823]">
            数据目录
          </h1>
        </header>

        <div className="flex min-h-0 flex-1 overflow-hidden">
          <aside
            className="group relative shrink-0 overflow-hidden bg-white transition-[width] duration-200 ease-out"
            style={{ width: leftCollapsed ? 0 : leftWidth }}
          >
            <div
              className="flex h-full flex-col overflow-hidden py-3"
              style={{ width: leftWidth }}
            >
              <div className="flex h-7 shrink-0 items-center justify-between px-4">
                <span className="text-[13px] font-semibold text-[#30323b]">
                  目录
                </span>
                <span className="text-[10px] text-[#98a2b3]">
                  {datasets.length}
                </span>
              </div>

              <div className="mt-2 shrink-0 px-[14px]">
                <Input
                  allowClear
                  size="small"
                  variant="filled"
                  value={treeKeyword}
                  prefix={<Search size={13} className="text-[#98a2b3]" />}
                  placeholder="搜索目录 / Dataset"
                  onChange={(event) => setTreeKeyword(event.target.value)}
                />
              </div>

              <div className="mt-2 min-h-0 flex-1 overflow-y-auto px-[14px]">
                <Spin spinning={loading} wrapperClassName="block min-h-full">
                  {visibleTreeData.length ? (
                    <Tree
                      blockNode
                      defaultExpandAll
                      autoExpandParent={Boolean(treeKeyword.trim())}
                      selectedKeys={[selectedKey]}
                      treeData={visibleTreeData}
                      titleRender={renderTreeTitle}
                      switcherIcon={<ChevronDown size={12} strokeWidth={1.8} />}
                      onSelect={(keys) => {
                        const key = String(keys[0] || '');
                        if (!key) return;
                        setSelectedKey(key);
                        setDetailTab('fields');
                        setCurrent(1);
                      }}
                      className="catalog-tree bg-transparent"
                    />
                  ) : (
                    <Empty
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description={
                        treeKeyword.trim()
                          ? '未找到匹配 Dataset'
                          : '暂无已发布 Dataset'
                      }
                      className="mt-10"
                    />
                  )}
                </Spin>
              </div>
            </div>
          </aside>

          <div
            role="separator"
            aria-label="调整数据目录面板宽度"
            aria-orientation="vertical"
            onPointerDown={leftCollapsed ? undefined : handleResizeStart}
            className={[
              'group relative z-20 w-3 shrink-0 touch-none',
              leftCollapsed ? 'cursor-default' : 'cursor-col-resize',
            ].join(' ')}
          >
            <div
              className={[
                'pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-[#dfe3e8]',
                'transition-[width,background-color] duration-150',
                !leftCollapsed
                  ? 'group-hover:w-[2px] group-hover:bg-[rgba(254,44,85,.55)] group-active:bg-[rgba(254,44,85,1)]'
                  : '',
              ].join(' ')}
            />
            <button
              type="button"
              aria-label={leftCollapsed ? '展开数据目录面板' : '收起数据目录面板'}
              onPointerDown={(event) => event.stopPropagation()}
              onClick={() => setLeftCollapsed((value) => !value)}
              className={[
                'absolute left-1/2 top-1/2 z-20 flex h-8 w-4 -translate-x-1/2 -translate-y-1/2',
                'items-center justify-center rounded-[3px] border border-[#dfe3e8] bg-white text-[#7b808a]',
                'shadow-[0_1px_2px_rgba(16,24,40,0.05)] transition-[color,border-color,box-shadow] duration-150',
                'hover:border-[#cfd4dc] hover:text-[#344054] focus:outline-none focus-visible:ring-2',
                'focus-visible:ring-[rgba(254,44,85,.16)]',
              ].join(' ')}
            >
              {leftCollapsed ? (
                <ChevronRight size={12} />
              ) : (
                <ChevronLeft size={12} />
              )}
            </button>
          </div>

          {selectedDataset ? renderDatasetDetail(selectedDataset) : renderDirectoryView()}
        </div>
      </div>

      <style>{`
        .catalog-tree.ant-tree {
          color: #344054;
        }
        .catalog-tree .ant-tree-list-holder-inner {
          gap: 2px;
        }
        .catalog-tree .ant-tree-treenode {
          box-sizing: border-box;
          width: 100%;
          min-height: 32px;
          padding: 0 8px !important;
          align-items: center;
          border-radius: 0;
          transition: background-color 0.15s ease;
        }
        .catalog-tree .ant-tree-treenode:hover,
        .catalog-tree .ant-tree-treenode:has(.ant-tree-node-selected) {
          background: #f5f5f5;
        }
        .catalog-tree .ant-tree-node-content-wrapper {
          display: flex;
          min-width: 0;
          height: 32px;
          flex: 1;
          align-items: center;
          padding: 0 !important;
          border-radius: 0 !important;
          background: transparent !important;
          line-height: 32px;
        }
        .catalog-tree .ant-tree-node-content-wrapper.ant-tree-node-selected {
          color: #1f2937;
          background: transparent !important;
        }
        .catalog-tree .ant-tree-title {
          display: flex;
          min-width: 0;
          flex: 1;
        }
        .catalog-tree .ant-tree-indent-unit {
          width: 22px;
        }
        .catalog-tree .ant-tree-switcher {
          display: inline-flex;
          width: 20px;
          height: 32px;
          flex: none;
          align-items: center;
          justify-content: center;
          color: #98a2b3;
          line-height: 32px;
        }
        .catalog-tree .ant-tree-switcher svg {
          transition: transform 0.15s ease;
        }
        .catalog-tree .ant-tree-switcher_close svg {
          transform: rotate(-90deg);
        }
        .catalog-tree .ant-tree-switcher-noop {
          width: 20px;
        }
      `}</style>
    </ConfigProvider>
  );
};

export default DataCatalogPage;
