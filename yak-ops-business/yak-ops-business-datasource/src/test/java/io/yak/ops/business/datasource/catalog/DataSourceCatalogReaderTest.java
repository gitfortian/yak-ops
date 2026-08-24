package io.yak.ops.business.datasource.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.ReadMode;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataSourceCatalogReaderTest {

  @Test
  void policyRejectsUnsafeReadBeforeDatasourceLookupOrGatewayCall() {
    DataSourceReader dataSourceReader = mock(DataSourceReader.class);
    DataSourceCatalogGateway gateway = mock(DataSourceCatalogGateway.class);
    DataSourceProperties properties = mock(DataSourceProperties.class);
    CatalogReadPolicy policy = mock(CatalogReadPolicy.class);
    CatalogTableMatcher matcher = mock(CatalogTableMatcher.class);
    DataSourceCatalogReader reader =
        new DataSourceCatalogReader(
            dataSourceReader,
            gateway,
            properties,
            policy,
            matcher);
    CatalogReadRequest request =
        new CatalogReadRequest(ReadMode.SQL, null, "DELETE FROM patient", List.of());
    doThrow(
            new DataSourceException(
                DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
                "数据预览仅允许执行单条 SELECT 查询"))
        .when(policy)
        .validateReadOnly(request);

    assertThatThrownBy(() -> reader.preview(1L, request))
        .isInstanceOf(DataSourceException.class);

    verifyNoInteractions(dataSourceReader, gateway, properties, matcher);
  }
}
