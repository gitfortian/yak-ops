package io.yak.ops.business.datasource.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionContext;
import io.yak.ops.core.execution.sql.SqlExecutionException;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionResultType;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultSqlExecutionRuntimeTest {

  @Test
  void mapsResultSetAndPreservesNullableSqlValues() {
    CapturingExecutor executor = new CapturingExecutor(new DataSourceSqlResult(
        true,
        List.of(new DataSourceSqlColumn("name", "name", "VARCHAR", Types.VARCHAR, true)),
        List.of(Arrays.asList("Yak", null)),
        0L,
        false));
    SqlExecutionRequest request = new SqlExecutionRequest(
        " 12 ",
        " select name from demo where id = ? and deleted_at is ? ",
        Arrays.asList(7L, null),
        100,
        30,
        SqlExecutionContext.of(SqlExecutionCaller.DATASET, "42"));

    SqlExecutionResult result = runtime(executor).execute(request);

    assertEquals(SqlExecutionResultType.RESULT_SET, result.type());
    assertTrue(result.resultSet());
    assertEquals(1, result.returnedRows());
    assertEquals("name", result.columns().get(0).label());
    assertNull(result.rows().get(0).get(1));
    assertEquals("12", executor.request.dataSourceId());
    assertNull(executor.request.parameters().get(1));
    assertTrue(executor.closed);
  }

  @Test
  void mapsUpdateCountWithoutPretendingItIsAQueryResult() {
    CapturingExecutor executor = new CapturingExecutor(DataSourceSqlResult.updateCount(3L));
    SqlExecutionResult result = runtime(executor).execute(new SqlExecutionRequest(
        "9",
        "update demo set enabled = 1",
        10,
        30,
        SqlExecutionContext.of(SqlExecutionCaller.SQL_TASK, "task-1")));

    assertEquals(SqlExecutionResultType.UPDATE_COUNT, result.type());
    assertFalse(result.resultSet());
    assertEquals(3L, result.affectedRows());
    assertEquals(0, result.returnedRows());
  }

  @Test
  void wrapsCheckedDatasourceFailuresAndKeepsTheCause() {
    CapturingExecutor executor = new CapturingExecutor(new Exception("driver failed"));
    SqlExecutionException exception = assertThrows(
        SqlExecutionException.class,
        () -> runtime(executor).execute(new SqlExecutionRequest(
            "9",
            "select 1",
            10,
            30,
            SqlExecutionContext.of(SqlExecutionCaller.DATA_SERVICE, "service-2"))));

    assertEquals("9", exception.dataSourceId());
    assertEquals(SqlExecutionCaller.DATA_SERVICE, exception.context().caller());
    assertInstanceOf(Exception.class, exception.getCause());
    assertEquals("driver failed", exception.getCause().getMessage());
  }

  private static DefaultSqlExecutionRuntime runtime(CapturingExecutor executor) {
    DataSourceExecutionProvider provider = dataSourceId -> {
      executor.request = new CapturedRequest(dataSourceId, List.of());
      return executor;
    };
    return new DefaultSqlExecutionRuntime(provider);
  }

  private record CapturedRequest(String dataSourceId, List<Object> parameters) {}

  private static final class CapturingExecutor implements DataSourceSqlExecutor {
    private final DataSourceSqlResult result;
    private final Exception failure;
    private CapturedRequest request;
    private boolean closed;

    private CapturingExecutor(DataSourceSqlResult result) {
      this.result = result;
      this.failure = null;
    }

    private CapturingExecutor(Exception failure) {
      this.result = null;
      this.failure = failure;
    }

    @Override
    public DataSourceSqlResult execute(DataSourceSqlRequest request) throws Exception {
      this.request = new CapturedRequest(this.request.dataSourceId(), request.parameters());
      if (failure != null) throw failure;
      return result;
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
