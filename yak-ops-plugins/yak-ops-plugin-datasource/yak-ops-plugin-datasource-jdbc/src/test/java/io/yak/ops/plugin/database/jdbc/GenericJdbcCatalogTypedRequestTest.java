package io.yak.ops.plugin.database.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogReadRequest;
import io.yak.ops.spi.datasource.query.DataSourceQueryResult;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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

  @Test
  void previewDoesNotCountAndAppliesQueryTimeout() throws Exception {
    Connection opened = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    ResultSetMetaData metadata = mock(ResultSetMetaData.class);
    AtomicInteger countCalls = new AtomicInteger();

    when(opened.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.getMetaData()).thenReturn(metadata);
    when(metadata.getColumnCount()).thenReturn(1);
    when(metadata.getColumnLabel(1)).thenReturn("id");
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getObject(1)).thenReturn(7L);

    GenericJdbcCatalog catalog =
        new GenericJdbcCatalog(connection(), 5) {
          @Override
          protected Connection openConnection() {
            return opened;
          }

          @Override
          public long count(DataSourceCatalogReadRequest request) {
            countCalls.incrementAndGet();
            return 99L;
          }
        };

    DataSourceQueryResult result =
        catalog.preview(DataSourceCatalogReadRequest.table("orders", Map.of()), 20);

    assertThat(result.getData()).hasSize(1);
    assertThat(result.getTotal()).isEqualTo(1L);
    assertThat(countCalls.get()).isZero();
    verify(statement).setQueryTimeout(5);
    verify(statement).setMaxRows(20);
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
