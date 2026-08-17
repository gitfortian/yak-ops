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
import {
  CopyOutlined,
  DownOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  Button,
  ConfigProvider,
  Divider,
  Drawer,
  Dropdown,
  Empty,
  Form,
  Input,
  Modal,
  Table,
  Tooltip,
  message,
} from 'antd';
import type { MenuProps } from 'antd';
import {
  CirclePause,
  CirclePlay,
  CloudOff,
  CloudUpload,
  GitBranch,
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

const shortId = (value?: string) => {
  if (!value) return '-';
  return value.length > 22 ? `${value.slice(0, 19)}...` : value;
};

const runtimeTone = (status?: string) => {
  if (!status) {
    return {
      className: 'border-[#e4e7ec] bg-[#f7f7f8] text-[#667085]',
      dotClassName: 'bg-[#98a2b3]',
    };
  }

  if (status === 'FAILED' || status === 'TIMED_OUT') {
    return {
      className: 'border-[#fecdca] bg-[#fef3f2] text-[#b42318]',
      dotClassName: 'bg-[#f04438]',
    };
  }

  if (status === 'WARNING' || status === 'SUCCESS_WITH_WARNINGS') {
    return {
      className: 'border-[#fedf89] bg-[#fffaeb] text-[#b54708]',
      dotClassName: 'bg-[#f79009]',
    };
  }

  if (isActiveRuntime(status)) {
    return {
      className: 'border-[#ffd8e0] bg-[#fff4f6] text-[#e5254e]',
      dotClassName: 'bg-[#fe2c55]',
    };
  }

  if (status === 'SUCCESS') {
    return {
      className: 'border-[#d0d5dd] bg-[#f8f9fb] text-[#344054]',
      dotClassName: 'bg-[#667085]',
    };
  }

  return {
    className: 'border-[#e4e7ec] bg-[#f7f7f8] text-[#667085]',
    dotClassName: 'bg-[#98a2b3]',
  };
};

export default function WorkflowDefinitionList() {
  const [definitions, setDefinitions] = useState<WorkflowDefinition[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionId, setActionId] = useState<string>();
  const [filter, setFilter] = useState<FilterKey>('ALL');
  const [keywordDraft, setKeywordDraft] = useState('');
  const [keyword, setKeyword] = useState('');
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
    if (!definitions.some((item) => isActiveRuntime(item.latestExecutionStatus))) {
      return;
    }

    const timer = window.setInterval(() => {
      void loadDefinitions(true);
    }, 1800);

    return () => window.clearInterval(timer);
  }, [definitions, loadDefinitions]);

  const filterTabs: Array<{ key: FilterKey; label: string }> = [
    { key: 'ALL', label: '全部工作流' },
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
        item.id.toLowerCase().includes(normalizedKeyword) ||
        (item.description || '').toLowerCase().includes(normalizedKeyword)
      );
    });
  }, [definitions, filter, keyword]);

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

  const goToDefinition = (record: WorkflowDefinition) => {
    history.push(`/workflow/definition/${record.id}?scene=edit`);
  };

  const copyId = async (id: string) => {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(id);
      } else {
        const textarea = document.createElement('textarea');
        textarea.value = id;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
      }
      message.success('工作流 ID 已复制');
    } catch {
      message.error('复制失败，请手动复制');
    }
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

  const renderDefinitionStatus = (status: WorkflowDefinitionStatus) => {
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

  const renderRuntimeStatus = (status?: string) => {
    const tone = runtimeTone(status);

    return (
      <span
        className={[
          'inline-flex h-6 items-center gap-1.5 rounded-md border px-2',
          'text-[11px] font-medium',
          tone.className,
        ].join(' ')}
      >
        <span className={['h-1.5 w-1.5 rounded-full', tone.dotClassName].join(' ')} />
        {status ? RUNTIME_LABEL[status] || status : '尚未运行'}
      </span>
    );
  };

  const handleMoreAction = (key: string, record: WorkflowDefinition) => {
    switch (key) {
      case 'edit':
        goToDefinition(record);
        break;
      case 'online':
        void executeAction(
          record.id,
          () => onlineWorkflowDefinition(record.id),
          '工作流已上线',
        );
        break;
      case 'offline':
        void executeAction(
          record.id,
          () => offlineWorkflowDefinition(record.id),
          '工作流已下线',
        );
        break;
      case 'delete':
        handleDelete(record);
        break;
      default:
        break;
    }
  };

  const getMoreMenuItems = (record: WorkflowDefinition): MenuProps['items'] => {
    const active = isActiveRuntime(record.latestExecutionStatus);
    const items: MenuProps['items'] = [
      {
        key: 'edit',
        icon: <Pencil size={14} strokeWidth={1.9} />,
        label: record.status === 'ONLINE' ? '查看配置' : '编辑配置',
      },
    ];

    if (record.status !== 'ONLINE') {
      items.push({
        key: 'online',
        icon: <CloudUpload size={14} strokeWidth={1.9} />,
        label: '上线',
      });
    } else {
      items.push({
        key: 'offline',
        icon: <CloudOff size={14} strokeWidth={1.9} />,
        label: active ? '下线（存在活动执行）' : '下线',
        disabled: active,
      });
    }

    if (record.status !== 'ONLINE' && !active) {
      items.push({ type: 'divider' });
      items.push({
        key: 'delete',
        icon: <Trash2 size={14} strokeWidth={1.9} />,
        label: '删除',
        danger: true,
      });
    }

    return items;
  };

  const renderPrimaryAction = (record: WorkflowDefinition) => {
    const busy = actionId === record.id;
    const runtimeStatus = record.latestExecutionStatus;

    if (record.status !== 'ONLINE') {
      return (
        <Button
          size="small"
          icon={<Pencil size={13} strokeWidth={1.9} />}
          className="!h-7 !rounded-md !px-2.5 !text-[12px]"
          onClick={() => goToDefinition(record)}
        >
          编辑
        </Button>
      );
    }

    if (runtimeStatus === 'PAUSED') {
      return (
        <Button
          size="small"
          loading={busy}
          icon={<CirclePlay size={13} strokeWidth={1.9} />}
          className="!h-7 !rounded-md !px-2.5 !text-[12px]"
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
      );
    }

    if (isRunningRuntime(runtimeStatus)) {
      return (
        <Button
          size="small"
          loading={busy}
          icon={<CirclePause size={13} strokeWidth={1.9} />}
          className="!h-7 !rounded-md !px-2.5 !text-[12px]"
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
      );
    }

    return (
      <Button
        size="small"
        loading={busy}
        icon={<CirclePlay size={13} strokeWidth={1.9} />}
        className="!h-7 !rounded-md !px-2.5 !text-[12px]"
        onClick={() =>
          void executeAction(
            record.id,
            () => runWorkflowDefinition(record.id),
            '工作流已启动',
          )
        }
      >
        运行
      </Button>
    );
  };

  const columns = [
    {
      title: '名称 / ID',
      dataIndex: 'name',
      width: 250,
      render: (_value: string, record: WorkflowDefinition) => (
        <div className="min-w-0 py-0.5">
          <button
            type="button"
            title={record.name}
            onClick={() => goToDefinition(record)}
            className={[
              'block max-w-full truncate border-0 bg-transparent p-0 text-left',
              'text-[13px] font-medium leading-5 text-[#344054]',
              'transition-colors hover:text-[#fe2c55]',
            ].join(' ')}
          >
            {record.name || '未命名工作流'}
          </button>

          <div className="mt-0.5 flex h-5 min-w-0 items-center gap-1 text-[11px] leading-5 text-[#98a2b3]">
            <span className="truncate" title={record.id}>
              ID：{record.id}
            </span>
            <Tooltip title="复制工作流 ID">
              <Button
                type="text"
                size="small"
                icon={<CopyOutlined className="text-[11px]" />}
                className="!flex !h-5 !w-5 !min-w-0 !items-center !justify-center !p-0 !text-[#98a2b3] hover:!bg-[#f2f4f7] hover:!text-[#475467]"
                onClick={(event) => {
                  event.stopPropagation();
                  void copyId(record.id);
                }}
              />
            </Tooltip>
          </div>
        </div>
      ),
    },
    {
      title: '工作流概况',
      dataIndex: 'overview',
      width: 320,
      render: (_value: unknown, record: WorkflowDefinition) => (
        <div className="min-w-0 py-0.5 text-[12px] leading-5 text-[#667085]">
          <div
            title={record.description || ''}
            className="truncate text-[#475467]"
          >
            {record.description || '暂无工作流描述'}
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-[#98a2b3]">
            <span>{record.nodeCount} 个节点</span>
            <span>{record.edgeCount} 条连线</span>
            <span>
              {record.workflowTimeoutSeconds > 0
                ? `超时 ${record.workflowTimeoutSeconds} 秒`
                : '未设置工作流超时'}
            </span>
          </div>
        </div>
      ),
    },
    {
      title: '发布信息',
      dataIndex: 'status',
      width: 210,
      render: (_value: WorkflowDefinitionStatus, record: WorkflowDefinition) => (
        <div className="py-0.5">
          <div className="flex flex-wrap items-center gap-1.5">
            {renderDefinitionStatus(record.status)}
            {record.draftChanged && record.latestVersionNo > 0 && (
              <span className="inline-flex h-6 items-center rounded-md bg-[#fff7e6] px-2 text-[11px] font-medium text-[#b54708]">
                有未发布变更
              </span>
            )}
          </div>
          <div className="mt-1.5 text-[11px] leading-5 text-[#98a2b3]">
            <span>最新 V{record.latestVersionNo || 0}</span>
            <span className="mx-1.5 text-[#d0d5dd]">·</span>
            <span>
              {record.activeVersionNo
                ? `生效 V${record.activeVersionNo}`
                : '暂无生效版本'}
            </span>
          </div>
        </div>
      ),
    },
    {
      title: '执行概况',
      dataIndex: 'latestExecutionStatus',
      width: 210,
      render: (_value: string | undefined, record: WorkflowDefinition) => (
        <div className="py-0.5">
          {renderRuntimeStatus(record.latestExecutionStatus)}
          <div
            title={record.latestExecutionId || ''}
            className="mt-1.5 truncate text-[11px] leading-5 text-[#98a2b3]"
          >
            最近执行：{shortId(record.latestExecutionId)}
          </div>
        </div>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 175,
      render: (_value: string, record: WorkflowDefinition) => (
        <div className="text-[12px] leading-5 text-[#667085]">
          <div className="whitespace-nowrap">{formatTime(record.updateTime)}</div>
          <div className="whitespace-nowrap text-[11px] text-[#98a2b3]">
            创建：{formatTime(record.createTime)}
          </div>
        </div>
      ),
    },
    {
      title: '操作',
      dataIndex: 'operate',
      width: 170,
      fixed: 'right' as const,
      render: (_value: unknown, record: WorkflowDefinition) => (
        <div className="flex min-h-7 items-center gap-1.5 whitespace-nowrap">
          {renderPrimaryAction(record)}
          <Dropdown
            trigger={['click']}
            menu={{
              items: getMoreMenuItems(record),
              onClick: ({ key }) => handleMoreAction(key, record),
            }}
          >
            <Button
              type="text"
              size="small"
              disabled={actionId === record.id}
              className="!h-7 !rounded-md !px-2 !text-[12px] !text-[#667085] hover:!bg-[#f5f5f6] hover:!text-[#344054]"
            >
              更多 <DownOutlined className="text-[10px]" />
            </Button>
          </Dropdown>
        </div>
      ),
    },
  ];

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

        <div className="mx-auto flex w-full max-w-full flex-1 flex-col">
          <div className="mb-3">
            <div className="border-b border-[#f0f0f0]">
              <div className="flex min-h-[54px] items-center justify-between gap-4 py-2">
                <div className="flex shrink-0 items-center gap-1 rounded-lg bg-[#f5f5f6] p-1">
                  {filterTabs.map((item) => {
                    const active = filter === item.key;

                    return (
                      <button
                        key={item.key}
                        type="button"
                        onClick={() => setFilter(item.key)}
                        className={[
                          'h-8 rounded-md px-3.5 text-[13px] font-medium transition-all',
                          active
                            ? 'bg-white text-[#fe2c55] shadow-[0_1px_4px_rgba(16,24,40,0.08)]'
                            : 'text-[#667085] hover:bg-white/70 hover:text-[#344054]',
                        ].join(' ')}
                      >
                        {item.label}
                      </button>
                    );
                  })}
                </div>

                <div className="flex min-w-0 flex-1 items-center justify-end gap-2 overflow-x-auto">
                  <Input
                    allowClear
                    variant="filled"
                    value={keywordDraft}
                    prefix={<SearchOutlined className="text-[#98a2b3]" />}
                    placeholder="搜索工作流名称、描述或 ID"
                    className="!h-9 !w-[300px] !min-w-[220px]"
                    onChange={(event) => setKeywordDraft(event.target.value)}
                    onPressEnter={handleSearch}
                  />

                  <Button
                    size="small"
                    className="!h-9 !px-4"
                    onClick={handleSearch}
                  >
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
                </div>
              </div>
            </div>

            <div className="flex min-h-[48px] items-center justify-end">
              <Button
                danger
                type="primary"
                size="small"
                className="!h-7"
                onClick={() => setCreateOpen(true)}
              >
                <span className="text-[13px]">新建工作流</span>
              </Button>
            </div>

            <div className="flex min-h-9 items-center rounded-sm bg-[#fff7e6] px-3 text-[12px] text-[#475467]">
              <span className="mr-2 text-[14px] text-[#faad14]">▲</span>
              <span className="font-medium text-[#344054]">【提示】</span>
              <span>
                工作流需完成任务节点配置并上线后才能运行；存在活动执行时不可直接下线。
              </span>
            </div>
          </div>

          <Divider style={{ marginTop: 4, marginBottom: 16 }} />

          <div className="flex-1">
            <Table
              columns={columns as any}
              dataSource={filteredDefinitions}
              rowKey="id"
              bordered
              size="small"
              loading={loading}
              pagination={{
                pageSize: 10,
                showSizeChanger: true,
                pageSizeOptions: [10, 20, 50],
                showTotal: (total) => `共 ${total} 条`,
                position: ['bottomRight'],
              }}
              scroll={{ x: 'max-content' }}
              className={[
                'compact-workflow-definition-table',
                '[&_.ant-table]:!text-[13px]',
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
                '[&_.ant-pagination]:!my-4',
              ].join(' ')}
              locale={{
                emptyText: (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                      <span className="text-[12px] text-[#98a2b3]">
                        {keyword || filter !== 'ALL'
                          ? '暂无符合条件的工作流'
                          : '暂无工作流定义'}
                      </span>
                    }
                  />
                ),
              }}
            />
          </div>
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
}
