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
