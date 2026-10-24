package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies JDBC target projection checks against executable table mappings. */
class JdbcSinkSchemaValidatorTest {

    private static final TableId SOURCE = new TableId("app", null, "orders");
    private static final TableId TARGET = new TableId(null, "public", "orders_copy");

    @Test
    void acceptsTargetOrderedProjectionAndExtraSourceColumns() {
        TableSchema source = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(32)),
                        new Column("extra", LogicalTypes.INTEGER)),
                List.of("id"));
        TableSchema target = new TableSchema(
                List.of(
                        new Column("name", LogicalTypes.varchar(64)),
                        new Column("id", LogicalTypes.BIGINT.copy(false))),
                List.of("id"));

        JdbcSinkSchemaValidator.validate(source, target, true);
        JdbcTableWritePlan writePlan =
                new JdbcTableWritePlan(SOURCE, TARGET, target, JdbcWriteMode.UPSERT, source, Map.of());

        assertEquals(List.of(1, 0), writePlan.sourcePositions());
    }

    @Test
    void rejectsNullableTargetColumnWithoutSourceMapping() {
        TableSchema source = table(
                new Column("id", LogicalTypes.BIGINT.copy(false)));
        TableSchema target = table(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("optional", LogicalTypes.varchar(64)));

        assertThrows(IllegalArgumentException.class, () -> JdbcSinkSchemaValidator.validate(source, target, false));
    }

    @Test
    void acceptsSafeNumericWideningBeforeJdbcSinkConstruction() {
        TableSchema source = table(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("amount", LogicalTypes.INTEGER));
        TableSchema target = table(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("amount", LogicalTypes.BIGINT));

        JdbcSinkSchemaValidator.validate(source, target, false);
        var plan = new JdbcTableWritePlan(SOURCE, TARGET, target, JdbcWriteMode.APPEND, source, Map.of());
        assertEquals(42L, plan.project(io.yak.ops.core.data.GenericRowData.of(5L, 42), io.yak.ops.core.data.RowKind.INSERT)
                .getLong(1));
    }

    @Test
    void rejectsCaseOnlyColumnMatchesAndAmbiguousNames() {
        TableSchema source = table(new Column("id", LogicalTypes.BIGINT.copy(false)));
        TableSchema target = new TableSchema(
                List.of(new Column("ID", LogicalTypes.BIGINT.copy(false))), List.of("ID"));
        TableSchema ambiguous = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT), new Column("ID", LogicalTypes.BIGINT)),
                List.of("id"));

        assertThrows(IllegalArgumentException.class, () -> JdbcSinkSchemaValidator.validate(source, target, false));
        assertThrows(IllegalArgumentException.class, () -> JdbcSinkSchemaValidator.validate(ambiguous, source, false));
    }

    @Test
    void rejectsNarrowerTargetColumnCapacity() {
        TableSchema source = table(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("name", LogicalTypes.varchar(64)));
        TableSchema target = table(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("name", LogicalTypes.varchar(32)));

        assertThrows(IllegalArgumentException.class, () -> JdbcSinkSchemaValidator.validate(source, target, false));
    }

    @Test
    void requiresMatchingKeysForUpsert() {
        List<Column> columns = List.of(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("name", LogicalTypes.varchar(64)));
        TableSchema source = new TableSchema(columns, List.of("id"));
        TableSchema target = new TableSchema(columns, List.of("name"));

        assertThrows(IllegalArgumentException.class, () -> JdbcSinkSchemaValidator.validate(source, target, true));
    }

    @Test
    void rejectsUnresolvedLogicalTypes() {
        TableSchema source = table(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("amount", LogicalTypes.decimal(null, null)));
        TableSchema target = table(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("amount", LogicalTypes.decimal(20, 2)));

        assertThrows(IllegalArgumentException.class, () -> JdbcSinkSchemaValidator.validate(source, target, false));
    }

    private static TableSchema table(Column... columns) {
        return new TableSchema(List.of(columns), List.of("id"));
    }
}
