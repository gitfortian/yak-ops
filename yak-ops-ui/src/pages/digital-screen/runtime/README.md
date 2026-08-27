# Digital Screen Runtime

`runtime` owns execution of a saved Digital Screen definition. Screen persistence and Draft/PublishedVersion lifecycle stay in `services/digital-screen`; Dataset HTTP stays in `services/dataset`.

## Roles

```text
Screen definition + Dataset catalog
              |
              v
          planner.ts
              |
       Runtime candidates
              |
              v
           query.ts
              |
              v
  Runtime Component Registry
   - bindable capability
   - query contract
   - data adapter
              |
              v
       useScreenRuntime
              |
              v
        ScreenRuntime.tsx
              |
              v
 generic screen-engine renderer
```

`components/screen-engine/runtime` owns the React Renderer Registry. It only answers “how does this component render?” and intentionally knows nothing about Dataset bindings.

PR 3 registers all current component types explicitly and removes the legacy adapter/render switches. PR 4 may optimize the planner/executor with request deduplication, caching, cancellation and refresh policies without changing Viewer or component plugins.
