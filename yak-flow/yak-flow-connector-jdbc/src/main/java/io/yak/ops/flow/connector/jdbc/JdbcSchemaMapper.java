package io.yak.ops.flow.connector.jdbc;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakDataType;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.row.YakTypeKind;
import io.yak.ops.flow.api.row.YakTypes;
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
            Integer length = isLengthType(dataType.kind()) ? column.size() : null;
            columns.add(new YakColumn(column.name(), dataType, column.nullable(), length));
            if (column.primaryKey()) {
                primaryKeys.add(column.name());
            }
        }
        return new YakTableSchema(columns, primaryKeys);
    }

    static YakDataType toYakType(DataSourceColumn column) {
        return switch (column.jdbcType()) {
            case Types.BOOLEAN, Types.BIT -> YakTypes.BOOLEAN;
            case Types.TINYINT -> YakTypes.TINYINT;
            case Types.SMALLINT -> YakTypes.SMALLINT;
            case Types.INTEGER -> YakTypes.INTEGER;
            case Types.BIGINT -> YakTypes.BIGINT;
            case Types.REAL, Types.FLOAT -> YakTypes.FLOAT;
            case Types.DOUBLE -> YakTypes.DOUBLE;
            case Types.NUMERIC, Types.DECIMAL ->
                YakTypes.decimal(knownPrecision(column.size()), knownScale(column.scale()));
            case Types.CHAR,
                    Types.VARCHAR,
                    Types.LONGVARCHAR,
                    Types.NCHAR,
                    Types.NVARCHAR,
                    Types.LONGNVARCHAR,
                    Types.CLOB,
                    Types.NCLOB -> YakTypes.STRING;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> YakTypes.BINARY;
            case Types.DATE -> YakTypes.DATE;
            case Types.TIME -> YakTypes.TIME;
            case Types.TIMESTAMP -> YakTypes.TIMESTAMP;
            case Types.TIMESTAMP_WITH_TIMEZONE -> YakTypes.TIMESTAMP_WITH_TIME_ZONE;
            default ->
                throw new IllegalArgumentException(
                        "暂不支持 JDBC 字段类型：" + column.typeName() + " (" + column.jdbcType() + ")");
        };
    }

    private static boolean isLengthType(YakTypeKind kind) {
        return kind == YakTypeKind.STRING || kind == YakTypeKind.BINARY;
    }

    private static Integer knownPrecision(Integer precision) {
        return precision != null && precision > 0 ? precision : null;
    }

    private static Integer knownScale(Integer scale) {
        return scale != null && scale >= 0 ? scale : null;
    }
}
