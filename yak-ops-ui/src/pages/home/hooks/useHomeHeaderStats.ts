import { getDataSourceSummary } from '@/services/data-source';
import { homeDataCenterApi } from '@/services/home';
import { useEffect, useState } from 'react';

export interface HomeHeaderStats {
  dataSourceCount: number;
  runningCount: number;
  exceptionCount: number;
}

const EMPTY_STATS: HomeHeaderStats = {
  dataSourceCount: 0,
  runningCount: 0,
  exceptionCount: 0,
};

export function useHomeHeaderStats(): HomeHeaderStats {
  const [stats, setStats] = useState<HomeHeaderStats>(EMPTY_STATS);

  useEffect(() => {
    let active = true;

    Promise.allSettled([
      getDataSourceSummary(),
      homeDataCenterApi.overview('7d'),
    ]).then(([dataSourceResult, overviewResult]) => {
      if (!active) return;

      setStats({
        dataSourceCount:
          dataSourceResult.status === 'fulfilled'
            ? dataSourceResult.value.total || 0
            : 0,
        runningCount:
          overviewResult.status === 'fulfilled'
            ? overviewResult.value.data?.metrics?.runningCount || 0
            : 0,
        exceptionCount:
          overviewResult.status === 'fulfilled'
            ? overviewResult.value.data?.metrics?.failedCount || 0
            : 0,
      });
    });

    return () => {
      active = false;
    };
  }, []);

  return stats;
}
