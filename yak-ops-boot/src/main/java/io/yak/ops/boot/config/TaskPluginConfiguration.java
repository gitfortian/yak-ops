package io.yak.ops.boot.config;

import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.datasync.SyncTaskPlugin;
import io.yak.ops.plugin.task.datasync.connector.SyncConnectorFactoryRegistry;
import io.yak.ops.plugin.task.datasync.execution.SyncTaskExecutor;
import io.yak.ops.plugin.task.datasync.pipeline.SyncPipelineCompiler;
import io.yak.ops.plugin.task.datasync.pipeline.SyncRuntimeOptions;
import io.yak.ops.plugin.task.datasync.plan.SyncJobPlanner;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeResources;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicitly constructs and registers built-in Task plugins with their runtime dependencies. */
@Configuration(proxyBeanMethods = false)
public class TaskPluginConfiguration {

    @Bean
    PipelineExecutor taskPipelineExecutor() {
        return new EmbeddedPipelineExecutor();
    }

    @Bean
    SyncConnectorFactoryRegistry syncConnectorFactoryRegistry() {
        return new SyncConnectorFactoryRegistry();
    }

    @Bean
    SyncJobPlanner syncJobPlanner(SyncRuntimeResources resources, SyncConnectorFactoryRegistry factories) {
        return new SyncJobPlanner(resources, factories);
    }

    @Bean
    SyncRuntimeOptions syncRuntimeOptions(
            @Value("${yak.task.data-sync.source-parallelism:1}") int sourceParallelism,
            @Value("${yak.task.data-sync.sink-parallelism:1}") int sinkParallelism,
            @Value("${yak.task.data-sync.cdc.snapshot-chunk-size:1024}") int snapshotChunkSize,
            @Value("${yak.task.data-sync.cdc.queue-capacity:256}") int cdcQueueCapacity,
            @Value("${yak.task.data-sync.cdc.checkpoint-interval:PT10S}") String checkpointInterval,
            @Value("${yak.task.data-sync.cdc.max-restarts:1}") int maxRestarts) {
        return new SyncRuntimeOptions(
                sourceParallelism,
                sinkParallelism,
                snapshotChunkSize,
                cdcQueueCapacity,
                Duration.parse(checkpointInterval),
                maxRestarts);
    }

    @Bean
    SyncPipelineCompiler syncPipelineCompiler(
            SyncRuntimeResources resources,
            SyncConnectorFactoryRegistry factories,
            SyncRuntimeOptions runtimeOptions,
            @Value("${yak.task.execution.state-directory:./data/task-state}") String stateDirectory) {
        return new SyncPipelineCompiler(resources, factories, runtimeOptions, Path.of(stateDirectory));
    }

    @Bean
    SyncTaskExecutor syncTaskExecutor(
            SyncJobPlanner planner, SyncPipelineCompiler compiler, PipelineExecutor executor) {
        return new SyncTaskExecutor(planner, compiler, executor);
    }

    @Bean
    SyncTaskPlugin syncTaskPlugin(
            SyncJobPlanner planner, SyncTaskExecutor executor, SyncConnectorFactoryRegistry factories) {
        return new SyncTaskPlugin(planner, executor, factories);
    }

    @Bean
    TaskPluginRegistry taskPluginRegistry(SyncTaskPlugin syncTaskPlugin) {
        return new TaskPluginRegistry(List.of(syncTaskPlugin));
    }
}
