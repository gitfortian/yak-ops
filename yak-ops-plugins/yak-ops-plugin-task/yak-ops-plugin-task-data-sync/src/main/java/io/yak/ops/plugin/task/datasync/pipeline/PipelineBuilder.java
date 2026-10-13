package io.yak.ops.plugin.task.datasync.pipeline;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.ConnectorExecutionIdentity;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.config.ExecutionConfigurationFactory;
import io.yak.ops.plugin.task.datasync.config.JobOptions;
import io.yak.ops.plugin.task.datasync.plan.JobPlan;
import io.yak.ops.plugin.task.datasync.resource.RuntimeConnection;
import io.yak.ops.plugin.task.datasync.resource.RuntimeResources;
import java.nio.file.Path;
import java.util.Objects;

/** Compiles an authorized JobPlan to a physical single-table YakFlow pipeline. */
public final class PipelineBuilder {

    private final RuntimeResources resources;
    private final FactoryUtil factories;
    private final ReadonlyConfig options;
    private final ExecutionConfigurationFactory configurationFactory;

    /** Creates a builder with system defaults for embedded execution. */
    public PipelineBuilder(RuntimeResources resources, FactoryUtil factories, Path stateRoot) {
        this(
                resources,
                factories,
                JobOptions.defaults(),
                new ExecutionConfigurationFactory(JobOptions.defaults(), stateRoot));
    }

    /** Constructs a builder with application-owned runtime options and configuration factory. */
    public PipelineBuilder(
            RuntimeResources resources,
            FactoryUtil factories,
            ReadonlyConfig options,
            ExecutionConfigurationFactory configurationFactory) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.factories = Objects.requireNonNull(factories, "factories");
        this.options = Objects.requireNonNull(options, "options");
        this.configurationFactory = Objects.requireNonNull(configurationFactory, "configurationFactory");
    }

    /**
     * Compiles one physical job with stable operator UIDs and validated resource revisions.
     *
     * @param plan authorized logical Job plan
     * @param identity Task Instance and Attempt identity
     * @return compiled topology and execution configuration
     */
    public CompiledPipeline build(JobPlan plan, TaskExecutionIdentity identity) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(identity, "identity");
        var sourceFactory = factories.source(plan.sourceParameters().get(JobOptions.TYPE));
        var sinkFactory = factories.sink(plan.sinkParameters().get(JobOptions.TYPE));
        int sourceParallelism = options.get(JobOptions.SOURCE_PARALLELISM);
        int sinkParallelism = options.get(JobOptions.SINK_PARALLELISM);
        if (sourceFactory.producesChangelog() && sinkParallelism != 1) {
            throw new UnsupportedOperationException(
                    "DATA_SYNC changelog requires a single Sink writer until keyed routing is configured");
        }
        Configuration configuration = configurationFactory.create(identity, sourceFactory.boundedness());
        String sourceId = plan.sourceParameters().get(JobOptions.DATASOURCE_ID);
        String sinkId = plan.sinkParameters().get(JobOptions.DATASOURCE_ID);
        RuntimeConnection sourceConnection = resources.connection(identity.workspaceId(), sourceId);
        RuntimeConnection targetConnection = resources.connection(identity.workspaceId(), sinkId);
        if (sourceConnection.revision() != plan.source().revision()
                || targetConnection.revision() != plan.target().revision()) {
            throw new IllegalStateException("DATA_SYNC datasource revision changed before execution");
        }
        ConnectorExecutionIdentity connectorIdentity =
                new ConnectorExecutionIdentity(identity.workspaceId(), identity.definitionId(), identity.instanceId());

        Source<TableRecord, ?, ?> source = sourceFactory.createSource(new TableSourceFactoryContext(
                plan.sourceParameters(),
                sourceConnection.connectorResource(),
                plan.source().table(),
                plan.source().schema(),
                configuration,
                connectorIdentity));
        if (source.getBoundedness() != sourceFactory.boundedness()) {
            throw new IllegalStateException("DATA_SYNC Source boundedness differs from its Factory contract");
        }
        Sink<TableRecord> sink = sinkFactory.createSink(new TableSinkFactoryContext(
                plan.sinkParameters(),
                targetConnection.connectorResource(),
                plan.source().table(),
                plan.source().schema(),
                plan.target().table(),
                plan.target().schema()));
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
