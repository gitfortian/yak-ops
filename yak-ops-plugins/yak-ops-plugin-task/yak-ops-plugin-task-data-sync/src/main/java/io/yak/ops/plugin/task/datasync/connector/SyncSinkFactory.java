package io.yak.ops.plugin.task.datasync.connector;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.datasync.sink.SinkParameters;

/** Resolves a DATA_SYNC Sink's write contract and creates its reusable YakFlow definition. */
public interface SyncSinkFactory {

    /** Returns the persistent JSON Sink type identifier. */
    String identifier();

    /** Returns the concrete JSON parameters class supported by this factory. */
    Class<? extends SinkParameters> parametersType();

    /**
     * Checks write semantics before any Datasource metadata or connection is acquired.
     *
     * @param parameters pinned Sink configuration
     * @param sourceProducesChangelog whether the selected Source emits changes
     */
    void validate(SinkParameters parameters, boolean sourceProducesChangelog);

    /**
     * Checks that the Sink can execute the resolved source-to-target field mapping.
     *
     * @param parameters pinned Sink configuration
     * @param source source logical schema
     * @param target target logical schema
     * @param sourceProducesChangelog whether the selected Source emits changes
     */
    void validateSchema(
            SinkParameters parameters, TableSchema source, TableSchema target, boolean sourceProducesChangelog);

    /**
     * Creates the physical Sink definition without starting a Writer.
     *
     * @param parameters pinned Sink configuration
     * @param context authorized runtime table and connection bindings
     * @return reusable Sink definition
     */
    Sink<TableRecord> create(SinkParameters parameters, SyncSinkContext context);
}
