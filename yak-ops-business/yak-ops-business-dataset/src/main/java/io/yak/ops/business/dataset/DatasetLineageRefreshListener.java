package io.yak.ops.business.dataset;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Refreshes derived Dataset lineage only after the Dataset business transaction has committed. */
@Component
class DatasetLineageRefreshListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(DatasetLineageRefreshListener.class);

  private final DatasetService datasetService;
  private final Consumer<DatasetDetail> syncOperation;

  @Autowired
  DatasetLineageRefreshListener(
      DatasetService datasetService,
      DatasetLineageTransactionRunner transactionRunner) {
    this(datasetService, transactionRunner::sync);
  }

  /** Keeps focused tests source-compatible while production uses the REQUIRES_NEW runner. */
  DatasetLineageRefreshListener(
      DatasetService datasetService,
      DatasetLineageService lineageService) {
    this(datasetService, lineageService::syncCurrent);
  }

  private DatasetLineageRefreshListener(
      DatasetService datasetService,
      Consumer<DatasetDetail> syncOperation) {
    this.datasetService = datasetService;
    this.syncOperation = syncOperation;
  }

  @TransactionalEventListener(
      phase = TransactionPhase.AFTER_COMMIT,
      fallbackExecution = true)
  public void refresh(DatasetLineageRefreshRequested event) {
    if (event == null || event.datasetId() <= 0L) return;
    try {
      syncOperation.accept(datasetService.get(event.datasetId()));
    } catch (RuntimeException exception) {
      // Lineage is derived metadata. A refresh failure must never turn a committed Dataset publish
      // into an apparent business failure for the caller.
      LOGGER.warn(
          "Dataset lineage refresh failed after commit for dataset {}: {}",
          event.datasetId(),
          exception.getMessage(),
          exception);
    }
  }
}

record DatasetLineageRefreshRequested(long datasetId) {
  DatasetLineageRefreshRequested {
    if (datasetId <= 0L) throw new IllegalArgumentException("datasetId 必须大于 0");
  }
}
