package io.yak.ops.plugin.task.datasync.pipeline;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.PipelineOptions;
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
import java.time.Duration;
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
    private final Path stateRoot;

    public SyncPipelineCompiler(
            SyncRuntimeResources resources, SyncConnectorFactoryRegistry factories, Path stateRoot) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.factories = Objects.requireNonNull(factories, "factories");
        this.stateRoot =
                Objects.requireNonNull(stateRoot, "stateRoot").toAbsolutePath().normalize();
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
        SyncRuntimeConnection sourceConnection = resources.connection(
                identity.workspaceId(), plan.sourceParameters().datasourceId());
        SyncRuntimeConnection targetConnection = resources.connection(
                identity.workspaceId(), plan.sinkParameters().datasourceId());
        if (sourceConnection.revision() != plan.source().revision()
                || targetConnection.revision() != plan.target().revision()) {
            throw new IllegalStateException("DATA_SYNC datasource revision changed before execution");
        }

        var sourceFactory = factories.source(plan.sourceParameters());
        var sinkFactory = factories.sink(plan.sinkParameters());
        Configuration configuration = new Configuration()
                .set(PipelineOptions.AUTO_GENERATE_UIDS, false)
                .set(
                        CheckpointingOptions.STATE_DIRECTORY,
                        stateRoot.resolve(identity.instanceId()).toString());
        if (sourceFactory.producesChangelog()) {
            configuration.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(10));
        }

        Source<TableRecord, ?, ?> source = sourceFactory.create(
                plan.sourceParameters(),
                new SyncSourceContext(sourceConnection, plan.source(), configuration, identity));
        Sink<TableRecord> sink = sinkFactory.create(
                plan.sinkParameters(), new SyncSinkContext(targetConnection, plan.source(), plan.target()));
        SourceTransformation<TableRecord> input =
                new SourceTransformation<>("data-sync-source", source, TableRecord.class, 1);
        SinkTransformation<TableRecord> output = new SinkTransformation<>(input, "data-sync-sink", sink, 1);
        input.setUid("data-sync-source-" + identity.definitionId());
        output.setUid("data-sync-sink-" + identity.definitionId());
        StreamGraph graph = new StreamGraphGenerator(output, configuration).generate();
        return new SyncCompiledPipeline(graph, configuration);
    }
}
