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
  Tree,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  Folder,
  FolderOpen,
  Plus,
  RefreshCw,
  Search,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import CreateTaskModal from './components/CreateTaskModal';
import { listSqlTasks } from './service';
import type {
  DevelopmentTaskRow,
  DevelopmentTaskType,
} from './types';

type ProjectFilterKey = 'all' | 'unassigned' | `project:${number}`;
type PublishFilter = 'ALL' | 'DRAFT' | 'PUBLISHED';

const projectKey = (projectId: number): ProjectFilterKey =>
  `project:${projectId}`;

const projectIdFromKey = (key: ProjectFilterKey): number | undefined => {
  if (!key.startsWith('project:')) return undefined;
  const value = Number(key.substring('project:'.length));
  return Number.isFinite(value) && value > 0 ? value : undefined;
};

const formatTime = (value?: string) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';

export default function DataDevelopmentPage() {
  const { projects, currentProject } = useSecurityProject();
  const [tasks, setTasks] = useState<DevelopmentTaskRow[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<'ALL' | DevelopmentTaskType>('ALL');
  const [publishFilter, setPublishFilter] = useState<PublishFilter>('ALL');
  const [selectedProject, setSelectedProject] = useState<ProjectFilterKey>(() =>
    currentProject?.id ? projectKey(Number(currentProject.id)) : 'all',
  );

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [taskResponse, dataSourceResponse] = await Promise.all([
        listSqlTasks(),
        fetchDataSourceAll(),
      ]);
      if (taskResponse?.code !== API_SUCCESS_CODE) {
        throw new Error(taskResponse?.message || taskResponse?.msg || '查询数据开发任务失败');
      }
      if (dataSourceResponse?.code !== API_SUCCESS_CODE) {
        throw new Error(
          dataSourceResponse?.message || dataSourceResponse?.msg || '查询数据源失败',
        );
      }
      setTasks(
        (taskResponse.data || []).map((task) => ({
          ...task,
          type: 'SQL' as const,
        })),
      );
      setDataSources(dataSourceResponse.data?.bizData || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '查询数据开发任务失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const projectNameMap = useMemo(
    () => new Map(projects.map((project) => [Number(project.id), project.projectName])),
    [projects],
  );
  const dataSourceMap = useMemo(
    () => new Map(dataSources.map((item) => [Number(item.id), item])),
    [dataSources],
  );

  const counts = useMemo(() => {
    const byProject = new Map<number, number>();
    let unassigned = 0;
    tasks.forEach((task) => {
      if (!task.projectId) {
        unassigned += 1;
        return;
      }
      byProject.set(task.projectId, (byProject.get(task.projectId) || 0) + 1);
    });
    return { byProject, unassigned };
  }, [tasks]);

  const treeData = useMemo(
    () => [
      {
        key: 'all',
        title: (
          <span className="flex w-full items-center justify-between gap-3">
            <span className="flex items-center gap-2">
              <FolderOpen size={15} />
              全部任务
            </span>
            <span className="text-[11px] text-[rgba(22,24,35,.34)]">{tasks.length}</span>
          </span>
        ),
      },
      {
        key: 'projects-root',
        selectable: false,
        title: (
          <span className="flex items-center gap-2 text-[12px] font-medium text-[rgba(22,24,35,.5)]">
            <Folder size={14} />
            项目
          </span>
        ),
        children: projects.map((project) => ({
          key: projectKey(Number(project.id)),
          title: (
            <span className="flex w-full items-center justify-between gap-3">
              <span className="truncate">{project.projectName}</span>
              <span className="text-[11px] text-[rgba(22,24,35,.34)]">
                {counts.byProject.get(Number(project.id)) || 0}
              </span>
            </span>
          ),
        })),
      },
      ...(counts.unassigned > 0
        ? [
            {
              key: 'unassigned',
              title: (
                <span className="flex w-full items-center justify-between gap-3">
                  <span className="text-[rgba(22,24,35,.55)]">未归属项目</span>
                  <span className="text-[11px] text-[rgba(22,24,35,.34)]">
                    {counts.unassigned}
                  </span>
                </span>
              ),
            },
          ]
        : []),
    ],
    [counts, projects, tasks.length],
  );

  const filteredTasks = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    const selectedProjectId = projectIdFromKey(selectedProject);
    return tasks.filter((task) => {
      if (selectedProject === 'unassigned' && task.projectId) return false;
      if (selectedProjectId && task.projectId !== selectedProjectId) return false;
      if (typeFilter !== 'ALL' && task.type !== typeFilter) return false;
      if (publishFilter === 'DRAFT' && task.latestVersionNo > 0) return false;
      if (publishFilter === 'PUBLISHED' && task.latestVersionNo <= 0) return false;
      if (
        normalizedKeyword &&
        !`${task.name} ${task.description || ''}`.toLowerCase().includes(normalizedKeyword)
      ) {
        return false;
      }
      return true;
    });
  }, [keyword, publishFilter, selectedProject, tasks, typeFilter]);

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
      title: '所属项目',
      dataIndex: 'projectId',
      width: 170,
      render: (value?: number) =>
        value ? projectNameMap.get(Number(value)) || `项目 ${value}` : '未归属',
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

  const selectedProjectId = projectIdFromKey(selectedProject);

  return (
    <section className="m-4 overflow-hidden rounded-xl border border-[#e4e7ec] bg-white">
      <div className="flex h-[calc(100vh-80px)] min-h-[620px]">
        <aside className="w-[228px] shrink-0 border-r border-[#e4e7ec] bg-[#fcfcfd]">
          <div className="border-b border-[#eceef1] px-4 py-4">
            <div className="text-[13px] font-semibold text-[#161823]">项目视图</div>
            <div className="mt-1 text-[11px] text-[rgba(22,24,35,.4)]">
              按项目组织数据开发任务
            </div>
          </div>
          <div className="p-2">
            <Tree
              blockNode
              defaultExpandAll
              selectedKeys={[selectedProject]}
              treeData={treeData}
              onSelect={(keys) => {
                const key = keys[0];
                if (key && key !== 'projects-root') {
                  setSelectedProject(String(key) as ProjectFilterKey);
                }
              }}
            />
          </div>
        </aside>

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
              onClick={() => void load()}
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
              scroll={{ x: 1120 }}
              locale={{
                emptyText: (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="当前项目暂无数据开发任务"
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
          selectedProjectId ??
          (currentProject?.id ? Number(currentProject.id) : undefined)
        }
        onCancel={() => setCreateOpen(false)}
        onNext={(type, projectId) => {
          setCreateOpen(false);
          history.push(
            `/data-development/task/new?type=${encodeURIComponent(type)}&projectId=${projectId}`,
          );
        }}
      />
    </section>
  );
}
