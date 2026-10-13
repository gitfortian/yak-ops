package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.config.DefinitionParser;
import io.yak.ops.plugin.task.datasync.connector.ConnectorFactoryRegistry;
import io.yak.ops.plugin.task.datasync.execution.DataSyncTaskExecutor;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Defines DATA_SYNC parameters and delegates planning and physical execution to dedicated components. */
public final class DataSyncTaskPlugin implements ExecutableTaskPlugin {

    public static final String TYPE = "DATA_SYNC";

    private final JobPlanner planner;
    private final DataSyncTaskExecutor executor;
    private final ConnectorFactoryRegistry factories;
    private final DefinitionParser parser;

    public DataSyncTaskPlugin(JobPlanner planner, DataSyncTaskExecutor executor, ConnectorFactoryRegistry factories) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.factories = Objects.requireNonNull(factories, "factories");
        this.parser = new DefinitionParser(factories);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public int parameterSchemaVersion() {
        return 2;
    }

    @Override
    public TaskParameters parse(String content) {
        DataSyncParameters parameters;
        try {
            parameters = parser.parse(content);
        } catch (IllegalArgumentException exception) {
            throw new TaskPluginException("Invalid DATA_SYNC pipeline JSON");
        }
        DefinitionValidator.validateParameters(parameters, factories);
        return parameters;
    }

    @Override
    public void validate(TaskParameters parameters, TaskValidationContext context) {
        if (!(parameters instanceof DataSyncParameters pipeline)) {
            throw new TaskPluginException("Invalid DATA_SYNC parameter type");
        }
        DefinitionValidator.validateResources(pipeline, context, factories);
    }

    @Override
    public TaskAdmissionPlan plan(TaskParameters parameters, TaskExecutionIdentity context) {
        if (!(parameters instanceof DataSyncParameters pipeline)) {
            throw new TaskPluginException("Invalid DATA_SYNC parameter type");
        }
        return planner.admissionPlan(pipeline, context);
    }

    @Override
    public CompletionStage<TaskHandle> start(
            TaskParameters parameters, TaskAdmissionPlan plan, TaskExecutionIdentity context) {
        return executor.start(parameters, plan, context);
    }
}
