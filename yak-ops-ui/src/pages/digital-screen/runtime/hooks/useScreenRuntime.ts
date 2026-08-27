import type { ScreenDataOverrides, ScreenTemplate } from '@/components/screen-engine';
import type { PublishedDataset } from '@/services/dataset';
import type { DigitalScreenBindings } from '@/services/digital-screen';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { ScreenRuntimeDataState, ScreenRuntimeState } from '../model';
import {
  countBoundScreenComponents,
  planScreenRuntimeQueries,
} from '../planner';
import { queryScreenComponentData } from '../query';

const EMPTY_STATE: ScreenRuntimeDataState = {
  data: {},
  loadingIds: [],
  errors: {},
};

/** Coordinates runtime query lifecycle. Query semantics live in plugins/planner, not this hook. */
export function useScreenRuntime(
  template: ScreenTemplate | undefined,
  bindings: DigitalScreenBindings,
  datasets: PublishedDataset[],
): ScreenRuntimeState {
  const [state, setState] = useState<ScreenRuntimeDataState>(EMPTY_STATE);
  const sequence = useRef(0);
  const bindingKey = useMemo(() => JSON.stringify(bindings), [bindings]);
  const datasetKey = useMemo(
    () => datasets.map((dataset) => `${dataset.id}:${dataset.currentVersionNo ?? ''}`).join('|'),
    [datasets],
  );

  useEffect(() => {
    if (!template) {
      setState(EMPTY_STATE);
      return undefined;
    }

    const candidates = planScreenRuntimeQueries(template, bindings, datasets);
    if (!candidates.length) {
      setState(EMPTY_STATE);
      return undefined;
    }

    const requestId = ++sequence.current;
    setState({
      data: {},
      loadingIds: candidates.map(({ component }) => component.id),
      errors: {},
    });

    const timer = window.setTimeout(async () => {
      const results = await Promise.allSettled(candidates.map(async ({ component, binding, dataset }) => ({
        componentId: component.id,
        data: await queryScreenComponentData(component, binding, dataset),
      })));
      if (requestId !== sequence.current) return;

      const data: ScreenDataOverrides = {};
      const errors: Record<string, string> = {};
      results.forEach((result, index) => {
        const componentId = candidates[index].component.id;
        if (result.status === 'fulfilled') {
          if (result.value.data) data[result.value.componentId] = result.value.data;
          return;
        }
        errors[componentId] = result.reason instanceof Error
          ? result.reason.message
          : 'Dataset 查询失败';
      });
      setState({ data, loadingIds: [], errors });
    }, 180);

    return () => {
      window.clearTimeout(timer);
      if (sequence.current === requestId) sequence.current += 1;
    };
  }, [bindingKey, datasetKey, template]);

  return {
    ...state,
    loadingCount: state.loadingIds.length,
    boundCount: countBoundScreenComponents(template, bindings),
  };
}
