package io.yak.ops.connector.jdbc.database.internal.dialect;

import io.yak.ops.connector.jdbc.database.dialect.AbstractDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcNativeType;
import io.yak.ops.connector.jdbc.database.dialect.JdbcTypeConverter;
import io.yak.ops.connector.jdbc.database.internal.convert.OracleTypeConverter;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements Oracle owner-qualified SQL, NUMBER-based numeric mappings, and MERGE UPSERT.
 *
 * <p>VARCHAR2 and RAW capacities are bounded. Oversized text/binary fields use CLOB/BLOB
 * and cannot be used as primary keys; Oracle has no independent TIME-only column type.
 * Native UPSERT uses MERGE with a key predicate, without emulated SELECT-then-UPDATE.
 */
public final class OracleJdbcDialect extends AbstractDialect {

    @Override
    public JdbcTypeConverter typeConverter() {
        return new OracleTypeConverter();
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

    @Override
    public JdbcNativeType nativeType(Column column) {
        return switch (column.dataType().getTypeRoot()) {
            case BOOLEAN -> JdbcNativeType.of("NUMBER(1)");
            case TINYINT -> JdbcNativeType.of("NUMBER(3)");
            case SMALLINT -> JdbcNativeType.of("NUMBER(5)");
            case INTEGER -> JdbcNativeType.of("NUMBER(10)");
            case BIGINT -> JdbcNativeType.of("NUMBER(19)");
            case FLOAT -> JdbcNativeType.of("BINARY_FLOAT");
            case DOUBLE -> JdbcNativeType.of("BINARY_DOUBLE");
            case DECIMAL -> JdbcTypeMappings.decimal(column, "NUMBER", 38, 38);
            case CHAR -> JdbcNativeType.of("CHAR(" + column.length() + " CHAR)");
            case VARCHAR -> stringType(column.length());
            case BINARY, VARBINARY -> binaryType(column.length());
            case DATE -> JdbcNativeType.of("DATE");
            case TIME_WITHOUT_TIME_ZONE ->
                throw new UnsupportedOperationException("Oracle has no standalone TIME column type");
            case TIMESTAMP_WITHOUT_TIME_ZONE ->
                JdbcNativeType.of("TIMESTAMP(" + JdbcTypeMappings.precision(column, 9, "Oracle TIMESTAMP") + ")");
            case TIMESTAMP_WITH_TIME_ZONE ->
                JdbcNativeType.of(
                        "TIMESTAMP(" + JdbcTypeMappings.precision(column, 9, "Oracle TIMESTAMP") + ") WITH TIME ZONE");
        };
    }

    private JdbcNativeType stringType(Integer length) {
        if (JdbcTypeMappings.knownLength(length) && length <= 4000) {
            return JdbcNativeType.of("VARCHAR2(" + length + " CHAR)");
        }
        String warning = JdbcTypeMappings.knownLength(length)
                ? "STRING length=" + length + " 超过 VARCHAR2(4000 CHAR)，目标使用 CLOB"
                : "STRING length 未知，目标使用 CLOB";
        return JdbcNativeType.nonKey("CLOB", warning);
    }

    private JdbcNativeType binaryType(Integer length) {
        if (JdbcTypeMappings.knownLength(length) && length <= 2000) {
            return JdbcNativeType.of("RAW(" + length + ")");
        }
        String warning = JdbcTypeMappings.knownLength(length)
                ? "BINARY length=" + length + " 超过 RAW(2000)，目标使用 BLOB"
                : "BINARY length 未知，目标使用 BLOB";
        return JdbcNativeType.nonKey("BLOB", warning);
    }

    /**
     * Renders an Oracle MERGE that matches the declared primary keys.
     *
     * <p>Each target column produces a positional placeholder in the USING projection.
     * A key-only table omits WHEN MATCHED UPDATE rather than inventing a no-op assignment.
     */
    /** Oracle ROWID limits a full before-image retraction to one physical occurrence. */
    @Override
    public String exactRowDeleteSql(TableId table, TableSchema schema) {
        if (!schema.primaryKeys().isEmpty() || schema.columnCount() == 0) {
            throw new IllegalArgumentException("Oracle RETRACT requires a nonempty keyless schema");
        }
        String predicate = schema.columns().stream()
                .map(column -> "DECODE(" + quoteIdentifier(column.name()) + ", ?, 1, 0) = 1")
                .collect(Collectors.joining(" AND "));
        String target = qualifiedTable(table);
        String select = "SELECT ROWID FROM " + target + " WHERE " + predicate + " AND ROWNUM = 1";
        return "DELETE FROM " + target + " WHERE ROWID = (" + select + ")";
    }

    @Override
    public String replayReceiptTableName() {
        return "YAK_FLOW_CDC_RECEIPTS";
    }

    /** A matched MERGE has zero affected rows; a new receipt is inserted once. */
    @Override
    public String replayReceiptSql(TableId table) {
        String key = quoteIdentifier("RECEIPT_KEY");
        String target = qualifiedTable(table);
        String source = "USING (SELECT ? " + key + " FROM DUAL) s";
        String match = "ON (t." + key + " = s." + key + ")";
        String insert = "WHEN NOT MATCHED THEN INSERT (" + key + ") VALUES (s." + key + ")";
        return "MERGE INTO " + target + " t " + source + " " + match + " " + insert;
    }

    @Override
    public String upsertSql(TableId table, TableSchema schema) {
        requirePrimaryKey(schema);
        List<String> primaryKeys = schema.primaryKeys();
        List<Column> updateColumns = schema.columns().stream()
                .filter(column -> !primaryKeys.contains(column.name()))
                .toList();
        String sourceProjection = schema.columns().stream()
                .map(column -> "? AS " + quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));
        String matchPredicate = primaryKeys.stream()
                .map(primaryKey -> "t." + quoteIdentifier(primaryKey) + " = s." + quoteIdentifier(primaryKey))
                .collect(Collectors.joining(" AND "));
        String insertColumns = schema.columns().stream()
                .map(Column::name)
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

    private void requirePrimaryKey(TableSchema schema) {
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("Oracle UPSERT requires primary key");
        }
    }
}
