import type { PublishedDataset } from '@/services/dataset';
import { listPublishedDatasets } from '@/services/dataset';
import {
  getPublishedDigitalScreen,
  type DigitalScreenBindings,
  type DigitalScreenInstance,
} from '@/services/digital-screen';
import { resolveScreenTemplateById } from '@/services/screen-template-service';
import { useEffect, useMemo, useState } from 'react';
import { useScreenRuntime } from '../../runtime/hooks/useScreenRuntime';
import { SCREEN_RUNTIME_VIEWER_REFRESH_INTERVAL_MS } from '../../runtime/policy';

const EMPTY_BINDINGS: DigitalScreenBindings = {};
const VIEWER_RUNTIME_OPTIONS = {
  refreshIntervalMs: SCREEN_RUNTIME_VIEWER_REFRESH_INTERVAL_MS,
} as const;

export function useDigitalScreenViewer(id?: string) {
  const [screen, setScreen] = useState<DigitalScreenInstance>();
  const [datasets, setDatasets] = useState<PublishedDataset[]>([]);
  const [loadError, setLoadError] = useState('');
  const [dataError, setDataError] = useState('');
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!id) {
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    setLoadError('');
    void getPublishedDigitalScreen(id)
      .then(setScreen)
      .catch((error) => {
        setScreen(undefined);
        setLoadError(error instanceof Error ? error.message : '加载已发布大屏失败');
      })
      .finally(() => setIsLoading(false));
  }, [id]);

  useEffect(() => {
    let active = true;
    setDataError('');
    void listPublishedDatasets()
      .then((values) => {
        if (active) setDatasets(values);
      })
      .catch((error) => {
        if (!active) return;
        setDatasets([]);
        setDataError(error instanceof Error ? error.message : '加载 Dataset 失败');
      });
    return () => {
      active = false;
    };
  }, []);

  const template = useMemo(
    () => (screen ? resolveScreenTemplateById(screen.templateId) : undefined),
    [screen],
  );
  const runtime = useScreenRuntime(
    template,
    screen?.bindings ?? EMPTY_BINDINGS,
    datasets,
    VIEWER_RUNTIME_OPTIONS,
  );

  return {
    screen,
    template,
    runtime,
    loadError,
    dataError,
    isLoading,
  };
}
