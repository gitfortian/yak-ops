package io.yak.ops.business.dataset;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import java.util.List;

/** Result returned to Dashboard/Chart consumers by the Dataset query runtime. */
public record DatasetQueryResult(
    @JsonSerialize(using = ToStringSerializer.class) long datasetId,
    @JsonSerialize(using = ToStringSerializer.class) long datasetVersionId,
    int datasetVersionNo,
    List<DatasetQueryColumnBinding> bindings,
    List<DataSourceSqlColumn> columns,
    List<List<Object>> rows,
    int returnedRows,
    boolean truncated,
    long elapsedMillis) {

  public DatasetQueryResult {
    bindings = bindings == null ? List.of() : List.copyOf(bindings);
    columns = columns == null ? List.of() : List.copyOf(columns);
    rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
  }
}

record DatasetQueryRequest(
    Integer versionNo,
    List<String> dimensions,
    List<DatasetMetricBinding> metrics,
    List<DatasetFilter> filters,
    List<DatasetSort> sorts,
    Integer limit,
    Integer timeoutSeconds) {
}

enum DatasetAggregation {
  SUM,
  AVG,
  COUNT,
  COUNT_DISTINCT,
  MAX,
  MIN
}

enum DatasetFilterOperator {
  EQ,
  NE,
  GT,
  GTE,
  LT,
  LTE,
  IN,
  NOT_IN,
  LIKE,
  NOT_LIKE,
  BETWEEN,
  IS_NULL,
  IS_NOT_NULL
}

enum DatasetSortDirection {
  ASC,
  DESC
}

record DatasetMetricBinding(
    String fieldId,
    DatasetAggregation aggregation) {
}

record DatasetFilter(
    String fieldId,
    DatasetFilterOperator operator,
    Object value,
    List<Object> values) {
}

record DatasetSort(
    String fieldId,
    DatasetAggregation aggregation,
    DatasetSortDirection direction) {
}

record DatasetQueryColumnBinding(
    String key,
    String fieldId,
    String displayName,
    DatasetFieldDataType dataType,
    DatasetAggregation aggregation) {
}
