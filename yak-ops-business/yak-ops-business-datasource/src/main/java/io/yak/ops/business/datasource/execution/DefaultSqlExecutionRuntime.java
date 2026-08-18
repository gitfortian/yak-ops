package io.yak.ops.business.datasource.execution;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.execution.sql.SqlExecutionColumn;
import io.yak.ops.core.execution.sql.SqlExecutionException;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionResultType;
import io.yak.ops.core.execution.sql.SqlExecutionRuntime;
import io.yak.ops.core.execution.sql.SqlExecutionTiming;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.util.List;
import org.springframework.stereotype.Component;

/** Default SQL runtime backed by the existing datasource execution SPI. */
@Component
@ConditionalOnDataSourceEnabled
public final class DefaultSqlExecutionRuntime implements SqlExecutionRuntime {

  private final DataSourceExecutionProvider executionProvider;

  public DefaultSqlExecutionRuntime(DataSourceExecutionProvider executionProvider) {
    this.executionProvider = executionProvider;
  }

  @Override
  public SqlExecutionResult execute(SqlExecutionRequest request) {
    long totalStartedAt = System.nanoTime();
    long openStartedAt = System.nanoTime();
    DataSourceSqlExecutor executor = executionProvider.open(request.dataSourceId());
    long openMillis = elapsedMillis(openStartedAt);

    long executeStartedAt = System.nanoTime();
    DataSourceSqlResult result;
    try (executor) {
      result = executor.execute(new DataSourceSqlRequest(
          request.sql(), request.maxRows(), request.timeoutSeconds(), request.parameters()));
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new SqlExecutionException(request.dataSourceId(), request.context(), exception);
    }
    long executeMillis = elapsedMillis(executeStartedAt);
    long totalMillis = elapsedMillis(totalStartedAt);

    return new SqlExecutionResult(
        result.resultSet() ? SqlExecutionResultType.RESULT_SET : SqlExecutionResultType.UPDATE_COUNT,
        mapColumns(result.columns()),
        result.rows(),
        result.affectedRows(),
        result.truncated(),
        new SqlExecutionTiming(openMillis, executeMillis, totalMillis));
  }

  private static List<SqlExecutionColumn> mapColumns(List<DataSourceSqlColumn> columns) {
    return columns.stream()
        .map(column -> new SqlExecutionColumn(
            column.name(),
            column.label(),
            column.typeName(),
            column.jdbcType(),
            column.nullable()))
        .toList();
  }

  private static long elapsedMillis(long startedAt) {
    return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
  }
}
