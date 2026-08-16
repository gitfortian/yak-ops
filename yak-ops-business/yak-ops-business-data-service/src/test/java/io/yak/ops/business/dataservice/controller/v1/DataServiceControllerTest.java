package io.yak.ops.business.dataservice.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.controller.v1.DataServiceController.UpdateDataServiceRequest;
import io.yak.ops.business.dataservice.service.DataServiceAccessService;
import io.yak.ops.business.dataservice.service.DataServiceDocumentationService;
import io.yak.ops.business.dataservice.service.DataServicePublicationService;
import io.yak.ops.business.dataservice.service.DataServicePublicationService.PublicationSettings;
import io.yak.ops.business.dataservice.service.DataServiceService;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataServiceControllerTest {

  @Test
  void updateContractContainsOnlyServiceFacingSettings() {
    List<String> components = Arrays.stream(UpdateDataServiceRequest.class.getRecordComponents())
        .map(component -> component.getName())
        .toList();

    assertThat(components).containsExactly(
        "name",
        "path",
        "maxRows",
        "timeoutSeconds",
        "enabled",
        "description");
    assertThat(components).doesNotContain("sql", "dataSourceId");
  }

  @Test
  void updateDelegatesThroughPublicationBoundary() {
    DataServiceService dataServiceService = mock(DataServiceService.class);
    DataServicePublicationService publicationService = mock(DataServicePublicationService.class);
    DataServiceController controller = new DataServiceController(
        dataServiceService,
        publicationService,
        mock(DataServiceAccessService.class),
        mock(DataServiceDocumentationService.class));
    when(publicationService.updateSettings(eq(9L), any(PublicationSettings.class)))
        .thenReturn(mock(ApiView.class));

    controller.update(
        9L,
        new UpdateDataServiceRequest(
            "订单查询 API",
            "/orders",
            500,
            20,
            false,
            "只修改服务侧配置"));

    ArgumentCaptor<PublicationSettings> inputCaptor =
        ArgumentCaptor.forClass(PublicationSettings.class);
    verify(publicationService).updateSettings(eq(9L), inputCaptor.capture());
    PublicationSettings input = inputCaptor.getValue();

    assertThat(input.name()).isEqualTo("订单查询 API");
    assertThat(input.path()).isEqualTo("/orders");
    assertThat(input.maxRows()).isEqualTo(500);
    assertThat(input.timeoutSeconds()).isEqualTo(20);
    assertThat(input.enabled()).isFalse();
    assertThat(input.description()).isEqualTo("只修改服务侧配置");
  }
}
