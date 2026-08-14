import { createAnalysis, fetchAnalyses } from '@/components/analysis/analysis-service';
import type { AnalysisSpec } from '@/components/analysis/model';
import { message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { DEFAULT_DASHBOARD } from './defaults';
import {
  activateDashboardVersion,
  createDashboard,
  fetchDashboard,
  fetchDashboards,
  saveDashboardVersion,
  toDashboardDocument,
} from './dashboard-service';
import {
  cloneDashboard,
  createInlineAnalysis,
  createWidget,
  findDataset,
  isPersistedDashboard,
  loadDashboard,
  reconcileDashboard,
  STORAGE_KEY,
} from './helpers';
import type {
  AnalysisAsset,
  ChartType,
  DashboardDocument,
  DashboardSummary,
  DashboardVersionSummary,
  DashboardWidget,
  DatasetField,
  PublishedDataset,
} from './model';
import { fetchDashboardDatasets } from './service';

const linkWidget = (widget: DashboardWidget, analysisId: string): DashboardWidget => ({
  id: widget.id,
  analysisId,
  x: widget.x,
  y: widget.y,
  w: widget.w,
  h: widget.h,
  minW: widget.minW,
  minH: widget.minH,
});

const assetSpec = (analysis: AnalysisAsset): AnalysisSpec => ({
  type: analysis.type,
  datasetId: analysis.datasetId,
  dimensions: [...analysis.dimensions],
  metrics: analysis.metrics.map((metric) => ({ ...metric })),
  filters: analysis.filters.map((filter, index) => ({ ...filter, id: `detached-${analysis.id}-${index}` })),
  sort: analysis.sort ? { ...analysis.sort } : undefined,
  style: { ...analysis.style },
  limit: analysis.limit,
  timeoutSeconds: analysis.timeoutSeconds,
});

export function useDashboardDesigner() {
  const [dashboard, setDashboard] = useState<DashboardDocument>(loadDashboard);
  const [datasets, setDatasets] = useState<PublishedDataset[]>([]);
  const [datasetsLoading, setDatasetsLoading] = useState(true);
  const [datasetsError, setDatasetsError] = useState('');
  const [analyses, setAnalyses] = useState<AnalysisAsset[]>([]);
  const [analysesLoading, setAnalysesLoading] = useState(true);
  const [analysesError, setAnalysesError] = useState('');
  const [dashboardAssets, setDashboardAssets] = useState<DashboardSummary[]>([]);
  const [dashboardVersions, setDashboardVersions] = useState<DashboardVersionSummary[]>([]);
  const [dashboardsLoading, setDashboardsLoading] = useState(true);
  const [dashboardSaving, setDashboardSaving] = useState(false);
  const [selectedId, setSelectedId] = useState<string>();
  const [preview, setPreview] = useState(false);
  const didAutoOpen = useRef(false);
  const widgets = dashboard.widgets;
  const selectedWidget = widgets.find((widget) => widget.id === selectedId);
  const activeDataset = useMemo(
    () => findDataset(datasets, dashboard.activeDatasetId),
    [dashboard.activeDatasetId, datasets],
  );

  const refreshDatasets = useCallback(async () => {
    setDatasetsLoading(true);
    setDatasetsError('');
    try {
      const values = await fetchDashboardDatasets();
      setDatasets(values);
      if (!values.length) setDatasetsError('暂无可用于分析的 ONLINE Dataset');
    } catch (error) {
      setDatasets([]);
      setDatasetsError(error instanceof Error ? error.message : '加载 Dataset 失败');
    } finally {
      setDatasetsLoading(false);
    }
  }, []);

  const refreshAnalyses = useCallback(async () => {
    setAnalysesLoading(true);
    setAnalysesError('');
    try {
      setAnalyses(await fetchAnalyses());
    } catch (error) {
      setAnalyses([]);
      setAnalysesError(error instanceof Error ? error.message : '加载 Analysis 失败');
    } finally {
      setAnalysesLoading(false);
    }
  }, []);

  const refreshDashboardAssets = useCallback(async () => {
    setDashboardsLoading(true);
    try {
      setDashboardAssets(await fetchDashboards());
    } catch (error) {
      setDashboardAssets([]);
      message.error(error instanceof Error ? error.message : '加载 Dashboard 列表失败');
    } finally {
      setDashboardsLoading(false);
    }
  }, []);

  const openDashboard = useCallback(async (dashboardId: string) => {
    try {
      const detail = await fetchDashboard(dashboardId);
      setDashboard(toDashboardDocument(detail));
      setDashboardVersions(detail.versions);
      setSelectedId(undefined);
      window.localStorage.removeItem(STORAGE_KEY);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 Dashboard 失败');
    }
  }, []);

  useEffect(() => {
    void refreshDatasets();
    void refreshAnalyses();
    void refreshDashboardAssets();
  }, [refreshDatasets, refreshAnalyses, refreshDashboardAssets]);

  useEffect(() => {
    if (didAutoOpen.current || dashboardsLoading) return;
    didAutoOpen.current = true;
    const hasLegacyDraft = !isPersistedDashboard(dashboard.id)
      && (dashboard.widgets.length > 0 || dashboard.name !== DEFAULT_DASHBOARD.name);
    if (!hasLegacyDraft && dashboardAssets.length) void openDashboard(dashboardAssets[0].id);
  }, [dashboard.id, dashboard.name, dashboard.widgets.length, dashboardAssets, dashboardsLoading, openDashboard]);

  useEffect(() => {
    if (!datasets.length) return;
    setDashboard((current) => reconcileDashboard(current, datasets));
  }, [datasets]);

  const updateWidget = (id: string, patch: Partial<DashboardWidget>) => setDashboard((current) => ({
    ...current,
    widgets: current.widgets.map((widget) => widget.id === id ? { ...widget, ...patch } : widget),
  }));

  const updateInlineAnalysis = (id: string, patch: Partial<AnalysisSpec>) => setDashboard((current) => ({
    ...current,
    widgets: current.widgets.map((widget) => {
      if (widget.id !== id || widget.analysisId || !widget.inlineAnalysis) return widget;
      return { ...widget, inlineAnalysis: { ...widget.inlineAnalysis, ...patch } };
    }),
  }));

  const maxY = () => widgets.reduce((value, widget) => Math.max(value, widget.y + widget.h), 0);

  const addWidget = (type: ChartType) => {
    if (!activeDataset) return void message.info('请先发布一个 ONLINE Dataset');
    const next = createWidget(type, activeDataset, maxY());
    setDashboard((current) => ({ ...current, widgets: [...current.widgets, next] }));
    setSelectedId(next.id);
  };

  const addAnalysis = (analysisId: string) => {
    const analysis = analyses.find((item) => item.id === analysisId);
    if (!analysis) return void message.warning('Analysis 不存在或已删除');
    const dataset = datasets.find((item) => item.id === analysis.datasetId);
    if (!dataset) return void message.warning('Analysis 依赖的 Dataset 已下线或不可用');
    const base = createWidget(analysis.type, dataset, maxY());
    const next = linkWidget(base, analysis.id);
    setDashboard((current) => ({ ...current, activeDatasetId: analysis.datasetId, widgets: [...current.widgets, next] }));
    setSelectedId(next.id);
  };

  const saveWidgetAsAnalysis = async (id: string) => {
    const source = widgets.find((widget) => widget.id === id);
    if (!source) return;
    if (source.analysisId) return void message.info('该组件已经引用 Analysis');
    if (!source.inlineAnalysis) return void message.warning('当前组件缺少可保存的分析定义');
    try {
      const analysis = await createAnalysis(source.title || '未命名分析', '', source.inlineAnalysis);
      setAnalyses((current) => [analysis, ...current.filter((item) => item.id !== analysis.id)]);
      setDashboard((current) => ({
        ...current,
        widgets: current.widgets.map((widget) => widget.id === id ? linkWidget(widget, analysis.id) : widget),
      }));
      message.success('已保存为可复用 Analysis，Dashboard 已切换为引用模式');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存 Analysis 失败');
    }
  };

  const detachAnalysis = (id: string) => {
    const widget = widgets.find((item) => item.id === id);
    if (!widget?.analysisId) return;
    const analysis = analyses.find((item) => item.id === widget.analysisId);
    if (!analysis) return void message.warning('引用的 Analysis 已不可用，无法生成独立副本');
    updateWidget(id, { analysisId: undefined, title: analysis.name, inlineAnalysis: assetSpec(analysis) });
    message.success('已解除 Analysis 引用，当前组件已复制为 Dashboard 本地分析');
  };

  const duplicateWidget = (id: string) => {
    const source = widgets.find((widget) => widget.id === id);
    if (!source) return;
    const next: DashboardWidget = {
      ...source,
      id: `widget-${Date.now()}-${Math.round(Math.random() * 1000)}`,
      y: maxY(),
      inlineAnalysis: source.inlineAnalysis
        ? JSON.parse(JSON.stringify(source.inlineAnalysis)) as AnalysisSpec
        : undefined,
    };
    setDashboard((current) => ({ ...current, widgets: [...current.widgets, next] }));
    setSelectedId(next.id);
  };

  const deleteWidget = (id: string) => {
    setDashboard((current) => ({ ...current, widgets: current.widgets.filter((widget) => widget.id !== id) }));
    setSelectedId((current) => current === id ? undefined : current);
  };

  const changeWidgetDataset = (id: string, datasetId: string) => {
    const widget = widgets.find((item) => item.id === id);
    if (widget?.analysisId) return void message.info('该组件引用 Analysis，请先解除引用后再编辑');
    if (!widget?.inlineAnalysis) return;
    const dataset = findDataset(datasets, datasetId);
    if (!dataset) return;
    updateWidget(id, { inlineAnalysis: createInlineAnalysis(widget.inlineAnalysis.type, dataset) });
  };

  const addField = (field: DatasetField) => {
    if (!selectedWidget) return void message.info('请先选择一个图表组件');
    if (selectedWidget.analysisId) return void message.info('该组件引用 Analysis，请先解除引用后再编辑');
    const spec = selectedWidget.inlineAnalysis;
    if (!spec) return;
    if (!activeDataset) return void message.info('当前没有可用 Dataset');
    if (spec.datasetId !== activeDataset.id) {
      changeWidgetDataset(selectedWidget.id, activeDataset.id);
      return void message.info('已切换图表数据集，请再次添加字段');
    }
    if (field.role === 'dimension') {
      if (spec.type === 'metric') return void message.info('指标卡不需要维度');
      const limit = spec.type === 'table' ? 3 : 1;
      if (!spec.dimensions.includes(field.key)) {
        updateInlineAnalysis(selectedWidget.id, { dimensions: [...spec.dimensions, field.key].slice(0, limit) });
      }
      return;
    }
    const limit = ['table', 'line', 'bar'].includes(spec.type) ? 3 : 1;
    if (!spec.metrics.some((metric) => metric.field === field.key)) {
      updateInlineAnalysis(selectedWidget.id, {
        metrics: [...spec.metrics, { field: field.key, aggregation: 'SUM' }].slice(0, limit),
      });
    }
  };

  const save = async () => {
    if (!dashboard.name.trim()) return void message.warning('请输入仪表盘名称');
    setDashboardSaving(true);
    try {
      const detail = isPersistedDashboard(dashboard.id)
        ? await saveDashboardVersion(dashboard.id, dashboard)
        : await createDashboard(dashboard);
      const document = toDashboardDocument(detail);
      setDashboard(document);
      setDashboardVersions(detail.versions);
      setDashboardAssets((current) => [
        detail.dashboard,
        ...current.filter((item) => item.id !== detail.dashboard.id),
      ]);
      window.localStorage.removeItem(STORAGE_KEY);
      message.success(`仪表盘已保存为 V${detail.dashboard.currentVersionNo}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存 Dashboard 失败');
    } finally {
      setDashboardSaving(false);
    }
  };

  const activateVersion = async (versionNo: number) => {
    if (!isPersistedDashboard(dashboard.id) || versionNo === dashboard.currentVersionNo) return;
    setDashboardSaving(true);
    try {
      const detail = await activateDashboardVersion(dashboard.id, versionNo);
      setDashboard(toDashboardDocument(detail));
      setDashboardVersions(detail.versions);
      setDashboardAssets((current) => current.map((item) => item.id === detail.dashboard.id ? detail.dashboard : item));
      setSelectedId(undefined);
      message.success(`已切换到 Dashboard V${versionNo}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '切换 DashboardVersion 失败');
    } finally {
      setDashboardSaving(false);
    }
  };

  const newDashboard = () => {
    const next = reconcileDashboard(cloneDashboard(DEFAULT_DASHBOARD), datasets);
    setDashboard(next);
    setDashboardVersions([]);
    setSelectedId(undefined);
    window.localStorage.removeItem(STORAGE_KEY);
  };

  return {
    dashboard,
    widgets,
    datasets,
    datasetsLoading,
    datasetsError,
    analyses,
    analysesLoading,
    analysesError,
    dashboardAssets,
    dashboardVersions,
    dashboardsLoading,
    dashboardSaving,
    selectedWidget,
    activeDataset,
    selectedId,
    preview,
    setDashboard,
    setSelectedId,
    setPreview,
    updateWidget,
    updateInlineAnalysis,
    addWidget,
    addAnalysis,
    saveWidgetAsAnalysis,
    detachAnalysis,
    duplicateWidget,
    deleteWidget,
    changeWidgetDataset,
    addField,
    save,
    activateVersion,
    newDashboard,
    openDashboard,
    refreshDatasets,
    refreshAnalyses,
    refreshDashboardAssets,
  };
}
