import { API_SUCCESS_CODE } from '@/services/http/response';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { fetchDataSourceAll } from '@/pages/data-source/service';
import type { DataSourceRecord } from '@/pages/data-source/types';
import { BRAND_THEME } from '@/styles/brand';
import { history } from '@umijs/max';
import {
  Button,
  ConfigProvider,
  Empty,
  Input,
  Pagination,
  Select,
  Table,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { RefreshCw, Search } from 'lucide-react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import CreateDirectoryModal from './components/CreateDirectoryModal';
import CreateTaskModal from './components/CreateTaskModal';
import DevelopmentTreePane, {
  type DevelopmentNodeCreateType,
  type DevelopmentTreeNode,
} from './components/DevelopmentTreePane';
import {
  createDevelopmentDirectory,
  createDevelopmentNode,
  listDevelopmentDirectories,
  listDevelopmentNodes,
  listSqlTasks,
} from './service';
import type {
  DevelopmentDirectory,
  DevelopmentNode,
  DevelopmentTaskRow,
  DevelopmentTaskType,
} from './types';

type TreeFilterKey = 'root' | `directory:${number}` | `node:${number}`;
type PublishFilter = 'ALL' | 'DRAFT' | 'PUBLISHED';

const DEFAULT_LEFT_WIDTH = 272;
const MIN_LEFT_WIDTH = 220;
const MAX_LEFT_WIDTH = 440;
const LEFT_WIDTH_STORAGE_KEY = 'yak-data-development.left-width';

const directoryKey = (directoryId: number): TreeFilterKey =>
  `directory:${directoryId}`;
const nodeKey = (nodeId: number): TreeFilterKey => `node:${nodeId}`;

const numberFromKey = (key: string, prefix: string) => {
  if (!key.startsWith(prefix)) return undefined;
  const value = Number(key.substring(prefix.length));
  return Number.isFinite(value) && value > 0 ? value : undefined;
};

const clampLeftWidth = (value: number) =>
  Math.min(MAX_LEFT_WIDTH, Math.max(MIN_LEFT_WIDTH, value));

const initialLeftWidth = () => {
  if (typeof window === 'undefined') return DEFAULT_LEFT_WIDTH;
  const stored = Number(window.localStorage.getItem(LEFT_WIDTH_STORAGE_KEY));
  return Number.isFinite(stored) && stored > 0
    ? clampLeftWidth(stored)
    : DEFAULT_LEFT_WIDTH;
};

const formatTime = (value?: string) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';

const responseData = <T,>(
  response: { code?: number; data?: T; msg?: string; message?: string },
  fallback: string,
): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const taskTableClassName = [
  'compact-development-task-table',
  '[&_.ant-table]:!text-[13px]',
  '[&_.ant-table-container]:!rounded-none',
  '[&_.ant-table-container]:!border-[#eaecf0]',
  '[&_.ant-table-cell]:!align-middle',
  '[&_.ant-table-thead>tr>th]:!h-10',
  '[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb]',
  '[&_.ant-table-thead>tr>th]:!px-4',
  '[&_.ant-table-thead>tr>th]:!py-2',
  '[&_.ant-table-thead>tr>th]:!text-[12px]',
  '[&_.ant-table-thead>tr>th]:!font-medium',
  '[&_.ant-table-thead>tr>th]:!text-[#667085]',
  '[&_.ant-table-thead>tr>th]:!border-[#eaecf0]',
  '[&_.ant-table-tbody>tr>td]:!px-4',
  '[&_.ant-table-tbody>tr>td]:!py-2.5',
  '[&_.ant-table-tbody>tr>td]:!border-[#f0f2f5]',
  '[&_.ant-table-tbody>tr>td]:!text-[#667085]',
  '[&_.ant-table-tbody>tr:hover>td]:!bg-[#fafbfc]',
  '[&_.ant-table-cell-fix-right]:!bg-white',
  '[&_.ant-table-tbody>tr:hover_.ant-table-cell-fix-right]:!bg-[#fafbfc]',
  '[&_.ant-table-placeholder>td]:!h-[240px]',
].join(' ');

export default function DataDevelopmentPage() {
  const { projects, currentProject } = useSecurityProject();
  const [tasks, setTasks] = useState<DevelopmentTaskRow[]>([]);
  const [nodes, setNodes] = useState<DevelopmentNode[]>([]);
  const [directories, setDirectories] = useState<DevelopmentDirectory[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [treeLoading, setTreeLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [createType, setCreateType] = useState<DevelopmentNodeCreateType>('SQL');
  const [nodeSaving, setNodeSaving] = useState(false);
  const [directoryOpen, setDirectoryOpen] = useState(false);
  const [directorySaving, setDirectorySaving] = useState(false);
  const [treeKeyword, setTreeKeyword] = useState('');
  const [keywordDraft, setKeywordDraft] = useState('');
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<'ALL' | DevelopmentTaskType>('ALL');
  const [publishFilter, setPublishFilter] = useState<PublishFilter>('ALL');
  const [selectedNode, setSelectedNode] = useState<TreeFilterKey>('root');
  const [leftWidth, setLeftWidth] = useState(initialLeftWidth);
  const [leftCollapsed, setLeftCollapsed] = useState(false);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const loadTree = useCallback(async () => {
    setTreeLoading(true);
    try {
      const [directoryResponse, nodeResponse] = await Promise.all([
        listDevelopmentDirectories(),
        listDevelopmentNodes(),
      ]);
      setDirectories(responseData(directoryResponse, '查询数据开发目录失败') || []);
      setNodes(responseData(nodeResponse, '查询数据开发节点失败') || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '查询数据开发树失败');
      setDirectories([]);
      setNodes([]);
    } finally {
      setTreeLoading(false);
    }
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [taskResponse, dataSourceResponse] = await Promise.all([
        listSqlTasks(),
        fetchDataSourceAll(),
      ]);
      const taskList = responseData(taskResponse, '查询数据开发任务失败');
      const sourceResult = responseData(dataSourceResponse, '查询数据源失败');
      setTasks(
        (taskList || []).map((task) => ({
          ...task,
          type: 'SQL' as const,
        })),
      );
      setDataSources(sourceResult?.bizData || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '查询数据开发任务失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    void loadTree();
  }, [load, loadTree]);

  const projectNameMap = useMemo(
    () => new Map(projects.map((project) => [Number(project.id), project.projectName])),
    [projects],
  );
  const dataSourceMap = useMemo(
    () => new Map(dataSources.map((item) => [Number(item.id), item])),
    [dataSources],
  );
  const directoryMap = useMemo(
    () => new Map(directories.map((directory) => [Number(directory.id), directory])),
    [directories],
  );
  const nodeMap = useMemo(
    () => new Map(nodes.map((node) => [Number(node.id), node])),
    [nodes],
  );

  const selectedResourceNodeId = useMemo(
    () => numberFromKey(selectedNode, 'node:'),
    [selectedNode],
  );
  const directoryIdForSelection = useMemo(() => {
    const selectedDirectoryId = numberFromKey(selectedNode, 'directory:');
    if (selectedDirectoryId) return selectedDirectoryId;
    if (!selectedResourceNodeId) return undefined;
    const resourceNode = nodeMap.get(selectedResourceNodeId);
    return resourceNode?.directoryId ? Number(resourceNode.directoryId) : undefined;
  }, [nodeMap, selectedNode, selectedResourceNodeId]);

  const fullTreeData = useMemo<DevelopmentTreeNode[]>(() => {
    if (!directories.length && !nodes.length) return [];

    const resourceNodes = (directoryId?: number): DevelopmentTreeNode[] =>
      nodes
        .filter((node) => {
          const nodeDirectoryId = node.directoryId
            ? Number(node.directoryId)
            : undefined;
          return nodeDirectoryId === directoryId;
        })
        .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
        .map((node) => ({
          key: nodeKey(Number(node.id)),
          title: node.name,
          nodeType: 'node',
          nodeId: Number(node.id),
          taskType: node.type,
          configured: node.configured,
          searchText: `${node.name} ${node.type} ${node.id}`,
          isLeaf: true,
        }));

    const directoryNodes = (parentId?: number): DevelopmentTreeNode[] =>
      directories
        .filter((directory) => (directory.parentId ?? undefined) === parentId)
        .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
        .map((directory) => {
          const directoryId = Number(directory.id);
          return {
            key: directoryKey(directoryId),
            title: directory.name,
            nodeType: 'directory',
            directoryId,
            searchText: `${directory.name} ${directory.path || ''}`,
            children: [
              ...directoryNodes(directoryId),
              ...resourceNodes(directoryId),
            ],
          };
        });

    return [
      {
        key: 'root',
        title: '/',
        nodeType: 'root',
        searchText: '/',
        children: [...directoryNodes(), ...resourceNodes()],
      },
    ];
  }, [directories, nodes]);

  const treeData = useMemo<DevelopmentTreeNode[]>(() => {
    const normalized = treeKeyword.trim().toLowerCase();
    if (!normalized) return fullTreeData;

    const filterNodes = (
      values: DevelopmentTreeNode[],
    ): DevelopmentTreeNode[] =>
      values.flatMap((node) => {
        const children = node.children ? filterNodes(node.children) : [];
        const text = `${node.title} ${node.searchText || ''}`.toLowerCase();
        const selfMatched = node.nodeType !== 'root' && text.includes(normalized);

        if (selfMatched) {
          return [{ ...node, children: node.children }];
        }
        if (children.length) {
          return [{ ...node, children }];
        }
        return [];
      });

    return filterNodes(fullTreeData);
  }, [fullTreeData, treeKeyword]);

  const filteredTasks = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    const selectedDirectoryId = numberFromKey(selectedNode, 'directory:');

    return tasks.filter((task) => {
      if (
        selectedDirectoryId
        && Number(task.directoryId) !== selectedDirectoryId
      ) {
        return false;
      }
      if (typeFilter !== 'ALL' && task.type !== typeFilter) return false;
      if (publishFilter === 'DRAFT' && task.latestVersionNo > 0) return false;
      if (publishFilter === 'PUBLISHED' && task.latestVersionNo <= 0) return false;
      if (
        normalizedKeyword
        && !`${task.name} ${task.description || ''}`
          .toLowerCase()
          .includes(normalizedKeyword)
      ) {
        return false;
      }
      return true;
    });
  }, [keyword, publishFilter, selectedNode, tasks, typeFilter]);

  useEffect(() => {
    setCurrent(1);
  }, [keyword, publishFilter, selectedNode, typeFilter]);

  useEffect(() => {
    const lastPage = Math.max(1, Math.ceil(filteredTasks.length / pageSize));
    if (current > lastPage) setCurrent(lastPage);
  }, [current, filteredTasks.length, pageSize]);

  const pagedTasks = useMemo(() => {
    const start = (current - 1) * pageSize;
    return filteredTasks.slice(start, start + pageSize);
  }, [current, filteredTasks, pageSize]);

  const applySearch = () => {
    setKeyword(keywordDraft.trim());
    setCurrent(1);
  };

  const openTask = (task: DevelopmentTaskRow) => {
    history.push(`/data-development/task/${task.id}`);
  };

  const columns: ColumnsType<DevelopmentTaskRow> = [
    {
      title: '任务名称',
      dataIndex: 'name',
      minWidth: 260,
      fixed: 'left',
      render: (_, task) => (
        <button
          type="button"
          className="border-0 bg-transparent p-0 text-left"
          onClick={() => openTask(task)}
        >
          <div className="font-medium text-[#161823] hover:text-[#fe2c55]">
            {task.name}
          </div>
          <div className="mt-1 max-w-[420px] truncate text-[11px] text-[#98a2b3]">
            {task.description || `SQL Task · ${task.id}`}
          </div>
        </button>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 100,
      render: () => (
        <Tag className="!m-0 !border-[#e4e7ec] !bg-[#f7f7f8] !text-[#344054]">
          SQL
        </Tag>
      ),
    },
    {
      title: '所属项目 / 目录',
      width: 220,
      render: (_, task) => {
        const projectName = task.projectId
          ? projectNameMap.get(Number(task.projectId)) || `项目 ${task.projectId}`
          : '未归属';
        const path = task.directoryId
          ? directoryMap.get(Number(task.directoryId))?.path || `目录 #${task.directoryId}`
          : '/';
        return (
          <div className="min-w-0 py-0.5">
            <div className="truncate text-[#344054]">{path}</div>
            <div className="mt-1 truncate text-[11px] text-[#98a2b3]">
              {projectName}
            </div>
          </div>
        );
      },
    },
    {
      title: '数据源',
      dataIndex: 'dataSourceId',
      width: 180,
      render: (value: number) => {
        const source = dataSourceMap.get(Number(value));
        return source ? (
          <div className="min-w-0 py-0.5">
            <div className="truncate text-[#344054]">{source.name || '-'}</div>
            <div className="mt-1 truncate text-[11px] text-[#98a2b3]">
              {source.dbType || '-'}
            </div>
          </div>
        ) : (
          `#${value}`
        );
      },
    },
    {
      title: '状态 / 版本',
      width: 140,
      render: (_, task) =>
        task.latestVersionNo > 0 ? (
          <span className="text-[#344054]">已发布 · V{task.latestVersionNo}</span>
        ) : (
          <span className="text-[#98a2b3]">草稿</span>
        ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (value?: string) => formatTime(value),
    },
    {
      title: '操作',
      width: 90,
      fixed: 'right',
      render: (_, task) => (
        <Button
          type="link"
          size="small"
          className="!px-0"
          onClick={() => openTask(task)}
        >
          配置
        </Button>
      ),
    },
  ];

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

  const submitDirectory = async (parentId: number | undefined, name: string) => {
    setDirectorySaving(true);
    try {
      const created = responseData(
        await createDevelopmentDirectory({ parentId, name }),
        '新建目录失败',
      );
      setTreeKeyword('');
      await loadTree();
      setDirectoryOpen(false);
      setSelectedNode(directoryKey(Number(created.id)));
      message.success('目录创建成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '新建目录失败');
    } finally {
      setDirectorySaving(false);
    }
  };

  const submitNode = async (
    type: DevelopmentTaskType,
    projectId: number | undefined,
    directoryId: number | undefined,
    name: string,
  ) => {
    setNodeSaving(true);
    try {
      const created = responseData(
        await createDevelopmentNode({
          name,
          type,
          projectId,
          directoryId,
        }),
        '新建节点失败',
      );
      setTreeKeyword('');
      await loadTree();
      setCreateOpen(false);
      setSelectedNode(nodeKey(Number(created.id)));
      message.success('节点创建成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '新建节点失败');
    } finally {
      setNodeSaving(false);
    }
  };

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="flex h-[calc(100vh-64px)] min-h-[640px] flex-col overflow-hidden bg-white">
        <header className="shrink-0 border-b border-[#e8e9ec] px-5 py-3">
          <h1 className="m-0 text-[22px] font-semibold leading-8 text-[#161823]">
            数据开发
          </h1>
        </header>

        <div className="flex min-h-0 flex-1 overflow-hidden">
          <DevelopmentTreePane
            treeData={treeData}
            treeLoading={treeLoading}
            selectedNodeKey={selectedNode}
            searchValue={treeKeyword}
            leftWidth={leftWidth}
            collapsed={leftCollapsed}
            onCreateDirectory={() => setDirectoryOpen(true)}
            onCreateNode={(type) => {
              setCreateType(type);
              setCreateOpen(true);
            }}
            onSearchChange={setTreeKeyword}
            onResizeStart={handleResizeStart}
            onCollapsedChange={setLeftCollapsed}
            onSelect={(keys) => {
              const key = keys[0];
              if (!key) return;
              setSelectedNode(String(key) as TreeFilterKey);
            }}
          />

          <main className="flex min-w-0 flex-1 flex-col overflow-hidden px-4 py-3">
            <div className="shrink-0 border-b border-[#eceef0] pb-2">
              <div className="flex min-w-0 flex-nowrap items-center justify-end gap-2 overflow-x-auto">
                <Input
                  allowClear
                  variant="filled"
                  value={keywordDraft}
                  onChange={(event) => setKeywordDraft(event.target.value)}
                  onPressEnter={applySearch}
                  prefix={<Search size={14} className="text-[#98a2b3]" />}
                  placeholder="搜索任务名称或描述"
                  className="w-[220px] shrink-0"
                />

                <Select
                  variant="filled"
                  value={typeFilter}
                  className="w-[120px] shrink-0"
                  options={[
                    { label: '全部类型', value: 'ALL' },
                    { label: 'SQL', value: 'SQL' },
                  ]}
                  onChange={setTypeFilter}
                />

                <Select
                  variant="filled"
                  value={publishFilter}
                  className="w-[130px] shrink-0"
                  options={[
                    { label: '全部状态', value: 'ALL' },
                    { label: '草稿', value: 'DRAFT' },
                    { label: '已发布', value: 'PUBLISHED' },
                  ]}
                  onChange={setPublishFilter}
                />

                <Button onClick={applySearch}>查询</Button>

                <Button
                  aria-label="刷新"
                  icon={<RefreshCw size={14} />}
                  onClick={() => {
                    void load();
                    void loadTree();
                  }}
                />
              </div>
            </div>

            <div className="min-h-0 flex-1 overflow-auto pt-2">
              <Table<DevelopmentTaskRow>
                rowKey="id"
                size="small"
                bordered
                loading={loading}
                pagination={false}
                columns={columns}
                dataSource={pagedTasks}
                scroll={{ x: 1180 }}
                className={taskTableClassName}
                locale={{
                  emptyText: (
                    <Empty
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description="当前目录暂无数据开发任务"
                    />
                  ),
                }}
              />
            </div>

            <div className="flex shrink-0 justify-end border-t border-[#f0f2f5] pt-3">
              <Pagination
                size="small"
                current={current}
                pageSize={pageSize}
                total={filteredTasks.length}
                showSizeChanger
                showTotal={(value) => `共 ${value} 条`}
                onChange={(nextCurrent, nextPageSize) => {
                  if (nextPageSize !== pageSize) {
                    setPageSize(nextPageSize);
                    setCurrent(1);
                    return;
                  }
                  setCurrent(nextCurrent);
                }}
              />
            </div>
          </main>
        </div>

        <CreateTaskModal
          open={createOpen}
          type={createType}
          directories={directories}
          defaultProjectId={
            currentProject?.id ? Number(currentProject.id) : undefined
          }
          defaultDirectoryId={directoryIdForSelection}
          loading={nodeSaving}
          onCancel={() => {
            if (!nodeSaving) setCreateOpen(false);
          }}
          onNext={(type, projectId, directoryId, name) => {
            void submitNode(type, projectId, directoryId, name);
          }}
        />

        <CreateDirectoryModal
          open={directoryOpen}
          directories={directories}
          defaultParentId={directoryIdForSelection}
          loading={directorySaving}
          onCancel={() => {
            if (!directorySaving) setDirectoryOpen(false);
          }}
          onSubmit={(parentId, name) => void submitDirectory(parentId, name)}
        />
      </div>
    </ConfigProvider>
  );
}
