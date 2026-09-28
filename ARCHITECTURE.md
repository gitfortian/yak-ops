# Yak Ops Architecture

Status: Active

Scope:
- Current Yak Ops Datasource product architecture
- Data Sync staged product task/instance architecture
- YakFlow staged data-sync execution capability
- Workspace business ownership boundary
- Supporting Platform capability for Security/User, Workspace and User Preference
- Module ownership and dependency direction

Depends On:
- `docs/engineering-context-model.md`

## Principle

Yak Ops currently exposes Datasource as its user-facing product, with Workspace as the shared business ownership boundary. Data Sync is now a staged product capability that owns task definitions and task instances, while YakFlow remains the execution capability underneath it.

Platform is the supporting product-context capability required to use Yak Ops. Inside Platform, Security answers who the user is, Workspace answers which business data boundary is active, and User Preference persists what the authenticated user prefers across logout, browsers and devices. These remain separate Java package and Service boundaries while sharing one physical Maven module.

Architecture follows current ownership, not historical modules and not a future platform plan.

HTTP is an application boundary. All Controller ownership belongs to `yak-ops-boot`; capability modules expose business capability to Boot and never own HTTP entry classes.

Application runtime infrastructure is also a Boot boundary. DataSource/MyBatis-Plus runtime policy, OpenAPI/Swagger and MVC interceptor/filter registration belong to `yak-ops-boot`. Capability modules provide behavior and persistence contracts without assembling the final Spring application. Boot prefers Spring Boot/Starter auto-configuration over manually recreating framework beans.

## Current Modules

### `yak-ops-common`

Owns shared data contracts for Datasource, Workspace, User Preference and Security, plus the unified Result / ErrorCode / PageData contracts, request `WorkspaceContext` and the cross-domain `BusinessException` base. Security HTTP DTO / VO remain here when Boot and Security share them, but Security-specific error codes, exceptions and internal models do not.

### `yak-ops-platform`

Owns supporting product context: Security/User, Workspace and User Preference. Security owns user management, login/logout/current identity, HttpSession authentication state, authentication policy and the authentication interceptor implementation. Workspace owns Workspace lifecycle and membership. User Preference owns authenticated-user-scoped favorites and usage signals. These capabilities share one Maven module but remain separate package and Service boundaries.

Security does not own Controller, ControllerAdvice, OpenAPI configuration, connection-pool/MyBatis assembly or MVC interceptor registration. Boot exposes and wires the current Security HTTP capability by calling Security-owned services and registering Security-owned behavior.

The Security capability inside Platform was migrated from `yak-framework/yak-security`.

Security business/runtime code uses the `io.yak.ops.security` product namespace. `SecurityErrorCode`, `YakSecurityException`, `UserAccount` and `UserCheckType` are Security-owned domain contracts. Shared HTTP DTO / VO live in `io.yak.ops.common`, while user persistence is owned by `yak-ops-dao`. `UserStatus` temporarily remains Common because DAO persistence directly owns its MyBatis enum mapping. Security exposes exactly two stable Service entries to Boot: `LoginService` and `UserService`; user administration behavior is consolidated inside `UserServiceImpl` rather than split into a second concrete service.

### `yak-ops-dao`

Owns shared database persistence infrastructure:

- MyBatis-Plus Repository base contract
- MyBatis-Plus Repository base implementation
- persistence rules shared by concrete DAO code
- the single Flyway configuration and schema history for all Yak Ops modules
- all versioned SQL under `yak-ops-dao/src/main/resources/db/migration/yak-ops`

Concrete Security user persistence, Workspace persistence, User Preference persistence, Datasource persistence and Data Sync task/instance persistence are owned here.

BusinessImpl may use DAO-owned Entity/Repository internally. Entity and DAO Model do not cross the Business boundary into Boot.

DAO owns persistence and schema migration, not final application DataSource/MyBatis runtime assembly. That assembly belongs to Boot.

### `yak-ops-spi`

Reserved minimal extension boundary.

### `yak-ops-core`

Reserved empty module.

### `yak-flow/yak-flow-api`

Owns the stable YakFlow batch/stream-neutral data plane contracts: row changelog semantics, common row/schema types, boundedness, Source/Split/Reader/Enumerator contracts, Sink/Writer contracts and opaque checkpoint state.

The API has no Spring, Debezium, JDBC or Yak Ops business dependency. Batch and streaming are not separate APIs: a Source declares `BOUNDED` or `CONTINUOUS_UNBOUNDED`, while CDC changes are represented by `RowKind` on `YakRow`.

Current scope intentionally excludes runtime scheduling, Transform, Debezium integration, JDBC implementation, distributed execution and exactly-once coordination. Detailed constraints are defined in `yak-flow/YAK_FLOW_RULES.md`.

### `yak-flow/yak-flow-runtime`

Owns the first single-node YakFlow execution runtime. It connects one Source and one Sink through a bounded in-memory row channel, runs source and sink work independently, supports cancellation and coordinates source checkpoints with a channel barrier.

The checkpoint barrier is an ordering boundary, not an exactly-once claim: Source state is captured before the barrier enters the channel, Sink flushes every preceding row before the checkpoint completes, and the runtime keeps the completed checkpoint in memory for the active execution. Durable checkpoint storage and restore-after-process-restart are not part of this phase.

The Local Execution Engine depends on `yak-flow-api` only. It does not depend on Spring, Yak Ops Business/DAO, JDBC or Debezium, and it does not introduce distributed scheduling, worker discovery or resource management.

### `yak-flow/yak-flow-connector-jdbc`

Owns YakFlow bounded JDBC table transfer. The first acceptance path is MySQL Source to MySQL, PostgreSQL or Oracle Sink.

The connector reuses the normalized `DataSourceConnection` and JDBC connection runtime from the existing Datasource plugin boundary. It owns synchronization-specific SQL generation, row reading, JDBC field compatibility, logical type conversion, bounded Source lifecycle and batched Sink writes. Datasource plugins continue to own connection parsing, Driver selection, SSH tunneling and Catalog discovery.

Phase 3 intentionally requires the target table to exist. Auto-create DDL and schema evolution are not part of this module stage. Phase 4 extends the same JDBC Sink with an explicit changelog mode for idempotent CDC application by primary key.

### `yak-flow/yak-flow-connector-cdc-mysql`

Owns MySQL continuous change capture for YakFlow. Debezium is strictly an implementation detail inside this connector: Debezium Engine, Kafka Connect SourceRecord, source offsets and schema-history storage never cross the connector boundary.

The connector performs Debezium `snapshot.mode=initial` followed by binlog streaming and converts `READ / CREATE / UPDATE / DELETE` events into the existing `YakRow + RowKind` contract. It uses file-backed Debezium offset and schema-history state under a caller-owned state directory.

Checkpoint completion is downstream-aware. The Local Execution Engine captures Source state, places a barrier into the row channel, flushes the Sink, and only then invokes `SourceReader.notifyCheckpointComplete`. The MySQL CDC Reader uses that callback to acknowledge Debezium records, so a crash before downstream flush does not advance the persisted Debezium offset. This provides at-least-once recovery semantics; it does not claim exactly-once.

### `yak-ops-business`

Owns the product-business Service Layer for Datasource and Data Sync. Stable product capabilities expose one public Service Layer interface and keep Spring implementation, transactions, validation and DAO/Plugin orchestration in `impl`.

The default naming is `XxxBusiness + XxxBusinessImpl`; a capability may explicitly choose `XxxService + XxxServiceImpl` in its nearest rules. A capability must not keep both names for the same boundary.

Boot depends on stable Service Layer interfaces. Public contracts use shared DTO / VO types and do not expose DAO Entity, Mapper, Repository Query or concrete Plugin implementation details.

Detailed rules are defined in `yak-ops-business/BUSINESS_RULES.md`.

### `yak-ops-platform` / Workspace

Owns Workspace creation, Workspace discovery, membership and membership validation through the single stable `WorkspaceService` boundary.

Workspace is not a Security role model. Security owns authenticated identity; Workspace owns the User ↔ Workspace membership relationship and supplies the ownership boundary used by future Workspace-scoped resources.

The request Workspace ID is carried by `X-Workspace-Id`. Boot validates membership and binds the trusted value into Common `WorkspaceContext`. Missing Workspace context is globally allowed; a Workspace-scoped capability explicitly requires it.

### `yak-ops-platform` / User Preference

Owns user-scoped product preference persistence through the single stable `UserPreferenceService` boundary.

User Preference is not Workspace-scoped. Boot supplies the trusted authenticated user ID, while callers only choose a supported preference scene and stable scene-local item key. The current capability persists explicit favorite state and usage signals; it does not own menu labels, routes, icons or datasource display metadata.

The persistence source of truth is `yak_ops_user_preference`. Browser storage may cache UI state but cannot replace server persistence for preferences that must survive logout and device changes.

### `yak-ops-business/yak-ops-business-datasource`

Owns only the current Workspace-scoped Datasource product behavior:

- datasource CRUD, paging and detail inside the active Workspace
- datasource connection testing
- internal datasource plugin discovery, connection parsing and secret handling
- read-only Catalog metadata access for saved Workspace datasources

Datasource exposes exactly one public Service Layer entry: `DataSourceService`. Plugin discovery and secret handling are internal mechanisms behind `DataSourceServiceImpl`.

The module exposes Catalog metadata through the existing DataSourceService boundary; it still does not own SQL execution, SQL audit, a duplicate Domain layer or a Gateway adapter layer.

Datasource may use DAO persistence and the stable Datasource Plugin API only behind `DataSourceServiceImpl`. Datasource is a Workspace Resource: Service reads the trusted active Workspace from `WorkspaceContext`, while DAO queries scope resource access by `workspace_id + resource id/query`.

Datasource does not own Controller, ControllerAdvice, connection-pool assembly or MyBatis runtime configuration. Boot exposes Datasource HTTP APIs and supplies application infrastructure.

### `yak-ops-business/yak-ops-business-data-sync`

Owns Workspace-scoped Data Sync product definitions and execution-instance persistence contracts through the single stable `DataSyncService` boundary.

The current product contract supports both `OFFLINE` and `REALTIME` task definitions. Both reuse the same Workspace-scoped Task persistence and source/target table contract, while `runtime_config` is interpreted by `sync_type`: OFFLINE stores bounded JDBC tuning and REALTIME stores CDC/checkpoint/write tuning. REALTIME currently accepts only MySQL Source and MySQL/PostgreSQL/Oracle Target, requires a Source primary key, and requires the Target primary-key field set to exactly match the Source primary-key field set under case-insensitive same-name mapping.

Data Sync depends on Datasource through the stable `DataSourceService` boundary for resource validation, Catalog reads and internal runtime connection resolution. It does not access Datasource DAO or Plugin Registry directly. The current executable product path can manually run both OFFLINE and REALTIME tasks through YakFlow Local Execution Engine, persist the shared instance lifecycle and Runtime counters, cancel active local executions and mark stale process-local executions LOST on startup.

REALTIME execution is currently MySQL CDC -> MySQL/PostgreSQL/Oracle JDBC CHANGELOG. `RealtimeSyncExecutionPlanner` resolves current Catalog schema and runtime connections after the Instance exists, while `RealtimeSyncExecutor` owns the process-local RUNNING/FAILED/CANCELED loop.

Realtime CDC state is product-owned under `${yak.ops.home}/data/data-sync/realtime/{workspaceId}/{taskId}/v{definitionVersion}`. Debezium engine identity uses the same stable Workspace/Task/version scope, so a later Instance for the same definition reuses persisted offsets and schema history. A new definitionVersion gets a new state domain and therefore starts a fresh snapshot. MySQL replication `serverId` is allocated per active state domain by a single-node allocator and released when execution ends.

LocalExecution itself is still process-local: after an application restart, old active Instances become LOST rather than being resurrected. A later manual run creates a new Instance and reuses the existing REALTIME state domain. Scheduling, distributed recovery and exactly-once coordination remain out of scope.

The instance `definition_snapshot` must never contain datasource credentials, normalized connection JSON, passwords, SSH private keys, tokens or other secrets. Runtime connection material remains owned by Datasource and is resolved by datasource ID only at execution time. Realtime state paths and MySQL serverId leases are runtime-owned and are not persisted inside the definition snapshot.

### `yak-ops-plugins/yak-ops-plugin-datasource`

Owns Datasource provider contracts and implementations.

The active plugin surface is limited to:

- plugin metadata and connection form
- connection parsing and connectivity testing
- Catalog metadata discovery

SQL execution/query contracts are not part of the current plugin boundary.

Datasource Providers are an open extension set. A Provider owns its stable string type, display name and compatibility aliases through the Plugin descriptor. Common and Service Layer code do not enumerate all supported database types; adding a Provider must not require a core enum change.

### `yak-ops-boot`

Owns final application assembly, all HTTP Controllers, ControllerAdvice, health and global runtime configuration. `GlobalExceptionHandler` is the single HTTP exception outlet: capability modules throw `BusinessException` with structured `ErrorCode`, and Boot centrally maps those errors to HTTP status and the unified `Result` contract.

Boot runtime configuration follows a single-runtime contract:

- one application DataSource, created from `spring.datasource` by Spring Boot
- one default transaction manager; Business/Security do not use capability-specific transaction-manager aliases
- one MyBatis-Plus SqlSessionFactory / SqlSessionTemplate created by the Starter
- one MyBatis-Plus interceptor chain; Security tenant isolation is table-scoped inside that shared chain
- MVC authentication interceptor registration
- one application OpenAPI document

Boot must not manually recreate DataSource, SqlSessionFactory, SqlSessionTemplate, TransactionManager or ObjectMapper when Spring Boot already provides the required runtime behavior.

Flyway schema history and migration SQL remain owned by `yak-ops-dao`; Boot supplies the runtime DataSource used by that persistence layer.

Hard boundary:

- every Yak Ops `@Controller` / `@RestController` lives in `yak-ops-boot`
- every Yak Ops Controller package lives under `io.yak.ops.boot.controller`
- Controller depends on stable Service Layer interfaces rather than Impl / DAO / Plugin internals
- application-wide Spring infrastructure configuration lives in `yak-ops-boot`
- capability modules must not depend on Boot

### `yak-ops-ui`

Owns the browser product. Data Integration currently exposes Datasource Management and Offline Sync. Offline Sync supports task definition plus manual run, instance list/detail and cancel; scheduling remains a later phase.

### `yak-ops-dist`

Owns release packaging.

### `yak-ops-bom`

Owns Yak Ops dependency version alignment.

## External Framework Boundary

Yak Ops no longer depends on `yak-framework`.

The former Yak Common and Yak Security code required by the product is now owned inside this repository.

## Dependency Direction

```text
UI
 ↓ HTTP
Boot
 ├────────→ Platform ─────────────→ Common
 │              │
 │              ├─ Security / User
 │              ├─ Workspace
 │              ├─ User Preference
 │              └───────────────→ DAO ─→ Common
 └────────→ Business
                ├─ DataSourceService ─→ DAO / Datasource Plugin API
                └─ DataSyncService ───→ DataSourceService / YakFlow
                                            ↑
                                      Plugin Implementations
```

Boot owns protocol entry and application assembly.

YakFlow API is an implementation-independent contract boundary. YakFlow Local Execution Engine depends on that API and provides the current single-node execution model. YakFlow JDBC Connector also depends on the API and reuses Datasource's normalized JDBC connection boundary. The MySQL CDC connector is now wired into the Data Sync REALTIME execution path; durable realtime state ownership and restart recovery remain later stages.

Platform owns Security/User, Workspace and User Preference capability behavior. Business owns Datasource and Data Sync product behavior. DAO owns persistence and schema. None of them depend on Boot.

## Refactor Rule

```text
Capability Contract
→ current ownership
→ nearest RULES
→ current code
→ minimal migration
→ explicit verification
```

Do not split classes, add modules, or introduce roles only because a file is long.
