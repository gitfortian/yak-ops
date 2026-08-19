package io.yak.ops.business.datasource.execution;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.execution.sql.LexicalSqlStatementClassifier;
import io.yak.ops.core.execution.sql.SqlExecutionColumn;
import io.yak.ops.core.execution.sql.SqlExecutionException;
import io.yak.ops.core.execution.sql.SqlExecutionPlan;
import io.yak.ops.core.execution.sql.SqlExecutionPolicy;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionResultType;
import io.yak.ops.core.execution.sql.SqlExecutionRuntime;
import io.yak.ops.core.execution.sql.SqlExecutionSnapshot;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlExecutionTiming;
import io.yak.ops.core.execution.sql.SqlStatementClassification;
import io.yak.ops.core.execution.sql.SqlStatementClassifier;
import io.yak.ops.core.execution.sql.SqlStatementRequest;
import io.yak.ops.core.execution.sql.SqlStatementSnapshot;
import io.yak.ops.core.execution.sql.SqlStatementStatus;
import io.yak.ops.core.execution.sql.SqlStatementType;
import io.yak.ops.core.execution.sql.SqlTransactionMode;
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
import java.util.Objects;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Default SQL runtime backed by the existing datasource execution SPI. */
@Component
@ConditionalOnDataSourceEnabled
public final class DefaultSqlExecutionRuntime implements SqlExecutionRuntime {

  private static final int MAX_COMPLETED_EXECUTIONS = 512;

  private final DataSourceExecutionProvider executionProvider;
  private final SqlStatementClassifier statementClassifier;
  private final SqlExecutionPolicy executionPolicy;
  private final ExecutorService lifecycleExecutor;
  private final ConcurrentMap<String, ManagedExecution> executions = new ConcurrentHashMap<>();
  private final ConcurrentLinkedDeque<String> completedOrder = new ConcurrentLinkedDeque<>();

  /** Spring entry point: policy remains replaceable while the standard classifier is deterministic. */
  @Autowired
  public DefaultSqlExecutionRuntime(
      DataSourceExecutionProvider executionProvider,
      SqlExecutionPolicy executionPolicy) {
    this(executionProvider, new LexicalSqlStatementClassifier(), executionPolicy);
  }

  DefaultSqlExecutionRuntime(
      DataSourceExecutionProvider executionProvider,
      SqlStatementClassifier statementClassifier,
      SqlExecutionPolicy executionPolicy) {
    this.executionProvider = Objects.requireNonNull(executionProvider, "executionProvider");
    this.statementClassifier = Objects.requireNonNull(statementClassifier, "statementClassifier");
    this.executionPolicy = Objects.requireNonNull(executionPolicy, "executionPolicy");
    this.lifecycleExecutor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @Override
  public SqlExecutionResult execute(SqlExecutionRequest request) {
    Objects.requireNonNull(request, "request");
    classifyAndValidate(request.context(), request.sql());
    return executeFresh(request, null, null);
  }

  @Override
  public SqlExecutionSnapshot start(SqlExecutionPlan plan) {
    Objects.requireNonNull(plan, "plan");
    List<SqlStatementClassification> classifications = new ArrayList<>(plan.statements().size());
    for (SqlStatementRequest statement : plan.statements()) {
      classifications.add(classifyAndValidate(plan.context(), statement.sql()));
    }

    String executionId = "sql-" + UUID.randomUUID();
    ManagedExecution execution = new ManagedExecution(executionId, plan, classifications);
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
        // Cancellation is best-effort. The worker still observes cancelRequested.
      }
    }
    return true;
  }

  @PreDestroy
  void shutdown() {
    lifecycleExecutor.shutdownNow();
  }

  private SqlStatementClassification classifyAndValidate(
      io.yak.ops.core.execution.sql.SqlExecutionContext context,
      String sql) {
    SqlStatementClassification classification = statementClassifier.classify(sql);
    executionPolicy.validate(context, classification);
    return classification;
  }

  private void run(ManagedExecution execution) {
    execution.markRunning();
    try {
      if (execution.plan().transactionMode() == SqlTransactionMode.SINGLE_TRANSACTION) {
        runSingleTransaction(execution);
      } else {
        runAutoCommit(execution);
      }
    } catch (RuntimeException exception) {
      execution.finishUnexpectedFailure(safeMessage(exception));
    } finally {
      if (!execution.snapshot().terminal()) {
        execution.finishUnexpectedFailure("SQL execution ended without a terminal state");
      }
      retainCompleted(execution.executionId());
    }
  }

  private void runAutoCommit(ManagedExecution execution) {
    List<SqlStatementRequest> statements = execution.plan().statements();
    for (int index = 0; index < statements.size(); index++) {
      if (execution.cancelRequested()) {
        execution.finishCancelled(index, "SQL execution cancelled");
        return;
      }

      SqlStatementRequest statement = statements.get(index);
      execution.markStatementRunning(index);
      SqlExecutionRequest request = request(execution, statement);
      try {
        SqlExecutionResult result =
            executeFresh(request, execution.activeExecutor(), execution);
        execution.markStatementSucceeded(index, result);
      } catch (RuntimeException exception) {
        finishStatementException(execution, index, exception);
        return;
      }
    }

    if (execution.cancelRequested()) {
      execution.finishCancelled(statements.size(), "SQL execution cancelled");
    } else {
      execution.finishSucceeded();
    }
  }

  private void runSingleTransaction(ManagedExecution execution) {
    if (execution.cancelRequested()) {
      execution.finishCancelled(0, "SQL execution cancelled");
      return;
    }

    DataSourceSqlExecutor executor = null;
    boolean transactionStarted = false;
    try {
      executor = executionProvider.open(execution.plan().dataSourceId());
      execution.activeExecutor().set(executor);

      if (execution.cancelRequested()) {
        cancelSafely(executor);
        execution.finishCancelled(0, "SQL execution cancelled");
        return;
      }
      if (!executor.supportsTransactions()) {
        execution.finishFailed(
            0,
            "Datasource SQL executor does not support SINGLE_TRANSACTION");
        return;
      }

      executor.beginTransaction();
      transactionStarted = true;
      if (execution.cancelRequested()) {
        String rollbackError = rollbackSafely(executor);
        transactionStarted = false;
        execution.finishCancelled(
            0,
            appendRollbackError("SQL execution cancelled", rollbackError));
        return;
      }

      List<SqlStatementRequest> statements = execution.plan().statements();
      for (int index = 0; index < statements.size(); index++) {
        if (execution.cancelRequested()) {
          String rollbackError = rollbackSafely(executor);
          transactionStarted = false;
          execution.finishCancelled(
              index,
              appendRollbackError("SQL execution cancelled", rollbackError));
          return;
        }

        SqlStatementRequest statement = statements.get(index);
        execution.markStatementRunning(index);
        SqlExecutionRequest request = request(execution, statement);
        try {
          SqlExecutionResult result = executeExisting(request, executor);
          execution.markStatementSucceeded(index, result);
        } catch (RuntimeException exception) {
          String rollbackError = rollbackSafely(executor);
          transactionStarted = false;
          finishStatementException(execution, index, exception, rollbackError);
          return;
        }
      }

      if (execution.cancelRequested()) {
        String rollbackError = rollbackSafely(executor);
        transactionStarted = false;
        execution.finishCancelled(
            statements.size(),
            appendRollbackError("SQL execution cancelled", rollbackError));
        return;
      }

      try {
        executor.commitTransaction();
        transactionStarted = false;
        execution.finishSucceeded();
      } catch (RuntimeException exception) {
        String rollbackError = rollbackSafely(executor);
        transactionStarted = false;
        execution.finishFailed(
            statements.size(),
            appendRollbackError(safeMessage(exception), rollbackError));
      }
    } catch (RuntimeException exception) {
      String rollbackError = transactionStarted && executor != null
          ? rollbackSafely(executor)
          : null;
      if (execution.cancelRequested()) {
        execution.finishCancelled(
            0,
            appendRollbackError("SQL execution cancelled", rollbackError));
      } else {
        execution.finishFailed(
            0,
            appendRollbackError(safeMessage(exception), rollbackError));
      }
    } finally {
      execution.activeExecutor().compareAndSet(executor, null);
      if (executor != null) {
        try {
          executor.close();
        } catch (RuntimeException ignored) {
          // Transaction outcome is already reflected above; close is best-effort cleanup.
        }
      }
    }
  }

  private SqlExecutionRequest request(
      ManagedExecution execution,
      SqlStatementRequest statement) {
    return new SqlExecutionRequest(
        execution.plan().dataSourceId(),
        statement.sql(),
        statement.parameters(),
        statement.maxRows(),
        statement.timeoutSeconds(),
        execution.plan().context());
  }

  private void finishStatementException(
      ManagedExecution execution,
      int index,
      RuntimeException exception) {
    finishStatementException(execution, index, exception, null);
  }

  private void finishStatementException(
      ManagedExecution execution,
      int index,
      RuntimeException exception,
      String rollbackError) {
    String message = appendRollbackError(safeMessage(exception), rollbackError);
    if (execution.cancelRequested()) {
      execution.markStatementCancelled(index, message);
      execution.finishCancelled(index + 1, message);
    } else if (causedBy(exception, SQLTimeoutException.class)) {
      execution.markStatementTimedOut(index, message);
      execution.finishTimedOut(index + 1, message);
    } else {
      execution.markStatementFailed(index, message);
      execution.finishFailed(index + 1, message);
    }
  }

  private SqlExecutionResult executeFresh(
      SqlExecutionRequest request,
      AtomicReference<DataSourceSqlExecutor> activeExecutor,
      ManagedExecution managedExecution) {
    long totalStartedAt = System.nanoTime();
    long openStartedAt = System.nanoTime();
    DataSourceSqlExecutor executor = executionProvider.open(request.dataSourceId());
    long openMillis = elapsedMillis(openStartedAt);
    if (activeExecutor != null) activeExecutor.set(executor);

    try (executor) {
      if (managedExecution != null && managedExecution.cancelRequested()) {
        cancelSafely(executor);
        throw new IllegalStateException("SQL execution cancelled");
      }
      return executeOnExecutor(request, executor, openMillis, totalStartedAt);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new SqlExecutionException(request.dataSourceId(), request.context(), exception);
    } finally {
      if (activeExecutor != null) activeExecutor.compareAndSet(executor, null);
    }
  }

  private SqlExecutionResult executeExisting(
      SqlExecutionRequest request,
      DataSourceSqlExecutor executor) {
    long totalStartedAt = System.nanoTime();
    return executeOnExecutor(request, executor, 0L, totalStartedAt);
  }

  private SqlExecutionResult executeOnExecutor(
      SqlExecutionRequest request,
      DataSourceSqlExecutor executor,
      long openMillis,
      long totalStartedAt) {
    long executeStartedAt = System.nanoTime();
    DataSourceSqlResult result = executor.execute(new DataSourceSqlRequest(
        request.sql(), request.maxRows(), request.timeoutSeconds(), request.parameters()));
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

  private static void cancelSafely(DataSourceSqlExecutor executor) {
    try {
      executor.cancel();
    } catch (RuntimeException ignored) {
      // Best effort cancellation.
    }
  }

  private String rollbackSafely(DataSourceSqlExecutor executor) {
    try {
      executor.rollbackTransaction();
      return null;
    } catch (RuntimeException exception) {
      return safeMessage(exception);
    }
  }

  private static String appendRollbackError(String message, String rollbackError) {
    if (rollbackError == null || rollbackError.isBlank()) return message;
    return message + "; rollback failed: " + rollbackError;
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

    private ManagedExecution(
        String executionId,
        SqlExecutionPlan plan,
        List<SqlStatementClassification> classifications) {
      this.executionId = executionId;
      this.plan = plan;
      this.statements = new ArrayList<>(plan.statements().size());
      for (int index = 0; index < plan.statements().size(); index++) {
        this.statements.add(new MutableStatement(
            executionId + ":stmt:" + (index + 1),
            index,
            plan.statements().get(index).sql(),
            classifications.get(index).primaryType()));
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

    synchronized void finishUnexpectedFailure(String message) {
      if (status.terminal()) return;
      int skipFrom = 0;
      for (int index = 0; index < statements.size(); index++) {
        MutableStatement statement = statements.get(index);
        if (statement.status == SqlStatementStatus.RUNNING) {
          statement.status = SqlStatementStatus.FAILED;
          statement.errorMessage = message;
          statement.finishedAt = Instant.now();
          skipFrom = index + 1;
          break;
        }
        if (statement.status == SqlStatementStatus.SUCCEEDED) {
          skipFrom = index + 1;
          continue;
        }
        skipFrom = index;
        break;
      }
      complete(SqlExecutionStatus.FAILED, message, skipFrom);
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
          plan.transactionMode(),
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
    private final SqlStatementType statementType;
    private SqlStatementStatus status = SqlStatementStatus.PENDING;
    private SqlExecutionResult result;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;

    private MutableStatement(
        String statementId,
        int index,
        String sql,
        SqlStatementType statementType) {
      this.statementId = statementId;
      this.index = index;
      this.sql = sql;
      this.statementType = statementType;
    }

    private SqlStatementSnapshot snapshot() {
      return new SqlStatementSnapshot(
          statementId,
          index,
          sql,
          statementType,
          status,
          result,
          errorMessage,
          startedAt,
          finishedAt);
    }
  }
}
