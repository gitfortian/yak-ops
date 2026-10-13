package io.yak.ops.boot.config;

import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.datasync.SyncTaskPlugin;
import io.yak.ops.plugin.task.datasync.plan.SyncJobPlanner;
import io.yak.ops.plugin.task.datasync.execution.SyncTaskExecutor;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeResources;
import java.nio.file.Path;
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
    SyncJobPlanner syncJobPlanner(SyncRuntimeResources resources) {
        return new SyncJobPlanner(resources);
    }

    @Bean
    SyncTaskExecutor syncTaskExecutor(
            SyncRuntimeResources resources,
            SyncJobPlanner planner,
            PipelineExecutor executor,
            @Value("${yak.task.execution.state-directory:./data/task-state}") String stateDirectory) {
        return new SyncTaskExecutor(resources, planner, executor, Path.of(stateDirectory));
    }

    @Bean
    SyncTaskPlugin syncTaskPlugin(SyncJobPlanner planner, SyncTaskExecutor executor) {
        return new SyncTaskPlugin(planner, executor);
    }

    @Bean
    TaskPluginRegistry taskPluginRegistry(SyncTaskPlugin syncTaskPlugin) {
        return new TaskPluginRegistry(List.of(syncTaskPlugin));
    }
}
