package io.yak.ops.business.job.task;

import java.util.Map;

/**
 * Executes one immutable task snapshot.
 *
 * <p>Workflow only depends on this contract. SQL, SYNC, HTTP, Shell and future task types plug in
 * independently without leaking task-specific configuration into the workflow runtime.</p>
 */
public interface TaskExecutor {

  String taskType();

  TaskExecution start(
      TaskVersionSnapshot snapshot,
      String idempotencyKey,
      Map<String, Object> input);

  TaskExecution status(String executionId);

  void cancel(String executionId);
}
