package io.yak.ops.flow.connector.jdbc;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakDataType;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 把 Datasource Catalog 的 JDBC 字段元数据转换为 YakFlow 逻辑表结构。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public final class JdbcSchemaMapper {

    private JdbcSchemaMapper() {}

    public static YakTableSchema fromColumns(List<DataSourceColumn> sourceColumns) {
        Objects.requireNonNull(sourceColumns, "sourceColumns must not be null");
        if (sourceColumns.isEmpty()) {
            throw new IllegalArgumentException("sourceColumns must not be empty");
        }

        List<DataSourceColumn> ordered = sourceColumns.stream()
                .sorted(Comparator.comparingInt(DataSourceColumn::ordinalPosition))
                .toList();
        List<YakColumn> columns = new ArrayList<>(ordered.size());
        List<String> primaryKeys = new ArrayList<>();

        for (DataSourceColumn column : ordered) {
            YakDataType dataType = toYakType(column);
            Integer length = isLengthType(dataType) ? column.size() : null;
            Integer precision = dataType == YakDataType.DECIMAL ? column.size() : null;
            Integer scale = dataType == YakDataType.DECIMAL ? column.scale() : null;
            columns.add(new YakColumn(column.name(), dataType, column.nullable(), length, precision, scale));
            if (column.primaryKey()) {
                primaryKeys.add(column.name());
            }
        }
        return new YakTableSchema(columns, primaryKeys);
    }

    static YakDataType toYakType(DataSourceColumn column) {
        return switch (column.jdbcType()) {
            case Types.BOOLEAN, Types.BIT -> YakDataType.BOOLEAN;
            case Types.TINYINT -> YakDataType.TINYINT;
            case Types.SMALLINT -> YakDataType.SMALLINT;
            case Types.INTEGER -> YakDataType.INTEGER;
            case Types.BIGINT -> YakDataType.BIGINT;
            case Types.REAL, Types.FLOAT -> YakDataType.FLOAT;
            case Types.DOUBLE -> YakDataType.DOUBLE;
            case Types.NUMERIC, Types.DECIMAL -> YakDataType.DECIMAL;
            case Types.CHAR,
                    Types.VARCHAR,
                    Types.LONGVARCHAR,
                    Types.NCHAR,
                    Types.NVARCHAR,
                    Types.LONGNVARCHAR,
                    Types.CLOB,
                    Types.NCLOB -> YakDataType.STRING;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> YakDataType.BINARY;
            case Types.DATE -> YakDataType.DATE;
            case Types.TIME -> YakDataType.TIME;
            case Types.TIMESTAMP -> YakDataType.TIMESTAMP;
            case Types.TIMESTAMP_WITH_TIMEZONE -> YakDataType.TIMESTAMP_WITH_TIME_ZONE;
            default ->
                throw new IllegalArgumentException(
                        "暂不支持 JDBC 字段类型：" + column.typeName() + " (" + column.jdbcType() + ")");
        };
    }

    private static boolean isLengthType(YakDataType dataType) {
        return dataType == YakDataType.STRING || dataType == YakDataType.BINARY;
    }
}
