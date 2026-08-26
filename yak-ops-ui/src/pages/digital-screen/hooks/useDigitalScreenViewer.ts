import type { PublishedDataset } from '@/components/analysis/model';
import {
  getDigitalScreen,
  listDigitalScreenDatasets,
  type DigitalScreenBindings,
  type DigitalScreenInstance,
} from '@/services/digital-screen';
import { resolveScreenTemplateById } from '@/services/screen-template-service';
import { message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useScreenRuntimeData } from './useScreenRuntimeData';

const EMPTY_BINDINGS: DigitalScreenBindings = {};

export function useDigitalScreenViewer(id?: string) {
  const [screen, setScreen] = useState<DigitalScreenInstance>();
  const [datasets, setDatasets] = useState<PublishedDataset[]>([]);
  const [dataError, setDataError] = useState('');
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!id) {
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    void getDigitalScreen(id)
      .then(setScreen)
      .catch((error) => message.error(error instanceof Error ? error.message : '加载数字化大屏失败'))
      .finally(() => setIsLoading(false));
  }, [id]);

  useEffect(() => {
    let active = true;
    setDataError('');
    void listDigitalScreenDatasets()
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
  const runtime = useScreenRuntimeData(template, screen?.bindings ?? EMPTY_BINDINGS, datasets);

  return {
    screen,
    template,
    runtime,
    dataError,
    isLoading,
  };
}
