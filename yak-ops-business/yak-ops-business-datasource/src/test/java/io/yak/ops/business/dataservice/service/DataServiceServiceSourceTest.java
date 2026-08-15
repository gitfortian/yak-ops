package io.yak.ops.business.dataservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceCallLogMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiPO;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiInput;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import io.yak.ops.business.dataservice.service.support.DataServiceSqlCompiler;
import io.yak.ops.business.datasource.service.support.BusinessDataSourceExecutionProvider;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataServiceServiceSourceTest {

  private DataServiceApiMapper apiMapper;
  private DataServiceService service;
  private DataServiceApiPO sourceManaged;

  @BeforeEach
  void setUp() {
    apiMapper = mock(DataServiceApiMapper.class);
    service = new DataServiceService(
        apiMapper,
        mock(DataServiceCallLogMapper.class),
        mock(BusinessDataSourceExecutionProvider.class),
        new DataServiceSqlCompiler(),
        new ObjectMapper());

    sourceManaged = new DataServiceApiPO();
    sourceManaged.setId(9L);
    sourceManaged.setName("订单查询");
    sourceManaged.setPath("/orders");
    sourceManaged.setDataSourceId(42L);
    sourceManaged.setSqlText("select id from orders where status = :status");
    sourceManaged.setMaxRows(1000);
    sourceManaged.setTimeoutSeconds(30);
    sourceManaged.setEnabled(true);
    sourceManaged.setSourceType("DATA_DEVELOPMENT_RELEASE");
    sourceManaged.setSourceRef("88");
    sourceManaged.setSourceRevisionId(102L);
    sourceManaged.setSourceRevisionNo(2);
    sourceManaged.setCreateTime(LocalDateTime.now());
    sourceManaged.setUpdateTime(LocalDateTime.now());

    when(apiMapper.selectById(9L)).thenReturn(sourceManaged);
    when(apiMapper.selectCount(any())).thenReturn(0L);
  }

  @Test
  void manualEditCannotChangeDatasourceOfSourceManagedService() {
    assertThatThrownBy(() -> service.save(
        9L,
        new ApiInput(
            "订单查询",
            "/orders",
            43L,
            sourceManaged.getSqlText(),
            1000,
            30,
            true,
            null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("发布来源");
  }

  @Test
  void manualEditCannotChangeSqlOfSourceManagedService() {
    assertThatThrownBy(() -> service.save(
        9L,
        new ApiInput(
            "订单查询",
            "/orders",
            42L,
            "select id, amount from orders where status = :status",
            1000,
            30,
            true,
            null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("发布来源");
  }

  @Test
  void manualEditMayChangeOperationalMetadata() {
    ApiView updated = service.save(
        9L,
        new ApiInput(
            "订单查询 API",
            "/orders/v1",
            42L,
            sourceManaged.getSqlText(),
            500,
            20,
            false,
            "运营查询"));

    assertThat(updated.name()).isEqualTo("订单查询 API");
    assertThat(updated.path()).isEqualTo("/orders/v1");
    assertThat(updated.maxRows()).isEqualTo(500);
    assertThat(updated.enabled()).isFalse();
    assertThat(updated.sourceRevisionNo()).isEqualTo(2);
    verify(apiMapper).updateById(sourceManaged);
  }
}
