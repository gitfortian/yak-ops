package io.yak.ops.business.development.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.service.DataServicePublicationService;
import io.yak.ops.business.dataservice.service.DataServicePublicationService.PublicationState;
import io.yak.ops.business.dataservice.service.DataServicePublicationService.PublishRequest;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.SourceDescriptor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DevelopmentDataServiceServiceTest {

  private DataServicePublicationService publicationService;
  private DevelopmentDataServiceService service;

  @BeforeEach
  void setUp() {
    publicationService = mock(DataServicePublicationService.class);
    service = new DevelopmentDataServiceService(publicationService);
  }

  @Test
  void publishDelegatesToUnifiedPublicationService() {
    ApiView published = view(9L, 2);
    when(publicationService.publish(any(PublishRequest.class))).thenReturn(published);

    ApiView result = service.publish(
        88L,
        new DevelopmentDataServiceService.PublishCommand(
            "订单查询",
            "/orders",
            500,
            20,
            true,
            "供运营系统查询"));

    ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(publicationService).publish(captor.capture());
    assertThat(captor.getValue().sourceType())
        .isEqualTo(DevelopmentDataServiceSourceProvider.SOURCE_TYPE);
    assertThat(captor.getValue().sourceRef()).isEqualTo("88");
    assertThat(captor.getValue().name()).isEqualTo("订单查询");
    assertThat(captor.getValue().path()).isEqualTo("/orders");
    assertThat(result).isSameAs(published);
  }

  @Test
  void stateMapsGenericPublicationStateForExistingFrontendContract() {
    SourceDescriptor source = new SourceDescriptor(
        DevelopmentDataServiceSourceProvider.SOURCE_TYPE,
        "88",
        "订单查询",
        "SQL",
        "ONLINE",
        103L,
        3,
        42L,
        30,
        "/query/88",
        Instant.parse("2026-08-16T01:00:00Z"));
    when(publicationService.state(
        DevelopmentDataServiceSourceProvider.SOURCE_TYPE,
        "88"))
        .thenReturn(new PublicationState(true, true, source, view(9L, 2)));

    DevelopmentDataServiceService.ReleaseDataServiceState state = service.state(88L);

    assertThat(state.published()).isTrue();
    assertThat(state.updateAvailable()).isTrue();
    assertThat(state.releaseRevisionNo()).isEqualTo(3);
    assertThat(state.releaseStatus()).isEqualTo("ONLINE");
  }

  private ApiView view(Long id, int revisionNo) {
    return new ApiView(
        id,
        "订单查询",
        "/orders",
        "/api/v1/data-service/runtime/orders",
        42L,
        "select id from orders where status = :status",
        List.of("status"),
        500,
        20,
        true,
        "NONE",
        null,
        DevelopmentDataServiceSourceProvider.SOURCE_TYPE,
        "88",
        100L + revisionNo,
        revisionNo,
        null,
        null);
  }
}
