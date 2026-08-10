package io.yak.ops.business.job.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskExecutionGatewayTest {

  @Test
  void shouldRouteByTaskType() {
    TaskExecutionGateway gateway = new TaskExecutionGateway(List.of(new FakeExecutor("SQL")));
    TaskVersionSnapshot snapshot =
        new TaskVersionSnapshot("SQL:1", "demo", "sql", 1L, "digest", "{}", "{}");

    TaskExecution execution = gateway.start(snapshot, "attempt-1", Map.of("biz_date", "2026-08-10"));

    assertThat(execution.executionId()).isEqualTo("SQL-1");
    assertThat(gateway.supports("sql")).isTrue();
    assertThat(gateway.status("SQL", execution.executionId()).successful()).isTrue();
  }

  @Test
  void shouldRejectDuplicateTaskTypes() {
    assertThatThrownBy(() -> new TaskExecutionGateway(
            List.of(new FakeExecutor("SQL"), new FakeExecutor("sql"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("重复的任务执行器");
  }

  private static final class FakeExecutor implements TaskExecutor {
    private final String type;

    private FakeExecutor(String type) {
      this.type = type;
    }

    @Override
    public String taskType() {
      return type;
    }

    @Override
    public TaskExecution start(
        TaskVersionSnapshot snapshot,
        String idempotencyKey,
        Map<String, Object> input) {
      return new TaskExecution("SQL-1", "SUCCEEDED", null, input);
    }

    @Override
    public TaskExecution status(String executionId) {
      return new TaskExecution(executionId, "SUCCEEDED", null, Map.of());
    }

    @Override
    public void cancel(String executionId) {}
  }
}
