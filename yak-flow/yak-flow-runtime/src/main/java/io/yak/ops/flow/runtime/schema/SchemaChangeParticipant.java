package io.yak.ops.flow.runtime.schema;

import io.yak.ops.core.event.SchemaChangeRequest;
import java.util.concurrent.CompletionStage;

/** Mailbox-acknowledged operations performed by one physical input task during schema coordination. */
public interface SchemaChangeParticipant {

    /** Validates local Writer support and the next immutable write plan before any DDL runs. */
    CompletionStage<Void> validateSchemaChange(SchemaChangeRequest request);

    /** Drains records already emitted before the source schema event from this task's input. */
    CompletionStage<Void> drainForSchemaChange();

    /** Flushes this task's Writer after all upstream input has been drained. */
    CompletionStage<Void> prepareSchemaChange(SchemaChangeRequest request);

    /** Adopts the new immutable Writer plan after target DDL is applied and verified. */
    CompletionStage<Void> commitSchemaChange(SchemaChangeRequest request, long schemaVersion);
}
