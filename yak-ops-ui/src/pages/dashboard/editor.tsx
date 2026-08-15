import { history, useParams } from '@umijs/max';
import { BRAND_CSS_VARIABLES } from '@/styles/brand';
import { Button, Modal } from 'antd';
import { BarChart3 } from 'lucide-react';
import ReactGridLayout, { useContainerWidth } from 'react-grid-layout';
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ChartEditor } from './chart-editor';
import { DashboardGlobalFilterBar } from './global-filter-bar';
import { DashboardGlobalFilterConfig } from './global-filter-config';
import { GRID_COLUMNS, GRID_ROW_HEIGHT } from './helpers';
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
  const designer = useDashboardDesigner(dashboardId);
  const { width, containerRef, mounted } = useContainerWidth();
  const [historyOpen, setHistoryOpen] = useState(false);
  const [filterConfigOpen, setFilterConfigOpen] = useState(false);
  const layout = useMemo(() => designer.widgets.map((widget) => ({
    i: widget.id,
    x: widget.x,
    y: widget.y,
    w: widget.w,
    h: widget.h,
    minW: widget.minW,
    minH: widget.minH,
  })), [designer.widgets]);
  const hasGlobalFilters = designer.dashboard.globalFilters.length > 0;
  const hasFilterBar = !designer.preview || hasGlobalFilters;
  let canvasMinHeight = 'min-h-[calc(100vh-120px)]';
  if (designer.preview) {
    canvasMinHeight = hasFilterBar
      ? 'min-h-[calc(100vh-172px)]'
      : 'min-h-[calc(100vh-136px)]';
  } else if (hasFilterBar) {
    canvasMinHeight = 'min-h-[calc(100vh-156px)]';
  }

  const addChart = () => designer.addWidget('bar');

  const saveDashboard = useCallback(async () => {
    const persisted = /^\d+$/.test(designer.dashboard.id);
    if (designer.dashboardSaving || designer.dashboardPublishing || (persisted && !designer.dirty)) return;
    const persistedId = await designer.saveDraft();
    if (!dashboardId && persistedId) history.replace(`/dashboard/${persistedId}`);
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
        if (!dashboardId && persistedId) history.replace(`/dashboard/${persistedId}`);
      },
    });
  }, [dashboardId, designer]);

  const leaveDashboard = () => {
    if (!designer.dirty) {
      history.push('/dashboard');
      return;
    }
    Modal.confirm({
      title: '有未保存修改',
      content: '当前仪表盘还有未保存到草稿的修改，离开后这些本地修改会丢失。',
      okText: '放弃修改并离开',
      cancelText: '继续编辑',
      okButtonProps: { danger: true },
      onOk: () => history.push('/dashboard'),
    });
  };

  const restoreVersion = (versionNo: number) => {
    const restore = async () => {
      await designer.restoreVersion(versionNo);
      setHistoryOpen(false);
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
      if (designer.preview) return;
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
  }, [designer, saveDashboard]);

  return (
    <div
      className="flex h-[calc(100vh-48px)] min-h-[640px] flex-col overflow-hidden bg-[#f4f6f8]"
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
        }}
        onSaveDraft={() => void saveDashboard()}
        onPublish={publishDashboard}
      />

      <DashboardGlobalFilterBar
        filters={designer.dashboard.globalFilters}
        runtimeValues={designer.runtimeFilterValues}
        widgets={designer.widgets}
        datasets={designer.datasets}
        analyses={designer.analyses}
        editable={!designer.preview}
        onRuntimeValue={designer.setRuntimeFilterValue}
        onReset={designer.resetRuntimeFilters}
        onManage={() => setFilterConfigOpen(true)}
      />

      <div className="flex min-h-0 flex-1 overflow-hidden">
        <main className="min-w-0 flex-1 overflow-auto">
          <div className={designer.preview ? 'min-h-full p-5' : 'min-h-full p-3'}>
            <div
              ref={containerRef}
              className={[
                'mx-auto min-w-[760px] bg-white',
                canvasMinHeight,
                designer.preview
                  ? 'max-w-[1500px] shadow-[0_1px_5px_rgba(16,24,40,.08)]'
                  : 'dashboard-grid-canvas border border-[#dfe3e8]',
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
                    margin: [8, 8],
                    containerPadding: [8, 8],
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
                    const spec = analysis ?? widget.inlineAnalysis;
                    const dataset = spec
                      ? designer.datasets.find((item) => item.id === spec.datasetId)
                      : undefined;

                    return (
                      <div key={widget.id}>
                        <WidgetShell
                          widget={widget}
                          analysis={analysis}
                          dataset={dataset}
                          runtimeFilters={designer.runtimeFiltersForWidget(widget.id)}
                          selected={designer.selectedId === widget.id}
                          preview={designer.preview}
                          onSelect={() => {
                            if (!designer.preview) designer.setSelectedId(widget.id);
                          }}
                          onDataSelect={(selection) =>
                            designer.handleWidgetSelection(widget.id, selection)}
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
                    <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-[8px] bg-[#f5f6f7] text-[#667085]">
                      <BarChart3 size={18} />
                    </div>
                    <div className="mt-3 text-[14px] font-medium text-[#344054]">
                      {designer.activeDataset ? '从一个图表开始' : '暂无可用数据集'}
                    </div>
                    <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">
                      {designer.activeDataset
                        ? '添加图表后，在右侧选择数据集、维度和指标即可完成配置。'
                        : '请先在数据开发发布中心发布并上线 Dataset。'}
                    </div>
                    {designer.activeDataset ? (
                      <Button
                        size="small"
                        type="primary"
                        className="mt-4"
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

        {!designer.preview && designer.selectedWidget ? (
          <ChartEditor
            widget={designer.selectedWidget}
            datasets={designer.datasets}
            analyses={designer.analyses}
            globalFilters={designer.dashboard.globalFilters}
            interactions={designer.dashboard.interactions}
            updateWidget={(patch) =>
              designer.updateWidget(designer.selectedWidget!.id, patch)}
            updateInlineAnalysis={(patch) =>
              designer.updateInlineAnalysis(designer.selectedWidget!.id, patch)}
            updateInteractions={designer.updateInteractions}
            changeDataset={(datasetId) =>
              designer.changeWidgetDataset(designer.selectedWidget!.id, datasetId)}
            detachAnalysis={() =>
              designer.detachAnalysis(designer.selectedWidget!.id)}
            close={() => designer.setSelectedId(undefined)}
          />
        ) : null}
      </div>

      <DashboardGlobalFilterConfig
        open={filterConfigOpen}
        filters={designer.dashboard.globalFilters}
        widgets={designer.widgets}
        datasets={designer.datasets}
        analyses={designer.analyses}
        onChange={designer.updateGlobalFilters}
        onClose={() => setFilterConfigOpen(false)}
      />

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
          background-color: #fff;
          background-image:
            linear-gradient(to right, rgba(15,23,42,.035) 1px, transparent 1px),
            linear-gradient(to bottom, rgba(15,23,42,.035) 1px, transparent 1px);
          background-size: calc(100% / 24) 36px;
        }
        .react-grid-item.react-grid-placeholder {
          background: var(--yak-brand-color-soft) !important;
          border: 1px dashed var(--yak-brand-color) !important;
          opacity: 1 !important;
        }
        .react-grid-item > .react-resizable-handle::after {
          border-color: #98a2b3 !important;
          border-width: 0 1px 1px 0 !important;
          height: 6px !important;
          width: 6px !important;
        }
        .chart-editor-more > .ant-collapse-item > .ant-collapse-header {
          padding: 10px 0 !important;
        }
        .chart-editor-more .ant-collapse-content-box {
          padding: 2px 0 0 !important;
        }
      `}</style>
    </div>
  );
}
