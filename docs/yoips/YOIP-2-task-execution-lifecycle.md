# YOIP-2: Task Execution Lifecycle and YakFlow Integration

Status: Discussion

Discussion: https://github.com/weifuwan/yak-ops/pull/1588

Decision: Pending

Implementation: N/A until accepted

Release: N/A until released

Related FLIPs: N/A

## Motivation

Yak Ops provides Workspace-scoped, versioned Task Definitions and a Task Plugin SPI for parameter parsing and validation. YakFlow provides a local PipelineExecutor, JobClient, JobGraph and ExecutionGraph lifecycle, durable checkpoints, and JDBC/MySQL CDC connectors for supported linear pipelines. These capabilities do not yet form an executable product path from a published Task Definition to a persisted execution result.

A Task must be independently executable without requiring a Workflow Instance. The product needs a durable record of each requested execution, immutable definition-version binding, trusted runtime resource resolution, asynchronous job submission, cancellation, diagnostics, and an observable terminal outcome. Manual execution is the initial trigger; scheduling and workflow orchestration must be able to reuse the same execution boundary later.

This proposal establishes the business Task execution contract and its boundary with YakFlow. It does not change the YakFlow checkpoint protocol defined by YOIP-1. The first supported execution mode is an embedded, single-Task, single-source/single-sink pipeline. Distributed workers, Workflow DAG execution, Quartz scheduling, rescaling, and exactly-once external commits are outside this proposal.

## Public Interfaces

### Task Instance and Task Attempt

A **Task Instance** represents one requested run of a published Task Definition. It has its own stable instance identity, Workspace identity, pinned Definition ID and immutable version, Task type, trigger identity, submission/start/end timestamps, lifecycle status, and terminal failure reason where applicable. A new independent run creates a new Instance; it never rewrites the Definition.

A **Task Attempt** records one platform-level execution submission associated with an Instance. Its identity, ordinal, submission/finish timestamps, status, YakFlow JobID when known, and opaque log reference are persisted. A Runtime-internal Execution Attempt or whole-Job checkpoint restart does not itself create another business Task Attempt. The first implementation creates one platform Attempt per Instance; additional platform retries require an explicitly designed policy.

The initial externally visible Instance lifecycle is:

- SUBMITTED: accepted and durably recorded; Runtime execution may not have started.
- RUNNING: execution has been successfully submitted and is active.
- CANCELLING: a cancellation request is pending completion.
- FINISHED: Runtime and required business finalization have succeeded.
- FAILED: execution or safe submission failed.
- CANCELED: cancellation has been confirmed, including cancellation before dispatch.

Terminal states are immutable. Updates must be guarded against concurrent callbacks and stale submit/cancel results. Definition publication state is distinct from Instance state: unpublishing prevents new submissions but does not retroactively cancel existing Instances.

### Task Execution API

Propose the following HTTP operations, scoped to the authorized Workspace:

- POST /api/v1/task-definitions/{id}/instances: request execution of the currently published immutable Definition version and return the Instance ID without awaiting Job completion.
- GET /api/v1/task-instances/{id}: inspect Instance status and the pinned Definition version.
- POST /api/v1/task-instances/page: list execution Instances.
- GET /api/v1/task-instances/{id}/attempts: inspect submission attempts and log references.
- POST /api/v1/task-instances/{id}/cancel: asynchronously request cancellation.

A submission includes a caller-stable request identity. An equivalent repeated request in the same Workspace and trigger scope resolves to the same Instance, including when the existing Instance is still active or has already terminated; reuse with conflicting submission data fails. A separate intentional run uses a new request identity. Authentication, Workspace authorization, and Definition publication checks remain mandatory.

A distinct DATA_SYNC request targeting a resource held by another nonterminal Instance is rejected with an execution conflict (HTTP 409), without creating another Instance or queuing it. This admission check does not change the result of a valid idempotent replay, which returns the originally created Instance.

The HTTP response means the request has been persisted, not that the YakFlow Job has started or succeeded. API clients must not receive connection credentials, local checkpoint contents, or raw internal exception details.

### Execution Plugin Capability

TaskPlugin remains the single registration SPI. It owns Task type, parameter schema version, parsing, and resource validation. A plugin that supports execution also implements the optional `ExecutableTaskPlugin extends TaskPlugin` capability. Definition-only plugins can remain TaskPlugin implementations. TaskPluginRegistry remains the single type-keyed registry; no second execution-plugin registry is introduced.

An executable plugin starts its task with pinned TaskParameters and a trusted TaskExecutionContext, using the engine appropriate to that Task type. It returns an asynchronous, engine-neutral TaskHandle supporting a runtime reference when available, terminal completion observation, and cancellation. The handle is scoped to one execution and is not a persistent Task Instance, an execution-state store, or a replacement for engine-specific APIs. The plugin starts the physical execution but does not persist business Instances or Attempts, or decide business lifecycle transitions.

Boot is the composition root for Task plugins. It constructs plugin instances with their explicitly declared dependencies and supplies them to the existing TaskPluginRegistry(Iterable<? extends TaskPlugin>) entry point. Built-in executable plugins do not require zero-argument ServiceLoader construction, a second registration mechanism, or Spring-aware plugin API types. The active application uses a single Boot-managed registration path, retaining the Registry's duplicate-type validation.

TaskExecutionContext is immutable execution identity and metadata, including the authorized Workspace, pinned Definition version, Instance, and Attempt identities. It does not expose service lookup, ApplicationContext, PipelineExecutor, arbitrary Spring beans, or credentials. Task-type-specific runtime dependencies are injected explicitly at plugin construction. Trusted resource operations must independently enforce Workspace scope; metadata in TaskExecutionContext alone is not an authorization grant.

### YakFlow Execution Boundary

The DATA_SYNC executable plugin builds a YakFlow Pipeline using its explicitly injected runtime resources, submits it through the existing PipelineExecutor.execute(Pipeline, Configuration), and adapts the returned JobClient into a TaskHandle. The generic Task Execution platform consumes TaskHandle and does not require a YakFlow Pipeline or expose JobClient to other Task types.

YakFlow remains authoritative for JobGraph, ExecutionGraph, StreamTask state, checkpoint storage format, and runtime-internal restart. Business Controllers and Services must not manipulate ExecutionGraph or Source/Sink Task internals.

## Proposed Changes

### Ownership and dependency direction

Task Definition owns stable task identity, immutable parameter versions, publication, and validation.

Task Execution owns Instance and Attempt persistence, request deduplication, submission coordination, observation of JobClient outcomes, cancellation, and durable business status.

An executable Task plugin owns task-specific physical execution startup and returns a TaskHandle. It does not own durable Instance or Attempt state. For DATA_SYNC, the plugin translates one Source/one Sink configuration into YakFlow Source/Sink transformations, uses its injected trusted Datasource capabilities and Connector APIs, creates a StreamGraph, and invokes PipelineExecutor. The effective physical configuration is supplied by the platform and validated by Runtime, not placed in user-defined Task JSON.

YakFlow owns Job execution and checkpoint/recovery mechanics. Core remains database-neutral; Runtime must not depend on product Controllers, DAO, or business persistence.

### Submission and completion

A successful request follows this logical sequence:

1. Authorize the Workspace and the published Definition; bind its immutable current version.
2. For DATA_SYNC, resolve its supported target-table resource identity and atomically acquire the execution claim together with the Instance and first Attempt, including the caller's request identity. Reject a conflicting new request before launch.
3. Commit the business transaction and resource claim before dispatching a runtime Job.
4. Claim the committed execution request for asynchronous processing.
5. Resolve the Boot-assembled executable capability and its trusted runtime resources scoped to the persisted Workspace.
6. Ask the executable plugin to start the task and persist the returned handle's runtime reference (YakFlow JobID for DATA_SYNC) when available.
7. Observe TaskHandle completion and record a terminal Instance/Attempt outcome.

Submission is nonblocking from the HTTP client's perspective. A successfully returned execution handle is not a FINISHED Task Instance. Completion of the Runtime Job and required finalization must precede FINISHED. A failed or canceled Runtime Job cannot be recorded as a successful business execution.

The submission claim, persistence updates, and terminal callbacks must be idempotent. A crash or lost acknowledgement between starting the embedded Job and recording JobID leaves an uncertain submission: the platform must not blindly submit another Job and risk duplicate external effects. On restart it must reconcile known outcomes or fail the Instance with an explicit interruption/uncertainty reason. An unknown JobID is not evidence that no Job ran.

An embedded JobClient handle is process-local. Persisting a JobID alone does not permit a new application process to reattach to a previously running embedded ExecutionGraph. Automatic platform-level restart or resubmission after process loss is not part of this initial proposal.

### Cancellation and terminal races

Cancel-before-dispatch must prevent a later launch. Cancel-after-dispatch delegates through TaskHandle.cancel() (to JobClient.cancel() for DATA_SYNC) and observes its result. CANCELLING does not imply CANCELED; if the Runtime has already finished or failed, the actual terminal result must be preserved. An Instance must not transition from FINISHED or FAILED to CANCELED due to a late cancel request.

Cancellation does not guarantee a new checkpoint or a recoverable stopped position. A separate resume/restart operation must validate checkpoint compatibility and external delivery semantics before it can be supported.

### DATA_SYNC pipeline

The initial DATA_SYNC execution capability supports:

- A bounded JDBC Source feeding a JDBC Sink.
- A continuous MySQL CDC Source feeding a JDBC Sink.
- A single logical Source, no Transform stage, a single logical Sink, and currently supported local linear YakFlow graph shapes.
- Existing DATA_SYNC supported write modes and table/schema compatibility checks.

The Task Definition stores datasource IDs, namespaces, table selectors, and write mode; it does not store runtime JDBC credentials. A narrow, Boot-injected DATA_SYNC runtime-resource capability resolves authorized connections and table schemas using the persisted Workspace identity. TaskExecutionContext carries that identity but is not a credential or proof of authorization. Resource lookup must enforce Workspace scope without assuming that the HTTP request's thread-local Workspace state propagates to an asynchronous worker.

Operator identities used for recoverable jobs must be stable for the pinned Definition topology. Runtime-specific graph planning and checkpoint capabilities are reused rather than reimplemented in Task Execution.

### DATA_SYNC target-table execution exclusion

The initial DATA_SYNC product execution path requires **exclusive ownership of each identifiable target table** for the full active lifetime of an Instance. Concurrent runs of different Definitions targeting the same identifiable resource must not both be admitted. JDBC APPEND can duplicate records; UPSERT can race on the same keys. The exclusivity requirement applies to both supported DATA_SYNC write modes and is independent of the Task's trigger source.

The admission identity is the authorized Workspace, the saved target Datasource ID, and the resolved structured target TableId (catalog, schema, table). Namespace resolution must follow the Datasource binding and Connector/catalog rules; it must not flatten names into ambiguous strings or indiscriminately lowercase case-sensitive table identifiers. A target whose identity cannot be determined safely cannot bypass admission and start writing. Distinct Datasource IDs that point to the same physical system, or independently configured Workspaces, are not proven aliases by this identity; this proposal does not claim global physical-table isolation.

The plugin declares its target resource before producing external write effects. The Task Execution platform owns the durable claim and enforces uniqueness at the database boundary, not through an in-memory check. Instance/Attempt creation, request-idempotency handling, and admission claim acquisition form one atomic transaction. A distinct conflicting request is rejected and not queued; an idempotent retry of the existing request returns the existing Instance.

SUBMITTED, RUNNING, and CANCELLING Instances retain claims. A claim is released only by an owner-checked update after the execution is conclusively terminal and cannot continue writing. Application interruption, unknown JobID, or uncertain cancellation does not authorize time-based or speculative release. Terminal-state persistence and claim release must be atomic to prevent late callbacks releasing another Instance's claim. The initial implementation does not introduce general-purpose distributed locks, resource scheduling, or configurable concurrency quotas.

This exclusion prevents overlapping writes for identifiable targets. It is not a guarantee against duplicates from sequential APPEND runs, replay within at-least-once recovery, or target aliases outside the supported identity.

### Checkpoint, storage, and operational evidence

YakFlow owns checkpoint serialization, completion, publication, and restore semantics as defined by YOIP-1. Task Execution owns the association between an Instance, its effective configuration, and its checkpoint namespace. Independent new Instances must not share a writable checkpoint directory. A future explicit recovery for the same Instance must retain the pinned Definition version and validate stable UIDs, topology, and state compatibility before restoring.

The initial FileCheckpointStore requires durable local storage accessible to the embedded executor. A checkpoint is not an external transactional commit; supported JDBC and CDC paths retain their documented at-least-once limitations.

Each Attempt records a structured failure reason and opaque log reference. Logging and metrics can be obtained from the relevant Runtime/operations sources, but credentials, connection strings containing secrets, and sensitive state must not be included in persisted diagnostic fields or public API responses.

### Integration scope

Manual Task execution is the only required trigger for the initial delivery. Scheduling and Workflow execution can later submit through the same Task Execution API or service with their own trigger identities. They must not directly construct ExecutionGraphs. A Task Instance has no mandatory Workflow Instance ID.

The embedded Runtime is the execution target for this proposal. Worker dispatch, cluster membership, external Flink submission, distributed failover, cross-process JobClient reattachment, and Workflow dependency scheduling are separate architecture decisions.

### Questions for discussion

The design review should resolve the following before acceptance:

1. **Submission identity:** Should a caller-provided idempotency key be mandatory for manual execution, and what is the durable uniqueness scope across future scheduler/workflow triggers?
2. **Same-Definition overlap:** Beyond mandatory DATA_SYNC target-table exclusion, should the generic Task platform also limit a Definition to one active Instance when its executions use disjoint resources or a different Task type?
3. **Process failure semantics:** Is fail-closed interruption acceptable for the first embedded executor, with automatic restart/reconciliation designed separately rather than silently replaying an uncertain submission?
4. **Attempt semantics:** Should an explicitly requested restore create a new platform Attempt under the same Instance, while YakFlow internal checkpoint restarts remain within the existing Attempt?

## Compatibility, Deprecation, and Migration Plan

Task Definition IDs, immutable versions, plugin parameter schema, existing REST definition operations, and workspace isolation remain unchanged. Running Instances continue to reference the version they originally pinned after a Definition is updated or unpublished.

Add Task Instance/Attempt persistence through a new forward-only Flyway migration. Published migrations are immutable; the current task-definition-only schema remains authoritative for Definitions. No credentials, serialized live JobClient references, or Runtime-internal ExecutionGraph objects are added to the database.

The optional executable capability is additive to TaskPlugin and preserves the Registry's exact-type lookup and duplicate-type rejection. Boot will assemble built-in Task plugins with explicit dependencies instead of relying on zero-argument ServiceLoader instantiation; the active application must not register the same plugin through both paths. This registration change is part of the future implementation, not an implemented behavior of this proposal. No existing Core Connector SPI, JobClient, PipelineExecutor, YakFlow checkpoint bytes, or user Task JSON shape must change as a prerequisite to the initial product execution path. Unsupported graph shapes and incompatible recovery state must be rejected rather than silently downgraded.

The initial implementation does not claim distributed execution, exactly-once delivery, automatic process-level failover, or a guarantee that cancel performs a checkpoint.

## Test Plan

Tests must cover the complete product chain rather than infer acceptance from Runtime-only integration tests:

1. **Identity and authorization:** Published definitions can be started within the authorized Workspace. Unpublished, missing, or cross-Workspace definitions cannot be started. Instance version binding is immutable.
2. **Submission idempotency:** Duplicate equivalent request identities create one Instance/Attempt; conflicting reuse is rejected. Concurrent requests obey the same database constraint.
3. **Real bounded JDBC:** Controller to Task Definition to execution plugin to YakFlow to real JDBC Source/Sink completes; all expected data is delivered and the Instance reaches FINISHED.
4. **Real continuous CDC:** Controller to MySQL CDC to JDBC Sink processes snapshot and binlog changes, records durable checkpoint progress, remains RUNNING until canceled, then reaches a truthful terminal state.
5. **Failure reporting:** Invalid resource resolution, Pipeline planning errors, submission failure, Runtime failure, and persistence/update failures cannot produce a false FINISHED state; errors remain observable without leaking credentials.
6. **Cancellation races:** Cancel while SUBMITTED, while RUNNING, and against a concurrent Runtime terminal result. No canceled request starts a late untracked Job.
7. **Submission uncertainty:** Simulated process interruption before submission, after job start but before JobID persistence, and after terminal completion before business update. Reconciliation cannot blindly replay uncertain external writes.
8. **Checkpoint isolation:** Independent Instances use distinct checkpoint namespaces; compatible explicit restore follows pinned Definition and stable UIDs; missing or incompatible state fails clearly.
9. **Observability:** Persist Instance/Attempt identity, timestamps, JobID and log reference without connection secrets; a restart does not leave stale RUNNING indefinitely.
10. **DATA_SYNC target exclusion:** Two simultaneous requests for different DATA_SYNC Definitions writing the same identified target table admit only one Instance; the other receives a conflict without launching a Job. Distinct targets may run concurrently. Confirm APPEND and UPSERT behavior, SUBMITTED/RUNNING/CANCELLING claim retention, repeat-request idempotency, failure/cancellation terminal release, owner-checked late callbacks, and persistence across application interruption. Alias targets outside supported identity are not claimed to be protected.
11. **Plugin composition:** Boot injects each executable plugin's required dependencies and registers it once in TaskPluginRegistry; duplicate type or missing dependency fails startup. Definition-only plugins continue to work, and no runtime service lookup is available through TaskExecutionContext.
12. **Release acceptance:** Run real-database product E2E as a required executable gate. A green unit build or connector-only acceptance job is not reported as product E2E.

## Rejected Alternatives

- **Require one Workflow Instance for each Task:** Independent Task execution is a platform feature, and a Workflow is only one potential caller.
- **Submit directly from TaskDefinitionController or DefinitionService:** Definition CRUD, HTTP processing, and long-running Job execution have different ownership and transaction lifecycles.
- **Require all plugins to produce a YakFlow Pipeline:** Executable plugins may use different engines, while DATA_SYNC uses the existing YakFlow PipelineExecutor.
- **Maintain two independently registered Task plugin SPIs:** Optional execution capability uses the same TaskPluginRegistry and exact Task type.
- **Resolve runtime services through TaskExecutionContext:** Dependencies are explicit at Boot composition; a generic service locator would obscure ownership and authorization.
- **Require a no-argument ServiceLoader constructor for executable plugins:** Explicit dependencies belong to plugin construction, while Boot controls application composition.
- **Make a Task plugin own durable business execution state:** The plugin may start physical execution and return TaskHandle, but the platform owns Instance/Attempt persistence and lifecycle decisions.
- **Duplicate YakFlow execution state or checkpoint coordination in business modules:** The Job and its physical task lifecycle remain Runtime concerns.
- **Treat a persisted JobID as sufficient for embedded process recovery:** It does not restore an in-memory JobClient or prove the outcome of an uncertain external submission.
- **Automatically rerun every nonterminal Instance after application restart:** An uncertain prior Job may already have committed external effects.
- **Allow simultaneous DATA_SYNC writers to an identifiable target table:** APPEND and UPSERT can interfere across different Definitions, so an exclusive durable admission claim is required.
- **Use an in-memory target lock or timed claim expiration:** Neither protects admission across requests and restarts without risking concurrent external writes.
- **Introduce full Workflow, Quartz, Master/Worker, or distributed Job services in this proposal:** These can be layered on the stable Task execution contract when separately designed and verified.
