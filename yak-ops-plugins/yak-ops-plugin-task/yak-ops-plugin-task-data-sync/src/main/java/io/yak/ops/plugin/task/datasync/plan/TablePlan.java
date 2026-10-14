package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import java.util.Objects;

/**
 * One authorized source-to-target route and its optional initial schema operation.
 *
 * <p>The target identity and revision exist independently of the physical target. A null
 * targetSchema means the target is absent; it must be prepared and read back before compilation.
 */
public record TablePlan(
        ReadonlyConfig sourceParameters,
        ReadonlyConfig sinkParameters,
        DataSourceTable source,
        DataSourceInfo targetDatasource,
        TableId targetTable,
        TableSchema targetSchema,
        CreateTableEvent schemaChange,
        ReadonlyConfig sourceOptions) {
    public TablePlan {
        Objects.requireNonNull(sourceParameters, "sourceParameters");
        Objects.requireNonNull(sinkParameters, "sinkParameters");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetDatasource, "targetDatasource");
        Objects.requireNonNull(targetTable, "targetTable");
        Objects.requireNonNull(sourceOptions, "sourceOptions");
        if (targetSchema == null && schemaChange == null) {
            throw new IllegalArgumentException("An absent target requires schema preparation");
        }
        if (schemaChange != null && !targetTable.equals(schemaChange.tableId())) {
            throw new IllegalArgumentException("Schema event and target identities differ");
        }
    }

    /** Creates a route to an existing target without initial schema preparation. */
    public TablePlan(
            ReadonlyConfig from,
            ReadonlyConfig to,
            DataSourceTable source,
            DataSourceTable target,
            ReadonlyConfig sourceOptions) {
        this(
                from,
                to,
                source,
                new DataSourceInfo(
                        target.datasourceType(),
                        target.table().catalog(),
                        target.table().schema(),
                        target.revision()),
                target.table(),
                target.schema(),
                null,
                sourceOptions);
    }

    /** Changes only physical Source settings; routing and target admission remain unchanged. */
    public TablePlan withSourceOptions(ReadonlyConfig physical) {
        return new TablePlan(
                sourceParameters,
                sinkParameters,
                source,
                targetDatasource,
                targetTable,
                targetSchema,
                schemaChange,
                physical);
    }

    /** Returns the admitted desired schema; creating the target does not change this value. */
    public TableSchema expectedTargetSchema() {
        return schemaChange == null ? targetSchema : schemaChange.getSchema();
    }

    /** Replaces only physical metadata after checking the pinned identity and datasource revision. */
    public TablePlan withTarget(DataSourceTable target) {
        if (!targetTable.equals(target.table())
                || target.revision() != targetDatasource.revision()
                || !target.datasourceType().equals(targetDatasource.type())) {
            throw new IllegalStateException("Target binding changed during schema preparation");
        }
        return new TablePlan(
                sourceParameters,
                sinkParameters,
                source,
                targetDatasource,
                targetTable,
                target.schema(),
                schemaChange,
                sourceOptions);
    }
}
