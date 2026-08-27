import type {
  ScreenComponent,
  ScreenDataOverrides,
} from '@/components/screen-engine';
import type { DatasetQueryResult, PublishedDataset } from '@/services/dataset';
import type {
  DigitalScreenBindings,
  DigitalScreenComponentBinding,
} from '@/services/digital-screen';

export interface ScreenRuntimeAdapterContext {
  component: ScreenComponent;
  binding: DigitalScreenComponentBinding;
  dataset: PublishedDataset;
  result: DatasetQueryResult;
}

export interface ScreenRuntimeCandidate {
  component: ScreenComponent;
  binding: DigitalScreenComponentBinding;
  dataset: PublishedDataset;
}

export interface ScreenRuntimeDataState {
  data: ScreenDataOverrides;
  loadingIds: string[];
  errors: Record<string, string>;
}

export interface ScreenRuntimeState extends ScreenRuntimeDataState {
  loadingCount: number;
  boundCount: number;
}

export interface ScreenRuntimePlanInput {
  template?: { components: ScreenComponent[] };
  bindings: DigitalScreenBindings;
  datasets: PublishedDataset[];
}
