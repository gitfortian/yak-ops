import type { ScreenComponent } from '@/components/screen-engine';
import {
  queryDataset,
  type DatasetQueryResult,
  type PublishedDataset,
} from '@/services/dataset';
import type { DigitalScreenComponentBinding } from '@/services/digital-screen';
import {
  buildScreenDatasetQueryPayload,
  canQueryScreenComponent,
} from './binding';
import { screenRuntimeComponentRegistry } from './registry/builtin-plugins';

export const toScreenComponentData = (
  component: ScreenComponent,
  binding: DigitalScreenComponentBinding,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
) => screenRuntimeComponentRegistry.adaptData(component, binding, { dataset, result });

export const queryScreenComponentData = async (
  component: ScreenComponent,
  binding: DigitalScreenComponentBinding,
  dataset: PublishedDataset,
) => {
  if (!canQueryScreenComponent(component, binding)) return undefined;
  const result = await queryDataset(
    dataset.id,
    buildScreenDatasetQueryPayload(component, binding),
  );
  return toScreenComponentData(component, binding, dataset, result);
};
