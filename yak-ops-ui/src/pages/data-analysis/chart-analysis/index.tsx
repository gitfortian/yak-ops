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
} from 'antd';
import {
  BarChart3,
  ChartLine,
  ChartPie,
  FileBarChart,
  Plus,
  RefreshCw,
  Save,
  Sigma,
  Table2,
  Trash2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

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
    name: '未命名分析',
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

export default function ChartAnalysisPage() {
  const [datasets, setDatasets] = useState<PublishedDataset[]>([]);
  const [analyses, setAnalyses] = useState<AnalysisAsset[]>([]);
  const [draft, setDraft] = useState<AnalysisAsset>(() => defaultDraft());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const [datasetValues, analysisValues] = await Promise.all([
        fetchAnalysisDatasets(),
        fetchAnalyses(),
      ]);
      setDatasets(datasetValues);
      setAnalyses(analysisValues);
      setDraft((current) => {
        if (current.id) {
          const refreshed = analysisValues.find((item) => item.id === current.id);
          if (refreshed) return cloneAsset(refreshed);
        }
        return analysisValues[0] ? cloneAsset(analysisValues[0]) : defaultDraft(datasetValues[0]);
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

  const selectAnalysis = (analysis: AnalysisAsset) => setDraft(cloneAsset(analysis));

  const createNew = () => setDraft(defaultDraft(datasets[0]));

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
  };

  const changeType = (type: ChartType) => {
    const firstDimension = draft.dimensions[0] ?? dimensionOptions[0]?.value;
    const firstMetric = draft.metrics[0] ?? (metricOptions[0]
      ? { field: metricOptions[0].value, aggregation: 'SUM' as Aggregation }
      : undefined);
    setDraft((current) => ({
      ...current,
      type,
      dimensions: type === 'metric' ? [] : firstDimension ? [firstDimension] : [],
      metrics: type === 'pie' || type === 'metric'
        ? firstMetric ? [firstMetric] : []
        : current.metrics.length ? current.metrics : firstMetric ? [firstMetric] : [],
      sort: undefined,
      style: { ...defaultStyle(type), ...current.style, smooth: type === 'line' ? current.style.smooth : false },
      limit: type === 'table' ? 200 : 500,
    }));
  };

  const updateMetricFields = (fields: string[]) => {
    const previous = new Map(draft.metrics.map((metric) => [metric.field, metric]));
    const limit = draft.type === 'pie' || draft.type === 'metric' ? 1 : 3;
    const metrics: MetricBinding[] = fields.slice(0, limit).map((field) => (
      previous.get(field) ?? { field, aggregation: 'SUM' }
    ));
    const sort = draft.sort && !draft.dimensions.includes(draft.sort.field)
      && !metrics.some((metric) => metric.field === draft.sort?.field)
      ? undefined
      : draft.sort;
    setDraft((current) => ({ ...current, metrics, sort }));
  };

  const updateDimensions = (dimensions: string[]) => {
    const limit = draft.type === 'pie' ? 1 : draft.type === 'table' ? 3 : 1;
    const next = dimensions.slice(0, limit);
    const sort = draft.sort && !next.includes(draft.sort.field)
      && !draft.metrics.some((metric) => metric.field === draft.sort?.field)
      ? undefined
      : draft.sort;
    setDraft((current) => ({ ...current, dimensions: next, sort }));
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
      setDraft(next[0] ? cloneAsset(next[0]) : defaultDraft(datasets[0]));
      message.success('Analysis 已删除');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除 Analysis 失败');
    }
  };

  return (
    <div className="flex h-[calc(100vh-48px)] min-h-[660px] flex-col overflow-hidden bg-[#f4f6f8]" style={BRAND_CSS_VARIABLES}>
      <header className="flex h-12 shrink-0 items-center gap-3 border-b border-[#e5e7eb] bg-white px-4">
        <div className="flex min-w-0 flex-1 items-center gap-2">
          <FileBarChart size={16} className="text-[#475467]" />
          <div className="min-w-0">
            <div className="text-[13px] font-semibold text-[#161823]">图表分析</div>
            <div className="text-[10px] text-[#98a2b3]">独立可复用 Analysis 资产 · Dashboard 通过 analysisId 引用</div>
          </div>
        </div>
        <Button size="small" icon={<RefreshCw size={12} />} onClick={() => void load()} loading={loading}>刷新</Button>
        <Button size="small" icon={<Plus size={12} />} onClick={createNew}>新建</Button>
        {draft.id ? (
          <Popconfirm title="确认删除这个 Analysis？" onConfirm={() => void remove()}>
            <Button size="small" danger icon={<Trash2 size={12} />}>删除</Button>
          </Popconfirm>
        ) : null}
        <Button type="primary" size="small" icon={<Save size={12} />} loading={saving} onClick={() => void save()}>保存</Button>
      </header>

      <div className="flex min-h-0 flex-1 overflow-hidden">
        <aside className="flex w-[260px] shrink-0 flex-col border-r border-[#e5e7eb] bg-white">
          <div className="border-b border-[#edf0f3] px-3 py-2 text-[11px] font-medium text-[#667085]">
            Analysis 资产 <span className="ml-1 text-[#98a2b3]">{analyses.length}</span>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto p-2">
            {loading && !analyses.length ? <div className="flex justify-center py-8"><Spin size="small" /></div> : null}
            {!loading && !analyses.length ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无 Analysis" className="mt-8" /> : null}
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
                    <span className="mt-0.5 block truncate text-[9px] text-[#98a2b3]">{source?.name ?? `Dataset #${analysis.datasetId}`}</span>
                  </span>
                </button>
              );
            })}
          </div>
        </aside>

        <main className="min-w-0 flex-1 overflow-auto p-4">
          {loadError ? (
            <div className="mb-3 border border-[#fecdca] bg-[#fffbfa] px-3 py-2 text-[11px] text-[#b42318]">{loadError}</div>
          ) : null}
          <div className="mx-auto flex min-h-[520px] max-w-[1200px] flex-col border border-[#dfe3e8] bg-white">
            <div className="flex h-10 shrink-0 items-center border-b border-[#edf0f3] px-4">
              <span className="min-w-0 flex-1 truncate text-[12px] font-medium text-[#344054]">{draft.name || '未命名分析'}</span>
              <span className="text-[10px] text-[#98a2b3]">{dataset ? `${dataset.name} · DV${dataset.currentVersionNo ?? '-'}` : '未选择 Dataset'}</span>
            </div>
            <div className="min-h-[460px] flex-1 p-4">
              <AnalysisPreview spec={draft} dataset={dataset} className="h-full min-h-[420px]" />
            </div>
          </div>
        </main>

        <aside className="w-[336px] shrink-0 overflow-y-auto border-l border-[#e5e7eb] bg-white p-3">
          <div className="mb-2 text-[11px] font-medium text-[#667085]">基本信息</div>
          <Input size="small" value={draft.name} placeholder="Analysis 名称" onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))} />
          <Input.TextArea className="mt-2" rows={2} value={draft.description} placeholder="描述（可选）" onChange={(event) => setDraft((current) => ({ ...current, description: event.target.value }))} />

          <div className="mb-2 mt-5 border-t border-[#edf0f3] pt-4 text-[11px] font-medium text-[#667085]">数据与图表</div>
          <Select
            size="small"
            className="w-full"
            value={draft.datasetId || undefined}
            placeholder="选择 ONLINE Dataset"
            options={datasets.map((item) => ({ label: item.name, value: item.id }))}
            onChange={changeDataset}
          />
          <Select size="small" className="mt-2 w-full" value={draft.type} options={CHART_OPTIONS} onChange={changeType} />

          {draft.type !== 'metric' ? (
            <>
              <div className="mb-1 mt-4 text-[10px] text-[#667085]">维度</div>
              <Select
                mode="multiple"
                size="small"
                className="w-full"
                value={draft.dimensions}
                options={dimensionOptions}
                onChange={updateDimensions}
                maxTagCount="responsive"
              />
            </>
          ) : null}

          <div className="mb-1 mt-4 text-[10px] text-[#667085]">指标</div>
          <Select
            mode="multiple"
            size="small"
            className="w-full"
            value={draft.metrics.map((metric) => metric.field)}
            options={metricOptions}
            onChange={updateMetricFields}
            maxTagCount="responsive"
          />
          <div className="mt-2 space-y-1.5">
            {draft.metrics.map((metric) => (
              <div key={metric.field} className="flex items-center gap-2 rounded-[4px] bg-[#f7f8fa] px-2 py-1">
                <span className="min-w-0 flex-1 truncate text-[10px] text-[#475467]">
                  {dataset?.fields.find((field) => field.key === metric.field)?.label ?? metric.field}
                </span>
                <Select
                  size="small"
                  variant="borderless"
                  className="w-[88px]"
                  value={metric.aggregation}
                  options={AGGREGATION_OPTIONS}
                  onChange={(aggregation: Aggregation) => setDraft((current) => ({
                    ...current,
                    metrics: current.metrics.map((item) => item.field === metric.field ? { ...item, aggregation } : item),
                  }))}
                />
              </div>
            ))}
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
              options={[{ label: '升序', value: 'asc' }, { label: '降序', value: 'desc' }]}
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
              filters: current.filters[0] ? [{ ...current.filters[0], value: event.target.value }] : [],
            }))}
          />

          <div className="mb-2 mt-5 border-t border-[#edf0f3] pt-4 text-[11px] font-medium text-[#667085]">样式</div>
          <div className="space-y-3 text-[11px] text-[#475467]">
            <label className="flex items-center justify-between"><span>显示图例</span><Switch size="small" checked={draft.style.showLegend} onChange={(showLegend) => setDraft((current) => ({ ...current, style: { ...current.style, showLegend } }))} /></label>
            <label className="flex items-center justify-between"><span>数据标签</span><Switch size="small" checked={draft.style.showDataLabels} onChange={(showDataLabels) => setDraft((current) => ({ ...current, style: { ...current.style, showDataLabels } }))} /></label>
            <label className="flex items-center justify-between"><span>平滑折线</span><Switch size="small" disabled={draft.type !== 'line'} checked={draft.style.smooth} onChange={(smooth) => setDraft((current) => ({ ...current, style: { ...current.style, smooth } }))} /></label>
            <label className="flex items-center justify-between"><span>网格线</span><Switch size="small" checked={draft.style.showGrid} onChange={(showGrid) => setDraft((current) => ({ ...current, style: { ...current.style, showGrid } }))} /></label>
          </div>
        </aside>
      </div>
    </div>
  );
}
