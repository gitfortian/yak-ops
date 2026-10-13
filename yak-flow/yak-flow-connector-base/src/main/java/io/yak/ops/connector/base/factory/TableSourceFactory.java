package io.yak.ops.connector.base.factory;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import java.util.List;

/** Creates a Source from a validated configuration and a trusted resource binding. */
public interface TableSourceFactory extends Factory {

    /** Declares whether this Source is bounded. */
    Boundedness boundedness();

    /** Indicates whether the Source emits update and delete events. */
    boolean producesChangelog();

    /** Declares exclusive source resources without obtaining credentials or opening connections. */
    default List<ConnectorResourceClaim> exclusiveResources(
            ReadonlyConfig options, ConnectorExecutionIdentity identity) {
        return List.of();
    }

    /** Checks whether the physical datasource type is supported. */
    default void validateDatasource(String datasourceType) {}

    /** Constructs one reusable Source definition without starting its readers. */
    Source<TableRecord, ?, ?> createSource(TableSourceFactoryContext context);
}
