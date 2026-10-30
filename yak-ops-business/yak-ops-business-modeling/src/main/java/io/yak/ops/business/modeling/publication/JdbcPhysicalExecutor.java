package io.yak.ops.business.modeling.publication;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import jakarta.annotation.Resource;
import java.sql.SQLException;
import java.util.ArrayList;
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
    public List<StatementResult> execute(String workspaceId, String datasourceId, List<String> statements) {
        var binding = new JdbcConnectionOptions(resources.connectionOptions(workspaceId, datasourceId));
        var connections = new DefaultJdbcConnectionProvider(binding);
        var completed = new ArrayList<StatementResult>(statements.size());
        try (var connection = connections.getConnection()) {
            connection.setAutoCommit(true);
            for (var sql : statements) {
                long started = System.nanoTime();
                try (var statement = connection.createStatement()) {
                    statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
                    statement.execute(sql);
                } catch (SQLException failure) {
                    throw new PhysicalExecutionException(failure, List.copyOf(completed), sql);
                }
                completed.add(new StatementResult(sql, (System.nanoTime() - started) / 1_000_000L));
            }
        } catch (SQLException exception) {
            // Connection-level failure: everything already recorded stays, nothing else executed.
            throw new PhysicalExecutionException(exception, List.copyOf(completed), null);
        }
        return List.copyOf(completed);
    }
}
