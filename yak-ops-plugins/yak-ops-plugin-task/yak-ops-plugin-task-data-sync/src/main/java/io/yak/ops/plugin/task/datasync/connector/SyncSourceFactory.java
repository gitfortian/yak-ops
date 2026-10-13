package io.yak.ops.plugin.task.datasync.connector;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;

/**
 * Creates one reusable YakFlow Source from a validated DATA_SYNC endpoint.
 *
 * <p>Factories are stateless SPI providers. Connector readers and external resources belong to
 * YakFlow execution attempts, not to factory registration or planning.
 */
public interface SyncSourceFactory {

    /** Returns the persistent JSON Source type identifier. */
    String identifier();

    /** Returns the concrete JSON parameters class supported by this factory. */
    Class<? extends SourceParameters> parametersType();

    /** Indicates whether this Source may emit UPDATE or DELETE events. */
    boolean producesChangelog();

    /**
     * Validates the authorized Datasource type before an execution is admitted.
     *
     * @param datasourceType physical Datasource type identifier
     */
    default void validateDatasource(String datasourceType) {}

    /**
     * Creates the physical Source definition without starting a Reader.
     *
     * @param parameters pinned Source configuration
     * @param context authorized runtime binding for the current Instance
     * @return reusable Source definition
     */
    Source<TableRecord, ?, ?> create(SourceParameters parameters, SyncSourceContext context);
}
