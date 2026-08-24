# Dashboard Architecture

## Architecture principle

Package names describe business capability and role. Dashboard uses explicit boundaries rather than a generic Service/Support layer.

```text
HTTP
  -> DashboardService facade
      -> Definition / Version / Publication
          -> shared Read side
          -> Composition
          -> Dashboard-owned gateways
          -> Repositories
              -> DAO

Committed Dashboard change
  -> Change fact
  -> after-commit Lineage listener
      -> effective snapshot reader
      -> Lineage synchronizer
          -> DashboardLineageGraphGateway
              -> Lineage adapter
```

## Package roles

### Root facade

`DashboardService` is the stable application/HTTP compatibility facade. It delegates to explicit internal roles and should not absorb their implementation logic.

### `definition`

Owns stable Dashboard identity mutation use cases.

- `DashboardManager`: create/delete identity lifecycle.

Definition uses the shared `read` role for identity/current reads and the neutral `change` fact for derived projection notification. It may delegate version append during initial V1 creation without making Version depend back on Definition.

### `read`

`DashboardReader` is the shared read-side entry for Dashboard identity and current detail.

It depends only on Domain and Repository ports, so Definition, Version, Publication and the stable facade can reuse it without creating package cycles.

### `change`

`DashboardChangedEvent` is the committed mutation fact consumed by derived projections. It is a tiny framework-free value and does not depend on Definition, Version, Publication or Lineage.

### `version`

Owns immutable version lifecycle.

- `DashboardVersionAppender`: append snapshot then move current pointer.
- `DashboardVersionManager`: save and restore use cases.
- `DashboardVersionReader`: version history and immutable snapshot reads.

This package may use shared `read` and `change` roles. It must not implement publication semantics.

### `publication`

Owns the published pointer and effective-snapshot selection.

- `DashboardPublisher`: move published pointer to current.
- `DashboardEffectiveSnapshotReader`: choose published when present, otherwise current for downstream effective projection.

Publication uses shared `read`, Repository ports and the neutral change fact. It must not mutate immutable versions.

### `composition`

Owns normalization of candidate Dashboard composition.

- `DashboardCompositionNormalizer`: orchestration only.
- `DashboardWidgetPolicy`: widget identity/content-mode rules.
- `DashboardLayoutPolicy`: grid constraints.
- `DashboardFilterPolicy`: global-filter/binding rules.
- `DashboardInteractionPolicy`: interaction graph rules.
- `DashboardJsonPolicy`: dynamic JSON shape/size boundary.

Composition may enter Analysis only through `DashboardAnalysisGateway`.

### `gateway/analysis`

Dashboard-owned port for validating reusable Analysis references.

```text
Dashboard composition
  -> DashboardAnalysisGateway
  -> AnalysisDashboardAdapter
  -> AnalysisReferenceService
```

Only the adapter knows the Analysis module API.

### `reference`

Hosts Dashboard's implementation of the Analysis deletion guard. It asks `DashboardReferenceRepository` about historical references rather than reaching into DAO directly.

### `lineage`

Owns derived lineage projection, not Dashboard command truth.

- `DashboardLineageRefreshListener`: after-commit trigger and failure isolation.
- `DashboardLineageSynchronizer`: independent-transaction projection convergence.
- `DashboardInlineLineageExtractor`: best-effort inline payload interpretation.

The listener consumes the neutral `change` fact and effective publication read-side. Lineage enters the shared graph only through `DashboardLineageGraphGateway`.

### `gateway/lineage`

Dashboard-owned graph contract and adapter.

Only `LineageDashboardAdapter` may translate to Lineage module types and ObjectMapper/JsonNode infrastructure required by that external contract.

### `repository`

Business persistence ports are split by role:

- `DashboardRepository`: identity and current/published pointers;
- `DashboardVersionRepository`: immutable version snapshots;
- `DashboardReferenceRepository`: historical Analysis reference query.

Adapters translate to DAO/PO and may use the persistence codec.

### `repository/codec`

`DashboardJsonCodec` owns persistence serialization for Dashboard JSON columns. JSON persistence details do not leak into application/domain APIs.

### `dao`

Database-facing persistence implementation. DAO may expose PO/MyBatis details internally but not upward through repository contracts.

### `domain`

Framework-free Dashboard semantic values and snapshots.

Domain does not depend on Spring, HTTP, MyBatis, DAO, Repository adapters, Analysis implementation APIs or Lineage implementation APIs.

### `controller/v1`

HTTP transport boundary. DTO/VO/mappers remain here. Controller enters the application through `DashboardService` and never through repositories, DAOs or gateway adapters.

### `config`

Infrastructure wiring only.

## Save flow

```text
HTTP request
 -> DashboardService.saveVersion
 -> DashboardVersionManager
 -> DashboardReader
 -> DashboardCompositionNormalizer
 -> policies / Analysis gateway
 -> DashboardVersionAppender
 -> DashboardVersionRepository.appendVersion
 -> DashboardRepository.updateCurrentVersion
 -> publish DashboardChangedEvent
 -> commit
 -> after-commit lineage projection
```

## Create flow

```text
normalize candidate
 -> create Dashboard identity
 -> append V1
 -> move current pointer to V1
 -> publish change fact
 -> commit
```

## Restore flow

```text
read historical immutable snapshot Vn
 -> convert snapshot to candidate DashboardDraft
 -> normalize
 -> append next version Vm
 -> current = Vm
 -> published unchanged
```

No historical version row is reactivated or mutated.

## Publish flow

```text
require Dashboard through shared read side
 -> require current version
 -> if current == published: no-op
 -> update published pointer to current
 -> publish change fact
 -> commit
```

## Lineage flow

```text
DashboardChangedEvent AFTER_COMMIT
 -> deleted? clear evidence
 -> otherwise DashboardEffectiveSnapshotReader
      published if present
      else current
 -> DashboardLineageSynchronizer (REQUIRES_NEW)
 -> DashboardLineageGraphGateway
 -> LineageDashboardAdapter
 -> shared Lineage graph
```

Failure after the Dashboard commit is logged and isolated.

## Persistence flow

```text
Application role
 -> Repository port
 -> Repository adapter
 -> DAO
 -> MyBatis / Dashboard tables
```

Repository adapters own persistence mapping. Application roles do not know PO/Mapper/JdbcTemplate.

## Acyclic application graph

The shared `read` and `change` packages are intentionally lower-level than Definition/Version/Publication:

```text
definition -> read / change / version-appender
version    -> read / change
publication-> read / change
read       -> repository / domain
change     -> JDK only
```

Version does not depend on Definition, which closes the Stage 1 `definition <-> version` package cycle.

## Architecture exclusions

Do not introduce top-level production buckets such as:

```text
service/
common/
helper/
helpers/
utils/
util/
base/
persistence/
support/
```

A new class must state the Dashboard capability and role it belongs to.
