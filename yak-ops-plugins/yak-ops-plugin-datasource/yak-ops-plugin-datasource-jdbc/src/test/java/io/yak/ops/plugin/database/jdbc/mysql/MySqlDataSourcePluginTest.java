package io.yak.ops.plugin.database.jdbc.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MySqlDataSourcePluginTest {

    @Test
    void discoversAllJdbcDatasourcePluginsThroughServiceLoader() {
        Set<String> types = new HashSet<>();
        for (DataSourcePlugin plugin : ServiceLoader.load(DataSourcePlugin.class)) {
            types.add(plugin.type());
        }
        assertEquals(Set.of("MYSQL", "ORACLE", "POSTGRE_SQL"), types);
    }

    @Test
    void shouldIncludeAdvancedPropertiesInDisplayJdbcUrl() {
        MySqlDataSourcePlugin plugin = new MySqlDataSourcePlugin();
        DataSourceConnection connection = plugin.parseConnection(ConfigParser.parseJson(
                """
                {
                  "host": "127.0.0.1",
                  "port": 3306,
                  "database": "yak_e2e_realtime_source",
                  "username": "root",
                  "properties": {
                    "connectionTimeZone": "Asia/Shanghai",
                    "useSSL": "false"
                  }
                }
                """));

        assertEquals(
                "jdbc:mysql://127.0.0.1:3306/yak_e2e_realtime_source?connectionTimeZone=Asia%2FShanghai&useSSL=false",
                plugin.displayJdbcUrl(connection));
    }
}
