package io.yak.ops.flow.connector.jdbc.dialect;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Oracle JDBC SQL 标识符和 Schema 表路径规则。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class OracleJdbcDialect implements JdbcDialect {

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String qualifiedTable(DataSourceTablePath table) {
        if (table.schema() == null || table.schema().isBlank()) {
            return quoteIdentifier(table.table());
        }
        return quoteIdentifier(table.schema()) + "." + quoteIdentifier(table.table());
    }

    @Override
    public String upsertSql(DataSourceTablePath table, YakTableSchema schema) {
        requirePrimaryKey(schema);
        List<String> primaryKeys = schema.primaryKeys();
        List<YakColumn> updateColumns = schema.columns().stream()
                .filter(column -> !primaryKeys.contains(column.name()))
                .toList();
        String sourceProjection = schema.columns().stream()
                .map(column -> "? AS " + quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));
        String matchPredicate = primaryKeys.stream()
                .map(primaryKey -> "t." + quoteIdentifier(primaryKey) + " = s." + quoteIdentifier(primaryKey))
                .collect(Collectors.joining(" AND "));
        String insertColumns = schema.columns().stream()
                .map(YakColumn::name)
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
        String insertValues = schema.columns().stream()
                .map(column -> "s." + quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));

        StringBuilder sql = new StringBuilder()
                .append("MERGE INTO ")
                .append(qualifiedTable(table))
                .append(" t USING (SELECT ")
                .append(sourceProjection)
                .append(" FROM DUAL) s ON (")
                .append(matchPredicate)
                .append(") ");
        if (!updateColumns.isEmpty()) {
            sql.append("WHEN MATCHED THEN UPDATE SET ")
                    .append(updateColumns.stream()
                            .map(column -> {
                                String name = quoteIdentifier(column.name());
                                return "t." + name + " = s." + name;
                            })
                            .collect(Collectors.joining(", ")))
                    .append(" ");
        }
        return sql.append("WHEN NOT MATCHED THEN INSERT (")
                .append(insertColumns)
                .append(") VALUES (")
                .append(insertValues)
                .append(")")
                .toString();
    }

    private void requirePrimaryKey(YakTableSchema schema) {
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("Oracle UPSERT requires primary key");
        }
    }
}
