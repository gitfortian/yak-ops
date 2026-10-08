package io.yak.ops.plugin.database.jdbc.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import org.junit.jupiter.api.Test;

class MySqlDataSourcePluginTest {

    @Test
    void shouldIncludeAdvancedPropertiesInDisplayJdbcUrl() {
        MySqlDataSourcePlugin plugin = new MySqlDataSourcePlugin();
        DataSourceConnection connection = plugin.parseConnection(
                """
                {
                  "host": "127.0.0.1",
                  "port": 3306,
                  "database": "yak_e2e_realtime_source",
                  "username": "root",
                  "properties": {
                    "serverTimezone": "Asia/Shanghai",
                    "useSSL": "false"
                  }
                }
                """);

        assertEquals(
                "jdbc:mysql://127.0.0.1:3306/yak_e2e_realtime_source?serverTimezone=Asia%2FShanghai&useSSL=false",
                plugin.displayJdbcUrl(connection));
    }
}
