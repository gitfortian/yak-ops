package io.yak.ops.business.analysis;

import io.yak.ops.business.analysis.repository.AnalysisRepository;
import io.yak.ops.business.analysis.service.event.AnalysisLineageRefreshRequested;
import io.yak.ops.business.analysis.service.support.AnalysisDefinitionNormalizer;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns reusable Analysis assets while Dataset execution and Dashboard layout remain separate. */
@Service
public class AnalysisService {

  private final AnalysisRepository repository;
  private final AnalysisDefinitionNormalizer normalizer;
  private final ApplicationEventPublisher eventPublisher;
  private final List<AnalysisDeletionGuard> deletionGuards;

  @Autowired
  public AnalysisService(
      AnalysisRepository repository,
      AnalysisDefinitionNormalizer normalizer,
      ApplicationEventPublisher eventPublisher,
      List<AnalysisDeletionGuard> deletionGuards) {
    this.repository = repository;
    this.normalizer = normalizer;
    this.eventPublisher = eventPublisher;
    this.deletionGuards = deletionGuards == null ? List.of() : List.copyOf(deletionGuards);
  }

  /** Focused tests and callers without cross-domain deletion guards retain the lightweight path. */
  public AnalysisService(
      AnalysisRepository repository,
      AnalysisDefinitionNormalizer normalizer,
      ApplicationEventPublisher eventPublisher) {
    this(repository, normalizer, eventPublisher, List.of());
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public List<AnalysisAsset> list() {
    return repository.list();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public AnalysisAsset get(long analysisId) {
    requireAnalysisId(analysisId);
    return repository.findById(analysisId)
        .orElseThrow(() -> new IllegalArgumentException("Analysis 不存在：" + analysisId));
  }

  @Transactional("yakBusinessTransactionManager")
  public AnalysisAsset create(SaveCommand command) {
    AnalysisDraft draft = normalizer.normalize(command);
    long analysisId = repository.insert(draft);
    eventPublisher.publishEvent(AnalysisLineageRefreshRequested.refresh(analysisId));
    return get(analysisId);
  }

  @Transactional("yakBusinessTransactionManager")
  public AnalysisAsset update(long analysisId, SaveCommand command) {
    requireAnalysisId(analysisId);
    get(analysisId);
    AnalysisDraft draft = normalizer.normalize(command);
    repository.update(analysisId, draft);
    eventPublisher.publishEvent(AnalysisLineageRefreshRequested.refresh(analysisId));
    return get(analysisId);
  }

  @Transactional("yakBusinessTransactionManager")
  public void delete(long analysisId) {
    requireAnalysisId(analysisId);
    get(analysisId);
    deletionGuards.forEach(guard -> guard.requireDeletable(analysisId));
    repository.delete(analysisId);
    eventPublisher.publishEvent(AnalysisLineageRefreshRequested.deleted(analysisId));
  }

  private static void requireAnalysisId(long analysisId) {
    if (analysisId <= 0L) throw new IllegalArgumentException("analysisId 必须大于 0");
  }

  /** Stable application command retained for callers outside the HTTP adapter. */
  public record SaveCommand(
      String name,
      String description,
      long datasetId,
      AnalysisChartType chartType,
      AnalysisQuerySpec querySpec,
      AnalysisVisualConfig visualConfig) {
  }
}
