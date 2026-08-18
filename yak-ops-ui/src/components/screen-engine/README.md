# Screen Template Engine

The screen template engine is the phase-1 foundation for Yak Ops digital screens. It keeps layout and visual design in a typed template document so later screen creation only needs to bind datasets into predefined component slots.

## What phase 1 contains

- `ScreenTemplate` / `ScreenComponent` typed protocol.
- Fixed 1920×1080 design canvas with responsive renderer.
- Metric, line, bar, pie, table and text components.
- Runtime data overrides keyed by component id for the next dataset-binding phase.
- Template validation for duplicate ids, invalid bounds and malformed preview series.
- Built-in template registry with three ready-to-render templates.

## Usage

```tsx
import {
  ScreenRenderer,
  getScreenTemplateById,
} from '@/components/screen-engine';

const template = getScreenTemplateById('data-center');

export default function Preview() {
  if (!template) return null;
  return <ScreenRenderer template={template} />;
}
```

Dataset integration can replace one component's preview payload without touching its layout or style:

```tsx
<ScreenRenderer
  template={template}
  data={{
    tasks: {
      value: 320,
      unit: '个',
      trend: 8.4,
      trendDirection: 'up',
    },
  }}
/>
```

Phase 2 should resolve `dataBinding` through the existing dataset APIs and pass query results into the same `data` override channel.
