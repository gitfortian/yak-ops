import { history, useParams } from '@umijs/max';
import { Button } from 'antd';
import { BarChart3, Pencil, RefreshCw } from 'lucide-react';
import ReactGridLayout, { useContainerWidth } from 'react-grid-layout';
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchDashboard } from './dashboard-service';
import { DashboardGlobalFilterBar } from './global-filter-bar';
import { GRID_COLUMNS, GRID_ROW_HEIGHT } from './helpers';
import type { DashboardSummary } from './model';
import { useDashboardDesigner } from './use-dashboard';
import { WidgetShell } from './widget';

export default function DashboardViewerPage() {
  const { id } = useParams<{ id?: string }>();
  const dashboardId = id;
  const [summary, setSummary] = useState<DashboardSummary>();
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [summaryError, setSummaryError] = useState<string>();
  const published = Boolean(summary && Number(summary.publishedVersionNo) > 0);
  const designer = useDashboardDesigner(published ? dashboardId : undefined, true, true);
  const { width, containerRef, mounted } = useContainerWidth();

  const loadSummary = useCallback(async () => {
    if (!dashboardId) return;
    setSummaryLoading(true);
    setSummaryError(undefined);
    try {
      const detail = await fetchDashboard(dashboardId);
      setSummary(detail.dashboard);
    } catch (error) {
      setSummary(undefined);
      setSummaryError(error instanceof Error ? error.message : '加载仪表盘失败');
    } finally {
      setSummaryLoading(false);
    }
  }, [dashboardId]);

  useEffect(() => {
    void loadSummary();
  }, [loadSummary]);

  const layout = useMemo(() => designer.widgets.map((widget) => ({
    i: widget.id,
    x: widget.x,
    y: widget.y,
    w: widget.w,
    h: widget.h,
    minW: widget.minW,
    minH: widget.minH,
  })), [designer.widgets]);

  const openEditor = () => {
    if (dashboardId) history.push(`/dashboard/${dashboardId}/edit`);
  };

  if (!dashboardId) return null;

  if (summaryLoading) {
    return (
      <div className="flex min-h-[calc(100vh-80px)] items-center justify-center bg-white text-[12px] text-[#98a2b3]">
        正在加载仪表盘...
      </div>
    );
  }

  if (summaryError) {
    return (
      <div className="flex min-h-[calc(100vh-80px)] items-center justify-center bg-white px-6 text-center">
        <div>
          <div className="text-[14px] font-semibold text-[#344054]">仪表盘加载失败</div>
          <div className="mt-1 text-[11px] text-[#98a2b3]">{summaryError}</div>
          <Button
            size="small"
            className="mt-4"
            icon={<RefreshCw size={13} />}
            onClick={() => void loadSummary()}
          >
            重新加载
          </Button>
        </div>
      </div>
    );
  }

  if (!published) {
    return (
      <div className="flex min-h-[calc(100vh-80px)] items-center justify-center bg-white px-6 text-center">
        <div className="max-w-[360px]">
          <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-[10px] bg-[#f4f5f6] text-[#7a818c]">
            <BarChart3 size={18} />
          </div>
          <div className="mt-3 text-[14px] font-semibold text-[#344054]">这个仪表盘还没有发布版本</div>
          <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">
            查看页只展示稳定的 Published Snapshot。进入编辑器完成配置并发布后即可在这里查看。
          </div>
          <Button
            type="primary"
            size="small"
            className="mt-4"
            icon={<Pencil size={13} />}
            onClick={openEditor}
          >
            进入编辑器
          </Button>
        </div>
      </div>
    );
  }

  const publishedReady = designer.dashboard.id === dashboardId;
  const viewerName = publishedReady
    ? designer.dashboard.name
    : summary?.name || '仪表盘';
  const viewerDescription = publishedReady
    ? designer.dashboard.description
    : summary?.description;
  const viewerVersionNo = publishedReady
    ? designer.dashboard.publishedVersionNo
    : summary?.publishedVersionNo;

  return (
    <div className="flex min-h-[calc(100vh-80px)] flex-col overflow-hidden bg-white">
      <div className="flex h-14 shrink-0 items-center justify-between border-b border-[#eceef1] px-5">
        <div className="min-w-0">
          <div className="flex min-w-0 items-center gap-2">
            <h1 className="m-0 max-w-[720px] truncate text-[15px] font-semibold text-[#161823]">
              {viewerName}
            </h1>
            <span className="shrink-0 rounded-[5px] bg-[#f4f5f6] px-2 py-0.5 text-[10px] text-[#667085]">
              已发布 V{viewerVersionNo}
            </span>
            {summary?.currentVersionId !== summary?.publishedVersionId ? (
              <span className="shrink-0 text-[10px] text-[#98a2b3]">有未发布草稿</span>
            ) : null}
          </div>
          <div className="mt-0.5 max-w-[760px] truncate text-[10px] text-[#98a2b3]">
            {viewerDescription || '查看当前已发布版本'}
          </div>
        </div>

        <Button
          type="primary"
          size="small"
          className="!h-8 !rounded-[7px] !px-3.5 !shadow-none"
          icon={<Pencil size={13} />}
          onClick={openEditor}
        >
          编辑
        </Button>
      </div>

      {publishedReady ? (
        <DashboardGlobalFilterBar
          filters={designer.dashboard.globalFilters}
          runtimeValues={designer.runtimeFilterValues}
          widgets={designer.widgets}
          datasets={designer.datasets}
          analyses={designer.analyses}
          editable={false}
          onRuntimeValue={designer.setRuntimeFilterValue}
          onReset={designer.resetRuntimeFilters}
          onManage={() => undefined}
        />
      ) : null}

      <main className="min-h-0 flex-1 overflow-auto bg-[#f6f7f9] p-4">
        <div
          ref={containerRef}
          className="mx-auto min-h-[calc(100vh-188px)] min-w-[760px] max-w-[1540px] rounded-[10px] border border-[#e7e9ed] bg-white shadow-[0_4px_18px_rgba(16,24,40,.045)]"
        >
          {!publishedReady ? (
            <div className="flex min-h-[420px] items-center justify-center text-[11px] text-[#98a2b3]">
              正在载入已发布版本...
            </div>
          ) : mounted && width > 0 ? (
            <ReactGridLayout
              width={width}
              layout={layout}
              gridConfig={{
                cols: GRID_COLUMNS,
                rowHeight: GRID_ROW_HEIGHT,
                margin: [10, 10],
                containerPadding: [10, 10],
              }}
              dragConfig={{ enabled: false }}
              resizeConfig={{ enabled: false }}
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

                return (
                  <div key={widget.id}>
                    <WidgetShell
                      widget={widget}
                      analysis={analysis}
                      runtimeSpec={runtimeSpec}
                      dataset={dataset}
                      runtimeFilters={designer.runtimeFiltersForWidget(widget.id)}
                      drillPath={drillPath}
                      selected={false}
                      preview
                      onSelect={() => undefined}
                      onDataSelect={(selection) => {
                        const target = designer.handleWidgetSelection(widget.id, selection);
                        if (target) history.push(target);
                      }}
                      onDrillBack={(depth) => designer.drillBack(widget.id, depth)}
                      onDuplicate={() => undefined}
                      onDelete={() => undefined}
                    />
                  </div>
                );
              })}
            </ReactGridLayout>
          ) : null}

          {publishedReady && !designer.widgets.length && !designer.datasetsLoading ? (
            <div className="flex min-h-[420px] items-center justify-center px-6 text-center">
              <div>
                <BarChart3 size={18} className="mx-auto text-[#a0a6af]" />
                <div className="mt-2 text-[12px] font-medium text-[#667085]">当前发布版本暂无图表</div>
              </div>
            </div>
          ) : null}
        </div>
      </main>
    </div>
  );
}
