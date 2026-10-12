package io.yak.ops.business.datasource.catalog;

import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.catalog.JdbcColumnInfo;
import io.yak.ops.connector.jdbc.database.catalog.JdbcTableInfo;
import io.yak.ops.connector.jdbc.database.catalog.factory.JdbcCatalogFactory;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Executes datasource metadata queries through request-scoped JDBC catalogs.
 *
 * <p>The provider owns connection creation and SSH resources. The catalog is closed after each query.
 */
@Component
public class DataSourceCatalogService {

    public List<String> databases(DataSourcePlugin plugin, DataSourceConnection connection, int timeoutSeconds) {
        return query(plugin, connection, timeoutSeconds, JdbcCatalog::listDatabases);
    }

    public List<String> schemas(
            DataSourcePlugin plugin, DataSourceConnection connection, int timeoutSeconds, String database) {
        return query(plugin, connection, timeoutSeconds, catalog -> catalog.listSchemas(database));
    }

    public List<JdbcTableInfo> tables(
            DataSourcePlugin plugin,
            DataSourceConnection connection,
            int timeoutSeconds,
            String database,
            String schema,
            String keyword,
            Integer limit) {
        return query(
                plugin,
                connection,
                timeoutSeconds,
                catalog -> catalog.listTableInfos(database, schema, keyword, limit));
    }

    public Optional<JdbcTableInfo> table(
            DataSourcePlugin plugin, DataSourceConnection connection, int timeoutSeconds, TableId tableId) {
        return query(plugin, connection, timeoutSeconds, catalog -> catalog.findTable(tableId));
    }

    public List<JdbcColumnInfo> columns(
            DataSourcePlugin plugin, DataSourceConnection connection, int timeoutSeconds, TableId tableId) {
        return query(plugin, connection, timeoutSeconds, catalog -> catalog.getColumns(tableId));
    }

    public TableSchema tableSchema(
            DataSourcePlugin plugin, DataSourceConnection connection, int timeoutSeconds, TableId tableId) {
        return query(plugin, connection, timeoutSeconds, catalog -> catalog.getTable(tableId));
    }

    private <T> T query(
            DataSourcePlugin plugin, DataSourceConnection connection, int timeoutSeconds, CatalogQuery<T> query) {
        try (JdbcCatalog catalog = JdbcCatalogFactory.create(connection.jdbcUrl(), () -> {
            try {
                Connection opened = plugin.openConnection(connection, timeoutSeconds);
                if (opened == null) {
                    throw new SQLException("Datasource provider returned a null JDBC connection");
                }
                return opened;
            } catch (SQLException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new SQLException("Failed to open JDBC metadata connection", exception);
            }
        })) {
            return query.execute(catalog);
        } catch (SQLException | RuntimeException exception) {
            throw new DataSourceException(DataSourceErrorCode.CATALOG_QUERY_FAILED, "读取数据源 Catalog 元数据失败", exception);
        }
    }

    @FunctionalInterface
    private interface CatalogQuery<T> {
        T execute(JdbcCatalog catalog) throws SQLException;
    }
}
