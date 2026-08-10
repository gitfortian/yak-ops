package io.yak.ops.business.job.task;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Generic execution gateway shared by workflow and direct task runs. */
@Service
public class TaskExecutionGateway {

  private final Map<String, TaskExecutor> executors;

  public TaskExecutionGateway(List<TaskExecutor> taskExecutors) {
    Map<String, TaskExecutor> discovered = new LinkedHashMap<>();
    for (TaskExecutor executor : taskExecutors) {
      String type = normalize(executor.taskType());
      TaskExecutor existing = discovered.putIfAbsent(type, executor);
      if (existing != null) {
        throw new IllegalStateException(
            "重复的任务执行器：" + type + " -> "
                + existing.getClass().getName() + ", " + executor.getClass().getName());
      }
    }
    this.executors = Map.copyOf(discovered);
  }

  public boolean supports(String taskType) {
    return executors.containsKey(normalize(taskType));
  }

  public TaskExecution start(
      TaskVersionSnapshot snapshot,
      String idempotencyKey,
      Map<String, Object> input) {
    if (snapshot == null) {
      throw new IllegalArgumentException("任务版本快照不能为空");
    }
    return require(snapshot.type()).start(
        snapshot,
        idempotencyKey,
        input == null ? Map.of() : Map.copyOf(input));
  }

  public TaskExecution status(String taskType, String executionId) {
    return require(taskType).status(executionId);
  }

  public void cancel(String taskType, String executionId) {
    require(taskType).cancel(executionId);
  }

  private TaskExecutor require(String taskType) {
    String normalized = normalize(taskType);
    TaskExecutor executor = executors.get(normalized);
    if (executor == null) {
      throw new IllegalArgumentException("不支持的任务类型：" + normalized);
    }
    return executor;
  }

  private String normalize(String taskType) {
    if (taskType == null || taskType.isBlank()) {
      throw new IllegalArgumentException("taskType 不能为空");
    }
    return taskType.trim().toUpperCase(Locale.ROOT);
  }
}
