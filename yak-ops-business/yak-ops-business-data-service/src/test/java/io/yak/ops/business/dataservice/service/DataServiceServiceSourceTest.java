package io.yak.ops.business.dataservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceCallLogMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiPO;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import io.yak.ops.business.dataservice.service.DataServiceService.RuntimeDefinition;
import io.yak.ops.business.dataservice.service.DataServiceService.ServiceSettingsInput;
import io.yak.ops.business.dataservice.service.DataServiceService.SourceSnapshot;
import io.yak.ops.business.dataservice.service.support.DataServiceSqlCompiler;
import io.yak.ops.business.datasource.service.support.BusinessDataSourceExecutionProvider;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataServiceServiceSourceTest {

  private DataServiceApiMapper apiMapper;
  private DataServiceRuntimeService runtimeService;
  private DataServiceService service;
  private DataServiceApiPO sourceManaged;

  @BeforeEach
  void setUp() {
    apiMapper = mock(DataServiceApiMapper.class);
    runtimeService = mock(DataServiceRuntimeService.class);
    service = new DataServiceService(
        apiMapper,
        mock(DataServiceCallLogMapper.class),
        mock(BusinessDataSourceExecutionProvider.class),
        new DataServiceSqlCompiler(),
        new ObjectMapper(),
        mock(DataServiceAccessService.class),
        runtimeService);

    sourceManaged = api(
        9L,
        "订单查询",
        "/orders",
        42L,
        "select id from orders where status = :status");
    sourceManaged.setSourceType("DATA_DEVELOPMENT_RELEASE");
    sourceManaged.setSourceRef("88");
    sourceManaged.setSourceRevisionId(102L);
    sourceManaged.setSourceRevisionNo(2);

    when(apiMapper.selectById(9L)).thenReturn(sourceManaged);
    when(apiMapper.selectCount(any())).thenReturn(0L);
  }

  @Test
  void sourceManagedSettingsUpdatePreservesRuntimeDefinition() {
    ApiView updated = service.updateSettings(
        9L,
        new ServiceSettingsInput(
            "订单查询 API",
            "/orders/v1",
            500,
            20,
            false,
            "运营查询"));

    assertThat(updated.name()).isEqualTo("订单查询 API");
    assertThat(updated.path()).isEqualTo("/orders/v1");
    assertThat(updated.maxRows()).isEqualTo(500);
    assertThat(updated.enabled()).isFalse();
    assertThat(updated.dataSourceId()).isEqualTo(42L);
    assertThat(updated.sql()).isEqualTo("select id from orders where status = :status");
    assertThat(updated.sourceRevisionNo()).isEqualTo(2);
    assertThat(sourceManaged.getCacheEnabled()).isFalse();
    assertThat(sourceManaged.getCircuitBreakerEnabled()).isFalse();
    verify(apiMapper).updateById(sourceManaged);
    verify(runtimeService).invalidate(9L);
  }

  @Test
  void legacySettingsUpdateAlsoPreservesRuntimeDefinition() {
    DataServiceApiPO legacy = api(
        10L,
        "历史 API",
        "/legacy",
        77L,
        "select id from legacy_orders");
    when(apiMapper.selectById(10L)).thenReturn(legacy);

    ApiView updated = service.updateSettings(
        10L,
        new ServiceSettingsInput(
            "历史订单 API",
            "/legacy/orders",
            300,
            15,
            true,
            "继续运行但执行快照冻结"));

    assertThat(updated.dataSourceId()).isEqualTo(77L);
    assertThat(updated.sql()).isEqualTo("select id from legacy_orders");
    assertThat(updated.sourceType()).isNull();
    assertThat(updated.name()).isEqualTo("历史订单 API");
    assertThat(updated.path()).isEqualTo("/legacy/orders");
  }

  @Test
  void sourceRepublishIsTheOnlyPathThatRefreshesRuntimeDefinition() {
    when(apiMapper.selectOne(any())).thenReturn(sourceManaged);

    ApiView refreshed = service.saveFromSource(
        new SourceSnapshot("DATA_DEVELOPMENT_RELEASE", "88", 103L, 3),
        new RuntimeDefinition(
            43L,
            "select id, amount from orders where status = :status"),
        new ServiceSettingsInput(
            "订单查询 API",
            "/orders",
            1000,
            30,
            true,
            "新版查询"));

    assertThat(refreshed.id()).isEqualTo(9L);
    assertThat(refreshed.dataSourceId()).isEqualTo(43L);
    assertThat(refreshed.sql())
        .isEqualTo("select id, amount from orders where status = :status");
    assertThat(refreshed.sourceRevisionId()).isEqualTo(103L);
    assertThat(refreshed.sourceRevisionNo()).isEqualTo(3);
    assertThat(sourceManaged.getCacheEnabled()).isFalse();
    assertThat(sourceManaged.getCircuitBreakerEnabled()).isFalse();
  }

  private DataServiceApiPO api(
      Long id,
      String name,
      String path,
      Long dataSourceId,
      String sql) {
    DataServiceApiPO api = new DataServiceApiPO();
    api.setId(id);
    api.setName(name);
    api.setPath(path);
    api.setDataSourceId(dataSourceId);
    api.setSqlText(sql);
    api.setMaxRows(1000);
    api.setTimeoutSeconds(30);
    api.setEnabled(true);
    api.setAuthMode("NONE");
    api.setCacheEnabled(false);
    api.setCacheTtlSeconds(60);
    api.setCacheMaxEntries(200);
    api.setCircuitBreakerEnabled(false);
    api.setCircuitFailureThreshold(5);
    api.setCircuitRecoverySeconds(30);
    api.setCreateTime(LocalDateTime.now());
    api.setUpdateTime(LocalDateTime.now());
    return api;
  }
}
