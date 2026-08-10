import { API_SUCCESS_CODE } from '@/services/http/response';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { fetchDataSourceAll } from '@/pages/data-source/service';
import type { DataSourceRecord } from '@/pages/data-source/types';
import { history } from '@umijs/max';
import {
  Button,
  Empty,
  Input,
  Select,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { Plus, RefreshCw, Search } from 'lucide-react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import CreateDirectoryModal from './components/CreateDirectoryModal';
import CreateTaskModal from './components/CreateTaskModal';
import DevelopmentTreePane, {
  type DevelopmentTreeNode,
} from './components/DevelopmentTreePane';
import {
  createDevelopmentDirectory,
  listDevelopmentDirectories,
  listSqlTasks,
} from './service';
import type {
  DevelopmentDirectory,
  DevelopmentTaskRow,
  DevelopmentTaskType,
} from './types';

type TreeFilterKey =
  | 'all'
  | 'unassigned'
  | `project:${number}`
  | `root:${number}`
  | `directory:${number}`;
type PublishFilter = 'ALL' | 'DRAFT' | 'PUBLISHED';

const DEFAULT_LEFT_WIDTH = 252;
const MIN_LEFT_WIDTH = 210;
const MAX_LEFT_WIDTH = 420;
const LEFT_WIDTH_STORAGE_KEY = 'yak-data-development.left-width';

const projectKey = (projectId: number): TreeFilterKey =>
  `project:${projectId}`;
const rootKey = (projectId: number): TreeFilterKey =>
  `root:${projectId}`;
const directoryKey = (directoryId: number): TreeFilterKey =>
  `directory:${directoryId}`;

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

export default function DataDevelopmentPage() {
  const { projects, currentProject } = useSecurityProject();
  const [tasks, setTasks] = useState<DevelopmentTaskRow[]>([]);
  const [directories, setDirectories] = useState<DevelopmentDirectory[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [treeLoading, setTreeLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [directoryOpen, setDirectoryOpen] = useState(false);
  const [directorySaving, setDirectorySaving] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<'ALL' | DevelopmentTaskType>('ALL');
  const [publishFilter, setPublishFilter] = useState<PublishFilter>('ALL');
  const [selectedNode, setSelectedNode] = useState<TreeFilterKey>(() =>
    currentProject?.id ? projectKey(Number(currentProject.id)) : 'all',
  );
  const [leftWidth, setLeftWidth] = useState(initialLeftWidth);
  const [leftCollapsed, setLeftCollapsed] = useState(false);

  const loadDirectories = useCallback(async () => {
    setTreeLoading(true);
    try {
      const responses = await Promise.all(
        projects.map(async (project) => {
          const projectId = Number(project.id);
          const response = await listDevelopmentDirectories(projectId);
          return responseData(response, `查询项目“${project.projectName}”目录失败`);
        }),
      );
      setDirectories(responses.flat());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '查询数据开发目录失败');
      setDirectories([]);
    } finally {
      setTreeLoading(false);
    }
  }, [projects]);

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
  }, [load]);

  useEffect(() => {
    void loadDirectories();
  }, [loadDirectories]);

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

  const projectIdForSelection = useMemo(() => {
    const projectId = numberFromKey(selectedNode, 'project:')
      ?? numberFromKey(selectedNode, 'root:');
    if (projectId) return projectId;
    const directoryId = numberFromKey(selectedNode, 'directory:');
    return directoryId ? directoryMap.get(directoryId)?.projectId : undefined;
  }, [directoryMap, selectedNode]);

  const directoryIdForSelection = useMemo(
    () => numberFromKey(selectedNode, 'directory:'),
    [selectedNode],
  );

  const projectDirectories = useMemo(
    () => projectIdForSelection
      ? directories.filter((directory) => directory.projectId === projectIdForSelection)
      : [],
    [directories, projectIdForSelection],
  );

  const counts = useMemo(() => {
    const byProject = new Map<number, number>();
    const byDirectory = new Map<number, number>();
    const rootByProject = new Map<number, number>();
    let unassigned = 0;

    tasks.forEach((task) => {
      if (!task.projectId) {
        unassigned += 1;
        return;
      }
      const projectId = Number(task.projectId);
      byProject.set(projectId, (byProject.get(projectId) || 0) + 1);
      if (task.directoryId) {
        const directoryId = Number(task.directoryId);
        byDirectory.set(directoryId, (byDirectory.get(directoryId) || 0) + 1);
      } else {
        rootByProject.set(projectId, (rootByProject.get(projectId) || 0) + 1);
      }
    });

    return { byProject, byDirectory, rootByProject, unassigned };
  }, [tasks]);

  const treeData = useMemo<DevelopmentTreeNode[]>(() => {
    const buildDirectoryChildren = (
      projectId: number,
      parentId?: number,
    ): DevelopmentTreeNode[] =>
      directories
        .filter(
          (directory) =>
            directory.projectId === projectId
            && (directory.parentId ?? undefined) === parentId,
        )
        .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
        .map((directory) => ({
          key: directoryKey(Number(directory.id)),
          title: directory.name,
          nodeType: 'directory',
          projectId,
          directoryId: Number(directory.id),
          count: counts.byDirectory.get(Number(directory.id)) || 0,
          children: buildDirectoryChildren(projectId, Number(directory.id)),
        }));

    const projectNodes: DevelopmentTreeNode[] = projects.map((project) => {
      const projectId = Number(project.id);
      return {
        key: projectKey(projectId),
        title: project.projectName,
        nodeType: 'project',
        projectId,
        count: counts.byProject.get(projectId) || 0,
        children: [
          {
            key: rootKey(projectId),
            title: '/',
            nodeType: 'directory',
            projectId,
            count: counts.rootByProject.get(projectId) || 0,
            children: buildDirectoryChildren(projectId),
          },
        ],
      };
    });

    return [
      {
        key: 'all',
        title: '全部任务',
        nodeType: 'all',
        count: tasks.length,
      },
      ...projectNodes,
      ...(counts.unassigned > 0
        ? [
            {
              key: 'unassigned' as const,
              title: '未归属项目',
              nodeType: 'unassigned' as const,
              count: counts.unassigned,
            },
          ]
        : []),
    ];
  }, [counts, directories, projects, tasks.length]);

  const filteredTasks = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    const selectedProjectId = numberFromKey(selectedNode, 'project:');
    const selectedRootProjectId = numberFromKey(selectedNode, 'root:');
    const selectedDirectoryId = numberFromKey(selectedNode, 'directory:');

    return tasks.filter((task) => {
      if (selectedNode === 'unassigned' && task.projectId) return false;
      if (selectedProjectId && Number(task.projectId) !== selectedProjectId) return false;
      if (
        selectedRootProjectId
        && (Number(task.projectId) !== selectedRootProjectId || Boolean(task.directoryId))
      ) {
        return false;
      }
      if (selectedDirectoryId && Number(task.directoryId) !== selectedDirectoryId) return false;
      if (typeFilter !== 'ALL' && task.type !== typeFilter) return false;
      if (publishFilter === 'DRAFT' && task.latestVersionNo > 0) return false;
      if (publishFilter === 'PUBLISHED' && task.latestVersionNo <= 0) return false;
      if (
        normalizedKeyword
        && !`${task.name} ${task.description || ''}`.toLowerCase().includes(normalizedKeyword)
      ) {
        return false;
      }
      return true;
    });
  }, [keyword, publishFilter, selectedNode, tasks, typeFilter]);

  const openTask = (task: DevelopmentTaskRow) => {
    history.push(`/data-development/task/${task.id}`);
  };

  const columns: ColumnsType<DevelopmentTaskRow> = [
    {
      title: '任务名称',
      dataIndex: 'name',
      minWidth: 260,
      render: (_, task) => (
        <button
          type="button"
          className="border-0 bg-transparent p-0 text-left"
          onClick={() => openTask(task)}
        >
          <div className="font-medium text-[#161823] hover:underline">{task.name}</div>
          <div className="mt-1 max-w-[420px] truncate text-[12px] text-[rgba(22,24,35,.45)]">
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
        <Tag className="!m-0 !border-[#e4e7ec] !bg-[#f7f7f8] !text-[#161823]">
          SQL
        </Tag>
      ),
    },
    {
      title: '所属项目 / 目录',
      dataIndex: 'projectId',
      width: 220,
      render: (_, task) => {
        const projectName = task.projectId
          ? projectNameMap.get(Number(task.projectId)) || `项目 ${task.projectId}`
          : '未归属';
        const path = task.directoryId
          ? directoryMap.get(Number(task.directoryId))?.path || `目录 #${task.directoryId}`
          : '/';
        return (
          <div className="min-w-0">
            <div className="truncate text-[#344054]">{projectName}</div>
            <div className="mt-0.5 truncate text-[11px] text-[rgba(22,24,35,.38)]">
              {path}
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
        return source ? `${source.name || '-'} · ${source.dbType || '-'}` : `#${value}`;
      },
    },
    {
      title: '状态 / 版本',
      width: 140,
      render: (_, task) =>
        task.latestVersionNo > 0 ? (
          <span className="text-[#161823]">已发布 · V{task.latestVersionNo}</span>
        ) : (
          <span className="text-[rgba(22,24,35,.45)]">草稿</span>
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
        <Button type="link" className="!px-0" onClick={() => openTask(task)}>
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

  const openCreateDirectory = () => {
    if (!projectIdForSelection) {
      message.info('请先在左侧选择一个项目或目录');
      return;
    }
    setDirectoryOpen(true);
  };

  const submitDirectory = async (parentId: number | undefined, name: string) => {
    if (!projectIdForSelection) return;
    setDirectorySaving(true);
    try {
      const created = responseData(
        await createDevelopmentDirectory({
          projectId: projectIdForSelection,
          parentId,
          name,
        }),
        '新建目录失败',
      );
      await loadDirectories();
      setDirectoryOpen(false);
      setSelectedNode(directoryKey(Number(created.id)));
      message.success('目录创建成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '新建目录失败');
    } finally {
      setDirectorySaving(false);
    }
  };

  const selectedProjectName = projectIdForSelection
    ? projectNameMap.get(projectIdForSelection)
    : undefined;
  const defaultDirectoryParentId = directoryIdForSelection;

  return (
    <section className="m-4 overflow-hidden rounded-xl border border-[#e4e7ec] bg-white">
      <div className="flex h-[calc(100vh-80px)] min-h-[620px]">
        <DevelopmentTreePane
          treeData={treeData}
          treeLoading={treeLoading}
          selectedNodeKey={selectedNode}
          leftWidth={leftWidth}
          collapsed={leftCollapsed}
          createDisabled={!projectIdForSelection}
          onCreateDirectory={openCreateDirectory}
          onResizeStart={handleResizeStart}
          onCollapsedChange={setLeftCollapsed}
          onSelect={(keys) => {
            const key = keys[0];
            if (key) setSelectedNode(String(key) as TreeFilterKey);
          }}
        />

        <main className="min-w-0 flex-1 overflow-auto">
          <div className="flex items-start justify-between gap-4 px-6 pb-4 pt-5">
            <div>
              <Typography.Title level={4} className="!mb-1 !text-[#161823]">
                数据开发
              </Typography.Title>
              <Typography.Text className="text-[12px] text-[rgba(22,24,35,.45)]">
                统一管理 SQL、Shell、HTTP、Python 等开发任务；当前阶段开放 SQL。
              </Typography.Text>
            </div>
            <Button
              type="primary"
              icon={<Plus size={15} />}
              onClick={() => setCreateOpen(true)}
            >
              新建任务
            </Button>
          </div>

          <div className="flex items-center justify-between gap-3 border-y border-[#eceef1] bg-[#fafafa] px-6 py-3">
            <div className="flex min-w-0 items-center gap-2">
              <Input
                value={keyword}
                allowClear
                prefix={<Search size={14} className="text-[rgba(22,24,35,.34)]" />}
                placeholder="搜索任务名称或描述"
                className="w-[260px]"
                onChange={(event) => setKeyword(event.target.value)}
              />
              <Select
                value={typeFilter}
                className="w-[120px]"
                options={[
                  { label: '全部类型', value: 'ALL' },
                  { label: 'SQL', value: 'SQL' },
                ]}
                onChange={setTypeFilter}
              />
              <Select
                value={publishFilter}
                className="w-[130px]"
                options={[
                  { label: '全部状态', value: 'ALL' },
                  { label: '草稿', value: 'DRAFT' },
                  { label: '已发布', value: 'PUBLISHED' },
                ]}
                onChange={setPublishFilter}
              />
            </div>
            <Button
              icon={<RefreshCw size={14} />}
              onClick={() => {
                void load();
                void loadDirectories();
              }}
            >
              刷新
            </Button>
          </div>

          <Spin spinning={loading}>
            <Table<DevelopmentTaskRow>
              rowKey="id"
              size="small"
              pagination={{ pageSize: 15, showSizeChanger: false }}
              columns={columns}
              dataSource={filteredTasks}
              scroll={{ x: 1180 }}
              locale={{
                emptyText: (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="当前目录暂无数据开发任务"
                  />
                ),
              }}
            />
          </Spin>
        </main>
      </div>

      <CreateTaskModal
        open={createOpen}
        projects={projects}
        defaultProjectId={
          projectIdForSelection
          ?? (currentProject?.id ? Number(currentProject.id) : undefined)
        }
        onCancel={() => setCreateOpen(false)}
        onNext={(type, projectId) => {
          setCreateOpen(false);
          const directoryId =
            projectId === projectIdForSelection && directoryIdForSelection
              ? directoryIdForSelection
              : 0;
          history.push(
            `/data-development/task/new?type=${encodeURIComponent(type)}&projectId=${projectId}&directoryId=${directoryId}`,
          );
        }}
      />

      <CreateDirectoryModal
        open={directoryOpen}
        projectName={selectedProjectName}
        directories={projectDirectories}
        defaultParentId={defaultDirectoryParentId}
        loading={directorySaving}
        onCancel={() => {
          if (!directorySaving) setDirectoryOpen(false);
        }}
        onSubmit={(parentId, name) => void submitDirectory(parentId, name)}
      />
    </section>
  );
}
