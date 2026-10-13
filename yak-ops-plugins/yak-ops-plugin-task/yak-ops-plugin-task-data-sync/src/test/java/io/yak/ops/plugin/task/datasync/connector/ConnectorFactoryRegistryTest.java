package io.yak.ops.plugin.task.datasync.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.datasync.config.DefinitionParser;
import io.yak.ops.plugin.task.datasync.connector.cdc.MySqlCdcSourceFactory;
import io.yak.ops.plugin.task.datasync.connector.jdbc.JdbcSinkFactory;
import io.yak.ops.plugin.task.datasync.connector.jdbc.JdbcSourceFactory;
import io.yak.ops.plugin.task.datasync.source.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies SPI discovery and saved JSON type resolution without changing the central parser. */
class ConnectorFactoryRegistryTest {

    @Test
    void discoversBuiltInFactoriesThroughJavaSpi() {
        ConnectorFactoryRegistry factories = new ConnectorFactoryRegistry();

        assertInstanceOf(JdbcSourceFactory.class, factories.source("JDBC"));
        assertInstanceOf(MySqlCdcSourceFactory.class, factories.source("MYSQL_CDC"));
        assertInstanceOf(JdbcSinkFactory.class, factories.sink("JDBC"));
        assertThrows(TaskPluginException.class, () -> factories.source("UNKNOWN"));
        assertThrows(TaskPluginException.class, () -> factories.sink("UNKNOWN"));
    }

    @Test
    void resolvesAnAdditionalSourceFromRegisteredParametersClass() {
        ConnectorFactoryRegistry factories = new ConnectorFactoryRegistry(
                List.of(new JdbcSourceFactory(), new ExternalSourceFactory()),
                List.of(new JdbcSinkFactory()));
        var parameters = new DefinitionParser(factories).parse("""
                {"source":{"type":"EXTERNAL","datasourceId":"a","table":"orders"},
                 "transform":[],
                 "sink":{"type":"JDBC","datasourceId":"b","table":"copy","writeMode":"APPEND"}}
                """);

        assertInstanceOf(ExternalSourceParameters.class, parameters.source());
        assertEquals("EXTERNAL", factories.source(parameters.source()).identifier());
        assertEquals("EXTERNAL", JSONUtils.readTree(JSONUtils.toJson(parameters))
                .path("source").path("type").asText());
    }

    @Test
    void rejectsDuplicateIdentifiersAndParameterTypes() {
        assertThrows(
                IllegalStateException.class,
                () -> new ConnectorFactoryRegistry(
                        List.of(new JdbcSourceFactory(), new JdbcSourceFactory()),
                        List.of(new JdbcSinkFactory())));
        assertThrows(
                IllegalStateException.class,
                () -> new ConnectorFactoryRegistry(
                        List.of(new JdbcSourceFactory(), new ConflictingSourceFactory()),
                        List.of(new JdbcSinkFactory())));
    }

    @Test
    void doesNotRegisterConcreteFactoriesInParameterInterfaces() {
        ConnectorFactoryRegistry factories = new ConnectorFactoryRegistry();
        assertTrue(factories.source("MYSQL_CDC").producesChangelog());
        assertTrue(!factories.source("JDBC").producesChangelog());
    }

    public record ExternalSourceParameters(String datasourceId, String database, String schema, String table)
            implements SourceParameters {

        @Override
        @JsonProperty("type")
        public String type() {
            return "EXTERNAL";
        }
    }

    private static class ExternalSourceFactory implements TableSourceFactory {
        @Override
        public String identifier() {
            return "EXTERNAL";
        }

        @Override
        public Class<? extends SourceParameters> parametersType() {
            return ExternalSourceParameters.class;
        }

        @Override
        public Boundedness boundedness() {
            return Boundedness.BOUNDED;
        }

        @Override
        public boolean producesChangelog() {
            return false;
        }

        @Override
        public Source<TableRecord, ?, ?> createSource(SourceParameters parameters, SourceFactoryContext context) {
            throw new UnsupportedOperationException("External test Source cannot execute");
        }
    }

    private static final class ConflictingSourceFactory extends ExternalSourceFactory {
        @Override
        public String identifier() {
            return "CONFLICT";
        }

        @Override
        public Class<? extends SourceParameters> parametersType() {
            return JdbcSourceParameters.class;
        }
    }
}
