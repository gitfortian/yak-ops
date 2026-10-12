package io.yak.ops.business.datasource.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasource.DataSourceCatalogService;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.bean.dto.datasource.DataSourceCatalogQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.core.data.TableId;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginDescriptor;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginRegistry;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Verifies that Datasource delegates its JDBC metadata queries to one Connector Catalog. */
class DataSourceCatalogServiceImplTest {

    @Test
    void injectedProviderOwnsEveryPhysicalAndLogicalMetadataConnection() throws Exception {
        String url = "jdbc:h2:mem:datasource_unified_catalog;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement ddl = connection.createStatement()) {
            ddl.execute("CREATE SCHEMA APP");
            ddl.execute("CREATE TABLE APP.PRODUCTS (TENANT INT, ID BIGINT, NAME VARCHAR(64),"
                    + " PRIMARY KEY(ID, TENANT))");
        }

        AtomicInteger connectionOpens = new AtomicInteger();
        DataSourceConnection settings = new DataSourceConnection() {
            @Override
            public String type() {
                return "TEST_H2";
            }

            @Override
            public String jdbcUrl() {
                return url;
            }

            @Override
            public String driverClassName() {
                return "org.h2.Driver";
            }

            @Override
            public String username() {
                return "sa";
            }

            @Override
            public String password() {
                return "";
            }

            @Override
            public String database() {
                return null;
            }

            @Override
            public String schema() {
                return "APP";
            }

            @Override
            public Map<String, String> properties() {
                return Map.of();
            }

            @Override
            public String normalizedJson() {
                return "{}";
            }
        };
        DataSourcePlugin plugin = new DataSourcePlugin() {
            @Override
            public String type() {
                return "TEST_H2";
            }

            @Override
            public DataSourcePluginDescriptor descriptor() {
                return new DataSourcePluginDescriptor(
                        "TEST_H2", Set.of(), DataSourcePluginDescriptor.CURRENT_API_VERSION, Set.of(), Set.of());
            }

            @Override
            public DataSourceConnection parseConnection(String connectionJson) {
                return settings;
            }

            @Override
            public void testConnection(DataSourceConnection value, int timeoutSeconds) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Connection openConnection(DataSourceConnection value, int timeoutSeconds) throws Exception {
                assertEquals(settings, value);
                connectionOpens.incrementAndGet();
                return DriverManager.getConnection(url, "sa", "");
            }
        };
        DataSourceEntity datasource = new DataSourceEntity();
        datasource.setId("h2-source");
        datasource.setWorkspaceId("workspace-1");
        datasource.setDbType("TEST_H2");
        datasource.setConnectionParams("{}");

        DataSourceEntityRepository repository = (DataSourceEntityRepository) Proxy.newProxyInstance(
                DataSourceEntityRepository.class.getClassLoader(),
                new Class<?>[] {DataSourceEntityRepository.class},
                (ignored, method, arguments) -> {
                    if ("queryById".equals(method.getName()) && arguments != null && arguments.length == 2) {
                        if ("workspace-1".equals(arguments[0]) && "h2-source".equals(arguments[1])) {
                            return Optional.of(datasource);
                        }
                        return Optional.empty();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DataSourceCatalogServiceImpl.class);
            context.registerBean(DataSourceEntityRepository.class, () -> repository);
            context.registerBean(DataSourcePluginRegistry.class, () -> new DataSourcePluginRegistry(List.of(plugin)));
            context.registerBean(DataSourceProperties.class);
            context.refresh();

            DataSourceCatalogService catalog = context.getBean(DataSourceCatalogService.class);
            WorkspaceContext.bind("workspace-1");
            try {
                DataSourceCatalogQueryDTO query = new DataSourceCatalogQueryDTO();
                query.setSchema("APP");
                query.setKeyword("prod");
                query.setLimit(10);
                assertEquals("PRODUCTS", catalog.listTables("h2-source", query).getFirst().getName());

                DataSourceTablePathDTO path = new DataSourceTablePathDTO();
                path.setSchema("APP");
                path.setTable("PRODUCTS");
                var columns = catalog.listColumns("h2-source", path);
                assertEquals(3, columns.size());
                assertEquals(2, columns.getFirst().getPrimaryKeyPosition());
                assertEquals(1, columns.get(1).getPrimaryKeyPosition());
                assertFalse(columns.get(2).getPrimaryKey());

                TableId table = new TableId(null, "APP", "PRODUCTS");
                var schema = catalog.getTableSchema("h2-source", table);
                assertEquals(3, schema.columnCount());
                assertEquals(List.of("ID", "TENANT"), schema.primaryKeys());
                assertTrue(connectionOpens.get() >= 3, "Catalog queries must use provider connections");

                WorkspaceContext.bind("another-workspace");
                DataSourceException exception = assertThrows(
                        DataSourceException.class, () -> catalog.getTableSchema("h2-source", table));
                assertEquals(DataSourceErrorCode.NOT_FOUND, exception.getErrorCode());
            } finally {
                WorkspaceContext.clear();
            }
        }
    }
}
