package io.yak.ops.plugin.database.jdbc.doris;

import com.google.auto.service.AutoService;
import io.yak.ops.plugin.database.jdbc.mysql.MySqlDataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;

/**
 * Doris datasource provider.
 *
 * <p>Doris speaks the MySQL wire protocol and accepts MySQL Connector/J URLs, so the whole
 * connection surface — driver selection, SSL options, URL building — is inherited from the MySQL
 * provider. Only the type name and the default query port (9030) differ; publishing models into
 * Doris is the consumer that needs this type to exist.
 */
@AutoService(DataSourcePlugin.class)
public class DorisDataSourcePlugin extends MySqlDataSourcePlugin {

    @Override
    public String type() {
        return "DORIS";
    }

    @Override
    protected int defaultPort() {
        return 9030;
    }
}
