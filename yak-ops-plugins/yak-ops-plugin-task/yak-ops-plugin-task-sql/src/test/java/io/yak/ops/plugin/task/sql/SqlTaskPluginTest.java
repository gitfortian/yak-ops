package io.yak.ops.plugin.task.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SqlTaskPluginTest {

  @Test
  void validatesDatasourceAndExecutesThroughRuntimeCapability() throws Exception {
    SqlTaskPlugin plugin = new SqlTaskPlugin();
    TaskDefinition definition =
        new TaskDefinition(
            "SQL",
            1,
            "select 1 as value",
            "{\"dataSourceId\":\"42\",\"maxRows\":10,\"timeoutSeconds\":5}");
    AtomicReference<DataSourceSqlRequest> captured = new AtomicReference<>();
    DataSourceExecutionProvider provider =
        reference ->
            new DataSourceSqlExecutor() {
              @Override
              public DataSourceSqlResult execute(DataSourceSqlRequest request) {
                captured.set(request);
                return DataSourceSqlResult.query(
                    List.of(new DataSourceSqlColumn("VALUE", "value", "INTEGER", Types.INTEGER, true)),
                    List.of(List.of(1)),
                    false);
              }
            };

    TaskExecutionContext context =
        new TaskExecutionContext() {
          @Override
          public TaskExecutionTrigger trigger() {
            return TaskExecutionTrigger.MANUAL;
          }

          @Override
          public Map<String, Object> parameters() {
            return Map.of();
          }

          @Override
          public <T> Optional<T> capability(Class<T> capabilityType) {
            return capabilityType.isInstance(provider)
                ? Optional.of(capabilityType.cast(provider))
                : Optional.empty();
          }
        };

    assertTrue(plugin.descriptor().executable());
    assertTrue(plugin.descriptor().cancellable());
    assertTrue(plugin.validate(definition).valid());

    TaskExecutor executor = plugin.createExecutor(definition, context);
    TaskExecutionResult result = executor.execute();

    assertEquals(TaskExecutionStatus.SUCCESS, result.status());
    assertEquals("RESULT_SET", result.output().get("kind"));
    assertEquals(10, captured.get().maxRows());
    assertEquals(5, captured.get().timeoutSeconds());
  }

  @Test
  void rejectsMissingDatasourceReference() {
    SqlTaskPlugin plugin = new SqlTaskPlugin();
    TaskDefinition definition = new TaskDefinition("SQL", 1, "select 1", "{}");

    assertFalse(plugin.validate(definition).valid());
    assertTrue(
        plugin.validate(definition).issues().stream()
            .anyMatch(issue -> "SQL_DATASOURCE_REQUIRED".equals(issue.code())));
  }
}
