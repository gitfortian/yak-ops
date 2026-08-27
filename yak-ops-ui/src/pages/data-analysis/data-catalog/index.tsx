import { BRAND_THEME } from '@/styles/brand';
import { ConfigProvider } from 'antd';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { CatalogDatasetDetail } from './components/CatalogDatasetDetail';
import { CatalogDatasetTable } from './components/CatalogDatasetTable';
import { CatalogSidebar } from './components/CatalogSidebar';
import { useDataCatalog } from './hooks/useDataCatalog';
import { DATA_CATALOG_TREE_STYLES } from './styles';

export default function DataCatalogPage() {
  const catalog = useDataCatalog();

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="flex h-[calc(100vh-64px)] min-h-[640px] flex-col overflow-hidden bg-white text-[#161823]">
        <header className="shrink-0 border-b border-[#e4e7ec] px-5 py-3">
          <h1 className="m-0 text-[22px] font-semibold leading-8 text-[#161823]">
            数据目录
          </h1>
        </header>

        <div className="flex min-h-0 flex-1 overflow-hidden">
          <aside
            className="group relative shrink-0 overflow-hidden bg-white transition-[width] duration-200 ease-out"
            style={{ width: catalog.isLeftCollapsed ? 0 : catalog.leftWidth }}
          >
            <div style={{ width: catalog.leftWidth }} className="h-full">
              <CatalogSidebar
                datasets={catalog.datasets}
                treeData={catalog.visibleTreeData}
                selectedKey={catalog.selectedKey}
                keyword={catalog.treeKeyword}
                isLoading={catalog.isLoading}
                onKeywordChange={catalog.setTreeKeyword}
                onSelect={catalog.selectTreeNode}
              />
            </div>
          </aside>

          <div
            role="separator"
            aria-label="调整数据目录面板宽度"
            aria-orientation="vertical"
            onPointerDown={catalog.isLeftCollapsed ? undefined : catalog.handleResizeStart}
            className={[
              'group relative z-20 w-3 shrink-0 touch-none',
              catalog.isLeftCollapsed ? 'cursor-default' : 'cursor-col-resize',
            ].join(' ')}
          >
            <div
              className={[
                'pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-[#dfe3e8]',
                'transition-[width,background-color] duration-150',
                !catalog.isLeftCollapsed
                  ? 'group-hover:w-[2px] group-hover:bg-[rgba(254,44,85,.55)] group-active:bg-[rgba(254,44,85,1)]'
                  : '',
              ].join(' ')}
            />
            <button
              type="button"
              aria-label={catalog.isLeftCollapsed ? '展开数据目录面板' : '收起数据目录面板'}
              onPointerDown={(event) => event.stopPropagation()}
              onClick={() => catalog.setIsLeftCollapsed((value) => !value)}
              className={[
                'absolute left-1/2 top-1/2 z-20 flex h-8 w-4 -translate-x-1/2 -translate-y-1/2',
                'items-center justify-center rounded-[3px] border border-[#dfe3e8] bg-white text-[#667085]',
                'shadow-[0_1px_2px_rgba(16,24,40,0.05)] transition-[color,border-color,box-shadow] duration-150',
                'hover:border-[#cfd4dc] hover:text-[#161823] focus:outline-none focus-visible:ring-2',
                'focus-visible:ring-[rgba(254,44,85,.16)]',
              ].join(' ')}
            >
              {catalog.isLeftCollapsed ? <ChevronRight size={12} /> : <ChevronLeft size={12} />}
            </button>
          </div>

          {catalog.selectedDataset ? (
            <CatalogDatasetDetail
              dataset={catalog.selectedDataset}
              activeTab={catalog.detailTab}
              statusUpdatingId={catalog.statusUpdatingId}
              onTabChange={catalog.setDetailTab}
              onToggleStatus={(dataset) => void catalog.updateDatasetStatus(dataset)}
            />
          ) : (
            <CatalogDatasetTable
              scopeTitle={catalog.scopeTitle}
              scopeCount={catalog.scopeDatasets.length}
              datasets={catalog.pagedDatasets}
              filteredCount={catalog.filteredDatasets.length}
              keyword={catalog.listKeyword}
              status={catalog.status}
              sourceType={catalog.sourceType}
              current={catalog.current}
              pageSize={catalog.pageSize}
              isLoading={catalog.isLoading}
              loadError={catalog.loadError}
              statusUpdatingId={catalog.statusUpdatingId}
              onKeywordChange={(keyword) => {
                catalog.setListKeyword(keyword);
                catalog.setCurrent(1);
              }}
              onStatusChange={(status) => {
                catalog.setStatus(status);
                catalog.setCurrent(1);
              }}
              onSourceTypeChange={(sourceType) => {
                catalog.setSourceType(sourceType);
                catalog.setCurrent(1);
              }}
              onPageChange={(current, pageSize) => {
                catalog.setCurrent(pageSize === catalog.pageSize ? current : 1);
                catalog.setPageSize(pageSize);
              }}
              onReset={() => {
                catalog.resetFilters();
                void catalog.loadCatalog();
              }}
              onReload={() => void catalog.loadCatalog()}
              onSelectDataset={catalog.selectDataset}
              onToggleStatus={(dataset) => void catalog.updateDatasetStatus(dataset)}
            />
          )}
        </div>
      </div>

      <style>{DATA_CATALOG_TREE_STYLES}</style>
    </ConfigProvider>
  );
}
