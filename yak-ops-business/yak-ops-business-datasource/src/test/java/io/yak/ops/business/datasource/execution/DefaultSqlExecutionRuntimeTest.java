package io.yak.ops.business.datasource.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionContext;
import io.yak.ops.core.execution.sql.SqlExecutionPlan;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionResultType;
import io.yak.ops.core.execution.sql.SqlExecutionSnapshot;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlStatementRequest;
import io.yak.ops.core.execution.sql.SqlStatementStatus;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.sql.SQLTimeoutException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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

    DefaultSqlExecutionRuntime runtime = runtime(executor);
    SqlExecutionResult result = runtime.execute(request);
    runtime.shutdown();

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
    DefaultSqlExecutionRuntime runtime = runtime(executor);
    SqlExecutionResult result = runtime.execute(new SqlExecutionRequest(
        "9",
        "update demo set enabled = 1",
        10,
        30,
        SqlExecutionContext.of(SqlExecutionCaller.SQL_TASK, "task-1")));
    runtime.shutdown();

    assertEquals(SqlExecutionResultType.UPDATE_COUNT, result.type());
    assertFalse(result.resultSet());
    assertEquals(3L, result.affectedRows());
    assertEquals(0, result.returnedRows());
  }

  @Test
  void propagatesRuntimeDatasourceFailures() {
    CapturingExecutor executor = new CapturingExecutor(new IllegalStateException("driver failed"));
    DefaultSqlExecutionRuntime runtime = runtime(executor);
    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> runtime.execute(new SqlExecutionRequest(
            "9",
            "select 1",
            10,
            30,
            SqlExecutionContext.of(SqlExecutionCaller.DATA_SERVICE, "service-2"))));
    runtime.shutdown();

    assertEquals("driver failed", exception.getMessage());
  }

  @Test
  void tracksExplicitMultiStatementLifecycleAndResults() {
    AtomicInteger opened = new AtomicInteger();
    DataSourceExecutionProvider provider = dataSourceId -> {
      int index = opened.getAndIncrement();
      return request -> index == 0
          ? DataSourceSqlResult.query(
              List.of(new DataSourceSqlColumn("VALUE", "value", "INTEGER", Types.INTEGER, true)),
              List.of(List.of(1)),
              false)
          : DataSourceSqlResult.updateCount(2L);
    };
    DefaultSqlExecutionRuntime runtime = new DefaultSqlExecutionRuntime(provider);
    SqlExecutionPlan plan = new SqlExecutionPlan(
        "42",
        List.of(
            new SqlStatementRequest("select 1 as value", 10, 5),
            new SqlStatementRequest("update demo set enabled = 1", 10, 5)),
        SqlExecutionContext.of(SqlExecutionCaller.STATEMENT, "console-1"));

    SqlExecutionSnapshot started = runtime.start(plan);
    assertTrue(runtime.find(started.executionId()).isPresent());

    SqlExecutionSnapshot completed = runtime.await(started.executionId());
    runtime.shutdown();

    assertEquals(SqlExecutionStatus.SUCCEEDED, completed.status());
    assertEquals(2, completed.statements().size());
    assertEquals(SqlStatementStatus.SUCCEEDED, completed.statements().get(0).status());
    assertEquals(SqlExecutionResultType.RESULT_SET, completed.statements().get(0).result().type());
    assertEquals(SqlExecutionResultType.UPDATE_COUNT, completed.statements().get(1).result().type());
    assertEquals(2L, completed.statements().get(1).result().affectedRows());
    assertTrue(completed.statements().get(0).statementId().contains(completed.executionId()));
  }

  @Test
  void cancelsActiveStatementAndSkipsRemainingStatements() throws Exception {
    BlockingExecutor blocking = new BlockingExecutor();
    AtomicInteger opened = new AtomicInteger();
    DataSourceExecutionProvider provider = dataSourceId ->
        opened.getAndIncrement() == 0 ? blocking : request -> DataSourceSqlResult.updateCount(1L);
    DefaultSqlExecutionRuntime runtime = new DefaultSqlExecutionRuntime(provider);
    SqlExecutionSnapshot started = runtime.start(new SqlExecutionPlan(
        "42",
        List.of(
            new SqlStatementRequest("select sleep", 10, 30),
            new SqlStatementRequest("update should_not_run set value = 1", 10, 30)),
        SqlExecutionContext.of(SqlExecutionCaller.SQL_TASK, "task-1")));

    assertTrue(blocking.started.await(2, TimeUnit.SECONDS));
    assertTrue(runtime.cancel(started.executionId()));
    SqlExecutionSnapshot cancelled = runtime.await(started.executionId());
    assertFalse(runtime.cancel(started.executionId()));
    runtime.shutdown();

    assertEquals(SqlExecutionStatus.CANCELLED, cancelled.status());
    assertEquals(SqlStatementStatus.CANCELLED, cancelled.statements().get(0).status());
    assertEquals(SqlStatementStatus.SKIPPED, cancelled.statements().get(1).status());
    assertTrue(blocking.cancelled.get());
    assertEquals(1, opened.get());
  }

  @Test
  void promotesStatementTimeoutToExecutionTimeout() {
    DataSourceExecutionProvider provider = dataSourceId -> request -> {
      throw new IllegalStateException(new SQLTimeoutException("query timeout"));
    };
    DefaultSqlExecutionRuntime runtime = new DefaultSqlExecutionRuntime(provider);
    SqlExecutionSnapshot completed = runtime.await(runtime.start(new SqlExecutionRequest(
        "42",
        "select slow_query",
        10,
        1,
        SqlExecutionContext.of(SqlExecutionCaller.STATEMENT, "console-2"))).executionId());
    runtime.shutdown();

    assertEquals(SqlExecutionStatus.TIMED_OUT, completed.status());
    assertEquals(SqlStatementStatus.TIMED_OUT, completed.statements().get(0).status());
    assertTrue(completed.errorMessage().contains("SQLTimeoutException")
        || completed.errorMessage().contains("query timeout"));
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
    private final RuntimeException failure;
    private CapturedRequest request;
    private boolean closed;

    private CapturingExecutor(DataSourceSqlResult result) {
      this.result = result;
      this.failure = null;
    }

    private CapturingExecutor(RuntimeException failure) {
      this.result = null;
      this.failure = failure;
    }

    @Override
    public DataSourceSqlResult execute(DataSourceSqlRequest request) {
      this.request = new CapturedRequest(this.request.dataSourceId(), request.parameters());
      if (failure != null) throw failure;
      return result;
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  private static final class BlockingExecutor implements DataSourceSqlExecutor {
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Override
    public DataSourceSqlResult execute(DataSourceSqlRequest request) {
      started.countDown();
      try {
        released.await(2, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
      if (cancelled.get()) throw new IllegalStateException("SQL execution cancelled");
      return DataSourceSqlResult.query(List.of(), List.of(), false);
    }

    @Override
    public void cancel() {
      cancelled.set(true);
      released.countDown();
    }
  }
}
