package io.yak.ops.business.modeling.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.enums.modeling.standard.DataType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Type mapping, DDL generation and target-independent publication rules. */
class PhysicalPublishingTest {

    private static final PhysicalAddress MYSQL_ADDRESS = PhysicalAddress.of("warehouse", " ");
    private static final PhysicalAddress PG_ADDRESS = PhysicalAddress.of("warehouse", "public");

    @Test
    void rendersStringLengthPerDialect() {
        assertEquals("varchar(32)", PhysicalTypes.of(PhysicalDialect.MYSQL, DataType.STRING, "32").sqlType());
        assertEquals("varchar(32)", PhysicalTypes.of(PhysicalDialect.POSTGRESQL, DataType.STRING, "32").sqlType());
        assertEquals("varchar2(32)", PhysicalTypes.of(PhysicalDialect.ORACLE, DataType.STRING, "32").sqlType());
    }

    @Test
    void refusesAStringWithoutALength() {
        assertThrows(
                IllegalArgumentException.class, () -> PhysicalTypes.of(PhysicalDialect.MYSQL, DataType.STRING, null));
        assertThrows(
                IllegalArgumentException.class, () -> PhysicalTypes.of(PhysicalDialect.MYSQL, DataType.STRING, "0"));
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalTypes.of(PhysicalDialect.ORACLE, DataType.STRING, "4001"));
    }

    @Test
    void rendersBigintAsNumberOnOracle() {
        var mysql = PhysicalTypes.of(PhysicalDialect.MYSQL, DataType.BIGINT, null);
        assertEquals("bigint", mysql.sqlType());
        assertEquals(PhysicalTypeSpec.BIGINT, mysql.family());
        var oracle = PhysicalTypes.of(PhysicalDialect.ORACLE, DataType.BIGINT, null);
        assertEquals("number(19)", oracle.sqlType());
        // Oracle has no BIGINT, so the canonical shape is the exact 64-bit range it maps to.
        assertEquals(PhysicalTypeSpec.DECIMAL, oracle.family());
        assertEquals(19, oracle.precision());
        assertEquals(0, oracle.scale());
    }

    @Test
    void requiresACompleteDecimalPrecisionPair() {
        assertEquals("decimal(18,2)", PhysicalTypes.of(PhysicalDialect.MYSQL, DataType.DECIMAL, "18,2").sqlType());
        assertEquals("number(18,2)", PhysicalTypes.of(PhysicalDialect.ORACLE, DataType.DECIMAL, "18,2").sqlType());
        assertThrows(
                IllegalArgumentException.class, () -> PhysicalTypes.of(PhysicalDialect.MYSQL, DataType.DECIMAL, "18"));
        assertThrows(
                IllegalArgumentException.class, () -> PhysicalTypes.of(PhysicalDialect.MYSQL, DataType.DECIMAL, "39,2"));
        assertThrows(
                IllegalArgumentException.class, () -> PhysicalTypes.of(PhysicalDialect.MYSQL, DataType.DECIMAL, "18,19"));
    }

    @Test
    void rendersTimestampWithoutTheMySqlYear2038Ceiling() {
        assertEquals("datetime(3)", PhysicalTypes.of(PhysicalDialect.MYSQL, DataType.TIMESTAMP, null).sqlType());
        assertEquals("timestamp", PhysicalTypes.of(PhysicalDialect.POSTGRESQL, DataType.TIMESTAMP, null).sqlType());
    }

    @Test
    void ordersThePrimaryKeyByKeyPosition() {
        var script = PhysicalDdlGenerator.generate(
                PhysicalDialect.MYSQL,
                MYSQL_ADDRESS,
                "dwd_order",
                List.of(
                        new PhysicalColumnSpec("order_no", DataType.STRING, "32", true, 2, "订单号"),
                        new PhysicalColumnSpec("tenant_id", DataType.BIGINT, null, false, 1, "租户")));
        assertTrue(script.script().contains("PRIMARY KEY (`tenant_id`, `order_no`)"), script.script());
        // A key column is NOT NULL even when the model did not mark it, because every engine does.
        assertTrue(script.script().contains("`tenant_id` bigint NOT NULL"), script.script());
    }

    @Test
    void writesInlineCommentsOnMySqlAndSeparateStatementsElsewhere() {
        var columns = List.of(new PhysicalColumnSpec("order_no", DataType.STRING, "32", true, null, "订单'号"));
        var mysql = PhysicalDdlGenerator.generate(PhysicalDialect.MYSQL, MYSQL_ADDRESS, "dwd_order", columns);
        assertEquals(1, mysql.statements().size());
        assertTrue(mysql.script().contains("COMMENT '订单''号'"), mysql.script());

        var postgres = PhysicalDdlGenerator.generate(PhysicalDialect.POSTGRESQL, PG_ADDRESS, "dwd_order", columns);
        assertEquals(2, postgres.statements().size());
        assertEquals(
                "COMMENT ON COLUMN \"public\".\"dwd_order\".\"order_no\" IS '订单''号'", postgres.statements().get(1));
    }

    @Test
    void doublesABackslashOnlyWhereTheEngineReadsItAsAnEscape() {
        var columns = List.of(new PhysicalColumnSpec("order_no", DataType.STRING, "32", true, null, "路径\\"));

        // MySQL would otherwise escape the closing quote; the other two store the backslash as is.
        assertTrue(
                PhysicalDdlGenerator.generate(PhysicalDialect.MYSQL, MYSQL_ADDRESS, "dwd_order", columns)
                        .script()
                        .contains("COMMENT '路径\\\\'"));
        assertTrue(
                PhysicalDdlGenerator.generate(PhysicalDialect.POSTGRESQL, PG_ADDRESS, "dwd_order", columns)
                        .script()
                        .contains("IS '路径\\'"));
        assertTrue(
                PhysicalDdlGenerator.generate(PhysicalDialect.ORACLE, PG_ADDRESS, "dwd_order", columns)
                        .script()
                        .contains("IS '路径\\'"));
    }

    @Test
    void reportsTheOracleCatalogNameInUpperCaseSoLookupsMatch() {
        assertEquals("DWD_ORDER", PhysicalDialect.ORACLE.catalogName("dwd_order"));
        // Idempotent, so a stored name can be normalized again during reconciliation.
        assertEquals("DWD_ORDER", PhysicalDialect.ORACLE.catalogName("DWD_ORDER"));
        assertEquals("dwd_order", PhysicalDialect.MYSQL.catalogName("dwd_order"));
        assertEquals("dwd_order", PhysicalDialect.POSTGRESQL.catalogName("dwd_order"));
    }

    @Test
    void omitsCommentStatementsWhenTheFieldHasNoChineseName() {
        var script = PhysicalDdlGenerator.generate(
                PhysicalDialect.POSTGRESQL,
                PG_ADDRESS,
                "dwd_order",
                List.of(new PhysicalColumnSpec("order_no", DataType.STRING, "32", true, null, "  ")));
        assertEquals(1, script.statements().size());
        assertFalse(script.script().contains("COMMENT ON"));
    }

    @Test
    void refusesAModelWithoutColumns() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalDdlGenerator.generate(PhysicalDialect.MYSQL, MYSQL_ADDRESS, "dwd_order", List.of()));
    }

    @Test
    void qualifiesTheTableByTheNamespaceTheEngineResolves() {
        assertEquals(
                "CREATE TABLE `warehouse`.`dwd_order`",
                PhysicalDdlGenerator.generate(
                                PhysicalDialect.MYSQL,
                                MYSQL_ADDRESS,
                                "dwd_order",
                                List.of(new PhysicalColumnSpec("id", DataType.BIGINT, null, true, null, null)))
                        .statements()
                        .get(0)
                        .split("\\(")[0]
                        .trim());
        assertTrue(PhysicalDdlGenerator.generate(
                        PhysicalDialect.POSTGRESQL,
                        PG_ADDRESS,
                        "dwd_order",
                        List.of(new PhysicalColumnSpec("id", DataType.BIGINT, null, true, null, null)))
                .script()
                .startsWith("CREATE TABLE \"public\".\"dwd_order\""));
        assertTrue(PhysicalDdlGenerator.generate(
                        PhysicalDialect.ORACLE,
                        PG_ADDRESS,
                        "dwd_order",
                        List.of(new PhysicalColumnSpec("id", DataType.BIGINT, null, true, null, null)))
                .script()
                .startsWith("CREATE TABLE PUBLIC.DWD_ORDER"));
    }
}
