package io.yak.ops.plugin.task.datasync.connector;

import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import java.util.List;

/**
 * Creates one reusable YakFlow Source from a validated DATA_SYNC endpoint.
 *
 * <p>Factories are stateless SPI providers. Connector readers and external resources belong to
 * YakFlow execution attempts, not to factory registration or planning.
 */
public interface TableSourceFactory {

    /** Returns the persistent JSON Source type identifier. */
    String identifier();

    /** Returns the concrete JSON parameters class supported by this factory. */
    Class<? extends SourceParameters> parametersType();

    /** Returns the boundedness required by this Source's YakFlow runtime configuration. */
    Boundedness boundedness();

    /** Indicates whether this Source may emit UPDATE or DELETE events. */
    boolean producesChangelog();

    /**
     * Declares Source-owned exclusive resources without resolving connection credentials.
     *
     * @param parameters pinned Source configuration
     * @param identity trusted Task Instance identity
     * @return deterministic durable admission claims
     */
    default List<ExecutionResource> exclusiveResources(SourceParameters parameters, TaskExecutionIdentity identity) {
        return List.of();
    }

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
    Source<TableRecord, ?, ?> createSource(SourceParameters parameters, SourceFactoryContext context);
}
