import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { API_SUCCESS_CODE } from '@/services/http/response';
import {
  Button,
  ConfigProvider,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Pagination,
  Popconfirm,
  Select,
  Spin,
  Table,
  Tooltip,
  message,
} from 'antd';
import moment from 'moment';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  activateDevelopmentReleaseRevision,
  getDevelopmentRelease,
  listDevelopmentReleases,
  offlineDevelopmentRelease,
  onlineDevelopmentRelease,
} from '../service';
import type {
  DevelopmentId,
  DevelopmentReleaseDetail,
  DevelopmentReleaseStatus,
  DevelopmentReleaseSummary,
  DevelopmentTaskRevisionSummary,
  DevelopmentTaskType,
} from '../types';

const taskTypeOptions = [
  { label: 'SQL', value: 'SQL' },
  { label: 'SHELL', value: 'SHELL' },
  { label: 'PYTHON', value: 'PYTHON' },
  { label: 'JAVA', value: 'JAVA' },
  { label: 'HTTP', value: 'HTTP' },
];

const statusTabs: Array<{
  label: string;
  value: 'ALL' | 'ONLINE' | 'OFFLINE';
}> = [
  { label: '全部任务', value: 'ALL' },
  { label: '已上线', value: 'ONLINE' },
  { label: '已下线', value: 'OFFLINE' },
];

const statusLabel: Record<string, string> = {
  ONLINE: '已上线',
  OFFLINE: '已下线',
  DISABLED: '已禁用',
};

const statusClassName: Record<string, string> = {
  ONLINE: 'bg-[#ecfdf3] text-[#027a48]',
  OFFLINE: 'bg-[#f2f4f7] text-[#667085]',
  DISABLED: 'bg-[#fff6ed] text-[#c4320a]',
};

const StatusBadge = ({ status }: { status?: DevelopmentReleaseStatus }) => {
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

const ReleaseCenterPage = () => {
  const [records, setRecords] = useState<DevelopmentReleaseSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [onlineCount, setOnlineCount] = useState(0);
  const [offlineCount, setOfflineCount] = useState(0);
  const [status, setStatus] = useState<'ALL' | 'ONLINE' | 'OFFLINE'>('ALL');
  const [taskType, setTaskType] = useState<DevelopmentTaskType | undefined>();
  const [keyword, setKeyword] = useState('');
  const [keywordDraft, setKeywordDraft] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<DevelopmentReleaseDetail>();
  const [actionLoading, setActionLoading] = useState('');

  const responseData = <T,>(
    response: { code?: number; data?: T; msg?: string; message?: string },
    fallback: string,
  ): T => {
    if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
      throw new Error(response?.message || response?.msg || fallback);
    }
    return response.data;
  };

  const loadRecords = useCallback(async () => {
    setLoading(true);
    try {
      const response = await listDevelopmentReleases({
        pageNo,
        pageSize,
        status,
        taskType,
        keyword: keyword || undefined,
      });
      const data = responseData(response, '查询发布中心失败');
      setRecords(data.records || []);
      setTotal(data.total || 0);
      setOnlineCount(data.onlineCount || 0);
      setOfflineCount(data.offlineCount || 0);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '查询发布中心失败');
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo, pageSize, refreshKey, status, taskType]);

  useEffect(() => {
    void loadRecords();
  }, [loadRecords]);

  const loadDetail = useCallback(async (assetId: DevelopmentId) => {
    setDetailLoading(true);
    try {
      const response = await getDevelopmentRelease(assetId);
      const data = responseData(response, '读取发布详情失败');
      setDetail(data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '读取发布详情失败');
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const openDetail = (record: DevelopmentReleaseSummary) => {
    setDetailOpen(true);
    setDetail(undefined);
    void loadDetail(record.assetId);
  };

  const search = () => {
    setKeyword(keywordDraft.trim());
    setPageNo(1);
  };

  const reset = () => {
    setKeyword('');
    setKeywordDraft('');
    setStatus('ALL');
    setTaskType(undefined);
    setPageNo(1);
  };

  const updateReleaseStatus = async (
    record: DevelopmentReleaseSummary,
    target: 'ONLINE' | 'OFFLINE',
  ) => {
    const actionKey = `${record.assetId}:${target}`;
    setActionLoading(actionKey);
    try {
      const response = target === 'ONLINE'
        ? await onlineDevelopmentRelease(record.assetId)
        : await offlineDevelopmentRelease(record.assetId);
      responseData(response, target === 'ONLINE' ? '重新上线失败' : '下线失败');
      message.success(target === 'ONLINE' ? '任务已重新上线' : '任务已下线');
      await loadRecords();
      if (detailOpen && detail?.release.assetId === record.assetId) {
        await loadDetail(record.assetId);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布状态更新失败');
    } finally {
      setActionLoading('');
    }
  };

  const activateRevision = async (
    assetId: DevelopmentId,
    revision: DevelopmentTaskRevisionSummary,
  ) => {
    const actionKey = `${assetId}:revision:${revision.revisionNo}`;
    setActionLoading(actionKey);
    try {
      const response = await activateDevelopmentReleaseRevision(assetId, revision.revisionNo);
      responseData(response, '切换线上版本失败');
      message.success(`已切换到 V${revision.revisionNo}`);
      await Promise.all([loadRecords(), loadDetail(assetId)]);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '切换线上版本失败');
    } finally {
      setActionLoading('');
    }
  };

  const columns = useMemo(
    () => [
      {
        title: '任务名称 / 节点 ID',
        dataIndex: 'taskName',
        width: 260,
        render: (_: unknown, record: DevelopmentReleaseSummary) => (
          <div className="min-w-0 py-0.5">
            <button
              type="button"
              className="max-w-full truncate border-0 bg-transparent p-0 text-left text-[13px] font-medium text-[#344054] hover:text-[#161823]"
              onClick={() => openDetail(record)}
            >
              {record.taskName || '-'}
            </button>
            <div className="mt-0.5 truncate text-[11px] text-[#98a2b3]">
              节点 ID：{record.nodeId}
            </div>
          </div>
        ),
      },
      {
        title: '类型',
        dataIndex: 'taskType',
        width: 90,
        render: (value: string) => (
          <span className="text-[12px] font-medium text-[#475467]">{value || '-'}</span>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        align: 'center' as const,
        render: (value: DevelopmentReleaseStatus) => <StatusBadge status={value} />,
      },
      {
        title: '当前发布版本',
        dataIndex: 'currentRevisionNo',
        width: 150,
        render: (_: unknown, record: DevelopmentReleaseSummary) => (
          <div>
            <div className="text-[13px] font-medium text-[#344054]">V{record.currentRevisionNo}</div>
            {record.hasNewerRevision ? (
              <div className="mt-0.5 text-[11px] text-[#b54708]">
                最新版本 V{record.latestRevisionNo}
              </div>
            ) : (
              <div className="mt-0.5 text-[11px] text-[#98a2b3]">当前为最新版本</div>
            )}
          </div>
        ),
      },
      {
        title: '版本校验',
        dataIndex: 'checksum',
        width: 180,
        render: (value?: string) => (
          <Tooltip title={value || undefined}>
            <span className="font-mono text-[11px] text-[#667085]">
              {value ? value.slice(0, 12) : '-'}
            </span>
          </Tooltip>
        ),
      },
      {
        title: '版本发布时间',
        dataIndex: 'revisionCreateTime',
        width: 170,
        render: (value?: string | null) => (
          <span className="whitespace-nowrap text-[12px] text-[#667085]">
            {value ? moment(value).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </span>
        ),
      },
      {
        title: '状态更新时间',
        dataIndex: 'updateTime',
        width: 170,
        render: (value?: string | null) => (
          <span className="whitespace-nowrap text-[12px] text-[#98a2b3]">
            {value ? moment(value).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </span>
        ),
      },
      {
        title: '操作',
        key: 'action',
        width: 180,
        fixed: 'right' as const,
        render: (_: unknown, record: DevelopmentReleaseSummary) => (
          <div className="flex items-center gap-3">
            <Button
              type="link"
              size="small"
              className="!px-0 !text-[12px] !text-[#475467]"
              onClick={() => openDetail(record)}
            >
              版本详情
            </Button>
            {record.status === 'ONLINE' ? (
              <Popconfirm
                title="确认下线这个任务？"
                description="下线后不会再出现在新的工作流任务选择中，历史版本仍会保留。"
                okText="确认下线"
                cancelText="取消"
                onConfirm={() => updateReleaseStatus(record, 'OFFLINE')}
              >
                <Button
                  type="link"
                  size="small"
                  loading={actionLoading === `${record.assetId}:OFFLINE`}
                  className="!px-0 !text-[12px] !text-[#667085]"
                >
                  下线
                </Button>
              </Popconfirm>
            ) : record.status === 'OFFLINE' ? (
              <Popconfirm
                title="确认重新上线？"
                description={`将重新上线当前版本 V${record.currentRevisionNo}。`}
                okText="确认上线"
                cancelText="取消"
                onConfirm={() => updateReleaseStatus(record, 'ONLINE')}
              >
                <Button
                  type="link"
                  size="small"
                  loading={actionLoading === `${record.assetId}:ONLINE`}
                  className="!px-0 !text-[12px] !text-[#475467]"
                >
                  重新上线
                </Button>
              </Popconfirm>
            ) : null}
          </div>
        ),
      },
    ],
    [actionLoading, detail, detailOpen, loadDetail, loadRecords],
  );

  const tabCount = (value: 'ALL' | 'ONLINE' | 'OFFLINE') => {
    if (value === 'ONLINE') return onlineCount;
    if (value === 'OFFLINE') return offlineCount;
    return onlineCount + offlineCount;
  };

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
          <div>
            <h1 className="m-0 text-[17px] font-semibold text-[#161823]">发布中心</h1>
            <div className="mt-1 text-[12px] text-[#98a2b3]">
              管理数据开发任务的线上状态、当前版本和历史不可变版本
            </div>
          </div>
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
                    key={item.value}
                    type="button"
                    onClick={() => {
                      setStatus(item.value);
                      setPageNo(1);
                    }}
                    className={[
                      'h-8 rounded-md px-3.5 text-[13px] font-medium transition-all',
                      active
                        ? 'bg-white text-[#fe2c55] shadow-[0_1px_4px_rgba(16,24,40,0.08)]'
                        : 'text-[#667085] hover:bg-white/70 hover:text-[#344054]',
                    ].join(' ')}
                  >
                    {item.label}
                    <span className="ml-1 text-[11px] opacity-70">{tabCount(item.value)}</span>
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
                placeholder="搜索任务名称 / 节点 ID"
                className="!h-9 !w-[240px] !min-w-[200px]"
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
              <Button type="primary" onClick={search}>查询</Button>
              <Button onClick={reset}>重置</Button>
            </div>
          </div>
        </div>

        <div className="min-h-0 flex-1 pt-4">
          <Table
            rowKey="assetId"
            size="small"
            bordered
            loading={loading}
            columns={columns}
            dataSource={records}
            pagination={false}
            scroll={{ x: 1420, y: 'calc(100vh - 315px)' }}
            locale={{
              emptyText: (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已发布的数据开发任务" />
              ),
            }}
          />
        </div>

        <div className="flex h-16 shrink-0 items-center justify-between border-t border-[#f0f0f0]">
          <span className="text-[12px] text-[#98a2b3]">共 {total} 条发布任务</span>
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
        title="发布详情"
        placement="right"
        width={780}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
      >
        {detailLoading ? (
          <div className="flex h-64 items-center justify-center"><Spin size="small" /></div>
        ) : detail ? (
          <div className="space-y-6">
            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="任务名称">{detail.release.taskName}</Descriptions.Item>
              <Descriptions.Item label="节点 ID">{detail.release.nodeId}</Descriptions.Item>
              <Descriptions.Item label="任务类型">{detail.release.taskType}</Descriptions.Item>
              <Descriptions.Item label="状态"><StatusBadge status={detail.release.status} /></Descriptions.Item>
              <Descriptions.Item label="当前版本">V{detail.release.currentRevisionNo}</Descriptions.Item>
              <Descriptions.Item label="最新版本">V{detail.release.latestRevisionNo}</Descriptions.Item>
              <Descriptions.Item label="版本发布时间">
                {detail.release.revisionCreateTime
                  ? moment(detail.release.revisionCreateTime).format('YYYY-MM-DD HH:mm:ss')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="状态更新时间">
                {detail.release.updateTime
                  ? moment(detail.release.updateTime).format('YYYY-MM-DD HH:mm:ss')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="Checksum" span={2}>
                <span className="break-all font-mono text-[11px] text-[#667085]">
                  {detail.release.checksum || '-'}
                </span>
              </Descriptions.Item>
            </Descriptions>

            <section>
              <div className="mb-2 text-[13px] font-medium text-[#344054]">当前发布内容</div>
              <pre className="max-h-[260px] overflow-auto rounded-lg border border-[#eaecf0] bg-[#fafafa] p-3 text-[12px] leading-5 text-[#475467]">
                {detail.currentRevision.definition?.content || '暂无内容'}
              </pre>
            </section>

            <section>
              <div className="mb-2 text-[13px] font-medium text-[#344054]">运行配置</div>
              <pre className="max-h-[180px] overflow-auto rounded-lg border border-[#eaecf0] bg-[#fafafa] p-3 text-[12px] leading-5 text-[#475467]">
                {detail.currentRevision.definition?.configJson || '{}'}
              </pre>
            </section>

            <section>
              <div className="mb-2 flex items-center justify-between">
                <div className="text-[13px] font-medium text-[#344054]">版本历史</div>
                <div className="text-[11px] text-[#98a2b3]">历史版本不可变，可切换线上指针</div>
              </div>
              <Table
                rowKey="id"
                size="small"
                bordered
                pagination={false}
                dataSource={detail.revisions || []}
                columns={[
                  {
                    title: '版本',
                    dataIndex: 'revisionNo',
                    width: 90,
                    render: (value: number) => (
                      <span className="font-medium text-[#344054]">V{value}</span>
                    ),
                  },
                  {
                    title: '发布时间',
                    dataIndex: 'createTime',
                    width: 170,
                    render: (value?: string) => (
                      <span className="text-[12px] text-[#667085]">
                        {value ? moment(value).format('YYYY-MM-DD HH:mm:ss') : '-'}
                      </span>
                    ),
                  },
                  {
                    title: 'Checksum',
                    dataIndex: 'checksum',
                    ellipsis: true,
                    render: (value?: string) => (
                      <Tooltip title={value || undefined}>
                        <span className="font-mono text-[11px] text-[#98a2b3]">
                          {value ? value.slice(0, 14) : '-'}
                        </span>
                      </Tooltip>
                    ),
                  },
                  {
                    title: '操作',
                    width: 125,
                    render: (_: unknown, revision: DevelopmentTaskRevisionSummary) => {
                      const current = revision.revisionNo === detail.release.currentRevisionNo;
                      if (current) {
                        return <span className="text-[12px] text-[#98a2b3]">当前线上版本</span>;
                      }
                      const buttonText = detail.release.status === 'OFFLINE' ? '切换并上线' : '切换版本';
                      return (
                        <Popconfirm
                          title={`确认切换到 V${revision.revisionNo}？`}
                          description={
                            revision.revisionNo < detail.release.currentRevisionNo
                              ? '这是历史版本。切换后新的工作流/调度引用将使用该版本。'
                              : '切换后新的工作流/调度引用将使用该版本。'
                          }
                          okText="确认切换"
                          cancelText="取消"
                          onConfirm={() => activateRevision(detail.release.assetId, revision)}
                        >
                          <Button
                            type="link"
                            size="small"
                            loading={actionLoading === `${detail.release.assetId}:revision:${revision.revisionNo}`}
                            className="!px-0 !text-[12px] !text-[#475467]"
                          >
                            {buttonText}
                          </Button>
                        </Popconfirm>
                      );
                    },
                  },
                ]}
              />
            </section>
          </div>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未读取到发布详情" />
        )}
      </Drawer>
    </ConfigProvider>
  );
};

export default ReleaseCenterPage;
