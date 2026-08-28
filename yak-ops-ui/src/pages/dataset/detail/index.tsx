import YakOpsEmpty from '@/components/YakOpsEmpty';
import { YakButton, YakTab } from '@/components/ui';
import {
  createDatasetVersion,
  getDatasetForManagement,
  isQueryableDatasetSourceType,
  offlineDataset,
  onlineDataset,
  queryDataset,
  type DatasetManagementDetail,
  type DatasetManagementField,
  type DatasetManagementVersion,
  type DatasetQueryResult,
  type DatasetSourceType,
} from '@/services/dataset';
import { BRAND_THEME } from '@/styles/brand';
import { history, useParams } from '@umijs/max';
import {
  Alert,
  ConfigProvider,
  Descriptions,
  InputNumber,
  Popconfirm,
  Select,
  Spin,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeft,
  BarChart3,
  Database,
  GitBranch,
  Play,
  RefreshCw,
  Rows3,
  Sigma,
  Workflow,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import DatasetQueryDiagnostics from './DatasetQueryDiagnostics';

const SOURCE_TYPE_LABELS: Record<DatasetSourceType, string> = {
  QUERY_REVISION: 'SQL 任务版本',
  SQL_QUERY: 'Standalone SQL',
  TABLE: '数据表',
  VIEW: '视图',
};

const FIELD_TYPE_LABELS: Record<string, string> = {
  STRING: '字符串',
  NUMBER: '数值',
  DATE: '日期',
  DATETIME: '日期时间',
  BOOLEAN: '布尔',
  UNKNOWN: '未知',
};

const formatDateTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const sourceSummary = (version?: DatasetManagementVersion) => {
  if (!version) return '尚未绑定版本';
  if (version.sourceType === 'QUERY_REVISION') {
    return version.sourceTaskAssetId
      ? `TaskAsset #${version.sourceTaskAssetId} · Revision V${version.sourceTaskRevisionNo || '-'}`
      : 'SQL TaskAsset';
  }
  if (version.sourceType === 'SQL_QUERY') {
    return version.dataSourceId ? `Standalone SQL · 数据源 ${version.dataSourceId}` : 'Standalone SQL';
  }
  return SOURCE_TYPE_LABELS[version.sourceType];
};

type DetailTab = 'overview' | 'schema' | 'versions' | 'query' | 'diagnostics' | 'source';

function DatasetQueryPlayground({ dataset }: { dataset: DatasetManagementDetail }) {
  const sortedVersions = useMemo(
    () => [...dataset.versions].sort((left, right) => right.versionNo - left.versionNo),
    [dataset.versions],
  );
  const [versionNo, setVersionNo] = useState<number | undefined>(
    dataset.currentVersion?.versionNo,
  );
  const [limit, setLimit] = useState(50);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<DatasetQueryResult>();
  const [error, setError] = useState('');

  useEffect(() => {
    setVersionNo(dataset.currentVersion?.versionNo);
    setResult(undefined);
    setError('');
  }, [dataset.currentVersion?.versionNo, dataset.id]);

  const selectedVersion = sortedVersions.find((version) => version.versionNo === versionNo);
  const queryable = Boolean(
    selectedVersion && isQueryableDatasetSourceType(selectedVersion.sourceType),
  );
  const disabledReason = dataset.status !== 'ONLINE'
    ? 'Dataset 当前已下线，Query Runtime 不接受查询。'
    : !selectedVersion
      ? '请选择一个 Dataset 版本。'
      : !queryable
        ? `${SOURCE_TYPE_LABELS[selectedVersion.sourceType]}尚未接入 Dataset Query Runtime。`
        : '';

  const runQuery = async () => {
    if (!versionNo || disabledReason) return;
    setRunning(true);
    setError('');
    try {
      setResult(await queryDataset(dataset.id, {
        versionNo,
        dimensions: [],
        metrics: [],
        filters: [],
        sorts: [],
        limit,
        timeoutSeconds: 30,
      }));
    } catch (queryError) {
      setResult(undefined);
      setError(queryError instanceof Error ? queryError.message : 'Dataset 查询失败');
    } finally {
      setRunning(false);
    }
  };

  const previewColumns = useMemo<ColumnsType<Record<string, unknown>>>(() => (
    result?.columns || []
  ).map((column, index) => ({
    title: result?.bindings[index]?.displayName || column.label || column.name,
    dataIndex: `c${index}`,
    key: `c${index}`,
    width: 180,
    ellipsis: true,
    render: (value: unknown) => value == null ? '-' : String(value),
  })), [result]);

  const previewRows = useMemo(() => (result?.rows || []).map((row, rowIndex) => {
    const record: Record<string, unknown> = { key: rowIndex };
    row.forEach((value, columnIndex) => {
      record[`c${columnIndex}`] = value;
    });
    return record;
  }), [result]);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3 border border-[#e4e7ec] bg-[#fafbfc] p-4">
        <div>
          <div className="mb-1.5 text-[12px] text-[#667085]">Dataset 版本</div>
          <Select
            value={versionNo}
            className="w-[220px]"
            placeholder="请选择版本"
            options={sortedVersions.map((version) => ({
              value: version.versionNo,
              label: `DV${version.versionNo} · ${SOURCE_TYPE_LABELS[version.sourceType]}`,
            }))}
            onChange={setVersionNo}
          />
        </div>
        <div>
          <div className="mb-1.5 text-[12px] text-[#667085]">最大返回行数</div>
          <InputNumber
            min={1}
            max={1000}
            value={limit}
            className="w-[150px]"
            onChange={(value) => setLimit(value || 50)}
          />
        </div>
        <Tooltip title={disabledReason}>
          <span>
            <YakButton
              type="primary"
              icon={<Play size={13} />}
              loading={running}
              disabled={Boolean(disabledReason)}
              onClick={() => void runQuery()}
            >
              执行查询
            </YakButton>
          </span>
        </Tooltip>
        <div className="ml-auto text-[12px] text-[#8a8f99]">
          原始数据预览，不修改 Dataset 版本语义
        </div>
      </div>

      {disabledReason && (
        <Alert type="info" showIcon message={disabledReason} />
      )}
      {error && <Alert type="error" showIcon message="查询失败" description={error} />}

      {result ? (
        <div className="border border-[#e4e7ec]">
          <div className="flex flex-wrap items-center gap-4 border-b border-[#e4e7ec] bg-[#fafbfc] px-4 py-2 text-[12px] text-[#667085]">
            <span>版本：DV{result.datasetVersionNo}</span>
            <span>返回：{result.returnedRows} 行</span>
            <span>耗时：{result.elapsedMillis} ms</span>
            {result.queryId && <span>Query ID：{result.queryId}</span>}
            {result.truncated && <Tag color="warning">结果已截断</Tag>}
          </div>
          <Table<Record<string, unknown>>
            rowKey="key"
            size="small"
            pagination={false}
            columns={previewColumns}
            dataSource={previewRows}
            scroll={{ x: 'max-content', y: 420 }}
            locale={{ emptyText: '查询成功，但没有返回数据' }}
          />
        </div>
      ) : !error ? (
        <div className="flex min-h-[300px] items-center justify-center border border-[#e4e7ec]">
          <YakOpsEmpty
            width={170}
            height={112}
            title="等待执行查询"
            description="选择不可变 Dataset 版本后，可直接预览该版本数据。"
            showCaption
          />
        </div>
      ) : null}
    </div>
  );
}

export default function DatasetDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const [dataset, setDataset] = useState<DatasetManagementDetail>();
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [activeTab, setActiveTab] = useState<DetailTab>('overview');
  const [statusUpdating, setStatusUpdating] = useState(false);
  const [versionCreating, setVersionCreating] = useState(false);

  const loadDataset = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setLoadError('');
    try {
      setDataset(await getDatasetForManagement(id));
    } catch (error) {
      setDataset(undefined);
      setLoadError(error instanceof Error ? error.message : '加载 Dataset 详情失败');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void loadDataset();
  }, [loadDataset]);

  const toggleStatus = async () => {
    if (!dataset) return;
    setStatusUpdating(true);
    try {
      const next = dataset.status === 'ONLINE'
        ? await offlineDataset(dataset.id)
        : await onlineDataset(dataset.id);
      setDataset(next);
      message.success(dataset.status === 'ONLINE' ? 'Dataset 已下线' : 'Dataset 已上线');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '更新 Dataset 状态失败');
    } finally {
      setStatusUpdating(false);
    }
  };

  const publishNextVersion = async () => {
    if (!dataset) return;
    setVersionCreating(true);
    try {
      setDataset(await createDatasetVersion(dataset.id));
      message.success('Dataset 新版本已发布');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布 Dataset 新版本失败');
    } finally {
      setVersionCreating(false);
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-white">
        <Spin size="large" />
      </div>
    );
  }

  if (!dataset) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-white">
        <YakOpsEmpty
          width={190}
          height={126}
          title="Dataset 不可用"
          description={loadError || 'Dataset 不存在或当前项目不可见。'}
          showCaption
        />
      </div>
    );
  }

  const currentVersion = dataset.currentVersion;
  const queryable = Boolean(
    currentVersion && isQueryableDatasetSourceType(currentVersion.sourceType),
  );
  const canCreateAnalysis = dataset.status === 'ONLINE' && queryable;
  const canCreateVersion = currentVersion?.sourceType === 'QUERY_REVISION';
  const dimensions = dataset.fields.filter((field) => field.defaultRole === 'DIMENSION').length;
  const measures = dataset.fields.filter((field) => field.defaultRole === 'MEASURE').length;

  const fieldColumns: ColumnsType<DatasetManagementField> = [
    {
      title: '字段',
      dataIndex: 'displayName',
      width: 220,
      render: (value: string, record) => (
        <div>
          <div className="font-medium text-[#161823]">{value}</div>
          <div className="mt-0.5 text-[12px] text-[#8a8f99]">{record.physicalName}</div>
        </div>
      ),
    },
    {
      title: '类型',
      dataIndex: 'dataType',
      width: 110,
      render: (value: string) => FIELD_TYPE_LABELS[value] || value,
    },
    {
      title: '角色',
      dataIndex: 'defaultRole',
      width: 110,
      render: (value: DatasetManagementField['defaultRole']) => (
        <span className="inline-flex items-center gap-1.5">
          {value === 'MEASURE' ? <Sigma size={13} /> : <Rows3 size={13} />}
          {value === 'MEASURE' ? '指标' : '维度'}
        </span>
      ),
    },
    {
      title: '可空',
      dataIndex: 'nullable',
      width: 80,
      render: (value: boolean) => value ? '是' : '否',
    },
    {
      title: '描述',
      dataIndex: 'description',
      render: (value?: string) => value || '-',
    },
  ];

  const versionColumns: ColumnsType<DatasetManagementVersion> = [
    {
      title: '版本',
      dataIndex: 'versionNo',
      width: 100,
      render: (value: number, record) => (
        <div className="flex items-center gap-2">
          <span className="font-medium">DV{value}</span>
          {record.id === dataset.currentVersionId && (
            <Tag bordered={false}>当前</Tag>
          )}
        </div>
      ),
    },
    {
      title: '来源类型',
      dataIndex: 'sourceType',
      width: 150,
      render: (value: DatasetSourceType) => SOURCE_TYPE_LABELS[value],
    },
    {
      title: '来源快照',
      render: (_, record) => sourceSummary(record),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
      render: (value?: string) => formatDateTime(value),
    },
  ];

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="min-h-[calc(100vh-64px)] bg-white text-[#161823]">
        <header className="border-b border-[#e4e7ec] px-6 py-4">
          <div className="flex flex-wrap items-center gap-3">
            <button
              type="button"
              aria-label="返回 Dataset 列表"
              className="flex h-9 w-9 items-center justify-center rounded-[6px] border border-[#e4e7ec] bg-white text-[#667085] hover:text-[#161823]"
              onClick={() => history.push('/dataset')}
            >
              <ArrowLeft size={16} />
            </button>
            <div className="flex h-10 w-10 items-center justify-center rounded-[8px] bg-[#f4f5f7] text-[#667085]">
              <Database size={18} />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="m-0 truncate text-[20px] font-semibold">{dataset.name}</h1>
                <Tag bordered={false} color={dataset.status === 'ONLINE' ? 'success' : 'default'}>
                  {dataset.status === 'ONLINE' ? '已上线' : '已下线'}
                </Tag>
                {currentVersion && <Tag bordered={false}>DV{currentVersion.versionNo}</Tag>}
              </div>
              <div className="mt-1 text-[12px] text-[#667085]">
                {dataset.description || '暂无描述'}
              </div>
            </div>
            <YakButton icon={<RefreshCw size={13} />} onClick={() => void loadDataset()}>
              刷新
            </YakButton>
            <YakButton
              icon={<Workflow size={13} />}
              href={`/data-analysis/lineage?datasetId=${encodeURIComponent(dataset.id)}`}
            >
              查看血缘
            </YakButton>
            <Tooltip title={canCreateVersion ? '从来源 TaskAsset 当前版本生成新的不可变 DatasetVersion' : '仅 SQL TaskAsset 来源支持此操作'}>
              <span>
                <Popconfirm
                  title="确认发布新的 DatasetVersion？"
                  description="新版本会冻结来源 TaskAsset 当前 Revision，不会修改历史版本。"
                  okText="发布"
                  cancelText="取消"
                  disabled={!canCreateVersion}
                  onConfirm={() => void publishNextVersion()}
                >
                  <YakButton
                    icon={<GitBranch size={13} />}
                    disabled={!canCreateVersion}
                    loading={versionCreating}
                  >
                    发布新版本
                  </YakButton>
                </Popconfirm>
              </span>
            </Tooltip>
            <Popconfirm
              title={dataset.status === 'ONLINE' ? '确认下线这个 Dataset？' : '确认上线这个 Dataset？'}
              description={dataset.status === 'ONLINE'
                ? '下线后下游 Analysis、仪表盘和大屏将无法继续查询。'
                : '上线后可重新被下游消费。'}
              okText="确认"
              cancelText="取消"
              onConfirm={() => void toggleStatus()}
            >
              <YakButton loading={statusUpdating}>
                {dataset.status === 'ONLINE' ? '下线' : '上线'}
              </YakButton>
            </Popconfirm>
            <Tooltip title={canCreateAnalysis ? '使用当前 Dataset 创建分析' : 'Dataset 需在线且来源类型已接入 Query Runtime'}>
              <span>
                <YakButton
                  type="primary"
                  icon={<BarChart3 size={13} />}
                  disabled={!canCreateAnalysis}
                  href={canCreateAnalysis
                    ? `/data-analysis/chart-analysis?datasetId=${encodeURIComponent(dataset.id)}`
                    : undefined}
                >
                  创建分析
                </YakButton>
              </span>
            </Tooltip>
          </div>
        </header>

        <div className="border-b border-[#e4e7ec] px-5">
          <YakTab
            activeKey={activeTab}
            items={[
              { key: 'overview', label: '基本信息' },
              { key: 'schema', label: `字段 Schema ${dataset.fields.length}` },
              { key: 'versions', label: `版本历史 ${dataset.versions.length}` },
              { key: 'query', label: 'Query Playground' },
              { key: 'diagnostics', label: '运行诊断' },
              { key: 'source', label: '来源信息' },
            ]}
            onChange={(key) => setActiveTab(key as DetailTab)}
          />
        </div>

        <main className="p-6">
          {activeTab === 'overview' && (
            <div className="space-y-4">
              <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
                {[
                  { label: '字段', value: dataset.fields.length },
                  { label: '维度', value: dimensions },
                  { label: '指标', value: measures },
                  { label: '版本', value: dataset.versions.length },
                ].map((item) => (
                  <div key={item.label} className="border border-[#e4e7ec] p-4">
                    <div className="text-[12px] text-[#667085]">{item.label}</div>
                    <div className="mt-2 text-[24px] font-semibold">{item.value}</div>
                  </div>
                ))}
              </div>
              <div className="border border-[#e4e7ec] p-5">
                <Descriptions
                  column={{ xs: 1, sm: 2, lg: 3 }}
                  items={[
                    { key: 'id', label: 'Dataset ID', children: dataset.id },
                    { key: 'status', label: '状态', children: dataset.status === 'ONLINE' ? '已上线' : '已下线' },
                    { key: 'version', label: '当前版本', children: currentVersion ? `DV${currentVersion.versionNo}` : '-' },
                    { key: 'source', label: '当前来源', children: sourceSummary(currentVersion) },
                    { key: 'created', label: '创建时间', children: formatDateTime(dataset.createTime) },
                    { key: 'updated', label: '更新时间', children: formatDateTime(dataset.updateTime || dataset.createTime) },
                  ]}
                />
              </div>
            </div>
          )}

          {activeTab === 'schema' && (
            <Table
              rowKey="fieldId"
              bordered
              pagination={false}
              columns={fieldColumns}
              dataSource={[...dataset.fields].sort((left, right) => left.sortOrder - right.sortOrder)}
              scroll={{ x: 850 }}
              locale={{ emptyText: '当前版本暂无字段定义' }}
            />
          )}

          {activeTab === 'versions' && (
            <Table
              rowKey="id"
              bordered
              pagination={false}
              columns={versionColumns}
              dataSource={[...dataset.versions].sort((left, right) => right.versionNo - left.versionNo)}
              scroll={{ x: 860 }}
              locale={{ emptyText: '暂无 Dataset 版本' }}
            />
          )}

          {activeTab === 'query' && <DatasetQueryPlayground dataset={dataset} />}
          {activeTab === 'diagnostics' && <DatasetQueryDiagnostics datasetId={dataset.id} />}

          {activeTab === 'source' && (
            <div className="space-y-4">
              <div className="border border-[#e4e7ec] p-5">
                <Descriptions
                  column={{ xs: 1, sm: 2 }}
                  items={[
                    { key: 'type', label: '来源类型', children: currentVersion ? SOURCE_TYPE_LABELS[currentVersion.sourceType] : '-' },
                    { key: 'summary', label: '来源快照', children: sourceSummary(currentVersion) },
                    { key: 'asset', label: 'TaskAsset ID', children: currentVersion?.sourceTaskAssetId || '-' },
                    { key: 'revision', label: 'TaskRevision ID', children: currentVersion?.sourceTaskRevisionId || '-' },
                    { key: 'revisionNo', label: 'TaskRevision No.', children: currentVersion?.sourceTaskRevisionNo || '-' },
                    { key: 'datasource', label: '数据源 ID', children: currentVersion?.dataSourceId || '-' },
                  ]}
                />
              </div>
              {currentVersion?.sql && (
                <div className="border border-[#e4e7ec]">
                  <div className="border-b border-[#e4e7ec] bg-[#fafbfc] px-4 py-2 text-[13px] font-medium">
                    冻结 SQL
                  </div>
                  <pre className="m-0 max-h-[440px] overflow-auto whitespace-pre-wrap break-words p-4 text-[12px] leading-6 text-[#344054]">
                    {currentVersion.sql}
                  </pre>
                </div>
              )}
              {currentVersion && !isQueryableDatasetSourceType(currentVersion.sourceType) && (
                <Alert
                  type="warning"
                  showIcon
                  message={`${SOURCE_TYPE_LABELS[currentVersion.sourceType]}当前仅保留 Dataset 合同值，尚未接入 Query Runtime。`}
                />
              )}
            </div>
          )}
        </main>
      </div>
    </ConfigProvider>
  );
}
