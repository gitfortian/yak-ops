package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.TaskValidationException;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.validation.SchemaValidationMessages;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Public validation errors for a MySQL CDC table with a verified UNIQUE key but no physical PK. */
class DataSyncValidationMessageTest {
    private static final TableSchema SOURCE_SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("order_no", LogicalTypes.varchar(64).copy(false)),
                    new Column("amount", LogicalTypes.decimal(14, 2).copy(false))),
            List.of());

    private final DataSyncTaskPlugin plugin = DataSyncTaskPluginContractTest.plugin();
    private final TaskValidationContext resources = new UniqueKeyResources();

    @Test
    void missingTargetWriteKeyExplainsWhyChunkKeyIsNotEnough() {
        TaskValidationException failure = validateFailure("UPSERT", "");
        assertEquals("SCHEMA_VALIDATION", failure.stage());
        assertEquals(
                "无物理主键的实时 UPSERT 需要目标主键；请选择来源表中具有非空唯一索引的字段",
                failure.publicMessage());
    }

    @Test
    void rejectedUniqueKeyNamesTheActualConstraint() {
        TaskValidationException failure = validateFailure("UPSERT", "\"primaryKeys\":[\"amount\"]");
        assertEquals("目标主键必须与来源表已建立的非空唯一索引或物理主键一致", failure.publicMessage());
    }

    @Test
    void keylessRetractExplainsUnsupportedFullRowFieldAndAlternative() {
        TaskValidationException failure =
                validateFailure("RETRACT", "\"transaction\":{\"mode\":\"CHECKPOINT_XA\"}");
        assertEquals(
                "字段 order_no：逐行更新不支持该字段类型；如有非空唯一索引，请填写目标主键改用按主键更新",
                failure.publicMessage());
    }

    @Test
    void acceptedUniqueKeyCanPrepareATargetPrimaryKeyWithoutChangingSourceSchema() {
        assertDoesNotThrow(() -> plugin.validate(
                plugin.parse(parameters("UPSERT", "\"primaryKeys\":[\"id\"]")), resources));
        assertEquals(List.of(), SOURCE_SCHEMA.primaryKeys());
    }

    @Test
    void unknownDriverErrorsNeverReachClient() {
        assertEquals(
                "目标表结构不兼容，请检查字段类型和默认值等属性",
                SchemaValidationMessages.safeSchemaMessage(
                        new IllegalArgumentException("jdbc:mysql://user:password@secret/?token=private")));
        assertEquals(
                "目标表结构不兼容，请检查字段类型和默认值等属性",
                SchemaValidationMessages.safeSchemaMessage(
                        new IllegalArgumentException("Target primary key cannot be nullable: password=secret")));
    }

    private TaskValidationException validateFailure(String mode, String options) {
        return assertThrows(
                TaskValidationException.class,
                () -> plugin.validate(plugin.parse(parameters(mode, options)), resources));
    }

    private static String parameters(String mode, String options) {
        String extra = options.isEmpty() ? "" : "," + options;
        return """
                {"solutionType":"SINGLE_TABLE_REALTIME","steps":["FULL","INCREMENTAL"],
                 "source":{"datasourceId":"source","tables":[{
                   "id":"orders_unique","database":"cdc_lab","table":"orders_unique",
                   "snapshot":{"chunkKeyColumn":"id"}}]},
                 "transform":[],
                 "sink":{"datasourceId":"target",
                   "schemaPolicy":{"initial":"CREATE_OR_ALTER",
                     "evolution":{"enabled":false,"allowDropColumns":false}},
                   "tables":[{"input":"orders_unique","database":"yak_e2e_target",
                     "table":"ods_orders_unique",
                     "write":{"mode":"%s"%s}}]},
                 "env":{}}
                """.formatted(mode, extra);
    }

    private static final class UniqueKeyResources implements TaskValidationContext {
        @Override
        public DataSourceInfo dataSource(String id) {
            return switch (id) {
                case "source" -> new DataSourceInfo("MYSQL", "cdc_lab", null, 1);
                case "target" -> new DataSourceInfo("MYSQL", "yak_e2e_target", null, 1);
                default -> throw new AssertionError("Unexpected datasource: " + id);
            };
        }

        @Override
        public TableSchema tableSchema(String datasourceId, TableId tableId) {
            if ("source".equals(datasourceId)) return SOURCE_SCHEMA;
            throw new AssertionError("Missing target must not be read as a physical table");
        }

        @Override
        public DataSourceTable tableMetadata(String datasourceId, TableId tableId) {
            if (!"source".equals(datasourceId)) {
                throw new AssertionError("Unexpected target metadata read");
            }
            return new DataSourceTable(
                    "MYSQL",
                    new TableId("cdc_lab", null, "orders_unique"),
                    SOURCE_SCHEMA,
                    1,
                    null,
                    Map.of(),
                    Set.of(),
                    Map.of(),
                    List.of(List.of("id")));
        }

        @Override
        public Optional<DataSourceTable> findTable(String datasourceId, TableId tableId) {
            if (!"target".equals(datasourceId)) {
                throw new AssertionError("Only the target can be absent");
            }
            return Optional.empty();
        }
    }
}
