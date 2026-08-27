import type { ScreenComponent } from '@/components/screen-engine';
import type { DigitalScreenComponentBinding } from '@/services/digital-screen';
import { SCREEN_AGGREGATION_LABELS } from './adapters/shared';
import { screenRuntimeComponentRegistry } from './registry/builtin-plugins';

export { SCREEN_AGGREGATION_LABELS };

export const isBindableScreenComponent = (component?: ScreenComponent) => (
  screenRuntimeComponentRegistry.isBindable(component)
);

export const canQueryScreenComponent = (
  component: ScreenComponent,
  binding?: DigitalScreenComponentBinding,
) => screenRuntimeComponentRegistry.canQuery(component, binding);

export const buildScreenDatasetQueryPayload = (
  component: ScreenComponent,
  binding: DigitalScreenComponentBinding,
) => screenRuntimeComponentRegistry.buildQuery(component, binding);
