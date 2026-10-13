package io.yak.ops.plugin.task.datasync.pipeline;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.connector.SyncConnectorFactoryRegistry;
import io.yak.ops.plugin.task.datasync.connector.SyncSinkContext;
import io.yak.ops.plugin.task.datasync.connector.SyncSourceContext;
import io.yak.ops.plugin.task.datasync.plan.SyncJobPlan;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeConnection;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeResources;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Compiles an authorized DATA_SYNC Job plan into a single YakFlow Source-to-Sink StreamGraph.
 *
 * <p>Runtime connection bindings are checked before physical Connector definitions are created.
 * Compilation opens no connections and does not submit work or persist Task state.
 */
public final class SyncPipelineCompiler {

    private final SyncRuntimeResources resources;
    private final SyncConnectorFactoryRegistry factories;
    private final SyncRuntimePolicy runtimePolicy;

    /** Creates a compiler using the built-in single-table execution settings. */
    public SyncPipelineCompiler(
            SyncRuntimeResources resources, SyncConnectorFactoryRegistry factories, Path stateRoot) {
        this(resources, factories, new SyncRuntimePolicy(SyncRuntimeOptions.defaults(), stateRoot));
    }

    /** Creates a compiler with system-owned execution and recovery settings. */
    public SyncPipelineCompiler(
            SyncRuntimeResources resources,
            SyncConnectorFactoryRegistry factories,
            SyncRuntimeOptions runtimeOptions,
            Path stateRoot) {
        this(resources, factories, new SyncRuntimePolicy(runtimeOptions, stateRoot));
    }

    private SyncPipelineCompiler(
            SyncRuntimeResources resources, SyncConnectorFactoryRegistry factories, SyncRuntimePolicy runtimePolicy) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.factories = Objects.requireNonNull(factories, "factories");
        this.runtimePolicy = Objects.requireNonNull(runtimePolicy, "runtimePolicy");
    }

    /**
     * Builds a reusable, validated YakFlow graph from the admitted logical Job.
     *
     * @param plan non-secret resolved DATA_SYNC plan
     * @param identity authorized Task Instance identity
     * @return compiled pipeline and effective configuration
     */
    public SyncCompiledPipeline compile(SyncJobPlan plan, TaskExecutionIdentity identity) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(identity, "identity");

        var sourceFactory = factories.source(plan.sourceParameters());
        var sinkFactory = factories.sink(plan.sinkParameters());
        int sourceParallelism = runtimePolicy.sourceParallelism();
        int sinkParallelism = runtimePolicy.sinkParallelism(sourceFactory.producesChangelog());
        Configuration configuration = runtimePolicy.configure(identity, sourceFactory.boundedness());

        SyncRuntimeConnection sourceConnection = resources.connection(
                identity.workspaceId(), plan.sourceParameters().datasourceId());
        SyncRuntimeConnection targetConnection = resources.connection(
                identity.workspaceId(), plan.sinkParameters().datasourceId());
        if (sourceConnection.revision() != plan.source().revision()
                || targetConnection.revision() != plan.target().revision()) {
            throw new IllegalStateException("DATA_SYNC datasource revision changed before execution");
        }

        Source<TableRecord, ?, ?> source = sourceFactory.create(
                plan.sourceParameters(),
                new SyncSourceContext(
                        sourceConnection, plan.source(), configuration, identity, runtimePolicy.options()));
        if (source.getBoundedness() != sourceFactory.boundedness()) {
            throw new IllegalStateException("DATA_SYNC Source boundedness differs from its Factory contract");
        }
        Sink<TableRecord> sink = sinkFactory.create(
                plan.sinkParameters(), new SyncSinkContext(targetConnection, plan.source(), plan.target()));
        SourceTransformation<TableRecord> input =
                new SourceTransformation<>("data-sync-source", source, TableRecord.class, sourceParallelism);
        SinkTransformation<TableRecord> output =
                new SinkTransformation<>(input, "data-sync-sink", sink, sinkParallelism);
        input.setUid("data-sync-source-" + identity.definitionId());
        output.setUid("data-sync-sink-" + identity.definitionId());
        StreamGraph graph = new StreamGraphGenerator(output, configuration).generate();
        return new SyncCompiledPipeline(graph, configuration);
    }
}
