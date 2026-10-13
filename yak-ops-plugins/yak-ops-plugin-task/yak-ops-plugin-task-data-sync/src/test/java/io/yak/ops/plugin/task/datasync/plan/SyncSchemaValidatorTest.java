package io.yak.ops.plugin.task.datasync.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.TaskPluginException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies the Definition and Job planning checks against executable JDBC table mappings. */
class SyncSchemaValidatorTest {

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

        SyncSchemaValidator.validate(source, target, true);
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

        assertThrows(TaskPluginException.class, () -> SyncSchemaValidator.validate(source, target, false));
    }

    @Test
    void rejectsImplicitNumericWideningBeforeJdbcSinkConstruction() {
        TableSchema source = table(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("amount", LogicalTypes.INTEGER));
        TableSchema target = table(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("amount", LogicalTypes.BIGINT));

        assertThrows(TaskPluginException.class, () -> SyncSchemaValidator.validate(source, target, false));
    }

    @Test
    void rejectsCaseOnlyColumnMatchesAndAmbiguousNames() {
        TableSchema source = table(new Column("id", LogicalTypes.BIGINT.copy(false)));
        TableSchema target = new TableSchema(
                List.of(new Column("ID", LogicalTypes.BIGINT.copy(false))), List.of("ID"));
        TableSchema ambiguous = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT), new Column("ID", LogicalTypes.BIGINT)),
                List.of("id"));

        assertThrows(TaskPluginException.class, () -> SyncSchemaValidator.validate(source, target, false));
        assertThrows(TaskPluginException.class, () -> SyncSchemaValidator.validate(ambiguous, source, false));
    }

    @Test
    void rejectsNarrowerTargetColumnCapacity() {
        TableSchema source = table(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("name", LogicalTypes.varchar(64)));
        TableSchema target = table(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("name", LogicalTypes.varchar(32)));

        assertThrows(TaskPluginException.class, () -> SyncSchemaValidator.validate(source, target, false));
    }

    @Test
    void requiresMatchingKeysForUpsert() {
        List<Column> columns = List.of(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("name", LogicalTypes.varchar(64)));
        TableSchema source = new TableSchema(columns, List.of("id"));
        TableSchema target = new TableSchema(columns, List.of("name"));

        assertThrows(TaskPluginException.class, () -> SyncSchemaValidator.validate(source, target, true));
    }

    @Test
    void rejectsUnresolvedLogicalTypes() {
        TableSchema source = table(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("amount", LogicalTypes.decimal(null, null)));
        TableSchema target = table(
                new Column("id", LogicalTypes.BIGINT.copy(false)),
                new Column("amount", LogicalTypes.decimal(20, 2)));

        assertThrows(TaskPluginException.class, () -> SyncSchemaValidator.validate(source, target, false));
    }

    private static TableSchema table(Column... columns) {
        return new TableSchema(List.of(columns), List.of("id"));
    }
}
