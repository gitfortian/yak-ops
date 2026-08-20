package io.yak.ops.business.development.service;

import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Resolves SQL table references into stable physical identities.
 *
 * <p>The resolver centralizes database/schema/default context handling so table-level
 * lineage, column lineage and future metadata sync do not create conflicting assets.
 */
@Component
public class TableIdentityResolver {

  public PhysicalTableIdentity resolve(
      SqlTableLineageParser.TableRef table,
      ResolutionContext context) {
    if (table == null) {
      throw new IllegalArgumentException("table 不能为空");
    }

    ResolutionContext actual = context == null ? ResolutionContext.empty() : context;

    String database = firstNonBlank(
        table.databaseName(),
        actual.databaseName());
    String schema = firstNonBlank(
        table.schemaName(),
        actual.schemaName());

    return new PhysicalTableIdentity(
        normalize(actual.dataSourceId()),
        normalize(database),
        normalize(schema),
        normalize(table.tableName()));
  }

  private String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String normalize(String value) {
    return value == null || value.isBlank()
        ? ""
        : value.trim().toLowerCase(Locale.ROOT);
  }

  public record ResolutionContext(
      String dataSourceId,
      String databaseName,
      String schemaName) {

    public static ResolutionContext empty() {
      return new ResolutionContext(null, null, null);
    }
  }

  public record PhysicalTableIdentity(
      String dataSourceId,
      String databaseName,
      String schemaName,
      String tableName) {

    public String assetKey() {
      return "table:%s:%s.%s.%s".formatted(
          dataSourceId,
          databaseName,
          schemaName,
          tableName);
    }
  }
}
