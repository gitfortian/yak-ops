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

A submission includes a caller-stable request identity. An equivalent repeated request in the same Workspace and trigger scope resolves to the same Instance; reuse with conflicting submission data fails. A separate intentional run uses a new request identity. Authentication, Workspace authorization, and Definition publication checks remain mandatory.

The HTTP response means the request has been persisted, not that the YakFlow Job has started or succeeded. API clients must not receive connection credentials, local checkpoint contents, or raw internal exception details.

### Execution Plugin Capability

The existing TaskPlugin contract continues to own Task type, parameter schema version, parsing, and resource validation. Its methods are not repurposed for Job submission or state management.

Introduce a task-type-specific execution capability that builds a YakFlow Pipeline using pinned Task parameters and a trusted execution context. The execution capability does not create Task Instances, submit the Pipeline, or own the Job lifecycle. The proposal favors a separate execution SPI over adding stateful execute methods to TaskPlugin. Exact interface names and registration mechanics remain an implementation detail unless they affect plugin interoperability.

The execution context carries explicit authorized Workspace and Instance identity, resource access, and effective execution configuration. Credentials and connections are resolved at the trusted execution boundary, not serialized into Task parameters, Instance persistence, or log records.

### YakFlow Execution Boundary

Use the existing PipelineExecutor.execute(Pipeline, Configuration) to submit the compiled Pipeline and JobClient to observe JobID, JobStatus, cancellation, and successful completion or exceptional result.

YakFlow remains authoritative for JobGraph, ExecutionGraph, StreamTask state, checkpoint storage format, and runtime-internal restart. Business Controllers and Services must not manipulate ExecutionGraph or Source/Sink Task internals.

## Proposed Changes

### Ownership and dependency direction

Task Definition owns stable task identity, immutable parameter versions, publication, and validation.

Task Execution owns Instance and Attempt persistence, request deduplication, submission coordination, observation of JobClient outcomes, cancellation, and durable business status.

The Task execution plugin owns type-specific Pipeline construction. For DATA_SYNC, it translates one Source/one Sink configuration into YakFlow Source/Sink transformations, uses trusted Datasource metadata and Connector APIs, and produces a StreamGraph. The effective physical configuration is supplied by the platform and validated by Runtime, not placed in user-defined Task JSON.

YakFlow owns Job execution and checkpoint/recovery mechanics. Core remains database-neutral; Runtime must not depend on product Controllers, DAO, or business persistence.

### Submission and completion

A successful request follows this logical sequence:

1. Authorize the Workspace and the published Definition; bind its immutable current version.
2. Atomically persist the Instance and the first Attempt, including the caller's request identity.
3. Commit the business transaction before dispatching a runtime Job.
4. Claim the committed execution request for asynchronous processing.
5. Resolve authorized runtime resources, validate current resource availability, and construct the Pipeline.
6. Submit through PipelineExecutor and persist the returned JobID on the Attempt.
7. Observe JobClient completion and record a terminal Instance/Attempt outcome.

Submission is nonblocking from the HTTP client's perspective. A successful JobClient submission is not a FINISHED Task Instance. Completion of the Runtime Job and required finalization must precede FINISHED. A failed or canceled Runtime Job cannot be recorded as a successful business execution.

The submission claim, persistence updates, and terminal callbacks must be idempotent. A crash or lost acknowledgement between starting the embedded Job and recording JobID leaves an uncertain submission: the platform must not blindly submit another Job and risk duplicate external effects. On restart it must reconcile known outcomes or fail the Instance with an explicit interruption/uncertainty reason. An unknown JobID is not evidence that no Job ran.

An embedded JobClient handle is process-local. Persisting a JobID alone does not permit a new application process to reattach to a previously running embedded ExecutionGraph. Automatic platform-level restart or resubmission after process loss is not part of this initial proposal.

### Cancellation and terminal races

Cancel-before-dispatch must prevent a later launch. Cancel-after-dispatch delegates to JobClient.cancel() and observes its result. CANCELLING does not imply CANCELED; if the Runtime has already finished or failed, the actual terminal result must be preserved. An Instance must not transition from FINISHED or FAILED to CANCELED due to a late cancel request.

Cancellation does not guarantee a new checkpoint or a recoverable stopped position. A separate resume/restart operation must validate checkpoint compatibility and external delivery semantics before it can be supported.

### DATA_SYNC pipeline

The initial DATA_SYNC execution capability supports:

- A bounded JDBC Source feeding a JDBC Sink.
- A continuous MySQL CDC Source feeding a JDBC Sink.
- A single logical Source, no Transform stage, a single logical Sink, and currently supported local linear YakFlow graph shapes.
- Existing DATA_SYNC supported write modes and table/schema compatibility checks.

The Task Definition stores datasource IDs, namespaces, table selectors, and write mode; it does not store runtime JDBC credentials. The execution context explicitly scopes Datasource resolution to the persisted Workspace, including when work runs outside the HTTP request thread. Thread-local request Workspace state must not be assumed to propagate to an asynchronous worker.

Operator identities used for recoverable jobs must be stable for the pinned Definition topology. Runtime-specific graph planning and checkpoint capabilities are reused rather than reimplemented in Task Execution.

### Checkpoint, storage, and operational evidence

YakFlow owns checkpoint serialization, completion, publication, and restore semantics as defined by YOIP-1. Task Execution owns the association between an Instance, its effective configuration, and its checkpoint namespace. Independent new Instances must not share a writable checkpoint directory. A future explicit recovery for the same Instance must retain the pinned Definition version and validate stable UIDs, topology, and state compatibility before restoring.

The initial FileCheckpointStore requires durable local storage accessible to the embedded executor. A checkpoint is not an external transactional commit; supported JDBC and CDC paths retain their documented at-least-once limitations.

Each Attempt records a structured failure reason and opaque log reference. Logging and metrics can be obtained from the relevant Runtime/operations sources, but credentials, connection strings containing secrets, and sensitive state must not be included in persisted diagnostic fields or public API responses.

### Integration scope

Manual Task execution is the only required trigger for the initial delivery. Scheduling and Workflow execution can later submit through the same Task Execution API or service with their own trigger identities. They must not directly construct ExecutionGraphs. A Task Instance has no mandatory Workflow Instance ID.

The embedded Runtime is the execution target for this proposal. Worker dispatch, cluster membership, external Flink submission, distributed failover, cross-process JobClient reattachment, and Workflow dependency scheduling are separate architecture decisions.

### Questions for discussion

The design review should resolve the following before acceptance:

1. **Execution plugin boundary:** Should Pipeline construction use a separate SPI keyed by Task type, or an optional capability on TaskPlugin? Preserve the distinction between parameter validation, planning, and execution regardless of the form.
2. **Submission identity:** Should a caller-provided idempotency key be mandatory for manual execution, and what is the durable uniqueness scope across future scheduler/workflow triggers?
3. **Overlapping runs:** Should the initial platform permit multiple concurrent Instances for the same Definition, or reject a second active DATA_SYNC run until target-write conflicts have an explicit policy?
4. **Process failure semantics:** Is fail-closed interruption acceptable for the first embedded executor, with automatic restart/reconciliation designed separately rather than silently replaying an uncertain submission?
5. **Attempt semantics:** Should an explicitly requested restore create a new platform Attempt under the same Instance, while YakFlow internal checkpoint restarts remain within the existing Attempt?

## Compatibility, Deprecation, and Migration Plan

Task Definition IDs, immutable versions, plugin parameter schema, existing REST definition operations, and workspace isolation remain unchanged. Running Instances continue to reference the version they originally pinned after a Definition is updated or unpublished.

Add Task Instance/Attempt persistence through a new forward-only Flyway migration. Published migrations are immutable; the current task-definition-only schema remains authoritative for Definitions. No credentials, serialized live JobClient references, or Runtime-internal ExecutionGraph objects are added to the database.

The Task execution capability is additive. No existing Core Connector SPI, JobClient, PipelineExecutor, YakFlow checkpoint bytes, or user Task JSON shape must change as a prerequisite to the initial product execution path. Unsupported graph shapes and incompatible recovery state must be rejected rather than silently downgraded.

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
10. **Release acceptance:** Run real-database product E2E as a required executable gate. A green unit build or connector-only acceptance job is not reported as product E2E.

## Rejected Alternatives

- **Require one Workflow Instance for each Task:** Independent Task execution is a platform feature, and a Workflow is only one potential caller.
- **Submit directly from TaskDefinitionController or DefinitionService:** Definition CRUD, HTTP processing, and long-running Job execution have different ownership and transaction lifecycles.
- **Make TaskPlugin own Job submission and mutable execution state:** Definition parsing/validation and Pipeline preparation must remain separate from Instance/Attempt lifecycle ownership.
- **Duplicate YakFlow execution state or checkpoint coordination in business modules:** The Job and its physical task lifecycle remain Runtime concerns.
- **Treat a persisted JobID as sufficient for embedded process recovery:** It does not restore an in-memory JobClient or prove the outcome of an uncertain external submission.
- **Automatically rerun every nonterminal Instance after application restart:** An uncertain prior Job may already have committed external effects.
- **Introduce full Workflow, Quartz, Master/Worker, or distributed Job services in this proposal:** These can be layered on the stable Task execution contract when separately designed and verified.
