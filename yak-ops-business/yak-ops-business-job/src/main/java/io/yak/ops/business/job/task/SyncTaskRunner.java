package io.yak.ops.business.job.task;

/** 工作流调用现有数据同步执行链路的最小适配边界。 */
public interface SyncTaskRunner {

  SyncTaskExecution start(String taskId);

  /**
   * 按工作流发布时固定的任务版本快照启动。
   *
   * <p>默认实现保持其它 Runner/测试桩兼容；离线同步 Runner 会覆盖并使用快照配置执行。</p>
   */
  default SyncTaskExecution start(TaskVersionSnapshot snapshot) {
    return start(snapshot.taskId());
  }

  SyncTaskExecution status(String executionId);

  void cancel(String executionId);
}
