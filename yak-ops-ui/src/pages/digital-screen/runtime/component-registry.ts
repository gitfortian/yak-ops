import type { ScreenComponent, ScreenComponentData } from '@/components/screen-engine';
import type { DatasetQueryResult, PublishedDataset } from '@/services/dataset';
import type { DigitalScreenComponentBinding } from '@/services/digital-screen';

export interface ScreenRuntimeAdapterContext {
  component: ScreenComponent;
  binding: DigitalScreenComponentBinding;
  dataset: PublishedDataset;
  result: DatasetQueryResult;
}

export interface ScreenRuntimeComponentPlugin {
  type: ScreenComponent['type'];
  adaptData?: (context: ScreenRuntimeAdapterContext) => ScreenComponentData | undefined;
}

/**
 * Incremental runtime extension point for screen components.
 *
 * PR 0 deliberately starts with the data-adapter role only. Renderer / inspector
 * roles can move into the same plugin contract later without forcing a big-bang
 * rewrite of the existing screen engine.
 */
class ScreenRuntimeComponentRegistry {
  private readonly plugins = new Map<ScreenComponent['type'], ScreenRuntimeComponentPlugin>();

  register(plugin: ScreenRuntimeComponentPlugin) {
    this.plugins.set(plugin.type, plugin);
    return this;
  }

  get(type: ScreenComponent['type']) {
    return this.plugins.get(type);
  }
}

export const screenRuntimeComponentRegistry = new ScreenRuntimeComponentRegistry();
