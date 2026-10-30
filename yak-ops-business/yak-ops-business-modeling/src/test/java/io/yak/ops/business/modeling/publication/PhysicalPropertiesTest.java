package io.yak.ops.business.modeling.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.enums.modeling.standard.DataType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Engine properties parsing and Doris DDL clauses. */
class PhysicalPropertiesTest {

    private static final PhysicalAddress ADDRESS = PhysicalAddress.of("warehouse", " ");
    private static final PhysicalAddress PG_ADDRESS = PhysicalAddress.of("warehouse", "public");

    @Test
    void parsesTheDeclaredDorisProperties() {
        var properties = PhysicalTableProperties.parse(
                "{\"dataModel\":\"UNIQUE\",\"bucketCount\":10,\"replicaCount\":3}", PhysicalDialect.DORIS);
        assertEquals("UNIQUE", properties.dataModel());
        assertEquals(10, properties.bucketCount());
        assertEquals(3, properties.replicaCount());
    }

    @Test
    void rejectsUnknownKeysAndOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> PhysicalTableProperties.parse("{\"ttl\":3}", PhysicalDialect.DORIS));
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalTableProperties.parse("{\"bucketCount\":0}", PhysicalDialect.DORIS));
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalTableProperties.parse("{\"dataModel\":\"MERGE\"}", PhysicalDialect.DORIS));
        assertThrows(
                IllegalArgumentException.class, () -> PhysicalTableProperties.parse("not json", PhysicalDialect.DORIS));
    }

    @Test
    void refusesPropertiesOnEnginesThatHaveNoPhysicalMeaning() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalTableProperties.parse("{\"bucketCount\":10}", PhysicalDialect.MYSQL));
        assertEquals(PhysicalTableProperties.NONE, PhysicalTableProperties.parse(null, PhysicalDialect.MYSQL));
        assertEquals(PhysicalTableProperties.NONE, PhysicalTableProperties.parse(" ", PhysicalDialect.ORACLE));
    }

    @Test
    void dorisCreateCarriesTheDataModelDistributionAndReplicaCount() {
        var columns = List.of(
                new PhysicalColumnSpec("order_no", DataType.STRING, "32", true, 1, "订单号"),
                new PhysicalColumnSpec("amount", DataType.DECIMAL, "18,2", false, null, "金额"));
        var script = PhysicalDdlGenerator.generate(
                PhysicalDialect.DORIS,
                ADDRESS,
                "dwd_order",
                columns,
                new PhysicalTableProperties("UNIQUE", 10, 3));
        var statement = script.statements().get(0);
        assertTrue(statement.contains("UNIQUE KEY(`order_no`)"), statement);
        assertTrue(statement.contains("DISTRIBUTED BY HASH(`order_no`) BUCKETS 10"), statement);
        assertTrue(statement.contains("PROPERTIES (\"replication_num\" = \"3\")"), statement);
        assertTrue(statement.contains("`amount` decimal(18,2)"), statement);
    }

    @Test
    void dorisCreateRefusesToGuessTheDistribution() {
        var columns = List.of(new PhysicalColumnSpec("order_no", DataType.STRING, "32", true, null, "订单号"));
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalDdlGenerator.generate(
                        PhysicalDialect.DORIS, ADDRESS, "dwd_order", columns, PhysicalTableProperties.NONE));
        // A data model without any primary key has nothing to key on; the engine would reject it.
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalDdlGenerator.generate(
                        PhysicalDialect.DORIS,
                        ADDRESS,
                        "dwd_order",
                        columns,
                        new PhysicalTableProperties("UNIQUE", 10, null)));
    }

    @Test
    void alterStatementsFollowTheDiffPerDialect() {
        var columns = List.of(
                new PhysicalColumnSpec("order_no", DataType.STRING, "32", true, null, "订单号"),
                new PhysicalColumnSpec("amount", DataType.DECIMAL, "18,2", false, null, "金额"));
        var changes = List.of(
                new SchemaDiff(SchemaDiff.Kind.ADD, "amount", null, null, "新增列 amount"),
                new SchemaDiff(
                        SchemaDiff.Kind.CHANGE,
                        "order_no",
                        "varchar(64)",
                        "varchar(32)",
                        "列 order_no 类型由 varchar(64) 变更为 varchar(32)"),
                new SchemaDiff(SchemaDiff.Kind.DROP, "legacy", "varchar(8)", null, "删除列 legacy"));

        var mysql = PhysicalDdlGenerator.generateAlter(PhysicalDialect.MYSQL, ADDRESS, "dwd_order", columns, changes);
        assertEquals(List.of(
                "ALTER TABLE `warehouse`.`dwd_order` ADD COLUMN `amount` decimal(18,2) COMMENT '金额'",
                "ALTER TABLE `warehouse`.`dwd_order` MODIFY COLUMN `order_no` varchar(32) NOT NULL COMMENT '订单号'",
                "ALTER TABLE `warehouse`.`dwd_order` DROP COLUMN `legacy`"), mysql.statements());

        var postgres =
                PhysicalDdlGenerator.generateAlter(PhysicalDialect.POSTGRESQL, PG_ADDRESS, "dwd_order", columns, changes);
        assertTrue(postgres.statements().get(2).contains("ALTER COLUMN \"order_no\" TYPE varchar(32) USING"), postgres.script());
        assertTrue(postgres.statements().get(3).contains("SET NOT NULL"), postgres.script());
        assertTrue(postgres.statements().get(5).contains("DROP COLUMN \"legacy\""), postgres.script());
    }

    @Test
    void diffReportsAddChangeAndDropOrderedByKind() {
        var dialect = PhysicalDialect.MYSQL;
        var expected = List.of(
                new PhysicalColumnSpec("order_no", DataType.STRING, "32", true, 1, null),
                new PhysicalColumnSpec("amount", DataType.DECIMAL, "18,2", false, null, null));
        var observed = List.of(
                observed("order_no", "VARCHAR", 64),
                observed("legacy", "VARCHAR", 8));

        var changes = SchemaDiff.diff(dialect, expected, observed);

        assertEquals(3, changes.size());
        assertEquals(SchemaDiff.Kind.CHANGE, changes.get(0).kind());
        assertEquals("order_no", changes.get(0).column());
        assertEquals(SchemaDiff.Kind.ADD, changes.get(1).kind());
        assertEquals("amount", changes.get(1).column());
        assertEquals(SchemaDiff.Kind.DROP, changes.get(2).kind());
        assertTrue(SchemaDiff.dropsData(changes));
    }

    @Test
    void diffTreatsAMissingColumnAsAnAdd() {
        var dialect = PhysicalDialect.MYSQL;
        var expected = List.of(new PhysicalColumnSpec("order_no", DataType.STRING, "32", true, 1, null));

        var changes = SchemaDiff.diff(dialect, expected, List.of());

        assertEquals(1, changes.size());
        assertEquals(SchemaDiff.Kind.ADD, changes.get(0).kind());
        assertFalse(SchemaDiff.dropsData(changes));
    }

    private static DataSourceCatalogColumnVO observed(String name, String typeName, int size) {
        var column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName(typeName);
        column.setJdbcType(12);
        column.setSize(size);
        column.setNullable(false);
        return column;
    }
}
