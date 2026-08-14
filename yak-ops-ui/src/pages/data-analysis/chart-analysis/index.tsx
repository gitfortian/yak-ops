import { AnalysisPreview } from '@/components/analysis/AnalysisPreview';
import {
  createAnalysis,
  deleteAnalysis,
  fetchAnalyses,
  updateAnalysis,
} from '@/components/analysis/analysis-service';
import { fetchAnalysisDatasets } from '@/components/analysis/dataset-service';
import type {
  Aggregation,
  AnalysisAsset,
  ChartType,
  DatasetField,
  FilterOperator,
  MetricBinding,
  PublishedDataset,
  SortDirection,
} from '@/components/analysis/model';
import { BRAND_CSS_VARIABLES } from '@/styles/brand';
import {
  Button,
  Empty,
  Input,
  message,
  Popconfirm,
  Select,
  Spin,
  Switch,
  Tag,
  Tooltip,
} from 'antd';
import {
  ArrowLeft,
  BarChart3,
  ChartLine,
  ChartPie,
  FileBarChart,
  GripVertical,
  Layers3,
  Plus,
  RefreshCw,
  Save,
  Search,
  Sigma,
  Table2,
  Trash2,
  X,
} from 'lucide-react';
import {
  type DragEvent,
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';

const CHART_OPTIONS: Array<{ label: string; value: ChartType }> = [
  { label: '指标卡', value: 'metric' },
  { label: '柱状图', value: 'bar' },
  { label: '折线图', value: 'line' },
  { label: '饼图', value: 'pie' },
  { label: '表格', value: 'table' },
];

const AGGREGATION_OPTIONS: Array<{ label: string; value: Aggregation }> = [
  { label: '求和', value: 'SUM' },
  { label: '平均', value: 'AVG' },
  { label: '计数', value: 'COUNT' },
  { label: '去重计数', value: 'COUNT_DISTINCT' },
  { label: '最大值', value: 'MAX' },
  { label: '最小值', value: 'MIN' },
];

const FILTER_OPTIONS: Array<{ label: string; value: FilterOperator }> = [
  { label: '等于', value: 'eq' },
  { label: '不等于', value: 'neq' },
  { label: '包含', value: 'contains' },
  { label: '大于', value: 'gt' },
  { label: '大于等于', value: 'gte' },
  { label: '小于', value: 'lt' },
  { label: '小于等于', value: 'lte' },
];

const FIELD_DRAG_TYPE = 'application/x-yak-analysis-field';

const chartIcon = (type: ChartType) => {
  if (type === 'metric') return <Sigma size={14} />;
  if (type === 'line') return <ChartLine size={14} />;
  if (type === 'pie') return <ChartPie size={14} />;
  if (type === 'table') return <Table2 size={14} />;
  return <BarChart3 size={14} />;
};

const defaultStyle = (type: ChartType) => ({
  showLegend: type === 'pie',
  showDataLabels: false,
  smooth: type === 'line',
  showGrid: type === 'bar' || type === 'line',
});

const defaultDraft = (dataset?: PublishedDataset): AnalysisAsset => {
  const dimension = dataset?.fields.find((field) => field.role === 'dimension');
  const metric = dataset?.fields.find((field) => field.role === 'metric');
  const type: ChartType = dimension && metric ? 'bar' : metric ? 'metric' : 'table';
  return {
    id: '',
    name: dataset ? `${dataset.name}分析` : '未命名分析',
    description: '',
    datasetId: dataset?.id ?? '',
    type,
    dimensions: type === 'metric' || !dimension ? [] : [dimension.key],
    metrics: metric ? [{ field: metric.key, aggregation: 'SUM' }] : [],
    filters: [],
    style: defaultStyle(type),
    limit: type === 'table' ? 200 : 500,
    timeoutSeconds: 30,
  };
};

const cloneAsset = (asset: AnalysisAsset): AnalysisAsset =>
  JSON.parse(JSON.stringify(asset)) as AnalysisAsset;

const navigationContext = () => {
  if (typeof window === 'undefined') return { datasetId: '', analysisId: '' };
  const params = new URLSearchParams(window.location.search);
  return {
    datasetId: params.get('datasetId')?.trim() || '',
    analysisId: params.get('analysisId')?.trim() || '',
  };
};

const replaceLocation = (search: string) => {
  if (typeof window === 'undefined') return;
  window.history.replaceState(
    window.history.state,
    '',
    `/data-analysis/chart-analysis${search ? `?${search}` : ''}`,
  );
};

const dimensionLimit = (type: ChartType) => {
  if (type === 'metric') return 0;
  if (type === 'table') return 3;
  return 1;
};

const metricLimit = (type: ChartType) => (
  type === 'metric' || type === 'pie' ? 1 : 3
);

const fieldTypeLabel = (field: DatasetField) => {
  if (field.dataType === 'datetime') return '时间';
  if (field.dataType === 'date') return '日期';
  if (field.dataType === 'number') return '数值';
  if (field.dataType === 'boolean') return '布尔';
  if (field.dataType === 'string') return '文本';
  return '未知';
};

export default function ChartAnalysisPage() {
  const [datasets, setDatasets] = useState<PublishedDataset[]>([]);
  const [analyses, setAnalyses] = useState<AnalysisAsset[]>([]);
  const [draft, setDraft] = useState<AnalysisAsset>(() => defaultDraft());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [fieldKeyword, setFieldKeyword] = useState('');
  const [catalogContextDatasetId, setCatalogContextDatasetId] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const [datasetValues, analysisValues] = await Promise.all([
        fetchAnalysisDatasets(),
        fetchAnalyses(),
      ]);
      const navigation = navigationContext();
      const requestedDataset = navigation.datasetId
        ? datasetValues.find((item) => item.id === navigation.datasetId)
        : undefined;
      const requestedAnalysis = navigation.analysisId
        ? analysisValues.find((item) => item.id === navigation.analysisId)
        : undefined;

      setDatasets(datasetValues);
      setAnalyses(analysisValues);

      if (navigation.datasetId) {
        setCatalogContextDatasetId(navigation.datasetId);
        if (!requestedDataset) {
          setLoadError(`Dataset #${navigation.datasetId} 当前不可用于分析，请确认它已上线且存在当前版本。`);
        }
      }
      if (navigation.analysisId && !requestedAnalysis) {
        setLoadError(`Analysis #${navigation.analysisId} 不存在或已被删除。`);
      }

      setDraft((current) => {
        if (requestedDataset) return defaultDraft(requestedDataset);
        if (requestedAnalysis) return cloneAsset(requestedAnalysis);
        if (current.id) {
          const refreshed = analysisValues.find((item) => item.id === current.id);
          if (refreshed) return cloneAsset(refreshed);
        }
        return analysisValues[0]
          ? cloneAsset(analysisValues[0])
          : defaultDraft(datasetValues[0]);
      });
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : '加载图表分析失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const dataset = datasets.find((item) => item.id === draft.datasetId);
  const dimensionOptions = dataset?.fields
    .filter((field) => field.role === 'dimension')
    .map((field) => ({ label: field.label, value: field.key })) ?? [];
  const metricOptions = dataset?.fields
    .filter((field) => field.role === 'metric')
    .map((field) => ({ label: field.label, value: field.key })) ?? [];
  const fieldOptions = dataset?.fields.map((field) => ({ label: field.label, value: field.key })) ?? [];
  const selectedFields = new Set([
    ...draft.dimensions,
    ...draft.metrics.map((metric) => metric.field),
  ]);
  const sortOptions = fieldOptions.filter((option) => selectedFields.has(option.value));
  const filter = draft.filters[0];
  const datasetMap = useMemo(() => new Map(datasets.map((item) => [item.id, item])), [datasets]);
  const normalizedFieldKeyword = fieldKeyword.trim().toLowerCase();
  const visibleFields = useMemo(() => (
    dataset?.fields.filter((field) => {
      if (!normalizedFieldKeyword) return true;
      return [field.label, field.physicalName, field.description || '', fieldTypeLabel(field)]
        .some((value) => value.toLowerCase().includes(normalizedFieldKeyword));
    }) ?? []
  ), [dataset, normalizedFieldKeyword]);
  const visibleDimensions = visibleFields.filter((field) => field.role === 'dimension');
  const visibleMetrics = visibleFields.filter((field) => field.role === 'metric');

  const selectAnalysis = (analysis: AnalysisAsset) => {
    setCatalogContextDatasetId('');
    setDraft(cloneAsset(analysis));
    replaceLocation(`analysisId=${encodeURIComponent(analysis.id)}`);
  };

  const createNew = () => {
    const source = dataset ?? datasets[0];
    setCatalogContextDatasetId('');
    setDraft(defaultDraft(source));
    replaceLocation(source ? `datasetId=${encodeURIComponent(source.id)}` : '');
  };

  const changeDataset = (datasetId: string) => {
    const nextDataset = datasets.find((item) => item.id === datasetId);
    if (!nextDataset) return;
    const next = defaultDraft(nextDataset);
    setDraft((current) => ({
      ...current,
      datasetId,
      type: next.type,
      dimensions: next.dimensions,
      metrics: next.metrics,
      filters: [],
      sort: undefined,
      style: next.style,
    }));
    if (!draft.id) replaceLocation(`datasetId=${encodeURIComponent(datasetId)}`);
  };

  const changeType = (type: ChartType) => {
    const firstDimension = draft.dimensions[0] ?? dimensionOptions[0]?.value;
    const firstMetric = draft.metrics[0] ?? (metricOptions[0]
      ? { field: metricOptions[0].value, aggregation: 'SUM' as Aggregation }
      : undefined);
    const maxDimensions = dimensionLimit(type);
    const maxMetrics = metricLimit(type);
    setDraft((current) => ({
      ...current,
      type,
      dimensions: maxDimensions === 0
        ? []
        : (current.dimensions.length ? current.dimensions : firstDimension ? [firstDimension] : [])
          .slice(0, maxDimensions),
      metrics: (current.metrics.length ? current.metrics : firstMetric ? [firstMetric] : [])
        .slice(0, maxMetrics),
      sort: undefined,
      style: {
        ...defaultStyle(type),
        ...current.style,
        smooth: type === 'line' ? current.style.smooth : false,
      },
      limit: type === 'table' ? 200 : 500,
    }));
  };

  const updateMetricFields = (fields: string[]) => {
    const previous = new Map(draft.metrics.map((metric) => [metric.field, metric]));
    const metrics: MetricBinding[] = fields
      .slice(0, metricLimit(draft.type))
      .map((field) => previous.get(field) ?? { field, aggregation: 'SUM' });
    const sort = draft.sort && !draft.dimensions.includes(draft.sort.field)
      && !metrics.some((metric) => metric.field === draft.sort?.field)
      ? undefined
      : draft.sort;
    setDraft((current) => ({ ...current, metrics, sort }));
  };

  const updateDimensions = (dimensions: string[]) => {
    const next = dimensions.slice(0, dimensionLimit(draft.type));
    const sort = draft.sort && !next.includes(draft.sort.field)
      && !draft.metrics.some((metric) => metric.field === draft.sort?.field)
      ? undefined
      : draft.sort;
    setDraft((current) => ({ ...current, dimensions: next, sort }));
  };

  const addDimension = (fieldId: string) => {
    const field = dataset?.fields.find((item) => item.key === fieldId);
    if (!field || field.role !== 'dimension') return;
    const limit = dimensionLimit(draft.type);
    if (!limit) return void message.info('指标卡不使用维度');
    if (draft.dimensions.includes(fieldId)) return;
    if (draft.dimensions.length >= limit) {
      return void message.info(`当前图表最多使用 ${limit} 个维度`);
    }
    updateDimensions([...draft.dimensions, fieldId]);
  };

  const addMetric = (fieldId: string) => {
    const field = dataset?.fields.find((item) => item.key === fieldId);
    if (!field || field.role !== 'metric') return;
    if (draft.metrics.some((item) => item.field === fieldId)) return;
    const limit = metricLimit(draft.type);
    if (draft.metrics.length >= limit) {
      return void message.info(`当前图表最多使用 ${limit} 个指标`);
    }
    updateMetricFields([...draft.metrics.map((item) => item.field), fieldId]);
  };

  const addField = (field: DatasetField) => {
    if (field.role === 'metric') addMetric(field.key);
    else addDimension(field.key);
  };

  const beginFieldDrag = (event: DragEvent<HTMLButtonElement>, field: DatasetField) => {
    event.dataTransfer.effectAllowed = 'copy';
    event.dataTransfer.setData(FIELD_DRAG_TYPE, field.key);
    event.dataTransfer.setData('text/plain', field.key);
  };

  const droppedFieldId = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    return event.dataTransfer.getData(FIELD_DRAG_TYPE)
      || event.dataTransfer.getData('text/plain');
  };

  const save = async () => {
    if (!draft.name.trim()) return void message.warning('请填写 Analysis 名称');
    if (!draft.datasetId) return void message.warning('请选择 Dataset');
    setSaving(true);
    try {
      const saved = draft.id
        ? await updateAnalysis(draft.id, draft.name, draft.description, draft)
        : await createAnalysis(draft.name, draft.description, draft);
      setDraft(cloneAsset(saved));
      setAnalyses((current) => {
        const exists = current.some((item) => item.id === saved.id);
        const next = exists
          ? current.map((item) => item.id === saved.id ? saved : item)
          : [saved, ...current];
        return [...next].sort((left, right) => (right.updatedAt || '').localeCompare(left.updatedAt || ''));
      });
      replaceLocation(`analysisId=${encodeURIComponent(saved.id)}`);
      message.success(draft.id ? 'Analysis 已更新' : 'Analysis 已创建');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存 Analysis 失败');
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!draft.id) return;
    try {
      await deleteAnalysis(draft.id);
      const next = analyses.filter((item) => item.id !== draft.id);
      setAnalyses(next);
      const nextDraft = next[0] ? cloneAsset(next[0]) : defaultDraft(datasets[0]);
      setDraft(nextDraft);
      setCatalogContextDatasetId('');
      replaceLocation(nextDraft.id
        ? `analysisId=${encodeURIComponent(nextDraft.id)}`
        : nextDraft.datasetId
          ? `datasetId=${encodeURIComponent(nextDraft.datasetId)}`
          : '');
      message.success('Analysis 已删除');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除 Analysis 失败');
    }
  };

  const catalogDataset = datasets.find((item) => item.id === catalogContextDatasetId);

  return (
    <div
      className="flex h-[calc(100vh-48px)] min-h-[660px] flex-col overflow-hidden bg-[#f4f6f8]"
      style={BRAND_CSS_VARIABLES}
    >
      <header className="flex h-12 shrink-0 items-center gap-3 border-b border-[#e5e7eb] bg-white px-4">
        {catalogContextDatasetId ? (
          <Button
            size="small"
            type="text"
            icon={<ArrowLeft size={13} />}
            href="/data-analysis/data-catalog"
          >
            数据目录
          </Button>
        ) : null}
        <div className="flex min-w-0 flex-1 items-center gap-2">
          <FileBarChart size={16} className="text-[#475467]" />
          <div className="min-w-0">
            <div className="flex items-center gap-2 text-[13px] font-semibold text-[#161823]">
              图表分析
              {catalogDataset ? (
                <Tag bordered={false} className="m-0 bg-[#f3f4f6] text-[10px] font-normal text-[#667085]">
                  来自 {catalogDataset.name}
                </Tag>
              ) : null}
            </div>
            <div className="text-[10px] text-[#98a2b3]">
              Dataset → Analysis · 保存后可被 Dashboard 通过 analysisId 复用
            </div>
          </div>
        </div>
        <Button
          size="small"
          icon={<RefreshCw size={12} />}
          onClick={() => void load()}
          loading={loading}
        >
          刷新
        </Button>
        <Button size="small" icon={<Plus size={12} />} onClick={createNew}>新建</Button>
        {draft.id ? (
          <Popconfirm title="确认删除这个 Analysis？" onConfirm={() => void remove()}>
            <Button size="small" danger icon={<Trash2 size={12} />}>删除</Button>
          </Popconfirm>
        ) : null}
        <Button
          type="primary"
          size="small"
          icon={<Save size={12} />}
          loading={saving}
          onClick={() => void save()}
        >
          保存
        </Button>
      </header>

      <div className="flex min-h-0 flex-1 overflow-hidden">
        <aside className="flex w-[224px] shrink-0 flex-col border-r border-[#e5e7eb] bg-white">
          <div className="border-b border-[#edf0f3] px-3 py-2 text-[11px] font-medium text-[#667085]">
            Analysis 资产 <span className="ml-1 text-[#98a2b3]">{analyses.length}</span>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto p-2">
            {loading && !analyses.length ? (
              <div className="flex justify-center py-8"><Spin size="small" /></div>
            ) : null}
            {!loading && !analyses.length ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无 Analysis" className="mt-8" />
            ) : null}
            {analyses.map((analysis) => {
              const active = draft.id === analysis.id;
              const source = datasetMap.get(analysis.datasetId);
              return (
                <button
                  key={analysis.id}
                  type="button"
                  onClick={() => selectAnalysis(analysis)}
                  className={[
                    'mb-1 flex w-full items-start gap-2 rounded-[5px] border-0 px-2.5 py-2 text-left',
                    active ? 'bg-[#f1f3f5]' : 'bg-transparent hover:bg-[#f7f8fa]',
                  ].join(' ')}
                >
                  <span className="mt-0.5 text-[#667085]">{chartIcon(analysis.type)}</span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[11px] font-medium text-[#344054]">{analysis.name}</span>
                    <span className="mt-0.5 block truncate text-[9px] text-[#98a2b3]">
                      {source?.name ?? `Dataset #${analysis.datasetId}`}
                    </span>
                  </span>
                </button>
              );
            })}
          </div>
        </aside>

        <aside className="flex w-[220px] shrink-0 flex-col border-r border-[#e5e7eb] bg-[#fbfcfd]">
          <div className="border-b border-[#edf0f3] p-2.5">
            <div className="mb-1.5 flex items-center justify-between text-[10px] font-medium text-[#667085]">
              <span>Dataset 字段</span>
              {dataset ? <span className="text-[#98a2b3]">DV{dataset.currentVersionNo ?? '-'}</span> : null}
            </div>
            <Select
              size="small"
              className="w-full"
              value={draft.datasetId || undefined}
              placeholder="选择 ONLINE Dataset"
              options={datasets.map((item) => ({ label: item.name, value: item.id }))}
              onChange={changeDataset}
            />
            <Input
              allowClear
              size="small"
              className="mt-2"
              value={fieldKeyword}
              prefix={<Search size={12} className="text-[#98a2b3]" />}
              placeholder="搜索字段"
              onChange={(event) => setFieldKeyword(event.target.value)}
            />
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto p-2">
            {!dataset ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择 Dataset" className="mt-8" />
            ) : (
              <>
                <div className="mb-1 flex items-center justify-between px-1 text-[10px] font-medium text-[#667085]">
                  <span>维度</span>
                  <span className="text-[#98a2b3]">{visibleDimensions.length}</span>
                </div>
                {visibleDimensions.map((field) => (
                  <Tooltip key={field.key} title={field.description || field.physicalName} placement="right">
                    <button
                      type="button"
                      draggable
                      onDragStart={(event) => beginFieldDrag(event, field)}
                      onDoubleClick={() => addDimension(field.key)}
                      className="group mb-0.5 flex h-8 w-full items-center gap-1.5 rounded-[4px] border-0 bg-transparent px-1.5 text-left hover:bg-white"
                    >
                      <GripVertical size={12} className="shrink-0 text-[#c0c4cc]" />
                      <Layers3 size={12} className="shrink-0 text-[#667085]" />
                      <span className="min-w-0 flex-1 truncate text-[11px] text-[#475467]">{field.label}</span>
                      <span className="text-[9px] text-[#a0a4ac]">{fieldTypeLabel(field)}</span>
                      <span
                        role="button"
                        tabIndex={0}
                        className="hidden h-5 w-5 items-center justify-center text-[#667085] group-hover:flex"
                        onClick={(event) => {
                          event.stopPropagation();
                          addDimension(field.key);
                        }}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') addDimension(field.key);
                        }}
                      >
                        <Plus size={12} />
                      </span>
                    </button>
                  </Tooltip>
                ))}

                <div className="mb-1 mt-4 flex items-center justify-between px-1 text-[10px] font-medium text-[#667085]">
                  <span>指标</span>
                  <span className="text-[#98a2b3]">{visibleMetrics.length}</span>
                </div>
                {visibleMetrics.map((field) => (
                  <Tooltip key={field.key} title={field.description || field.physicalName} placement="right">
                    <button
                      type="button"
                      draggable
                      onDragStart={(event) => beginFieldDrag(event, field)}
                      onDoubleClick={() => addMetric(field.key)}
                      className="group mb-0.5 flex h-8 w-full items-center gap-1.5 rounded-[4px] border-0 bg-transparent px-1.5 text-left hover:bg-white"
                    >
                      <GripVertical size={12} className="shrink-0 text-[#c0c4cc]" />
                      <Sigma size={12} className="shrink-0 text-[#667085]" />
                      <span className="min-w-0 flex-1 truncate text-[11px] text-[#475467]">{field.label}</span>
                      <span className="text-[9px] text-[#a0a4ac]">{fieldTypeLabel(field)}</span>
                      <span
                        role="button"
                        tabIndex={0}
                        className="hidden h-5 w-5 items-center justify-center text-[#667085] group-hover:flex"
                        onClick={(event) => {
                          event.stopPropagation();
                          addMetric(field.key);
                        }}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') addMetric(field.key);
                        }}
                      >
                        <Plus size={12} />
                      </span>
                    </button>
                  </Tooltip>
                ))}

                {!visibleFields.length ? (
                  <div className="py-8 text-center text-[10px] text-[#98a2b3]">没有匹配字段</div>
                ) : null}
                <div className="mt-4 border-t border-[#edf0f3] px-1 pt-3 text-[9px] leading-4 text-[#98a2b3]">
                  拖拽字段到右侧维度 / 指标区域；双击字段也可以快速添加。
                </div>
              </>
            )}
          </div>
        </aside>

        <main className="min-w-0 flex-1 overflow-auto p-4">
          {loadError ? (
            <div className="mb-3 border border-[#fecdca] bg-[#fffbfa] px-3 py-2 text-[11px] text-[#b42318]">
              {loadError}
            </div>
          ) : null}
          <div className="mx-auto flex min-h-[520px] max-w-[1200px] flex-col border border-[#dfe3e8] bg-white">
            <div className="flex h-10 shrink-0 items-center border-b border-[#edf0f3] px-4">
              <span className="min-w-0 flex-1 truncate text-[12px] font-medium text-[#344054]">
                {draft.name || '未命名分析'}
              </span>
              <span className="text-[10px] text-[#98a2b3]">
                {dataset ? `${dataset.name} · DV${dataset.currentVersionNo ?? '-'}` : '未选择 Dataset'}
              </span>
            </div>
            <div className="min-h-[460px] flex-1 p-4">
              <AnalysisPreview spec={draft} dataset={dataset} className="h-full min-h-[420px]" />
            </div>
          </div>
        </main>

        <aside className="w-[320px] shrink-0 overflow-y-auto border-l border-[#e5e7eb] bg-white p-3">
          <div className="mb-2 text-[11px] font-medium text-[#667085]">基本信息</div>
          <Input
            size="small"
            value={draft.name}
            placeholder="Analysis 名称"
            onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))}
          />
          <Input.TextArea
            className="mt-2"
            rows={2}
            value={draft.description}
            placeholder="描述（可选）"
            onChange={(event) => setDraft((current) => ({ ...current, description: event.target.value }))}
          />

          <div className="mb-2 mt-5 border-t border-[#edf0f3] pt-4 text-[11px] font-medium text-[#667085]">
            图表类型
          </div>
          <Select
            size="small"
            className="w-full"
            value={draft.type}
            options={CHART_OPTIONS}
            onChange={changeType}
          />

          {draft.type !== 'metric' ? (
            <>
              <div className="mb-1 mt-4 flex items-center justify-between text-[10px] text-[#667085]">
                <span>维度</span>
                <span className="text-[#98a2b3]">{draft.dimensions.length}/{dimensionLimit(draft.type)}</span>
              </div>
              <div
                onDragOver={(event) => {
                  event.preventDefault();
                  event.dataTransfer.dropEffect = 'copy';
                }}
                onDrop={(event) => addDimension(droppedFieldId(event))}
                className="min-h-10 border border-dashed border-[#d9dde3] bg-[#fafbfc] p-1.5"
              >
                {draft.dimensions.length ? (
                  <div className="space-y-1">
                    {draft.dimensions.map((fieldId) => {
                      const field = dataset?.fields.find((item) => item.key === fieldId);
                      return (
                        <div key={fieldId} className="flex h-7 items-center gap-1.5 bg-white px-2 text-[10px] text-[#475467] shadow-sm">
                          <Layers3 size={11} className="text-[#667085]" />
                          <span className="min-w-0 flex-1 truncate">{field?.label ?? fieldId}</span>
                          <button
                            type="button"
                            className="border-0 bg-transparent p-0 text-[#98a2b3] hover:text-[#475467]"
                            onClick={() => updateDimensions(draft.dimensions.filter((item) => item !== fieldId))}
                          >
                            <X size={11} />
                          </button>
                        </div>
                      );
                    })}
                  </div>
                ) : (
                  <div className="flex h-7 items-center justify-center text-[10px] text-[#a0a4ac]">拖入维度字段</div>
                )}
              </div>
            </>
          ) : null}

          <div className="mb-1 mt-4 flex items-center justify-between text-[10px] text-[#667085]">
            <span>指标</span>
            <span className="text-[#98a2b3]">{draft.metrics.length}/{metricLimit(draft.type)}</span>
          </div>
          <div
            onDragOver={(event) => {
              event.preventDefault();
              event.dataTransfer.dropEffect = 'copy';
            }}
            onDrop={(event) => addMetric(droppedFieldId(event))}
            className="min-h-10 border border-dashed border-[#d9dde3] bg-[#fafbfc] p-1.5"
          >
            {draft.metrics.length ? (
              <div className="space-y-1">
                {draft.metrics.map((metric) => {
                  const field = dataset?.fields.find((item) => item.key === metric.field);
                  return (
                    <div key={metric.field} className="flex h-8 items-center gap-1.5 bg-white px-2 text-[10px] text-[#475467] shadow-sm">
                      <Sigma size={11} className="text-[#667085]" />
                      <span className="min-w-0 flex-1 truncate">{field?.label ?? metric.field}</span>
                      <Select
                        size="small"
                        variant="borderless"
                        className="w-[82px]"
                        value={metric.aggregation}
                        options={AGGREGATION_OPTIONS}
                        onChange={(aggregation: Aggregation) => setDraft((current) => ({
                          ...current,
                          metrics: current.metrics.map((item) => (
                            item.field === metric.field ? { ...item, aggregation } : item
                          )),
                        }))}
                      />
                      <button
                        type="button"
                        className="border-0 bg-transparent p-0 text-[#98a2b3] hover:text-[#475467]"
                        onClick={() => updateMetricFields(
                          draft.metrics.filter((item) => item.field !== metric.field).map((item) => item.field),
                        )}
                      >
                        <X size={11} />
                      </button>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="flex h-7 items-center justify-center text-[10px] text-[#a0a4ac]">拖入指标字段</div>
            )}
          </div>

          <div className="mb-1 mt-4 text-[10px] text-[#667085]">排序</div>
          <div className="flex gap-2">
            <Select
              allowClear
              size="small"
              className="min-w-0 flex-1"
              value={draft.sort?.field}
              options={sortOptions}
              placeholder="已选维度 / 指标"
              onChange={(field?: string) => setDraft((current) => ({
                ...current,
                sort: field ? { field, direction: current.sort?.direction ?? 'asc' } : undefined,
              }))}
            />
            <Select
              size="small"
              className="w-[78px]"
              disabled={!draft.sort}
              value={draft.sort?.direction ?? 'asc'}
              options={[
                { label: '升序', value: 'asc' },
                { label: '降序', value: 'desc' },
              ]}
              onChange={(direction: SortDirection) => setDraft((current) => ({
                ...current,
                sort: current.sort ? { ...current.sort, direction } : undefined,
              }))}
            />
          </div>

          <div className="mb-1 mt-4 text-[10px] text-[#667085]">过滤</div>
          <div className="grid grid-cols-[1fr_92px] gap-2">
            <Select
              allowClear
              size="small"
              value={filter?.field}
              options={fieldOptions}
              placeholder="字段"
              onChange={(field?: string) => setDraft((current) => ({
                ...current,
                filters: field ? [{
                  id: filter?.id ?? 'analysis-filter-main',
                  field,
                  operator: filter?.operator ?? 'eq',
                  value: filter?.value ?? '',
                }] : [],
              }))}
            />
            <Select
              size="small"
              disabled={!filter}
              value={filter?.operator ?? 'eq'}
              options={FILTER_OPTIONS}
              onChange={(operator: FilterOperator) => setDraft((current) => ({
                ...current,
                filters: current.filters[0] ? [{ ...current.filters[0], operator }] : [],
              }))}
            />
          </div>
          <Input
            size="small"
            className="mt-2"
            disabled={!filter}
            value={filter?.value ?? ''}
            placeholder="过滤值"
            onChange={(event) => setDraft((current) => ({
              ...current,
              filters: current.filters[0]
                ? [{ ...current.filters[0], value: event.target.value }]
                : [],
            }))}
          />

          <div className="mb-2 mt-5 border-t border-[#edf0f3] pt-4 text-[11px] font-medium text-[#667085]">
            样式
          </div>
          <div className="space-y-3 text-[11px] text-[#475467]">
            <label className="flex items-center justify-between">
              <span>显示图例</span>
              <Switch
                size="small"
                checked={draft.style.showLegend}
                onChange={(showLegend) => setDraft((current) => ({
                  ...current,
                  style: { ...current.style, showLegend },
                }))}
              />
            </label>
            <label className="flex items-center justify-between">
              <span>数据标签</span>
              <Switch
                size="small"
                checked={draft.style.showDataLabels}
                onChange={(showDataLabels) => setDraft((current) => ({
                  ...current,
                  style: { ...current.style, showDataLabels },
                }))}
              />
            </label>
            <label className="flex items-center justify-between">
              <span>平滑折线</span>
              <Switch
                size="small"
                disabled={draft.type !== 'line'}
                checked={draft.style.smooth}
                onChange={(smooth) => setDraft((current) => ({
                  ...current,
                  style: { ...current.style, smooth },
                }))}
              />
            </label>
            <label className="flex items-center justify-between">
              <span>网格线</span>
              <Switch
                size="small"
                checked={draft.style.showGrid}
                onChange={(showGrid) => setDraft((current) => ({
                  ...current,
                  style: { ...current.style, showGrid },
                }))}
              />
            </label>
          </div>
        </aside>
      </div>
    </div>
  );
}
