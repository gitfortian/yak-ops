package io.yak.ops.business.dataservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.ResolvedSource;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.SourceDescriptor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataServicePublicationStateTest {

  private static final String SOURCE_TYPE = "DATA_DEVELOPMENT_DATA_SERVICE";

  private DataServiceService dataServiceService;
  private DataServiceSourceProvider provider;
  private DataServicePublicationService service;

  @BeforeEach
  void setUp() {
    dataServiceService = mock(DataServiceService.class);
    provider = mock(DataServiceSourceProvider.class);
    when(provider.sourceType()).thenReturn(SOURCE_TYPE);
    service = new DataServicePublicationService(
        dataServiceService,
        mock(DataServiceDocumentationService.class),
        List.of(provider));
  }

  @Test
  void stateDistinguishesUndeployedPendingAndSynchronizedRuntime() {
    when(provider.resolve("100")).thenReturn(new ResolvedSource(
        new SourceDescriptor(
            SOURCE_TYPE,
            "100",
            "订单查询 API",
            "DATA_SERVICE",
            "ONLINE",
            903L,
            3,
            42L,
            500,
            30,
            "/orders",
            null,
            Instant.parse("2026-08-16T01:00:00Z")),
        "select id from orders"));

    when(dataServiceService.findBySource(SOURCE_TYPE, "100")).thenReturn(Optional.empty());
    var undeployed = service.state(SOURCE_TYPE, "100");
    assertThat(undeployed.published()).isFalse();
    assertThat(undeployed.updateAvailable()).isFalse();

    when(dataServiceService.findBySource(SOURCE_TYPE, "100"))
        .thenReturn(Optional.of(view(902L, 2, false)));
    var pending = service.state(SOURCE_TYPE, "100");
    assertThat(pending.published()).isTrue();
    assertThat(pending.updateAvailable()).isTrue();
    assertThat(pending.detail().enabled()).isFalse();

    when(dataServiceService.findBySource(SOURCE_TYPE, "100"))
        .thenReturn(Optional.of(view(903L, 3, true)));
    var synchronizedState = service.state(SOURCE_TYPE, "100");
    assertThat(synchronizedState.published()).isTrue();
    assertThat(synchronizedState.updateAvailable()).isFalse();
    assertThat(synchronizedState.detail().enabled()).isTrue();
  }

  private ApiView view(Long revisionId, int revisionNo, boolean enabled) {
    return new ApiView(
        9L,
        "订单查询 API",
        "/orders",
        "/api/v1/data-service/runtime/orders",
        42L,
        "select id from orders",
        List.of(),
        500,
        30,
        enabled,
        "NONE",
        null,
        SOURCE_TYPE,
        "100",
        revisionId,
        revisionNo,
        null,
        null,
        false);
  }
}
