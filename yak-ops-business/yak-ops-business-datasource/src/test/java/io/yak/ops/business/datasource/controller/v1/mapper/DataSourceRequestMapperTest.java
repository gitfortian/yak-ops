package io.yak.ops.business.datasource.controller.v1.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.datasource.domain.DataSourceQuery;
import io.yak.ops.business.datasource.management.DataSourceValidator;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import org.junit.jupiter.api.Test;

class DataSourceRequestMapperTest {

  private final DataSourceRequestMapper mapper =
      new DataSourceRequestMapper(new DataSourceValidator());

  @Test
  void pageQueryIsNormalizedWithoutMutatingHttpRequest() {
    DataSourceQueryDTO request = new DataSourceQueryDTO();
    request.setPageNo(2);
    request.setPageSize(50);
    request.setName("  orders-db  ");
    request.setKeyword("  jdbc:mysql  ");
    request.setDbType(" mysql ");
    request.setEnvironment(" production ");
    request.setConnStatus(" connected ");

    DataSourceQuery query = mapper.query(request);

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
}
