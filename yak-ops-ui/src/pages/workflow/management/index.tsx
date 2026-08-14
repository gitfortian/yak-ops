import {
  createWorkflowDefinition,
  deleteWorkflowDefinition,
  listWorkflowDefinitions,
  offlineWorkflowDefinition,
  onlineWorkflowDefinition,
  pauseWorkflowDefinition,
  resumeWorkflowDefinition,
  runWorkflowDefinition,
  type WorkflowDefinition,
  type WorkflowDefinitionStatus,
} from '@/services/workflow/definitions';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  Button,
  ConfigProvider,
  Drawer,
  Dropdown,
  Empty,
  Form,
  Input,
  Modal,
  Pagination,
  Spin,
  Tooltip,
  message,
} from 'antd';
import {
  CirclePause,
  CirclePlay,
  CloudOff,
  CloudUpload,
  Copy,
  GitBranch,
  MoreHorizontal,
  Pencil,
  Trash2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

type FilterKey = 'ALL' | WorkflowDefinitionStatus;

interface StatusMeta {
  label: string;
  className: string;
  dotClassName: string;
}

const DEFINITION_STATUS: Record<WorkflowDefinitionStatus, StatusMeta> = {
  DRAFT: {
    label: '草稿',
    className: 'border-[#e4e7ec] bg-[#f7f7f8] text-[#667085]',
    dotClassName: 'bg-[#98a2b3]',
  },
  ONLINE: {
    label: '已上线',
    className: 'border-[#ffd8e0] bg-[#fff4f6] text-[#e5254e]',
    dotClassName: 'bg-[#fe2c55]',
  },
  OFFLINE: {
    label: '已下线',
    className: 'border-[#e4e7ec] bg-[#f5f5f6] text-[#667085]',
    dotClassName: 'bg-[#98a2b3]',
  },
};

const RUNTIME_LABEL: Record<string, string> = {
  CREATED: '已创建',
  WAITING: '等待中',
  READY: '就绪',
  SUBMITTED: '待执行',
  RUNNING: '运行中',
  PAUSING: '暂停中',
  PAUSED: '已暂停',
  RESUMING: '恢复中',
  SUCCESS: '成功',
  SUCCESS_WITH_WARNINGS: '完成（有告警）',
  FAILED: '失败',
  WARNING: '告警',
  CANCELED: '已取消',
  TIMED_OUT: '已超时',
};

const ACTIVE_RUNTIME_STATUSES = new Set([
  'CREATED',
  'WAITING',
  'READY',
  'SUBMITTED',
  'RUNNING',
  'PAUSING',
  'PAUSED',
  'RESUMING',
]);

const RUNNING_RUNTIME_STATUSES = new Set([
  'CREATED',
  'WAITING',
  'READY',
  'SUBMITTED',
  'RUNNING',
  'PAUSING',
  'RESUMING',
]);

const isActiveRuntime = (status?: string) =>
  Boolean(status && ACTIVE_RUNTIME_STATUSES.has(status));

const isRunningRuntime = (status?: string) =>
  Boolean(status && RUNNING_RUNTIME_STATUSES.has(status));

const formatTime = (value?: string) => {
  if (!value) return '-';

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  return date.toLocaleString();
};

const runtimeStatusClassName = (status?: string) => {
  switch (status) {
    case 'RUNNING':
    case 'RESUMING':
    case 'PAUSING':
      return 'text-[#fe2c55]';
    case 'FAILED':
    case 'TIMED_OUT':
      return 'text-[#d92d20]';
    case 'SUCCESS':
    case 'SUCCESS_WITH_WARNINGS':
      return 'text-[#344054]';
    case 'PAUSED':
      return 'text-[#667085]';
    default:
      return 'text-[#98a2b3]';
  }
};

const WorkflowTopologyPreview = ({ definition }: { definition: WorkflowDefinition }) => {
  const nodes = definition.nodes || [];
  const edges = definition.edges || [];

  if (!nodes.length) {
    return (
      <div className="flex h-full min-h-[112px] flex-col items-center justify-center gap-2 rounded-[10px] border border-dashed border-[#e4e7ec] bg-white text-[#98a2b3]">
        <GitBranch size={24} strokeWidth={1.6} />
        <span className="text-[11px]">尚未配置节点</span>
      </div>
    );
  }

  const minX = Math.min(...nodes.map((node) => node.positionX || 0));
  const maxX = Math.max(...nodes.map((node) => node.positionX || 0));
  const minY = Math.min(...nodes.map((node) => node.positionY || 0));
  const maxY = Math.max(...nodes.map((node) => node.positionY || 0));
  const spanX = Math.max(maxX - minX, 1);
  const spanY = Math.max(maxY - minY, 1);
  const positions = new Map(
    nodes.map((node) => [
      node.id,
      {
        x: 18 + ((node.positionX - minX) / spanX) * 132,
        y: 18 + ((node.positionY - minY) / spanY) * 60,
      },
    ]),
  );

  return (
    <div className="relative h-full min-h-[112px] overflow-hidden rounded-[10px] border border-[#eaecf0] bg-white">
      <div className="absolute left-2.5 top-2 z-10 rounded bg-[#f5f5f6] px-1.5 py-0.5 text-[10px] font-medium text-[#667085]">
        DAG
      </div>
      <svg
        viewBox="0 0 168 96"
        className="absolute inset-0 h-full w-full"
        role="img"
        aria-label={`${definition.name} 工作流拓扑预览`}
      >
        {edges.map((edge, index) => {
          const source = positions.get(edge.source);
          const target = positions.get(edge.target);
          if (!source || !target) return null;

          return (
            <line
              key={`${edge.source}-${edge.target}-${index}`}
              x1={source.x}
              y1={source.y}
              x2={target.x}
              y2={target.y}
              stroke="#d0d5dd"
              strokeWidth="1.4"
            />
          );
        })}
        {nodes.map((node, index) => {
          const position = positions.get(node.id);
          if (!position) return null;

          return (
            <g key={node.id}>
              <rect
                x={position.x - 6}
                y={position.y - 5}
                width="12"
                height="10"
                rx="3"
                fill={index === 0 ? '#fe2c55' : '#ffffff'}
                stroke={index === 0 ? '#fe2c55' : '#98a2b3'}
                strokeWidth="1.2"
              />
            </g>
          );
        })}
      </svg>
      <div className="absolute bottom-2 right-2 rounded bg-white/90 px-1.5 py-0.5 text-[10px] text-[#98a2b3]">
        {definition.nodeCount} 节点 · {definition.edgeCount} 连线
      </div>
    </div>
  );
};

const WorkflowManagementPage = () => {
  const [definitions, setDefinitions] = useState<WorkflowDefinition[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionId, setActionId] = useState<string>();
  const [filter, setFilter] = useState<FilterKey>('ALL');
  const [keywordDraft, setKeywordDraft] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);

  const [form] = Form.useForm<{
    name: string;
    description?: string;
  }>();

  const loadDefinitions = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);

    try {
      const data = await listWorkflowDefinitions();
      setDefinitions(data || []);
    } catch (error) {
      if (!silent) {
        message.error(
          error instanceof Error ? error.message : '工作流加载失败',
        );
      }
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDefinitions();
  }, [loadDefinitions]);

  useEffect(() => {
    const hasActiveExecution = definitions.some((item) =>
      isActiveRuntime(item.latestExecutionStatus),
    );

    if (!hasActiveExecution) return;

    const timer = window.setInterval(() => {
      void loadDefinitions(true);
    }, 1800);

    return () => window.clearInterval(timer);
  }, [definitions, loadDefinitions]);

  const counts = useMemo(
    () => ({
      ALL: definitions.length,
      ONLINE: definitions.filter((item) => item.status === 'ONLINE').length,
      DRAFT: definitions.filter((item) => item.status === 'DRAFT').length,
      OFFLINE: definitions.filter((item) => item.status === 'OFFLINE').length,
    }),
    [definitions],
  );

  const filterTabs: Array<{ key: FilterKey; label: string }> = [
    { key: 'ALL', label: '全部' },
    { key: 'ONLINE', label: '已上线' },
    { key: 'DRAFT', label: '草稿' },
    { key: 'OFFLINE', label: '已下线' },
  ];

  const filteredDefinitions = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();

    return definitions.filter((item) => {
      if (filter !== 'ALL' && item.status !== filter) return false;
      if (!normalizedKeyword) return true;

      return (
        item.name.toLowerCase().includes(normalizedKeyword) ||
        (item.description || '').toLowerCase().includes(normalizedKeyword) ||
        item.id.toLowerCase().includes(normalizedKeyword)
      );
    });
  }, [definitions, filter, keyword]);

  const pagedDefinitions = useMemo(() => {
    const start = (page - 1) * pageSize;
    return filteredDefinitions.slice(start, start + pageSize);
  }, [filteredDefinitions, page, pageSize]);

  useEffect(() => {
    const maxPage = Math.max(1, Math.ceil(filteredDefinitions.length / pageSize));
    if (page > maxPage) setPage(maxPage);
  }, [filteredDefinitions.length, page, pageSize]);

  const executeAction = async (
    id: string,
    action: () => Promise<WorkflowDefinition>,
    success: string,
  ) => {
    if (actionId) return;

    setActionId(id);
    try {
      await action();
      message.success(success);
      await loadDefinitions(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    } finally {
      setActionId(undefined);
    }
  };

  const handleSearch = () => {
    setKeyword(keywordDraft.trim());
    setPage(1);
  };

  const handleTabChange = (nextFilter: FilterKey) => {
    setFilter(nextFilter);
    setPage(1);
  };

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setCreating(true);

      const created = await createWorkflowDefinition({
        name: values.name.trim(),
        description: values.description?.trim() || undefined,
      });

      message.success('工作流草稿已创建，请继续配置任务节点');
      setCreateOpen(false);
      form.resetFields();
      history.push(`/workflow/definition/${created.id}?scene=create`);
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(
        error instanceof Error ? error.message : '创建工作流失败',
      );
    } finally {
      setCreating(false);
    }
  };

  const handleCreateClose = () => {
    if (creating) return;
    setCreateOpen(false);
    form.resetFields();
  };

  const handleDelete = (record: WorkflowDefinition) => {
    Modal.confirm({
      centered: true,
      title: '确认删除工作流吗？',
      content: (
        <div className="text-[13px] leading-6 text-[rgba(22,24,35,.58)]">
          即将删除工作流
          <span className="mx-1 font-semibold text-[#fe2c55]">
            [{record.name}]
          </span>
          。
          <br />
          删除后无法恢复，请谨慎操作。
        </div>
      ),
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true, size: 'small' },
      cancelButtonProps: { size: 'small' },
      maskClosable: true,
      async onOk() {
        try {
          await deleteWorkflowDefinition(record.id);
          message.success('工作流已删除');
          await loadDefinitions(true);
        } catch (error) {
          message.error(
            error instanceof Error ? error.message : '删除工作流失败',
          );
        }
      },
    });
  };

  const goToDefinition = (record: WorkflowDefinition) => {
    history.push(`/workflow/definition/${record.id}?scene=edit`);
  };

  const handleMoreAction = async (key: string, record: WorkflowDefinition) => {
    if (key === 'copy-id') {
      try {
        await navigator.clipboard.writeText(record.id);
        message.success('工作流 ID 已复制');
      } catch {
        message.warning('复制失败，请手动复制工作流 ID');
      }
      return;
    }

    if (key === 'online') {
      await executeAction(
        record.id,
        () => onlineWorkflowDefinition(record.id),
        '工作流已上线',
      );
      return;
    }

    if (key === 'offline') {
      await executeAction(
        record.id,
        () => offlineWorkflowDefinition(record.id),
        '工作流已下线',
      );
      return;
    }

    if (key === 'delete') handleDelete(record);
  };

  const renderStatus = (status: WorkflowDefinitionStatus) => {
    const meta = DEFINITION_STATUS[status];

    return (
      <span
        className={[
          'inline-flex h-6 items-center gap-1.5 rounded-full border px-2.5',
          'text-[11px] font-medium',
          meta.className,
        ].join(' ')}
      >
        <span className={['h-1.5 w-1.5 rounded-full', meta.dotClassName].join(' ')} />
        {meta.label}
      </span>
    );
  };

  const renderActions = (record: WorkflowDefinition) => {
    const busy = actionId === record.id;
    const runtimeStatus = record.latestExecutionStatus;
    const active = isActiveRuntime(runtimeStatus);
    const running = isRunningRuntime(runtimeStatus);
    const paused = runtimeStatus === 'PAUSED';
    const actionButtonClass = [
      '!h-8 !rounded-md !px-2.5',
      '!text-[12px] !text-[#667085]',
      'hover:!bg-[#f5f5f6] hover:!text-[#344054]',
    ].join(' ');

    const moreItems = [
      ...(record.status !== 'ONLINE'
        ? [
            {
              key: 'online',
              label: '上线工作流',
              icon: <CloudUpload size={14} strokeWidth={1.8} />,
              disabled: busy,
            },
          ]
        : [
            {
              key: 'offline',
              label: '下线工作流',
              icon: <CloudOff size={14} strokeWidth={1.8} />,
              disabled: busy || active,
            },
          ]),
      {
        key: 'copy-id',
        label: '复制工作流 ID',
        icon: <Copy size={14} strokeWidth={1.8} />,
      },
      ...(record.status !== 'ONLINE' && !active
        ? [
            { type: 'divider' as const },
            {
              key: 'delete',
              label: '删除工作流',
              danger: true,
              icon: <Trash2 size={14} strokeWidth={1.8} />,
            },
          ]
        : []),
    ];

    return (
      <div className="flex shrink-0 items-center gap-0.5 whitespace-nowrap">
        <Button
          type="text"
          size="small"
          icon={<Pencil size={13} strokeWidth={1.9} />}
          className={actionButtonClass}
          onClick={() => goToDefinition(record)}
        >
          {record.status === 'ONLINE' ? '查看' : '编辑'}
        </Button>

        {record.status === 'ONLINE' && !active && (
          <Button
            type="text"
            size="small"
            loading={busy}
            icon={<CirclePlay size={13} strokeWidth={1.9} />}
            className={actionButtonClass}
            onClick={() =>
              void executeAction(
                record.id,
                () => runWorkflowDefinition(record.id),
                '工作流已启动',
              )
            }
          >
            运行一次
          </Button>
        )}

        {record.status === 'ONLINE' && running && (
          <Button
            type="text"
            size="small"
            loading={busy}
            icon={<CirclePause size={13} strokeWidth={1.9} />}
            className={actionButtonClass}
            onClick={() =>
              void executeAction(
                record.id,
                () => pauseWorkflowDefinition(record.id),
                '已请求暂停工作流',
              )
            }
          >
            暂停
          </Button>
        )}

        {record.status === 'ONLINE' && paused && (
          <Button
            type="text"
            size="small"
            loading={busy}
            icon={<CirclePlay size={13} strokeWidth={1.9} />}
            className={actionButtonClass}
            onClick={() =>
              void executeAction(
                record.id,
                () => resumeWorkflowDefinition(record.id),
                '工作流已恢复',
              )
            }
          >
            恢复
          </Button>
        )}

        <Dropdown
          trigger={['click']}
          placement="bottomRight"
          menu={{
            items: moreItems,
            onClick: ({ key }) => void handleMoreAction(key, record),
          }}
        >
          <Button
            type="text"
            size="small"
            icon={<MoreHorizontal size={15} strokeWidth={1.9} />}
            className={actionButtonClass}
          >
            更多
          </Button>
        </Dropdown>
      </div>
    );
  };

  const renderMetric = (
    label: string,
    value: string | number,
    valueClassName = 'text-[#344054]',
  ) => (
    <div className="min-w-0 flex-1 px-4 first:pl-0 last:pr-0">
      <div className="text-[11px] leading-5 text-[#98a2b3]">{label}</div>
      <div className={`mt-0.5 truncate text-[12px] font-medium leading-5 ${valueClassName}`}>
        {value}
      </div>
    </div>
  );

  return (
    <ConfigProvider
      theme={{
        token: {
          borderRadius: 10,
          colorBorder: '#f0f0f0',
          colorBgContainer: '#ffffff',
        },
        components: {
          Button: { borderRadius: 8 },
          Input: { activeShadow: 'none' },
        },
      }}
    >
      <div className="flex min-h-[calc(100vh-64px)] flex-col bg-white px-5 pt-4 text-[#161823]">
        <h1 className="m-0 text-[17px] font-semibold leading-8 text-[#161823]">
          工作流定义
        </h1>

        <div className="mt-2 flex min-h-[58px] items-center justify-between gap-5 border-b border-[#f0f0f0]">
          <div className="flex shrink-0 items-center gap-2">
            <button
              type="button"
              className="h-8 rounded-lg bg-[#f1f2f4] px-3 text-[12px] font-semibold text-[#161823]"
            >
              工作流 ({definitions.length})
            </button>
          </div>

          <div className="flex min-w-0 flex-1 items-center justify-end gap-3 overflow-x-auto py-2">
            <div className="flex shrink-0 items-center text-[12px]">
              {filterTabs.map((item, index) => {
                const active = filter === item.key;
                return (
                  <div key={item.key} className="flex items-center">
                    {index > 0 && <span className="mx-2 h-3 w-px bg-[#e4e7ec]" />}
                    <button
                      type="button"
                      onClick={() => handleTabChange(item.key)}
                      className={[
                        'border-0 bg-transparent p-0 transition-colors',
                        active
                          ? 'font-semibold text-[#161823]'
                          : 'font-normal text-[#98a2b3] hover:text-[#475467]',
                      ].join(' ')}
                    >
                      {item.label}
                      <span className="ml-1 text-[10px] text-[#98a2b3]">
                        {counts[item.key]}
                      </span>
                    </button>
                  </div>
                );
              })}
            </div>

            <Input
              allowClear
              variant="filled"
              value={keywordDraft}
              prefix={<SearchOutlined className="text-[#98a2b3]" />}
              placeholder="搜索工作流"
              className="!h-9 !w-[240px] !min-w-[200px]"
              onChange={(event) => setKeywordDraft(event.target.value)}
              onPressEnter={handleSearch}
            />

            <Button size="small" className="!h-9 !px-3" onClick={handleSearch}>
              查询
            </Button>

            <Tooltip title="刷新">
              <Button
                size="small"
                icon={<ReloadOutlined spin={loading} />}
                className="!h-9 !w-9 !px-0"
                disabled={loading}
                onClick={() => void loadDefinitions()}
              />
            </Tooltip>

            <Button
              danger
              type="primary"
              size="small"
              className="!h-9 !px-4"
              onClick={() => setCreateOpen(true)}
            >
              新建工作流
            </Button>
          </div>
        </div>

        <div className="flex-1 py-4">
          <Spin spinning={loading}>
            {pagedDefinitions.length ? (
              <div className="space-y-3">
                {pagedDefinitions.map((record) => (
                  <div
                    key={record.id}
                    className="overflow-x-auto rounded-[12px] border border-[#eef0f2] bg-[#fcfcfd] transition-shadow hover:shadow-[0_3px_14px_rgba(16,24,40,.05)]"
                  >
                    <div className="flex min-w-[900px] gap-4 px-4 py-4">
                      <div className="w-[176px] shrink-0 self-stretch">
                        <WorkflowTopologyPreview definition={record} />
                      </div>

                      <div className="min-w-0 flex-1">
                        <div className="flex min-h-8 items-start justify-between gap-4">
                          <div className="min-w-0 flex-1">
                            <div className="flex min-w-0 items-center gap-2">
                              <button
                                type="button"
                                title={record.name}
                                onClick={() => goToDefinition(record)}
                                className="min-w-0 truncate border-0 bg-transparent p-0 text-left text-[14px] font-semibold leading-6 text-[#161823] transition-colors hover:text-[#fe2c55]"
                              >
                                {record.name || '未命名工作流'}
                              </button>
                              {renderStatus(record.status)}
                              {record.draftChanged && (
                                <span className="rounded bg-[#fff7e6] px-1.5 py-0.5 text-[10px] text-[#ad6800]">
                                  有未发布变更
                                </span>
                              )}
                            </div>

                            <div
                              title={record.id}
                              className="mt-0.5 truncate text-[10px] leading-5 text-[#98a2b3]"
                            >
                              ID：{record.id}
                            </div>
                          </div>

                          {renderActions(record)}
                        </div>

                        <div
                          title={record.description || ''}
                          className="mt-2 line-clamp-2 min-h-5 text-[12px] leading-5 text-[#667085]"
                        >
                          {record.description || '暂无工作流描述'}
                        </div>

                        <div className="mt-4 flex items-stretch divide-x divide-[#eaecf0] border-t border-[#f0f1f3] pt-3">
                          {renderMetric('节点', record.nodeCount)}
                          {renderMetric('连线', record.edgeCount)}
                          {renderMetric('最新版本', `V${record.latestVersionNo || 0}`)}
                          {renderMetric(
                            '生效版本',
                            record.activeVersionNo ? `V${record.activeVersionNo}` : '暂无',
                            record.activeVersionNo ? 'text-[#344054]' : 'text-[#98a2b3]',
                          )}
                          {renderMetric(
                            '运行状态',
                            record.latestExecutionStatus
                              ? RUNTIME_LABEL[record.latestExecutionStatus] || record.latestExecutionStatus
                              : '尚未运行',
                            runtimeStatusClassName(record.latestExecutionStatus),
                          )}
                          {renderMetric('更新时间', formatTime(record.updateTime), 'text-[#667085]')}
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex min-h-[320px] items-center justify-center">
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={
                    <span className="text-[12px] text-[#98a2b3]">
                      {keyword || filter !== 'ALL'
                        ? '暂无符合条件的工作流'
                        : '暂无工作流定义'}
                    </span>
                  }
                >
                  {!keyword && filter === 'ALL' && (
                    <Button
                      danger
                      type="primary"
                      size="small"
                      onClick={() => setCreateOpen(true)}
                    >
                      新建工作流
                    </Button>
                  )}
                </Empty>
              </div>
            )}
          </Spin>

          {filteredDefinitions.length > 0 && (
            <div className="mt-5 flex items-center justify-between border-t border-[#f5f5f5] py-4">
              <span className="text-[11px] text-[#98a2b3]">
                共 {filteredDefinitions.length} 个工作流
              </span>
              <Pagination
                current={page}
                pageSize={pageSize}
                total={filteredDefinitions.length}
                showSizeChanger
                pageSizeOptions={[10, 20, 50]}
                size="small"
                onChange={(nextPage, nextPageSize) => {
                  setPage(nextPageSize !== pageSize ? 1 : nextPage);
                  setPageSize(nextPageSize);
                }}
              />
            </div>
          )}

          {!loading && filteredDefinitions.length > 0 && filteredDefinitions.length <= pageSize && (
            <div className="pb-6 pt-1 text-center text-[11px] text-[#b4bac3]">
              没有更多工作流
            </div>
          )}
        </div>

        <Drawer
          open={createOpen}
          width={560}
          placement="right"
          closable={false}
          destroyOnClose
          maskClosable={!creating}
          keyboard={!creating}
          onClose={handleCreateClose}
          title={
            <div>
              <div className="text-[18px] font-semibold leading-7 text-[#101828]">
                新建工作流
              </div>
              <div className="mt-1 text-[11px] font-normal text-[rgba(22,24,35,.42)]">
                第一步创建基础信息，下一步进入画布完成任务编排
              </div>
            </div>
          }
          extra={
            <div className="flex items-center gap-2">
              <Button
                disabled={creating}
                onClick={handleCreateClose}
                className="!h-9 !rounded-lg !px-4"
              >
                取消
              </Button>
              <Button
                type="primary"
                loading={creating}
                onClick={() => void handleCreate()}
                className="!h-9 !rounded-lg !px-5 !text-white"
              >
                创建并配置
              </Button>
            </div>
          }
          styles={{
            header: {
              padding: '18px 24px',
              borderBottom: '1px solid #eaecf0',
            },
            body: { padding: 24 },
          }}
        >
          <Form form={form} layout="vertical" requiredMark="optional">
            <Form.Item
              name="name"
              label="工作流名称"
              rules={[
                { required: true, message: '请输入工作流名称' },
                { max: 100, message: '名称不能超过 100 个字符' },
              ]}
            >
              <Input
                variant="filled"
                placeholder="例如：每日订单同步工作流"
              />
            </Form.Item>

            <Form.Item
              name="description"
              label="工作流描述"
              rules={[
                { max: 500, message: '描述不能超过 500 个字符' },
              ]}
            >
              <Input.TextArea
                variant="filled"
                rows={4}
                placeholder="简单说明这个工作流负责什么"
              />
            </Form.Item>

            <div className="mt-2 rounded-[9px] border border-[rgba(22,24,35,.055)] bg-[#f8f9fa] p-4">
              <div className="flex items-center gap-2 text-[12px] font-semibold text-[#161823]">
                <GitBranch
                  size={15}
                  strokeWidth={1.9}
                  className="text-[#667085]"
                />
                创建后进入工作流配置
              </div>
              <div className="mt-2 text-[11px] leading-5 text-[rgba(22,24,35,.48)]">
                在下一步从左侧拖入已经配置好的任务，完成 DAG
                连线，并设置节点重试、超时、触发规则和失败策略。
              </div>
            </div>
          </Form>
        </Drawer>
      </div>
    </ConfigProvider>
  );
};

export default WorkflowManagementPage;
