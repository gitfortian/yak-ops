package io.yak.ops.boot.config;

import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.datasync.DataSyncTaskPlugin;
import io.yak.ops.plugin.task.datasync.config.ExecutionConfigurationFactory;
import io.yak.ops.plugin.task.datasync.config.RuntimeOptions;
import io.yak.ops.plugin.task.datasync.connector.ConnectorFactoryRegistry;
import io.yak.ops.plugin.task.datasync.connector.cdc.MySqlCdcOptions;
import io.yak.ops.plugin.task.datasync.execution.DataSyncTaskExecutor;
import io.yak.ops.plugin.task.datasync.pipeline.PipelineBuilder;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import io.yak.ops.plugin.task.datasync.resource.RuntimeResources;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Constructs and registers built-in Task plugins with their execution dependencies. */
@Configuration(proxyBeanMethods = false)
public class TaskPluginConfiguration {

    @Bean
    PipelineExecutor taskPipelineExecutor() {
        return new EmbeddedPipelineExecutor();
    }

    @Bean
    ConnectorFactoryRegistry connectorFactoryRegistry() {
        return new ConnectorFactoryRegistry();
    }

    @Bean
    JobPlanner jobPlanner(RuntimeResources resources, ConnectorFactoryRegistry factories) {
        return new JobPlanner(resources, factories);
    }

    @Bean
    RuntimeOptions dataSyncRuntimeOptions(
            @Value("${yak.task.data-sync.source-parallelism:1}") int sourceParallelism,
            @Value("${yak.task.data-sync.sink-parallelism:1}") int sinkParallelism,
            @Value("${yak.task.data-sync.cdc.checkpoint-interval:PT10S}") String checkpointInterval,
            @Value("${yak.task.data-sync.cdc.max-restarts:1}") int maxRestarts) {
        return new RuntimeOptions(sourceParallelism, sinkParallelism, Duration.parse(checkpointInterval), maxRestarts);
    }

    @Bean
    MySqlCdcOptions mySqlCdcOptions(
            @Value("${yak.task.data-sync.cdc.snapshot-chunk-size:1024}") int snapshotChunkSize,
            @Value("${yak.task.data-sync.cdc.queue-capacity:256}") int queueCapacity) {
        return new MySqlCdcOptions(snapshotChunkSize, queueCapacity);
    }

    @Bean
    ExecutionConfigurationFactory executionConfigurationFactory(
            RuntimeOptions runtimeOptions,
            MySqlCdcOptions cdcOptions,
            @Value("${yak.task.execution.state-directory:./data/task-state}") String stateDirectory) {
        return new ExecutionConfigurationFactory(runtimeOptions, Path.of(stateDirectory), cdcOptions.configuration());
    }

    @Bean
    PipelineBuilder pipelineBuilder(
            RuntimeResources resources,
            ConnectorFactoryRegistry factories,
            RuntimeOptions runtimeOptions,
            ExecutionConfigurationFactory configurationFactory) {
        return new PipelineBuilder(resources, factories, runtimeOptions, configurationFactory);
    }

    @Bean
    DataSyncTaskExecutor dataSyncTaskExecutor(JobPlanner planner, PipelineBuilder builder, PipelineExecutor executor) {
        return new DataSyncTaskExecutor(planner, builder, executor);
    }

    @Bean
    DataSyncTaskPlugin dataSyncTaskPlugin(
            JobPlanner planner, DataSyncTaskExecutor executor, ConnectorFactoryRegistry factories) {
        return new DataSyncTaskPlugin(planner, executor, factories);
    }

    @Bean
    TaskPluginRegistry taskPluginRegistry(DataSyncTaskPlugin taskPlugin) {
        return new TaskPluginRegistry(List.of(taskPlugin));
    }
}
