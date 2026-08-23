package io.yak.ops.spi.datasource.catalog;

import java.util.List;
import java.util.Objects;

/** Typed Catalog read request used by datasource plugins. */
public record DataSourceCatalogReadRequest(
    Mode mode,
    String tablePath,
    String query,
    List<Variable> variables) {

  public DataSourceCatalogReadRequest {
    mode = Objects.requireNonNull(mode, "mode");
    tablePath = trimToNull(tablePath);
    query = trimToNull(query);
    variables = variables == null ? List.of() : List.copyOf(variables);
    if (variables.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("variables must not contain null");
    }
    if (mode == Mode.TABLE && tablePath == null) {
      throw new IllegalArgumentException("tablePath must not be blank in TABLE mode");
    }
    if (mode == Mode.SQL && query == null) {
      throw new IllegalArgumentException("query must not be blank in SQL mode");
    }
  }

  public static DataSourceCatalogReadRequest table(String tablePath, List<Variable> variables) {
    return new DataSourceCatalogReadRequest(Mode.TABLE, tablePath, null, variables);
  }

  public static DataSourceCatalogReadRequest sql(String query, List<Variable> variables) {
    return new DataSourceCatalogReadRequest(Mode.SQL, null, query, variables);
  }

  public boolean sqlMode() {
    return mode == Mode.SQL;
  }

  public enum Mode {
    TABLE,
    SQL
  }

  /** Catalog SQL variable. Null value is retained for compatibility with the historic request. */
  public record Variable(String name, String value) {
    public Variable {
      name = requireText(name, "variable name");
      value = trimToNull(value);
    }
  }

  private static String requireText(String value, String name) {
    String normalized = trimToNull(value);
    if (normalized == null) throw new IllegalArgumentException(name + " must not be blank");
    return normalized;
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
