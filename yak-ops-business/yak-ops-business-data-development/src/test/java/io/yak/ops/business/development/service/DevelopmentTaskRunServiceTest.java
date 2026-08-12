package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRunResult;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskExecutor;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginDescriptor;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DevelopmentTaskRunServiceTest {

  private DevelopmentTaskRunService service;

  @BeforeEach
  void setUp() {
    DevelopmentNodeRepository nodeRepository = mock(DevelopmentNodeRepository.class);
    Instant now = Instant.parse("2026-08-12T00:00:00Z");
    when(nodeRepository.findById(1L))
        .thenReturn(
            Optional.of(
                new DevelopmentNode(1L, "今天统计", "SQL", null, null, true, now, now)));

    @SuppressWarnings("unchecked")
    ObjectProvider<DataSourceExecutionProvider> provider = mock(ObjectProvider.class);
    service =
        new DevelopmentTaskRunService(
            nodeRepository,
            TaskPluginRegistry.from(List.of(new TestExecutablePlugin())),
            provider,
            new ObjectMapper());
  }

  @Test
  void runsCurrentDefinitionWithoutDraftOrRevisionDependency() {
    DevelopmentTaskRunResult result =
        service.run(1L, "sql", 1, "select 42", "{\"dataSourceId\":\"7\"}");

    assertEquals(TaskExecutionStatus.SUCCESS, result.status());
    assertEquals("select 42", result.output().get("sql"));
    assertEquals("MANUAL", result.output().get("trigger"));
    assertTrue(result.durationMs() >= 0L);
  }

  private static final class TestExecutablePlugin implements TaskPlugin {

    @Override
    public TaskPluginDescriptor descriptor() {
      return new TaskPluginDescriptor("SQL", "SQL", "test", "1.0.0", 1, true, false);
    }

    @Override
    public TaskValidationResult validate(TaskDefinition definition) {
      return TaskValidationResult.ok();
    }

    @Override
    public TaskExecutor createExecutor(
        TaskDefinition definition,
        TaskExecutionContext context) {
      return () ->
          TaskExecutionResult.success(
              Map.of(
                  "sql", definition.content(),
                  "trigger", context.trigger().name()));
    }
  }
}
