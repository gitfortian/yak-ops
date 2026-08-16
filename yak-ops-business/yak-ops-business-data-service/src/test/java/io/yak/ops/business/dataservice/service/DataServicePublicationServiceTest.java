package io.yak.ops.business.dataservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.service.DataServiceDocumentationService.DocumentationInput;
import io.yak.ops.business.dataservice.service.DataServicePublicationService.PublishRequest;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import io.yak.ops.business.dataservice.service.DataServiceService.RuntimeDefinition;
import io.yak.ops.business.dataservice.service.DataServiceService.ServiceSettingsInput;
import io.yak.ops.business.dataservice.service.DataServiceService.SourceSnapshot;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.ParameterContract;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.ResolvedSource;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.ResponseFieldContract;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.SourceContract;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.SourceDescriptor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataServicePublicationServiceTest {

  private static final String SOURCE_TYPE = "DATA_DEVELOPMENT_DATA_SERVICE";

  private DataServiceService dataServiceService;
  private DataServiceDocumentationService documentationService;
  private DataServiceSourceProvider provider;
  private DataServicePublicationService service;

  @BeforeEach
  void setUp() {
    dataServiceService = mock(DataServiceService.class);
    documentationService = mock(DataServiceDocumentationService.class);
    provider = mock(DataServiceSourceProvider.class);
    when(provider.sourceType()).thenReturn(SOURCE_TYPE);
    when(provider.managesServiceDefinition()).thenReturn(true);
    service = new DataServicePublicationService(
        dataServiceService, documentationService, List.of(provider));
  }

  @Test
  void publishUsesDataServiceRevisionDefinitionAndSyncsContract() {
    when(provider.resolve("100")).thenReturn(resolved("ONLINE", 900L, 2, "/orders-v2"));
    when(dataServiceService.findBySource(SOURCE_TYPE, "100")).thenReturn(Optional.empty());
    when(dataServiceService.saveFromSource(
        any(SourceSnapshot.class),
        any(RuntimeDefinition.class),
        any(ServiceSettingsInput.class)))
        .thenAnswer(invocation -> view(
            9L,
            invocation.getArgument(1),
            invocation.getArgument(2),
            invocation.getArgument(0)));

    ApiView result = service.publish(new PublishRequest(
        SOURCE_TYPE,
        "100",
        "客户端不应覆盖名称",
        "/client-path",
        9999,
        99,
        false,
        "客户端不应覆盖说明"));

    ArgumentCaptor<SourceSnapshot> sourceCaptor = ArgumentCaptor.forClass(SourceSnapshot.class);
    ArgumentCaptor<RuntimeDefinition> runtimeCaptor = ArgumentCaptor.forClass(RuntimeDefinition.class);
    ArgumentCaptor<ServiceSettingsInput> settingsCaptor =
        ArgumentCaptor.forClass(ServiceSettingsInput.class);
    verify(dataServiceService).saveFromSource(
        sourceCaptor.capture(), runtimeCaptor.capture(), settingsCaptor.capture());

    assertThat(sourceCaptor.getValue().sourceRef()).isEqualTo("100");
    assertThat(sourceCaptor.getValue().sourceRevisionId()).isEqualTo(900L);
    assertThat(sourceCaptor.getValue().sourceRevisionNo()).isEqualTo(2);
    assertThat(runtimeCaptor.getValue().dataSourceId()).isEqualTo(42L);
    assertThat(runtimeCaptor.getValue().sql())
        .isEqualTo("select id, amount from orders where status = :status");
    assertThat(settingsCaptor.getValue().name()).isEqualTo("订单查询 API");
    assertThat(settingsCaptor.getValue().path()).isEqualTo("/orders-v2");
    assertThat(settingsCaptor.getValue().maxRows()).isEqualTo(500);
    assertThat(settingsCaptor.getValue().timeoutSeconds()).isEqualTo(20);
    assertThat(settingsCaptor.getValue().enabled()).isFalse();
    assertThat(settingsCaptor.getValue().description()).isEqualTo("DS Revision 定义");
    assertThat(result.id()).isEqualTo(9L);

    ArgumentCaptor<DocumentationInput> docsCaptor = ArgumentCaptor.forClass(DocumentationInput.class);
    verify(documentationService).save(org.mockito.ArgumentMatchers.eq(9L), docsCaptor.capture());
    assertThat(docsCaptor.getValue().parameters()).singleElement()
        .satisfies(parameter -> {
          assertThat(parameter.name()).isEqualTo("status");
          assertThat(parameter.description()).isEqualTo("订单状态");
        });
    assertThat(docsCaptor.getValue().responseFields()).singleElement()
        .satisfies(field -> assertThat(field.name()).isEqualTo("id"));
  }

  @Test
  void publishRejectsSourceThatIsNotOnline() {
    when(provider.resolve("100")).thenReturn(resolved("OFFLINE", 900L, 2, "/orders"));

    assertThatThrownBy(() -> service.publish(new PublishRequest(
        SOURCE_TYPE, "100", null, null, null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ONLINE");

    verify(dataServiceService, never()).saveFromSource(any(), any(), any());
    verify(documentationService, never()).save(any(), any());
  }

  @Test
  void republishRefreshesLatestDataServiceRevisionAndPreservesRuntimeEnablement() {
    ApiView existing = view(
        9L,
        new RuntimeDefinition(42L, "select old_sql from orders"),
        new ServiceSettingsInput("订单查询 API", "/orders", 500, 20, false, "旧定义"),
        new SourceSnapshot(SOURCE_TYPE, "100", 900L, 2));
    when(dataServiceService.get(9L)).thenReturn(existing);
    when(provider.resolve("100")).thenReturn(resolved("ONLINE", 901L, 3, "/orders-v3"));
    when(dataServiceService.findBySource(SOURCE_TYPE, "100")).thenReturn(Optional.of(existing));
    when(dataServiceService.saveFromSource(
        any(SourceSnapshot.class), any(RuntimeDefinition.class), any(ServiceSettingsInput.class)))
        .thenAnswer(invocation -> view(
            9L,
            invocation.getArgument(1),
            invocation.getArgument(2),
            invocation.getArgument(0)));

    ApiView refreshed = service.republish(9L, null);

    assertThat(refreshed.id()).isEqualTo(9L);
    assertThat(refreshed.sourceRevisionNo()).isEqualTo(3);
    assertThat(refreshed.path()).isEqualTo("/orders-v3");
    assertThat(refreshed.enabled()).isFalse();
    verify(documentationService).save(org.mockito.ArgumentMatchers.eq(9L), any());
  }

  @Test
  void updateSettingsRejectsSourceManagedDefinition() {
    ApiView existing = view(
        9L,
        new RuntimeDefinition(42L, "select id from orders"),
        new ServiceSettingsInput("订单查询 API", "/orders", 500, 20, true, null),
        new SourceSnapshot(SOURCE_TYPE, "100", 900L, 2));
    when(dataServiceService.get(9L)).thenReturn(existing);

    assertThatThrownBy(() -> service.updateSettings(
        9L,
        new DataServicePublicationService.PublicationSettings(
            "changed", "/changed", 1, 1, false, null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Data Service Node");

    verify(dataServiceService, never()).updateSettings(any(), any());
  }

  @Test
  void legacyFrozenSourceCannotRepublishWithoutProviderButKeepsExistingSnapshot() {
    ApiView existing = view(
        10L,
        new RuntimeDefinition(42L, "select id from orders"),
        new ServiceSettingsInput("Legacy", "/legacy", 500, 20, true, null),
        new SourceSnapshot("DATA_DEVELOPMENT_RELEASE", "88", 102L, 2));
    when(dataServiceService.get(10L)).thenReturn(existing);

    assertThatThrownBy(() -> service.republish(10L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("历史冻结来源");

    verify(dataServiceService, never()).saveFromSource(any(), any(), any());
  }

  private ResolvedSource resolved(String status, Long revisionId, int revisionNo, String path) {
    SourceDescriptor descriptor = new SourceDescriptor(
        SOURCE_TYPE,
        "100",
        "订单查询 API",
        "DATA_SERVICE",
        status,
        revisionId,
        revisionNo,
        42L,
        500,
        20,
        path,
        "DS Revision 定义",
        Instant.parse("2026-08-16T01:00:00Z"));
    SourceContract contract = new SourceContract(
        List.of(new ParameterContract("status", "STRING", true, "订单状态", "PAID")),
        List.of(new ResponseFieldContract("id", "INTEGER", false, "订单 ID", "1")));
    return new ResolvedSource(
        descriptor,
        "select id, amount from orders where status = :status",
        contract);
  }

  private ApiView view(
      Long id,
      RuntimeDefinition runtime,
      ServiceSettingsInput settings,
      SourceSnapshot source) {
    return new ApiView(
        id,
        settings.name(),
        settings.path(),
        "/api/v1/data-service/runtime" + settings.path(),
        runtime.dataSourceId(),
        runtime.sql(),
        List.of("status"),
        settings.maxRows(),
        settings.timeoutSeconds(),
        Boolean.TRUE.equals(settings.enabled()),
        "NONE",
        settings.description(),
        source.sourceType(),
        source.sourceRef(),
        source.sourceRevisionId(),
        source.sourceRevisionNo(),
        null,
        null);
  }
}
