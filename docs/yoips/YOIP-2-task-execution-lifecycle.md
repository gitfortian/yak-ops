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

A **Task Attempt** records one platform-level execution submission associated with an Instance. Its identity, ordinal, submission/finish timestamps, status, YakFlow JobID when known, and opaque log reference are persisted. A Runtime-internal Execution Attempt or whole-Job checkpoint restart does not itself create another business Task Attempt or change its JobID. The initial embedded implementation creates exactly one platform Attempt per Instance. Process interruption records the outcome of that Attempt; no second Attempt is created automatically. Any later platform-level recovery that submits a new Job requires a separately accepted contract for a new Attempt on a nonterminal Instance, and must never reopen a terminal Instance.

The initial externally visible Instance lifecycle is:

- SUBMITTED: accepted and durably recorded; Runtime execution may not have started.
- RUNNING: execution has been successfully submitted and is active.
- CANCELLING: a cancellation request is pending completion.
- RECONCILING: the prior embedded execution owner's termination cannot currently be established; the platform is reconciling ownership, not restoring or restarting a Job. No new submission or target-claim release is authorized.
- FINISHED: Runtime and required business finalization have succeeded.
- FAILED: execution or safe submission failed.
- CANCELED: cancellation has been confirmed, including cancellation before dispatch.

Terminal states are immutable. RECONCILING is nonterminal, is not a YakFlow JobStatus, and must not be confused with a healthy RUNNING Job or a future automatic RECOVERING state. Its only Stage 1 exit is to FAILED after proving the prior owner stopped and the outcome remains unknown, or to a conclusively verified terminal result if that durable evidence exists. It must not transition directly into a newly launched Job. Updates must be guarded against concurrent callbacks and stale submit/cancel results. Definition publication state is distinct from Instance state: unpublishing prevents new submissions but does not retroactively cancel existing Instances.

A Task Definition does not itself constitute an exclusive execution resource. Distinct valid submissions may create concurrent Instances for the same Definition unless their Task-type-specific declared execution resources conflict. The Task platform must not impose a generic single-active-Instance constraint or serialize all runs by Definition ID.

### Task Execution API

Propose the following HTTP operations, scoped to the authorized Workspace:

- POST /api/v1/task-definitions/{id}/instances: request execution of the currently published immutable Definition version and return the Instance ID without awaiting Job completion.
- GET /api/v1/task-instances/{id}: inspect Instance status and the pinned Definition version.
- POST /api/v1/task-instances/page: list execution Instances.
- GET /api/v1/task-instances/{id}/attempts: inspect submission attempts and log references.
- POST /api/v1/task-instances/{id}/cancel: asynchronously request cancellation.

Manual execution requires an `Idempotency-Key` HTTP header identifying one intentional submission. The UI generates a new, sufficiently unpredictable key for each new Run action and reuses that key for every network retry of the same action. A missing or invalid key is rejected (HTTP 400); one new deliberate Run action uses a new key. The header is a caller-supplied submission identity, not an authentication or authorization token.

The platform persists request identity with a database uniqueness constraint on (workspace_id, trigger_type, request_id). MANUAL, SCHEDULE, and WORKFLOW are distinct trigger namespaces; future scheduling and workflow callers must derive a stable request identity from the underlying trigger event so replay of one event remains idempotent. A normalized submission fingerprint binds the key to the requested Definition and explicit request options. For the same authorized Workspace, trigger type, and key, an equivalent retry returns the original Instance and its pinned immutable Definition version, whether SUBMITTED, active, or terminal, even if the Definition is later unpublished or changed. A conflicting payload using the same key returns HTTP 409 and never changes the existing Instance. The fingerprint must not be silently reinterpreted using the Definition's current version during a retry. Authentication and Workspace authorization are required on every request, including replay.

Idempotency lookup and new Instance/Attempt persistence must be atomic and safe under concurrent requests. The database uniqueness constraint is authoritative. A concurrent insert collision on the same equivalent key is resolved by reading and returning the committed original Instance; it is not mistaken for a separate execution conflict. No duplicate Instance, Attempt, or Runtime submission may be created by an equivalent retry.

A distinct DATA_SYNC request targeting a resource held by another nonterminal Instance is rejected with an execution conflict (HTTP 409), without creating another Instance or queuing it. A valid idempotent replay is resolved before fresh resource-admission checks, so it returns the originally created Instance instead of an erroneous target conflict.

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

1. Authenticate and authorize the Workspace, then resolve the request identity against any existing Instance before evaluating a new submission. An equivalent replay returns that Instance and pinned version; a conflicting replay fails.
2. For a new submission, authorize the published Definition and pin its immutable current version. For DATA_SYNC, resolve its supported target-table resource identity and atomically acquire its execution claim together with the new Instance, first Attempt, request identity, and immutable fingerprint. Reject conflicting new resource claims before launch.
3. Commit the business transaction and resource claim before dispatching a runtime Job.
4. Claim the committed execution request for asynchronous processing.
5. Resolve the Boot-assembled executable capability and its trusted runtime resources scoped to the persisted Workspace.
6. Ask the executable plugin to start the task and persist the returned handle's runtime reference (YakFlow JobID for DATA_SYNC) when available.
7. Observe TaskHandle completion and record a terminal Instance/Attempt outcome.

Submission is nonblocking from the HTTP client's perspective. A successfully returned execution handle is not a FINISHED Task Instance. Completion of the Runtime Job and required finalization must precede FINISHED. A failed or canceled Runtime Job cannot be recorded as a successful business execution.

The submission claim, persistence updates, and terminal callbacks must be idempotent. A crash or lost acknowledgement between starting the embedded Job and recording JobID leaves an uncertain submission: the platform must not blindly submit another Job and risk duplicate external effects. An unknown JobID is not evidence that no Job ran.

An embedded JobClient handle is process-local. Persisting a JobID alone does not permit a new application process to reattach to a previously running embedded ExecutionGraph. Initial process-interruption reconciliation and the Attempt failure boundary are defined below.

### Embedded process interruption and Attempt lifecycle

The initial local executor uses a **fail-closed process-interruption policy**. It does not implement cross-process automatic recovery, checkpoint-driven resubmission, or a second platform Attempt within the same Instance. In-process, checkpoint-backed YakFlow whole-Job restart continues to follow YOIP-1 and ExecutionOptions.MAX_RESTART_ATTEMPTS under the same platform Attempt and JobID. The Task platform must not duplicate those retries.

An accepted submission creates one Instance and one Attempt. If a live Job ultimately succeeds, fails, or is canceled, its observed TaskHandle result drives the corresponding terminal business transition. A failed Runtime Job is recorded as a failed Attempt and Instance, without an unconditional second Task submission.

At application startup, the platform reconciles persisted nonterminal Instances and their Attempts before accepting conflicting writes. An earlier embedded JobClient is not reattached by JobID. Reconciliation must establish whether the prior embedded execution owner has stopped and whether a durable terminal result was already recorded. Deployment of the initial embedded mode requires a verifiable single-active-owner assumption; concurrently active application executors are not implicitly supported.

When the previous execution owner is conclusively stopped and no durable success/cancellation result can be established, the Attempt records an interruption/unknown-outcome reason and the Instance transitions to FAILED. This is a **platform execution failure**, not proof that externally written JDBC data was rolled back. Terminal persistence and the release of DATA_SYNC target claims are atomic and owner-checked. Checkpoint data may remain available for diagnosis or a future separately specified recovery mechanism, but is not automatically replayed by the initial platform.

If the old execution owner may still be active and its status cannot be conclusively established, the Instance transitions to RECONCILING. The platform must not declare terminal success/failure, release its target claim, or dispatch another writer. RECONCILING is returned through the regular Instance query API with a safe reason that ownership cannot be verified. The original Attempt remains associated with that Instance; no second Attempt is created. A new, conflicting submission is rejected while the claim is held, while a legitimate same-key idempotent replay still returns the original RECONCILING Instance. Time-based expiry, an operator's force-release action, or a stale heartbeat alone cannot establish that external writes have stopped.

For a separate intentional run after a terminal Instance, create a new Instance and its first Attempt using a new request identity. Future automatic process recovery, if adopted, must explicitly define how a new Attempt is created while the logical Instance remains nonterminal, how prior execution is fenced, and how compatible checkpoints and external side effects are handled. No already-terminal Instance may be revived.

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

Each executable Task type may declare the resources whose simultaneous use must be exclusive; an empty declaration means that the platform does not impose a resource-based concurrency limit for that type. This is an execution-admission capability, not a second Task plugin registration SPI or a configurable generic Definition concurrency quota.

DATA_SYNC declares its resolved target table before producing external write effects. The Task Execution platform owns the durable claim and enforces uniqueness at the database boundary, not through an in-memory check. Instance/Attempt creation, request-idempotency handling, and admission claim acquisition form one atomic transaction. A distinct conflicting request is rejected and not queued; an idempotent retry of the existing request returns the existing Instance. Two independent runs of the *same* DATA_SYNC Definition are also rejected from overlapping execution when they claim the same target table; the rejection is based on their target resource, not on their shared Definition ID.

SUBMITTED, RUNNING, CANCELLING, and RECONCILING Instances retain claims. A claim is released only by an owner-checked update after the execution is conclusively terminal and cannot continue writing. Application interruption, unknown JobID, or uncertain cancellation does not authorize time-based or speculative release. Terminal-state persistence and claim release must be atomic to prevent late callbacks releasing another Instance's claim. The initial implementation does not introduce general-purpose distributed locks, resource scheduling, or configurable concurrency quotas.

This exclusion prevents overlapping writes for identifiable targets. It is not a guarantee against duplicates from sequential APPEND runs, replay within at-least-once recovery, or target aliases outside the supported identity.

### Checkpoint, storage, and operational evidence

YakFlow owns checkpoint serialization, completion, publication, and restore semantics as defined by YOIP-1. Task Execution owns the association between an Instance, its effective configuration, and its checkpoint namespace. Independent new Instances must not share a writable checkpoint directory. A future explicit recovery for the same Instance must retain the pinned Definition version and validate stable UIDs, topology, and state compatibility before restoring.

The initial FileCheckpointStore requires durable local storage accessible to the embedded executor. A checkpoint is not an external transactional commit; supported JDBC and CDC paths retain their documented at-least-once limitations. The presence of checkpoint.bin alone is insufficient evidence of an Instance's final outcome or authorization to start a replacement Job. The first-stage platform does not automatically restore or resume an Instance following application-process interruption.

Each Attempt records a structured failure reason and opaque log reference. Logging and metrics can be obtained from the relevant Runtime/operations sources, but credentials, connection strings containing secrets, and sensitive state must not be included in persisted diagnostic fields or public API responses.

### Integration scope

Manual Task execution is the only required trigger for the initial delivery. Scheduling and Workflow execution can later submit through the same Task Execution API or service with their own trigger identities. They must not directly construct ExecutionGraphs. A Task Instance has no mandatory Workflow Instance ID.

The embedded Runtime is the execution target for this proposal. Worker dispatch, cluster membership, external Flink submission, distributed failover, cross-process JobClient reattachment, and Workflow dependency scheduling are separate architecture decisions.

## Compatibility, Deprecation, and Migration Plan

Task Definition IDs, immutable versions, plugin parameter schema, existing REST definition operations, and workspace isolation remain unchanged. Running Instances continue to reference the version they originally pinned after a Definition is updated or unpublished.

Add Task Instance/Attempt persistence through a new forward-only Flyway migration. Published migrations are immutable; the current task-definition-only schema remains authoritative for Definitions. No credentials, serialized live JobClient references, or Runtime-internal ExecutionGraph objects are added to the database.

The optional executable capability is additive to TaskPlugin and preserves the Registry's exact-type lookup and duplicate-type rejection. Boot will assemble built-in Task plugins with explicit dependencies instead of relying on zero-argument ServiceLoader instantiation; the active application must not register the same plugin through both paths. This registration change is part of the future implementation, not an implemented behavior of this proposal. No existing Core Connector SPI, JobClient, PipelineExecutor, YakFlow checkpoint bytes, or user Task JSON shape must change as a prerequisite to the initial product execution path. Unsupported graph shapes and incompatible recovery state must be rejected rather than silently downgraded.

The initial implementation does not claim distributed execution, exactly-once delivery, automatic process-level failover, or a guarantee that cancel performs a checkpoint. It must enforce a single verified embedded execution owner. Restart reconciliation may terminally fail an Instance after proving the previous owner stopped; no cross-process restore or second business Attempt is implicitly scheduled. Recovery of a still-nonterminal Instance with another Attempt remains a future design.

## Test Plan

Tests must cover the complete product chain rather than infer acceptance from Runtime-only integration tests:

1. **Identity and authorization:** Published definitions can be started within the authorized Workspace. Unpublished, missing, or cross-Workspace definitions cannot be started. Instance version binding is immutable.
2. **Submission idempotency:** Manual execution requires an Idempotency-Key; missing/invalid keys receive HTTP 400. Concurrent or sequential equivalent retries in one Workspace and trigger namespace return exactly the same Instance/Attempt and pinned version, including after completion, unpublication, or a later Definition version change. Conflicting reuse receives HTTP 409. The database uniqueness constraint, stable request fingerprint, and authorized retry lookup prevent double submission; future scheduler/workflow triggers have separate namespaces and deterministic event identities.
3. **Real bounded JDBC:** Controller to Task Definition to execution plugin to YakFlow to real JDBC Source/Sink completes; all expected data is delivered and the Instance reaches FINISHED.
4. **Real continuous CDC:** Controller to MySQL CDC to JDBC Sink processes snapshot and binlog changes, records durable checkpoint progress, remains RUNNING until canceled, then reaches a truthful terminal state.
5. **Failure reporting:** Invalid resource resolution, Pipeline planning errors, submission failure, Runtime failure, and persistence/update failures cannot produce a false FINISHED state; errors remain observable without leaking credentials.
6. **Cancellation races:** Cancel while SUBMITTED, while RUNNING, and against a concurrent Runtime terminal result. No canceled request starts a late untracked Job.
7. **Submission uncertainty and process interruption:** Simulate process failure before dispatch, after Job start but before JobID persistence, and after Runtime completion but before the business terminal update. A restarted application must not replay uncertain external writes or create a second Attempt. Confirmed dead execution ownership with unknown outcome produces explicit failed Instance/Attempt evidence without claiming external rollback.
8. **Checkpoint isolation:** Independent Instances use distinct checkpoint namespaces. Completed checkpoints remain valid according to YOIP-1 but are not automatically restored or resubmitted after application restart in the initial product implementation.
9. **Observability and claim safety:** Persist Instance/Attempt identity, timestamps, JobID and log reference without secrets. Reconcile nonterminal records at startup; after proving the old embedded execution stopped, record terminal interruption and release owned target claims atomically. If execution ownership is uncertain, transition to RECONCILING, expose a safe reason in the Instance API, retain the original Attempt and target claim, and block conflicting writers. A same-key replay returns this existing Instance. No stale heartbeat or force-release control may bypass the proof of old-owner termination.
10. **DATA_SYNC target exclusion and generic concurrency:** Two simultaneous requests for different DATA_SYNC Definitions writing the same identified target table admit only one Instance; the other receives a conflict without launching a Job. Two distinct Run requests for the same DATA_SYNC Definition also conflict on that same target claim. Distinct targets may run concurrently. Verify with a separate test execution plugin that two independent Instances of the same Definition can run concurrently when no exclusive resource is declared. Confirm APPEND and UPSERT behavior, SUBMITTED/RUNNING/CANCELLING claim retention, repeat-request idempotency, failure/cancellation terminal release, owner-checked late callbacks, and persistence across application interruption. Alias targets outside supported identity are not claimed to be protected.
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
- **Treat a published checkpoint as authority to create another platform Attempt after process death:** Checkpoint publication does not prove Job termination or a safe external replay, and embedded JobClient cannot be reattached by persisted JobID.
- **Reuse a terminal business Instance for a later manual run:** Each independent intent produces a new Instance; terminal records remain immutable.
- **Allow simultaneous DATA_SYNC writers to an identifiable target table:** APPEND and UPSERT can interfere across different Definitions, so an exclusive durable admission claim is required.
- **Use an in-memory target lock or timed claim expiration:** Neither protects admission across requests and restarts without risking concurrent external writes.
- **Use Definition ID as the idempotency identity:** A successfully completed Definition must support later intentional runs; a stable request key distinguishes retries from new execution intents.
- **Treat a new Idempotency-Key as proof of safe parallel execution:** Distinct submissions still undergo DATA_SYNC resource-admission checks and may be rejected without being queued.
- **Enforce one active Instance per Task Definition globally:** A Definition is a reusable immutable configuration; exclusive access is defined by the Task type's actual execution resources, not by the Definition ID.
- **Introduce full Workflow, Quartz, Master/Worker, or distributed Job services in this proposal:** These can be layered on the stable Task execution contract when separately designed and verified.
