package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.SyncParameters;
import io.yak.ops.plugin.task.datasync.SyncValidator;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeResources;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeTable;
import io.yak.ops.plugin.task.datasync.sink.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.source.MySqlCdcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Resolves authorized DATA_SYNC metadata and constructs the logical Job and admission plans. */
public final class SyncJobPlanner {

    private final SyncRuntimeResources resources;

    public SyncJobPlanner(SyncRuntimeResources resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    /**
     * Declares the exclusive destination and its non-secret binding fingerprint.
     *
     * @param parameters pinned DATA_SYNC definition
     * @param context trusted Task execution identity
     * @return admission claim validated against the effective schema
     */
    public TaskAdmissionPlan plan(SyncParameters parameters, TaskExecutionIdentity context) {
        return prepare(parameters, context).admissionPlan();
    }

    /**
     * Resolves source and target metadata and validates the executable JDBC Sink projection.
     *
     * <p>Planning does not resolve connection credentials, open connections or submit jobs.
     *
     * @param parameters pinned DATA_SYNC definition
     * @param context trusted Task execution identity
     * @return immutable, non-secret logical DATA_SYNC plan
     */
    public SyncJobPlan prepare(SyncParameters parameters, TaskExecutionIdentity context) {
        SyncValidator.checkParameters(parameters);
        SourceParameters source = parameters.source();
        JdbcSinkParameters sink = (JdbcSinkParameters) parameters.sink();
        SyncRuntimeTable from = resources.table(
                context.workspaceId(), source.datasourceId(), source.database(), source.schema(), source.table());
        SyncRuntimeTable to = resources.table(
                context.workspaceId(), sink.datasourceId(), sink.database(), sink.schema(), sink.table());

        if (source instanceof MySqlCdcSourceParameters && !"MYSQL".equals(from.datasourceType())) {
            throw new TaskPluginException("MySQL CDC requires a MySQL datasource");
        }
        boolean requireKeys =
                source instanceof MySqlCdcSourceParameters || sink.writeMode() == DataSyncWriteMode.UPSERT;
        SyncSchemaValidator.validate(from.schema(), to.schema(), requireKeys);

        String target = encodePart(sink.datasourceId()) + encodeTable(to.table());
        String binding = encodePart(context.workspaceId())
                + encodePart(source.datasourceId())
                + encodePart(Long.toString(from.revision()))
                + encodeTable(from.table())
                + encodeSchema(from.schema())
                + encodePart(sink.datasourceId())
                + encodePart(Long.toString(to.revision()))
                + encodeTable(to.table())
                + encodeSchema(to.schema());
        TaskAdmissionPlan admission =
                new TaskAdmissionPlan(List.of(new ExecutionResource("DATA_SYNC_TARGET", target)), sha256(binding));
        return new SyncJobPlan(source, sink, from, to, admission);
    }

    private static String encodeTable(TableId table) {
        return encodePart(table.catalog()) + encodePart(table.schema()) + encodePart(table.table());
    }

    private static String encodeSchema(TableSchema schema) {
        StringBuilder value = new StringBuilder().append(schema.columnCount()).append(':');
        for (Column column : schema.columns()) {
            value.append(encodePart(column.name()));
            value.append(encodePart(column.dataType().asSerializableString()));
        }
        value.append(schema.primaryKeys().size()).append(':');
        for (String key : schema.primaryKeys()) {
            value.append(encodePart(key));
        }
        return value.toString();
    }

    private static String encodePart(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required", error);
        }
    }
}
