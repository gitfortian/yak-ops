package io.yak.ops.business.job.task;

/** Legacy Workflow-test compatibility boundary; production SYNC uses {@link TaskExecutor}. */
@Deprecated(forRemoval = true)
public interface SyncTaskRunner {

  SyncTaskExecution start(String taskId);

  default SyncTaskExecution start(TaskVersionSnapshot snapshot) {
    return start(snapshot.taskId());
  }

  default SyncTaskExecution start(TaskVersionSnapshot snapshot, String idempotencyKey) {
    return start(snapshot);
  }

  SyncTaskExecution status(String executionId);

  void cancel(String executionId);
}
