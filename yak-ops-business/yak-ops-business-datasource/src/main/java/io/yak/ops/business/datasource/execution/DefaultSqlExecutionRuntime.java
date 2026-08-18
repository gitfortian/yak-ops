package io.yak.ops.business.datasource.execution;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.execution.sql.SqlExecutionColumn;
import io.yak.ops.core.execution.sql.SqlExecutionException;
import io.yak.ops.core.execution.sql.SqlExecutionPlan;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionResultType;
import io.yak.ops.core.execution.sql.SqlExecutionRuntime;
import io.yak.ops.core.execution.sql.SqlExecutionSnapshot;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlExecutionTiming;
import io.yak.ops.core.execution.sql.SqlStatementRequest;
import io.yak.ops.core.execution.sql.SqlStatementSnapshot;
import io.yak.ops.core.execution.sql.SqlStatementStatus;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import jakarta.annotation.PreDestroy;
import java.sql.SQLTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Default SQL runtime backed by the existing datasource execution SPI. */
@Component
@ConditionalOnDataSourceEnabled
public final class DefaultSqlExecutionRuntime implements SqlExecutionRuntime {

  private static final int MAX_COMPLETED_EXECUTIONS = 512;

  private final DataSourceExecutionProvider executionProvider;
  private final ExecutorService lifecycleExecutor;
  private final ConcurrentMap<String, ManagedExecution> executions = new ConcurrentHashMap<>();
  private final ConcurrentLinkedDeque<String> completedOrder = new ConcurrentLinkedDeque<>();

  public DefaultSqlExecutionRuntime(DataSourceExecutionProvider executionProvider) {
    this.executionProvider = executionProvider;
    this.lifecycleExecutor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @Override
  public SqlExecutionResult execute(SqlExecutionRequest request) {
    return executeOne(request, null);
  }

  @Override
  public SqlExecutionSnapshot start(SqlExecutionPlan plan) {
    String executionId = "sql-" + UUID.randomUUID();
    ManagedExecution execution = new ManagedExecution(executionId, plan);
    executions.put(executionId, execution);
    try {
      lifecycleExecutor.submit(() -> run(execution));
    } catch (RuntimeException exception) {
      execution.failBeforeStart(exception);
      retainCompleted(executionId);
    }
    return execution.snapshot();
  }

  @Override
  public Optional<SqlExecutionSnapshot> find(String executionId) {
    if (executionId == null || executionId.isBlank()) return Optional.empty();
    ManagedExecution execution = executions.get(executionId.trim());
    return execution == null ? Optional.empty() : Optional.of(execution.snapshot());
  }

  @Override
  public SqlExecutionSnapshot await(String executionId) {
    ManagedExecution execution = requireExecution(executionId);
    try {
      return execution.completion().join();
    } catch (RuntimeException exception) {
      return execution.snapshot();
    }
  }

  @Override
  public boolean cancel(String executionId) {
    ManagedExecution execution = executions.get(normalizeExecutionId(executionId));
    if (execution == null || !execution.requestCancel()) return false;
    DataSourceSqlExecutor active = execution.activeExecutor().get();
    if (active != null) {
      try {
        active.cancel();
      } catch (RuntimeException ignored) {
        // Cancellation is best-effort. The worker will still observe cancelRequested.
      }
    }
    return true;
  }

  @PreDestroy
  void shutdown() {
    lifecycleExecutor.shutdownNow();
  }

  private void run(ManagedExecution execution) {
    execution.markRunning();
    List<SqlStatementRequest> statements = execution.plan().statements();
    for (int index = 0; index < statements.size(); index++) {
      if (execution.cancelRequested()) {
        execution.finishCancelled(index, "SQL execution cancelled");
        retainCompleted(execution.executionId());
        return;
      }

      SqlStatementRequest statement = statements.get(index);
      execution.markStatementRunning(index);
      SqlExecutionRequest request = new SqlExecutionRequest(
          execution.plan().dataSourceId(),
          statement.sql(),
          statement.parameters(),
          statement.maxRows(),
          statement.timeoutSeconds(),
          execution.plan().context());
      try {
        SqlExecutionResult result = executeOne(request, execution.activeExecutor());
        execution.markStatementSucceeded(index, result);
      } catch (RuntimeException exception) {
        if (execution.cancelRequested()) {
          execution.markStatementCancelled(index, safeMessage(exception));
          execution.finishCancelled(index + 1, safeMessage(exception));
        } else if (causedBy(exception, SQLTimeoutException.class)) {
          execution.markStatementTimedOut(index, safeMessage(exception));
          execution.finishTimedOut(index + 1, safeMessage(exception));
        } else {
          execution.markStatementFailed(index, safeMessage(exception));
          execution.finishFailed(index + 1, safeMessage(exception));
        }
        retainCompleted(execution.executionId());
        return;
      }
    }

    if (execution.cancelRequested()) {
      execution.finishCancelled(statements.size(), "SQL execution cancelled");
    } else {
      execution.finishSucceeded();
    }
    retainCompleted(execution.executionId());
  }

  private SqlExecutionResult executeOne(
      SqlExecutionRequest request,
      AtomicReference<DataSourceSqlExecutor> activeExecutor) {
    long totalStartedAt = System.nanoTime();
    long openStartedAt = System.nanoTime();
    DataSourceSqlExecutor executor = executionProvider.open(request.dataSourceId());
    long openMillis = elapsedMillis(openStartedAt);
    if (activeExecutor != null) activeExecutor.set(executor);

    long executeStartedAt = System.nanoTime();
    DataSourceSqlResult result;
    try (executor) {
      result = executor.execute(new DataSourceSqlRequest(
          request.sql(), request.maxRows(), request.timeoutSeconds(), request.parameters()));
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new SqlExecutionException(request.dataSourceId(), request.context(), exception);
    } finally {
      if (activeExecutor != null) activeExecutor.compareAndSet(executor, null);
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

  private ManagedExecution requireExecution(String executionId) {
    String normalized = normalizeExecutionId(executionId);
    ManagedExecution execution = executions.get(normalized);
    if (execution == null) {
      throw new IllegalArgumentException("SQL execution not found: " + normalized);
    }
    return execution;
  }

  private String normalizeExecutionId(String executionId) {
    if (executionId == null || executionId.isBlank()) {
      throw new IllegalArgumentException("executionId must not be blank");
    }
    return executionId.trim();
  }

  private void retainCompleted(String executionId) {
    completedOrder.addLast(executionId);
    while (completedOrder.size() > MAX_COMPLETED_EXECUTIONS) {
      String expired = completedOrder.pollFirst();
      if (expired != null) executions.remove(expired);
    }
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

  private static boolean causedBy(Throwable throwable, Class<? extends Throwable> expected) {
    Throwable current = throwable;
    while (current != null) {
      if (expected.isInstance(current)) return true;
      current = current.getCause();
    }
    return false;
  }

  private static String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "SQL execution failed" : throwable.getClass().getSimpleName();
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }

  private static long elapsedMillis(long startedAt) {
    return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
  }

  private static final class ManagedExecution {

    private final String executionId;
    private final SqlExecutionPlan plan;
    private final List<MutableStatement> statements;
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final AtomicReference<DataSourceSqlExecutor> activeExecutor = new AtomicReference<>();
    private final CompletableFuture<SqlExecutionSnapshot> completion = new CompletableFuture<>();
    private SqlExecutionStatus status = SqlExecutionStatus.PENDING;
    private Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;

    private ManagedExecution(String executionId, SqlExecutionPlan plan) {
      this.executionId = executionId;
      this.plan = plan;
      this.statements = new ArrayList<>(plan.statements().size());
      for (int index = 0; index < plan.statements().size(); index++) {
        this.statements.add(new MutableStatement(
            executionId + ":stmt:" + (index + 1),
            index,
            plan.statements().get(index).sql()));
      }
    }

    String executionId() {
      return executionId;
    }

    SqlExecutionPlan plan() {
      return plan;
    }

    AtomicReference<DataSourceSqlExecutor> activeExecutor() {
      return activeExecutor;
    }

    CompletableFuture<SqlExecutionSnapshot> completion() {
      return completion;
    }

    boolean cancelRequested() {
      return cancelRequested.get();
    }

    synchronized void markRunning() {
      if (status.terminal()) return;
      startedAt = Instant.now();
      status = cancelRequested.get() ? SqlExecutionStatus.CANCELLING : SqlExecutionStatus.RUNNING;
    }

    synchronized void markStatementRunning(int index) {
      MutableStatement statement = statements.get(index);
      statement.status = SqlStatementStatus.RUNNING;
      statement.startedAt = Instant.now();
    }

    synchronized void markStatementSucceeded(int index, SqlExecutionResult result) {
      MutableStatement statement = statements.get(index);
      statement.result = result;
      statement.status = SqlStatementStatus.SUCCEEDED;
      statement.finishedAt = Instant.now();
    }

    synchronized void markStatementFailed(int index, String message) {
      finishStatement(index, SqlStatementStatus.FAILED, message);
    }

    synchronized void markStatementTimedOut(int index, String message) {
      finishStatement(index, SqlStatementStatus.TIMED_OUT, message);
    }

    synchronized void markStatementCancelled(int index, String message) {
      finishStatement(index, SqlStatementStatus.CANCELLED, message);
    }

    private void finishStatement(int index, SqlStatementStatus statementStatus, String message) {
      MutableStatement statement = statements.get(index);
      statement.status = statementStatus;
      statement.errorMessage = message;
      statement.finishedAt = Instant.now();
    }

    synchronized boolean requestCancel() {
      if (status.terminal()) return false;
      cancelRequested.set(true);
      status = SqlExecutionStatus.CANCELLING;
      return true;
    }

    synchronized void finishSucceeded() {
      complete(SqlExecutionStatus.SUCCEEDED, null, statements.size());
    }

    synchronized void finishFailed(int skipFrom, String message) {
      complete(SqlExecutionStatus.FAILED, message, skipFrom);
    }

    synchronized void finishTimedOut(int skipFrom, String message) {
      complete(SqlExecutionStatus.TIMED_OUT, message, skipFrom);
    }

    synchronized void finishCancelled(int skipFrom, String message) {
      complete(SqlExecutionStatus.CANCELLED, message, skipFrom);
    }

    synchronized void failBeforeStart(Throwable throwable) {
      startedAt = Instant.now();
      complete(SqlExecutionStatus.FAILED, safeMessage(throwable), 0);
    }

    private void complete(SqlExecutionStatus finalStatus, String message, int skipFrom) {
      if (status.terminal()) return;
      Instant now = Instant.now();
      for (int index = Math.max(0, skipFrom); index < statements.size(); index++) {
        MutableStatement statement = statements.get(index);
        if (statement.status == SqlStatementStatus.PENDING) {
          statement.status = SqlStatementStatus.SKIPPED;
          statement.finishedAt = now;
          statement.errorMessage = message;
        }
      }
      status = finalStatus;
      errorMessage = message;
      finishedAt = now;
      SqlExecutionSnapshot snapshot = snapshot();
      completion.complete(snapshot);
    }

    synchronized SqlExecutionSnapshot snapshot() {
      List<SqlStatementSnapshot> snapshots = statements.stream()
          .map(MutableStatement::snapshot)
          .toList();
      return new SqlExecutionSnapshot(
          executionId,
          status,
          plan.dataSourceId(),
          plan.context(),
          snapshots,
          startedAt,
          finishedAt,
          errorMessage);
    }
  }

  private static final class MutableStatement {
    private final String statementId;
    private final int index;
    private final String sql;
    private SqlStatementStatus status = SqlStatementStatus.PENDING;
    private SqlExecutionResult result;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;

    private MutableStatement(String statementId, int index, String sql) {
      this.statementId = statementId;
      this.index = index;
      this.sql = sql;
    }

    private SqlStatementSnapshot snapshot() {
      return new SqlStatementSnapshot(
          statementId,
          index,
          sql,
          status,
          result,
          errorMessage,
          startedAt,
          finishedAt);
    }
  }
}
