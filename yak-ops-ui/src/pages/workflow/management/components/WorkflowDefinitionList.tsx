import YakButton from '@/components/YakButton';
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
  CalendarOutlined,
  CloudDownloadOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  ConfigProvider,
  Divider,
  Drawer,
  Dropdown,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Table,
  Tooltip,
  message,
} from 'antd';
import type { MenuProps } from 'antd';
import { GitBranch } from 'lucide-react';
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

const FAILURE_STRATEGY_LABEL: Record<string, string> = {
  FAIL_FAST: '快速失败',
  CONTINUE_INDEPENDENT_BRANCHES: '继续独立分支',
  TERMINATE_ALL: '终止全部',
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

const formatDuration = (seconds?: number) => {
  if (!seconds || seconds <= 0) return '未设置';
  if (seconds < 60) return `${seconds} 秒`;
  if (seconds % 3600 === 0) return `${seconds / 3600} 小时`;
  if (seconds % 60 === 0) return `${seconds / 60} 分钟`;
  return `${seconds} 秒`;
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

const getRuntimeHint = (
  runtimeStatus: string | undefined,
  definitionStatus: WorkflowDefinitionStatus,
) => {
  if (!runtimeStatus) {
    return definitionStatus === 'ONLINE'
      ? '已上线，可运行当前生效版本'
      : '发布并上线后可正式运行';
  }

  switch (runtimeStatus) {
    case 'RUNNING':
      return '当前存在运行中的执行实例';
    case 'PAUSING':
      return '正在暂停当前执行';
    case 'PAUSED':
      return '执行已暂停，可恢复后继续';
    case 'RESUMING':
      return '正在恢复当前执行';
    case 'CREATED':
    case 'WAITING':
    case 'READY':
    case 'SUBMITTED':
      return '执行已提交，等待运行';
    case 'SUCCESS':
      return '最近一次执行已成功完成';
    case 'SUCCESS_WITH_WARNINGS':
    case 'WARNING':
      return '最近一次执行完成，但存在告警';
    case 'FAILED':
      return '最近一次执行失败';
    case 'TIMED_OUT':
      return '最近一次执行超时';
    case 'CANCELED':
      return '最近一次执行已取消';
    default:
      return '查看运行记录获取更多信息';
  }
};

const getPublishActionLabel = (record: WorkflowDefinition) => {
  if (record.status === 'ONLINE') return '下线工作流';
  if (record.status === 'OFFLINE' && record.activeVersionNo && !record.draftChanged) {
    return '重新上线';
  }
  if (record.activeVersionNo && record.draftChanged) return '发布更新并上线';
  return '发布并上线';
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

  const goToSchedules = (record: WorkflowDefinition) => {
    history.push(
      `/workflow/definition/${encodeURIComponent(record.id)}/schedule`,
    );
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
      okYakButtonProps: { danger: true, size: 'small' },
      cancelYakButtonProps: { size: 'small' },
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
        <span
          className={['h-1.5 w-1.5 rounded-full', meta.dotClassName].join(' ')}
        />
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
        <span
          className={['h-1.5 w-1.5 rounded-full', tone.dotClassName].join(' ')}
        />
        {status ? RUNTIME_LABEL[status] || status : '尚未运行'}
      </span>
    );
  };

  const confirmOnline = (record: WorkflowDefinition) => {
    if (record.nodeCount <= 0) {
      message.warning('请先编辑工作流并添加至少一个任务节点');
      return;
    }

    const reenable =
      record.status === 'OFFLINE' &&
      Boolean(record.activeVersionNo) &&
      !record.draftChanged;
    const publishingUpdate = Boolean(record.activeVersionNo) && record.draftChanged;
    const targetVersionNo = reenable
      ? record.activeVersionNo
      : (record.latestVersionNo || 0) + 1;
    const title = reenable
      ? `重新上线工作流 v${targetVersionNo}？`
      : publishingUpdate
        ? `发布更新 v${targetVersionNo} 并上线？`
        : `发布并上线工作流 v${targetVersionNo}？`;
    const content = reenable
      ? `将重新启用已发布的 v${targetVersionNo}，不会创建新版本，已保存调度将恢复触发。`
      : publishingUpdate
        ? `当前草稿将形成不可变的 v${targetVersionNo} 并成为正式运行版本；已有运行实例不会受到影响。`
        : `当前草稿将形成不可变的 v${targetVersionNo} 并开启正式运行入口；后续草稿修改不会影响该版本。`;
    const success = reenable
      ? '工作流已重新上线'
      : publishingUpdate
        ? `工作流 v${targetVersionNo} 已发布并上线`
        : `工作流 v${targetVersionNo} 已发布并上线`;

    Modal.confirm({
      centered: true,
      title,
      content,
      okText: reenable
        ? '重新上线'
        : publishingUpdate
          ? '发布更新并上线'
          : '发布并上线',
      cancelText: '取消',
      async onOk() {
        await executeAction(
          record.id,
          () => onlineWorkflowDefinition(record.id),
          success,
        );
      },
    });
  };

  const confirmOffline = (record: WorkflowDefinition) => {
    Modal.confirm({
      centered: true,
      title: '下线工作流',
      content:
        '下线后将关闭新的正式运行和调度触发；已经启动的实例继续执行，草稿仍可继续编辑和测试。确认下线吗？',
      okText: '下线',
      cancelText: '取消',
      async onOk() {
        await executeAction(
          record.id,
          () => offlineWorkflowDefinition(record.id),
          '工作流已下线',
        );
      },
    });
  };

  const handleMoreAction = (key: string, record: WorkflowDefinition) => {
    switch (key) {
      case 'edit':
        goToDefinition(record);
        break;
      case 'schedule':
        goToSchedules(record);
        break;
      case 'online':
        confirmOnline(record);
        break;
      case 'offline':
        confirmOffline(record);
        break;
      case 'pause':
        void executeAction(
          record.id,
          () => pauseWorkflowDefinition(record.id),
          '已请求暂停最近执行',
        );
        break;
      case 'resume':
        void executeAction(
          record.id,
          () => resumeWorkflowDefinition(record.id),
          '最近执行已恢复',
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
    const canDelete = record.status !== 'ONLINE' && !active;
    const items: NonNullable<MenuProps['items']> = [
      {
        key: 'edit',
        icon: <EditOutlined />,
        label: '编辑工作流',
      },
      {
        key: 'schedule',
        icon: <CalendarOutlined />,
        label: '调度配置',
      },
    ];

    // 草稿编辑与运行实例是两条独立生命周期；非 ONLINE 状态下仍保留最近执行的控制入口。
    if (record.status !== 'ONLINE') {
      if (isRunningRuntime(record.latestExecutionStatus)) {
        items.push({
          key: 'pause',
          icon: <PauseCircleOutlined />,
          label: '暂停最近执行',
        });
      } else if (record.latestExecutionStatus === 'PAUSED') {
        items.push({
          key: 'resume',
          icon: <PlayCircleOutlined />,
          label: '恢复最近执行',
        });
      } else if (
        record.latestExecutionStatus === 'PAUSING' ||
        record.latestExecutionStatus === 'RESUMING'
      ) {
        items.push({
          key: 'runtime-transition',
          icon:
            record.latestExecutionStatus === 'PAUSING' ? (
              <PauseCircleOutlined />
            ) : (
              <PlayCircleOutlined />
            ),
          label:
            record.latestExecutionStatus === 'PAUSING'
              ? '最近执行暂停中'
              : '最近执行恢复中',
          disabled: true,
        });
      }
    }

    items.push(
      { type: 'divider' },
      {
        key: record.status === 'ONLINE' ? 'offline' : 'online',
        icon:
          record.status === 'ONLINE' ? (
            <CloudDownloadOutlined />
          ) : (
            <CloudUploadOutlined />
          ),
        label: getPublishActionLabel(record),
      },
      { type: 'divider' },
      {
        key: 'delete',
        icon: <DeleteOutlined />,
        label: '删除工作流',
        danger: true,
        disabled: !canDelete,
      },
    );

    return items;
  };

  const getRunDisabledReason = (record: WorkflowDefinition) => {
    if (actionId && actionId !== record.id) return '正在处理其他工作流，请稍候';
    if (record.status !== 'ONLINE') return '请先发布并上线工作流';
    if (record.nodeCount <= 0) return '请先完成节点编排';
    if (!record.activeVersionNo) return '当前没有生效版本，请重新发布并上线';
    if (isActiveRuntime(record.latestExecutionStatus)) return '当前已有活动执行';
    return undefined;
  };

  const renderPrimaryAction = (record: WorkflowDefinition) => {
    const busy = actionId === record.id;
    const runtimeStatus = record.latestExecutionStatus;

    // 草稿/下线状态的主任务是继续编辑，而不是让最近一次执行状态抢占入口。
    if (record.status !== 'ONLINE') {
      return (
        <YakButton
          size="small"
          color="default"
          variant="filled"
          disabled={Boolean(actionId)}
          icon={<EditOutlined />}
          className="!h-7 !rounded-md !px-2.5 !text-xs"
          onClick={() => goToDefinition(record)}
        >
          编辑
        </YakButton>
      );
    }

    if (runtimeStatus === 'PAUSING') {
      return (
        <YakButton
          size="small"
          color="danger"
          variant="filled"
          disabled
          icon={<PauseCircleOutlined />}
          className="!h-7 !rounded-md !px-2.5 !text-xs"
        >
          暂停中
        </YakButton>
      );
    }

    if (runtimeStatus === 'RESUMING') {
      return (
        <YakButton
          size="small"
          color="primary"
          variant="filled"
          disabled
          icon={<PlayCircleOutlined />}
          className="!h-7 !rounded-md !px-2.5 !text-xs"
        >
          恢复中
        </YakButton>
      );
    }

    if (runtimeStatus === 'PAUSED') {
      return (
        <YakButton
          size="small"
          color="primary"
          variant="filled"
          loading={busy}
          disabled={Boolean(actionId && !busy)}
          icon={<PlayCircleOutlined />}
          className="!h-7 !rounded-md !px-2.5 !text-xs"
          onClick={() =>
            void executeAction(
              record.id,
              () => resumeWorkflowDefinition(record.id),
              '工作流已恢复',
            )
          }
        >
          恢复
        </YakButton>
      );
    }

    if (isRunningRuntime(runtimeStatus)) {
      return (
        <Popconfirm
          title="暂停工作流"
          description="确认暂停当前工作流执行吗？"
          okText="确认"
          cancelText="取消"
          disabled={Boolean(actionId && !busy)}
          onConfirm={() =>
            executeAction(
              record.id,
              () => pauseWorkflowDefinition(record.id),
              '已请求暂停工作流',
            )
          }
        >
          <YakButton
            size="small"
            color="danger"
            variant="filled"
            loading={busy}
            disabled={Boolean(actionId && !busy)}
            icon={<PauseCircleOutlined />}
            className="!h-7 !rounded-md !px-2.5 !text-xs"
          >
            暂停
          </YakButton>
        </Popconfirm>
      );
    }

    const disabledReason = getRunDisabledReason(record);
    const canRun = !disabledReason;

    return (
      <Tooltip title={disabledReason}>
        <span className="inline-flex">
          <Popconfirm
            title="运行已上线版本"
            description={
              record.draftChanged
                ? `当前存在未发布草稿，本次仍运行已上线的 v${record.activeVersionNo}。确认运行吗？`
                : `确认运行当前已上线的 v${record.activeVersionNo} 吗？`
            }
            okText="确认"
            cancelText="取消"
            disabled={!canRun}
            onConfirm={() =>
              executeAction(
                record.id,
                () => runWorkflowDefinition(record.id),
                `工作流 v${record.activeVersionNo} 已启动`,
              )
            }
          >
            <YakButton
              size="small"
              color={canRun ? 'primary' : 'default'}
              variant="filled"
              loading={busy}
              disabled={!canRun}
              icon={<PlayCircleOutlined />}
              className={[
                '!h-7 !rounded-md !px-2.5 !text-xs',
                !canRun ? '!cursor-not-allowed !text-[#98a2b3]' : '',
              ].join(' ')}
            >
              运行
            </YakButton>
          </Popconfirm>
        </span>
      </Tooltip>
    );
  };

  const columns = [
    {
      title: '工作流',
      dataIndex: 'name',
      width: 310,
      render: (_value: string, record: WorkflowDefinition) => (
        <div className="min-w-0 py-0.5">
          <YakButton
            type="YakButton"
            title={record.name}
            onClick={() => goToDefinition(record)}
            className={[
              'block max-w-full truncate border-0 bg-transparent p-0 text-left',
              'text-[13px] font-medium leading-5 text-[#344054]',
              'transition-colors hover:text-[#fe2c55]',
            ].join(' ')}
          >
            {record.name || '未命名工作流'}
          </YakButton>
          <div
            title={record.description || ''}
            className="mt-0.5 truncate text-[11px] leading-5 text-[#98a2b3]"
          >
            {record.description || '暂无描述'}
          </div>
        </div>
      ),
    },
    {
      title: '编排信息',
      dataIndex: 'topology',
      width: 300,
      render: (_value: unknown, record: WorkflowDefinition) => (
        <div className="py-0.5 text-[12px] leading-5 text-[#667085]">
          <div className="flex items-center gap-2 text-[#475467]">
            <span>
              {record.nodeCount > 0 ? `${record.nodeCount} 个节点` : '尚未配置节点'}
            </span>
            <span className="text-[#d0d5dd]">·</span>
            <span>{record.edgeCount} 条依赖</span>
          </div>
          <div className="mt-1 text-[11px] text-[#98a2b3]">
            超时：{formatDuration(record.workflowTimeoutSeconds)}
            <span className="mx-1.5 text-[#d0d5dd]">·</span>
            失败：{FAILURE_STRATEGY_LABEL[record.failureStrategy] || record.failureStrategy}
          </div>
        </div>
      ),
    },
    {
      title: '发布信息',
      dataIndex: 'status',
      width: 230,
      render: (_value: WorkflowDefinitionStatus, record: WorkflowDefinition) => (
        <div className="py-0.5">
          <div className="flex flex-wrap items-center gap-1.5">
            {renderDefinitionStatus(record.status)}
            {record.draftChanged && (
              <span className="inline-flex h-6 items-center rounded-md bg-[#fff7e6] px-2 text-[11px] font-medium text-[#b54708]">
                有草稿修改
              </span>
            )}
          </div>
          <div className="mt-1.5 text-[11px] leading-5 text-[#98a2b3]">
            {record.latestVersionNo > 0 ? (
              <>
                <span>最新 V{record.latestVersionNo}</span>
                <span className="mx-1.5 text-[#d0d5dd]">·</span>
                <span>
                  {record.activeVersionNo
                    ? `生效 V${record.activeVersionNo}`
                    : '暂无生效版本'}
                </span>
              </>
            ) : (
              <span>尚未发布版本</span>
            )}
          </div>
        </div>
      ),
    },
    {
      title: '执行状态',
      dataIndex: 'latestExecutionStatus',
      width: 245,
      render: (_value: string | undefined, record: WorkflowDefinition) => (
        <div className="py-0.5">
          {renderRuntimeStatus(record.latestExecutionStatus)}
          <div className="mt-1.5 text-[11px] leading-5 text-[#98a2b3]">
            {getRuntimeHint(record.latestExecutionStatus, record.status)}
          </div>
        </div>
      ),
    },
    {
      title: '时间',
      dataIndex: 'updateTime',
      width: 200,
      render: (_value: string, record: WorkflowDefinition) => (
        <div className="text-[12px] leading-5 text-[#667085]">
          <div className="whitespace-nowrap">更新：{formatTime(record.updateTime)}</div>
          <div className="whitespace-nowrap text-[11px] text-[#98a2b3]">
            创建：{formatTime(record.createTime)}
          </div>
        </div>
      ),
    },
    {
      title: '操作',
      dataIndex: 'operate',
      width: 190,
      fixed: 'right' as const,
      render: (_value: unknown, record: WorkflowDefinition) => (
        <div className="flex min-h-7 items-center gap-1 whitespace-nowrap">
          {renderPrimaryAction(record)}
          <Dropdown
            trigger={['click']}
            placement="bottomRight"
            menu={{
              items: getMoreMenuItems(record),
              onClick: ({ key, domEvent }) => {
                domEvent.stopPropagation();
                handleMoreAction(key, record);
              },
            }}
          >
            <YakButton
              size="small"
              color="default"
              variant="text"
              disabled={Boolean(actionId)}
              className="!h-7 !rounded-md !px-2 !text-xs !text-[#667085]"
              onClick={(event) => event.stopPropagation()}
            >
              更多
              <DownOutlined className="text-[9px]" />
            </YakButton>
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
          YakButton: { borderRadius: 8 },
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
                      <YakButton
                        key={item.key}
                        type="YakButton"
                        onClick={() => setFilter(item.key)}
                        className={[
                          'h-8 rounded-md px-3.5 text-[13px] font-medium transition-all',
                          active
                            ? 'bg-white text-[#fe2c55] shadow-[0_1px_4px_rgba(16,24,40,0.08)]'
                            : 'text-[#667085] hover:bg-white/70 hover:text-[#344054]',
                        ].join(' ')}
                      >
                        {item.label}
                      </YakButton>
                    );
                  })}
                </div>

                <div className="flex min-w-0 flex-1 items-center justify-end gap-2 overflow-x-auto">
                  <Input
                    allowClear
                    variant="filled"
                    value={keywordDraft}
                    prefix={<SearchOutlined className="text-[#98a2b3]" />}
                    placeholder="搜索工作流名称或描述"
                    className="!h-9 !w-[300px] !min-w-[220px]"
                    onChange={(event) => setKeywordDraft(event.target.value)}
                    onPressEnter={handleSearch}
                  />

                  <YakButton
                    size="small"
                    className="!h-9 !px-4"
                    onClick={handleSearch}
                  >
                    查询
                  </YakButton>

                  <Tooltip title="刷新">
                    <YakButton
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
              <YakButton
                danger
                type="primary"
                size="small"
                className="!h-7"
                onClick={() => setCreateOpen(true)}
              >
                <span className="text-[13px]">新建工作流</span>
              </YakButton>
            </div>

            <div className="flex min-h-9 items-center rounded-sm bg-[#fff7e6] px-3 text-[12px] text-[#475467]">
              <span className="mr-2 text-[14px] text-[#faad14]">▲</span>
              <span className="font-medium text-[#344054]">【提示】</span>
              <span>
                草稿可随时编辑和测试；发布后生成正式版本。调度仅对已上线版本生效，下线不会中断已经启动的实例。
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
              <YakButton
                disabled={creating}
                onClick={handleCreateClose}
                className="!h-9 !rounded-lg !px-4"
              >
                取消
              </YakButton>
              <YakButton
                type="primary"
                loading={creating}
                onClick={() => void handleCreate()}
                className="!h-9 !rounded-lg !px-5 !text-white"
              >
                创建并配置
              </YakButton>
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
