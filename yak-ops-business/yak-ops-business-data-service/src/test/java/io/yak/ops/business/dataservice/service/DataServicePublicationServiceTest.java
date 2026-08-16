package io.yak.ops.business.dataservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.service.DataServicePublicationService.PublishRequest;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiInput;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import io.yak.ops.business.dataservice.service.DataServiceService.SourceSnapshot;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.ResolvedSource;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.SourceDescriptor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataServicePublicationServiceTest {

  private static final String SOURCE_TYPE = "DATA_DEVELOPMENT_RELEASE";

  private DataServiceService dataServiceService;
  private DataServiceSourceProvider provider;
  private DataServicePublicationService service;

  @BeforeEach
  void setUp() {
    dataServiceService = mock(DataServiceService.class);
    provider = mock(DataServiceSourceProvider.class);
    when(provider.sourceType()).thenReturn(SOURCE_TYPE);
    service = new DataServicePublicationService(dataServiceService, List.of(provider));
  }

  @Test
  void publishAlwaysCopiesSqlAndDatasourceFromResolvedSource() {
    when(provider.resolve("88")).thenReturn(resolved("ONLINE", 102L, 2));
    when(dataServiceService.findBySource(SOURCE_TYPE, "88")).thenReturn(Optional.empty());
    when(dataServiceService.saveFromSource(any(SourceSnapshot.class), any(ApiInput.class)))
        .thenAnswer(invocation -> view(
            9L,
            invocation.getArgument(1),
            invocation.getArgument(0)));

    ApiView result = service.publish(new PublishRequest(
        SOURCE_TYPE,
        "88",
        "订单查询 API",
        "/orders",
        500,
        20,
        true,
        "供运营系统查询"));

    ArgumentCaptor<SourceSnapshot> sourceCaptor = ArgumentCaptor.forClass(SourceSnapshot.class);
    ArgumentCaptor<ApiInput> inputCaptor = ArgumentCaptor.forClass(ApiInput.class);
    verify(dataServiceService).saveFromSource(sourceCaptor.capture(), inputCaptor.capture());

    assertThat(sourceCaptor.getValue().sourceType()).isEqualTo(SOURCE_TYPE);
    assertThat(sourceCaptor.getValue().sourceRef()).isEqualTo("88");
    assertThat(sourceCaptor.getValue().sourceRevisionId()).isEqualTo(102L);
    assertThat(sourceCaptor.getValue().sourceRevisionNo()).isEqualTo(2);
    assertThat(inputCaptor.getValue().dataSourceId()).isEqualTo(42L);
    assertThat(inputCaptor.getValue().sql())
        .isEqualTo("select id, amount from orders where status = :status");
    assertThat(result.id()).isEqualTo(9L);
  }

  @Test
  void publishRejectsSourceThatIsNotOnline() {
    when(provider.resolve("88")).thenReturn(resolved("OFFLINE", 102L, 2));

    assertThatThrownBy(() -> service.publish(new PublishRequest(
        SOURCE_TYPE, "88", null, null, null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ONLINE");

    verify(dataServiceService, never()).saveFromSource(any(), any());
  }

  @Test
  void republishRefreshesSameServiceIdentityAndPreservesOperationalSettings() {
    ApiView existing = view(
        9L,
        new ApiInput(
            "订单查询 API",
            "/orders",
            42L,
            "select old_sql from orders",
            500,
            20,
            false,
            "运营查询"),
        new SourceSnapshot(SOURCE_TYPE, "88", 102L, 2));
    when(dataServiceService.get(9L)).thenReturn(existing);
    when(provider.resolve("88")).thenReturn(resolved("ONLINE", 103L, 3));
    when(dataServiceService.findBySource(SOURCE_TYPE, "88")).thenReturn(Optional.of(existing));
    when(dataServiceService.saveFromSource(any(SourceSnapshot.class), any(ApiInput.class)))
        .thenAnswer(invocation -> view(
            9L,
            invocation.getArgument(1),
            invocation.getArgument(0)));

    ApiView refreshed = service.republish(9L, null);

    assertThat(refreshed.id()).isEqualTo(9L);
    assertThat(refreshed.sourceRevisionNo()).isEqualTo(3);
    assertThat(refreshed.name()).isEqualTo("订单查询 API");
    assertThat(refreshed.path()).isEqualTo("/orders");
    assertThat(refreshed.maxRows()).isEqualTo(500);
    assertThat(refreshed.timeoutSeconds()).isEqualTo(20);
    assertThat(refreshed.enabled()).isFalse();
    assertThat(refreshed.description()).isEqualTo("运营查询");
  }

  private ResolvedSource resolved(String status, Long revisionId, int revisionNo) {
    SourceDescriptor descriptor = new SourceDescriptor(
        SOURCE_TYPE,
        "88",
        "订单查询",
        "SQL",
        status,
        revisionId,
        revisionNo,
        42L,
        30,
        "/query/88",
        Instant.parse("2026-08-16T01:00:00Z"));
    return new ResolvedSource(
        descriptor,
        "select id, amount from orders where status = :status");
  }

  private ApiView view(Long id, ApiInput input, SourceSnapshot source) {
    return new ApiView(
        id,
        input.name(),
        input.path(),
        "/api/v1/data-service/runtime" + input.path(),
        input.dataSourceId(),
        input.sql(),
        List.of("status"),
        input.maxRows(),
        input.timeoutSeconds(),
        Boolean.TRUE.equals(input.enabled()),
        "NONE",
        input.description(),
        source.sourceType(),
        source.sourceRef(),
        source.sourceRevisionId(),
        source.sourceRevisionNo(),
        null,
        null);
  }
}
