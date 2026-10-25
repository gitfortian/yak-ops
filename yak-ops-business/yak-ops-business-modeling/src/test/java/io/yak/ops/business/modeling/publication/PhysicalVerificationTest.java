package io.yak.ops.business.modeling.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.enums.modeling.standard.DataType;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Verification of an existing physical table and classification of execution failures. */
class PhysicalVerificationTest {

    private static final PhysicalColumnSpec ORDER_NO =
            new PhysicalColumnSpec("order_no", DataType.STRING, "32", true, 1, "订单号");

    private static DataSourceCatalogColumnVO observed(String name, String typeName, Integer size, Integer scale) {
        var column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName(typeName);
        column.setJdbcType(12);
        column.setSize(size);
        column.setScale(scale);
        column.setNullable(false);
        column.setPrimaryKey(true);
        column.setPrimaryKeyPosition(1);
        column.setRemarks("订单号");
        return column;
    }

    @Test
    void acceptsATableThatAlreadyMatches() {
        var mismatch = PhysicalTableVerifier.verify(
                PhysicalDialect.MYSQL, List.of(ORDER_NO), List.of(observed("order_no", "VARCHAR", 32, null)));
        assertEquals(Optional.empty(), mismatch);
    }

    @Test
    void reportsAColumnCountDifference() {
        var mismatch = PhysicalTableVerifier.verify(
                PhysicalDialect.MYSQL,
                List.of(ORDER_NO),
                List.of(observed("order_no", "VARCHAR", 32, null), observed("amount", "DECIMAL", 18, 2)));
        assertTrue(mismatch.orElseThrow().contains("2 个字段"), mismatch.orElseThrow());
    }

    @Test
    void reportsANameDifference() {
        var mismatch = PhysicalTableVerifier.verify(
                PhysicalDialect.MYSQL, List.of(ORDER_NO), List.of(observed("order_id", "VARCHAR", 32, null)));
        assertTrue(mismatch.orElseThrow().contains("物理列名为 order_id"), mismatch.orElseThrow());
    }

    @Test
    void reportsATypeFamilyDifference() {
        var mismatch = PhysicalTableVerifier.verify(
                PhysicalDialect.MYSQL, List.of(ORDER_NO), List.of(observed("order_no", "BIGINT", 19, null)));
        assertTrue(mismatch.orElseThrow().contains("类型为 BIGINT"), mismatch.orElseThrow());
    }

    @Test
    void reportsALengthDifference() {
        var mismatch = PhysicalTableVerifier.verify(
                PhysicalDialect.MYSQL, List.of(ORDER_NO), List.of(observed("order_no", "VARCHAR", 64, null)));
        assertTrue(mismatch.orElseThrow().contains("精度为 64"), mismatch.orElseThrow());
    }

    @Test
    void reportsANullabilityDifferenceInBothDirections() {
        var nullableColumn = observed("order_no", "VARCHAR", 32, null);
        nullableColumn.setNullable(true);
        var requiredButNullable = PhysicalTableVerifier.verify(
                PhysicalDialect.MYSQL, List.of(ORDER_NO), List.of(nullableColumn));
        assertTrue(requiredButNullable.orElseThrow().contains("允许为空"), requiredButNullable.orElseThrow());

        var notNullColumn = observed("order_no", "VARCHAR", 32, null);
        var optional = new PhysicalColumnSpec("order_no", DataType.STRING, "32", false, null, "订单号");
        var optionalButNotNull = PhysicalTableVerifier.verify(
                PhysicalDialect.MYSQL, List.of(optional), List.of(notNullColumn));
        assertTrue(optionalButNotNull.orElseThrow().contains("不允许为空"), optionalButNotNull.orElseThrow());
    }

    @Test
    void reportsAKeyPositionDifference() {
        var notAKey = observed("order_no", "VARCHAR", 32, null);
        notAKey.setPrimaryKey(false);
        notAKey.setPrimaryKeyPosition(null);
        var mismatch =
                PhysicalTableVerifier.verify(PhysicalDialect.MYSQL, List.of(ORDER_NO), List.of(notAKey));
        assertTrue(mismatch.orElseThrow().contains("主键位置"), mismatch.orElseThrow());
    }

    @Test
    void reportsACommentDifference() {
        var renamed = observed("order_no", "VARCHAR", 32, null);
        renamed.setRemarks("订单编号");
        var mismatch = PhysicalTableVerifier.verify(PhysicalDialect.MYSQL, List.of(ORDER_NO), List.of(renamed));
        assertTrue(mismatch.orElseThrow().contains("注释为「订单编号」"), mismatch.orElseThrow());
    }

    @Test
    void treatsAnOracleNumberAsTheFamilyThePrecisionImplies() {
        var key = new PhysicalColumnSpec("id", DataType.BIGINT, null, true, 1, null);
        var observedKey = observed("id", "NUMBER", 19, 0);
        observedKey.setRemarks(null);
        assertEquals(
                Optional.empty(), PhysicalTableVerifier.verify(PhysicalDialect.ORACLE, List.of(key), List.of(observedKey)));

        var decimal = new PhysicalColumnSpec("amount", DataType.DECIMAL, "18,2", true, 1, null);
        var observedDecimal = observed("amount", "NUMBER", 18, 2);
        observedDecimal.setRemarks(null);
        assertEquals(
                Optional.empty(),
                PhysicalTableVerifier.verify(PhysicalDialect.ORACLE, List.of(decimal), List.of(observedDecimal)));
    }

    @Test
    void classifiesConnectionFailuresAsUnknownAndRefusalsAsFailed() {
        assertEquals(
                PublicationFailures.Outcome.UNKNOWN,
                PublicationFailures.classify(new SQLException("broken pipe", "08006")));
        assertEquals(
                PublicationFailures.Outcome.UNKNOWN,
                PublicationFailures.classify(new SQLTransientConnectionException("closed")));
        assertEquals(PublicationFailures.Outcome.UNKNOWN, PublicationFailures.classify(new SQLTimeoutException("slow")));
        assertEquals(
                PublicationFailures.Outcome.FAILED,
                PublicationFailures.classify(new SQLSyntaxErrorException("bad ddl", "42000")));
        assertEquals(PublicationFailures.Outcome.FAILED, PublicationFailures.classify(new IllegalStateException("no")));
    }

    @Test
    void boundsThePersistedErrorDetail() {
        var described = PublicationFailures.describe(new SQLException("x".repeat(4_000), "42000"));
        assertEquals(1_000, described.length());
    }
}
