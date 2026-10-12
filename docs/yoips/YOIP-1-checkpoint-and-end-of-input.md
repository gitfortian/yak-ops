# YOIP-1: YakFlow Checkpoint and End-of-Input Semantics

Status: Discussion

Discussion: Pending proposal PR

Decision: Pending

Implementation: N/A until accepted

Release: N/A until released

Related FLIPs: [FLIP-27](https://cwiki.apache.org/confluence/spaces/FLINK/pages/95653746/FLIP-27+Refactor+Source+Interface), [FLIP-131](https://cwiki.apache.org/confluence/pages/viewpage.action?pageId=158866741), [FLIP-147](https://cwiki.apache.org/confluence/pages/viewpage.action?pageId=184617665)

## Motivation

YakFlow already has mailbox-driven Source and Sink tasks, aligned checkpoint barriers, durable local checkpoint storage, and end-of-input flushing. The remaining gap is the contract between **input exhaustion**, **operator completion**, **physical task termination**, and **checkpoint recovery**.

Today:

- A bounded SourceReader reports `END_OF_INPUT` after `NoMoreSplits`; the task finishes its operator chain and closes the output partition.
- An InputGate declines an in-flight checkpoint when an upstream producer finishes before delivering the required barrier. The data pipeline can still finish normally.
- The checkpoint coordinator stops periodic triggering when any Source task has finished. A completed downstream task cannot acknowledge a new checkpoint.
- The checkpoint format records Source/Reader/Operator state, but not whether an operator has finished. Normal Sink `flush(true)` is not a durable checkpoint or transactional commit.

These behaviors are appropriate safeguards for the existing local runtime, but do not provide checkpoints **after** a bounded part of a future mixed bounded/unbounded pipeline has finished. Recovery cannot safely infer whether completed work should run again.

This proposal defines the lifecycle and checkpoint rules before extending the runtime. It follows the reasoning of FLIP-147 without treating YakFlow as a distributed Flink implementation.

## Public Interfaces

- **Core connector APIs:** No change to `Source`, `SourceReader`, `SplitEnumerator`, `InputStatus`, `Sink`, or `SinkWriter`. `END_OF_INPUT` remains a Reader result, not a declaration that the Job has finished.
- **Runtime control protocol:** Distinguish a logical `EndOfData` signal from physical `EndOfPartition`. These are internal task/channel events, not new connector APIs.
- **Checkpoint persistence:** A future checkpoint format must be able to identify fully finished operators by stable operator UID, alongside the current Source, Reader, assignment, and operator state. A checkpoint containing finish markers is a new versioned format; old snapshots remain readable.
- **User-facing configuration, REST, plugin SPI, task definitions, and database schema:** No changes proposed. No new user-controlled checkpoint tuning is required.

## Proposed Changes

### Scope and lifecycle

The initial implementation target remains the **local, aligned, single-input YakFlow runtime**. Multi-source and mixed bounded/unbounded execution are motivation and future acceptance targets, not functionality claimed to exist today. This YOIP does not introduce distributed scheduling, rescaling, unaligned checkpoints, or an exactly-once Sink protocol.

The normal completion sequence is:

```text
NoMoreSplits
  -> SourceReader END_OF_INPUT after all assigned records are emitted
  -> EndOfData after the final record on each output channel
  -> downstream inputs consume their final records and all EndOfData signals
  -> operator / SinkWriter finish
  -> any required final checkpoint or commit completes
  -> EndOfPartition and physical task cleanup
  -> Job FINISHED after all tasks complete successfully
```

`NoMoreSplits` means that the enumerator will assign no more splits; it does not prove that assigned splits have finished. An operator's `finish` must run exactly once per successful execution attempt after its complete logical input. `close` releases resources; failure and cancellation must not invoke normal final flushing.

Logical `EndOfData` and physical `EndOfPartition` are separate so a task can stop accepting records while remaining alive to participate in a required final checkpoint. For today's at-least-once JDBC Sink, successful final `flush(true)` completes normal bounded output; **an unconditional final checkpoint is not required**. Any future Sink that depends on checkpoint completion to commit must wait for that checkpoint before physical termination. The commit protocol itself belongs in a separate YOIP.

### Checkpoints racing with end-of-input

A checkpoint cut is valid only when every required barrier has been accounted for, all participating operator snapshots have succeeded, and the snapshot has been durably published. A Source snapshot or Sink flush alone is not checkpoint completion.

- A barrier already ordered before `EndOfData` may complete its checkpoint if all required inputs align and acknowledge it.
- If an upstream channel reaches its terminal event without a required barrier, **decline that checkpoint**, unblock its channels, and preserve all ordinary records. EOF alone must not fail an otherwise healthy job.
- If a task finishes while checkpoint participants are being selected, the attempt must either establish a consistent cut or decline promptly. It must not wait indefinitely for an acknowledgement from a terminated task.
- Actual read/write errors, failed final `flush(true)`, invalid state, and unrecoverable serialization/storage errors remain failures, not harmless EOF declines.
- A failed or declined checkpoint must never replace the last successfully published checkpoint. Cancellation must not silently publish state or commit buffered output.

### Checkpoints after operators finish

Checkpoint participation follows **running operators**, not the assumption that every original Source task is still active.

- When an operator is fully finished, its stable UID is recorded in subsequent completed checkpoints. Recovery can then skip its execution without replaying its completed work.
- A running operator whose predecessors are all finished becomes a new checkpoint trigger root; otherwise it receives ordered barriers from its still-running predecessors. Finished input channels count as terminal for future alignment, rather than waiting for barriers they can no longer produce.
- The checkpoint coordinator must base trigger roots and completion metadata on a consistent view of task/operator state. A finish/trigger race declines only the affected checkpoint.
- A **partially finished** operator cannot be declared fully finished. Persist only state that is sufficient for correct recovery; where split ownership or state redistribution cannot be proven safe, decline the checkpoint explicitly instead of silently discarding progress.

The first code change should establish the logical/physical finish boundary for currently supported graphs. Finished-operator checkpoint triggering and partial-finish recovery should be enabled only with the necessary end-to-end tests and topology support, rather than approximated with synthetic acknowledgements.

### Durable state and recovery

The finish marker belongs to the **logical operator UID**, not the numeric JobVertex ID or a physical Task object. Fully finished operators must not be redeployed when restoring a checkpoint that explicitly marks them finished. Their downstream inputs must be reconstructed as completed; remaining active operators resume from their own saved state.

Source split discovery and completed assignment progress remain the SourceCoordinator/Enumerator's responsibility. A finished marker is not a substitute for correct, versioned Source state. Restore continues to reject incompatible graph signatures, operator UIDs, serializer versions, and unsupported parallelism changes.

Until a checkpoint format includes explicit finish metadata, old checkpoints are interpreted with their original behavior; no finished operator is inferred from missing state. A completed Job does not automatically restart merely because a checkpoint file exists.

### Delivery guarantees

Checkpoint recovery is **at-least-once** for the existing JDBC Sink. `SinkWriter.flush(false)` during a checkpoint and `flush(true)` on normal end-of-data must remain distinct lifecycle operations. Neither is a two-phase commit, and neither makes external JDBC writes exactly-once.

## Compatibility, Deprecation, and Migration Plan

No existing Core connector signatures or product persistence formats are changed by accepting this design.

Implementation must keep reading the current `FileCheckpointStore` format versions 1 and 2. Finished-operator metadata is written only by a new, explicitly versioned format with round-trip and backward-read tests; corrupted, mismatched, or unsupported state is rejected. No published checkpoint is silently rewritten, and no compatibility shim is added without a real migration need.

The currently supported local graphs and at-least-once Sink behavior remain valid. Runtime support for checkpoints after finished operators must be gated by actual supported graph and state semantics, not a configuration flag that suggests unsupported guarantees. There is no migration for REST APIs, Flyway tables, frontend configuration, or release artifacts.

## Test Plan

Existing behavior must remain covered by `CheckpointRecoveryTest`, `AlignedBarrierInputGateTest`, `ResultPartitionInputGateTest`, and `SinkWriterOperatorFailureTest`.

Required acceptance cases for implementation:

1. **Bounded completion:** All produced records reach the Sink; `finish` and final `flush(true)` occur once per successful attempt. Physical EOF follows logical finish. A failed final flush fails the job.
2. **Barrier/EOF ordering:** Checkpoint-before-EOF can finish; EOF-before-missing-barrier declines only the checkpoint. Neither path loses data, deadlocks, or publishes partial state.
3. **Failed/cancelled execution:** No success path or implicit final commit runs. A failed checkpoint preserves the previous durable snapshot.
4. **Finished operator recovery:** After full completion is recorded in a new checkpoint, restore skips that operator, does not reassign completed splits, and resumes still-running downstream work correctly.
5. **Partial completion:** A checkpoint either restores all remaining work correctly or explicitly declines when recovery semantics are unsupported; it never marks a partially finished operator as fully finished.
6. **Format compatibility:** Legacy v1/v2 checkpoints remain readable with unchanged meaning; the new format round-trips finished UIDs and rejects corrupt or incompatible metadata.
7. **Mixed bounded/unbounded pipeline:** Once such a topology is supported, periodic checkpoints continue after the bounded operator finishes. This case is a future integration gate, **not** a claim about today's runtime.

Run targeted runtime unit tests, followed by the existing Maven verification and relevant real-connector acceptance where the implementation touches persistence or Sink behavior. A proposal-only PR does not count as passing these tests.

## Rejected Alternatives

- **Stop checkpointing permanently after any Source finishes:** Keeps the current limitation and prevents bounded/unbounded mixtures from making durable progress.
- **Keep every finished Task alive indefinitely:** Wastes resources and conflates operator finish with physical task lifetime.
- **Carry final state from every finished Task into every later checkpoint:** Adds state ownership and retention complexity where a stable logical finished marker is sufficient.
- **Treat EOF as an implicit successful checkpoint:** Cannot prove all operator acknowledgements, durability, or external Sink commit.
- **Require an exactly-once/final-commit protocol for all Sinks:** Breaks the current JDBC at-least-once contract and mixes this lifecycle proposal with Sink transaction design.
- **Implement distributed, unaligned, rescaling, and mixed-source support in this first step:** Broadens the change without the corresponding runtime and acceptance foundations.
