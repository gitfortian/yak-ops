package io.yak.ops.business.datasource.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.common.PageData;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DataSourceQuery;
import io.yak.ops.business.datasource.gateway.DataSourcePluginGateway;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.business.datasource.service.support.DataSourceViewMapper;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSourceServiceImplTest {

  @Mock private DataSourceRepository repository;
  @Mock private DataSourcePluginGateway pluginGateway;
  @Mock private DataSourceProperties properties;
  @Mock private DataSourceViewMapper viewMapper;

  @Test
  void pageConvertsHttpQueryToDomainWithoutMutatingRequest() {
    DataSourceQueryDTO request = new DataSourceQueryDTO();
    request.setPageNo(2);
    request.setPageSize(50);
    request.setName("  orders-db  ");
    request.setKeyword("  jdbc:mysql  ");
    request.setDbType(" mysql ");
    request.setEnvironment(" production ");
    request.setConnStatus(" connected ");

    when(repository.page(any(DataSourceQuery.class)))
        .thenReturn(new PageData<>(java.util.List.of(), 0L, 0L, 2L, 50L));

    service().getDataSourcePage(request);

    ArgumentCaptor<DataSourceQuery> captor = ArgumentCaptor.forClass(DataSourceQuery.class);
    verify(repository).page(captor.capture());
    DataSourceQuery query = captor.getValue();

    assertThat(query.pageNo()).isEqualTo(2);
    assertThat(query.pageSize()).isEqualTo(50);
    assertThat(query.name()).isEqualTo("orders-db");
    assertThat(query.keyword()).isEqualTo("jdbc:mysql");
    assertThat(query.dbType()).isEqualTo(DataSourceDbType.MYSQL);
    assertThat(query.environment()).isEqualTo(DataSourceEnvironment.PROD);
    assertThat(query.connStatus()).isEqualTo(DataSourceConnStatus.CONNECTED);

    assertThat(request.getName()).isEqualTo("  orders-db  ");
    assertThat(request.getDbType()).isEqualTo(" mysql ");
    assertThat(request.getEnvironment()).isEqualTo(" production ");
  }

  private DataSourceServiceImpl service() {
    return new DataSourceServiceImpl(
        repository,
        pluginGateway,
        properties,
        viewMapper);
  }
}
