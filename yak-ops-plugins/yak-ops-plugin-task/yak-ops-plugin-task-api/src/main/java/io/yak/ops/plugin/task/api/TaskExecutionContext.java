package io.yak.ops.plugin.task.api;

import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.util.Map;

/**
 * Minimal runtime context visible to task plugins.
 *
 * <p>The contract intentionally stays small in stage 2. Task Runtime may add backward-compatible
 * default capabilities later without leaking Workflow-specific state into plugins.
 */
public interface TaskExecutionContext {

  TaskExecutionTrigger trigger();

  Map<String, Object> parameters();
}
