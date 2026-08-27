import { Sparkles } from 'lucide-react';

import {
  DataLineageOverview,
  DatasetOverview,
  useHomeAssetOverview,
} from './HomeAssetOverview';
import HomeDataServiceOverview from './HomeDataServiceOverview';
import QualityOverview from './HomeQualityOverview';
import HomeVisualizationOverview from './HomeVisualizationOverview';

/**
 * 首页业务总览。
 *
 * 数据集、血缘、数据质量、数据服务与可视化均接入当前真实数据源。
 */
export default function HomeWorkbench() {
  const assetOverviewState = useHomeAssetOverview();

  return (
    <div className="mt-4 space-y-4">
      <DatasetOverview state={assetOverviewState} />

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.32fr)_minmax(400px,0.68fr)]">
        <QualityOverview />
        <HomeDataServiceOverview />
      </div>

      <DataLineageOverview state={assetOverviewState} />

      <HomeVisualizationOverview />

    </div>
  );
}
