package io.yak.ops.business.digitalscreen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.digitalscreen.domain.DigitalScreen;
import io.yak.ops.business.digitalscreen.domain.DigitalScreenStatus;
import io.yak.ops.business.digitalscreen.repository.DigitalScreenRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DigitalScreenApplicationServiceTest {

  @Mock
  private DigitalScreenRepository repository;

  private DigitalScreenApplicationService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new DigitalScreenApplicationService(repository);
  }

  @Test
  void createNormalizesDefinitionAndStartsFromDraftPersistence() {
    DigitalScreen created = screen(1L, "运营大屏", DigitalScreenStatus.DRAFT, null);
    when(repository.insert("运营大屏", null, "operation-center", 1, Map.of()))
        .thenReturn(created);

    DigitalScreen result = service.create(new CreateDigitalScreenCommand(
        "  运营大屏  ",
        "   ",
        " operation-center ",
        null));

    assertThat(result).isSameAs(created);
    verify(repository).insert("运营大屏", null, "operation-center", 1, Map.of());
  }

  @Test
  void publishAndOfflineKeepCurrentMutableLifecycleContract() {
    DigitalScreen draft = screen(7L, "监控大屏", DigitalScreenStatus.DRAFT, null);
    DigitalScreen published = screen(7L, "监控大屏", DigitalScreenStatus.PUBLISHED, Instant.now());
    when(repository.findById(7L)).thenReturn(Optional.of(draft), Optional.of(published));
    when(repository.updateStatus(eq(7L), eq(DigitalScreenStatus.PUBLISHED), any(Instant.class)))
        .thenReturn(published);
    when(repository.updateStatus(7L, DigitalScreenStatus.DRAFT, null)).thenReturn(draft);

    assertThat(service.publish(7L).status()).isEqualTo(DigitalScreenStatus.PUBLISHED);
    assertThat(service.offline(7L).status()).isEqualTo(DigitalScreenStatus.DRAFT);

    verify(repository).updateStatus(eq(7L), eq(DigitalScreenStatus.PUBLISHED), any(Instant.class));
    verify(repository).updateStatus(7L, DigitalScreenStatus.DRAFT, null);
  }

  @Test
  void duplicateCopiesDefinitionThroughInsertSoRepositoryCreatesFreshDraft() {
    Map<String, Object> bindings = Map.of("metric-1", Map.of("datasetId", "12"));
    DigitalScreen source = screen(9L, "院区运营大屏", DigitalScreenStatus.PUBLISHED, Instant.now(), bindings);
    DigitalScreen copy = screen(10L, "院区运营大屏 - 副本", DigitalScreenStatus.DRAFT, null, bindings);
    when(repository.findById(9L)).thenReturn(Optional.of(source));
    when(repository.insert(
        "院区运营大屏 - 副本",
        source.description(),
        source.templateId(),
        source.templateVersion(),
        source.bindings())).thenReturn(copy);

    DigitalScreen result = service.duplicate(9L);

    assertThat(result.status()).isEqualTo(DigitalScreenStatus.DRAFT);
    verify(repository).insert(
        "院区运营大屏 - 副本",
        source.description(),
        source.templateId(),
        source.templateVersion(),
        source.bindings());
  }

  private DigitalScreen screen(
      long id,
      String name,
      DigitalScreenStatus status,
      Instant publishedTime) {
    return screen(id, name, status, publishedTime, Map.of());
  }

  private DigitalScreen screen(
      long id,
      String name,
      DigitalScreenStatus status,
      Instant publishedTime,
      Map<String, Object> bindings) {
    Instant now = Instant.parse("2026-08-27T00:00:00Z");
    return new DigitalScreen(
        id,
        name,
        "大屏说明",
        "operation-center",
        1,
        status,
        bindings,
        publishedTime,
        now,
        now);
  }
}
