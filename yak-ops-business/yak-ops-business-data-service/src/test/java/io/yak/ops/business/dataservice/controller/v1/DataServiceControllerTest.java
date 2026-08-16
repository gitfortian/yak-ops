package io.yak.ops.business.dataservice.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.controller.v1.DataServiceController.UpdateDataServiceRequest;
import io.yak.ops.business.dataservice.service.DataServiceAccessService;
import io.yak.ops.business.dataservice.service.DataServiceDocumentationService;
import io.yak.ops.business.dataservice.service.DataServicePublicationService;
import io.yak.ops.business.dataservice.service.DataServiceService;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiInput;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataServiceControllerTest {

  @Test
  void updatePreservesServerOwnedSqlAndDatasourceSnapshot() {
    DataServiceService dataServiceService = mock(DataServiceService.class);
    DataServiceController controller = new DataServiceController(
        dataServiceService,
        mock(DataServicePublicationService.class),
        mock(DataServiceAccessService.class),
        mock(DataServiceDocumentationService.class));

    ApiView current = new ApiView(
        9L,
        "历史订单 API",
        "/legacy/orders",
        "/api/v1/data-service/runtime/legacy/orders",
        42L,
        "select id from orders where status = :status",
        List.of("status"),
        1000,
        30,
        true,
        "NONE",
        "legacy service",
        null,
        null,
        null,
        null,
        null,
        null);
    when(dataServiceService.get(9L)).thenReturn(current);
    when(dataServiceService.save(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.any(ApiInput.class)))
        .thenReturn(current);

    controller.update(
        9L,
        new UpdateDataServiceRequest(
            "订单查询 API",
            "/orders",
            500,
            20,
            false,
            "只修改服务侧配置"));

    ArgumentCaptor<ApiInput> inputCaptor = ArgumentCaptor.forClass(ApiInput.class);
    verify(dataServiceService).save(org.mockito.ArgumentMatchers.eq(9L), inputCaptor.capture());
    ApiInput input = inputCaptor.getValue();

    assertThat(input.name()).isEqualTo("订单查询 API");
    assertThat(input.path()).isEqualTo("/orders");
    assertThat(input.maxRows()).isEqualTo(500);
    assertThat(input.timeoutSeconds()).isEqualTo(20);
    assertThat(input.enabled()).isFalse();
    assertThat(input.description()).isEqualTo("只修改服务侧配置");
    assertThat(input.dataSourceId()).isEqualTo(42L);
    assertThat(input.sql()).isEqualTo("select id from orders where status = :status");
  }
}
