package io.yak.ops.business.modeling.publication;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import jakarta.annotation.Resource;
import java.sql.SQLException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Executes DDL through the saved datasource connection.
 *
 * <p>This is the lightweight path section 5 of the proposal selects: the data-sync metadata applier
 * is built around schema-evolution events and would drag the connector runtime into modeling, while
 * all this release needs is an ordered list of statements on a caller-owned connection. Connection
 * settings come from the datasource boundary, so credentials never enter this module.
 */
@Component
public class JdbcPhysicalExecutor implements PhysicalExecutor {

    /** Same default the data-sync metadata path applies to one DDL statement. */
    public static final int STATEMENT_TIMEOUT_SECONDS = 30;

    @Resource
    private DataSourceLookupService resources;

    @Override
    public void execute(String workspaceId, String datasourceId, List<String> statements) {
        var binding = new JdbcConnectionOptions(resources.connectionOptions(workspaceId, datasourceId));
        var connections = new DefaultJdbcConnectionProvider(binding);
        try (var connection = connections.getConnection()) {
            connection.setAutoCommit(true);
            for (var sql : statements) {
                try (var statement = connection.createStatement()) {
                    statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
                    statement.execute(sql);
                }
            }
        } catch (SQLException exception) {
            throw new PhysicalExecutionException(exception);
        }
    }
}
