package io.yak.ops.connector.jdbc.database.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.database.internal.convert.MySqlTypeConverter;
import io.yak.ops.connector.jdbc.database.internal.convert.OracleTypeConverter;
import io.yak.ops.connector.jdbc.database.internal.convert.PostgresTypeConverter;
import io.yak.ops.connector.jdbc.database.internal.convert.StandardJdbcTypeConverter;
import io.yak.ops.core.types.LogicalTypes;
import java.lang.reflect.Proxy;
import java.sql.ResultSetMetaData;
import java.sql.SQLDataException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import org.junit.jupiter.api.Test;

/** Metadata-only regression tests for vendor-specific JDBC source mappings. */
class JdbcTypeConverterTest {

    @Test
    void mysqlPreservesUnsignedRangesAndNativeStringTypes() throws Exception {
        var schema = new MySqlTypeConverter().convert(metadata(
                field("tiny_unsigned", Types.TINYINT, "TINYINT UNSIGNED", 3, 0),
                field("small_unsigned", Types.SMALLINT, "SMALLINT UNSIGNED", 5, 0),
                field("int_unsigned", Types.INTEGER, "INT UNSIGNED", 10, 0),
                field("big_unsigned", Types.BIGINT, "BIGINT UNSIGNED", 20, 0),
                field("status", Types.VARCHAR, "ENUM", 0, 0),
                field("flags", Types.VARCHAR, "SET", 0, 0),
                field("payload", Types.VARCHAR, "JSON", 0, 0)));
        assertEquals(LogicalTypes.SMALLINT, schema.column(0).dataType());
        assertEquals(LogicalTypes.INTEGER, schema.column(1).dataType());
        assertEquals(LogicalTypes.BIGINT, schema.column(2).dataType());
        assertEquals(LogicalTypes.decimal(20, 0), schema.column(3).dataType());
        assertEquals(LogicalTypes.STRING, schema.column(4).dataType());
        assertEquals(LogicalTypes.STRING, schema.column(5).dataType());
        assertEquals(LogicalTypes.STRING, schema.column(6).dataType());
    }

    @Test
    void mysqlSpatialTypesRemainUnsupported() {
        var failure = assertThrows(SQLFeatureNotSupportedException.class,
                () -> new MySqlTypeConverter().convert(metadata(field("f53_geometry", Types.BINARY, "POINT", 0, 0))));
        assertTrue(failure.getMessage().contains("f53_geometry"));
        assertTrue(failure.getMessage().contains("POINT"));
    }

    @Test
    void postgresMapsKnownOtherTypesButRejectsUnknownNativeTypes() throws Exception {
        var schema = new PostgresTypeConverter().convert(metadata(
                field("details", Types.OTHER, "jsonb", 0, 0),
                field("uuid", Types.OTHER, "uuid", 0, 0),
                field("doc", Types.OTHER, "json", 0, 0)));
        assertEquals(LogicalTypes.STRING, schema.column(0).dataType());
        assertEquals(LogicalTypes.STRING, schema.column(1).dataType());
        assertEquals(LogicalTypes.STRING, schema.column(2).dataType());
        assertThrows(SQLFeatureNotSupportedException.class,
                () -> new PostgresTypeConverter().convert(metadata(field("unknown", Types.OTHER, "geometry", 0, 0))));
    }

    @Test
    void oracleDateRetainsTimeOfDay() throws Exception {
        var schema = new OracleTypeConverter().convert(metadata(field("created_at", Types.DATE, "DATE", 0, 0)));
        assertEquals(LogicalTypes.timestamp(0), schema.column(0).dataType());
        assertEquals(LogicalTypes.DATE,
                new StandardJdbcTypeConverter().convert(metadata(field("created_at", Types.DATE, "DATE", 0, 0)))
                        .column(0).dataType());
    }

    @Test
    void unsupportedPrecisionAndNestedJdbcTypesStillFail() {
        assertThrows(SQLDataException.class,
                () -> new StandardJdbcTypeConverter().convert(metadata(field("amount", Types.DECIMAL, "DECIMAL", 50, 2))));
        assertThrows(SQLFeatureNotSupportedException.class,
                () -> new StandardJdbcTypeConverter().convert(metadata(field("items", Types.ARRAY, "ARRAY", 0, 0))));
    }

    private static Field field(String name, int sqlType, String nativeType, int precision, int scale) {
        return new Field(name, sqlType, nativeType, precision, scale);
    }

    private static ResultSetMetaData metadata(Field... fields) {
        return (ResultSetMetaData) Proxy.newProxyInstance(
                ResultSetMetaData.class.getClassLoader(),
                new Class<?>[] {ResultSetMetaData.class},
                (proxy, method, args) -> {
                    if ("getColumnCount".equals(method.getName())) {
                        return fields.length;
                    }
                    int index = args == null || args.length == 0 ? -1 : (Integer) args[0] - 1;
                    return switch (method.getName()) {
                        case "getColumnLabel" -> fields[index].name();
                        case "getColumnType" -> fields[index].sqlType();
                        case "getColumnTypeName" -> fields[index].nativeType();
                        case "getPrecision" -> fields[index].precision();
                        case "getScale" -> fields[index].scale();
                        case "isNullable" -> ResultSetMetaData.columnNullable;
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }

    private record Field(String name, int sqlType, String nativeType, int precision, int scale) {}
}
