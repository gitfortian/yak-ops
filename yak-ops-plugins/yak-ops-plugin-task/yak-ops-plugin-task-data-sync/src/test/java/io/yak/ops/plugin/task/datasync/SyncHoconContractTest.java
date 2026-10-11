package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import org.junit.jupiter.api.Test;

/** Contract tests for restricted in-memory HOCON input and canonical JSON output. */
class SyncHoconContractTest {
    private final TaskPluginRegistry registry = new TaskPluginRegistry();

    @Test
    void hoconJsonAndReformattedHoconHaveTheSamePipelineMeaning() {
        for (boolean cdc : new boolean[] {false, true}) {
            SyncParameters first = (SyncParameters) registry.parseParameters("DATA_SYNC", hocon(cdc), "HOCON");
            String exported = registry.get("DATA_SYNC").formatParameters(first, "HOCON");
            SyncParameters second = (SyncParameters) registry.parseParameters("DATA_SYNC", exported, "HOCON");
            assertEquals(JSONUtils.readTree(JSONUtils.toJson(first)), JSONUtils.readTree(JSONUtils.toJson(second)));
            assertEquals(cdc ? DataSyncType.REALTIME : DataSyncType.OFFLINE, second.syncType());
            assertTrue(exported.contains("datasource_id"));
        }
    }

    @Test
    void ignoresFormattingAndCommentsButNotExecutableValues() {
        var initial = registry.parseParameters("DATA_SYNC", hocon(false), "HOCON");
        var comments = registry.parseParameters("DATA_SYNC", "# only an explanation\n" + hocon(false) + "\n", "HOCON");
        assertEquals(JSONUtils.toJson(initial), JSONUtils.toJson(comments));
        var changed = registry.parseParameters("DATA_SYNC", hocon(false).replace("orders_copy", "sales"), "HOCON");
        org.junit.jupiter.api.Assertions.assertNotEquals(JSONUtils.toJson(initial), JSONUtils.toJson(changed));
    }

    @Test
    void forbidsExternalIncludesAndEnvironmentSubstitutions() {
        for (String input : new String[] {
            "include \"local.conf\"\n" + hocon(false),
            "include file(\"/etc/passwd\")\n" + hocon(false),
            "include classpath(\"application.conf\")\n" + hocon(false),
            "include url(\"http://127.0.0.1/config\")\n" + hocon(false),
            hocon(false).replace("orders_copy", "${HOME}")
        }) {
            assertThrows(TaskPluginException.class,
                    () -> registry.parseParameters("DATA_SYNC", input, "HOCON"));
        }
    }

    @Test
    void rejectsExtraStagesUnsupportedOptionsAndNonemptyTransforms() {
        for (String input : new String[] {
            hocon(false).replace("transform {}", "transform { Sql { query = \"select 1\" } }"),
            hocon(false).replace("source {", "source { Kafka { topic = \"x\" }"),
            hocon(false).replace("datasource_id = \"source\"", "password = \"secret\""),
            hocon(false).replace("source {", "env { parallelism = 8 }\nsource {"),
            hocon(false).replace("source {", "source { MySQL-CDC { datasource_id = \"other\" }")
        }) {
            assertThrows(TaskPluginException.class,
                    () -> registry.parseParameters("DATA_SYNC", input, "HOCON"));
        }
    }

    @Test
    void rejectsLargeAndUnreasonablyNestedConfiguration() {
        assertThrows(TaskPluginException.class,
                () -> registry.parseParameters("DATA_SYNC", " ".repeat(33_000) + hocon(false), "HOCON"));
        String deep = "source { Jdbc { " + "outer { ".repeat(24)
                + "table = orders " + "}".repeat(24) + " } }" + hocon(false);
        assertThrows(TaskPluginException.class,
                () -> registry.parseParameters("DATA_SYNC", deep, "HOCON"));
    }

    private static String hocon(boolean cdc) {
        return """
                source {
                  %s {
                    datasource_id = "source"
                    table = "orders"
                  }
                }
                transform {}
                sink {
                  Jdbc {
                    datasource_id = "target"
                    table = "orders_copy"
                    write_mode = "%s"
                  }
                }
                """.formatted(cdc ? "MySQL-CDC" : "Jdbc", cdc ? "UPSERT" : "APPEND");
    }
}
