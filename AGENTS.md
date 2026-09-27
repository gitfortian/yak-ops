# Yak Ops Agent Context Router

Scope:
- Whole repository

Purpose:
- Route a task to the minimum required contracts and rules.
- Keep Datasource as the current user-facing product domain while YakFlow is built as a staged data-sync capability.
- Prevent removed architecture from becoming new context.

Engineering Model:
- `docs/engineering-context-model.md`

## Backend Context

Any Java change starts with:

```text
ARCHITECTURE.md
JAVA_RULES.md
```

Then load only the nearest rules touched by the task:

```text
logging / logger / log level / runtime logging
→ LOGGING_RULES.md

**/controller/**
→ CONTROLLER_RULES.md

yak-ops-security/**
→ yak-ops-security/SECURITY_RULES.md

yak-ops-business/yak-ops-business-datasource/**
→ yak-ops-business/yak-ops-business-datasource/DATASOURCE_RULES.md

yak-ops-business/yak-ops-business-data-sync/**
→ yak-ops-business/yak-ops-business-data-sync/DATA_SYNC_RULES.md

yak-ops-common/**
→ yak-ops-common/COMMON_RULES.md

yak-ops-dao/**
→ yak-ops-dao/DAO_RULES.md

yak-ops-dao/src/main/resources/db/migration/**
→ yak-ops-dao/FLYWAY_RULES.md

yak-ops-core/**
→ yak-ops-core/CORE_RULES.md

yak-ops-spi/**
→ yak-ops-spi/SPI_RULES.md

yak-ops-plugins/yak-ops-plugin-datasource/**
→ yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN_RULES.md

yak-flow/**
→ yak-flow/YAK_FLOW_RULES.md
```

## Frontend Context

Any frontend change starts with:

```text
yak-ops-ui/ARCHITECTURE.md
yak-ops-ui/FRONTEND_RULES.md
```

Load `yak-ops-ui/SERVICE_RULES.md` when changing backend API calls.

Load `yak-ops-ui/docs/tooling.md` when changing dependencies, Vite, TypeScript, lint, format, build or package scripts.

Frontend architecture is currently in migration toward `app / pages / features / service / shared`. Treat `src/services`, `src/components` and `src/utils` as migration facts, not target architecture.

## Capability Context

```text
Task
→ docs/README.md
→ target capability README under docs/capabilities/
→ Target Capability when one exists
→ Target Code
→ Nearest Rules
```

If no Capability Contract exists, inspect current code first and write the minimum contract before changing product behavior.

## Execution Rules

Must:
- Read current code and direct dependencies before changing structure.
- Treat Datasource as the current user-facing product domain, Data Sync as the staged product layer being added on top, and YakFlow as the execution capability.
- Treat User/Login/Security as supporting platform capability, not a second product domain.
- Reuse existing utilities before adding abstractions.
- Solve only the current task.
- Prefer modifying existing code over adding layers.
- Validate the smallest meaningful result with explicit local compile/build/manual verification when needed.
- For Java changes, apply the formatter when needed and run the repository Spotless check command defined in `JAVA_RULES.md` before declaring style verification.
- State exactly what verification was or was not executed.

Must Not:
- Reintroduce removed domains, external yak-framework dependencies, tests or CI as a side effect.
- Add Manager / Coordinator / Handler / Assembler / Adapter only for symmetry.
- Treat future design as current implementation.
- Use deleted documentation or old Git history as current architecture unless historical analysis is explicitly requested.

**Locate first. Load only what constrains the task. Change only what the task owns.**