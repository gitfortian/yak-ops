package io.yak.ops.business.job.task;

import java.util.Map;

/**
 * Compatibility adapter for legacy Workflow tests/constructors.
 *
 * <p>Production SYNC execution is contributed by Offline Sync directly through {@link TaskExecutor}.
 */
@Deprecated(forRemoval = true)
public class SyncTaskExecutorAdapter implements TaskExecutor {

  private final SyncTaskRunner runner;

  public SyncTaskExecutorAdapter(SyncTaskRunner runner) {
    this.runner = runner;
  }

  @Override
  public String taskType() {
    return "SYNC";
  }

  @Override
  public TaskExecution start(
      TaskVersionSnapshot snapshot,
      String idempotencyKey,
      Map<String, Object> input) {
    return convert(runner.start(snapshot, idempotencyKey));
  }

  @Override
  public TaskExecution status(String executionId) {
    return convert(runner.status(executionId));
  }

  @Override
  public void cancel(String executionId) {
    runner.cancel(executionId);
  }

  private TaskExecution convert(SyncTaskExecution execution) {
    return new TaskExecution(
        execution.executionId(),
        execution.status(),
        execution.errorMessage(),
        execution.output());
  }
}
