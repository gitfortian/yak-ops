package io.yak.ops.business.job.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginDescriptor;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class SqlTaskExecutorAdapterTest {

  private SqlTaskExecutorAdapter adapter;

  @AfterEach
  void tearDown() {
    if (adapter != null) adapter.shutdown();
  }

  @Test
  void executesDefinitionFromImmutableSnapshot() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    RecordingSqlPlugin plugin = new RecordingSqlPlugin(
        TaskExecutionResult.success(Map.of("version", "v1")));
    adapter = new SqlTaskExecutorAdapter(
        TaskPluginRegistry.from(List.of(plugin)),
        emptyDataSourceProvider(),
        objectMapper);

    TaskDefinition frozenV1 = new TaskDefinition(
        "SQL",
        1,
        "select 1 as version",
        "{\"dataSourceId\":\"1\"}");
    TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
        "task-asset:12",
        "今天统计",
        "SQL",
        1L,
        "checksum-v1",
        objectMapper.writeValueAsString(frozenV1),
        frozenV1.configJson());

    TaskExecution started = adapter.start(snapshot, "attempt-1", Map.of("bizDate", "2026-08-12"));
    TaskExecution completed = awaitTerminal(started.executionId());

    assertTrue(completed.successful());
    assertEquals("v1", completed.output().get("version"));
    assertEquals("select 1 as version", plugin.definition.get().content());
    assertEquals("2026-08-12", plugin.context.get().parameters().get("bizDate"));
  }

  @Test
  void rejectsMissingImmutableSnapshotWithoutFallback() {
    adapter = new SqlTaskExecutorAdapter(
        TaskPluginRegistry.from(List.of(new RecordingSqlPlugin(TaskExecutionResult.success(Map.of())))),
        emptyDataSourceProvider(),
        new ObjectMapper());
    TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
        "task-asset:12", "今天统计", "SQL", 1L, "checksum-v1", null, null);

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> adapter.start(snapshot, "attempt-1", Map.of()));

    assertTrue(exception.getMessage().contains("拒绝回退"));
  }

  @Test
  void reusesExecutionForSameWorkflowAttempt() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    RecordingSqlPlugin plugin = new RecordingSqlPlugin(
        TaskExecutionResult.success(Map.of("ok", true)));
    adapter = new SqlTaskExecutorAdapter(
        TaskPluginRegistry.from(List.of(plugin)),
        emptyDataSourceProvider(),
        objectMapper);
    TaskDefinition definition = new TaskDefinition("SQL", 1, "select 1", "{}");
    TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
        "task-asset:12",
        "SQL",
        "SQL",
        1L,
        "checksum-v1",
        objectMapper.writeValueAsString(definition),
        definition.configJson());

    TaskExecution first = adapter.start(snapshot, "same-attempt", Map.of());
    TaskExecution second = adapter.start(snapshot, "same-attempt", Map.of());

    assertEquals(first.executionId(), second.executionId());
    assertSame(plugin.definition.get(), plugin.definition.get());
  }

  private TaskExecution awaitTerminal(String executionId) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
    TaskExecution current = adapter.status(executionId);
    while (!current.terminal() && Instant.now().isBefore(deadline)) {
      Thread.sleep(10L);
      current = adapter.status(executionId);
    }
    assertTrue(current.terminal(), "SQL execution should become terminal");
    return current;
  }

  @SuppressWarnings("unchecked")
  private ObjectProvider<DataSourceExecutionProvider> emptyDataSourceProvider() {
    ObjectProvider<DataSourceExecutionProvider> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);
    return provider;
  }

  private static final class RecordingSqlPlugin implements TaskPlugin {
    private final TaskExecutionResult result;
    private final AtomicReference<TaskDefinition> definition = new AtomicReference<>();
    private final AtomicReference<TaskExecutionContext> context = new AtomicReference<>();

    private RecordingSqlPlugin(TaskExecutionResult result) {
      this.result = result;
    }

    @Override
    public TaskPluginDescriptor descriptor() {
      return new TaskPluginDescriptor(
          "SQL", "SQL", "test SQL plugin", "1.0.0", 1, true, true);
    }

    @Override
    public TaskValidationResult validate(TaskDefinition definition) {
      return TaskValidationResult.ok();
    }

    @Override
    public io.yak.ops.plugin.task.api.TaskExecutor createExecutor(
        TaskDefinition definition,
        TaskExecutionContext context) {
      this.definition.set(definition);
      this.context.set(context);
      return new io.yak.ops.plugin.task.api.TaskExecutor() {
        @Override
        public TaskExecutionResult execute() {
          return result;
        }
      };
    }
  }
}
