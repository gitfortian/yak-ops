package io.yak.ops.plugin.task.api;

import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Runtime context visible to task plugins.
 *
 * <p>Parameters carry serializable runtime values, while capabilities expose typed in-process
 * services such as datasource execution. This keeps task plugins independent from Spring and from
 * concrete business modules.
 */
public interface TaskExecutionContext {

  TaskExecutionTrigger trigger();

  Map<String, Object> parameters();

  /** Resolve one optional runtime capability by its stable interface type. */
  default <T> Optional<T> capability(Class<T> capabilityType) {
    Objects.requireNonNull(capabilityType, "capabilityType");
    return Optional.empty();
  }

  /** Resolve one required runtime capability or fail with a clear runtime error. */
  default <T> T requireCapability(Class<T> capabilityType) {
    Objects.requireNonNull(capabilityType, "capabilityType");
    return capability(capabilityType)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Task runtime capability is not available: " + capabilityType.getName()));
  }
}
