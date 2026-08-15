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
  createDashboard,
  fetchDashboard,
  publishDashboard,
  restoreDashboardVersion,
  saveDashboardVersion,
  toDashboardDocument,
} from './dashboard-service';
import {
  cloneDashboard,
  createInlineAnalysis,
  createWidget,
  findDataset,
  isPersistedDashboard,
  reconcileDashboard,
  STORAGE_KEY,
} from './helpers';
import type {
  AnalysisAsset,
  ChartType,
  DashboardDocument,
  DashboardGlobalFilter,
  DashboardInteraction,
  DashboardServerDetail,
  DashboardVersionSummary,
  DashboardWidget,
  PublishedDataset,
} from './model';
import { fetchDashboardDatasets } from './service';

const HISTORY_LIMIT = 50;
const HISTORY_MERGE_WINDOW = 500;

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

const dashboardFingerprint = (dashboard: DashboardDocument) => JSON.stringify({
  name: dashboard.name,
  description: dashboard.description,
  activeDatasetId: dashboard.activeDatasetId,
  widgets: dashboard.widgets,
  globalFilters: dashboard.globalFilters,
  interactions: dashboard.interactions,
});

const trimHistory = (items: DashboardDocument[]) => (
  items.length > HISTORY_LIMIT ? items.slice(items.length - HISTORY_LIMIT) : items
);

export function useDashboardDesigner(dashboardId?: string) {
  const initialDashboard = useMemo(() => cloneDashboard(DEFAULT_DASHBOARD), []);
  const [dashboard, setDashboardState] = useState<DashboardDocument>(initialDashboard);
  const dashboardRef = useRef<DashboardDocument>(initialDashboard);
  const savedFingerprintRef = useRef(dashboardFingerprint(initialDashboard));
  const undoStackRef = useRef<DashboardDocument[]>([]);
  const redoStackRef = useRef<DashboardDocument[]>([]);
  const lastHistoryRef = useRef<{ key: string; at: number }>();

  const [datasets, setDatasets] = useState<PublishedDataset[]>([]);
  const [datasetsLoading, setDatasetsLoading] = useState(true);
  const [analyses, setAnalyses] = useState<AnalysisAsset[]>([]);
  const [dashboardVersions, setDashboardVersions] = useState<DashboardVersionSummary[]>([]);
  const [dashboardSaving, setDashboardSaving] = useState(false);
  const [dashboardPublishing, setDashboardPublishing] = useState(false);
  const [runtimeFilterValues, setRuntimeFilterValues] = useState<Record<string, Scalar | undefined>>({});
  const [selectedId, setSelectedId] = useState<string>();
  const [preview, setPreview] = useState(false);

  const widgets = dashboard.widgets;
  const selectedWidget = widgets.find((widget) => widget.id === selectedId);
  const activeDataset = useMemo(
    () => findDataset(datasets, dashboard.activeDatasetId),
    [dashboard.activeDatasetId, datasets],
  );
  const dirty = dashboardFingerprint(dashboard) !== savedFingerprintRef.current;
  const canUndo = undoStackRef.current.length > 0;
  const canRedo = redoStackRef.current.length > 0;
  const persisted = isPersistedDashboard(dashboard.id);
  const hasPublishedVersion = Boolean(dashboard.publishedVersionId);
  const hasUnpublishedDraft = Boolean(
    dashboard.currentVersionId
    && dashboard.currentVersionId !== dashboard.publishedVersionId,
  );
  const canPublish = !persisted || dirty || hasUnpublishedDraft || !hasPublishedVersion;

  const setDashboardWithoutHistory = useCallback((next: DashboardDocument) => {
    const cloned = cloneDashboard(next);
    dashboardRef.current = cloned;
    setDashboardState(cloned);
    setSelectedId((current) => (
      current && cloned.widgets.some((widget) => widget.id === current) ? current : undefined
    ));
  }, []);

  const resetDashboardState = useCallback((next: DashboardDocument, markSaved = true) => {
    const cloned = cloneDashboard(next);
    dashboardRef.current = cloned;
    undoStackRef.current = [];
    redoStackRef.current = [];
    lastHistoryRef.current = undefined;
    if (markSaved) savedFingerprintRef.current = dashboardFingerprint(cloned);
    setDashboardState(cloned);
    setSelectedId(undefined);
  }, []);

  const applyServerDetail = useCallback((detail: DashboardServerDetail) => {
    const document = toDashboardDocument(detail);
    resetDashboardState(document);
    setDashboardVersions(detail.versions);
    window.localStorage.removeItem(STORAGE_KEY);
    return document;
  }, [resetDashboardState]);

  const commitDashboard = useCallback((
    updater: DashboardDocument | ((current: DashboardDocument) => DashboardDocument),
    historyKey: string,
  ) => {
    const current = dashboardRef.current;
    const next = typeof updater === 'function' ? updater(current) : updater;
    if (dashboardFingerprint(current) === dashboardFingerprint(next)) return;

    const now = Date.now();
    const last = lastHistoryRef.current;
    const mergeWithPrevious = Boolean(
      last
      && last.key === historyKey
      && now - last.at <= HISTORY_MERGE_WINDOW,
    );

    if (!mergeWithPrevious) {
      undoStackRef.current = trimHistory([
        ...undoStackRef.current,
        cloneDashboard(current),
      ]);
    }
    redoStackRef.current = [];
    lastHistoryRef.current = { key: historyKey, at: now };
    setDashboardWithoutHistory(next);
  }, [setDashboardWithoutHistory]);

  const undo = useCallback(() => {
    const previous = undoStackRef.current.at(-1);
    if (!previous) return;
    undoStackRef.current = undoStackRef.current.slice(0, -1);
    redoStackRef.current = trimHistory([
      ...redoStackRef.current,
      cloneDashboard(dashboardRef.current),
    ]);
    lastHistoryRef.current = undefined;
    setDashboardWithoutHistory(previous);
  }, [setDashboardWithoutHistory]);

  const redo = useCallback(() => {
    const next = redoStackRef.current.at(-1);
    if (!next) return;
    redoStackRef.current = redoStackRef.current.slice(0, -1);
    undoStackRef.current = trimHistory([
      ...undoStackRef.current,
      cloneDashboard(dashboardRef.current),
    ]);
    lastHistoryRef.current = undefined;
    setDashboardWithoutHistory(next);
  }, [setDashboardWithoutHistory]);

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

  const openDashboard = useCallback(async (targetDashboardId: string) => {
    try {
      applyServerDetail(await fetchDashboard(targetDashboardId));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 Dashboard 失败');
    }
  }, [applyServerDetail]);

  useEffect(() => {
    void loadDatasets();
    void loadAnalyses();
  }, [loadDatasets, loadAnalyses]);

  useEffect(() => {
    if (dashboardId) {
      void openDashboard(dashboardId);
      return;
    }
    resetDashboardState(cloneDashboard(DEFAULT_DASHBOARD));
    setDashboardVersions([]);
    setRuntimeFilterValues({});
  }, [dashboardId, openDashboard, resetDashboardState]);

  useEffect(() => {
    if (!datasets.length) return;
    const current = dashboardRef.current;
    const next = reconcileDashboard(current, datasets);
    if (dashboardFingerprint(current) === dashboardFingerprint(next)) return;

    const wasClean = dashboardFingerprint(current) === savedFingerprintRef.current;
    if (wasClean) savedFingerprintRef.current = dashboardFingerprint(next);
    setDashboardWithoutHistory(next);
  }, [datasets, setDashboardWithoutHistory]);

  useEffect(() => {
    setRuntimeFilterValues(runtimeDefaults(dashboard.globalFilters));
  }, [dashboard.id, dashboard.currentVersionId]);

  useEffect(() => {
    if (!dirty) return undefined;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [dirty]);

  const updateDashboardName = (name: string) => commitDashboard(
    (current) => ({ ...current, name }),
    'dashboard:name',
  );

  const updateLayout = (
    nextLayout: readonly { i: string; x: number; y: number; w: number; h: number }[],
  ) => {
    const nextMap = new Map(nextLayout.map((item) => [item.i, item]));
    commitDashboard((current) => ({
      ...current,
      widgets: current.widgets.map((widget) => {
        const next = nextMap.get(widget.id);
        return next ? { ...widget, x: next.x, y: next.y, w: next.w, h: next.h } : widget;
      }),
    }), 'dashboard:layout');
  };

  const updateWidget = (id: string, patch: Partial<DashboardWidget>) => commitDashboard(
    (current) => ({
      ...current,
      widgets: current.widgets.map((widget) => widget.id === id ? { ...widget, ...patch } : widget),
    }),
    `widget:${id}:${Object.keys(patch).sort().join(',')}`,
  );

  const updateInlineAnalysis = (id: string, patch: Partial<AnalysisSpec>) => commitDashboard(
    (current) => ({
      ...current,
      widgets: current.widgets.map((widget) => {
        if (widget.id !== id || widget.analysisId || !widget.inlineAnalysis) return widget;
        return { ...widget, inlineAnalysis: { ...widget.inlineAnalysis, ...patch } };
      }),
    }),
    `widget:${id}:analysis:${Object.keys(patch).sort().join(',')}`,
  );

  const updateGlobalFilters = (filters: DashboardGlobalFilter[]) => {
    const filterIds = new Set(filters.map((filter) => filter.id));
    commitDashboard((current) => ({
      ...current,
      globalFilters: filters,
      interactions: current.interactions.filter((interaction) => filterIds.has(interaction.targetFilterId)),
    }), 'dashboard:global-filters');
    setRuntimeFilterValues((current) => Object.fromEntries(
      filters.map((filter) => [
        filter.id,
        hasOwn(current, filter.id) ? current[filter.id] : filter.defaultValue,
      ]),
    ));
  };

  const updateInteractions = (interactions: DashboardInteraction[]) => commitDashboard(
    (current) => ({ ...current, interactions }),
    'dashboard:interactions',
  );

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

  const maxY = () => dashboardRef.current.widgets.reduce(
    (value, widget) => Math.max(value, widget.y + widget.h),
    0,
  );

  const addWidget = (type: ChartType) => {
    if (!activeDataset) return void message.info('请先发布一个 ONLINE Dataset');
    const next = createWidget(type, activeDataset, maxY());
    commitDashboard(
      (current) => ({ ...current, widgets: [...current.widgets, next] }),
      `widget:add:${next.id}`,
    );
    setSelectedId(next.id);
  };

  const detachAnalysis = (id: string) => {
    const widget = dashboardRef.current.widgets.find((item) => item.id === id);
    if (!widget?.analysisId) return;
    const analysis = analyses.find((item) => item.id === widget.analysisId);
    if (!analysis) return void message.warning('引用的 Analysis 已不可用，无法生成独立副本');
    commitDashboard((current) => ({
      ...current,
      widgets: current.widgets.map((item) => item.id === id ? {
        ...item,
        analysisId: undefined,
        title: analysis.name,
        inlineAnalysis: assetSpec(analysis),
      } : item),
    }), `widget:${id}:detach`);
    message.success('已解除 Analysis 引用，当前组件已复制为 Dashboard 本地图表');
  };

  const duplicateWidget = (id: string) => {
    const source = dashboardRef.current.widgets.find((widget) => widget.id === id);
    if (!source) return;
    const next: DashboardWidget = {
      ...source,
      id: `widget-${Date.now()}-${Math.round(Math.random() * 1000)}`,
      y: maxY(),
      inlineAnalysis: source.inlineAnalysis
        ? JSON.parse(JSON.stringify(source.inlineAnalysis)) as AnalysisSpec
        : undefined,
    };
    commitDashboard(
      (current) => ({ ...current, widgets: [...current.widgets, next] }),
      `widget:duplicate:${id}`,
    );
    setSelectedId(next.id);
  };

  const deleteWidget = (id: string) => {
    commitDashboard((current) => ({
      ...current,
      widgets: current.widgets.filter((widget) => widget.id !== id),
      globalFilters: current.globalFilters.map((filter) => ({
        ...filter,
        bindings: filter.bindings.filter((binding) => binding.widgetId !== id),
      })),
      interactions: current.interactions.filter((interaction) => interaction.sourceWidgetId !== id),
    }), `widget:delete:${id}`);
    setSelectedId((current) => current === id ? undefined : current);
  };

  const changeWidgetDataset = (id: string, datasetId: string) => {
    const widget = dashboardRef.current.widgets.find((item) => item.id === id);
    if (widget?.analysisId) return void message.info('该组件引用历史共享图表，请先复制为可编辑图表');
    if (!widget?.inlineAnalysis) return;
    const dataset = findDataset(datasets, datasetId);
    if (!dataset) return;
    commitDashboard((current) => ({
      ...current,
      activeDatasetId: datasetId,
      widgets: current.widgets.map((item) => item.id === id
        ? { ...item, inlineAnalysis: createInlineAnalysis(item.inlineAnalysis!.type, dataset) }
        : item),
      globalFilters: current.globalFilters.map((filter) => ({
        ...filter,
        bindings: filter.bindings.filter((binding) => binding.widgetId !== id),
      })),
      interactions: current.interactions.filter((interaction) => interaction.sourceWidgetId !== id),
    }), `widget:${id}:dataset`);
  };

  const persistCurrentDraft = async (): Promise<DashboardServerDetail | undefined> => {
    const currentDashboard = dashboardRef.current;
    if (!currentDashboard.name.trim()) {
      message.warning('请输入仪表盘名称');
      return undefined;
    }
    return isPersistedDashboard(currentDashboard.id)
      ? saveDashboardVersion(currentDashboard.id, currentDashboard)
      : createDashboard(currentDashboard);
  };

  const saveDraft = async (): Promise<string | undefined> => {
    const currentDashboard = dashboardRef.current;
    if (isPersistedDashboard(currentDashboard.id) && !dirty) return currentDashboard.id;
    setDashboardSaving(true);
    try {
      const detail = await persistCurrentDraft();
      if (!detail) return undefined;
      applyServerDetail(detail);
      message.success(`草稿已保存为 V${detail.dashboard.currentVersionNo}`);
      return detail.dashboard.id;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存 Dashboard 草稿失败');
      return undefined;
    } finally {
      setDashboardSaving(false);
    }
  };

  const publish = async (): Promise<string | undefined> => {
    if (!canPublish || dashboardPublishing) return dashboardRef.current.id;
    setDashboardPublishing(true);
    try {
      let dashboardIdToPublish = dashboardRef.current.id;
      if (!isPersistedDashboard(dashboardIdToPublish) || dirty) {
        const draftDetail = await persistCurrentDraft();
        if (!draftDetail) return undefined;
        applyServerDetail(draftDetail);
        dashboardIdToPublish = draftDetail.dashboard.id;
      }
      const detail = await publishDashboard(dashboardIdToPublish);
      applyServerDetail(detail);
      message.success(`仪表盘已发布 V${detail.dashboard.publishedVersionNo}`);
      return detail.dashboard.id;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布 Dashboard 失败');
      return undefined;
    } finally {
      setDashboardPublishing(false);
    }
  };

  const restoreVersion = async (versionNo: number) => {
    const currentDashboard = dashboardRef.current;
    if (!isPersistedDashboard(currentDashboard.id)) return;
    setDashboardSaving(true);
    try {
      const detail = await restoreDashboardVersion(currentDashboard.id, versionNo);
      applyServerDetail(detail);
      message.success(`V${versionNo} 已恢复为草稿 V${detail.dashboard.currentVersionNo}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '恢复 DashboardVersion 失败');
    } finally {
      setDashboardSaving(false);
    }
  };

  return {
    dashboard,
    widgets,
    datasets,
    datasetsLoading,
    analyses,
    dashboardVersions,
    dashboardSaving,
    dashboardPublishing,
    runtimeFilterValues,
    selectedWidget,
    activeDataset,
    selectedId,
    preview,
    dirty,
    canUndo,
    canRedo,
    persisted,
    hasPublishedVersion,
    hasUnpublishedDraft,
    canPublish,
    setSelectedId,
    setPreview,
    updateDashboardName,
    updateLayout,
    updateWidget,
    updateInlineAnalysis,
    updateGlobalFilters,
    updateInteractions,
    setRuntimeFilterValue,
    resetRuntimeFilters,
    runtimeFiltersForWidget,
    handleWidgetSelection,
    addWidget,
    detachAnalysis,
    duplicateWidget,
    deleteWidget,
    changeWidgetDataset,
    undo,
    redo,
    saveDraft,
    publish,
    restoreVersion,
  };
}
