package io.yak.ops.business.job.task;

import io.yak.ops.business.job.env.SystemEnvVarService;
import io.yak.ops.plugin.task.api.DefaultTaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Central factory for creating {@link TaskExecutionContext} instances.
 *
 * <p>Automatically resolves and injects merged global environment variables
 * (OS-level + application-level from the Settings page) so that every task
 * adapter receives them without depending on {@link SystemEnvVarService} directly.
 *
 * <p>Usage in task adapters:
 * <pre>{@code
 *   TaskExecutionContext context = contextFactory.create(trigger, input);
 *
 *   // With a capability (e.g. DataSourceExecutionProvider for SQL):
 *   TaskExecutionContext context = contextFactory.create(trigger, input,
 *       builder -> builder.capability(DataSourceExecutionProvider.class, provider));
 * }</pre>
 */
@Service
public class TaskExecutionContextFactory {

  private final SystemEnvVarService envVarService;

  public TaskExecutionContextFactory(SystemEnvVarService envVarService) {
    this.envVarService = envVarService;
  }

  /**
   * Create a context with trigger, parameters, and automatically resolved global env vars.
   */
  public TaskExecutionContext create(
      TaskExecutionTrigger trigger,
      Map<String, Object> input) {
    return DefaultTaskExecutionContext.builder()
        .trigger(trigger)
        .parameters(input)
        .globalEnvVars(envVarService.resolveMergedEnv())
        .build();
  }

  /**
   * Create a context with a customiser callback for registering capabilities
   * or overriding defaults.
   *
   * <p>Example:
   * <pre>{@code
   *   contextFactory.create(trigger, input, builder ->
   *       builder.capability(DataSourceExecutionProvider.class, provider));
   * }</pre>
   */
  public TaskExecutionContext create(
      TaskExecutionTrigger trigger,
      Map<String, Object> input,
      java.util.function.Consumer<DefaultTaskExecutionContext.Builder> customiser) {
    DefaultTaskExecutionContext.Builder builder = DefaultTaskExecutionContext.builder()
        .trigger(trigger)
        .parameters(input)
        .globalEnvVars(envVarService.resolveMergedEnv());
    if (customiser != null) {
      customiser.accept(builder);
    }
    return builder.build();
  }
}
