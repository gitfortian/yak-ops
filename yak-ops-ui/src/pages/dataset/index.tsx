import YakOpsEmpty from '@/components/YakOpsEmpty';
import { YakButton } from '@/components/ui';
import {
  listDatasetsForManagement,
  offlineDataset,
  onlineDataset,
  type DatasetManagementItem,
  type DatasetSourceType,
  type DatasetStatus,
} from '@/services/dataset';
import { BRAND_THEME } from '@/styles/brand';
import { history } from '@umijs/max';
import { ConfigProvider, Input, Popconfirm, Select, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Database, GitBranch, RefreshCw, Search } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

const SOURCE_TYPE_LABELS: Record<DatasetSourceType, string> = {
  QUERY_REVISION: 'SQL 任务',
  SQL_QUERY: 'Standalone SQL',
  TABLE: '数据表',
  VIEW: '视图',
};

const formatDateTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

export default function DatasetManagementPage() {
  const [datasets, setDatasets] = useState<DatasetManagementItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<'ALL' | DatasetStatus>('ALL');
  const [sourceType, setSourceType] = useState<'ALL' | DatasetSourceType>('ALL');
  const [statusUpdatingId, setStatusUpdatingId] = useState('');

  const loadDatasets = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      setDatasets(await listDatasetsForManagement());
    } catch (error) {
      setDatasets([]);
      setLoadError(error instanceof Error ? error.message : '加载 Dataset 失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDatasets();
  }, [loadDatasets]);

  const filteredDatasets = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    return datasets.filter((dataset) => {
      if (status !== 'ALL' && dataset.status !== status) return false;
      if (sourceType !== 'ALL' && dataset.currentVersion?.sourceType !== sourceType) return false;
      if (!normalized) return true;
      return [
        dataset.name,
        dataset.description,
        dataset.currentVersion?.dataSourceId || '',
        dataset.currentVersion?.sourceTaskAssetId || '',
        ...dataset.fields.flatMap((field) => [
          field.displayName,
          field.physicalName,
          field.description || '',
        ]),
      ].some((value) => value.toLowerCase().includes(normalized));
    });
  }, [datasets, keyword, sourceType, status]);

  const toggleStatus = useCallback(async (dataset: DatasetManagementItem) => {
    setStatusUpdatingId(dataset.id);
    try {
      if (dataset.status === 'ONLINE') {
        await offlineDataset(dataset.id);
        message.success(`Dataset「${dataset.name}」已下线`);
      } else {
        await onlineDataset(dataset.id);
        message.success(`Dataset「${dataset.name}」已上线`);
      }
      await loadDatasets();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '更新 Dataset 状态失败');
    } finally {
      setStatusUpdatingId('');
    }
  }, [loadDatasets]);

  const columns = useMemo<ColumnsType<DatasetManagementItem>>(() => [
    {
      title: 'Dataset',
      dataIndex: 'name',
      minWidth: 260,
      render: (_value, record) => (
        <div className="min-w-0">
          <button
            type="button"
            className="max-w-full truncate bg-transparent p-0 text-left text-[14px] font-medium text-[#161823] hover:text-[#fe2c55]"
            onClick={(event) => {
              event.stopPropagation();
              history.push(`/dataset/${record.id}`);
            }}
          >
            {record.name}
          </button>
          <div className="mt-1 line-clamp-1 text-[12px] text-[#8a8f99]">
            {record.description || '暂无描述'}
          </div>
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: DatasetStatus) => (
        <Tag bordered={false} color={value === 'ONLINE' ? 'success' : 'default'}>
          {value === 'ONLINE' ? '已上线' : '已下线'}
        </Tag>
      ),
    },
    {
      title: '来源类型',
      width: 150,
      render: (_, record) => record.currentVersion
        ? SOURCE_TYPE_LABELS[record.currentVersion.sourceType]
        : '-',
    },
    {
      title: '当前版本',
      width: 110,
      render: (_, record) => record.currentVersion
        ? `DV${record.currentVersion.versionNo}`
        : '-',
    },
    {
      title: '字段数',
      dataIndex: 'fields',
      width: 90,
      render: (fields: DatasetManagementItem['fields']) => fields.length,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 180,
      render: (value?: string, record) => formatDateTime(value || record.createTime),
    },
    {
      title: '操作',
      width: 190,
      fixed: 'right',
      render: (_, record) => (
        <div className="flex items-center gap-2" onClick={(event) => event.stopPropagation()}>
          <YakButton onClick={() => history.push(`/dataset/${record.id}`)}>
            详情
          </YakButton>
          <Popconfirm
            title={record.status === 'ONLINE' ? '确认下线这个 Dataset？' : '确认上线这个 Dataset？'}
            description={record.status === 'ONLINE'
              ? '下线后 Analysis、仪表盘和大屏将无法继续查询。'
              : '上线后可重新用于下游消费。'}
            okText="确认"
            cancelText="取消"
            onConfirm={() => void toggleStatus(record)}
          >
            <YakButton loading={statusUpdatingId === record.id}>
              {record.status === 'ONLINE' ? '下线' : '上线'}
            </YakButton>
          </Popconfirm>
        </div>
      ),
    },
  ], [statusUpdatingId, toggleStatus]);

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="min-h-[calc(100vh-64px)] bg-white text-[#161823]">
        <header className="border-b border-[#e4e7ec] px-6 py-4">
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex min-w-0 flex-1 items-center gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[8px] bg-[#f4f5f7] text-[#667085]">
                <Database size={18} />
              </div>
              <div className="min-w-0">
                <h1 className="m-0 text-[22px] font-semibold leading-8">数据集</h1>
                <p className="m-0 mt-0.5 text-[13px] text-[#667085]">
                  统一管理可被 Analysis、仪表盘、数字化大屏和数据服务消费的稳定数据契约。
                </p>
              </div>
            </div>
            <YakButton icon={<RefreshCw size={14} />} onClick={() => void loadDatasets()}>
              刷新
            </YakButton>
            <YakButton type="primary" icon={<GitBranch size={14} />} href="/data-development/releases">
              发布中心
            </YakButton>
          </div>
        </header>

        <main className="p-6">
          <div className="mb-4 flex flex-wrap items-center gap-3 border border-[#e4e7ec] bg-[#fafbfc] p-3">
            <Input
              allowClear
              value={keyword}
              prefix={<Search size={14} />}
              placeholder="搜索 Dataset、字段、来源"
              className="w-[320px]"
              onChange={(event) => setKeyword(event.target.value)}
            />
            <Select
              value={status}
              className="w-[130px]"
              options={[
                { value: 'ALL', label: '全部状态' },
                { value: 'ONLINE', label: '已上线' },
                { value: 'OFFLINE', label: '已下线' },
              ]}
              onChange={setStatus}
            />
            <Select
              value={sourceType}
              className="w-[170px]"
              options={[
                { value: 'ALL', label: '全部来源' },
                ...Object.entries(SOURCE_TYPE_LABELS).map(([value, label]) => ({ value, label })),
              ]}
              onChange={setSourceType}
            />
            <div className="ml-auto text-[12px] text-[#667085]">
              共 {filteredDatasets.length} 个 Dataset
            </div>
          </div>

          {loadError && !loading ? (
            <div className="flex min-h-[420px] items-center justify-center border border-[#e4e7ec]">
              <YakOpsEmpty
                width={180}
                height={120}
                title="Dataset 加载失败"
                description={loadError}
                showCaption
              />
            </div>
          ) : (
            <Table
              rowKey="id"
              loading={loading}
              columns={columns}
              dataSource={filteredDatasets}
              scroll={{ x: 1120 }}
              pagination={{ pageSize: 20, showSizeChanger: true }}
              onRow={(record) => ({
                onClick: () => history.push(`/dataset/${record.id}`),
                className: 'cursor-pointer',
              })}
              locale={{
                emptyText: (
                  <div className="flex min-h-[360px] items-center justify-center">
                    <YakOpsEmpty
                      width={180}
                      height={120}
                      title="暂无 Dataset"
                      description="请先从数据开发发布中心发布可消费的数据集。"
                      showCaption
                    />
                  </div>
                ),
              }}
            />
          )}
        </main>
      </div>
    </ConfigProvider>
  );
}
