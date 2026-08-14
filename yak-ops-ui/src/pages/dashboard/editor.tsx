import { history, useParams } from '@umijs/max';
import { BRAND_CSS_VARIABLES } from '@/styles/brand';
import { Button } from 'antd';
import { BarChart3 } from 'lucide-react';
import ReactGridLayout, { useContainerWidth } from 'react-grid-layout';
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
import { useMemo } from 'react';
import { ChartEditor } from './chart-editor';
import { DashboardGlobalFilterBar } from './global-filter-bar';
import { GRID_COLUMNS, GRID_ROW_HEIGHT } from './helpers';
import { DashboardToolbar } from './toolbar';
import { useDashboardDesigner } from './use-dashboard';
import { WidgetShell } from './widget';

export default function DashboardEditorPage() {
  const { id } = useParams<{ id?: string }>();
  const dashboardId = id && id !== 'new' ? id : undefined;
  const designer = useDashboardDesigner(dashboardId);
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
  const hasGlobalFilters = designer.dashboard.globalFilters.length > 0;
  let canvasMinHeight = 'min-h-[calc(100vh-120px)]';
  if (designer.preview) {
    canvasMinHeight = hasGlobalFilters
      ? 'min-h-[calc(100vh-172px)]'
      : 'min-h-[calc(100vh-136px)]';
  } else if (hasGlobalFilters) {
    canvasMinHeight = 'min-h-[calc(100vh-156px)]';
  }

  const syncLayout = (
    nextLayout: readonly { i: string; x: number; y: number; w: number; h: number }[],
  ) => {
    const nextMap = new Map(nextLayout.map((item) => [item.i, item]));
    designer.setDashboard((current) => ({
      ...current,
      widgets: current.widgets.map((widget) => {
        const next = nextMap.get(widget.id);
        return next ? { ...widget, x: next.x, y: next.y, w: next.w, h: next.h } : widget;
      }),
    }));
  };

  const addChart = () => designer.addWidget('bar');

  const saveDashboard = async () => {
    const persistedId = await designer.save();
    if (!dashboardId && persistedId) history.replace(`/dashboard/${persistedId}`);
  };

  return (
    <div
      className="flex h-[calc(100vh-48px)] min-h-[640px] flex-col overflow-hidden bg-[#f4f6f8]"
      style={BRAND_CSS_VARIABLES}
    >
      <DashboardToolbar
        name={designer.dashboard.name}
        dashboardId={designer.dashboard.id}
        currentVersionNo={designer.dashboard.currentVersionNo}
        versions={designer.dashboardVersions}
        saving={designer.dashboardSaving}
        preview={designer.preview}
        canAddChart={Boolean(designer.activeDataset) && !designer.datasetsLoading}
        onName={(name) => designer.setDashboard((current) => ({ ...current, name }))}
        onAddChart={addChart}
        onVersion={(versionNo) => void designer.activateVersion(versionNo)}
        onPreview={() => {
          designer.setPreview((current) => !current);
          designer.setSelectedId(undefined);
        }}
        onSave={() => void saveDashboard()}
      />

      <DashboardGlobalFilterBar
        filters={designer.dashboard.globalFilters}
        runtimeValues={designer.runtimeFilterValues}
        onRuntimeValue={designer.setRuntimeFilterValue}
        onReset={designer.resetRuntimeFilters}
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
            updateWidget={(patch) =>
              designer.updateWidget(designer.selectedWidget!.id, patch)}
            updateInlineAnalysis={(patch) =>
              designer.updateInlineAnalysis(designer.selectedWidget!.id, patch)}
            changeDataset={(datasetId) => {
              designer.setDashboard((current) => ({ ...current, activeDatasetId: datasetId }));
              designer.changeWidgetDataset(designer.selectedWidget!.id, datasetId);
            }}
            detachAnalysis={() =>
              designer.detachAnalysis(designer.selectedWidget!.id)}
            close={() => designer.setSelectedId(undefined)}
          />
        ) : null}
      </div>

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
