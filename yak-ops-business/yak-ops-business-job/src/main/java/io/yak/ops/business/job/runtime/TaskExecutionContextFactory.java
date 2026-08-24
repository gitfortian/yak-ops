package io.yak.ops.business.job.runtime;

import io.yak.ops.business.job.environment.TaskEnvironmentResolver;
import io.yak.ops.plugin.task.api.DefaultTaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Creates runtime contexts without depending on environment-management CRUD. */
@Component
public class TaskExecutionContextFactory {

  private final TaskEnvironmentResolver environmentResolver;

  public TaskExecutionContextFactory(TaskEnvironmentResolver environmentResolver) {
    this.environmentResolver = environmentResolver;
  }

  public TaskExecutionContext create(
      TaskExecutionTrigger trigger,
      Map<String, Object> input) {
    return DefaultTaskExecutionContext.builder()
        .trigger(trigger)
        .parameters(input)
        .globalEnvVars(environmentResolver.resolveMergedEnv())
        .build();
  }

  public TaskExecutionContext create(
      TaskExecutionTrigger trigger,
      Map<String, Object> input,
      java.util.function.Consumer<DefaultTaskExecutionContext.Builder> customiser) {
    DefaultTaskExecutionContext.Builder builder = DefaultTaskExecutionContext.builder()
        .trigger(trigger)
        .parameters(input)
        .globalEnvVars(environmentResolver.resolveMergedEnv());
    if (customiser != null) {
      customiser.accept(builder);
    }
    return builder.build();
  }
}
