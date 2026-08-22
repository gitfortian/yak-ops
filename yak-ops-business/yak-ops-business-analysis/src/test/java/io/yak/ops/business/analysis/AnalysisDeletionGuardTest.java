package io.yak.ops.business.analysis;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.analysis.repository.AnalysisRepository;
import io.yak.ops.business.analysis.service.support.AnalysisDefinitionNormalizer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AnalysisDeletionGuardTest {

  @Test
  void blocksDeleteWhenCrossDomainGuardRejectsReference() {
    AnalysisRepository repository = mock(AnalysisRepository.class);
    AnalysisDeletionGuard guard = mock(AnalysisDeletionGuard.class);
    when(repository.findById(7L)).thenReturn(Optional.of(mock(AnalysisAsset.class)));
    doThrow(new IllegalStateException("still referenced"))
        .when(guard)
        .requireDeletable(7L);

    AnalysisService service = new AnalysisService(
        repository,
        mock(AnalysisDefinitionNormalizer.class),
        mock(ApplicationEventPublisher.class),
        List.of(guard));

    assertThrows(IllegalStateException.class, () -> service.delete(7L));
    verify(repository, never()).delete(7L);
  }
}
