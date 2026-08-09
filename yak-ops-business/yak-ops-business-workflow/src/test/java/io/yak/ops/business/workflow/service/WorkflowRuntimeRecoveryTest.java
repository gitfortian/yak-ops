package io.yak.ops.business.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.framework.workflow.engine.support.InMemoryExecutionRepository;
import io.yak.framework.workflow.engine.support.InMemoryWorkflowDefinitionRepository;
import io.yak.ops.business.job.task.SyncTaskExecution;
import io.yak.ops.business.job.task.SyncTaskRunner;
import io.yak.ops.business.job.task.TaskDefinition;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.model.WorkflowInstanceVO;
import io.yak.ops.business.workflow.model.WorkflowRunRequest;
import io.yak.ops.business.workflow.model.WorkflowRunRequest.NodeRequest;
import io.yak.ops.business.workflow.persistence.InMemoryWorkflowRuntimePersistence;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkflowRuntimeRecoveryTest {

  private WorkflowRuntimeService first;
  private WorkflowRuntimeService second;

  @AfterEach
  void tearDown() {
    if (first != null) first.shutdown();
    if (second != null) second.shutdown();
  }

  @Test
  void shouldRestartPersistedSubmittedAttemptWithSameAttemptId()
      throws InterruptedException {
    InMemoryWorkflowDefinitionRepository definitions = new InMemoryWorkflowDefinitionRepository();
    InMemoryExecutionRepository executions = new InMemoryExecutionRepository();
    InMemoryWorkflowRuntimePersistence runtime = new InMemoryWorkflowRuntimePersistence();
    RecordingRunner runner = new RecordingRunner();
    TaskRegistry registry = registry();

    first = service(registry, runner, definitions, executions, runtime);
    WorkflowInstanceVO prepared = first.run(request());
    String attemptId = node(prepared).currentAttemptId();
    assertThat(runner.starts()).isZero();
    first.shutdown();
    first = null;

    second = service(registry, runner, definitions, executions, runtime);
    // Startup recovery pre-registers persisted executions as active before reconciliation.
    second.activate(prepared.id());
    assertThat(second.recoverPersistedExecutions()).isEqualTo(1);
    waitFor(() -> runner.starts() == 1);

    WorkflowInstanceVO recovered = second.getInstance(prepared.id());
    assertThat(node(recovered).currentAttemptId()).isEqualTo(attemptId);
    assertThat(node(recovered).attemptCount()).isEqualTo(1);
    assertThat(runner.idempotencyKey()).isEqualTo(attemptId);
  }

  @Test
  void shouldPollBoundRemoteExecutionWithoutStartingAnotherOne()
      throws InterruptedException {
    InMemoryWorkflowDefinitionRepository definitions = new InMemoryWorkflowDefinitionRepository();
    InMemoryExecutionRepository executions = new InMemoryExecutionRepository();
    InMemoryWorkflowRuntimePersistence runtime = new InMemoryWorkflowRuntimePersistence();
    RecordingRunner runner = new RecordingRunner();
    TaskRegistry registry = registry();

    first = service(registry, runner, definitions, executions, runtime);
    WorkflowInstanceVO prepared = first.run(request());
    first.activate(prepared.id());
    waitFor(() -> runner.starts() == 1);
    waitFor(() -> "RUNNING".equals(node(first.getInstance(prepared.id())).status()));
    String attemptId = node(first.getInstance(prepared.id())).currentAttemptId();
    first.shutdown();
    first = null;

    second = service(registry, runner, definitions, executions, runtime);
    second.activate(prepared.id());
    assertThat(second.recoverPersistedExecutions()).isEqualTo(1);
    waitFor(() -> runner.statusCalls() > 0);

    assertThat(runner.starts()).isEqualTo(1);
    assertThat(node(second.getInstance(prepared.id())).currentAttemptId()).isEqualTo(attemptId);
  }

  private WorkflowRuntimeService service(
      TaskRegistry registry,
      RecordingRunner runner,
      InMemoryWorkflowDefinitionRepository definitions,
      InMemoryExecutionRepository executions,
      InMemoryWorkflowRuntimePersistence runtime) {
    return new WorkflowRuntimeService(
        new WorkflowEventStreamService(),
        registry,
        runner,
        10_000L,
        definitions,
        executions,
        runtime);
  }

  private TaskRegistry registry() {
    return new TaskRegistry() {
      @Override
      public List<TaskDefinition> list() {
        return List.of(new TaskDefinition("task-a", "Task A", "SYNC"));
      }

      @Override
      public TaskDefinition get(String taskId) {
        return new TaskDefinition(taskId, "Task A", "SYNC");
      }
    };
  }

  private WorkflowRunRequest request() {
    return new WorkflowRunRequest(
        "recovery",
        List.of(new NodeRequest("a", "task-a")),
        List.of(),
        Map.of());
  }

  private WorkflowInstanceVO.NodeInstanceVO node(WorkflowInstanceVO instance) {
    return instance.nodes().stream()
        .filter(node -> "a".equals(node.id()))
        .findFirst()
        .orElseThrow();
  }

  private void waitFor(Check check) throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      if (check.done()) return;
      Thread.sleep(10L);
    }
    assertThat(check.done()).isTrue();
  }

  @FunctionalInterface
  private interface Check {
    boolean done();
  }

  private static final class RecordingRunner implements SyncTaskRunner {
    private final AtomicInteger starts = new AtomicInteger();
    private final AtomicInteger statusCalls = new AtomicInteger();
    private final ConcurrentMap<String, SyncTaskExecution> executions = new ConcurrentHashMap<>();
    private volatile String idempotencyKey;

    int starts() {
      return starts.get();
    }

    int statusCalls() {
      return statusCalls.get();
    }

    String idempotencyKey() {
      return idempotencyKey;
    }

    @Override
    public SyncTaskExecution start(String taskId) {
      return startInternal(null);
    }

    @Override
    public SyncTaskExecution start(TaskVersionSnapshot snapshot, String key) {
      idempotencyKey = key;
      return startInternal(key);
    }

    private SyncTaskExecution startInternal(String key) {
      int number = starts.incrementAndGet();
      String id = "remote-" + number;
      SyncTaskExecution execution = new SyncTaskExecution(
          id,
          "RUNNING",
          null,
          Map.of("idempotencyKey", key == null ? "" : key));
      executions.put(id, execution);
      return execution;
    }

    @Override
    public SyncTaskExecution status(String executionId) {
      statusCalls.incrementAndGet();
      SyncTaskExecution execution = executions.get(executionId);
      if (execution == null) {
        throw new IllegalArgumentException("execution not found: " + executionId);
      }
      return execution;
    }

    @Override
    public void cancel(String executionId) {
      // Not used by these recovery tests.
    }
  }
}
