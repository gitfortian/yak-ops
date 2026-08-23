import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.plugin.database.jdbc.GenericJdbcCatalog;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.ConnectionForm;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogReadRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DatasourcePluginContractSmoke {

  public static void main(String[] args) {
    DataSourcePluginDescriptor descriptor =
        new DataSourcePluginDescriptor(
            DataSourceDbType.MYSQL,
            "MySQL",
            DataSourcePluginDescriptor.CURRENT_API_VERSION,
            Set.of(
                DataSourceCapability.CONNECTION_TEST,
                DataSourceCapability.CATALOG_METADATA,
                DataSourceCapability.CATALOG_READ),
            ConnectionForm.empty(),
            false,
            null);
    require(descriptor.supports(DataSourceCapability.CATALOG_READ), "capability missing");
    require("1".equals(descriptor.apiVersion()), "api version mismatch");

    JdbcConnectionProperties connection =
        new JdbcConnectionProperties(
            DataSourceDbType.MYSQL,
            "jdbc:mysql://localhost:3306/demo",
            "test.Driver",
            "test_user",
            null,
            "demo",
            null,
            Map.of(),
            "{}");
    GenericJdbcCatalog catalog = new GenericJdbcCatalog(connection, 5);
    DataSourceCatalogReadRequest request =
        DataSourceCatalogReadRequest.sql(
            "select '${day}' as day_value, ${var:tenant} as tenant_value",
            Map.of("day", "2026-08-23", "tenant", "42"));
    String resolved = catalog.resolveSql(request.query(), request);
    require(
        "select '2026-08-23' as day_value, 42 as tenant_value".equals(resolved),
        "typed catalog variable resolution failed: " + resolved);

    System.out.println("Datasource Plugin Contract Smoke: OK");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
