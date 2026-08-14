import { BRAND_THEME } from '@/styles/brand';
import type { ColumnsType } from 'antd/es/table';
import {
  Button,
  ConfigProvider,
  Empty,
  Input,
  message,
  Popconfirm,
  Select,
  Spin,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import {
  BarChart3,
  Clock3,
  Database,
  GitBranch,
  History,
  Layers3,
  RefreshCw,
  Rows3,
  Search,
  TableProperties,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  fetchCatalogDatasets,
  offlineCatalogDataset,
  onlineCatalogDataset,
  type CatalogDataset,
  type CatalogDatasetFieldRole,
  type CatalogDatasetSourceType,
  type CatalogDatasetStatus,
} from './service';

const sourceTypeLabel: Record<CatalogDatasetSourceType, string> = {
  QUERY_REVISION: 'SQL 查询',
  TABLE: '数据表',
  VIEW: '视图',
};

const roleLabel: Record<CatalogDatasetFieldRole, string> = {
  DIMENSION: '维度',
  MEASURE: '指标',
};

const formatTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date).replaceAll('/', '-');
};

const schemaSummary = (dataset: CatalogDataset) => {
  const dimensions = dataset.fields.filter((field) => field.defaultRole === 'DIMENSION').length;
  const metrics = dataset.fields.filter((field) => field.defaultRole === 'MEASURE').length;
  return { fields: dataset.fields.length, dimensions, metrics };
};

const DataCatalogPage = () => {
  const [datasets, setDatasets] = useState<CatalogDataset[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<'ALL' | CatalogDatasetStatus>('ALL');
  const [sourceType, setSourceType] = useState<'ALL' | CatalogDatasetSourceType>('ALL');
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [statusUpdatingId, setStatusUpdatingId] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const values = await fetchCatalogDatasets();
      setDatasets(values);
      setSelectedId((current) => (
        current && values.some((item) => item.id === current)
          ? current
          : values[0]?.id || ''
      ));
    } catch (error) {
      const text = error instanceof Error ? error.message : '加载数据目录失败';
      setLoadError(text);
      setDatasets([]);
      setSelectedId('');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const filteredData = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    return datasets.filter((dataset) => {
      if (status !== 'ALL' && dataset.status !== status) return false;
      if (sourceType !== 'ALL' && dataset.currentVersion?.sourceType !== sourceType) return false;
      if (!normalized) return true;
      return [
        dataset.name,
        dataset.description,
        dataset.currentVersion?.sourceTaskAssetId || '',
        dataset.currentVersion ? String(dataset.currentVersion.sourceTaskRevisionNo) : '',
        ...dataset.fields.flatMap((field) => [field.displayName, field.physicalName, field.description || '']),
      ].some((value) => value.toLowerCase().includes(normalized));
    });
  }, [datasets, keyword, sourceType, status]);

  const selectedDataset = datasets.find((item) => item.id === selectedId)
    ?? filteredData[0];

  const statusCount = useMemo(() => ({
    all: datasets.length,
    online: datasets.filter((item) => item.status === 'ONLINE').length,
    offline: datasets.filter((item) => item.status === 'OFFLINE').length,
  }), [datasets]);

  const updateDatasetStatus = async (dataset: CatalogDataset) => {
    setStatusUpdatingId(dataset.id);
    try {
      if (dataset.status === 'ONLINE') {
        await offlineCatalogDataset(dataset.id);
        message.success(`Dataset「${dataset.name}」已下线`);
      } else {
        await onlineCatalogDataset(dataset.id);
        message.success(`Dataset「${dataset.name}」已上线`);
      }
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '更新 Dataset 状态失败');
    } finally {
      setStatusUpdatingId('');
    }
  };

  const columns: ColumnsType<CatalogDataset> = [
    {
      title: '数据集',
      dataIndex: 'name',
      width: 300,
      render: (_, record) => (
        <div className="min-w-0 py-1">
          <div className="flex items-center gap-2">
            <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-[#f4f5f7] text-[#5b616e]">
              <TableProperties size={14} />
            </div>
            <button
              type="button"
              className="truncate border-0 bg-transparent p-0 text-left text-[14px] font-medium text-[#161823] hover:underline"
              onClick={() => setSelectedId(record.id)}
            >
              {record.name}
            </button>
          </div>
          <div className="ml-9 mt-1 truncate text-[12px] text-[#8a8f99]">
            {record.description || '暂无描述'}
          </div>
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: CatalogDatasetStatus) => (
        <Tag
          bordered={false}
          className={value === 'ONLINE'
            ? 'm-0 bg-[#f1f3f5] text-[#30343b]'
            : 'm-0 bg-[#f5f5f6] text-[#8a8f99]'}
        >
          {value === 'ONLINE' ? '已上线' : '已下线'}
        </Tag>
      ),
    },
    {
      title: '来源',
      width: 190,
      render: (_, record) => {
        const version = record.currentVersion;
        if (!version) return <span className="text-[12px] text-[#a0a4ac]">尚无当前版本</span>;
        return (
          <div>
            <div className="text-[13px] text-[#30343b]">{sourceTypeLabel[version.sourceType]}</div>
            <div className="mt-1 text-[12px] text-[#90949c]">
              TaskAsset #{version.sourceTaskAssetId} · SQL V{version.sourceTaskRevisionNo}
            </div>
          </div>
        );
      },
    },
    {
      title: 'Schema',
      width: 145,
      render: (_, record) => {
        const summary = schemaSummary(record);
        return (
          <div className="space-y-1 text-[12px] text-[#676d78]">
            <div>{summary.fields} 个字段</div>
            <div>{summary.dimensions} 维度 · {summary.metrics} 指标</div>
          </div>
        );
      },
    },
    {
      title: '版本',
      width: 105,
      render: (_, record) => (
        <div>
          <div className="text-[13px] font-medium text-[#30343b]">
            {record.currentVersion ? `DV${record.currentVersion.versionNo}` : '-'}
          </div>
          <div className="mt-1 text-[11px] text-[#92969f]">共 {record.versions.length} 个版本</div>
        </div>
      ),
    },
    {
      title: '消费',
      width: 95,
      render: (_, record) => (
        <div className="flex items-center gap-1.5 text-[12px] text-[#4f5661]">
          <BarChart3 size={13} /> {record.analysisCount} Analysis
        </div>
      ),
    },
    {
      title: '更新时间',
      width: 155,
      render: (_, record) => (
        <div className="flex items-center gap-1.5 text-[12px] text-[#676d78]">
          <Clock3 size={12} /> {formatTime(record.updateTime || record.createTime)}
        </div>
      ),
    },
    {
      title: '操作',
      fixed: 'right',
      width: 180,
      render: (_, record) => (
        <div className="flex items-center gap-1">
          <Tooltip title={record.status === 'ONLINE' ? '使用当前 Dataset 创建图表分析' : 'Dataset 上线后才能创建 Analysis'}>
            <Button
              type="link"
              size="small"
              disabled={record.status !== 'ONLINE' || !record.currentVersion}
              href={record.status === 'ONLINE' && record.currentVersion
                ? `/data-analysis/chart-analysis?datasetId=${encodeURIComponent(record.id)}`
                : undefined}
              onClick={(event) => event.stopPropagation()}
            >
              创建分析
            </Button>
          </Tooltip>
          <Popconfirm
            title={record.status === 'ONLINE' ? '确认下线这个 Dataset？' : '确认上线这个 Dataset？'}
            description={record.status === 'ONLINE'
              ? '下线后现有 Analysis 将无法继续查询这个 Dataset。'
              : '上线后可继续用于图表分析和仪表盘。'}
            okText="确认"
            cancelText="取消"
            onConfirm={() => void updateDatasetStatus(record)}
          >
            <Button
              type="text"
              size="small"
              loading={statusUpdatingId === record.id}
              onClick={(event) => event.stopPropagation()}
            >
              {record.status === 'ONLINE' ? '下线' : '上线'}
            </Button>
          </Popconfirm>
        </div>
      ),
    },
  ];

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="flex h-[calc(100vh-64px)] min-h-[650px] flex-col overflow-hidden bg-white text-[#161823]">
        <div className="flex h-14 shrink-0 items-center justify-between border-b border-[#e8e9ec] px-5">
          <div>
            <h1 className="m-0 text-[20px] font-semibold">数据目录</h1>
            <div className="mt-0.5 text-[12px] text-[#8a8f99]">发现、理解并消费数据开发发布的 Dataset</div>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-[12px] text-[#8a8f99]">共 {datasets.length} 个 Dataset</span>
            <Button
              icon={<RefreshCw size={14} className={loading ? 'animate-spin' : undefined} />}
              disabled={loading}
              onClick={() => void load()}
            >
              刷新
            </Button>
          </div>
        </div>

        <div className="flex min-h-0 flex-1 overflow-hidden">
          <aside className="flex w-[210px] shrink-0 flex-col border-r border-[#e8e9ec] bg-[#fbfbfc]">
            <div className="border-b border-[#eceef1] p-3">
              <Input
                allowClear
                value={keyword}
                prefix={<Search size={14} className="text-[#a0a4ac]" />}
                placeholder="搜索 Dataset"
                onChange={(event) => setKeyword(event.target.value)}
              />
            </div>
            <div className="p-2">
              <div className="px-2 pb-2 pt-1 text-[12px] font-medium text-[#8a8f99]">生命周期</div>
              {[
                { key: 'ALL', label: '全部 Dataset', count: statusCount.all, icon: Layers3 },
                { key: 'ONLINE', label: '已上线', count: statusCount.online, icon: Database },
                { key: 'OFFLINE', label: '已下线', count: statusCount.offline, icon: Database },
              ].map((item) => {
                const Icon = item.icon;
                const active = status === item.key;
                return (
                  <button
                    key={item.key}
                    type="button"
                    onClick={() => setStatus(item.key as 'ALL' | CatalogDatasetStatus)}
                    className={`mb-0.5 flex h-9 w-full items-center rounded-md border-0 px-2.5 text-left text-[13px] transition-colors ${
                      active ? 'bg-white font-medium text-[#161823] shadow-sm' : 'bg-transparent text-[#5f6570] hover:bg-white'
                    }`}
                  >
                    <Icon size={14} className="mr-2" />
                    <span className="flex-1">{item.label}</span>
                    <span className="text-[11px] text-[#a0a4ac]">{item.count}</span>
                  </button>
                );
              })}
            </div>
          </aside>

          <main className="flex min-w-0 flex-1 flex-col overflow-hidden">
            <div className="flex h-12 shrink-0 items-center justify-between gap-3 border-b border-[#e8e9ec] px-4">
              <div className="text-[12px] text-[#8a8f99]">
                找到 <span className="font-medium text-[#30343b]">{filteredData.length}</span> 个 Dataset
              </div>
              <div className="flex items-center gap-2">
                <Input
                  allowClear
                  value={keyword}
                  prefix={<Search size={14} className="text-[#a0a4ac]" />}
                  placeholder="搜索名称、描述、字段、来源资产"
                  className="w-[320px]"
                  onChange={(event) => setKeyword(event.target.value)}
                />
                <Select
                  value={sourceType}
                  className="w-[132px]"
                  onChange={setSourceType}
                  options={[
                    { label: '全部来源', value: 'ALL' },
                    { label: 'SQL 查询', value: 'QUERY_REVISION' },
                    { label: '数据表', value: 'TABLE' },
                    { label: '视图', value: 'VIEW' },
                  ]}
                />
                <Button
                  onClick={() => {
                    setKeyword('');
                    setStatus('ALL');
                    setSourceType('ALL');
                  }}
                >
                  重置
                </Button>
              </div>
            </div>

            <div className="flex min-h-0 flex-1 overflow-hidden">
              <section className="min-w-0 flex-1 overflow-hidden p-4 pr-3">
                {loadError ? (
                  <div className="flex h-full items-center justify-center">
                    <Empty description={loadError}>
                      <Button onClick={() => void load()}>重新加载</Button>
                    </Empty>
                  </div>
                ) : (
                  <Table<CatalogDataset>
                    rowKey="id"
                    size="small"
                    bordered
                    loading={loading}
                    columns={columns}
                    dataSource={filteredData}
                    scroll={{ x: 1170, y: 'calc(100vh - 226px)' }}
                    pagination={{
                      pageSize: 10,
                      showSizeChanger: false,
                      showTotal: (total) => `共 ${total} 条`,
                    }}
                    locale={{ emptyText: '暂无已发布的 Dataset' }}
                    onRow={(record) => ({
                      onClick: () => setSelectedId(record.id),
                      style: { cursor: 'pointer' },
                    })}
                  />
                )}
              </section>

              <aside className="w-[320px] shrink-0 overflow-y-auto border-l border-[#e8e9ec] bg-[#fcfcfd]">
                {loading && !selectedDataset ? (
                  <div className="flex h-full items-center justify-center"><Spin /></div>
                ) : selectedDataset ? (
                  <>
                    <div className="p-4">
                      <div className="flex items-start gap-3">
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-[#e5e7eb] bg-white text-[#59606c]">
                          <TableProperties size={19} />
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-2">
                            <div className="truncate text-[15px] font-semibold text-[#161823]">{selectedDataset.name}</div>
                            <Tag bordered={false} className="m-0 shrink-0 bg-[#f1f3f5] text-[11px] text-[#5f6570]">
                              {selectedDataset.status === 'ONLINE' ? '已上线' : '已下线'}
                            </Tag>
                          </div>
                          <div className="mt-1 text-[12px] leading-5 text-[#8a8f99]">
                            {selectedDataset.description || '暂无描述'}
                          </div>
                        </div>
                      </div>

                      <div className="mt-4 grid grid-cols-2 gap-2">
                        <Tooltip title={selectedDataset.status === 'ONLINE' ? '' : 'Dataset 上线后才能创建 Analysis'}>
                          <Button
                            type="primary"
                            block
                            icon={<BarChart3 size={14} />}
                            disabled={selectedDataset.status !== 'ONLINE' || !selectedDataset.currentVersion}
                            href={selectedDataset.status === 'ONLINE' && selectedDataset.currentVersion
                              ? `/data-analysis/chart-analysis?datasetId=${encodeURIComponent(selectedDataset.id)}`
                              : undefined}
                          >
                            创建分析
                          </Button>
                        </Tooltip>
                        <Button icon={<GitBranch size={14} />} href="/data-development/releases">
                          发布中心
                        </Button>
                      </div>
                    </div>

                    <div className="border-t border-[#eceef1] p-4">
                      <div className="mb-3 text-[13px] font-medium text-[#30343b]">数据概况</div>
                      {(() => {
                        const summary = schemaSummary(selectedDataset);
                        return (
                          <div className="grid grid-cols-2 gap-x-4 gap-y-3">
                            <div>
                              <div className="text-[11px] text-[#9a9fa8]">当前版本</div>
                              <div className="mt-1 text-[13px] font-medium text-[#30343b]">
                                {selectedDataset.currentVersion ? `DV${selectedDataset.currentVersion.versionNo}` : '-'}
                              </div>
                            </div>
                            <div>
                              <div className="text-[11px] text-[#9a9fa8]">历史版本</div>
                              <div className="mt-1 text-[13px] font-medium text-[#30343b]">{selectedDataset.versions.length}</div>
                            </div>
                            <div>
                              <div className="text-[11px] text-[#9a9fa8]">字段</div>
                              <div className="mt-1 flex items-center gap-1 text-[13px] font-medium text-[#30343b]">
                                <Rows3 size={13} /> {summary.fields}
                              </div>
                            </div>
                            <div>
                              <div className="text-[11px] text-[#9a9fa8]">维度 / 指标</div>
                              <div className="mt-1 text-[13px] font-medium text-[#30343b]">
                                {summary.dimensions} / {summary.metrics}
                              </div>
                            </div>
                          </div>
                        );
                      })()}
                    </div>

                    <div className="border-t border-[#eceef1] p-4">
                      <div className="mb-3 text-[13px] font-medium text-[#30343b]">来源</div>
                      {selectedDataset.currentVersion ? (
                        <div className="space-y-2 text-[12px]">
                          <div className="flex items-center justify-between gap-3">
                            <span className="text-[#9a9fa8]">来源类型</span>
                            <span className="text-[#4f5661]">{sourceTypeLabel[selectedDataset.currentVersion.sourceType]}</span>
                          </div>
                          <div className="flex items-center justify-between gap-3">
                            <span className="text-[#9a9fa8]">TaskAsset</span>
                            <span className="text-[#4f5661]">#{selectedDataset.currentVersion.sourceTaskAssetId}</span>
                          </div>
                          <div className="flex items-center justify-between gap-3">
                            <span className="text-[#9a9fa8]">SQL 版本</span>
                            <span className="text-[#4f5661]">V{selectedDataset.currentVersion.sourceTaskRevisionNo}</span>
                          </div>
                          <div className="flex items-center justify-between gap-3">
                            <span className="text-[#9a9fa8]">更新时间</span>
                            <span className="text-[#4f5661]">{formatTime(selectedDataset.updateTime || selectedDataset.createTime)}</span>
                          </div>
                        </div>
                      ) : (
                        <div className="text-[12px] text-[#9a9fa8]">尚未建立当前 DatasetVersion</div>
                      )}
                    </div>

                    <div className="border-t border-[#eceef1] p-4">
                      <div className="mb-3 flex items-center justify-between">
                        <span className="text-[13px] font-medium text-[#30343b]">字段</span>
                        <span className="text-[11px] text-[#9a9fa8]">{selectedDataset.fields.length} 个</span>
                      </div>
                      <div className="max-h-[280px] space-y-1 overflow-y-auto pr-1">
                        {selectedDataset.fields.length ? selectedDataset.fields.map((field) => (
                          <div key={field.fieldId} className="border border-[#eceef1] bg-white px-2.5 py-2">
                            <div className="flex items-center gap-2">
                              <span className="min-w-0 flex-1 truncate text-[12px] font-medium text-[#4f5661]" title={field.physicalName}>
                                {field.displayName}
                              </span>
                              <Tag bordered={false} className="m-0 bg-[#f5f6f7] text-[10px] text-[#737984]">
                                {roleLabel[field.defaultRole]}
                              </Tag>
                            </div>
                            <div className="mt-1 flex items-center justify-between gap-2 text-[10px] text-[#a0a4ac]">
                              <span className="truncate" title={field.physicalName}>{field.physicalName}</span>
                              <span>{field.dataType}</span>
                            </div>
                          </div>
                        )) : (
                          <div className="text-[12px] text-[#9a9fa8]">当前版本没有字段信息</div>
                        )}
                      </div>
                    </div>

                    <div className="border-t border-[#eceef1] p-4">
                      <div className="mb-3 flex items-center gap-1.5 text-[13px] font-medium text-[#30343b]">
                        <History size={14} /> 版本历史
                      </div>
                      <div className="space-y-2">
                        {selectedDataset.versions.map((version) => (
                          <div key={version.id} className="flex items-center justify-between gap-3 text-[12px]">
                            <div>
                              <span className="font-medium text-[#4f5661]">DV{version.versionNo}</span>
                              <span className="ml-2 text-[#9a9fa8]">SQL V{version.sourceTaskRevisionNo}</span>
                            </div>
                            <span className="text-[11px] text-[#a0a4ac]">{formatTime(version.createTime)}</span>
                          </div>
                        ))}
                      </div>
                    </div>

                    <div className="border-t border-[#eceef1] p-4">
                      <div className="mb-3 text-[13px] font-medium text-[#30343b]">消费情况</div>
                      <div className="flex items-center justify-between border border-[#e7e9ec] bg-white p-3">
                        <div>
                          <div className="text-[18px] font-semibold text-[#30343b]">{selectedDataset.analysisCount}</div>
                          <div className="mt-1 text-[11px] text-[#969ba4]">关联 Analysis</div>
                        </div>
                        <BarChart3 size={18} className="text-[#8a8f99]" />
                      </div>
                    </div>
                  </>
                ) : (
                  <div className="flex h-full items-center justify-center px-4">
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无 Dataset" />
                  </div>
                )}
              </aside>
            </div>
          </main>
        </div>
      </div>
    </ConfigProvider>
  );
};

export default DataCatalogPage;
