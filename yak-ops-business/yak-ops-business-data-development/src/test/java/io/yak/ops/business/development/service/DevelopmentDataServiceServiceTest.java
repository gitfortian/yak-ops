package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataservice.service.DataServiceService;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiInput;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import io.yak.ops.business.dataservice.service.DataServiceService.SourceSnapshot;
import io.yak.ops.business.development.domain.DevelopmentReleaseDetail;
import io.yak.ops.business.development.domain.DevelopmentReleaseSummary;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DevelopmentDataServiceServiceTest {

  private DevelopmentReleaseService releaseService;
  private DataServiceService dataServiceService;
  private DevelopmentDataServiceService service;

  @BeforeEach
  void setUp() {
    releaseService = mock(DevelopmentReleaseService.class);
    dataServiceService = mock(DataServiceService.class);
    service = new DevelopmentDataServiceService(
        releaseService,
        dataServiceService,
        new ObjectMapper());
  }

  @Test
  void publishCopiesImmutableSqlRevisionAndDatasourceIntoStableService() {
    when(releaseService.get(88L)).thenReturn(release(TaskAssetStatus.ONLINE, 2));
    when(dataServiceService.findBySource(
        DevelopmentDataServiceService.SOURCE_TYPE, "88"))
        .thenReturn(Optional.empty());
    when(dataServiceService.saveFromSource(any(SourceSnapshot.class), any(ApiInput.class)))
        .thenAnswer(invocation -> {
          SourceSnapshot source = invocation.getArgument(0);
          ApiInput input = invocation.getArgument(1);
          return view(9L, input, source);
        });

    ApiView result = service.publish(
        88L,
        new DevelopmentDataServiceService.PublishCommand(
            "订单查询",
            "/orders",
            500,
            20,
            true,
            "供运营系统查询"));

    assertEquals(9L, result.id());
    assertEquals(42L, result.dataSourceId());
    assertEquals("select id, amount from orders where status = :status", result.sql());
    assertEquals(2, result.sourceRevisionNo());

    ArgumentCaptor<SourceSnapshot> sourceCaptor = ArgumentCaptor.forClass(SourceSnapshot.class);
    ArgumentCaptor<ApiInput> inputCaptor = ArgumentCaptor.forClass(ApiInput.class);
    verify(dataServiceService).saveFromSource(sourceCaptor.capture(), inputCaptor.capture());
    assertEquals("DATA_DEVELOPMENT_RELEASE", sourceCaptor.getValue().sourceType());
    assertEquals("88", sourceCaptor.getValue().sourceRef());
    assertEquals(102L, sourceCaptor.getValue().sourceRevisionId());
    assertEquals(2, sourceCaptor.getValue().sourceRevisionNo());
    assertEquals(42L, inputCaptor.getValue().dataSourceId());
    assertEquals("/orders", inputCaptor.getValue().path());
  }

  @Test
  void stateMarksExistingServiceWhenActiveSqlRevisionChanged() {
    when(releaseService.get(88L)).thenReturn(release(TaskAssetStatus.ONLINE, 3));
    ApiView existing = new ApiView(
        9L,
        "订单查询",
        "/orders",
        "/api/v1/data-service/runtime/orders",
        42L,
        "select 1",
        List.of(),
        500,
        20,
        true,
        null,
        DevelopmentDataServiceService.SOURCE_TYPE,
        "88",
        102L,
        2,
        null,
        null);
    when(dataServiceService.findBySource(
        DevelopmentDataServiceService.SOURCE_TYPE, "88"))
        .thenReturn(Optional.of(existing));

    DevelopmentDataServiceService.ReleaseDataServiceState state = service.state(88L);

    assertTrue(state.published());
    assertTrue(state.updateAvailable());
    assertEquals(3, state.releaseRevisionNo());
  }

  @Test
  void publishRejectsOfflineSqlRelease() {
    when(releaseService.get(88L)).thenReturn(release(TaskAssetStatus.OFFLINE, 2));

    IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> service.publish(88L, null));

    assertTrue(error.getMessage().contains("ONLINE"));
  }

  private DevelopmentReleaseDetail release(TaskAssetStatus status, int revisionNo) {
    Instant now = Instant.parse("2026-08-15T10:00:00Z");
    TaskDefinition definition = new TaskDefinition(
        "SQL",
        1,
        "select id, amount from orders where status = :status",
        "{\"dataSourceId\":\"42\",\"timeoutSeconds\":30}");
    DevelopmentTaskRevision revision = new DevelopmentTaskRevision(
        100L + revisionNo,
        7L,
        revisionNo,
        revisionNo,
        definition,
        "checksum-" + revisionNo,
        now);
    DevelopmentReleaseSummary summary = new DevelopmentReleaseSummary(
        88L,
        7L,
        "订单查询",
        "SQL",
        status,
        revision.id(),
        revisionNo,
        revisionNo,
        false,
        revision.checksum(),
        now,
        now);
    return new DevelopmentReleaseDetail(summary, revision, List.of());
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
        input.enabled(),
        input.description(),
        source.sourceType(),
        source.sourceRef(),
        source.sourceRevisionId(),
        source.sourceRevisionNo(),
        null,
        null);
  }
}
