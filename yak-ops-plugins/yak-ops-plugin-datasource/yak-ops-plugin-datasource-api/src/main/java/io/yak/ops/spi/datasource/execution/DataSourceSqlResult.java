package io.yak.ops.spi.datasource.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of one SQL statement, supporting both result-set and update-count statements. */
public record DataSourceSqlResult(
    boolean resultSet,
    List<DataSourceSqlColumn> columns,
    List<List<Object>> rows,
    long affectedRows,
    boolean truncated) {

  public DataSourceSqlResult {
    columns = columns == null ? List.of() : List.copyOf(columns);
    rows = immutableRows(rows);
  }

  public int returnedRows() {
    return rows.size();
  }

  public static DataSourceSqlResult query(
      List<DataSourceSqlColumn> columns,
      List<List<Object>> rows,
      boolean truncated) {
    return new DataSourceSqlResult(true, columns, rows, -1L, truncated);
  }

  public static DataSourceSqlResult update(long affectedRows) {
    return new DataSourceSqlResult(false, List.of(), List.of(), affectedRows, false);
  }

  private static List<List<Object>> immutableRows(List<List<Object>> rows) {
    if (rows == null || rows.isEmpty()) return List.of();
    List<List<Object>> copy = new ArrayList<>(rows.size());
    for (List<Object> row : rows) {
      List<Object> values = row == null ? new ArrayList<>() : new ArrayList<>(row);
      copy.add(Collections.unmodifiableList(values));
    }
    return Collections.unmodifiableList(copy);
  }
}
