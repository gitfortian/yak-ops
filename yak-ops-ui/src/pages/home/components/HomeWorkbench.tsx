import {
  DataLineageOverview,
  DatasetOverview,
  useHomeAssetOverview,
} from './HomeAssetOverview';
import HomeDataServiceOverview from './HomeDataServiceOverview';
import HomeQualitySidebarOverview from './HomeQualitySidebarOverview';
import HomeVisualizationOverview from './HomeVisualizationOverview';

/**
 * 首页主内容流。
 *
 * 宽内容保留在主列，避免数据集、血缘和可视化在窄侧栏中失去信息密度。
 */
export function HomeWorkbenchMain() {
  const assetOverviewState = useHomeAssetOverview();

  return (
    <div className="min-w-0 space-y-4">
      <DatasetOverview state={assetOverviewState} />
      <DataLineageOverview state={assetOverviewState} />
      <HomeVisualizationOverview />
    </div>
  );
}

/**
 * 首页辅助内容流。
 *
 * 数据质量使用侧栏专用紧凑视图，数据服务本身即适配约 400px 宽度。
 */
export function HomeWorkbenchSidebar() {
  return (
    <div className="min-w-0 space-y-4">
      <HomeQualitySidebarOverview />
      <HomeDataServiceOverview />
    </div>
  );
}

/**
 * 保留独立使用 HomeWorkbench 时的兼容入口。
 */
export default function HomeWorkbench() {
  return (
    <div className="mt-4 grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_380px] 2xl:grid-cols-[minmax(0,1fr)_410px]">
      <HomeWorkbenchMain />
      <HomeWorkbenchSidebar />
    </div>
  );
}
