# Digital Screen Runtime

`runtime` owns the execution path of a saved screen definition. It may resolve Dataset bindings,
adapt query results and coordinate component runtime state, but it must not own screen persistence
or editor-only state.

PR 0 introduces `ScreenRuntimeComponentRegistry` as an incremental extension point. Bar chart data
adaptation is the first migrated plugin role; legacy component adapters remain as a fallback so this
refactor does not change current screen behavior.
