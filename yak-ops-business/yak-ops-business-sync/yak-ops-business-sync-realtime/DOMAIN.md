# Realtime Sync Domain Guardrails

> Scope: `yak-ops-business-sync-realtime` and any change that creates, publishes, executes, observes, serializes, or persists realtime sync tasks.
>
> This file is the **module-level mandatory domain contract**. Before changing realtime sync code, read this file and the detailed design under `docs/realtime-sync/domain/`.

## 0. Mandatory rule

**Do not start coding before performing a Domain Impact Analysis.**

Every realtime-sync change MUST first answer:

```text
1. Which bounded context owns this requirement?
2. Which aggregate is changed?
   - RealtimeSyncTask
   - DefinitionVersion
   - SyncExecution
   - none of them / adjacent context
3. If SyncDefinition changes, which part changes?
   - Endpoint
   - Route / Selector / Target / ReplayKey
   - SyncPolicy
   - ExecutionPolicy
4. Which invariant or lifecycle transition changes?
5. Is the change Domain, Application, Infrastructure, or Interface/UI?
6. Does the change introduce another source of truth for sync definition?
7. Does the change introduce a new syncType / sceneType / task subclass?
8. Does the change put Flink/YAML/SSH/JDBC credentials or adapter tuning into Core Domain?
9. Which Stage-4 migration wave does this change belong to?
10. Which existing safety properties must remain protected?
```

If items 2–4 cannot be answered, mark the requirement as:

```text
Domain Gap
```

**STOP. Extend/review the domain model before implementation. Do not add a temporary field, enum, `*Spec`, `*Task`, or `*Service` to bypass the gap.**

---

# 1. Core model: do not leave this coordinate system

Realtime Sync Core Domain has three aggregate roots:

```text
RealtimeSyncTask
DefinitionVersion
SyncExecution
```

The canonical sync configuration is the immutable value object:

```text
SyncDefinition
├── SourceEndpoint
├── SinkEndpoint
├── SyncRoute[]
├── SyncPolicy
└── ExecutionPolicy
```

The conceptual lifecycle is:

```text
RealtimeSyncTask.currentDraft
        │ publish
        ▼
DefinitionVersion (immutable)
        │ start
        ▼
SyncExecution
```

Keep the following meanings separate:

```text
RealtimeSyncTask = long-lived task identity and current draft
SyncDefinition   = what/how to synchronize
DefinitionVersion = immutable published fact
SyncExecution    = one actual run of one published version
```

**Task ≠ Definition ≠ Version ≠ Execution.**

Do not collapse them back into one giant `RealtimeJob` object.

---

# 2. One definition truth only

`SyncDefinition` is the single domain source of truth for realtime sync configuration.

The following are adapters/projections only:

```text
Wizard
Yak Realtime YAML
HTTP Request DTO
HTTP Response VO
DB JSON representation
Flink CDC Pipeline YAML
```

MUST NOT create domain truth models such as:

```text
WizardSpec
YamlSpec
FlinkSpec
MysqlSyncSpec
PostgresSyncSpec
KafkaSyncDefinition
```

A serializer/editor may have its own DTO/document model, but it MUST map to/from `SyncDefinition` and MUST NOT become a second editable business definition.

Flink Pipeline YAML MUST remain a transient compiled artifact. It MUST NOT become the persisted domain source of truth.

---

# 3. Version rules

A published version is an immutable domain fact.

MUST:

```text
Publish -> create DefinitionVersion
DefinitionVersion -> immutable after creation
Execution -> reference explicit DefinitionVersion
```

MUST NOT:

```text
publish by only toggling a marker on mutable draft content
modify a published version in place
make an Execution read the latest mutable Draft
replace historical Published content with current Draft
```

Important legacy warning:

```text
current definition_version = DraftRevision / legacy revision
current published_version  = legacy published-revision marker
```

They are NOT the final `DefinitionVersion` aggregate semantics.

If legacy Published content cannot be reconstructed reliably, do not guess and do not use the current Draft as a substitute.

---

# 4. Draft, Published, and active Execution may coexist

The following is valid and expected:

```text
Published v3
+
Draft v4 candidate
+
Execution E100(v3)
```

A newer Draft MUST NOT mutate or invalidate historical Published versions or running Executions.

A newer Draft MUST NOT automatically become the version used by an existing Execution.

Wave 4+ current rule:

```text
Active/uncertain SyncExecution
    MUST NOT block saving a newer Draft
    MUST NOT block publishing a newer DefinitionVersion
```

Save/Publish MUST NOT mutate the active Execution's:

```text
DefinitionVersionRef
RuntimeEnvironmentSnapshot
DesiredState
ObservedState
EngineExecutionRef
```

Publishing while an Execution is active only advances `RealtimeSyncTask.publishedDefinitionRef`.

Delete remains lifecycle-guarded and is NOT made safe merely because Save/Publish are now independent from execution state.

Do not model Draft/Published as a permanent mutually-exclusive domain state.

---

# 5. Execution rules

Every actual run is a new `SyncExecution`.

MUST:

```text
Start -> new Execution
RestartExecution -> new Execution pinned to the same DefinitionVersion
ApplyPublishedVersion -> separate use case that starts the explicitly captured Published version
```

MUST NOT resurrect a terminal Execution.

For one Execution:

```text
STOPPED = terminal
FAILED  = terminal
```

A later Start creates another Execution ID.

For v1, one Task may have at most one Active / Uncertain Execution:

```text
STARTING
RUNNING
STOPPING
UNKNOWN
CONFLICT
```

Do not create a second Execution while one of the above exists.

Version-changing lifecycle commands MUST NOT be used to bypass an uncertain runtime state. `RestartExecution` and `ApplyPublishedVersion` require a stable `RUNNING/RUNNING` execution in Wave 5.

---

# 6. DesiredState and ObservedState are different facts

```text
DesiredState  = control-plane/user intent
ObservedState = latest known runtime fact
```

Allowed desired values in v1:

```text
RUNNING
STOPPED
```

Observed values in v1:

```text
STARTING
RUNNING
STOPPING
STOPPED
FAILED
UNKNOWN
CONFLICT
```

Rules:

- Stop MUST persist `desired = STOPPED` before waiting for the engine to converge.
- Reconcile MUST update observed/runtime facts; it MUST NOT rewrite user intent merely to match the engine.
- `UNKNOWN` means insufficient knowledge, not failure.
- `CONFLICT` means ambiguous runtime identity; do not guess which external job is correct.
- A submission timeout with uncertain external result MUST NOT immediately become a fresh retry that can double-run the task.

Current ownership (Wave 3+): Desired/Observed lifecycle belongs to `SyncExecution`, not `RealtimeSyncTask`.

Task-row `desired_state / observed_state / last_error` are compatibility projections only. They MUST NOT be used as command truth for Start/Stop/Reconcile or for deciding whether an Execution is active.

Task row locking may remain a cross-instance command mutex; lock location does not imply lifecycle ownership.

---

# 7. RestartExecution and ApplyPublishedVersion are distinct commands

These are different domain commands and MUST remain different in Application/API/UI semantics.

```text
RestartExecution
E100(v3) -> stop -> E101(v3)
```

```text
ApplyPublishedVersion
E100(v3) -> stop -> E101(v4)
```

## 7.1 RestartExecution

Restart MUST pin the immutable `DefinitionVersionId` from the Execution being restarted.

It MUST NOT depend on the current `RealtimeSyncTask.publishedDefinitionRef`.

Therefore this is valid:

```text
E100 runs V3
Task.publishedDefinitionRef = V4
RestartExecution(E100)
  -> E101(V3)
```

The legacy `/restart` endpoint, while it exists for compatibility, MUST delegate to `RestartExecution` semantics and MUST NEVER become a restart-to-latest shortcut.

## 7.2 ApplyPublishedVersion

ApplyPublishedVersion explicitly captures `RealtimeSyncTask.publishedDefinitionRef` at command start.

That captured immutable target MUST remain pinned for the entire command. If another Publish advances the Task to V5 while an Apply of V4 is already in progress, the existing command MUST NOT drift from V4 to V5.

If the active Execution already uses the captured Published `DefinitionVersionId`, Apply MUST reject as unnecessary.

## 7.3 Preflight before Stop

Both commands MUST resolve and preflight their exact target DefinitionVersion **before** stopping the currently healthy Execution.

If target datasource/runtime/connector validation fails, the command MUST fail before `STOPPING` is persisted or an engine Stop is issued.

After target preflight succeeds, runtime Stop uncertainty continues to use the normal `STOPPING/UNKNOWN` rules; do not start a replacement Execution unless the old one is definitely terminal.

## 7.4 Version identity

Do not infer version identity from legacy `definition_version / published_version` integers.

The authoritative comparison is:

```text
SyncExecution.definitionVersionId
vs
RealtimeSyncTask.publishedDefinitionVersionId
```

The UI/query projection may expose a derived fact such as:

```text
publishedUpdateAvailable
```

but that flag MUST be derived server-side from immutable VersionIds. It is not another version source of truth.

---

# 8. Route/Selector/Policy composition comes before scene enums

Do not model product labels directly as task types.

Prefer:

```text
1 Exact Route       -> UI may call it single-table
N Exact Routes      -> UI may call it multi-table
Pattern Selector    -> rule matching
future DatabaseSelector -> whole-database capability
```

MUST NOT add without explicit domain review:

```text
syncType
sceneType
SINGLE_TABLE
MULTI_TABLE
WHOLE_DATABASE
SHARDING
MysqlRealtimeTask
KafkaRealtimeTask
```

New synchronization scenarios SHOULD first be expressed by extending:

```text
SourceSelector
SyncRoute
SinkTarget
SyncPolicy
ExecutionPolicy
```

If composition cannot express the scenario, mark `Domain Gap` and redesign the model first.

---

# 9. Replay safety is an invariant, not an optional boolean

v1 requires each route to have a non-empty `ReplayKey`.

MUST:

- ReplayKey is non-empty;
- ReplayKey fields are unique;
- contextual preflight verifies the key against the actual source uniqueness semantics supported by the current implementation;
- ambiguous routing is rejected before Publish/Start.

Core Domain MUST NOT introduce:

```text
strictReplaySafety = false
```

The current legacy boolean is a compatibility field, not a desired permanent domain policy.

---

# 10. Runtime Environment is an adjacent context

`SyncDefinition` MUST NOT own runtime-environment internals.

Correct relationship:

```text
DefinitionDraft
├── SyncDefinition
└── RuntimeEnvironmentRef

DefinitionVersion
├── SyncDefinition
└── RuntimeEnvironmentRef

SyncExecution
├── DefinitionVersionRef
└── RuntimeEnvironmentSnapshot
```

Core Domain MUST NOT contain:

```text
flinkHome
flinkCdcHome
flinkRestUrl
sshHost
sshUser
identityFile
javaHome
```

These belong to Compute/Runtime Environment and infrastructure adapters.

A physical Java/Maven package location does not change bounded-context ownership.

---

# 11. DataSource is an adjacent context

Realtime Sync stores/references datasource identity, not credentials or connection definitions.

Core Domain MUST NOT persist/copy:

```text
password
jdbcUrl
host
port
username
connectionJson
driver
```

Credentials MUST only be resolved at the submission boundary and MUST retain current short-lifetime/zeroization behavior.

Do not put datasource connectivity or password material into `SyncDefinition` for convenience.

---

# 12. Flink is Infrastructure

Flink CDC is the current engine adapter, not the realtime-sync domain.

Core Domain MUST NOT model:

```text
Flink Job as Task
Flink YAML as Definition
Flink REST status as business enum directly
flink-cdc.sh command
SSH submission mode as task type
Connector JAR path as definition field
```

Keep the translation boundary:

```text
SyncDefinition / DefinitionVersion
        ↓ Application/Compiler boundary
Compiled engine artifact
        ↓
Flink CDC Adapter
```

`EngineExecutionRef` is the engine-neutral domain/application reference. A Flink JobId is one adapter-specific external ID.

---

# 13. Adapter-private tuning must not leak into Core Domain

Only engine-neutral execution semantics belong in `ExecutionPolicy`.

Examples acceptable in Core Domain when semantics are actually supported:

```text
parallelism
checkpoint policy
restart policy
sink write batching/retry semantics
```

Adapter-private knobs MUST stay outside Core Domain, for example:

```text
statementCacheSize
connectorJarPath
flinkRestAddress
ssh options
JDBC driver-specific cache settings
```

If a Core `ExecutionPolicy` is accepted by the domain/API, the current engine adapter MUST either:

```text
apply it correctly
or
reject it explicitly during preflight
```

MUST NOT silently persist a policy that has no runtime effect.

Current known gap: checkpoint/restart settings are persisted but not fully applied by the current compiler/runtime path.

---

# 14. Validation has three layers

Do not merge all failures into “domain invalid”.

```text
1. Intrinsic Domain Validation
   - value/object invariants
   - no external I/O

2. Contextual Preflight
   - datasource catalog
   - replay-key drift
   - runtime/environment capability
   - route ambiguity against current metadata

3. Adapter/Artifact Validation
   - Flink connector support
   - compiled YAML shape
   - engine readiness/health where required
```

A source database being temporarily offline does not mutate the historical `SyncDefinition` into an intrinsically invalid object.

Product policy may require live preflight on Draft Save, but code/comments MUST call it an Application Policy, not a permanent Core Domain invariant.

---

# 15. Digest semantics must remain distinct

MUST distinguish:

```text
DefinitionDigest
= semantic canonical digest of SyncDefinition + RuntimeEnvironmentRef
```

from:

```text
ExecutionArtifactDigest
= digest of the compiled/runtime artifact used for one execution
```

Do not call both `configDigest` in new domain code.

Definition canonicalization MUST ignore ordering that has no business semantics, including at least route ordering and ReplayKey field ordering when order is not meaningful.

Do not use YAML formatting/comments/editor mode/task display name as DefinitionDigest inputs.

---

# 16. Historical evidence is append-only/immutable by default

`DefinitionVersion` and `SyncExecution` are historical facts.

MUST NOT casually hard-delete:

```text
published versions
execution history
events/audit evidence
```

Hard delete is only suitable for a never-published, never-executed, externally-unreferenced task with no active/uncertain runtime.

Otherwise prefer Archive/Tombstone semantics.

Do not cascade-delete Deployment/Execution/Event history just because the user removes a task from the normal UI.

---

# 17. Preserve the existing safety protection list

Domain refactoring MUST preserve these current safety properties unless an explicit replacement is proven equivalent or safer:

```text
Idempotency-Key
unique persistence constraint for start idempotency
DB locking / CAS around lifecycle commands
start reservation before external submit
same-key race recovery
prepared-definition/version re-check before commit/submit
stop-during-start safety
uncertain submission -> UNKNOWN
runtime identity persistence before CLI boundary
runtime job discovery/recovery
ambiguous matches -> CONFLICT
RuntimeEnvironmentSnapshot per execution
submission-scoped credentials and zeroization
secret-free persistent definition/snapshot
submission log redaction
reconcile lease for multi-instance reconciliation
```

Do not rewrite working Flink/SSH/recovery code merely to make packages look more DDD-like.

---

# 18. Stage-4 migration order is mandatory

Do not skip migration waves casually.

```text
Wave 0  New Core VOs + legacy mapper, no behavior change                     ✅
Wave 1  Immutable DefinitionVersion persistence + publish dual-write          ✅
Wave 2  Start by Published DefinitionVersion                                  ✅
Wave 3  Deployment evolves to SyncExecution; move desired/observed ownership  ✅
Wave 4  Allow editing/publishing while execution is active                     ✅
Wave 5  Separate RestartExecution from ApplyPublishedVersion                   ✅ current
Wave 6  Cleanup legacy fields/package/names                                    pending
```

Critical ordering rule:

> **Wave 4 MUST NOT be implemented before Wave 3.**

Historical reason: before Wave 3, task-row state and DAO predicates participated in lifecycle ownership. Removing only service-level Save/Publish guards at that time would have produced inconsistent concurrency semantics.

Current rule after Wave 5:

```text
Save Draft / Publish -> RealtimeSyncTask + DefinitionVersion lifecycle
Start                -> current PublishedDefinitionRef -> new SyncExecution
RestartExecution     -> current Execution VersionRef -> new same-version SyncExecution
ApplyPublishedVersion-> command-time PublishedDefinitionRef -> new versioned SyncExecution
Stop / Reconcile     -> SyncExecution lifecycle
Delete               -> requires terminal Execution and runtime safety checks
```

Database migration MUST use:

```text
expand -> dual write/read -> verify -> switch -> contract
```

Do not perform a Big-Bang rename/drop migration.

---

# 19. Current legacy names are not authoritative domain semantics

When reading existing code, remember:

```text
CdcPipelineSpec                 -> compatibility definition model
TableRoute                      -> compatibility route model
definition_version              -> DraftRevision / legacy revision
published_version               -> legacy published marker
DefinitionRow desired/observed  -> latest SyncExecution compatibility projection
DeploymentRow                   -> SyncExecution persistence compatibility row
configDigest on definition      -> draft DefinitionDigest-like value
configDigest on deployment      -> ExecutionArtifactDigest
ReleaseState DRAFT/PUBLISHED    -> legacy/derived presentation state
legacy /restart endpoint        -> compatibility alias for RestartExecution
```

Do not compare legacy `definition_version / published_version` as if they were immutable Domain `DefinitionVersionId` values.

Do not infer the target domain model solely from current class/table/field names.

The accepted domain documents have higher semantic authority than legacy naming.

---

# 20. AI coding contract

Before proposing code changes, the AI MUST produce a short block like:

```text
Domain Impact Analysis
- Bounded context:
- Aggregate(s):
- SyncDefinition area (if any):
- Invariant/lifecycle impact:
- Layer: Domain / Application / Infrastructure / Interface
- Existing Stage-4 mapping/gap:
- Migration wave:
- Safety properties to preserve:
- Domain Gap: yes/no
```

If `Domain Gap: yes`, implementation MUST stop at domain-design proposal unless the user explicitly approves the domain extension.

After implementation, the AI MUST report:

```text
- Which domain rule was implemented
- Which legacy compatibility behavior remains
- Which protection-list tests were added/preserved
- Whether DB dual-read/dual-write is involved
- Which known gaps remain
```

Do not claim “domain-compliant” merely because class names were renamed.

---

# 21. Review rejection triggers

A realtime-sync PR should be rejected or returned for domain review if it introduces any of the following without an explicit domain decision:

```text
new WizardSpec / YamlSpec / engine-specific domain Spec
new syncType / sceneType for a UI scenario
Flink/SSH/JDBC credential fields in Core Domain
Execution reading current Draft
in-place mutation of Published Version
Restart that may silently upgrade version
ApplyPublishedVersion that re-reads a newer Published target after command start
target-version command that stops a healthy Execution before target preflight succeeds
second active Execution while UNKNOWN/CONFLICT exists
UNKNOWN treated as FAILED solely to permit retry
hard deletion of execution/version/audit history
adapter-private tuning added to SyncDefinition
accepted ExecutionPolicy silently ignored by runtime adapter
business rules changed only in Service but not corresponding DAO CAS/DB constraints
Big-Bang schema rename/drop before migration switch is verified
```

---

# 22. Source documents

Detailed accepted design lives at:

```text
docs/realtime-sync/domain/01-domain-boundary-and-language.md
docs/realtime-sync/domain/02-core-domain-model.md
docs/realtime-sync/domain/03-invariants-and-lifecycle.md
docs/realtime-sync/domain/04-current-code-mapping.md
docs/realtime-sync/domain/05-ai-domain-rules.md
```

Conflict resolution order for realtime-sync design intent:

```text
1. Explicit newest approved user/domain decision
2. DOMAIN.md hard guardrails
3. Accepted docs/realtime-sync/domain/* design
4. Tests that encode approved invariants
5. Current implementation details / legacy names
```

Current implementation is evidence of behavior and compatibility requirements; it is not automatically the source of truth for target domain semantics.
