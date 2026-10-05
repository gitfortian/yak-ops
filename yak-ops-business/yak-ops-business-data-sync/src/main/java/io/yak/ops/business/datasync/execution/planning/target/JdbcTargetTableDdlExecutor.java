package io.yak.ops.business.datasync.execution.planning.target;

import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.jdbc.JdbcTargetTableProvisioner;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import org.springframework.stereotype.Component;

/**
 * 使用 YakFlow JDBC Connector 受控 DDL 执行器创建目标表。
 *
 * @author weifuwan
 * @since 2026-10-04
 */
@Component
public class JdbcTargetTableDdlExecutor implements TargetTableDdlExecutor {

    private final JdbcTargetTableProvisioner provisioner = new JdbcTargetTableProvisioner();

    @Override
    public String createTable(
            DataSourceConnection connection, DataSourceTablePath table, YakTableSchema schema, int timeoutSeconds)
            throws Exception {
        return provisioner.createTable(connection, table, schema, timeoutSeconds);
    }
}
