import type { ScreenTemplate } from '@/components/screen-engine';
import type { PublishedDataset } from '@/services/dataset';
import type { DigitalScreenBindings } from '@/services/digital-screen';
import { canQueryScreenComponent } from './binding';
import type { ScreenRuntimeCandidate } from './model';

/** Builds execution candidates only. Request merging/caching belongs to PR 4. */
export const planScreenRuntimeQueries = (
  template: ScreenTemplate | undefined,
  bindings: DigitalScreenBindings,
  datasets: PublishedDataset[],
): ScreenRuntimeCandidate[] => {
  if (!template) return [];
  const datasetMap = new Map(datasets.map((dataset) => [dataset.id, dataset]));
  return template.components.flatMap((component) => {
    const binding = bindings[component.id];
    if (!binding || !canQueryScreenComponent(component, binding)) return [];
    const dataset = datasetMap.get(binding.datasetId);
    return dataset ? [{ component, binding, dataset }] : [];
  });
};

export const countBoundScreenComponents = (
  template: ScreenTemplate | undefined,
  bindings: DigitalScreenBindings,
) => template?.components.filter((component) => Boolean(bindings[component.id])).length ?? 0;
