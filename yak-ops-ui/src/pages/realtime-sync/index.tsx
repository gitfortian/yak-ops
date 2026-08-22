import { history } from '@umijs/max';
import {
  Button,
  ConfigProvider,
  Descriptions,
  Divider,
  Drawer,
  Dropdown,
  Empty,
  Input,
  message,
  Modal,
  Popover,
  Select,
  Table,
  Tooltip,
} from 'antd';
import { CopyOutlined, FilterOutlined, MoreOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { MenuProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';
import CustomPagination from '../batch-link-up/CustomPagination';
import { realtimeApi } from './api';
import JobEditor from './JobEditor';
import CreateRealtimeTaskDrawer from './CreateRealtimeTaskDrawer';
import RealtimeRuntimeDetail from './RealtimeRuntimeDetail';
import type {
  DataSourceOption,
  RealtimeEvent,
  RealtimeJob,
  RealtimeJobChange,
  ReleaseState,
  RuntimeCapabilities,
} from './types';

type StateGroup = 'ALL' | 'RUNNING' | 'STOPPED' | 'ABNORMAL';

interface FilterState {
  keyword?: string;
  id?: string;
  releaseState?: ReleaseState;
  stateGroup: StateGroup;
}

const statusTabs: Array<{ label: string; value: StateGroup }> = [
  { label: '全部任务', value: 'ALL' },
  { label: '运行中', value: 'RUNNING' },
  { label: '已停止', value: 'STOPPED' },
  { label: '异常', value: 'ABNORMAL' },
];

const releaseOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
];

const observedStateLabel: Record<string, string> = {
  STOPPED: '已停止',
  STARTING: '启动中',
  RUNNING: '运行中',
  STOPPING: '停止中',
  FAILED: '失败',
  UNKNOWN: '未知',
  CONFLICT: '冲突',
};

const releaseStateLabel: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
};

const statusStyle = (state?: string) => {
  switch (String(state || '').toUpperCase()) {
    case 'RUNNING':
    case 'PUBLISHED':
      return { dot: '#12b76a', text: '#027a48', background: '#ecfdf3', border: '#abefc6' };
    case 'STARTING':
    case 'STOPPING':
      return { dot: '#2e90fa', text: '#175cd3', background: '#eff8ff', border: '#b2ddff' };
    case 'FAILED':
    case 'CONFLICT':
      return { dot: '#f04438', text: '#b42318', background: '#fef3f2', border: '#fecdca' };
    case 'UNKNOWN':
      return { dot: '#f79009', text: '#b54708', background: '#fffaeb', border: '#fedf89' };
    case 'DRAFT':
    case 'STOPPED':
    default:
      return { dot: '#98a2b3', text: '#475467', background: '#f9fafb', border: '#eaecf0' };
  }
};

const StateBadge = ({ state, label }: { state?: string; label?: string }) => {
  const style = statusStyle(state);
  return (
    <span
      className="inline-flex h-6 items-center gap-1.5 rounded-full border px-2 text-[11px] font-medium"
      style={{ color: style.text, background: style.background, borderColor: style.border }}
    >
      <span className="h-1.5 w-1.5 rounded-full" style={{ background: style.dot }} />
      {label || state || '-'}
    </span>
  );
};

const initialFilters: FilterState = { stateGroup: 'ALL' };

export default function RealtimeSync() {
  const [jobs, setJobs] = useState<RealtimeJob[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [capabilities, setCapabilities] = useState<RuntimeCapabilities>({});
  const [dataSources, setDataSources] = useState<DataSourceOption[]>([]);
  const [filterDraft, setFilterDraft] = useState<FilterState>(initialFilters);
  const [filters, setFilters] = useState<FilterState>(initialFilters);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [editor, setEditor] = useState<{ open: boolean; job?: RealtimeJob }>({ open: false });
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<RealtimeJob>();
  const [events, setEvents] = useState<RealtimeEvent[]>([]);
  const [streamConnected, setStreamConnected] = useState(false);

  const dataSourceMap = useMemo(
    () => new Map(dataSources.map((item) => [String(item.value), item])),
    [dataSources],
  );

  const loadJobs = useCallback(async () => {
    setLoading(true);
    try {
      const id = filters.id?.trim();
      const page = await realtimeApi.page({
        pageNo,
        pageSize,
        keyword: filters.keyword?.trim() || undefined,
        id: id && /^\d+$/.test(id) ? Number(id) : undefined,
        releaseState: filters.releaseState,
        stateGroup: filters.stateGroup === 'ALL' ? undefined : filters.stateGroup,
      });
      setJobs(page.data.records || []);
      setTotal(page.data.total || 0);
    } catch (error: any) {
      message.error(error?.message || '加载实时同步任务失败');
    } finally {
      setLoading(false);
    }
  }, [filters, pageNo, pageSize]);

  const loadMetadata = useCallback(async () => {
    const [caps, sources] = await Promise.allSettled([
      realtimeApi.capabilities(),
      realtimeApi.dataSources(),
    ]);
    setCapabilities(caps.status === 'fulfilled' ? caps.value.data || {} : {});
    setDataSources(sources.status === 'fulfilled' ? sources.value.data || [] : []);
    if (caps.status === 'rejected') {
      message.warning('Flink CDC 当前不可用，任务定义仍可查看');
    }
  }, []);

  useEffect(() => {
    void loadJobs();
  }, [loadJobs]);

  useEffect(() => {
    void loadMetadata();
  }, [loadMetadata]);

  useEffect(() => {
    const source = new EventSource('/api/v1/realtime-sync/stream');
    let fallbackTimer: number | undefined;
    const stopFallback = () => {
      if (fallbackTimer !== undefined) window.clearInterval(fallbackTimer);
      fallbackTimer = undefined;
    };
    const startFallback = () => {
      if (fallbackTimer !== undefined) return;
      fallbackTimer = window.setInterval(() => void loadJobs(), 5000);
    };
    const refreshChangedJob = async (change: RealtimeJobChange) => {
      try {
        await loadJobs();
        if (detail?.id !== change.definitionId) return;
        const [jobResult, eventResult] = await Promise.all([
          realtimeApi.detail(change.definitionId),
          realtimeApi.events(change.definitionId),
        ]);
        setDetail(jobResult.data);
        setEvents(eventResult.data || []);
      } catch {
        startFallback();
      }
    };
    source.onopen = () => {
      setStreamConnected(true);
      stopFallback();
    };
    source.onerror = () => {
      setStreamConnected(false);
      startFallback();
    };
    source.addEventListener('realtime', (event) => {
      try {
        const change = JSON.parse((event as MessageEvent<string>).data) as RealtimeJobChange;
        void refreshChangedJob(change);
      } catch {
        void loadJobs();
      }
    });
    return () => {
      source.close();
      stopFallback();
    };
  }, [detail?.id, loadJobs]);

  const refresh = async () => {
    await Promise.all([loadJobs(), loadMetadata()]);
  };

  const waitForStartResult = async (id: number) => {
    for (let attempt = 0; attempt < 15; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 2000));
      try {
        const result = await realtimeApi.detail(id);
        if (['RUNNING', 'FAILED', 'UNKNOWN', 'CONFLICT'].includes(result.data.observedState)) {
          return result.data.observedState;
        }
      } catch {
        // A transient read failure must not turn an accepted deployment into an action failure.
      }
    }
    return 'STARTING';
  };

  const action = async (
    job: RealtimeJob,
    name: 'publish' | 'validate' | 'start' | 'stop' | 'restart' | 'reconcile',
  ) => {
    try {
      await realtimeApi.action(job.id, name);
      if (name === 'start') {
        const state = await waitForStartResult(job.id);
        if (state === 'RUNNING') message.success('实时同步任务已启动');
        else if (state === 'STARTING') message.warning('Flink 任务仍在启动，请稍后刷新状态');
        else message.warning(`Flink 启动结果：${observedStateLabel[state] || state}`);
      } else {
        message.success(name === 'validate' ? 'Flink CDC 校验通过' : '操作成功');
      }
      await loadJobs();
    } catch (error: any) {
      message.error(error?.message || '操作失败');
    }
  };

  const openDetail = async (job: RealtimeJob) => {
    try {
      const [jobResult, eventResult] = await Promise.all([
        realtimeApi.detail(job.id),
        realtimeApi.events(job.id),
      ]);
      setDetail(jobResult.data);
      setEvents(eventResult.data || []);
    } catch (error: any) {
      message.error(error?.message || '加载运行详情失败');
    }
  };

  const copyToClipboard = async (value: string | number) => {
    const text = String(value);
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
      } else {
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
      }
      message.success('任务 ID 已复制');
    } catch {
      message.error('复制失败，请手动复制');
    }
  };

  const applyFilters = (next: FilterState) => {
    setFilterDraft(next);
    setFilters(next);
    setPageNo(1);
  };

  const handleSearch = () => {
    if (filterDraft.id && !/^\d+$/.test(filterDraft.id.trim())) {
      message.warning('任务 ID 仅支持数字');
      return;
    }
    setFilters({ ...filterDraft });
    setPageNo(1);
  };

  const handleTabChange = (stateGroup: StateGroup) => {
    applyFilters({ ...filterDraft, stateGroup });
  };

  const handleReleaseChange = (releaseState?: ReleaseState) => {
    applyFilters({ ...filterDraft, releaseState });
  };

  const handleReset = () => {
    applyFilters(initialFilters);
    setAdvancedOpen(false);
  };

  const advancedFilterCount = [filterDraft.id].filter(Boolean).length;

  const sourceLabel = (job: RealtimeJob) => {
    const source = dataSourceMap.get(String(job.spec?.sourceDataSourceRef));
    return source?.label || `数据源 #${job.spec?.sourceDataSourceRef || '-'}`;
  };

  const sinkLabel = (job: RealtimeJob) => {
    const sink = dataSourceMap.get(String(job.spec?.sinkDataSourceRef));
    return sink?.label || `数据源 #${job.spec?.sinkDataSourceRef || '-'}`;
  };

  const sourceType = (job: RealtimeJob) =>
    dataSourceMap.get(String(job.spec?.sourceDataSourceRef))?.dbType || '-';
  const sinkType = (job: RealtimeJob) =>
    dataSourceMap.get(String(job.spec?.sinkDataSourceRef))?.dbType || '-';

  const deleteJob = (job: RealtimeJob) => {
    Modal.confirm({
      title: '删除实时同步任务',
      content: `确认删除“${job.name}”？该操作仅允许已停止任务执行。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await realtimeApi.remove(job.id);
          message.success('任务已删除');
          await loadJobs();
        } catch (error: any) {
          message.error(error?.message || '删除失败');
          throw error;
        }
      },
    });
  };

  const handleMoreAction = (job: RealtimeJob, key: string) => {
    if (key === 'detail') {
      void openDetail(job);
      return;
    }
    if (key === 'delete') {
      deleteJob(job);
      return;
    }
    if (key === 'validate' || key === 'publish' || key === 'restart' || key === 'reconcile') {
      void action(job, key);
    }
  };

  const moreItems = (job: RealtimeJob): MenuProps['items'] => [
    { key: 'detail', label: '查看运行详情' },
    {
      key: 'validate',
      label: 'Flink CDC 校验',
      disabled: job.releaseState === 'PUBLISHED',
    },
    {
      key: 'publish',
      label: '发布当前版本',
      disabled: job.releaseState === 'PUBLISHED' || job.desiredState === 'RUNNING',
    },
    {
      key: 'restart',
      label: '重启任务',
      disabled: job.desiredState !== 'RUNNING' || !capabilities.deployEnabled,
    },
    {
      key: 'reconcile',
      label: '立即状态对账',
      disabled: !['UNKNOWN', 'CONFLICT', 'STARTING', 'STOPPING'].includes(job.observedState),
    },
    { type: 'divider' },
    {
      key: 'delete',
      label: <span className="text-[#d92d20]">删除任务</span>,
      disabled: job.desiredState !== 'STOPPED',
    },
  ];

  const columns: ColumnsType<RealtimeJob> = [
    {
      title: '名称 / ID',
      dataIndex: 'name',
      width: 250,
      render: (value, job) => (
        <div className="min-w-0 py-0.5">
          <button
            type="button"
            className="block max-w-full truncate text-left text-[13px] font-medium leading-5 text-[#344054] hover:text-[#ff4d4f]"
            title={value}
            onClick={() => void openDetail(job)}
          >
            {value || '-'}
          </button>
          <div className="mt-0.5 flex h-5 items-center gap-1 text-[11px] leading-5 text-[#98a2b3]">
            <span className="truncate">ID：{job.id} · v{job.definitionVersion}</span>
            <Tooltip title="复制任务 ID">
              <Button
                type="text"
                size="small"
                icon={<CopyOutlined className="text-[11px]" />}
                className="!flex !h-5 !w-5 !min-w-0 !items-center !justify-center !p-0 !text-[#98a2b3] hover:!bg-[#f2f4f7] hover:!text-[#475467]"
                onClick={(event) => {
                  event.stopPropagation();
                  void copyToClipboard(job.id);
                }}
              />
            </Tooltip>
          </div>
        </div>
      ),
    },
    {
      title: '数据源同步方案',
      dataIndex: 'syncPlan',
      width: 300,
      render: (_, job) => (
        <div className="min-w-0 py-0.5 text-[12px] leading-5 text-[#667085]">
          <div className="flex min-w-0 items-center gap-2">
            <span className="max-w-[118px] truncate font-medium text-[#475467]" title={sourceLabel(job)}>
              {sourceLabel(job)}
            </span>
            <span className="text-[#98a2b3]">→</span>
            <span className="max-w-[118px] truncate font-medium text-[#475467]" title={sinkLabel(job)}>
              {sinkLabel(job)}
            </span>
          </div>
          <div className="mt-0.5 text-[11px] text-[#98a2b3]">
            {sourceType(job)} → {sinkType(job)} · {job.spec?.tables?.length || 0} 张表
          </div>
        </div>
      ),
    },
    {
      title: '发布状态',
      dataIndex: 'releaseState',
      width: 115,
      align: 'center',
      render: (value) => (
        <StateBadge state={value} label={releaseStateLabel[value] || value} />
      ),
    },
    {
      title: '运行状态',
      dataIndex: 'observedState',
      width: 145,
      render: (value, job) => (
        <div className="flex flex-col items-start gap-1">
          <StateBadge state={value} label={observedStateLabel[value] || value} />
          <span className="text-[10px] leading-4 text-[#98a2b3]">
            期望：{job.desiredState === 'RUNNING' ? '运行' : '停止'}
          </span>
        </div>
      ),
    },
    {
      title: 'Flink 运行时',
      dataIndex: 'runtime',
      width: 170,
      render: (_, job) => (
        <div className="text-[12px] leading-5 text-[#667085]">
          <div className="truncate text-[#475467]" title={job.latestDeployment?.runtimeRevision}>
            {job.latestDeployment?.runtimeRevision || '-'}
          </div>
          <div className="text-[11px] text-[#98a2b3]">
            {job.latestDeployment?.engineJobId ? `Job ${job.latestDeployment.engineJobId}` : '尚未部署'}
          </div>
        </div>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (value) => (
        <span className="whitespace-nowrap text-[12px] leading-5 text-[#98a2b3]">{value || '-'}</span>
      ),
    },
    {
      title: '操作',
      key: 'operate',
      fixed: 'right',
      width: 215,
      render: (_, job) => {
        const running = job.desiredState === 'RUNNING';
        const startDisabled =
          !capabilities.deployEnabled ||
          job.releaseState !== 'PUBLISHED' ||
          running;
        const startTooltip = !capabilities.deployEnabled
          ? capabilities.deployDisabledReason || 'Flink CDC 尚未准备好提交任务'
          : job.releaseState !== 'PUBLISHED'
            ? '请先发布当前任务版本'
            : running
              ? '任务已处于运行期望状态'
              : undefined;

        return (
          <div className="flex min-h-7 items-center gap-0.5 whitespace-nowrap">
            <Button
              type="link"
              size="small"
              className="!h-7 !px-1.5 !text-[12px]"
              disabled={running}
              onClick={() => history.push(`/sync/realtime/${job.id}/detail?scene=edit`)}
            >
              编辑
            </Button>
            {running ? (
              <Button
                type="link"
                danger
                size="small"
                className="!h-7 !px-1.5 !text-[12px]"
                onClick={() => void action(job, 'stop')}
              >
                停止
              </Button>
            ) : (
              <Tooltip title={startTooltip}>
                <span>
                  <Button
                    type="link"
                    danger
                    size="small"
                    className="!h-7 !px-1.5 !text-[12px]"
                    disabled={startDisabled}
                    onClick={() => void action(job, 'start')}
                  >
                    启动
                  </Button>
                </span>
              </Tooltip>
            )}
            <Dropdown
              trigger={['click']}
              menu={{
                items: moreItems(job),
                onClick: ({ key }) => handleMoreAction(job, key),
              }}
            >
              <Button
                type="text"
                size="small"
                icon={<MoreOutlined />}
                className="!h-7 !w-7 !min-w-0 !p-0 !text-[#667085] hover:!bg-[#f2f4f7]"
              />
            </Dropdown>
          </div>
        );
      },
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
          Select: { activeOutlineColor: 'transparent' },
        },
      }}
    >
      <div className="flex min-h-[calc(100vh-64px)] flex-col bg-white px-5 pt-4">
        <div className="flex items-end justify-between gap-4">
          <div>
            <h1 className="m-0 text-[17px] font-semibold leading-7 text-[#101828]">实时同步</h1>
            <div className="mt-0.5 text-[12px] text-[#98a2b3]">
              Flink CDC CLI + Flink REST · MySQL CDC → MySQL / PostgreSQL
              <span className="ml-2">· {streamConnected ? '状态流已连接' : '状态流重连中'}</span>
            </div>
          </div>
        </div>

        <div className="mx-auto mt-3 flex w-full max-w-full flex-1 flex-col">
          <div className="mb-3">
            <div className="border-b border-[#f0f0f0]">
              <div className="flex min-h-[54px] items-center justify-between gap-4 py-2">
                <div className="flex shrink-0 items-center gap-1 rounded-lg bg-[#f5f5f6] p-1">
                  {statusTabs.map((item) => {
                    const active = filters.stateGroup === item.value;
                    return (
                      <button
                        key={item.value}
                        type="button"
                        onClick={() => handleTabChange(item.value)}
                        className={[
                          'h-8 rounded-md px-3.5 text-[13px] font-medium transition-all',
                          active
                            ? 'bg-white text-[#ff4d4f] shadow-[0_1px_4px_rgba(16,24,40,0.08)]'
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
                    value={filterDraft.keyword}
                    prefix={<SearchOutlined className="text-[#98a2b3]" />}
                    placeholder="搜索任务名称 / 描述"
                    className="!h-9 !w-[240px] !min-w-[190px]"
                    onChange={(event) =>
                      setFilterDraft((previous) => ({
                        ...previous,
                        keyword: event.target.value || undefined,
                      }))
                    }
                    onPressEnter={handleSearch}
                  />
                  <Select
                    allowClear
                    variant="filled"
                    value={filterDraft.releaseState}
                    options={releaseOptions}
                    placeholder="发布状态"
                    className="!h-9 !w-[135px] !min-w-[125px]"
                    onChange={handleReleaseChange}
                  />
                  <Button size="small" className="!h-9 !px-4" onClick={handleSearch}>
                    查询
                  </Button>
                  <Popover
                    trigger="click"
                    placement="bottomRight"
                    open={advancedOpen}
                    onOpenChange={setAdvancedOpen}
                    content={
                      <div className="w-[320px]">
                        <div className="text-[14px] font-semibold text-[#101828]">高级搜索</div>
                        <div className="mt-1 text-[12px] text-[#98a2b3]">按任务 ID 精确定位实时任务</div>
                        <div className="mt-4">
                          <div className="mb-1.5 text-[12px] text-[#667085]">任务 ID</div>
                          <Input
                            allowClear
                            variant="filled"
                            value={filterDraft.id}
                            placeholder="请输入数字任务 ID"
                            onChange={(event) =>
                              setFilterDraft((previous) => ({
                                ...previous,
                                id: event.target.value || undefined,
                              }))
                            }
                            onPressEnter={() => {
                              handleSearch();
                              setAdvancedOpen(false);
                            }}
                          />
                        </div>
                        <div className="mt-5 flex items-center justify-end gap-2 border-t border-[#f0f0f0] pt-4">
                          <Button size="small" className="!h-8" onClick={handleReset}>
                            重置全部
                          </Button>
                          <Button
                            danger
                            type="primary"
                            size="small"
                            className="!h-8"
                            onClick={() => {
                              handleSearch();
                              setAdvancedOpen(false);
                            }}
                          >
                            应用筛选
                          </Button>
                        </div>
                      </div>
                    }
                  >
                    <Button
                      size="small"
                      icon={<FilterOutlined />}
                      className={[
                        '!h-9 !px-3',
                        advancedFilterCount > 0
                          ? '!border-[#ffccc7] !bg-[#fff1f0] !text-[#ff4d4f]'
                          : '',
                      ].join(' ')}
                    >
                      高级搜索
                      {advancedFilterCount > 0 && (
                        <span className="ml-1.5 inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-[#ff4d4f] px-1 text-[10px] leading-[18px] text-white">
                          {advancedFilterCount}
                        </span>
                      )}
                    </Button>
                  </Popover>
                </div>
              </div>
            </div>

            <div className="flex min-h-[48px] items-center justify-between">
              <div className="flex items-center gap-2">
                <Popover
                  placement="bottomLeft"
                  content={
                    <Descriptions size="small" column={1} className="w-[420px]">
                      <Descriptions.Item label="提交引擎">{capabilities.runtimeVersion || '-'}</Descriptions.Item>
                      <Descriptions.Item label="Flink">{capabilities.flinkVersion || '-'}</Descriptions.Item>
                      <Descriptions.Item label="Flink CDC">{capabilities.flinkCdcVersion || '-'}</Descriptions.Item>
                      <Descriptions.Item label="REST">{capabilities.restUrl || '-'}</Descriptions.Item>
                      <Descriptions.Item label="语义">{capabilities.deliverySemantics || '-'}</Descriptions.Item>
                      <Descriptions.Item label="Sources">
                        {capabilities.connectors?.sources?.join(', ') || '-'}
                      </Descriptions.Item>
                      <Descriptions.Item label="Sinks">
                        {capabilities.connectors?.sinks?.join(', ') || '-'}
                      </Descriptions.Item>
                    </Descriptions>
                  }
                >
                  <Button size="small" className="!h-8">Flink 能力</Button>
                </Popover>
                <Tooltip title="刷新任务与 Flink 能力">
                  <Button
                    size="small"
                    icon={<ReloadOutlined spin={loading} />}
                    className="!h-8 !w-8 !px-0"
                    onClick={() => void refresh()}
                  />
                </Tooltip>
              </div>
              <Button
                type="primary"
                size="small"
                danger
                className="!h-7"
                onClick={() => setCreateOpen(true)}
              >
                <span className="text-[13px]">新建同步任务</span>
              </Button>
            </div>

          </div>

          <Divider style={{ marginTop: 4, marginBottom: 16 }} />

          <div className="flex-1">
            <Table
              rowKey="id"
              loading={loading}
              dataSource={jobs}
              columns={columns}
              bordered
              size="small"
              pagination={false}
              scroll={{ x: 'max-content' }}
              className={[
                'compact-sync-task-table',
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
              ].join(' ')}
              locale={{
                emptyText: (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={<span className="text-[12px] text-[#98a2b3]">暂无实时同步任务</span>}
                  />
                ),
              }}
            />
          </div>

          <div className="sticky bottom-0 z-20 mt-auto flex min-h-[56px] items-center justify-end border border-t-0 border-[#e5e7eb] bg-white px-5 py-3 shadow-[0_-4px_12px_rgba(16,24,40,0.04)]">
            <CustomPagination
              total={total}
              current={pageNo}
              pageSize={pageSize}
              onChange={(nextPage, nextSize) => {
                setPageNo(nextPage);
                setPageSize(nextSize);
              }}
            />
          </div>
        </div>

        <JobEditor
          open={editor.open}
          job={editor.job}
          dataSources={dataSources}
          onClose={() => setEditor({ open: false })}
          onSaved={() => {
            setEditor({ open: false });
            void loadJobs();
          }}
        />
        <CreateRealtimeTaskDrawer open={createOpen} onClose={() => setCreateOpen(false)} />

        <Drawer
          width={960}
          title={detail?.name}
          open={Boolean(detail)}
          onClose={() => setDetail(undefined)}
        >
          {detail && (
            <RealtimeRuntimeDetail job={detail} events={events} capabilities={capabilities} />
          )}
        </Drawer>
      </div>
    </ConfigProvider>
  );
}
