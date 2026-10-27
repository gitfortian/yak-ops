package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.base.source.reader.SchemaChangeHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.configuration.SchemaEvolutionOptions;
import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.Test;

/** Exercises structured Debezium DDL, ordered row conversion and Source mailbox schema-change handling. */
class MySqlSchemaChangeConverterTest {

    private static final TableId SOURCE = new TableId("shop", null, "orders");
    private static final TableSchema ORIGINAL = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(64))),
            List.of("id"));

    private static final org.apache.kafka.connect.data.Schema TABLE_CHANGE =
            SchemaBuilder.struct()
                    .field("type", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                    .field("id", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                    .field("table", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                    .build();

    private static final org.apache.kafka.connect.data.Schema DDL =
            SchemaBuilder.struct()
                    .field("ddl", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                    .field("tableChanges", SchemaBuilder.array(TABLE_CHANGE).build())
                    .build();

    private static final String NEW_TABLE = """
            {"primaryKeyColumnNames":["id"],"columns":[
                {"name":"id","jdbcType":-5,"typeName":"BIGINT","position":1,"optional":false,"autoIncremented":false,"generated":false},
                {"name":"name","jdbcType":12,"typeName":"VARCHAR","length":64,"position":2,"optional":true,"autoIncremented":false,"generated":false},
                {"name":"note","jdbcType":12,"typeName":"VARCHAR","length":128,"position":3,"optional":true,"autoIncremented":false,"generated":false}
            ]}
            """;

    @Test
    void initialCreateIsSchemaBootstrapButRuntimeCreateRemainsRejected() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL), Map.of(), true);
        SourceRecord initial = ddl("CREATE", NEW_TABLE, 60L);

        java.util.Set<TableId> registered = new java.util.HashSet<>();
        converter.applyInitialSchemaTables(initial, registered);
        assertEquals(java.util.Set.of(SOURCE), registered);
        converter.applyInitialSchemaTables(ddl("ALTER", NEW_TABLE, 61L), registered);
        assertEquals(java.util.Set.of(SOURCE), registered);
        converter.applyInitialSchemaTables(ddl("DROP", NEW_TABLE, 62L), registered);
        assertTrue(registered.isEmpty());
        converter.applyInitialSchemaTables(ddl("CREATE", NEW_TABLE, 63L), registered);
        assertEquals(java.util.Set.of(SOURCE), registered);
        assertThrows(UnsupportedOperationException.class, () -> converter.convert(initial));
        assertThrows(
                IllegalStateException.class,
                () -> converter.applyInitialSchemaTables(insertWithNote(64L), registered));
        // Initial CREATE metadata is consumed only by the engine before the completed
        // schema-snapshot heartbeat; it must not change the registered runtime schema.
        assertEquals(2, converter.convert(ddl("ALTER", NEW_TABLE, 63L))
                .schemaChange().before().columns().size());
    }

    @Test
    void ddlCarriesSchemaChangeAndFollowingRowsUseNewColumnPositions() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL), Map.of(), true);
        BinlogEvent change = converter.convert(ddl("ALTER", NEW_TABLE, 100L));
        assertTrue(change.records().isEmpty());
        SourceSchemaChange schema = change.schemaChange();
        assertEquals(SOURCE, schema.tableId());
        assertEquals(2, schema.before().columns().size());
        assertEquals(3, schema.after().columns().size());
        assertEquals("note", schema.after().columns().getLast().name());
        assertEquals(128, schema.after().toTableSchema().column(2).length());

        BinlogEvent row = converter.convert(insertWithNote(101L));
        assertEquals(1, row.records().size());
        assertEquals(3, row.records().getFirst().row().getArity());
        assertEquals("hello", row.records().getFirst().row().getString(2));
        assertTrue(schema.eventId().length() == 64);
    }

    @Test
    void quotedDebeziumTableIdentityStillEmitsIndependentSchemaChange() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL), Map.of(), true);
        Struct tableChange = new Struct(TABLE_CHANGE)
                .put("type", "ALTER")
                .put("id", "\"shop\".\"orders\"")
                .put("table", NEW_TABLE);
        Struct envelope = new Struct(DDL)
                .put("ddl", "redacted-structured-source-event")
                .put("tableChanges", List.of(tableChange));
        SourceRecord record = new SourceRecord(
                Map.of("server", "yak"),
                Map.of("file", "mysql-bin.000001", "pos", 100L),
                "yak.shop",
                DDL,
                envelope);
        BinlogEvent change = converter.convert(record);
        assertTrue(change.records().isEmpty());
        assertEquals(SOURCE, change.schemaChange().tableId());
        assertEquals(3, change.schemaChange().after().columns().size());
    }

    @Test
    void kafkaConnectStructTableMetadataIsConvertedWithoutLosingColumnTypes() {
        var columnMetadata = SchemaBuilder.struct()
                .field("name", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("jdbcType", org.apache.kafka.connect.data.Schema.INT32_SCHEMA)
                .field("typeName", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("length", org.apache.kafka.connect.data.Schema.OPTIONAL_INT32_SCHEMA)
                .field("position", org.apache.kafka.connect.data.Schema.INT32_SCHEMA)
                .field("autoIncremented", org.apache.kafka.connect.data.Schema.BOOLEAN_SCHEMA)
                .field("generated", org.apache.kafka.connect.data.Schema.BOOLEAN_SCHEMA)
                .field("optional", org.apache.kafka.connect.data.Schema.BOOLEAN_SCHEMA)
                .build();
        var idColumn = new Struct(columnMetadata)
                .put("name", "id")
                .put("jdbcType", java.sql.Types.BIGINT)
                .put("typeName", "BIGINT")
                .put("position", 1)
                .put("autoIncremented", false)
                .put("generated", false)
                .put("optional", false);
        var nameColumn = new Struct(columnMetadata)
                .put("name", "name")
                .put("jdbcType", java.sql.Types.VARCHAR)
                .put("typeName", "VARCHAR")
                .put("length", 64)
                .put("position", 2)
                .put("autoIncremented", false)
                .put("generated", false)
                .put("optional", true);
        var noteColumn = new Struct(columnMetadata)
                .put("name", "note")
                .put("jdbcType", java.sql.Types.VARCHAR)
                .put("typeName", "VARCHAR")
                .put("length", 128)
                .put("position", 3)
                .put("autoIncremented", false)
                .put("generated", false)
                .put("optional", true);
        var tableMetadata = SchemaBuilder.struct()
                .field(
                        "primaryKeyColumnNames",
                        SchemaBuilder.array(org.apache.kafka.connect.data.Schema.STRING_SCHEMA).build())
                .field("columns", SchemaBuilder.array(columnMetadata).build())
                .build();
        var table = new Struct(tableMetadata)
                .put("primaryKeyColumnNames", List.of("id"))
                .put("columns", List.of(idColumn, nameColumn, noteColumn));
        var changeMetadata = SchemaBuilder.struct()
                .field("type", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("id", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("table", tableMetadata)
                .build();
        var change = new Struct(changeMetadata)
                .put("type", "ALTER")
                .put("id", "\"shop\".\"orders\"")
                .put("table", table);
        var ddlMetadata = SchemaBuilder.struct()
                .field("ddl", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("tableChanges", SchemaBuilder.array(changeMetadata).build())
                .build();
        var ddl = new Struct(ddlMetadata)
                .put("ddl", "redacted-structured-source-event")
                .put("tableChanges", List.of(change));
        var source = new SourceRecord(
                Map.of("server", "yak"),
                Map.of("file", "mysql-bin.000001", "pos", 200L),
                "yak.shop",
                ddlMetadata,
                ddl);

        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL), Map.of(), true);
        SourceSchemaChange evolved = converter.convert(source).schemaChange();
        assertEquals(SOURCE, evolved.tableId());
        assertEquals(3, evolved.after().columns().size());
        assertEquals(128, evolved.after().toTableSchema().column(2).length());
    }

    @Test
    void emptyStructuredTableChangesAdvancesOffsetWithoutMutatingRegisteredSchema() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL), Map.of(), true);
        Struct ddl = new Struct(DDL)
                .put("ddl", "CREATE DATABASE unrelated")
                .put("tableChanges", List.of());
        SourceRecord source = new SourceRecord(
                Map.of("server", "yak"),
                Map.of("file", "mysql-bin.000001", "pos", 99L),
                "yak.shop",
                DDL,
                ddl);
        BinlogEvent ignored = converter.convert(source);
        assertTrue(ignored.records().isEmpty());
        assertEquals(null, ignored.schemaChange());
        assertEquals(99L, ignored.offset().position().get("pos"));

        SourceSchemaChange later = converter.convert(ddl("ALTER", NEW_TABLE, 100L)).schemaChange();
        assertEquals(2, later.before().columns().size());
        assertEquals(3, later.after().columns().size());
    }

    @Test
    void rowSchemaMismatchUsesDebeziumMetadataWithoutQueryingLiveCatalog() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL), Map.of(), true);
        SourceRecord row = insertWithNote(101L);
        assertEquals(SOURCE, converter.schemaMismatch(row));
        var previous = new io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 100L));
        BinlogEvent transition = converter.reconcileRowSchema(row, previous);
        assertEquals(previous, transition.offset());
        assertTrue(transition.records().isEmpty());
        assertEquals(3, transition.schemaChange().after().columns().size());
        assertEquals(null, converter.schemaMismatch(row));
        BinlogEvent delivered = converter.convert(row);
        assertEquals(101L, delivered.offset().position().get("pos"));
        assertEquals("hello", delivered.records().getFirst().row().getString(2));
    }

    @Test
    void incompleteRowMetadataFailsClosedBeforeUpdatingSourceSchema() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL), Map.of(), true);
        SourceRecord row = insertWithNote(101L, false);
        var previous = new io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 100L));
        assertThrows(UnsupportedOperationException.class, () -> converter.reconcileRowSchema(row, previous));
        assertEquals(SOURCE, converter.schemaMismatch(row));
    }


    @Test
    void structuredDdlMatchesJdbcTypeMappingsAndPreservesFractions() throws Exception {
        String table = """
                {"primaryKeyColumnNames":["id"],"columns":[
                  {"name":"id","jdbcType":-5,"typeName":"BIGINT","position":1,"optional":false,"autoIncremented":false,"generated":false},
                  {"name":"name","jdbcType":12,"typeName":"VARCHAR","length":64,"position":2,"optional":true,"autoIncremented":false,"generated":false},
                  {"name":"bits","jdbcType":-7,"typeName":"BIT","length":8,"position":3,"optional":true,"autoIncremented":false,"generated":false},
                  {"name":"amount","jdbcType":3,"typeName":"DECIMAL","length":14,"scale":2,"position":4,"optional":true,"autoIncremented":false,"generated":false},
                  {"name":"updated_at","jdbcType":93,"typeName":"DATETIME","length":0,"scale":3,"position":5,"optional":true,"autoIncremented":false,"generated":false}
                ]}
                """;
        var after = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL), Map.of(), true)
                .convert(ddl("ALTER", table, 201L)).schemaChange().after();
        assertEquals(LogicalTypes.varbinary(8), after.column("bits").orElseThrow().type());
        assertEquals(LogicalTypes.decimal(14, 2), after.column("amount").orElseThrow().type());
        assertEquals(LogicalTypes.timestamp(3), after.column("updated_at").orElseThrow().type());
    }

    @Test
    void unsupportedStructuredDdlDoesNotAdvanceTheRegisteredSchema() {
        String spatial = NEW_TABLE.replace(
                "\"jdbcType\":12,\"typeName\":\"VARCHAR\",\"length\":128",
                "\"jdbcType\":-2,\"typeName\":\"POINT\",\"length\":0");
        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL), Map.of(), true);
        assertThrows(UnsupportedOperationException.class, () -> converter.convert(ddl("ALTER", spatial, 202L)));
        assertEquals(2, converter.convert(ddl("ALTER", NEW_TABLE, 203L))
                .schemaChange().before().columns().size());
    }

    @Test
    void changingCdcFieldTypeCannotKeepStalePhysicalMetadata() {
        var before = Schema.from(
                ORIGINAL, null, Map.of(), Map.of("name",
                        new io.yak.ops.core.event.ColumnProperties(null, false, false, null, false, "varchar(64)")));
        String altered = NEW_TABLE.replace(
                "\"typeName\":\"VARCHAR\",\"length\":64",
                "\"typeName\":\"VARCHAR\",\"length\":128");
        var after = new MySqlDebeziumRecordConverter(
                Map.of(SOURCE, ORIGINAL), Map.of(SOURCE, before), true)
                .convert(ddl("ALTER", altered, 204L)).schemaChange().after();
        assertEquals(null, after.column("name").orElseThrow().properties());
        assertEquals(128, after.toTableSchema().column(1).length());
    }

    @Test
    void disablingEvolutionRejectsDdlBeforeAdvancingTheOffset() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL));
        assertThrows(UnsupportedOperationException.class, () -> converter.convert(ddl("ALTER", NEW_TABLE, 100L)));
    }

    @Test
    void rejectsDropRenameAndKeyMutation() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL), Map.of(), true);
        assertThrows(UnsupportedOperationException.class, () -> converter.convert(ddl("DROP", NEW_TABLE, 100L)));
        String changedKey = NEW_TABLE.replace("[\"id\"]", "[\"name\"]");
        assertThrows(UnsupportedOperationException.class, () -> converter.convert(ddl("ALTER", changedKey, 101L)));
    }

    @Test
    void pendingDdlCannotBeCheckpointedAndReleasesRowsOnlyAfterAcknowledgment() {
        CompletableFuture<Long> acknowledgment = new CompletableFuture<>();
        AtomicBoolean progressed = new AtomicBoolean();
        SourceReaderContext context = new SourceReaderContext() {
            @Override
            public Configuration getConfiguration() {
                return new Configuration();
            }

            @Override
            public int getIndexOfSubtask() {
                return 0;
            }

            @Override
            public int currentParallelism() {
                return 1;
            }

            @Override
            public void sendSplitRequest() {}

            @Override
            public CompletionStage<Long> sendSourceSchemaChange(SourceSchemaChange change) {
                return acknowledgment;
            }
        };
        var handler = new SchemaChangeHandler(context);
        Schema before = Schema.from(ORIGINAL, null, Map.of(), Map.of());
        Schema after = new Schema(
                List.of(
                        before.columns().get(0),
                        before.columns().get(1),
                        new io.yak.ops.core.schema.PhysicalColumn("note", LogicalTypes.varchar(128))),
                List.of("id"));
        handler.submitSchemaChange(
                new SourceSchemaChange("position-100", SOURCE, before, after), () -> progressed.set(true));
        assertFalse(handler.tryCompleteSchemaChange());
        assertFalse(handler.getAvailableFuture().isDone());
        assertThrows(IllegalStateException.class, handler::requireNoPendingSchemaChange);
        acknowledgment.complete(1L);
        assertTrue(handler.getAvailableFuture().isDone());
        assertThrows(IllegalStateException.class, handler::requireNoPendingSchemaChange);
        assertFalse(progressed.get());
        assertTrue(handler.tryCompleteSchemaChange());
        assertTrue(progressed.get());
        handler.requireNoPendingSchemaChange();
    }

    private static SourceRecord ddl(String type, String tableJson, long position) {
        Struct tableChange =
                new Struct(TABLE_CHANGE).put("type", type).put("id", "shop.orders").put("table", tableJson);
        Struct ddl = new Struct(DDL)
                .put("ddl", "redacted-structured-source-event")
                .put("tableChanges", List.of(tableChange));
        return new SourceRecord(
                Map.of("server", "yak"),
                Map.of("file", "mysql-bin.000001", "pos", position),
                "yak.shop",
                DDL,
                ddl);
    }

    private static SourceRecord insertWithNote(long position) {
        return insertWithNote(position, true);
    }

    private static SourceRecord insertWithNote(long position, boolean withMetadata) {
        org.apache.kafka.connect.data.Schema rowSchema = SchemaBuilder.struct()
                .field("id", org.apache.kafka.connect.data.Schema.INT64_SCHEMA)
                .field("name", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("note", withMetadata
                        ? SchemaBuilder.string()
                                .optional()
                                .parameter("__debezium.source.column.type", "VARCHAR(128)")
                                .parameter("__debezium.source.column.length", "128")
                                .build()
                        : org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .build();
        org.apache.kafka.connect.data.Schema sourceSchema = SchemaBuilder.struct()
                .field("db", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("table", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .build();
        org.apache.kafka.connect.data.Schema envelopeSchema = SchemaBuilder.struct()
                .field("op", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("source", sourceSchema)
                .field("after", rowSchema)
                .build();
        Struct source = new Struct(sourceSchema).put("db", "shop").put("table", "orders");
        Struct row = new Struct(rowSchema).put("id", 1L).put("name", "order").put("note", "hello");
        Struct envelope = new Struct(envelopeSchema).put("op", "c").put("source", source).put("after", row);
        return new SourceRecord(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", position),
                "yak.shop.orders", envelopeSchema, envelope);
    }
}
