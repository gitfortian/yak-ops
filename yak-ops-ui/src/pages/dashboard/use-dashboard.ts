import { fetchAnalyses } from '@/components/analysis/analysis-service';
import type {
  AnalysisFilter,
  AnalysisSelection,
  AnalysisSpec,
  Scalar,
} from '@/components/analysis/model';
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
  DashboardGlobalFilter,
  DashboardSummary,
  DashboardVersionSummary,
  DashboardWidget,
  PublishedDataset,
} from './model';
import { fetchDashboardDatasets } from './service';

const assetSpec = (analysis: AnalysisAsset): AnalysisSpec => ({
  type: analysis.type,
  datasetId: analysis.datasetId,
  dimensions: [...analysis.dimensions],
  metrics: analysis.metrics.map((metric) => ({ ...metric })),
  filters: analysis.filters.map((filter, index) => ({
    ...filter,
    id: `detached-${analysis.id}-${index}`,
  })),
  sort: analysis.sort ? { ...analysis.sort } : undefined,
  style: { ...analysis.style },
  limit: analysis.limit,
  timeoutSeconds: analysis.timeoutSeconds,
});

const runtimeDefaults = (filters: DashboardGlobalFilter[]) => Object.fromEntries(
  filters.map((filter) => [filter.id, filter.defaultValue]),
) as Record<string, Scalar | undefined>;

const hasOwn = (value: Record<string, Scalar | undefined>, key: string) =>
  Object.prototype.hasOwnProperty.call(value, key);

export function useDashboardDesigner() {
  const [dashboard, setDashboard] = useState<DashboardDocument>(loadDashboard);
  const [datasets, setDatasets] = useState<PublishedDataset[]>([]);
  const [datasetsLoading, setDatasetsLoading] = useState(true);
  const [analyses, setAnalyses] = useState<AnalysisAsset[]>([]);
  const [dashboardAssets, setDashboardAssets] = useState<DashboardSummary[]>([]);
  const [dashboardVersions, setDashboardVersions] = useState<DashboardVersionSummary[]>([]);
  const [dashboardsLoading, setDashboardsLoading] = useState(true);
  const [dashboardSaving, setDashboardSaving] = useState(false);
  const [runtimeFilterValues, setRuntimeFilterValues] = useState<Record<string, Scalar | undefined>>({});
  const [selectedId, setSelectedId] = useState<string>();
  const [preview, setPreview] = useState(false);
  const didAutoOpen = useRef(false);

  const widgets = dashboard.widgets;
  const selectedWidget = widgets.find((widget) => widget.id === selectedId);
  const activeDataset = useMemo(
    () => findDataset(datasets, dashboard.activeDatasetId),
    [dashboard.activeDatasetId, datasets],
  );

  const loadDatasets = useCallback(async () => {
    setDatasetsLoading(true);
    try {
      setDatasets(await fetchDashboardDatasets());
    } catch {
      setDatasets([]);
    } finally {
      setDatasetsLoading(false);
    }
  }, []);

  const loadAnalyses = useCallback(async () => {
    try {
      setAnalyses(await fetchAnalyses());
    } catch {
      setAnalyses([]);
    }
  }, []);

  const loadDashboardAssets = useCallback(async () => {
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
    void loadDatasets();
    void loadAnalyses();
    void loadDashboardAssets();
  }, [loadDatasets, loadAnalyses, loadDashboardAssets]);

  useEffect(() => {
    if (didAutoOpen.current || dashboardsLoading) return;
    didAutoOpen.current = true;
    const hasLegacyDraft = !isPersistedDashboard(dashboard.id)
      && (dashboard.widgets.length > 0 || dashboard.name !== DEFAULT_DASHBOARD.name);
    if (!hasLegacyDraft && dashboardAssets.length) void openDashboard(dashboardAssets[0].id);
  }, [
    dashboard.id,
    dashboard.name,
    dashboard.widgets.length,
    dashboardAssets,
    dashboardsLoading,
    openDashboard,
  ]);

  useEffect(() => {
    if (!datasets.length) return;
    setDashboard((current) => reconcileDashboard(current, datasets));
  }, [datasets]);

  useEffect(() => {
    setRuntimeFilterValues(runtimeDefaults(dashboard.globalFilters));
  }, [dashboard.id, dashboard.currentVersionId]);

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

  const setRuntimeFilterValue = (filterId: string, value: Scalar | undefined) => {
    setRuntimeFilterValues((current) => ({ ...current, [filterId]: value }));
  };

  const resetRuntimeFilters = () => setRuntimeFilterValues(runtimeDefaults(dashboard.globalFilters));

  const runtimeFiltersForWidget = useCallback((widgetId: string): AnalysisFilter[] => (
    dashboard.globalFilters.flatMap((filter) => {
      const binding = filter.bindings.find((item) => item.widgetId === widgetId);
      if (!binding) return [];
      const value = hasOwn(runtimeFilterValues, filter.id)
        ? runtimeFilterValues[filter.id]
        : filter.defaultValue;
      if (value === undefined || value === null || value === '') return [];
      return [{
        id: `dashboard-${filter.id}`,
        field: binding.field,
        operator: filter.operator,
        value: String(value),
      }];
    })
  ), [dashboard.globalFilters, runtimeFilterValues]);

  const handleWidgetSelection = useCallback((widgetId: string, selection: AnalysisSelection) => {
    const matched = dashboard.interactions.filter((interaction) => (
      interaction.event === 'select'
      && interaction.sourceWidgetId === widgetId
      && interaction.sourceField === selection.fieldId
    ));
    if (!matched.length) return;
    setRuntimeFilterValues((current) => {
      const next = { ...current };
      matched.forEach((interaction) => {
        next[interaction.targetFilterId] = selection.value;
      });
      return next;
    });
  }, [dashboard.interactions]);

  const maxY = () => widgets.reduce((value, widget) => Math.max(value, widget.y + widget.h), 0);

  const addWidget = (type: ChartType) => {
    if (!activeDataset) return void message.info('请先发布一个 ONLINE Dataset');
    const next = createWidget(type, activeDataset, maxY());
    setDashboard((current) => ({ ...current, widgets: [...current.widgets, next] }));
    setSelectedId(next.id);
  };

  const detachAnalysis = (id: string) => {
    const widget = widgets.find((item) => item.id === id);
    if (!widget?.analysisId) return;
    const analysis = analyses.find((item) => item.id === widget.analysisId);
    if (!analysis) return void message.warning('引用的 Analysis 已不可用，无法生成独立副本');
    updateWidget(id, {
      analysisId: undefined,
      title: analysis.name,
      inlineAnalysis: assetSpec(analysis),
    });
    message.success('已解除 Analysis 引用，当前组件已复制为 Dashboard 本地图表');
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
    setDashboard((current) => ({
      ...current,
      widgets: current.widgets.filter((widget) => widget.id !== id),
      globalFilters: current.globalFilters.map((filter) => ({
        ...filter,
        bindings: filter.bindings.filter((binding) => binding.widgetId !== id),
      })),
      interactions: current.interactions.filter((interaction) => interaction.sourceWidgetId !== id),
    }));
    setSelectedId((current) => current === id ? undefined : current);
  };

  const changeWidgetDataset = (id: string, datasetId: string) => {
    const widget = widgets.find((item) => item.id === id);
    if (widget?.analysisId) return void message.info('该组件引用历史共享图表，请先复制为可编辑图表');
    if (!widget?.inlineAnalysis) return;
    const dataset = findDataset(datasets, datasetId);
    if (!dataset) return;
    setDashboard((current) => ({
      ...current,
      widgets: current.widgets.map((item) => item.id === id
        ? { ...item, inlineAnalysis: createInlineAnalysis(item.inlineAnalysis!.type, dataset) }
        : item),
      globalFilters: current.globalFilters.map((filter) => ({
        ...filter,
        bindings: filter.bindings.filter((binding) => binding.widgetId !== id),
      })),
      interactions: current.interactions.filter((interaction) => interaction.sourceWidgetId !== id),
    }));
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
      setDashboardAssets((current) => current.map((item) => (
        item.id === detail.dashboard.id ? detail.dashboard : item
      )));
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
    setRuntimeFilterValues({});
    window.localStorage.removeItem(STORAGE_KEY);
  };

  return {
    dashboard,
    widgets,
    datasets,
    datasetsLoading,
    analyses,
    dashboardAssets,
    dashboardVersions,
    dashboardsLoading,
    dashboardSaving,
    runtimeFilterValues,
    selectedWidget,
    activeDataset,
    selectedId,
    preview,
    setDashboard,
    setSelectedId,
    setPreview,
    updateWidget,
    updateInlineAnalysis,
    setRuntimeFilterValue,
    resetRuntimeFilters,
    runtimeFiltersForWidget,
    handleWidgetSelection,
    addWidget,
    detachAnalysis,
    duplicateWidget,
    deleteWidget,
    changeWidgetDataset,
    save,
    activateVersion,
    newDashboard,
    openDashboard,
  };
}
