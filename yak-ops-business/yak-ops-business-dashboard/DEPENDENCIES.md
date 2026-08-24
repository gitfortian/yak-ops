# Dashboard Dependency Contract

## Direction

Dependencies point from transport/application orchestration toward explicit business/persistence ports. Cross-module dependencies pass through Dashboard-owned gateways.

## Package matrix

| Source | Allowed direct Dashboard dependencies |
| --- | --- |
| root `DashboardService` | `definition`, `read`, `version`, `publication`, `domain` |
| `controller` | root stable facade, controller DTO/VO/mapper, domain types needed by transport mappers |
| `definition` | `read`, `change`, `domain`, `composition`, version appender, `repository` ports |
| `read` | `domain`, `repository` ports |
| `change` | JDK only |
| `version` | `read`, `change`, `domain`, `composition`, `repository` ports |
| `publication` | `read`, `change`, `domain`, `repository` ports |
| `composition` | `domain`, `gateway.analysis` port |
| `reference` | `repository` reference port plus external Analysis deletion extension contract |
| `lineage` | `change`, `domain`, `publication` effective read-side, `gateway.lineage` port |
| `gateway.analysis` | Dashboard-owned port; adapter may call Analysis stable reference facade |
| `gateway.lineage` | Dashboard-owned port; adapter may call Lineage module APIs |
| `repository` | `domain`, `dao`, `repository.codec`, infrastructure annotations |
| `repository.codec` | Jackson persistence infrastructure only |
| `dao` | DAO/PO/Mapper/MyBatis + datasource infrastructure |
| `domain` | JDK only |
| `config` | wiring/infrastructure only |

The matrix describes permitted direction, not permission for every class to depend on every package in its row. Keep dependencies narrower when possible.

## Stable facade corridor

HTTP and compatibility callers enter through:

```text
DashboardController
    -> DashboardService
```

Controller must not inject repositories, DAOs, cross-module gateways or lineage internals.

## Shared read and change roles

`DashboardReader` lives in `read` rather than Definition because Version and Publication also need Dashboard identity/current reads. `DashboardChangedEvent` lives in neutral `change` for the same reason.

This deliberately prevents:

```text
definition -> version
version    -> definition
```

from becoming a package cycle.

`read` depends only on Repository/Domain. `change` is framework-free and depends only on JDK types.

## Analysis corridor

Composition cannot import the Analysis module directly.

The only Dashboard business corridor is:

```text
composition
 -> DashboardAnalysisGateway
 -> AnalysisDashboardAdapter
 -> AnalysisReferenceService
```

Only `gateway/analysis/AnalysisDashboardAdapter` may import Analysis application/reference implementation types for reusable widget validation.

Dashboard's Analysis deletion guard is the inverse extension mechanism already defined by Analysis. The guard implementation may implement `AnalysisDeletionGuard`, but it must query Dashboard history through `DashboardReferenceRepository`, not DAO.

## Lineage corridor

Dashboard lineage business code cannot import `LineageService`, `LineageMaintenanceService`, Lineage PO/model types or ObjectMapper for graph translation.

The only graph corridor is:

```text
lineage
 -> DashboardLineageGraphGateway
 -> LineageDashboardAdapter
 -> Lineage module
```

Only `gateway/lineage/LineageDashboardAdapter` may know the Lineage module API and graph JSON translation infrastructure.

## Repository corridor

Application packages use repository interfaces only.

```text
application role
 -> DashboardRepository / DashboardVersionRepository / DashboardReferenceRepository
 -> adapter
 -> DashboardDao
```

Repository interfaces must not expose:

- `DashboardPO` or other DAO models;
- MyBatis Mapper types;
- `JdbcTemplate`;
- controller DTO/VO;
- serialized JSON strings as the business representation of version composition.

## Domain purity

`domain` must not import:

- Spring;
- Jackson transport/persistence infrastructure;
- MyBatis;
- Dashboard DAO/repository adapters;
- controller DTO/VO;
- Analysis module APIs;
- Lineage module APIs.

Domain types remain plain Java records/enums/value objects.

## Version and publication separation

`version` owns append/read/restore. It must not update the published pointer.

`publication` owns published-pointer transition and effective-snapshot selection. It must not append or mutate historical version rows.

This separation protects:

```text
current != published
```

as a first-class Dashboard invariant.

## Effective projection corridor

Derived consumers that need the effective Dashboard snapshot should use `DashboardEffectiveSnapshotReader` rather than reproducing:

```text
published if present else current
```

in multiple places.

Lineage currently uses this read-side policy.

## Datasource dependency

Dashboard retains a direct Maven dependency on `yak-ops-business-datasource` because persistence/configuration reuses:

- conditional datasource enablement;
- business DataSource wiring;
- Flyway/MyBatis infrastructure.

This is an infrastructure dependency. It must not leak into Dashboard domain/composition/version/publication business semantics beyond configuration/conditional annotations already required for module activation.

## Dataset dependency

Dashboard does not currently require a direct Dataset business dependency. `activeDatasetId` remains optional metadata and inline lineage parsing is projection-only evidence.

Do not add Dataset validation as a side effect of architecture refactoring.

## Forbidden broad buckets

Do not reintroduce top-level production packages named:

```text
service
common
helper
helpers
utils
util
base
persistence
support
```

Use the role vocabulary from repository `CODE_STYLE.md` and the Dashboard architecture instead.

## Acyclic rule

The Dashboard package graph must remain acyclic. A new import that creates a cycle is an architecture defect even when compilation succeeds.
