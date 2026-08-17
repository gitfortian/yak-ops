package io.yak.ops.business.dataset;

import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.util.List;
import org.springframework.stereotype.Component;

/** Query runtime for Dataset versions that own datasource + SQL directly. */
@Component
final class SqlQueryDatasetSourceAdapter implements DatasetSourceQueryAdapter {

  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final int MAX_TIMEOUT_SECONDS = 120;

  private final DataSourceExecutionProvider dataSourceExecutionProvider;
  private final DatasetQueryCompiler compiler;

  SqlQueryDatasetSourceAdapter(
      DataSourceExecutionProvider dataSourceExecutionProvider,
      DatasetQueryCompiler compiler) {
    this.dataSourceExecutionProvider = dataSourceExecutionProvider;
    this.compiler = compiler;
  }

  @Override
  public DatasetSourceType sourceType() {
    return DatasetSourceType.SQL_QUERY;
  }

  @Override
  public DatasetQueryResult execute(
      Dataset dataset,
      DatasetVersion version,
      List<DatasetField> fields,
      DatasetQueryRequest request) {
    if (version.dataSourceId() == null || version.dataSourceId().isBlank()) {
      throw new IllegalStateException("SQL_QUERY DatasetVersion 缺少 dataSourceId");
    }
    if (version.sql() == null || version.sql().isBlank()) {
      throw new IllegalStateException("SQL_QUERY DatasetVersion 缺少 SQL");
    }

    DatasetQueryCompiler.CompiledQuery compiled = compiler.compile(version.sql(), fields, request);
    int timeoutSeconds = queryTimeout(request);
    long startedAt = System.nanoTime();
    DataSourceSqlResult result;
    try (DataSourceSqlExecutor executor = dataSourceExecutionProvider.open(version.dataSourceId())) {
      result = executor.execute(new DataSourceSqlRequest(
          compiled.sql(), compiled.fetchRows(), timeoutSeconds));
    }
    if (!result.resultSet()) {
      throw new IllegalStateException("Dataset Query Runtime 只接受结果集查询");
    }

    boolean overflow = result.rows().size() > compiled.limit();
    List<List<Object>> rows = overflow
        ? result.rows().subList(0, compiled.limit())
        : result.rows();
    long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
    return new DatasetQueryResult(
        dataset.id(), version.id(), version.versionNo(), compiled.bindings(), result.columns(),
        rows, rows.size(), result.truncated() || overflow, elapsedMillis);
  }

  private int queryTimeout(DatasetQueryRequest request) {
    Integer requested = request == null ? null : request.timeoutSeconds();
    if (requested == null) return DEFAULT_TIMEOUT_SECONDS;
    if (requested < 1 || requested > MAX_TIMEOUT_SECONDS) {
      throw new IllegalArgumentException("timeoutSeconds 必须在 1~120 之间");
    }
    return requested;
  }
}
