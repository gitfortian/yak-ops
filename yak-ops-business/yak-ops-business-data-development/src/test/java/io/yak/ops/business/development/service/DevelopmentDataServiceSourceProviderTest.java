package io.yak.ops.business.development.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.ResolvedSource;
import io.yak.ops.business.development.domain.DevelopmentReleaseDetail;
import io.yak.ops.business.development.domain.DevelopmentReleaseSummary;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DevelopmentDataServiceSourceProviderTest {

  private DevelopmentReleaseService releaseService;
  private DevelopmentDataServiceSourceProvider provider;

  @BeforeEach
  void setUp() {
    releaseService = mock(DevelopmentReleaseService.class);
    provider = new DevelopmentDataServiceSourceProvider(releaseService, new ObjectMapper());
  }

  @Test
  void resolveCopiesImmutableSqlRevisionAndDatasource() {
    when(releaseService.get(88L)).thenReturn(release(TaskAssetStatus.ONLINE, 2));

    ResolvedSource resolved = provider.resolve("88");

    assertThat(resolved.descriptor().sourceType())
        .isEqualTo(DevelopmentDataServiceSourceProvider.SOURCE_TYPE);
    assertThat(resolved.descriptor().sourceRef()).isEqualTo("88");
    assertThat(resolved.descriptor().sourceRevisionId()).isEqualTo(102L);
    assertThat(resolved.descriptor().sourceRevisionNo()).isEqualTo(2);
    assertThat(resolved.descriptor().dataSourceId()).isEqualTo(42L);
    assertThat(resolved.descriptor().status()).isEqualTo("ONLINE");
    assertThat(resolved.descriptor().defaultPath()).isEqualTo("/query/88");
    assertThat(resolved.sql())
        .isEqualTo("select id, amount from orders where status = :status");
  }

  @Test
  void resolveKeepsOfflineStateForManagementInspection() {
    when(releaseService.get(88L)).thenReturn(release(TaskAssetStatus.OFFLINE, 2));

    ResolvedSource resolved = provider.resolve("88");

    assertThat(resolved.descriptor().status()).isEqualTo("OFFLINE");
  }

  @Test
  void resolveRejectsReleaseWithoutDatasource() {
    when(releaseService.get(88L)).thenReturn(releaseWithConfig("{}"));

    assertThatThrownBy(() -> provider.resolve("88"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dataSourceId");
  }

  private DevelopmentReleaseDetail release(TaskAssetStatus status, int revisionNo) {
    return release(
        status,
        revisionNo,
        "{\"dataSourceId\":\"42\",\"timeoutSeconds\":30}");
  }

  private DevelopmentReleaseDetail releaseWithConfig(String configJson) {
    return release(TaskAssetStatus.ONLINE, 2, configJson);
  }

  private DevelopmentReleaseDetail release(
      TaskAssetStatus status,
      int revisionNo,
      String configJson) {
    Instant now = Instant.parse("2026-08-16T01:00:00Z");
    TaskDefinition definition = new TaskDefinition(
        "SQL",
        1,
        "select id, amount from orders where status = :status",
        configJson);
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
}
