package io.yak.ops.business.datasource.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.catalog.CatalogQueryResult;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.ReadMode;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
                    Map.of("read_mode", "sql", "query", "DELETE FROM patient")))
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
                        "read_mode",
                        "sql",
                        "query",
                        "SELECT * FROM patient; DROP TABLE patient")))
        .isInstanceOf(DataSourceException.class)
        .hasMessageContaining("仅允许执行单条 SELECT 查询");
  }

  @Test
  void legacyHttpMapIsParsedOnceIntoTypedCatalogRequest() {
    DataSourceRepository typedRepository = mock(DataSourceRepository.class);
    DataSourceCatalogGateway typedGateway = mock(DataSourceCatalogGateway.class);
    DataSourceProperties typedProperties = new DataSourceProperties();
    DataSourceCatalogServiceImpl typedService =
        new DataSourceCatalogServiceImpl(typedRepository, typedGateway, typedProperties);
    DataSourceDefinition dataSource = new DataSourceDefinition();
    when(typedRepository.findById(1L)).thenReturn(Optional.of(dataSource));
    when(typedGateway.preview(eq(dataSource), any(CatalogReadRequest.class), eq(20), eq(5)))
        .thenReturn(new CatalogQueryResult(List.of(), List.of(), 0L));

    typedService.preview(
        1L,
        Map.of(
            "readMode",
            "sql",
            "sql",
            "SELECT * FROM patient WHERE day = ${day}",
            "paramsList",
            List.of(Map.of("paramName", "day", "paramValue", "2026-08-23"))));

    ArgumentCaptor<CatalogReadRequest> captor = ArgumentCaptor.forClass(CatalogReadRequest.class);
    verify(typedGateway).preview(eq(dataSource), captor.capture(), eq(20), eq(5));
    CatalogReadRequest request = captor.getValue();
    assertThat(request.mode()).isEqualTo(ReadMode.SQL);
    assertThat(request.sql()).isEqualTo("SELECT * FROM patient WHERE day = ${day}");
    assertThat(request.variables()).hasSize(1);
    assertThat(request.variables().getFirst().name()).isEqualTo("day");
  }
}
