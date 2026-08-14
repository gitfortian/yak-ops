import { message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { DEFAULT_DASHBOARD } from './defaults';
import {
  cloneDashboard,
  createWidget,
  defaultBindings,
  findDataset,
  loadDashboard,
  reconcileDashboard,
  STORAGE_KEY,
} from './helpers';
import type { ChartType, DashboardDocument, DashboardWidget, DatasetField, PublishedDataset } from './model';
import { fetchDashboardDatasets } from './service';

export function useDashboardDesigner() {
  const [dashboard, setDashboard] = useState<DashboardDocument>(loadDashboard);
  const [datasets, setDatasets] = useState<PublishedDataset[]>([]);
  const [datasetsLoading, setDatasetsLoading] = useState(true);
  const [datasetsError, setDatasetsError] = useState('');
  const [selectedId, setSelectedId] = useState<string>();
  const [preview, setPreview] = useState(false);
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

  useEffect(() => {
    void refreshDatasets();
  }, [refreshDatasets]);

  useEffect(() => {
    if (!datasets.length) return;
    setDashboard((current) => reconcileDashboard(current, datasets));
  }, [datasets]);

  const updateWidget = (id: string, patch: Partial<DashboardWidget>) => setDashboard((current) => ({
    ...current,
    widgets: current.widgets.map((widget) => widget.id === id ? { ...widget, ...patch } : widget),
  }));
  const maxY = () => widgets.reduce((value, widget) => Math.max(value, widget.y + widget.h), 0);
  const addWidget = (type: ChartType) => {
    if (!activeDataset) return void message.info('请先发布一个 ONLINE Dataset');
    const next = createWidget(type, activeDataset, maxY());
    setDashboard((current) => ({ ...current, widgets: [...current.widgets, next] }));
    setSelectedId(next.id);
  };
  const duplicateWidget = (id: string) => {
    const source = widgets.find((widget) => widget.id === id);
    if (!source) return;
    const next: DashboardWidget = {
      ...source,
      id: `${source.type}-${Date.now()}-${Math.round(Math.random() * 1000)}`,
      y: maxY(),
      dimensions: [...source.dimensions],
      metrics: source.metrics.map((metric) => ({ ...metric })),
      filters: source.filters.map((filter) => ({ ...filter, id: `${filter.id}-${Date.now()}` })),
      style: { ...source.style },
      sort: source.sort ? { ...source.sort } : undefined,
    };
    setDashboard((current) => ({ ...current, widgets: [...current.widgets, next] }));
    setSelectedId(next.id);
  };
  const deleteWidget = (id: string) => {
    setDashboard((current) => ({ ...current, widgets: current.widgets.filter((widget) => widget.id !== id) }));
    setSelectedId((current) => current === id ? undefined : current);
  };
  const changeWidgetDataset = (id: string, datasetId: string) => {
    const dataset = findDataset(datasets, datasetId);
    if (!dataset) return;
    const bindings = defaultBindings(dataset);
    const widget = widgets.find((item) => item.id === id);
    updateWidget(id, {
      datasetId,
      dimensions: widget?.type === 'metric' ? [] : bindings.dimensions,
      metrics: bindings.metrics,
      filters: [],
      sort: undefined,
    });
  };
  const addField = (field: DatasetField) => {
    if (!selectedWidget) return void message.info('请先选择一个图表组件');
    if (!activeDataset) return void message.info('当前没有可用 Dataset');
    if (selectedWidget.datasetId !== activeDataset.id) {
      changeWidgetDataset(selectedWidget.id, activeDataset.id);
      return void message.info('已切换图表数据集，请再次添加字段');
    }
    if (field.role === 'dimension') {
      if (selectedWidget.type === 'metric') return void message.info('指标卡不需要维度');
      const limit = selectedWidget.type === 'table' ? 3 : 1;
      if (!selectedWidget.dimensions.includes(field.key)) {
        updateWidget(selectedWidget.id, { dimensions: [...selectedWidget.dimensions, field.key].slice(0, limit) });
      }
      return;
    }
    const limit = ['table', 'line', 'bar'].includes(selectedWidget.type) ? 3 : 1;
    if (!selectedWidget.metrics.some((metric) => metric.field === field.key)) {
      updateWidget(selectedWidget.id, {
        metrics: [...selectedWidget.metrics, { field: field.key, aggregation: 'SUM' }].slice(0, limit),
      });
    }
  };
  const save = () => {
    const next = { ...dashboard, updatedAt: new Date().toISOString() };
    setDashboard(next);
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    message.success('仪表盘草稿已保存');
  };
  const reset = () => {
    const next = reconcileDashboard(cloneDashboard(DEFAULT_DASHBOARD), datasets);
    setDashboard(next);
    setSelectedId(undefined);
    window.localStorage.removeItem(STORAGE_KEY);
    message.success('已恢复空白仪表盘');
  };
  return {
    dashboard,
    widgets,
    datasets,
    datasetsLoading,
    datasetsError,
    selectedWidget,
    activeDataset,
    selectedId,
    preview,
    setDashboard,
    setSelectedId,
    setPreview,
    updateWidget,
    addWidget,
    duplicateWidget,
    deleteWidget,
    changeWidgetDataset,
    addField,
    save,
    reset,
    refreshDatasets,
  };
}
