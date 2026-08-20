package io.yak.ops.business.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Keeps reusable Chart lineage aligned with committed Analysis mutations. */
@Component
class AnalysisLineageRefreshListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisLineageRefreshListener.class);

  private final AnalysisService analysisService;
  private final AnalysisLineageService lineageService;

  AnalysisLineageRefreshListener(
      AnalysisService analysisService,
      AnalysisLineageService lineageService) {
    this.analysisService = analysisService;
    this.lineageService = lineageService;
  }

  @TransactionalEventListener(
      phase = TransactionPhase.AFTER_COMMIT,
      fallbackExecution = true)
  public void refresh(AnalysisLineageRefreshRequested event) {
    if (event == null || event.analysisId() <= 0L) return;
    try {
      if (event.deleted()) {
        lineageService.clear(event.analysisId());
      } else {
        lineageService.syncCurrent(analysisService.get(event.analysisId()));
      }
    } catch (RuntimeException exception) {
      LOGGER.warn(
          "Analysis lineage refresh failed after commit for analysis {}: {}",
          event.analysisId(),
          exception.getMessage(),
          exception);
    }
  }
}

record AnalysisLineageRefreshRequested(long analysisId, boolean deleted) {
  AnalysisLineageRefreshRequested {
    if (analysisId <= 0L) throw new IllegalArgumentException("analysisId 必须大于 0");
  }

  static AnalysisLineageRefreshRequested refresh(long analysisId) {
    return new AnalysisLineageRefreshRequested(analysisId, false);
  }

  static AnalysisLineageRefreshRequested deleted(long analysisId) {
    return new AnalysisLineageRefreshRequested(analysisId, true);
  }
}
