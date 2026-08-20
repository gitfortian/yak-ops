import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  Button,
  ConfigProvider,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Pagination,
  Select,
  Spin,
  Table,
  Tooltip,
  message,
} from 'antd';
import moment from 'moment';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  getDevelopmentTaskExecution,
  listDevelopmentTaskExecutions,
} from '../service';
import type {
  DevelopmentTaskExecutionDetail,
  DevelopmentTaskExecutionStatus,
  DevelopmentTaskExecutionSummary,
  DevelopmentTaskType,
} from '../types';

const { RangePicker } = DatePicker;

const statusTabs: Array<{ label: string; value?: DevelopmentTaskExecutionStatus }> = [
  { label: '全部记录' },
  { label: '运行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
];

const taskTypeOptions = [
  { label: 'SQL', value: 'SQL' },
  { label: 'SHELL', value: 'SHELL' },
  { label: 'PYTHON', value: 'PYTHON' },
  { label: 'JAVA', value: 'JAVA' },
  { label: 'HTTP', value: 'HTTP' },
];

const triggerOptions = [
  { label: '手动运行', value: 'MANUAL' },
  { label: '工作流', value: 'WORKFLOW' },
  { label: '调度', value: 'SCHEDULE' },
];

const statusLabel: Record<string, string> = {
  PENDING: '等待中',
  RUNNING: '运行中',
  SUCCESS: '成功',
  FAILED: '失败',
  CANCELLED: '已取消',
  TIMEOUT: '超时',
};

const statusClassName: Record<string, string> = {
  PENDING: 'bg-[#f2f4f7] text-[#667085]',
  RUNNING: 'bg-[#eff8ff] text-[#175cd3]',
  SUCCESS: 'bg-[#ecfdf3] text-[#027a48]',
  FAILED: 'bg-[#fef3f2] text-[#b42318]',
  CANCELLED: 'bg-[#f2f4f7] text-[#667085]',
  TIMEOUT: 'bg-[#fff6ed] text-[#c4320a]',
};

const StatusBadge = ({ status }: { status?: string }) => {
  const normalized = String(status || '').toUpperCase();
  return (
    <span
      className={[
        'inline-flex h-6 items-center rounded-md px-2 text-[12px] font-medium',
        statusClassName[normalized] || 'bg-[#f2f4f7] text-[#667085]',
      ].join(' ')}
    >
      {statusLabel[normalized] || normalized || '-'}
    </span>
  );
};

const formatDuration = (duration?: number | null) => {
  if (duration === null || duration === undefined) return '-';
  if (duration < 1000) return `${duration} ms`;
  if (duration < 60_000) return `${(duration / 1000).toFixed(duration < 10_000 ? 2 : 1)} s`;
  return `${(duration / 60_000).toFixed(1)} min`;
};

const ExecutionHistoryPage = () => {
  const [records, setRecords] = useState<DevelopmentTaskExecutionSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState<DevelopmentTaskExecutionStatus | undefined>();
  const [taskType, setTaskType] = useState<DevelopmentTaskType | undefined>();
  const [triggerType, setTriggerType] = useState<string | undefined>();
  const [dateRange, setDateRange] = useState<any>();
  const [keyword, setKeyword] = useState('');
  const [keywordDraft, setKeywordDraft] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<DevelopmentTaskExecutionDetail>();

  const loadRecords = useCallback(async () => {
    setLoading(true);
    try {
      const response = await listDevelopmentTaskExecutions({
        pageNo,
        pageSize,
        keyword: keyword || undefined,
        status,
        taskType,
        triggerType,
        startTime: dateRange?.[0]?.format?.('YYYY-MM-DD 00:00:00'),
        endTime: dateRange?.[1]?.format?.('YYYY-MM-DD 23:59:59'),
      });
      setRecords(response.data?.records || []);
      setTotal(response.data?.total || 0);
    } catch {
      message.error('查询运行记录失败');
    } finally {
      setLoading(false);
    }
  }, [dateRange, keyword, pageNo, pageSize, refreshKey, status, taskType, triggerType]);

  useEffect(() => {
    void loadRecords();
  }, [loadRecords]);

  const applyStatus = (value?: DevelopmentTaskExecutionStatus) => {
    setStatus(value);
    setPageNo(1);
  };

  const search = () => {
    setKeyword(keywordDraft.trim());
    setPageNo(1);
  };

  const reset = () => {
    setKeyword('');
    setKeywordDraft('');
    setStatus(undefined);
    setTaskType(undefined);
    setTriggerType(undefined);
    setDateRange(undefined);
    setPageNo(1);
  };

  const openDetail = async (record: DevelopmentTaskExecutionSummary) => {
    setDetailOpen(true);
    setDetail(undefined);
    setDetailLoading(true);
    try {
      const response = await getDevelopmentTaskExecution(record.id);
      setDetail(response.data);
    } catch {
      message.error('读取运行详情失败');
    } finally {
      setDetailLoading(false);
    }
  };

  const columns = useMemo(
    () => [
      {
        title: '任务名称 / 节点 ID',
        dataIndex: 'taskName',
        width: 240,
        render: (_: unknown, record: DevelopmentTaskExecutionSummary) => (
          <div className="min-w-0 py-0.5">
            <button
              type="button"
              className="max-w-full truncate border-0 bg-transparent p-0 text-left text-[13px] font-medium text-[#344054] hover:text-[#161823]"
              title={record.taskName}
              onClick={() => history.push('/data-development')}
            >
              {record.taskName || '-'}
            </button>
            <div className="mt-0.5 truncate text-[11px] text-[#98a2b3]">节点 ID：{record.nodeId}</div>
          </div>
        ),
      },
      {
        title: '类型',
        dataIndex: 'taskType',
        width: 90,
        render: (value: string) => <span className="text-[12px] font-medium text-[#475467]">{value || '-'}</span>,
      },
      {
        title: '触发方式',
        dataIndex: 'triggerType',
        width: 105,
        render: (value: string) => (
          <span className="text-[12px] text-[#667085]">
            {value === 'MANUAL' ? '手动运行' : value === 'WORKFLOW' ? '工作流' : value === 'SCHEDULE' ? '调度' : value || '-'}
          </span>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        align: 'center' as const,
        render: (value: string) => <StatusBadge status={value} />,
      },
      {
        title: '运行实例',
        dataIndex: 'runtimeExecutionId',
        width: 205,
        ellipsis: true,
        render: (value?: string | null) => (
          <Tooltip title={value || undefined}>
            <span className="font-mono text-[11px] text-[#667085]">{value || '-'}</span>
          </Tooltip>
        ),
      },
      {
        title: '执行人',
        dataIndex: 'operatorName',
        width: 120,
        ellipsis: true,
        render: (value?: string | null) => <span className="text-[12px] text-[#667085]">{value || '-'}</span>,
      },
      {
        title: '耗时',
        dataIndex: 'durationMs',
        width: 105,
        align: 'right' as const,
        render: (value?: number | null) => <span className="text-[12px] text-[#667085]">{formatDuration(value)}</span>,
      },
      {
        title: '开始时间',
        dataIndex: 'startTime',
        width: 170,
        render: (value?: string | null) => (
          <span className="whitespace-nowrap text-[12px] text-[#98a2b3]">{value ? moment(value).format('YYYY-MM-DD HH:mm:ss') : '-'}</span>
        ),
      },
      {
        title: '操作',
        key: 'action',
        width: 90,
        fixed: 'right' as const,
        render: (_: unknown, record: DevelopmentTaskExecutionSummary) => (
          <Button type="link" size="small" className="!px-0 !text-[12px] !text-[#475467]" onClick={() => void openDetail(record)}>
            查看详情
          </Button>
        ),
      },
    ],
    [],
  );

  return (
    <ConfigProvider
      theme={{
        token: { borderRadius: 8, colorBorder: '#f0f0f0', colorBgContainer: '#ffffff' },
        components: {
          Input: { activeShadow: 'none' },
          Select: { activeOutlineColor: 'transparent' },
        },
      }}
    >
      <div className="flex min-h-[calc(100vh-64px)] flex-col bg-white px-5 pt-4">
        <div className="flex items-center justify-between">
          <h1 className="m-0 text-[17px] font-semibold text-[#161823]">运行记录</h1>
          <Button icon={<ReloadOutlined />} onClick={() => setRefreshKey((value) => value + 1)}>
            刷新
          </Button>
        </div>

        <div className="mt-3 border-b border-[#f0f0f0]">
          <div className="flex min-h-[54px] items-center justify-between gap-4 py-2">
            <div className="flex shrink-0 items-center gap-1 rounded-lg bg-[#f5f5f6] p-1">
              {statusTabs.map((item) => {
                const active = status === item.value;
                return (
                  <button
                    key={item.label}
                    type="button"
                    onClick={() => applyStatus(item.value)}
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
                placeholder="搜索任务 / 实例 / 执行人"
                className="!h-9 !w-[230px] !min-w-[190px]"
                onChange={(event) => setKeywordDraft(event.target.value)}
                onPressEnter={search}
              />
              <Select
                allowClear
                variant="filled"
                value={taskType}
                options={taskTypeOptions}
                placeholder="任务类型"
                className="!h-9 !w-[130px] !min-w-[120px]"
                onChange={(value) => {
                  setTaskType(value);
                  setPageNo(1);
                }}
              />
              <Select
                allowClear
                variant="filled"
                value={triggerType}
                options={triggerOptions}
                placeholder="触发方式"
                className="!h-9 !w-[130px] !min-w-[120px]"
                onChange={(value) => {
                  setTriggerType(value);
                  setPageNo(1);
                }}
              />
              <RangePicker
                allowClear
                variant="filled"
                value={dateRange}
                format="YYYY-MM-DD"
                placeholder={['开始日期', '结束日期']}
                className="!h-9 !w-[250px] !min-w-[230px]"
                onChange={(value) => {
                  setDateRange(value);
                  setPageNo(1);
                }}
              />
              <Button type="primary" onClick={search}>查询</Button>
              <Button onClick={reset}>重置</Button>
            </div>
          </div>
        </div>

        <div className="min-h-0 flex-1 pt-4">
          <Table
            rowKey="id"
            size="small"
            bordered
            loading={loading}
            columns={columns}
            dataSource={records}
            pagination={false}
            scroll={{ x: 1320, y: 'calc(100vh - 300px)' }}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无运行记录" /> }}
          />
        </div>

        <div className="flex h-16 shrink-0 items-center justify-between border-t border-[#f0f0f0]">
          <span className="text-[12px] text-[#98a2b3]">共 {total} 条运行记录</span>
          <Pagination
            current={pageNo}
            pageSize={pageSize}
            total={total}
            showSizeChanger
            showQuickJumper
            pageSizeOptions={[10, 20, 50, 100]}
            onChange={(page, size) => {
              setPageNo(size === pageSize ? page : 1);
              setPageSize(size);
            }}
          />
        </div>
      </div>

      <Drawer
        title="运行详情"
        placement="right"
        width={720}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
      >
        {detailLoading ? (
          <div className="flex h-64 items-center justify-center"><Spin size="small" /></div>
        ) : detail ? (
          <div className="space-y-6">
            <Descriptions size="small" column={2} bordered>
              <Descriptions.Item label="任务名称">{detail.taskName}</Descriptions.Item>
              <Descriptions.Item label="节点 ID">{detail.nodeId}</Descriptions.Item>
              <Descriptions.Item label="任务类型">{detail.taskType}</Descriptions.Item>
              <Descriptions.Item label="状态"><StatusBadge status={detail.status} /></Descriptions.Item>
              <Descriptions.Item label="触发方式">{detail.triggerType === 'MANUAL' ? '手动运行' : detail.triggerType}</Descriptions.Item>
              <Descriptions.Item label="执行人">{detail.operatorName || '-'}</Descriptions.Item>
              <Descriptions.Item label="运行实例" span={2}>{detail.runtimeExecutionId || '-'}</Descriptions.Item>
              <Descriptions.Item label="开始时间">{detail.startTime ? moment(detail.startTime).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
              <Descriptions.Item label="耗时">{formatDuration(detail.durationMs)}</Descriptions.Item>
            </Descriptions>

            {detail.errorMessage ? (
              <section>
                <div className="mb-2 text-[13px] font-semibold text-[#344054]">错误信息</div>
                <div className="rounded-md bg-[#fef3f2] px-3 py-2 text-[12px] leading-5 text-[#b42318]">{detail.errorMessage}</div>
              </section>
            ) : null}

            <section>
              <div className="mb-2 text-[13px] font-semibold text-[#344054]">运行内容</div>
              <pre className="max-h-[280px] overflow-auto rounded-md border border-[#eaecf0] bg-[#fafafa] p-3 text-[12px] leading-5 text-[#344054]">{detail.content || '-'}</pre>
            </section>

            <section>
              <div className="mb-2 text-[13px] font-semibold text-[#344054]">运行配置</div>
              <pre className="max-h-[220px] overflow-auto rounded-md border border-[#eaecf0] bg-[#fafafa] p-3 text-[12px] leading-5 text-[#344054]">{detail.configJson || '{}'}</pre>
            </section>

            <section>
              <div className="mb-2 text-[13px] font-semibold text-[#344054]">运行输出</div>
              <pre className="max-h-[360px] overflow-auto rounded-md border border-[#eaecf0] bg-[#fafafa] p-3 text-[12px] leading-5 text-[#344054]">{JSON.stringify(detail.output || {}, null, 2)}</pre>
            </section>
          </div>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="运行详情不存在" />
        )}
      </Drawer>
    </ConfigProvider>
  );
};

export default ExecutionHistoryPage;
