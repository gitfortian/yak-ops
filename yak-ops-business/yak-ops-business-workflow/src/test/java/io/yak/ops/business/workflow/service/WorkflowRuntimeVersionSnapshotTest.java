package io.yak.ops.business.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.job.task.SyncTaskExecution;
import io.yak.ops.business.job.task.SyncTaskRunner;
import io.yak.ops.business.job.task.TaskDefinition;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.model.WorkflowInstanceVO;
import io.yak.ops.business.workflow.model.WorkflowRunRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkflowRuntimeVersionSnapshotTest {

  private WorkflowRuntimeService service;

  @AfterEach
  void tearDown() {
    if (service != null) service.shutdown();
  }

  @Test
  void shouldExecutePinnedSnapshotWithoutReadingCurrentTaskRegistry() throws InterruptedException {
    TaskRegistry registry = new TaskRegistry() {
      @Override public List<TaskDefinition> list() { return List.of(); }
      @Override public TaskDefinition get(String taskId) {
        throw new AssertionError("published runtime must not read current TaskRegistry");
      }
    };
    SnapshotRunner runner = new SnapshotRunner();
    service = new WorkflowRuntimeService(new WorkflowEventStreamService(), registry, runner, 2L);
    TaskVersionSnapshot pinned = new TaskVersionSnapshot(
        "task-a", "订单同步", "SYNC", 17L, "digest-17", "{\"definition\":17}", "{\"jobSpec\":17}");
    WorkflowRunRequest request = new WorkflowRunRequest(
        "published-v3",
        List.of(new WorkflowRunRequest.NodeRequest("a", "task-a")),
        List.of(),
        Map.of());

    WorkflowInstanceVO prepared = service.run(request, Map.of("a", pinned), "version-3", 3, false);
    service.activate(prepared.id());
    WorkflowInstanceVO completed = waitForTerminal(prepared.id());

    assertThat(completed.status()).isEqualTo("SUCCESS");
    assertThat(completed.workflowVersionId()).isEqualTo("version-3");
    assertThat(completed.workflowVersionNo()).isEqualTo(3);
    assertThat(completed.testRun()).isFalse();
    assertThat(runner.snapshot.get()).isEqualTo(pinned);
    assertThat(completed.nodes().get(0).output()).containsEntry("taskVersion", 17L);
  }

  @Test
  void shouldPollRemoteStatusAsScheduledShortOperations() throws InterruptedException {
    TaskRegistry registry = new TaskRegistry() {
      @Override public List<TaskDefinition> list() { return List.of(); }
      @Override public TaskDefinition get(String taskId) { return new TaskDefinition(taskId, taskId, "SYNC"); }
    };
    SnapshotRunner runner = new SnapshotRunner();
    runner.runningPolls.set(4);
    service = new WorkflowRuntimeService(new WorkflowEventStreamService(), registry, runner, 2L);
    TaskVersionSnapshot pinned = new TaskVersionSnapshot("task-a", "A", "SYNC", 2L, null, "{}", "{}");
    WorkflowRunRequest request = new WorkflowRunRequest(
        "polling",
        List.of(new WorkflowRunRequest.NodeRequest("a", "task-a")),
        List.of(),
        Map.of());

    WorkflowInstanceVO prepared = service.run(request, Map.of("a", pinned), "version-1", 1, false);
    service.activate(prepared.id());
    WorkflowInstanceVO completed = waitForTerminal(prepared.id());

    assertThat(completed.status()).isEqualTo("SUCCESS");
    assertThat(runner.statusCalls.get()).isGreaterThanOrEqualTo(5);
  }

  private WorkflowInstanceVO waitForTerminal(String executionId) throws InterruptedException {
    for (int i = 0; i < 500; i++) {
      WorkflowInstanceVO current = service.getInstance(executionId);
      if (List.of("SUCCESS", "FAILED", "CANCELED", "TIMED_OUT").contains(current.status())) return current;
      Thread.sleep(5L);
    }
    return service.getInstance(executionId);
  }

  private static final class SnapshotRunner implements SyncTaskRunner {
    private final AtomicReference<TaskVersionSnapshot> snapshot = new AtomicReference<>();
    private final AtomicInteger statusCalls = new AtomicInteger();
    private final AtomicInteger runningPolls = new AtomicInteger();

    @Override
    public SyncTaskExecution start(String taskId) {
      throw new AssertionError("versioned runtime must call start(TaskVersionSnapshot)");
    }

    @Override
    public SyncTaskExecution start(TaskVersionSnapshot value) {
      snapshot.set(value);
      return new SyncTaskExecution("sync-1", "RUNNING", null, Map.of());
    }

    @Override
    public SyncTaskExecution status(String executionId) {
      int call = statusCalls.incrementAndGet();
      String status = call <= runningPolls.get() ? "RUNNING" : "SUCCEEDED";
      return new SyncTaskExecution(executionId, status, null, Map.of("calls", call));
    }

    @Override
    public void cancel(String executionId) {}
  }
}
