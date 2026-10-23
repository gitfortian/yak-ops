package io.yak.ops.core.api.connector.sink;

import io.yak.ops.core.schema.SchemaVersionedTableState;
import java.util.List;

/**
 * Optional capability for a Sink whose runtime can coordinate schema changes.
 *
 * <p>Implementations create a task-attempt-owned MetadataApplier. The runtime never infers
 * schema evolution support from the existence of a JDBC connection or a DDL dialect.
 */
public interface SchemaEvolutionSink {

    /** Creates an independent metadata applier owned by the active Job attempt. */
    MetadataApplier createMetadataApplier() throws Exception;

    /**
     * Restores the committed schema version before any Writer is created.
     *
     * <p>The default is fail-closed: a Sink must explicitly implement restoration if the
     * checkpoint contains evolved schema state.
     */
    default void restoreSchemaState(List<SchemaVersionedTableState> states) throws Exception {
        if (!List.copyOf(states).isEmpty()) {
            throw new UnsupportedOperationException("Sink cannot restore evolved schema state");
        }
    }
}
