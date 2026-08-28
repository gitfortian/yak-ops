import { homeAssetOverviewApi } from '@/services/home';
import { useEffect, useState } from 'react';

import type { HomeAssetOverviewState } from './homeAssetOverviewShared';

export type { HomeAssetOverviewState } from './homeAssetOverviewShared';
export { DatasetOverview } from './HomeDatasetOverview';
export { DataLineageOverview } from './HomeLineageOverview';

export function useHomeAssetOverview(): HomeAssetOverviewState {
  const [state, setState] = useState<HomeAssetOverviewState>({
    loading: true,
    failed: false,
  });

  useEffect(() => {
    let active = true;
    homeAssetOverviewApi
      .overview()
      .then((response) => {
        if (!active) return;
        if (!response.data) {
          setState({ loading: false, failed: true });
          return;
        }
        setState({ data: response.data, loading: false, failed: false });
      })
      .catch(() => {
        if (active) setState({ loading: false, failed: true });
      });

    return () => {
      active = false;
    };
  }, []);

  return state;
}
