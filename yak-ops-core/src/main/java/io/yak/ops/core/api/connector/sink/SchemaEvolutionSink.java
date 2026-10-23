package io.yak.ops.core.api.connector.sink;

/**
 * Optional capability for a Sink whose runtime can coordinate schema changes.
 *
 * <p>Implementations create a task-attempt-owned MetadataApplier. The runtime never infers
 * schema evolution support from the existence of a JDBC connection or a DDL dialect.
 */
public interface SchemaEvolutionSink {

    /** Creates an independent metadata applier owned by the active Job attempt. */
    MetadataApplier createMetadataApplier() throws Exception;
}
