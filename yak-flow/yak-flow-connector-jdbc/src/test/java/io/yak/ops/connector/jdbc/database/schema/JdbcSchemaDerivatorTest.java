package io.yak.ops.connector.jdbc.database.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.PostgresJdbcDialect;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.AlterColumnTypeEvent;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.LogicalTypes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests schema derivation without using JDBC connections or applying schema changes. */
class JdbcSchemaDerivatorTest {

    private static final TableId TABLE = new TableId("orders_db", null, "orders");
    private static final JdbcDialect MYSQL = new MySqlJdbcDialect();
    private static final JdbcDialect POSTGRES = new PostgresJdbcDialect();

    @Test
    void createsCanonicalPostgresSchemaWithoutNativeSql() {
        Schema source = schema(
                List.of(
                        column("id", LogicalTypes.BIGINT.copy(false)),
                        column("small_value", LogicalTypes.TINYINT),
                        column("payload", LogicalTypes.varbinary(8))),
                List.of("id"));
        SchemaChangePlan plan = JdbcSchemaDerivator.derive(TABLE, source, Optional.empty(), POSTGRES);

        assertFalse(plan.blocked());
        assertTrue(plan.requiresDdl());
        CreateTableEvent event = assertInstanceOf(CreateTableEvent.class, plan.changes().getFirst());
        assertEquals(LogicalTypes.SMALLINT, event.getPhysicalSchema().column("small_value").orElseThrow().type());
        assertEquals(LogicalTypes.BINARY, event.getPhysicalSchema().column("payload").orElseThrow().type());
        assertEquals(SchemaChangePlan.Reason.TABLE_MISSING, plan.differences().getLast().reason());
        assertEquals(List.of("id"), event.getSchema().primaryKeys());
    }

    @Test
    void identifiesSafeAddAndTypeWideningWithoutApplyingSql() {
        Schema source = schema(
                List.of(column("id", LogicalTypes.BIGINT.copy(false)),
                        column("name", LogicalTypes.varchar(255)),
                        column("remark", LogicalTypes.STRING)),
                List.of("id"));
        Schema target = schema(
                List.of(column("id", LogicalTypes.BIGINT.copy(false)),
                        column("name", LogicalTypes.varchar(100))),
                List.of("id"));
        SchemaChangePlan plan = JdbcSchemaDerivator.derive(TABLE, source, Optional.of(target), POSTGRES);

        assertFalse(plan.blocked());
        assertEquals(2, plan.changes().size());
        AddColumnEvent added = assertInstanceOf(AddColumnEvent.class, plan.changes().get(0));
        assertEquals("remark", added.addedColumns().getFirst().column().name());
        assertEquals(AddColumnEvent.ColumnPosition.LAST, added.addedColumns().getFirst().position());
        AlterColumnTypeEvent alter = assertInstanceOf(AlterColumnTypeEvent.class, plan.changes().get(1));
        assertEquals(LogicalTypes.varchar(255), alter.typeMapping().get("name"));
        assertEquals(LogicalTypes.varchar(100), alter.oldTypeMapping().get("name"));
        assertEquals(LogicalTypes.varchar(100), target.column("name").orElseThrow().type());
    }

    @Test
    void flagsDifferencesWithoutDdlWhenExistingSchemaCanContainAllColumns() {
        Schema source = schema(
                List.of(column("id", LogicalTypes.BIGINT.copy(false)),
                        column("name", LogicalTypes.varchar(50))),
                List.of("id"));
        Schema target = schema(
                List.of(column("id", LogicalTypes.BIGINT.copy(false)),
                        column("name", LogicalTypes.varchar(150))),
                List.of("id"));
        SchemaChangePlan plan = JdbcSchemaDerivator.derive(TABLE, source, Optional.of(target), MYSQL);

        assertFalse(plan.blocked());
        assertFalse(plan.requiresDdl());
        assertEquals(2, plan.differences().size());
        assertTrue(plan.differences().stream().allMatch(d -> d.kind() == SchemaChangePlan.Kind.COMPATIBLE));
    }

    @Test
    void refusesPrimaryKeyChangesAndDoesNotProposePartialDdl() {
        Schema source = schema(
                List.of(column("id", LogicalTypes.BIGINT.copy(false)),
                        column("missing", LogicalTypes.varchar(30))),
                List.of("id"));
        Schema target = schema(
                List.of(column("id", LogicalTypes.BIGINT.copy(false))), List.of());

        SchemaChangePlan plan = JdbcSchemaDerivator.derive(TABLE, source, Optional.of(target), POSTGRES);
        assertTrue(plan.blocked());
        assertTrue(plan.changes().isEmpty());
        assertTrue(plan.differences().stream().anyMatch(
                d -> d.reason() == SchemaChangePlan.Reason.PRIMARY_KEY_MISMATCH));
        assertTrue(plan.differences().stream().anyMatch(
                d -> d.kind() == SchemaChangePlan.Kind.ADD_COLUMN));
    }

    @Test
    void blocksMissingNonNullableAndKeyFields() {
        Schema source = schema(
                List.of(column("id", LogicalTypes.BIGINT.copy(false)),
                        column("required", LogicalTypes.varchar(30).copy(false))),
                List.of("id"));
        Schema target = schema(
                List.of(column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));

        SchemaChangePlan plan = JdbcSchemaDerivator.derive(TABLE, source, Optional.of(target), MYSQL);
        assertTrue(plan.blocked());
        assertTrue(plan.changes().isEmpty());
        assertTrue(plan.differences().stream().anyMatch(
                d -> d.columnName().equals("required") && d.kind() == SchemaChangePlan.Kind.INCOMPATIBLE));
    }

    @Test
    void blocksTargetOnlyColumnsUntilSinkCanProjectThem() {
        Schema source = schema(
                List.of(column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        Schema target = schema(
                List.of(column("id", LogicalTypes.BIGINT.copy(false)),
                        column("server_default", LogicalTypes.INTEGER)),
                List.of("id"));

        SchemaChangePlan plan = JdbcSchemaDerivator.derive(TABLE, source, Optional.of(target), POSTGRES);
        assertTrue(plan.blocked());
        assertTrue(plan.changes().isEmpty());
        assertTrue(plan.differences().stream().anyMatch(
                d -> d.reason() == SchemaChangePlan.Reason.COLUMN_ONLY_IN_TARGET));
    }

    @Test
    void rejectsPotentialTruncationAndKeyTypeChanges() {
        Schema source = schema(
                List.of(column("id", LogicalTypes.BIGINT.copy(false)),
                        column("name", LogicalTypes.varchar(100))),
                List.of("id"));
        Schema target = schema(
                List.of(column("id", LogicalTypes.INTEGER.copy(false)),
                        column("name", LogicalTypes.varchar(30).copy(false))),
                List.of("id"));

        SchemaChangePlan plan = JdbcSchemaDerivator.derive(TABLE, source, Optional.of(target), MYSQL);
        assertTrue(plan.blocked());
        assertTrue(plan.changes().isEmpty());
        assertTrue(plan.differences().stream().anyMatch(
                d -> d.columnName().equals("id") && d.kind() == SchemaChangePlan.Kind.INCOMPATIBLE));
        assertTrue(plan.differences().stream().anyMatch(
                d -> d.columnName().equals("name") && d.reason() == SchemaChangePlan.Reason.NULLABILITY_MISMATCH));
    }

    @Test
    void detectsMetadataMismatchButNeverCopiesRawDefaultsIntoDifferenceMessages() {
        PhysicalColumn original = new PhysicalColumn(
                "name", LogicalTypes.varchar(30), "private-source-comment",
                new ColumnProperties("sensitive-source-default", false, false, null, false));
        PhysicalColumn actual = new PhysicalColumn(
                "name", LogicalTypes.varchar(50), "private-target-comment",
                new ColumnProperties("sensitive-target-default", false, false, null, false));
        Schema source = schema(List.of(column("id", LogicalTypes.BIGINT.copy(false)), original), List.of("id"));
        Schema target = schema(List.of(column("id", LogicalTypes.BIGINT.copy(false)), actual), List.of("id"));
        SchemaChangePlan plan = JdbcSchemaDerivator.derive(TABLE, source, Optional.of(target), POSTGRES);

        assertFalse(plan.blocked());
        assertFalse(plan.requiresDdl());
        assertTrue(plan.differences().stream().anyMatch(
                d -> d.kind() == SchemaChangePlan.Kind.METADATA_DIFFERENCE));
        assertFalse(plan.toString().contains("sensitive-source-default"));
        assertFalse(plan.toString().contains("sensitive-target-default"));
        assertFalse(plan.toString().contains("private-source-comment"));
    }

    @Test
    void rejectsCaseAmbiguityAndCrossDialectUnsignedTypes() {
        Schema ambiguous = schema(
                List.of(column("id", LogicalTypes.BIGINT), column("ID", LogicalTypes.BIGINT)), List.of("id"));
        assertTrue(JdbcSchemaDerivator.derive(TABLE, ambiguous, Optional.empty(), MYSQL).blocked());

        Schema unsigned = schema(
                List.of(new PhysicalColumn(
                        "id",
                        LogicalTypes.INTEGER.copy(false),
                        null,
                        new ColumnProperties(null, false, false, null, false, "int unsigned"))),
                List.of("id"));
        SchemaChangePlan plan = JdbcSchemaDerivator.derive(TABLE, unsigned, Optional.empty(), POSTGRES);
        assertTrue(plan.blocked());
        assertTrue(plan.changes().isEmpty());
    }

    @Test
    void rejectsUnresolvedTypesUnsupportedTimezoneAndUnusablePrimaryKeys() {
        Schema unknown = schema(List.of(column("id", LogicalTypes.decimal(null, null))), List.of());
        assertTrue(JdbcSchemaDerivator.derive(TABLE, unknown, Optional.empty(), MYSQL).blocked());

        Schema zoned = schema(
                List.of(column("id", LogicalTypes.INTEGER.copy(false)),
                        column("event_time", LogicalTypes.TIMESTAMP_WITH_TIME_ZONE)),
                List.of("id"));
        assertTrue(JdbcSchemaDerivator.derive(TABLE, zoned, Optional.empty(), MYSQL).blocked());

        Schema unboundedKey = schema(
                List.of(column("id", LogicalTypes.STRING.copy(false))), List.of("id"));
        assertTrue(JdbcSchemaDerivator.derive(TABLE, unboundedKey, Optional.empty(), MYSQL).blocked());
    }

    @Test
    void normalizesNativeTargetTypeWithoutTouchingSourceDefinitions() {
        assertEquals(
                LogicalTypes.TINYINT,
                MYSQL.normalizeTargetType(new io.yak.ops.core.types.Column("flag", LogicalTypes.BOOLEAN)));
        assertEquals(
                LogicalTypes.BINARY,
                POSTGRES.normalizeTargetType(
                        new io.yak.ops.core.types.Column("payload", LogicalTypes.fixedBinary(20))));
        assertEquals(
                LogicalTypes.STRING,
                MYSQL.normalizeTargetType(new io.yak.ops.core.types.Column("txt", LogicalTypes.varchar(20000))));
    }

    private static Schema schema(List<PhysicalColumn> columns, List<String> keys) {
        return new Schema(columns, keys, null);
    }

    private static PhysicalColumn column(String name, io.yak.ops.core.types.LogicalType type) {
        return new PhysicalColumn(name, type);
    }
}
