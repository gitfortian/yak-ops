package io.yak.ops.connector.jdbc.database.internal.dialect;

import io.yak.ops.connector.jdbc.database.dialect.AbstractDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialectConverter;
import io.yak.ops.connector.jdbc.database.dialect.JdbcNativeType;
import io.yak.ops.connector.jdbc.database.internal.convert.PostgresJdbcDialectConverter;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements PostgreSQL schema-qualified SQL, target types, and ON CONFLICT UPSERT.
 *
 * <p>Read connections disable autocommit so PostgreSQL cursor fetching can use a transaction.
 * Unbounded text maps to TEXT and binary values to BYTEA. An UPSERT with only key
 * fields resolves to ON CONFLICT DO NOTHING instead of an invalid empty UPDATE clause.
 */
public final class PostgresJdbcDialect extends AbstractDialect {

    @Override
    public JdbcDialectConverter createRowConverter(java.sql.ResultSetMetaData metadata) throws java.sql.SQLException {
        return new PostgresJdbcDialectConverter(metadata);
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + JdbcDialect.requireIdentifier(identifier).replace("\"", "\"\"") + "\"";
    }

    @Override
    public String qualifiedTable(TableId table) {
        if (table.schema() == null || table.schema().isBlank()) {
            return quoteIdentifier(table.table());
        }
        return quoteIdentifier(table.schema()) + "." + quoteIdentifier(table.table());
    }

    /**
     * Sets a read-only, non-autocommit transaction for PostgreSQL cursor-based fetching.
     *
     * <p>The SourceReader continues to own the transaction and connection lifecycle.
     */
    @Override
    public void configureReadConnection(java.sql.Connection connection) throws java.sql.SQLException {
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
    }

    @Override
    public JdbcNativeType nativeType(Column column) {
        return switch (column.dataType().getTypeRoot()) {
            case BOOLEAN -> JdbcNativeType.of("BOOLEAN");
            case TINYINT, SMALLINT -> JdbcNativeType.of("SMALLINT");
            case INTEGER -> JdbcNativeType.of("INTEGER");
            case BIGINT -> JdbcNativeType.of("BIGINT");
            case FLOAT -> JdbcNativeType.of("REAL");
            case DOUBLE -> JdbcNativeType.of("DOUBLE PRECISION");
            case DECIMAL -> JdbcTypeMappings.decimal(column, "NUMERIC", 1000, 1000);
            case CHAR -> JdbcNativeType.of("CHAR(" + column.length() + ")");
            case VARCHAR -> stringType(column.length());
            case BINARY, VARBINARY -> JdbcNativeType.of("BYTEA");
            case DATE -> JdbcNativeType.of("DATE");
            case TIME_WITHOUT_TIME_ZONE -> timeType(column);
            case TIMESTAMP_WITHOUT_TIME_ZONE -> timestampType(column, false);
            case TIMESTAMP_WITH_TIME_ZONE -> timestampType(column, true);
        };
    }

    @Override
    public List<String> addColumnStatements(TableId table, PhysicalColumn column) {
        Column target = new Column(column.name(), column.type());
        String identifier = qualifiedTable(table) + "." + quoteIdentifier(column.name());
        String add = "ALTER TABLE " + qualifiedTable(table) + " ADD COLUMN " + quoteIdentifier(column.name()) + " "
                + nativeType(target).ddl();
        if (column.comment() == null || column.comment().isBlank()) {
            return List.of(add);
        }
        return List.of(add, "COMMENT ON COLUMN " + identifier + " IS " + stringLiteral(column.comment()));
    }

    @Override
    public String dropColumnSql(TableId table, String column) {
        // PostgreSQL defaults to RESTRICT; omitting the keyword also supports the H2 JDBC contract tests.
        return "ALTER TABLE " + qualifiedTable(table) + " DROP COLUMN " + quoteIdentifier(column);
    }

    @Override
    public String alterColumnTypeSql(TableId table, Column previous, Column updated, String comment) {
        if (!previous.name().equals(updated.name()) || previous.nullable() != updated.nullable()) {
            throw new IllegalArgumentException("Column identity and nullability must remain unchanged");
        }
        return "ALTER TABLE " + qualifiedTable(table) + " ALTER COLUMN " + quoteIdentifier(updated.name()) + " TYPE "
                + nativeType(updated).ddl();
    }

    @Override
    public LogicalType normalizeTargetType(Column column) {
        JdbcNativeType targetType = nativeType(column);
        boolean nullable = column.nullable();
        return switch (column.dataType().getTypeRoot()) {
            case TINYINT -> LogicalTypes.SMALLINT.copy(nullable);
            case BINARY, VARBINARY -> LogicalTypes.BINARY.copy(nullable);
            case VARCHAR -> "TEXT".equals(targetType.ddl()) ? LogicalTypes.STRING.copy(nullable) : column.dataType();
            default -> column.dataType();
        };
    }

    private JdbcNativeType timeType(Column column) {
        int precision = JdbcTypeMappings.precision(column, 6, "PostgreSQL TIME");
        return JdbcNativeType.of(precision == 6 ? "TIME" : "TIME(" + precision + ")");
    }

    private JdbcNativeType timestampType(Column column, boolean zoned) {
        int precision = JdbcTypeMappings.precision(column, 6, "PostgreSQL TIMESTAMP");
        String sqlType = precision == 6 ? "TIMESTAMP" : "TIMESTAMP(" + precision + ")";
        return JdbcNativeType.of(zoned ? sqlType + " WITH TIME ZONE" : sqlType);
    }

    private JdbcNativeType stringType(Integer length) {
        if (JdbcTypeMappings.knownLength(length)) {
            return JdbcNativeType.of("VARCHAR(" + length + ")");
        }
        return JdbcNativeType.of("TEXT", "STRING length 未知，目标使用 TEXT");
    }

    @Override
    public String upsertSql(TableId table, TableSchema schema) {
        requirePrimaryKey(schema);
        List<String> primaryKeys = schema.primaryKeys();
        String conflictColumns = primaryKeys.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        List<Column> updateColumns = schema.columns().stream()
                .filter(column -> !primaryKeys.contains(column.name()))
                .toList();
        if (updateColumns.isEmpty()) {
            return insertSql(table, schema) + " ON CONFLICT (" + conflictColumns + ") DO NOTHING";
        }
        String updateClause = updateColumns.stream()
                .map(column -> {
                    String name = quoteIdentifier(column.name());
                    return name + " = EXCLUDED." + name;
                })
                .collect(Collectors.joining(", "));
        return insertSql(table, schema) + " ON CONFLICT (" + conflictColumns + ") DO UPDATE SET " + updateClause;
    }

    private void requirePrimaryKey(TableSchema schema) {
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("PostgreSQL UPSERT requires primary key");
        }
    }
}
