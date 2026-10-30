package io.yak.ops.plugin.database.jdbc.doris;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import org.junit.jupiter.api.Test;

class DorisDataSourcePluginTest {

    @Test
    void reusesTheMysqlWireProtocolWithTheDorisQueryPort() {
        var plugin = new DorisDataSourcePlugin();
        assertEquals("DORIS", plugin.type());
        assertEquals(9030, plugin.defaultPort());
        DataSourceConnection connection = plugin.parseConnection(ConfigParser.parseJson(
                """
                {
                  "host": "127.0.0.1",
                  "port": 9030,
                  "database": "dwd",
                  "username": "root"
                }
                """));
        assertEquals("jdbc:mysql://127.0.0.1:9030/dwd", plugin.displayJdbcUrl(connection));
    }
}
