package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JdbcTableWritePlanTest {

    private static final TableId SOURCE = new TableId(null, "public", "input");
    private static final TableId TARGET = new TableId(null, "public", "output");

    @Test
    void resolvedMappingKeepsTheWritePlanImmutable() {
        TableSchema source = sourceSchema();
        TableSchema target = targetSchema();
        List<Integer> positions = new ArrayList<>(List.of(1, 0));
        JdbcTableWritePlan plan =
                new JdbcTableWritePlan(SOURCE, TARGET, target, JdbcWriteMode.UPSERT, source, positions);
        positions.set(0, 0);

        assertEquals(List.of(1, 0), plan.sourcePositions());
        assertThrows(UnsupportedOperationException.class, () -> plan.sourcePositions().add(0));
        assertEquals(new JdbcTableWritePlan(
                SOURCE, TARGET, target, JdbcWriteMode.UPSERT, source, List.of(1, 0)), plan);
    }

    @Test
    void targetProjectionDetachesSourceRowsAndPreservesPrimaryKeyOrder() {
        JdbcTableWritePlan plan = new JdbcTableWritePlan(
                SOURCE,
                TARGET,
                targetSchema(),
                JdbcWriteMode.UPSERT,
                sourceSchema(),
                Map.of("ID", "SOURCE_ID", "NAME", "SOURCE_NAME"));
        GenericRowData input = GenericRowData.of("before", 123L);
        RowData output = plan.project(input, RowKind.INSERT);

        assertEquals(123L, output.getLong(0));
        assertEquals("before", output.getString(1));
        input.setField(0, "after");
        input.setField(1, 456L);
        assertEquals(123L, output.getLong(0));
        assertEquals("before", output.getString(1));

        assertEquals(List.of("ID"), plan.keySchema().columns().stream().map(Column::name).toList());
        RowData deleted = plan.project(input, RowKind.DELETE);
        assertEquals(1, deleted.getArity());
        assertEquals(456L, deleted.getLong(0));
        assertFalse(plan.sourcePositions().isEmpty());
    }

    @Test
    void schemaReplacementRemovesDeletedFieldMappingsBeforeWriterRebuild() {
        TableSchema sourceBefore = new TableSchema(
                List.of(
                        new Column("SOURCE_NAME", LogicalTypes.STRING),
                        new Column("SOURCE_ID", LogicalTypes.BIGINT),
                        new Column("SOURCE_REMARK", LogicalTypes.varchar(200))),
                List.of("SOURCE_ID"));
        TableSchema targetBefore = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.BIGINT),
                        new Column("NAME", LogicalTypes.STRING),
                        new Column("REMARK", LogicalTypes.varchar(200))),
                List.of("ID"));
        JdbcTableWritePlan old = new JdbcTableWritePlan(
                SOURCE, TARGET, targetBefore, JdbcWriteMode.UPSERT, sourceBefore,
                Map.of("ID", "SOURCE_ID", "NAME", "SOURCE_NAME", "REMARK", "SOURCE_REMARK"));
        JdbcTableWritePlan updated = old.withSchemas(sourceSchema(), targetSchema());
        assertEquals(List.of(1, 0), updated.sourcePositions());
        assertEquals(2, updated.schema().columnCount());
        RowData projected = updated.project(GenericRowData.of("updated", 123L), RowKind.UPDATE_AFTER);
        assertEquals("updated", projected.getString(1));
        assertEquals(123L, projected.getLong(0));
    }


    @Test
    void convertsSourceValuesToExactTargetLogicalTypesAndKeys() {
        var source = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.INTEGER.copy(false)),
                        new Column("small", LogicalTypes.TINYINT),
                        new Column("amount", LogicalTypes.decimal(6, 2)),
                        new Column("qty", LogicalTypes.SMALLINT),
                        new Column("enabled", LogicalTypes.BOOLEAN),
                        new Column("weight", LogicalTypes.FLOAT),
                        new Column("created", LogicalTypes.DATE)),
                List.of("id"));
        var target = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("small", LogicalTypes.SMALLINT),
                        new Column("amount", LogicalTypes.decimal(8, 3)),
                        new Column("qty", LogicalTypes.decimal(6, 0)),
                        new Column("enabled", LogicalTypes.INTEGER),
                        new Column("weight", LogicalTypes.DOUBLE),
                        new Column("created", LogicalTypes.timestamp(0))),
                List.of("id"));
        var plan = new JdbcTableWritePlan(SOURCE, TARGET, target, JdbcWriteMode.UPSERT, source, Map.of());
        var row = GenericRowData.of(7, (byte) 12, new java.math.BigDecimal("12.50"), (short) 23,
                true, 1.25f, java.time.LocalDate.of(2026, 10, 24));
        RowData projected = plan.project(row, RowKind.INSERT);
        assertEquals(7L, projected.getLong(0));
        assertEquals((short) 12, projected.getShort(1));
        assertEquals(new java.math.BigDecimal("12.500"), projected.getDecimal(2, 8, 3));
        assertEquals(new java.math.BigDecimal("23"), projected.getDecimal(3, 6, 0));
        assertEquals(1, projected.getInt(4));
        assertEquals(1.25d, projected.getDouble(5));
        assertEquals(java.time.LocalDateTime.of(2026, 10, 24, 0, 0), projected.getTimestamp(6, 0));
        RowData keys = plan.project(row, RowKind.DELETE);
        assertEquals(7L, keys.getLong(0));
        assertEquals(1, keys.getArity());
        assertEquals(7L, plan.project(row, RowKind.UPDATE_BEFORE).getLong(0));
    }

    @Test
    void rejectsNarrowingAndNullsBeforeTheyReachJdbcBatch() {
        TableSchema source = new TableSchema(
                List.of(new Column("id", LogicalTypes.INTEGER.copy(false)),
                        new Column("value", LogicalTypes.SMALLINT.copy(false))),
                List.of("id"));
        TableSchema unsafe = new TableSchema(
                List.of(new Column("id", LogicalTypes.SMALLINT.copy(false)),
                        new Column("value", LogicalTypes.TINYINT)),
                List.of("id"));
        assertThrows(IllegalArgumentException.class,
                () -> new JdbcTableWritePlan(SOURCE, TARGET, unsafe, JdbcWriteMode.APPEND, source, Map.of()));

        TableSchema target = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("value", LogicalTypes.INTEGER.copy(false))),
                List.of("id"));
        var plan = new JdbcTableWritePlan(SOURCE, TARGET, target, JdbcWriteMode.APPEND, source, Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> plan.project(GenericRowData.of(1, null), RowKind.INSERT));
    }

    @Test
    void rejectsDecimalsThatDoNotFitTargetPrecisionWithoutRounding() {
        TableSchema source = new TableSchema(
                List.of(new Column("amount", LogicalTypes.decimal(6, 2))), List.of());
        TableSchema target = new TableSchema(
                List.of(new Column("amount", LogicalTypes.decimal(7, 3))), List.of());
        var plan = new JdbcTableWritePlan(SOURCE, TARGET, target, JdbcWriteMode.APPEND, source, Map.of());
        assertEquals(new java.math.BigDecimal("10.250"),
                plan.project(GenericRowData.of(new java.math.BigDecimal("10.25")), RowKind.INSERT)
                        .getDecimal(0, 7, 3));
        assertThrows(IllegalArgumentException.class,
                () -> plan.project(GenericRowData.of(new java.math.BigDecimal("99999.99")), RowKind.INSERT));
    }

    @Test
    void upsertRequiresPhysicalSourceIdentityNotAnArbitraryChunkColumn() {
        var sourceColumns = List.of(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("bucket", LogicalTypes.BIGINT.copy(false)),
                new Column("value", LogicalTypes.varchar(40)));
        var source = new TableSchema(sourceColumns, List.of("id"));
        var keyless = new TableSchema(sourceColumns, List.of());
        var target = new TableSchema(sourceColumns, List.of("bucket"));

        assertThrows(IllegalArgumentException.class,
                () -> new JdbcTableWritePlan(SOURCE, TARGET, target, JdbcWriteMode.UPSERT, source, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new JdbcTableWritePlan(SOURCE, TARGET, target, JdbcWriteMode.UPSERT, keyless, Map.of()));
        // APPEND has no DELETE or UPDATE semantics, so it does not need a source row identity.
        var append = new JdbcTableWritePlan(SOURCE, TARGET, target, JdbcWriteMode.APPEND, keyless, Map.of());
        assertEquals(JdbcWriteMode.APPEND, append.writeMode());
    }

    @Test
    void upsertAcceptsRenamedPhysicalKeysButRejectsKeyMappingToNonKeySourceColumns() {
        var source = new TableSchema(
                List.of(
                        new Column("source_tenant", LogicalTypes.BIGINT.copy(false)),
                        new Column("source_id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(40))),
                List.of("source_tenant", "source_id"));
        var target = new TableSchema(
                List.of(
                        new Column("name", LogicalTypes.varchar(40)),
                        new Column("tenant", LogicalTypes.BIGINT.copy(false)),
                        new Column("id", LogicalTypes.BIGINT.copy(false))),
                List.of("tenant", "id"));
        var valid = new JdbcTableWritePlan(SOURCE, TARGET, target, JdbcWriteMode.UPSERT, source,
                Map.of("tenant", "source_tenant", "id", "source_id"));
        assertEquals(List.of(2, 0, 1), valid.sourcePositions());
        assertEquals(List.of(15L, 21L), List.of(
                valid.project(GenericRowData.of(15L, 21L, "same"), RowKind.DELETE).getLong(0),
                valid.project(GenericRowData.of(15L, 21L, "same"), RowKind.DELETE).getLong(1)));

        assertThrows(IllegalArgumentException.class,
                () -> new JdbcTableWritePlan(SOURCE, TARGET, target, JdbcWriteMode.UPSERT, source,
                        Map.of("tenant", "source_tenant", "id", "name")));
        assertThrows(IllegalArgumentException.class,
                () -> new JdbcTableWritePlan(SOURCE, TARGET, target, JdbcWriteMode.UPSERT, source,
                        Map.of("tenant", "source_id", "id", "source_id")));
    }

    @Test
    void nativeUpsertAndDeleteUseTheTargetsPrimaryKeyAcrossDialects() {
        var schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(40))),
                List.of("id"));
        var plan = new JdbcTableWritePlan(SOURCE, TARGET, schema, JdbcWriteMode.UPSERT);
        var dialects = List.of(
                new io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect(),
                new io.yak.ops.connector.jdbc.database.internal.dialect.PostgresJdbcDialect(),
                new io.yak.ops.connector.jdbc.database.internal.dialect.OracleJdbcDialect());
        for (var dialect : dialects) {
            String insert = plan.sql(dialect);
            String delete = dialect.deleteSql(TARGET, schema);
            assertEquals(true, insert.contains("id") || insert.contains("ID"));
            assertEquals(true, delete.contains("WHERE"));
            assertEquals(true, delete.contains("id") || delete.contains("ID"));
            assertEquals(1, plan.project(GenericRowData.of(9L, "same"), RowKind.DELETE).getArity());
        }
    }

    private static TableSchema sourceSchema() {
        return new TableSchema(
                List.of(new Column("SOURCE_NAME", LogicalTypes.STRING),
                        new Column("SOURCE_ID", LogicalTypes.BIGINT)),
                List.of("SOURCE_ID"));
    }

    private static TableSchema targetSchema() {
        return new TableSchema(
                List.of(new Column("ID", LogicalTypes.BIGINT),
                        new Column("NAME", LogicalTypes.STRING)),
                List.of("ID"));
    }
}
