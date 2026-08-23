package io.yak.ops.plugin.database.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogReadRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenericJdbcCatalogTypedRequestTest {

  @Test
  void resolveSqlUsesTypedVariablesWithoutLegacyMapKeys() {
    GenericJdbcCatalog catalog = new GenericJdbcCatalog(connection(), 5);
    DataSourceCatalogReadRequest request =
        DataSourceCatalogReadRequest.sql(
            "select '${day}' as day_value, ${var:tenant} as tenant_value",
            Map.of("day", "2026-08-23", "tenant", "42"));

    String resolved = catalog.resolveSql(request.query(), request);

    assertThat(resolved)
        .isEqualTo("select '2026-08-23' as day_value, 42 as tenant_value");
  }

  @Test
  void typedRequestRejectsMissingModePayload() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new DataSourceCatalogReadRequest(
                DataSourceCatalogReadRequest.Mode.SQL, null, null, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("query");
  }

  private JdbcConnectionProperties connection() {
    return new JdbcConnectionProperties(
        DataSourceDbType.MYSQL,
        "jdbc:mysql://localhost:3306/demo",
        "com.mysql.cj.jdbc.Driver",
        "test_user",
        null,
        "demo",
        null,
        Map.of(),
        "{}");
  }
}
