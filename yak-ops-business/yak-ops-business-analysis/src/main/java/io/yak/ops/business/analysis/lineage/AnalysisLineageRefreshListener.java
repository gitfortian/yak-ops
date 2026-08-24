package io.yak.ops.business.analysis.lineage;

import io.yak.ops.business.analysis.definition.AnalysisChangedEvent;
import io.yak.ops.business.analysis.definition.AnalysisReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Keeps derived Analysis lineage aligned with committed Analysis definition mutations. */
@Component
public class AnalysisLineageRefreshListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisLineageRefreshListener.class);

  private final AnalysisReader reader;
  private final AnalysisLineageSynchronizer lineage;

  public AnalysisLineageRefreshListener(
      AnalysisReader reader,
      AnalysisLineageSynchronizer lineage) {
    this.reader = reader;
    this.lineage = lineage;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void refresh(AnalysisChangedEvent event) {
    if (event == null || event.analysisId() <= 0L) return;
    try {
      if (event.deleted()) lineage.clear(event.analysisId());
      else lineage.syncCurrent(reader.require(event.analysisId()));
    } catch (RuntimeException exception) {
      LOGGER.warn(
          "Analysis lineage refresh failed after commit for analysis {}: {}",
          event.analysisId(),
          exception.getMessage(),
          exception);
    }
  }
}
