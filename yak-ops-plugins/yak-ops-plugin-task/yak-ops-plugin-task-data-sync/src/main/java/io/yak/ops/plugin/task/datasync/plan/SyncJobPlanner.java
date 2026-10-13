package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.SyncParameters;
import io.yak.ops.plugin.task.datasync.SyncValidator;
import io.yak.ops.plugin.task.datasync.connector.SyncConnectorFactoryRegistry;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeResources;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeTable;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Resolves authorized DATA_SYNC metadata and constructs the logical Job and admission plans. */
public final class SyncJobPlanner {

    private final SyncRuntimeResources resources;
    private final SyncConnectorFactoryRegistry factories;

    public SyncJobPlanner(SyncRuntimeResources resources, SyncConnectorFactoryRegistry factories) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.factories = Objects.requireNonNull(factories, "factories");
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
        SyncValidator.checkParameters(parameters, factories);
        SourceParameters source = parameters.source();
        var sink = parameters.sink();
        var sourceFactory = factories.source(source);
        var sinkFactory = factories.sink(sink);
        SyncRuntimeTable from = resources.table(
                context.workspaceId(), source.datasourceId(), source.database(), source.schema(), source.table());
        SyncRuntimeTable to = resources.table(
                context.workspaceId(), sink.datasourceId(), sink.database(), sink.schema(), sink.table());

        sourceFactory.validateDatasource(from.datasourceType());
        sinkFactory.validateSchema(sink, from.schema(), to.schema(), sourceFactory.producesChangelog());

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
        List<ExecutionResource> claims = new ArrayList<>();
        claims.add(new ExecutionResource("DATA_SYNC_TARGET", target));
        claims.addAll(sourceFactory.exclusiveResources(source, context));
        TaskAdmissionPlan admission = new TaskAdmissionPlan(claims, sha256(binding));
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
