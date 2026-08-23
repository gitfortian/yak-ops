package io.yak.ops.business.datasource.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataSourceCatalogServiceImplTest {

  private final DataSourceRepository repository = mock(DataSourceRepository.class);
  private final DataSourceCatalogGateway catalogGateway = mock(DataSourceCatalogGateway.class);
  private final DataSourceProperties properties = mock(DataSourceProperties.class);
  private final DataSourceCatalogServiceImpl service =
      new DataSourceCatalogServiceImpl(repository, catalogGateway, properties);

  @Test
  void shouldRejectNonSelectSqlBeforeOpeningDatasourceConnection() {
    assertThatThrownBy(
            () ->
                service.preview(
                    1L,
                    Map.of(
                        "read_mode", "sql",
                        "query", "DELETE FROM patient")))
        .isInstanceOf(DataSourceException.class)
        .hasMessageContaining("仅允许执行单条 SELECT 查询");

    verifyNoInteractions(repository, catalogGateway, properties);
  }

  @Test
  void shouldRejectMultipleStatements() {
    assertThatThrownBy(
            () ->
                service.count(
                    1L,
                    Map.of(
                        "read_mode", "sql",
                        "query", "SELECT * FROM patient; DROP TABLE patient")))
        .isInstanceOf(DataSourceException.class)
        .hasMessageContaining("仅允许执行单条 SELECT 查询");
  }
}
