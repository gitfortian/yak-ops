import type { ScreenComponent, ScreenTemplate } from '@/components/screen-engine';
import type { PublishedDataset } from '@/services/dataset';
import type { DigitalScreenBindings } from '@/services/digital-screen';
import {
  buildScreenDatasetQueryPayload,
  canQueryScreenComponent,
  isBindableScreenComponent,
} from './binding';
import { planScreenRuntimeQueries } from './planner';
import { screenRuntimeComponentRegistry } from './registry/builtin-plugins';

const component = (type: ScreenComponent['type'], id = type) => ({
  id,
  type,
  x: 0,
  y: 0,
  width: 100,
  height: 100,
}) as ScreenComponent;

const binding = {
  datasetId: '12',
  dimensions: ['region'],
  metrics: [{ field: 'amount', aggregation: 'SUM' as const }],
};

describe('digital screen runtime roles', () => {
  it('registers every supported component type explicitly', () => {
    expect(screenRuntimeComponentRegistry.list().map((plugin) => plugin.type).sort()).toEqual([
      'bar', 'line', 'map', 'metric', 'pie', 'table', 'text', 'ticker',
    ]);
  });

  it('keeps binding/query semantics inside component plugins', () => {
    expect(isBindableScreenComponent(component('text'))).toBe(false);
    expect(isBindableScreenComponent(component('bar'))).toBe(true);
    expect(canQueryScreenComponent(component('bar'), binding)).toBe(true);
    expect(buildScreenDatasetQueryPayload(component('table'), binding).limit).toBe(100);
    expect(buildScreenDatasetQueryPayload(component('metric'), binding).dimensions).toEqual([]);
  });

  it('planner only schedules queryable components with an available dataset', () => {
    const template = {
      components: [component('bar', 'bound'), component('text', 'static'), component('pie', 'missing')],
    } as ScreenTemplate;
    const bindings: DigitalScreenBindings = {
      bound: binding,
      missing: { ...binding, datasetId: '404' },
    };
    const datasets = [{ id: '12' }] as PublishedDataset[];

    expect(planScreenRuntimeQueries(template, bindings, datasets).map((item) => item.component.id))
      .toEqual(['bound']);
  });
});
