package io.yak.ops.plugin.task.datasync.pipeline;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.config.ExecutionConfigurationFactory;
import io.yak.ops.plugin.task.datasync.config.RuntimeOptions;
import io.yak.ops.plugin.task.datasync.connector.ConnectorFactoryRegistry;
import io.yak.ops.plugin.task.datasync.connector.SinkFactoryContext;
import io.yak.ops.plugin.task.datasync.connector.SourceFactoryContext;
import io.yak.ops.plugin.task.datasync.plan.JobPlan;
import io.yak.ops.plugin.task.datasync.resource.RuntimeConnection;
import io.yak.ops.plugin.task.datasync.resource.RuntimeResources;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Builds a single-table YakFlow StreamGraph from an authorized logical Job plan.
 *
 * <p>Connector factories own concrete Source and Sink creation. The builder checks resource
 * revisions and constructs transformations without opening JDBC connections or submitting a Job.
 */
public final class PipelineBuilder {

    private final RuntimeResources resources;
    private final ConnectorFactoryRegistry factories;
    private final RuntimeOptions options;
    private final ExecutionConfigurationFactory configurationFactory;

    /** Creates a builder with system defaults for embedded execution. */
    public PipelineBuilder(RuntimeResources resources, ConnectorFactoryRegistry factories, Path stateRoot) {
        this(
                resources,
                factories,
                RuntimeOptions.defaults(),
                new ExecutionConfigurationFactory(RuntimeOptions.defaults(), stateRoot));
    }

    /** Creates a builder using the supplied execution options and configuration factory. */
    public PipelineBuilder(
            RuntimeResources resources,
            ConnectorFactoryRegistry factories,
            RuntimeOptions options,
            ExecutionConfigurationFactory configurationFactory) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.factories = Objects.requireNonNull(factories, "factories");
        this.options = Objects.requireNonNull(options, "options");
        this.configurationFactory = Objects.requireNonNull(configurationFactory, "configurationFactory");
    }

    /**
     * Builds the physical topology with stable operator UIDs.
     *
     * @param plan non-secret, authorized logical Job plan
     * @param identity trusted Task Instance identity
     * @return graph and effective YakFlow configuration for submission
     */
    public CompiledPipeline build(JobPlan plan, TaskExecutionIdentity identity) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(identity, "identity");

        var sourceFactory = factories.source(plan.sourceParameters());
        var sinkFactory = factories.sink(plan.sinkParameters());
        int sourceParallelism = options.sourceParallelism();
        int sinkParallelism = options.sinkParallelism();
        if (sourceFactory.producesChangelog() && sinkParallelism != 1) {
            throw new UnsupportedOperationException(
                    "DATA_SYNC changelog requires a single Sink writer until keyed routing is configured");
        }
        Configuration configuration = configurationFactory.create(identity, sourceFactory.boundedness());

        RuntimeConnection sourceConnection = resources.connection(
                identity.workspaceId(), plan.sourceParameters().datasourceId());
        RuntimeConnection targetConnection = resources.connection(
                identity.workspaceId(), plan.sinkParameters().datasourceId());
        if (sourceConnection.revision() != plan.source().revision()
                || targetConnection.revision() != plan.target().revision()) {
            throw new IllegalStateException("DATA_SYNC datasource revision changed before execution");
        }

        Source<TableRecord, ?, ?> source = sourceFactory.createSource(
                plan.sourceParameters(),
                new SourceFactoryContext(sourceConnection, plan.source(), configuration, identity));
        if (source.getBoundedness() != sourceFactory.boundedness()) {
            throw new IllegalStateException("DATA_SYNC Source boundedness differs from its Factory contract");
        }
        Sink<TableRecord> sink = sinkFactory.createSink(
                plan.sinkParameters(), new SinkFactoryContext(targetConnection, plan.source(), plan.target()));
        SourceTransformation<TableRecord> input =
                new SourceTransformation<>("data-sync-source", source, TableRecord.class, sourceParallelism);
        SinkTransformation<TableRecord> output =
                new SinkTransformation<>(input, "data-sync-sink", sink, sinkParallelism);
        input.setUid("data-sync-source-" + identity.definitionId());
        output.setUid("data-sync-sink-" + identity.definitionId());
        StreamGraph graph = new StreamGraphGenerator(output, configuration).generate();
        return new CompiledPipeline(graph, configuration);
    }
}
