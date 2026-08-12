package io.yak.ops.plugin.task.sql;

import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import java.sql.SQLTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** One physical SQL task execution attempt. */
final class SqlTaskExecutor implements TaskExecutor {

  private final TaskDefinition definition;
  private final SqlTaskConfig config;
  private final DataSourceExecutionProvider dataSourceProvider;
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicReference<DataSourceSqlExecutor> activeExecutor = new AtomicReference<>();

  SqlTaskExecutor(
      TaskDefinition definition,
      SqlTaskConfig config,
      DataSourceExecutionProvider dataSourceProvider) {
    this.definition = definition;
    this.config = config;
    this.dataSourceProvider = dataSourceProvider;
  }

  @Override
  public TaskExecutionResult execute() {
    if (cancelled.get()) {
      return cancelledResult("SQL execution was cancelled before start");
    }

    try (DataSourceSqlExecutor executor = dataSourceProvider.open(config.dataSourceId())) {
      activeExecutor.set(executor);
      if (cancelled.get()) {
        executor.cancel();
        return cancelledResult("SQL execution was cancelled before statement execution");
      }

      DataSourceSqlResult result =
          executor.execute(
              new DataSourceSqlRequest(
                  definition.content(),
                  config.maxRows(),
                  config.timeoutSeconds()));
      Map<String, Object> output = new LinkedHashMap<>();
      output.put("kind", result.resultSet() ? "RESULT_SET" : "UPDATE_COUNT");
      output.put("columns", result.columns());
      output.put("rows", result.rows());
      output.put("returnedRows", result.returnedRows());
      output.put("affectedRows", result.affectedRows());
      output.put("truncated", result.truncated());
      output.put("dataSourceId", config.dataSourceId());
      return TaskExecutionResult.success(output);
    } catch (Exception exception) {
      if (cancelled.get()) {
        return cancelledResult(safeMessage(exception));
      }
      TaskExecutionStatus status =
          causedBy(exception, SQLTimeoutException.class)
              ? TaskExecutionStatus.TIMEOUT
              : TaskExecutionStatus.FAILED;
      return new TaskExecutionResult(
          status,
          safeMessage(exception),
          Map.of("dataSourceId", config.dataSourceId()));
    } finally {
      activeExecutor.set(null);
    }
  }

  @Override
  public void cancel() {
    cancelled.set(true);
    DataSourceSqlExecutor executor = activeExecutor.get();
    if (executor != null) executor.cancel();
  }

  private TaskExecutionResult cancelledResult(String message) {
    return new TaskExecutionResult(
        TaskExecutionStatus.CANCELLED,
        message == null ? "SQL execution cancelled" : message,
        Map.of("dataSourceId", config.dataSourceId()));
  }

  private boolean causedBy(Throwable throwable, Class<? extends Throwable> expected) {
    Throwable current = throwable;
    while (current != null) {
      if (expected.isInstance(current)) return true;
      current = current.getCause();
    }
    return false;
  }

  private String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "SQL execution failed" : throwable.getClass().getSimpleName();
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }
}
