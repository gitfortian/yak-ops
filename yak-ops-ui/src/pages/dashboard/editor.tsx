import { history, useParams } from '@umijs/max';
import { BRAND_CSS_VARIABLES } from '@/styles/brand';
import { Button, Modal } from 'antd';
import { BarChart3 } from 'lucide-react';
import ReactGridLayout, { useContainerWidth } from 'react-grid-layout';
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { DashboardChartSheetWorkspace } from './chart-sheet-workspace';
import { DashboardGlobalFilterBar } from './global-filter-bar';
import { GRID_COLUMNS, GRID_ROW_HEIGHT } from './helpers';
import {
  directCrossFiltersForWidget,
  pruneRuntimeSelections,
  sameDashboardSelection,
  type DashboardRuntimeSelections,
} from './interaction-runtime';
import type { AnalysisSelection } from './model';
import { DashboardSheetBar } from './sheet-bar';
import { DashboardToolbar } from './toolbar';
import { useDashboardDesigner } from './use-dashboard';
import { DashboardVersionHistoryDrawer } from './version-history-drawer';
import { WidgetShell } from './widget';

const isEditableTarget = (target: EventTarget | null) => {
  if (!(target instanceof HTMLElement)) return false;
  return target.isContentEditable
    || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)
    || Boolean(target.closest('.monaco-editor'));
};

export default function DashboardEditorPage() {
  const { id } = useParams<{ id?: string }>();
  const dashboardId = id && id !== 'new' ? id : undefined;
  const initialPreview = new URLSearchParams(window.location.search).get('preview') === '1';
  const designer = useDashboardDesigner(dashboardId, initialPreview, false);
  const { width, containerRef, mounted, measureWidth } = useContainerWidth();
  const [historyOpen, setHistoryOpen] = useState(false);
  const [activeSheet, setActiveSheet] = useState<'dashboard' | 'chart'>('dashboard');
  const [activeSheetId, setActiveSheetId] = useState<string>();
  const [sheetOrder, setSheetOrder] = useState<string[]>([]);
  const [runtimeSelections, setRuntimeSelections] = useState<DashboardRuntimeSelections>({});
  const layout = useMemo(() => designer.widgets.map((widget) => ({
    i: widget.id,
    x: widget.x,
    y: widget.y,
    w: widget.w,
    h: widget.h,
    minW: widget.minW,
    minH: widget.minH,
  })), [designer.widgets]);
  const sheets = useMemo(() => sheetOrder.flatMap((widgetId) => {
    const widget = designer.widgets.find((item) => item.id === widgetId);
    if (!widget) return [];
    const analysis = widget.analysisId
      ? designer.analyses.find((item) => item.id === widget.analysisId)
      : undefined;
    return [{
      id: widget.id,
      title: widget.title?.trim()
        || (widget.analysisId ? analysis?.name ?? '历史图表' : '未命名图表'),
    }];
  }), [designer.analyses, designer.widgets, sheetOrder]);
  const hasGlobalFilters = designer.dashboard.globalFilters.length > 0;
  const showRuntimeFilterBar = designer.preview && hasGlobalFilters;
  let canvasMinHeight = 'min-h-[calc(100vh-128px)] 2xl:min-h-[calc(100vh-96px)]';
  if (designer.preview) {
    canvasMinHeight = showRuntimeFilterBar
      ? 'min-h-[calc(100vh-140px)]'
      : 'min-h-[calc(100vh-96px)]';
  }

  useEffect(() => {
    setSheetOrder(designer.widgets.map((widget) => widget.id));
    setActiveSheet('dashboard');
    setActiveSheetId(undefined);
    setRuntimeSelections({});
  }, [designer.dashboard.id]);

  useEffect(() => {
    setRuntimeSelections({});
  }, [designer.dashboard.currentVersionId]);

  useEffect(() => {
    const widgetIds = designer.widgets.map((widget) => widget.id);
    setSheetOrder((current) => {
      const retained = current.filter((widgetId) => widgetIds.includes(widgetId));
      const added = widgetIds.filter((widgetId) => !retained.includes(widgetId));
      const next = [...retained, ...added];
      return next.length === current.length && next.every((item, index) => item === current[index])
        ? current
        : next;
    });
    setRuntimeSelections((current) => pruneRuntimeSelections(designer.widgets, current));
  }, [designer.widgets]);

  useEffect(() => {
    if (designer.preview) return;
    if (designer.selectedId) {
      setActiveSheet('chart');
      setActiveSheetId(designer.selectedId);
      return;
    }
    setActiveSheet('dashboard');
    setActiveSheetId(undefined);
  }, [designer.preview, designer.selectedId]);

  useEffect(() => {
    if (designer.preview || activeSheet !== 'dashboard') return undefined;
    const frame = window.requestAnimationFrame(() => {
      measureWidth();
    });
    return () => window.cancelAnimationFrame(frame);
  }, [activeSheet, designer.preview, measureWidth]);

  const activateDashboardSheet = () => {
    setActiveSheet('dashboard');
    setActiveSheetId(undefined);
    designer.setSelectedId(undefined);
  };

  const activateChartSheet = (widgetId: string) => {
    setActiveSheet('chart');
    setActiveSheetId(widgetId);
    designer.setSelectedId(widgetId);
  };

  const addChart = () => designer.addWidget('bar');

  const clearWidgetSelection = useCallback((widgetId: string) => {
    const nextSelections = { ...runtimeSelections };
    delete nextSelections[widgetId];
    setRuntimeSelections(nextSelections);

    const targetFilterIds = new Set(designer.dashboard.interactions
      .filter((interaction) => interaction.sourceWidgetId === widgetId)
      .map((interaction) => interaction.targetFilterId));
    targetFilterIds.forEach((filterId) => {
      const replacement = designer.dashboard.interactions.find((interaction) => {
        if (interaction.targetFilterId !== filterId || interaction.sourceWidgetId === widgetId) return false;
        const selection = nextSelections[interaction.sourceWidgetId];
        return selection?.fieldId === interaction.sourceField;
      });
      const replacementSelection = replacement
        ? nextSelections[replacement.sourceWidgetId]
        : undefined;
      const defaultValue = designer.dashboard.globalFilters.find((filter) => filter.id === filterId)?.defaultValue;
      designer.setRuntimeFilterValue(filterId, replacementSelection?.value ?? defaultValue);
    });
  }, [designer, runtimeSelections]);

  const handleRuntimeSelection = useCallback((widgetId: string, selection: AnalysisSelection) => {
    const current = runtimeSelections[widgetId];
    if (sameDashboardSelection(current, selection)) {
      clearWidgetSelection(widgetId);
      return;
    }

    const widget = designer.widgets.find((item) => item.id === widgetId);
    const behavior = widget?.inlineAnalysis?.dashboardBehavior;
    const hasDirectLink = Boolean(behavior?.crossFilters?.some((rule) => rule.sourceField === selection.fieldId));
    const hasGlobalLink = designer.dashboard.interactions.some((interaction) => (
      interaction.sourceWidgetId === widgetId && interaction.sourceField === selection.fieldId
    ));
    const hasClickAction = Boolean(behavior?.clickAction && behavior.clickAction !== 'none');
    if (hasDirectLink || hasGlobalLink || hasClickAction) {
      setRuntimeSelections((items) => ({ ...items, [widgetId]: selection }));
    }

    const target = designer.handleWidgetSelection(widgetId, selection);
    if (target) history.push(target);
  }, [clearWidgetSelection, designer, runtimeSelections]);

  const resetRuntimeInteractions = useCallback(() => {
    setRuntimeSelections({});
    designer.resetRuntimeFilters();
  }, [designer]);

  const saveDashboard = useCallback(async () => {
    const persisted = /^\d+$/.test(designer.dashboard.id);
    if (designer.dashboardSaving || designer.dashboardPublishing || (persisted && !designer.dirty)) return;
    const persistedId = await designer.saveDraft();
    if (!dashboardId && persistedId) history.replace(`/dashboard/${persistedId}/edit`);
  }, [dashboardId, designer]);

  const publishDashboard = useCallback(() => {
    if (!designer.canPublish || designer.dashboardSaving || designer.dashboardPublishing) return;
    const firstPublish = !designer.hasPublishedVersion;
    Modal.confirm({
      title: firstPublish ? '发布仪表盘？' : '发布当前草稿？',
      content: designer.dirty
        ? '当前还有未保存修改。系统会先保存为新的草稿版本，再将该版本发布。'
        : firstPublish
          ? `草稿 V${designer.dashboard.currentVersionNo || 1} 将成为首个正式发布版本。`
          : `草稿 V${designer.dashboard.currentVersionNo} 将替换当前已发布的 V${designer.dashboard.publishedVersionNo}。`,
      okText: firstPublish ? '发布' : '发布更新',
      cancelText: '取消',
      onOk: async () => {
        const persistedId = await designer.publish();
        if (!dashboardId && persistedId) history.replace(`/dashboard/${persistedId}/edit`);
      },
    });
  }, [dashboardId, designer]);

  const leaveDashboard = () => {
    const target = '/dashboard';
    if (!designer.dirty) {
      history.push(target);
      return;
    }
    Modal.confirm({
      title: '有未保存修改',
      content: '当前仪表盘还有未保存到草稿的修改，离开后这些本地修改会丢失。',
      okText: '放弃修改并离开',
      cancelText: '继续编辑',
      okButtonProps: { danger: true },
      onOk: () => history.push(target),
    });
  };

  const restoreVersion = (versionNo: number) => {
    const restore = async () => {
      await designer.restoreVersion(versionNo);
      setHistoryOpen(false);
      setRuntimeSelections({});
    };
    if (!designer.dirty) {
      void restore();
      return;
    }
    Modal.confirm({
      title: `恢复 V${versionNo} 为草稿？`,
      content: '当前还有未保存到草稿的修改。继续恢复会放弃这些本地修改，并基于历史版本生成一个新的草稿版本。',
      okText: '放弃修改并恢复',
      cancelText: '继续编辑',
      onOk: restore,
    });
  };

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (designer.preview) {
        if (event.key === 'Escape' && Object.keys(runtimeSelections).length) {
          event.preventDefault();
          resetRuntimeInteractions();
        }
        return;
      }
      const key = event.key.toLowerCase();
      const modifier = event.metaKey || event.ctrlKey;

      if (modifier && key === 'z') {
        event.preventDefault();
        if (event.shiftKey) designer.redo();
        else designer.undo();
        return;
      }

      if (modifier && key === 'y') {
        event.preventDefault();
        designer.redo();
        return;
      }

      if (modifier && key === 's') {
        event.preventDefault();
        void saveDashboard();
        return;
      }

      if (
        modifier
        && key === 'd'
        && designer.selectedId
        && !isEditableTarget(event.target)
      ) {
        event.preventDefault();
        designer.duplicateWidget(designer.selectedId);
        return;
      }

      if (event.key === 'Escape' && designer.selectedId) {
        designer.setSelectedId(undefined);
        return;
      }

      if (
        (event.key === 'Delete' || event.key === 'Backspace')
        && designer.selectedId
        && !isEditableTarget(event.target)
      ) {
        event.preventDefault();
        designer.deleteWidget(designer.selectedId);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [designer, resetRuntimeInteractions, runtimeSelections, saveDashboard]);

  const showDashboardWorkspace = designer.preview || activeSheet === 'dashboard';

  return (
    <div
      className="flex h-screen min-h-[640px] flex-col overflow-hidden bg-[#f3f4f6]"
      style={BRAND_CSS_VARIABLES}
    >
      <DashboardToolbar
        name={designer.dashboard.name}
        dashboardId={designer.dashboard.id}
        currentVersionNo={designer.dashboard.currentVersionNo}
        publishedVersionNo={designer.dashboard.publishedVersionNo}
        saving={designer.dashboardSaving}
        publishing={designer.dashboardPublishing}
        preview={designer.preview}
        dirty={designer.dirty}
        canUndo={designer.canUndo}
        canRedo={designer.canRedo}
        canAddChart={Boolean(designer.activeDataset) && !designer.datasetsLoading}
        canPublish={designer.canPublish}
        hasPublishedVersion={designer.hasPublishedVersion}
        hasUnpublishedDraft={designer.hasUnpublishedDraft}
        onBack={leaveDashboard}
        onName={designer.updateDashboardName}
        onUndo={designer.undo}
        onRedo={designer.redo}
        onAddChart={addChart}
        onHistory={() => setHistoryOpen(true)}
        onPreview={() => {
          designer.setPreview((current) => !current);
          designer.setSelectedId(undefined);
          setRuntimeSelections({});
        }}
        onSaveDraft={() => void saveDashboard()}
        onPublish={publishDashboard}
      />

      {showRuntimeFilterBar ? (
        <DashboardGlobalFilterBar
          filters={designer.dashboard.globalFilters}
          runtimeValues={designer.runtimeFilterValues}
          widgets={designer.widgets}
          datasets={designer.datasets}
          analyses={designer.analyses}
          editable={false}
          onRuntimeValue={designer.setRuntimeFilterValue}
          onReset={resetRuntimeInteractions}
          onManage={() => undefined}
        />
      ) : null}

      <div className="flex min-h-0 flex-1 overflow-hidden">
        {showDashboardWorkspace ? (
          <main className="min-w-0 flex-1 overflow-auto bg-[#f3f4f6]">
            <div className={designer.preview ? 'min-h-full p-5' : 'min-h-full p-4 2xl:p-0'}>
              <div
                ref={containerRef}
                className={[
                  'min-w-[760px]',
                  canvasMinHeight,
                  designer.preview
                    ? 'mx-auto max-w-[1480px] rounded-[10px] border border-[#e7e9ed] bg-white shadow-[0_6px_24px_rgba(16,24,40,.055)]'
                    : 'dashboard-grid-canvas mx-auto max-w-[1540px] rounded-[10px] 2xl:mx-0 2xl:max-w-none 2xl:rounded-none',
                ].join(' ')}
                onMouseDown={(event) => {
                  if (event.target === event.currentTarget) designer.setSelectedId(undefined);
                }}
              >
                {mounted && width > 0 ? (
                  <ReactGridLayout
                    width={width}
                    layout={layout}
                    gridConfig={{
                      cols: GRID_COLUMNS,
                      rowHeight: GRID_ROW_HEIGHT,
                      margin: [10, 10],
                      containerPadding: [10, 10],
                    }}
                    dragConfig={{
                      enabled: !designer.preview,
                      handle: '.dashboard-widget__drag-handle',
                    }}
                    resizeConfig={{ enabled: !designer.preview }}
                    onLayoutChange={designer.updateLayout}
                  >
                    {designer.widgets.map((widget) => {
                      const analysis = widget.analysisId
                        ? designer.analyses.find((item) => item.id === widget.analysisId)
                        : undefined;
                      const runtimeSpec = designer.runtimeSpecForWidget(widget.id);
                      const dataset = runtimeSpec
                        ? designer.datasets.find((item) => item.id === runtimeSpec.datasetId)
                        : undefined;
                      const drillPath = designer.drillPathForWidget(widget.id);
                      const directFilters = directCrossFiltersForWidget(
                        designer.widgets,
                        runtimeSelections,
                        widget.id,
                      );

                      return (
                        <div key={widget.id} id={`dashboard-widget-${widget.id}`}>
                          <WidgetShell
                            widget={widget}
                            analysis={analysis}
                            runtimeSpec={runtimeSpec}
                            dataset={dataset}
                            runtimeFilters={[
                              ...designer.runtimeFiltersForWidget(widget.id),
                              ...directFilters,
                            ]}
                            drillPath={drillPath}
                            activeSelection={runtimeSelections[widget.id]}
                            selected={designer.selectedId === widget.id}
                            preview={designer.preview}
                            onSelect={() => {
                              if (!designer.preview) activateChartSheet(widget.id);
                            }}
                            onDataSelect={(selection) => {
                              if (!designer.preview) return;
                              handleRuntimeSelection(widget.id, selection);
                            }}
                            onClearSelection={() => clearWidgetSelection(widget.id)}
                            onDrillBack={(depth) => {
                              clearWidgetSelection(widget.id);
                              designer.drillBack(widget.id, depth);
                            }}
                            onDuplicate={() => designer.duplicateWidget(widget.id)}
                            onDelete={() => designer.deleteWidget(widget.id)}
                          />
                        </div>
                      );
                    })}
                  </ReactGridLayout>
                ) : null}

                {!designer.widgets.length && !designer.datasetsLoading ? (
                  <div className="flex min-h-[420px] items-center justify-center px-6 text-center">
                    <div className="max-w-[340px]">
                      <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-[10px] bg-white text-[#7a818c] shadow-[0_1px_2px_rgba(16,24,40,.04)]">
                        <BarChart3 size={18} />
                      </div>
                      <div className="mt-3 text-[14px] font-semibold text-[#344054]">
                        {designer.activeDataset ? '从一个图表开始' : '暂无可用数据集'}
                      </div>
                      <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">
                        {designer.activeDataset
                          ? '添加图表后，进入对应图表 Sheet 完成数据与样式配置。'
                          : '请先在数据开发发布中心发布并上线 Dataset。'}
                      </div>
                      {designer.activeDataset ? (
                        <Button
                          size="small"
                          className="mt-4 !h-8 !rounded-[7px] !px-3"
                          icon={<BarChart3 size={13} />}
                          onClick={addChart}
                        >
                          添加图表
                        </Button>
                      ) : null}
                    </div>
                  </div>
                ) : null}
              </div>
            </div>
          </main>
        ) : designer.selectedWidget ? (
          <DashboardChartSheetWorkspace
            currentDashboardId={designer.dashboard.id}
            widget={designer.selectedWidget}
            widgets={designer.widgets}
            datasets={designer.datasets}
            analyses={designer.analyses}
            globalFilters={designer.dashboard.globalFilters}
            interactions={designer.dashboard.interactions}
            runtimeFilters={designer.runtimeFiltersForWidget(designer.selectedWidget.id)}
            updateWidget={(patch) =>
              designer.updateWidget(designer.selectedWidget!.id, patch)}
            updateInlineAnalysis={(patch) =>
              designer.updateInlineAnalysis(designer.selectedWidget!.id, patch)}
            updateInteractions={designer.updateInteractions}
            changeDataset={(datasetId) =>
              designer.changeWidgetDataset(designer.selectedWidget!.id, datasetId)}
            detachAnalysis={() =>
              designer.detachAnalysis(designer.selectedWidget!.id)}
            onDone={activateDashboardSheet}
          />
        ) : null}
      </div>

      {!designer.preview ? (
        <DashboardSheetBar
          dashboardKey={designer.dashboard.id}
          sheets={sheets}
          activeSheet={activeSheet}
          activeSheetId={activeSheetId}
          canAddChart={Boolean(designer.activeDataset) && !designer.datasetsLoading}
          onDashboard={activateDashboardSheet}
          onChart={activateChartSheet}
          onReorder={setSheetOrder}
          onAddChart={addChart}
          onRename={(sheetId, title) => designer.updateWidget(sheetId, { title })}
          onDuplicate={designer.duplicateWidget}
          onDelete={designer.deleteWidget}
        />
      ) : null}

      {designer.persisted ? (
        <DashboardVersionHistoryDrawer
          open={historyOpen}
          dashboardId={designer.dashboard.id}
          versions={designer.dashboardVersions}
          currentVersionNo={designer.dashboard.currentVersionNo}
          publishedVersionNo={designer.dashboard.publishedVersionNo}
          busy={designer.dashboardSaving || designer.dashboardPublishing}
          onClose={() => setHistoryOpen(false)}
          onRestore={restoreVersion}
        />
      ) : null}

      <style>{`
        .dashboard-grid-canvas {
          background-color: #f3f4f6;
          background-image: radial-gradient(circle, rgba(15, 23, 42, .035) 1px, transparent 1px);
          background-size: calc(100% / 24) 36px;
          background-position: 10px 10px;
        }
        .react-grid-item.react-grid-placeholder {
          background: var(--yak-brand-color-soft) !important;
          border: 1px dashed var(--yak-brand-color) !important;
          border-radius: 9px !important;
          opacity: 1 !important;
        }
        .react-grid-item > .react-resizable-handle::after {
          border-color: #8e95a0 !important;
          border-width: 0 1px 1px 0 !important;
          height: 6px !important;
          width: 6px !important;
        }
        .chart-editor-more > .ant-collapse-item {
          border-bottom: 1px solid #eceef1 !important;
        }
        .chart-editor-more > .ant-collapse-item > .ant-collapse-header {
          padding: 12px 0 !important;
        }
        .chart-editor-more .ant-collapse-content-box {
          padding: 2px 0 10px !important;
        }
      `}</style>
    </div>
  );
}
