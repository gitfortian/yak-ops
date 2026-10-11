package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.plugin.task.api.ParameterFormat;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.datasync.source.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.MySqlCdcSourceParameters;
import org.junit.jupiter.api.Test;

/** Ensures user HOCON parses directly into typed parameters, with no external configuration access. */
class SyncHoconContractTest {

    private final TaskPluginRegistry registry = new TaskPluginRegistry();

    @Test
    void exportsAndReadsBothSourceTypesWithoutIntermediateJsonConversions() {
        for (boolean cdc : new boolean[] {false, true}) {
            SyncParameters original =
                    (SyncParameters) registry.parse("DATA_SYNC", hocon(cdc), ParameterFormat.HOCON);
            String json = JSONUtils.toJson(original);
            String exported = registry.format("DATA_SYNC", json, ParameterFormat.HOCON);
            SyncParameters reloaded =
                    (SyncParameters) registry.parse("DATA_SYNC", exported, ParameterFormat.HOCON);
            assertEquals(JSONUtils.readTree(json), JSONUtils.readTree(JSONUtils.toJson(reloaded)));
            assertTrue(exported.contains("datasource_id"));
            if (cdc) {
                assertTrue(reloaded.source() instanceof MySqlCdcSourceParameters);
            } else {
                assertTrue(reloaded.source() instanceof JdbcSourceParameters);
            }
        }
    }

    @Test
    void ignoresCommentsAndFormattingButNotExecutionSemantics() {
        SyncParameters original = (SyncParameters) registry.parse("DATA_SYNC", hocon(false), ParameterFormat.HOCON);
        SyncParameters comments = (SyncParameters) registry.parse(
                "DATA_SYNC", "# comment\n" + hocon(false) + "\n", ParameterFormat.HOCON);
        assertEquals(JSONUtils.toJson(original), JSONUtils.toJson(comments));
        SyncParameters changed = (SyncParameters) registry.parse(
                "DATA_SYNC", hocon(false).replace("orders_copy", "sales"), ParameterFormat.HOCON);
        assertNotEquals(JSONUtils.toJson(original), JSONUtils.toJson(changed));
    }

    @Test
    void forbidsIncludesAndEnvironmentSubstitutions() {
        for (String input : new String[] {
            "include \"local.conf\"\n" + hocon(false),
            "include file(\"/etc/passwd\")\n" + hocon(false),
            "include classpath(\"application.conf\")\n" + hocon(false),
            "include url(\"http://127.0.0.1/config\")\n" + hocon(false),
            hocon(false).replace("orders_copy", "${HOME}")
        }) {
            assertThrows(TaskPluginException.class,
                    () -> registry.parse("DATA_SYNC", input, ParameterFormat.HOCON));
        }
    }

    @Test
    void forbidsUnimplementedStagesAndInlineSecrets() {
        for (String input : new String[] {
            hocon(false).replace("transform {}", "transform { Sql { query = \"select 1\" } }"),
            hocon(false).replace("source {", "source { Kafka { topic = \"x\" }"),
            hocon(false).replace("datasource_id = \"source\"", "password = \"secret\""),
            hocon(false).replace("source {", "env { parallelism = 8 }\nsource {"),
            hocon(false).replace("source {", "source { MySQL-CDC { datasource_id = \"other\" }")
        }) {
            assertThrows(TaskPluginException.class,
                    () -> registry.parse("DATA_SYNC", input, ParameterFormat.HOCON));
        }
    }

    @Test
    void rejectsExcessiveInputSizeAndNesting() {
        assertThrows(TaskPluginException.class,
                () -> registry.parse("DATA_SYNC", " ".repeat(33_000) + hocon(false), ParameterFormat.HOCON));
        String deep = "source { Jdbc { " + "outer { ".repeat(24)
                + "table = orders " + "}".repeat(24) + " } }" + hocon(false);
        assertThrows(TaskPluginException.class,
                () -> registry.parse("DATA_SYNC", deep, ParameterFormat.HOCON));
    }

    private static String hocon(boolean cdc) {
        String kind = "Jdbc";
        String writeMode = "APPEND";
        if (cdc) {
            kind = "MySQL-CDC";
            writeMode = "UPSERT";
        }
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
                """.formatted(kind, writeMode);
    }
}
