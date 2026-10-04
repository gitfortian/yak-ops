package io.yak.ops.flow.connector.jdbc.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.row.YakTypes;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcCreateTableDialectTest {

    private static final YakTableSchema SCHEMA = new YakTableSchema(
            List.of(
                    new YakColumn("id", YakTypes.BIGINT, false, null),
                    new YakColumn("name", YakTypes.STRING, false, 100),
                    new YakColumn("amount", YakTypes.decimal(18, 2), true, null)),
            List.of("id"));

    @Test
    void shouldGenerateMysqlCreateTable() {
        assertEquals(
                "CREATE TABLE `yakflow`.`orders` (`id` BIGINT NOT NULL, `name` VARCHAR(100) NOT NULL, "
                        + "`amount` DECIMAL(18,2), PRIMARY KEY (`id`))",
                JdbcDialects.forType("MYSQL")
                        .createTableSql(new DataSourceTablePath("yakflow", null, "orders"), SCHEMA));
    }

    @Test
    void shouldGeneratePostgresqlCreateTable() {
        assertEquals(
                "CREATE TABLE \"public\".\"orders\" (\"id\" BIGINT NOT NULL, \"name\" VARCHAR(100) NOT NULL, "
                        + "\"amount\" NUMERIC(18,2), PRIMARY KEY (\"id\"))",
                JdbcDialects.forType("POSTGRE_SQL")
                        .createTableSql(new DataSourceTablePath("yakflow", "public", "orders"), SCHEMA));
    }

    @Test
    void shouldGenerateOracleCreateTable() {
        assertEquals(
                "CREATE TABLE \"APP\".\"orders\" (\"id\" NUMBER(19) NOT NULL, "
                        + "\"name\" VARCHAR2(100 CHAR) NOT NULL, \"amount\" NUMBER(18,2), PRIMARY KEY (\"id\"))",
                JdbcDialects.forType("ORACLE")
                        .createTableSql(new DataSourceTablePath(null, "APP", "orders"), SCHEMA));
    }

    @Test
    void shouldRejectMysqlTimestampWithTimeZone() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> JdbcDialects.forType("MYSQL")
                        .nativeType(new YakColumn(
                                "event_time", YakTypes.TIMESTAMP_WITH_TIME_ZONE, true, null)));
    }

    @Test
    void shouldRejectOracleTime() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> JdbcDialects.forType("ORACLE")
                        .nativeType(new YakColumn("event_time", YakTypes.TIME, true, null)));
    }

    @Test
    void shouldMarkOracleClobAsUnsupportedPrimaryKeyType() {
        JdbcNativeType type =
                JdbcDialects.forType("ORACLE").nativeType(new YakColumn("payload", YakTypes.STRING, false, null));

        assertEquals("CLOB", type.ddl());
        assertFalse(type.primaryKeySupported());
    }
}
