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

    boolean unqualified = table.databaseName() == null && table.schemaName() == null;
    String database = table.databaseName();
    String schema = table.schemaName();
    if (unqualified) {
      database = actual.databaseName();
      schema = actual.schemaName();
    } else if (database == null && schema != null && actual.dialect() == SqlDialect.MYSQL) {
      // JSQLParser exposes a two-part name as schema.table. Its SQL meaning is dialect-specific:
      // PostgreSQL keeps that interpretation, while MySQL treats the first part as database.
      database = schema;
      schema = null;
    }

    return new PhysicalTableIdentity(
        normalize(actual.dataSourceId()),
        normalize(database),
        normalize(schema),
        normalize(table.tableName()));
  }

  private String normalize(String value) {
    return value == null || value.isBlank()
        ? ""
        : value.trim().toLowerCase(Locale.ROOT);
  }

  public record ResolutionContext(
      String dataSourceId,
      String databaseName,
      String schemaName,
      SqlDialect dialect) {

    public ResolutionContext(String dataSourceId, String databaseName, String schemaName) {
      this(dataSourceId, databaseName, schemaName, SqlDialect.UNKNOWN);
    }

    public static ResolutionContext empty() {
      return new ResolutionContext(null, null, null, SqlDialect.UNKNOWN);
    }
  }

  public enum SqlDialect {
    POSTGRESQL,
    MYSQL,
    UNKNOWN;

    public static SqlDialect from(String value) {
      if (value == null) return UNKNOWN;
      String normalized = value.trim().toLowerCase(Locale.ROOT);
      if (normalized.contains("postgres")) return POSTGRESQL;
      if (normalized.contains("mysql") || normalized.contains("mariadb")
          || normalized.contains("doris")) return MYSQL;
      return UNKNOWN;
    }
  }

  public record PhysicalTableIdentity(
      String dataSourceId,
      String databaseName,
      String schemaName,
      String tableName) {

    public String assetKey() {
      // Legacy configs without database/schema must never alias a confirmed physical asset.
      String resolution = databaseName.isEmpty() && schemaName.isEmpty() ? "unresolved:" : "";
      return "table:%s%s:%s.%s.%s".formatted(
          resolution,
          dataSourceId,
          databaseName,
          schemaName,
          tableName);
    }
  }
}
