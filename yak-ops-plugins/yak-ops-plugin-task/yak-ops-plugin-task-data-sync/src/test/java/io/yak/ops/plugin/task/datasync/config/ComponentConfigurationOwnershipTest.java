package io.yak.ops.plugin.task.datasync.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceOptions;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies that the persisted V3 contract is unchanged while each component owns its physical options. */
class ComponentConfigurationOwnershipTest {

    @Test
    void projectsKeylessXaOnlyToSinkWithoutRewritingDefinition() {
        String json = """
                {"solutionType":"SINGLE_TABLE_REALTIME","steps":["FULL","INCREMENTAL"],
                 "schemaChangesEnabled":false,
                 "source":{"datasourceId":"source"},"sink":{"datasourceId":"sink"},
                 "tableSelection":{"mode":"EXPLICIT"},
                 "tableMappings":[{"source":{"table":"orders","keyless":true,"chunkKeyColumn":"bucket","xa":true},
                                   "target":{"table":"orders_copy"},"writeMode":"RETRACT"}]}
                """;
        var persisted = ConfigParser.parseJson(json);
        var job = JobConfig.from(persisted);
        assertEquals(persisted, job.parameters());
        assertTrue(job.source().get(MySqlCdcSourceOptions.KEYLESS_ENABLED));
        assertEquals("bucket", job.source().get(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN));
        assertFalse(job.source().getSourceMap().containsKey("xa"));
        assertFalse(job.sink().getSourceMap().containsKey("primaryKeys"));
        assertTrue(job.sink().get(JdbcSinkOptions.XA_ENABLED));
        assertFalse(persisted.getSourceMap().containsKey(JdbcSinkOptions.XA_ENABLED.key()));
    }

    @Test
    void keepsTargetWriteIdentityOutOfPhysicalSourceAndSinkOptions() {
        String json = """
                {"solutionType":"SINGLE_TABLE_REALTIME","steps":["FULL","INCREMENTAL"],
                 "schemaChangesEnabled":false,
                 "source":{"datasourceId":"source"},"sink":{"datasourceId":"sink"},
                 "tableSelection":{"mode":"EXPLICIT"},
                 "tableMappings":[{"source":{"table":"orders","keyless":true,"chunkKeyColumn":"bucket"},
                                   "target":{"table":"orders_copy","primaryKeys":["bucket"]},"writeMode":"UPSERT"}]}
                """;
        var job = JobConfig.from(ConfigParser.parseJson(json));
        assertEquals(List.of("bucket"), job.tables().getFirst().targetPrimaryKeys());
        assertFalse(job.sink().get(JdbcSinkOptions.XA_ENABLED));
        assertFalse(job.sink().getSourceMap().containsKey("primaryKeys"));
        assertFalse(job.source().getSourceMap().containsKey("keyless"));
        assertEquals("MYSQL_CDC", job.source().get(JobOptions.TYPE));
        assertEquals("JDBC", job.sink().get(JobOptions.TYPE));
    }

    @Test
    void assignsOfflineIncrementalReadSettingsToSourceAndRejectsAppend() {
        String json = """
                {"solutionType":"SINGLE_TABLE_OFFLINE","steps":["INCREMENTAL"],
                 "source":{"datasourceId":"source"},"sink":{"datasourceId":"sink"},
                 "tableSelection":{"mode":"EXPLICIT"},
                 "tableMappings":[{"source":{"table":"orders"},"target":{"table":"orders_copy"},
                                   "writeMode":"UPSERT",
                                   "incremental":{"mode":"TIMESTAMP","column":"updated_at",
                                                  "initialValue":"2026-10-01T00:00:00"}}]}
                """;
        var job = JobConfig.from(ConfigParser.parseJson(json));
        assertEquals("updated_at", job.tables().getFirst().incremental().column());
        assertEquals("UPSERT", job.sink().getSourceMap().get("writeMode"));
        assertThrows(IllegalArgumentException.class, () -> JobConfig.from(ConfigParser.parseJson(
                json.replace("\"writeMode\":\"UPSERT\"", "\"writeMode\":\"APPEND\""))));
    }

    @Test
    void forbidsUserSuppliedEnvironmentOrPhysicalConnectorOverrides() {
        String json = """
                {"solutionType":"SINGLE_TABLE_OFFLINE","steps":["FULL"],
                 "source":{"datasourceId":"source"},"sink":{"datasourceId":"sink"},
                 "tableSelection":{"mode":"EXPLICIT"},
                 "tableMappings":[{"source":{"table":"orders"},"target":{"table":"copy"},"writeMode":"APPEND"}]}
                """;
        assertThrows(RuntimeException.class, () -> JobConfig.from(ConfigParser.parseJson(
                json.replace("\"steps\":[\"FULL\"]", "\"steps\":[\"FULL\"],\"env\":{\"execution.debug\":true}"))));
        assertThrows(RuntimeException.class, () -> JobConfig.from(ConfigParser.parseJson(
                json.replace("\"table\":\"orders\"", "\"table\":\"orders\",\"connector.mysql-cdc.server-id\":12"))));
        assertThrows(RuntimeException.class, () -> JobConfig.from(ConfigParser.parseJson(
                json.replace("\"table\":\"copy\"", "\"table\":\"copy\",\"connector.jdbc.sink.xa-enabled\":true"))));
    }
}
