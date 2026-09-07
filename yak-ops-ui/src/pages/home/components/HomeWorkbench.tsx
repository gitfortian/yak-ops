import {
  DataLineageOverview,
  DatasetOverview,
  useHomeAssetOverview,
} from './HomeAssetOverview';
import HomeDataServiceOverview from './HomeDataServiceOverview';
import HomeQualitySidebarOverview from './HomeQualitySidebarOverview';
import HomeVisualizationOverview from './HomeVisualizationOverview';

/**
 * 首页工作台。
 *
 * 数据集保留中等信息密度，其余能力收敛为等高的轻量入口，避免首页变成多个详情页的拼接。
 */
export function HomeWorkbenchMain() {
  const assetOverviewState = useHomeAssetOverview();

  return (
    <div className="min-w-0 space-y-4">
      <DatasetOverview state={assetOverviewState} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-4">
        <HomeQualitySidebarOverview />
        <DataLineageOverview state={assetOverviewState} />
        <HomeVisualizationOverview />
        <HomeDataServiceOverview />
      </div>
    </div>
  );
}

export default function HomeWorkbench() {
  return <HomeWorkbenchMain />;
}
