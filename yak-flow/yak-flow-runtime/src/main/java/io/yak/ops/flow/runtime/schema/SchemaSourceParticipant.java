package io.yak.ops.flow.runtime.schema;

import java.util.concurrent.CompletionStage;

/** Source-side pause gate used to hold post-change records until schema coordination is committed. */
public interface SchemaSourceParticipant {

    /** Returns the stable zero-based Source subtask index. */
    int subtaskIndex();

    /** Stops further Reader polling at a mailbox boundary. */
    CompletionStage<Void> pauseForSchemaChange();

    /** Releases the Source only after target DDL and every Writer schema plan are committed. */
    CompletionStage<Void> resumeAfterSchemaChange();
}
