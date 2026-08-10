package io.yak.ops.business.job.task;

import java.util.Map;
import org.springframework.stereotype.Service;

/** Keeps the existing offline-sync runner behind the generic task execution contract. */
@Service
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
