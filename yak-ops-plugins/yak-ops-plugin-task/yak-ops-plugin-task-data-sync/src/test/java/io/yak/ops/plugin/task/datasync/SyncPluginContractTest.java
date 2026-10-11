package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskPluginFactory;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.TaskValidationContext;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests plugin registration, canonical JSON, source boundedness and metadata-only validation. */
class SyncPluginContractTest {
    private final TaskPluginRegistry registry = new TaskPluginRegistry();

    @Test
    void discoversOnlyTheDeclaredTaskType() {
        assertTrue(registry.types().contains("DATA_SYNC"));
        assertEquals("DATA_SYNC", registry.get("data-sync").type());
        assertEquals(2, registry.get("DATA_SYNC").parameterSchemaVersion());
    }

    @Test
    void readsBoundedAndContinuousPipelineDefinitions() {
        SyncParameters jdbc = assertInstanceOf(
                SyncParameters.class, registry.parseParameters("DATA_SYNC", validJson(false)));
        SyncParameters cdc = assertInstanceOf(
                SyncParameters.class, registry.parseParameters("DATA_SYNC", validJson(true)));
        assertEquals(DataSyncType.OFFLINE, jdbc.syncType());
        assertEquals(DataSyncType.REALTIME, cdc.syncType());
        assertEquals("JDBC", JSONUtils.readTree(JSONUtils.toJson(jdbc)).path("source").path("type").asText());
        assertEquals("MYSQL_CDC", JSONUtils.readTree(JSONUtils.toJson(cdc)).path("source").path("type").asText());
    }

    @Test
    void rejectsUnsupportedAndObsoleteConfiguration() {
        assertThrows(TaskPluginException.class, () -> registry.parseParameters("DATA_SYNC", "{}"));
        assertThrows(TaskPluginException.class, () -> registry.parseParameters("DATA_SYNC", "[ ]"));
        assertThrows(TaskPluginException.class, () -> registry.parseParameters("DATA_SYNC", "{"));
        assertThrows(TaskPluginException.class, () -> registry.parseParameters(
                "DATA_SYNC", validJson(false).replace("JDBC", "KAFKA")));
        assertThrows(TaskPluginException.class, () -> registry.parseParameters(
                "DATA_SYNC", validJson(false).replace("\"transform\": []", "\"transform\":[{}]")));
        assertThrows(TaskPluginException.class, () -> registry.parseParameters(
                "DATA_SYNC", validJson(false).replace("\"transform\": []", "\"runtimeConfig\":{}")));
        assertThrows(TaskPluginException.class, () -> registry.parseParameters(
                "DATA_SYNC", validJson(true).replace("UPSERT", "APPEND")));
        assertThrows(TaskPluginException.class, () -> registry.parseParameters(
                "DATA_SYNC", validJson(false).replace("APPEND", "OVERWRITE")));
    }

    @Test
    void rejectsUnknownSecretsRatherThanEchoingInput() {
        String json = validJson(false).replace("\"table\":\"orders\"",
                "\"table\":\"orders\",\"password\":\"sensitive-value\"");
        TaskPluginException error = assertThrows(
                TaskPluginException.class, () -> registry.parseParameters("DATA_SYNC", json));
        assertEquals("Invalid DATA_SYNC pipeline JSON", error.getMessage());
    }

    @Test
    void validatesSchemasAndNormalizesAuthorizedDatasourceDefaults() {
        var p = assertInstanceOf(SyncParameters.class, registry.validateDefinition(
                "DATA_SYNC", validJson(true), new Metadata(), "JSON"));
        assertEquals(DataSyncType.REALTIME, p.syncType());
        assertEquals("app", p.source().database());
        assertEquals("ods", p.sink().schema());
        TaskPluginException failure = assertThrows(TaskPluginException.class, () ->
                registry.validateDefinition("DATA_SYNC", validJson(true), new Metadata() {
                    @Override
                    public TableSchema tableSchema(String id, String db, String schema, String table) {
                        return id.equals("source") ? super.tableSchema(id, db, schema, table)
                                : new TableSchema(List.of(new Column("other", LogicalTypes.INTEGER)), List.of("other"));
                    }
                }, "JSON"));
        assertEquals("Source and target table schemas are incompatible", failure.getMessage());
    }

    @Test
    void rejectsUnknownAndDuplicateFactories() {
        assertThrows(TaskPluginException.class, () -> registry.get("SQL"));
        assertThrows(TaskPluginException.class, () -> new TaskPluginRegistry(
                List.of(new SyncPluginFactory(), new SyncPluginFactory())));
        assertThrows(TaskPluginException.class, () -> new TaskPluginRegistry(List.of(new TaskPluginFactory() {
            @Override
            public String type() {
                return "SQL";
            }

            @Override
            public TaskPlugin create() {
                return new SyncPlugin();
            }
        })));
    }

    static String validJson(boolean cdc) {
        return """
                {
                  "source": {"type":"%s","datasourceId":"source","table":"orders"},
                  "transform": [],
                  "sink": {"type":"JDBC","datasourceId":"target","table":"orders_copy","writeMode":"%s"}
                }
                """.formatted(cdc ? "MYSQL_CDC" : "JDBC", cdc ? "UPSERT" : "APPEND");
    }

    static class Metadata implements TaskValidationContext {
        @Override
        public DataSourceInfo dataSource(String id) {
            return id.equals("source")
                    ? new DataSourceInfo("MYSQL", "app", null)
                    : new DataSourceInfo("POSTGRESQL", null, "ods");
        }

        @Override
        public TableSchema tableSchema(String id, String database, String schema, String table) {
            return new TableSchema(
                    List.of(new Column("id", LogicalTypes.BIGINT),
                            new Column("name", LogicalTypes.STRING)),
                    List.of("id"));
        }
    }
}
