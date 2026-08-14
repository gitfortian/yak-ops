import { BRAND_CSS_VARIABLES } from '@/styles/brand';
import ReactGridLayout, { useContainerWidth } from 'react-grid-layout';
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
import { useMemo } from 'react';
import { GRID_COLUMNS, GRID_ROW_HEIGHT } from './helpers';
import { LeftPanel } from './left-panel';
import { SelectedConfig } from './selected-config';
import { DashboardToolbar } from './toolbar';
import { useDashboardDesigner } from './use-dashboard';
import { WidgetShell } from './widget';

export default function DashboardPage() {
  const designer = useDashboardDesigner();
  const { width, containerRef, mounted } = useContainerWidth();
  const layout = useMemo(() => designer.widgets.map((widget) => ({
    i: widget.id,
    x: widget.x,
    y: widget.y,
    w: widget.w,
    h: widget.h,
    minW: widget.minW,
    minH: widget.minH,
  })), [designer.widgets]);

  const syncLayout = (nextLayout: readonly { i: string; x: number; y: number; w: number; h: number }[]) => {
    const nextMap = new Map(nextLayout.map((item) => [item.i, item]));
    designer.setDashboard((current) => ({
      ...current,
      widgets: current.widgets.map((widget) => {
        const next = nextMap.get(widget.id);
        return next ? { ...widget, x: next.x, y: next.y, w: next.w, h: next.h } : widget;
      }),
    }));
  };

  return (
    <div className="flex h-[calc(100vh-48px)] min-h-[640px] flex-col overflow-hidden bg-[#f4f6f8]" style={BRAND_CSS_VARIABLES}>
      <DashboardToolbar
        name={designer.dashboard.name}
        dashboardId={designer.dashboard.id}
        currentVersionNo={designer.dashboard.currentVersionNo}
        dashboards={designer.dashboardAssets}
        versions={designer.dashboardVersions}
        loading={designer.dashboardsLoading}
        saving={designer.dashboardSaving}
        preview={designer.preview}
        onName={(name) => designer.setDashboard((current) => ({ ...current, name }))}
        onDashboard={(dashboardId) => void designer.openDashboard(dashboardId)}
        onNew={designer.newDashboard}
        onVersion={(versionNo) => void designer.activateVersion(versionNo)}
        onPreview={() => { designer.setPreview((current) => !current); designer.setSelectedId(undefined); }}
        onSave={() => void designer.save()}
      />

      <div className="flex min-h-0 flex-1 overflow-hidden">
        {!designer.preview ? (
          <LeftPanel
            datasets={designer.datasets}
            activeDataset={designer.activeDataset}
            datasetsLoading={designer.datasetsLoading}
            datasetsError={designer.datasetsError}
            analyses={designer.analyses}
            analysesLoading={designer.analysesLoading}
            analysesError={designer.analysesError}
            selectedWidget={designer.selectedWidget}
            onDatasetChange={(activeDatasetId) => designer.setDashboard((current) => ({ ...current, activeDatasetId }))}
            onRefreshDatasets={() => void designer.refreshDatasets()}
            onRefreshAnalyses={() => void designer.refreshAnalyses()}
            onAddChart={designer.addWidget}
            onAddAnalysis={designer.addAnalysis}
            onAddField={designer.addField}
          />
        ) : null}

        <main className="min-w-0 flex-1 overflow-auto">
          <div className={designer.preview ? 'min-h-full p-5' : 'min-h-full p-3'}>
            <div
              ref={containerRef}
              className={[
                'mx-auto min-h-[calc(100vh-92px)] min-w-[760px] bg-white',
                designer.preview ? 'max-w-[1500px] shadow-[0_1px_5px_rgba(16,24,40,.08)]' : 'dashboard-grid-canvas border border-[#dfe3e8]',
              ].join(' ')}
              onMouseDown={(event) => { if (event.target === event.currentTarget) designer.setSelectedId(undefined); }}
            >
              {mounted && width > 0 ? (
                <ReactGridLayout
                  width={width}
                  layout={layout}
                  gridConfig={{ cols: GRID_COLUMNS, rowHeight: GRID_ROW_HEIGHT, margin: [8, 8], containerPadding: [8, 8] }}
                  dragConfig={{ enabled: !designer.preview, handle: '.dashboard-widget__drag-handle' }}
                  resizeConfig={{ enabled: !designer.preview }}
                  onLayoutChange={syncLayout}
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
                          selected={designer.selectedId === widget.id}
                          preview={designer.preview}
                          onSelect={() => { if (!designer.preview) designer.setSelectedId(widget.id); }}
                          onSaveAsAnalysis={() => void designer.saveWidgetAsAnalysis(widget.id)}
                          onDuplicate={() => designer.duplicateWidget(widget.id)}
                          onDelete={() => designer.deleteWidget(widget.id)}
                        />
                      </div>
                    );
                  })}
                </ReactGridLayout>
              ) : null}
              {!designer.widgets.length && !designer.datasetsLoading ? (
                <div className="flex min-h-[360px] items-center justify-center px-6 text-center text-[12px] text-[#98a2b3]">
                  {designer.activeDataset
                    ? '从左侧复用 Analysis，或新建图表开始分析；点击保存后会生成服务端 DashboardVersion'
                    : '暂无可用 Dataset，请先在数据开发发布中心发布数据集'}
                </div>
              ) : null}
            </div>
          </div>
        </main>

        {!designer.preview && designer.selectedWidget ? (
          <SelectedConfig
            widget={designer.selectedWidget}
            datasets={designer.datasets}
            analyses={designer.analyses}
            updateWidget={(patch) => designer.updateWidget(designer.selectedWidget!.id, patch)}
            updateInlineAnalysis={(patch) => designer.updateInlineAnalysis(designer.selectedWidget!.id, patch)}
            changeDataset={(datasetId) => designer.changeWidgetDataset(designer.selectedWidget!.id, datasetId)}
            detachAnalysis={() => designer.detachAnalysis(designer.selectedWidget!.id)}
            close={() => designer.setSelectedId(undefined)}
          />
        ) : null}
      </div>

      <style>{`
        .dashboard-grid-canvas { background-color: #fff; background-image: linear-gradient(to right, rgba(15,23,42,.035) 1px, transparent 1px), linear-gradient(to bottom, rgba(15,23,42,.035) 1px, transparent 1px); background-size: calc(100% / 24) 36px; }
        .react-grid-item.react-grid-placeholder { background: var(--yak-brand-color-soft) !important; border: 1px dashed var(--yak-brand-color) !important; opacity: 1 !important; }
        .react-grid-item > .react-resizable-handle::after { border-color: #98a2b3 !important; border-width: 0 1px 1px 0 !important; height: 6px !important; width: 6px !important; }
        .dashboard-config-tabs > .ant-tabs-nav { margin: 0 !important; padding: 0 12px; }
        .dashboard-config-tabs .ant-tabs-tab { padding: 9px 0 !important; font-size: 12px; }
      `}</style>
    </div>
  );
}
