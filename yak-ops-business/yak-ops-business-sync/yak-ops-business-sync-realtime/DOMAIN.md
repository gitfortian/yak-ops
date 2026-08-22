# Realtime Sync Domain Guardrails

> Scope: `yak-ops-business-sync-realtime` and any change that creates, publishes, executes, observes, serializes, or persists realtime sync tasks.
>
> This file is the **module-level mandatory domain contract**. Before changing realtime-sync code, read this file and `docs/realtime-sync/domain/`.

## 0. Mandatory Domain Impact Analysis

**Do not start coding before performing a Domain Impact Analysis.**

Every realtime-sync change MUST first answer:

```text
1. Which bounded context owns this requirement?
2. Which aggregate is changed?
   - RealtimeSyncTask
   - DefinitionVersion
   - SyncExecution
   - adjacent context / none
3. If SyncDefinition changes, which part changes?
   - Endpoint
   - Route / Selector / Target / ReplayKey
   - SyncPolicy
   - ExecutionPolicy
4. Which invariant or lifecycle transition changes?
5. Is the change Domain, Application, Infrastructure, or Interface/UI?
6. Does it create another editable definition source of truth?
7. Does it introduce a new syncType / sceneType / task subclass?
8. Does it leak Flink/YAML/SSH/JDBC credentials or adapter tuning into Core Domain?
9. Which accepted migration/current implementation boundary is affected?
10. Which safety properties must remain protected?
```

If items 2–4 cannot be answered, mark:

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

Lifecycle coordinate:

```text
RealtimeSyncTask.currentDraft
        │ publish
        ▼
DefinitionVersion (immutable)
        │ start
        ▼
SyncExecution
```

Meanings MUST remain separate:

```text
RealtimeSyncTask  = long-lived task identity + current Draft + PublishedDefinitionRef
SyncDefinition    = what/how to synchronize
DefinitionVersion = immutable published fact
SyncExecution     = one actual run of one immutable published version
```

**Task ≠ Definition ≠ Version ≠ Execution.**

Do not collapse them back into one giant `RealtimeJob` model.

---

# 2. One definition truth only

`SyncDefinition` is the single domain source of truth for realtime sync configuration.

These are adapters/projections only:

```text
Wizard
Yak Realtime YAML
HTTP DTO / VO
DB JSON representation
Flink CDC Pipeline YAML
```

MUST NOT create editable domain truths such as:

```text
WizardSpec
YamlSpec
FlinkSpec
MysqlSyncSpec
PostgresSyncSpec
KafkaSyncDefinition
```

A serializer/editor may have a document DTO, but it MUST map to/from `SyncDefinition` and MUST NOT become a second business definition.

Flink Pipeline YAML is a transient compiled artifact. It MUST NOT become persisted domain truth.

---

# 3. Version rules

A published version is an immutable domain fact.

MUST:

```text
Publish -> create/reuse immutable DefinitionVersion
DefinitionVersion -> immutable after creation
Execution -> reference explicit DefinitionVersionId
```

MUST NOT:

```text
publish by only toggling a marker on mutable Draft content
modify a Published DefinitionVersion in place
make an Execution read the latest mutable Draft
replace historical Published content with current Draft
```

Legacy warning:

```text
definition_version = DraftRevision compatibility field
published_version  = published DraftRevision compatibility marker
```

They are **not** immutable DefinitionVersion identity.

Authoritative identity is:

```text
published_definition_version_id
execution.definition_version_id
```

If historical Published content cannot be reconstructed reliably, do not guess and do not use the current Draft as a substitute.

---

# 4. Draft, Published, and active Execution may coexist

This is valid and expected:

```text
Published V3
+
Draft r4 candidate
+
Execution E100(V3)
```

Active/uncertain SyncExecution MUST NOT block:

```text
Save newer Draft
Publish newer DefinitionVersion
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

Delete remains lifecycle-guarded. Definition/Execution independence does not make active-runtime deletion safe.

---

# 5. Execution rules

Every actual run is a new `SyncExecution`.

MUST:

```text
Start -> new Execution
RestartExecution -> new Execution pinned to the same DefinitionVersion
ApplyPublishedVersion -> new Execution pinned to the explicitly captured Published version
```

MUST NOT resurrect a terminal Execution.

For one Execution:

```text
STOPPED = terminal
FAILED  = terminal
```

For v1, one Task may have at most one Active / Uncertain Execution:

```text
STARTING
RUNNING
STOPPING
UNKNOWN
CONFLICT
```

Do not create a second Execution while one of the above exists.

Version-changing/replacement commands MUST NOT bypass uncertain runtime state. `RestartExecution` and `ApplyPublishedVersion` require a stable `RUNNING/RUNNING` Execution with a bound EngineExecutionRef.

---

# 6. DesiredState and ObservedState belong to SyncExecution

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

- Stop MUST persist `desired = STOPPED` before waiting for engine convergence.
- Reconcile updates observed/runtime facts; it MUST NOT rewrite user intent merely to match the engine.
- `UNKNOWN` means insufficient knowledge, not failure.
- `CONFLICT` means ambiguous runtime identity; do not guess.
- uncertain external submission MUST NOT become a fresh retry that can double-run the task.

## Wave 6 current ownership

Runtime lifecycle truth is **SyncExecution only**.

Physical Task columns:

```text
yak_realtime_job_definition.desired_state
yak_realtime_job_definition.observed_state
yak_realtime_job_definition.last_error
```

are now **inert compatibility storage**.

MUST NOT:

```text
dual-write Execution lifecycle into those Task columns
read those Task columns as Application command truth
fallback API runtime state to those Task columns
reintroduce desiredJobs / hasOtherDesiredRunning style Task-runtime queries
```

Read model rule:

```text
latest SyncExecution exists -> derive desired/observed/error from that Execution
no SyncExecution            -> STOPPED / STOPPED / null
```

Task row locking may remain a cross-instance command mutex; lock location does not imply lifecycle ownership.

---

# 7. RestartExecution and ApplyPublishedVersion are distinct commands

These commands MUST remain different in Application/API/UI semantics.

```text
RestartExecution
E100(V3) -> stop -> E101(V3)
```

```text
ApplyPublishedVersion
E100(V3) -> stop -> E101(V4)
```

## 7.1 RestartExecution

Target MUST come from:

```text
current SyncExecution.definitionVersionId
```

It MUST NOT depend on current `Task.publishedDefinitionRef`.

Therefore this is valid:

```text
E100 runs V3
Task.publishedDefinitionRef = V4
RestartExecution(E100)
  -> E101(V3)
```

## 7.2 ApplyPublishedVersion

Target MUST be captured from `Task.publishedDefinitionRef` at command start.

That immutable target remains pinned for the command. If another Publish advances to V5 while Apply V4 is in progress, the existing command MUST remain V4.

If the active Execution already uses the captured VersionId, Apply MUST reject as unnecessary.

## 7.3 Preflight before Stop

Both commands MUST resolve/validate/compile their exact target DefinitionVersion **before** stopping a healthy Execution.

If target datasource/runtime/connector validation fails, the old Execution stays running.

After preflight, a DB-lock replacement-stop reservation MUST re-check the same stable Execution before persisting STOPPING. A competing Stop/Restart/Apply that already won makes the later command fail.

## 7.4 Compatibility `/restart`

The HTTP v1 `/restart` endpoint may remain as an external compatibility alias, but it MUST delegate to `RestartExecution` semantics.

Internal Application/UI code MUST NOT use a generic `restart-to-latest` action.

---

# 8. Route/Selector/Policy composition comes before scene enums

Do not model product labels directly as Task types.

Prefer:

```text
1 Exact Route            -> UI may call it single-table
N Exact Routes           -> UI may call it multi-table
Pattern Selector         -> rule matching
future DatabaseSelector  -> whole-database capability
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

New scenarios SHOULD first extend:

```text
SourceSelector
SyncRoute
SinkTarget
SyncPolicy
ExecutionPolicy
```

If composition cannot express the scenario, mark `Domain Gap` first.

---

# 9. Replay safety is an invariant

v1 requires each route to have a non-empty `ReplayKey`.

MUST:

- ReplayKey is non-empty;
- ReplayKey fields are unique;
- contextual preflight verifies actual source uniqueness semantics supported by the implementation;
- ambiguous routing is rejected before Publish/Start.

Core Domain MUST NOT introduce:

```text
strictReplaySafety = false
```

The legacy boolean is a compatibility field, not a desired Core policy.

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

Credentials are resolved at the submission boundary and MUST retain short lifetime / zeroization behavior.

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

Keep translation boundary:

```text
SyncDefinition / DefinitionVersion
        ↓ Application/Compiler
Compiled engine artifact
        ↓
Flink CDC Adapter
```

`EngineExecutionRef` is the engine-neutral reference. A Flink JobId is one adapter-specific external ID.

---

# 13. Adapter-private tuning must not leak into Core Domain

Only engine-neutral execution semantics belong in `ExecutionPolicy`.

Adapter-private knobs stay outside Core Domain, including:

```text
statementCacheSize
connectorJarPath
flinkRestAddress
ssh options
JDBC driver-specific cache settings
```

If Core `ExecutionPolicy` accepts a setting, the current engine adapter MUST either:

```text
apply it correctly
or
reject it explicitly during preflight
```

MUST NOT silently persist a policy with no runtime effect.

Known gap: checkpoint/restart settings are not yet fully applied by the compiler/runtime path.

---

# 14. Validation has three layers

Do not merge all failures into “domain invalid”.

```text
1. Intrinsic Domain Validation
   - object/value invariants
   - no external I/O

2. Contextual Preflight
   - datasource catalog
   - replay-key drift
   - runtime/environment capability
   - route ambiguity against current metadata

3. Adapter/Artifact Validation
   - Flink connector support
   - compiled YAML shape
   - engine readiness where required
```

A source DB being temporarily offline does not mutate historical `SyncDefinition` into an intrinsically invalid object.

---

# 15. Digest semantics must remain distinct

MUST distinguish:

```text
DefinitionDigest
= semantic canonical digest of SyncDefinition + RuntimeEnvironmentRef

sourceConfigDigest
= exact compatibility digest used for mutable Draft / publish CAS

ExecutionArtifactDigest / artifactDigest
= digest of compiled runtime artifact for one Execution
```

Physical schema may still contain two columns both named `config_digest`. That physical name does **not** collapse the semantics.

New Application/Domain code MUST use semantic names such as:

```text
sourceConfigDigest
artifactDigest
DefinitionDigest
```

and MUST NOT introduce a new ambiguous generic digest concept.

Definition canonicalization MUST ignore ordering with no business semantics, including route order and ReplayKey field order where order is not meaningful.

Do not use YAML comments/formatting/editor mode/task display name as DefinitionDigest inputs.

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

Current hard-delete behavior remains a known gap; do not treat Stage 6 cleanup as solving it.

---

# 17. Preserve the safety protection list

Refactoring MUST preserve these safety properties unless an explicit replacement is proven equivalent or safer:

```text
Idempotency-Key
unique persistence constraint for start idempotency
DB locking / CAS around lifecycle commands
start reservation before external submit
same-key race recovery
prepared-definition/version re-check
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
replacement-stop reservation for Restart/Apply
```

Do not rewrite working Flink/SSH/recovery code merely to make packages look more DDD-like.

---

# 18. Stage 6 migration is complete

Accepted migration sequence:

```text
Wave 0  Core VOs + compatibility mapper                                ✅
Wave 1  Immutable DefinitionVersion persistence                         ✅
Wave 2  Start by Published DefinitionVersion                            ✅
Wave 3  SyncExecution owns desired/observed lifecycle                   ✅
Wave 4  Active Execution no longer blocks Draft Save / Publish          ✅
Wave 5  RestartExecution / ApplyPublishedVersion explicit split          ✅
Wave 6  Contract cleanup / legacy runtime projection isolation           ✅
```

Current command ownership:

```text
Save Draft             -> RealtimeSyncTask.currentDraft
Publish                -> DefinitionVersion + Task.publishedDefinitionRef
Start                  -> command-time PublishedDefinitionRef -> new SyncExecution
RestartExecution       -> current Execution VersionRef -> new same-version Execution
ApplyPublishedVersion  -> command-time PublishedDefinitionRef -> new versioned Execution
Stop / Reconcile       -> SyncExecution lifecycle
Delete                 -> terminal Execution + runtime safety guard
```

## 18.1 Contract-but-not-drop

Stage 6 does **not** perform a Big-Bang physical schema/API rename.

These physical/v1 names may remain temporarily:

```text
yak_realtime_job_definition
yak_realtime_job_deployment
Task desired_state / observed_state / last_error
Deployment status
Definition/Deployment config_digest
definition_version / published_version
latestDeployment JSON
legacy HTTP /restart
```

But existing compatibility names MUST NOT be used to infer domain ownership.

Database/API contract removal must remain incremental:

```text
expand -> switch readers/writers -> verify -> contract
```

Never edit/drop an already-applied schema merely to make names look cleaner.

---

# 19. Current legacy-name mapping

When reading current code/schema, interpret these names as follows:

```text
CdcPipelineSpec
  -> compatibility definition representation; SyncDefinition is Core truth

TableRoute
  -> compatibility route representation

definition_version
  -> DraftRevision compatibility field

published_version
  -> published DraftRevision compatibility marker

published_definition_version_id
  -> immutable Published DefinitionVersion identity

DefinitionRow desired/observed/lastError
  -> inert compatibility values; not runtime truth

RealtimeJobDeploymentPO / DeploymentRow
  -> SyncExecution persistence compatibility row

Deployment.status
  -> physical compatibility mirror; not lifecycle truth

DefinitionRow.configDigest
  -> sourceConfigDigest compatibility storage

DeploymentRow.configDigest
  -> ExecutionArtifactDigest compatibility storage

latestDeployment JSON
  -> latest SyncExecution read projection

ReleaseState DRAFT/PUBLISHED
  -> legacy/derived presentation state

HTTP /restart
  -> compatibility alias for RestartExecution only
```

Do not compare `definition_version / published_version` as immutable VersionIds.

Do not introduce a new dependency on inert Task runtime columns.

Accepted domain documents have higher semantic authority than legacy naming.

---

# 20. Known gaps after Stage 6

Stage 6 completion does not mean all architecture work is finished.

Keep these as explicit independent gaps:

```text
Audit-safe Archive/Tombstone delete
ExecutionPolicy checkpoint/restart runtime application
Flink FINISHED normal completion / snapshot-only
legacy failure-rate mapping
Read-model package hygiene
Compute Environment physical context/package cleanup
API v2 / physical schema naming cleanup
```

Do not sneak these changes into unrelated feature PRs. Each needs its own Domain Impact Analysis.

---

# 21. AI coding contract

Before proposing code changes, AI MUST output:

```text
Domain Impact Analysis
- Bounded context
- Aggregate(s)
- SyncDefinition area (if any)
- Invariant/lifecycle impact
- Layer
- Current mapping/gap
- Safety properties to preserve
- Domain Gap: yes/no
```

If `Domain Gap: yes`, implementation stops at domain-design proposal unless the user explicitly approves the extension.

After implementation, report:

```text
- Domain rule implemented
- Aggregates changed
- Invariants/lifecycle affected
- Legacy compatibility retained
- Safety properties preserved
- DB/API migration mode
- Tests added/updated
- Known gaps remaining
```

Do not claim “domain-compliant” merely because class names were renamed.

---

# 22. Review rejection triggers

Return a realtime-sync PR for domain review if it introduces any of these without an explicit decision:

```text
new WizardSpec / YamlSpec / engine-specific domain Spec
new syncType / sceneType for a UI scenario
Flink/SSH/JDBC credential fields in Core Domain
Execution reading current Draft
in-place mutation of Published Version
Restart that may silently upgrade version
ApplyPublishedVersion that re-reads a newer target after command start
target-version command that stops healthy Execution before target preflight
second active Execution while UNKNOWN/CONFLICT exists
UNKNOWN treated as FAILED solely to permit retry
new read/write dependency on Task runtime compatibility columns
Deployment.status used as lifecycle truth
legacy revision integer used as DefinitionVersion identity
ambiguous generic configDigest introduced into new domain/application code
hard deletion of execution/version/audit history presented as a harmless cleanup
adapter-private tuning added to SyncDefinition
accepted ExecutionPolicy silently ignored by runtime adapter
Big-Bang schema/API rename/drop without migration verification
```

---

# 23. Source documents

Detailed accepted design/current migration status:

```text
docs/realtime-sync/domain/01-domain-boundary-and-language.md
docs/realtime-sync/domain/02-core-domain-model.md
docs/realtime-sync/domain/03-invariants-and-lifecycle.md
docs/realtime-sync/domain/04-current-code-mapping.md       # historical mapping snapshot
docs/realtime-sync/domain/05-ai-domain-rules.md
docs/realtime-sync/domain/06-stage6-migration-completion.md # current Stage 6 implementation facts
```

Conflict resolution order:

```text
1. Explicit newest approved user/domain decision
2. DOMAIN.md hard guardrails
3. 06-stage6-migration-completion.md current implementation facts
4. Accepted domain design docs
5. Tests encoding approved invariants
6. Current legacy names / physical schema details
```

Current implementation is compatibility evidence; legacy naming is not automatically domain truth.
