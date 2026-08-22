package io.yak.ops.business.dataset;

import io.yak.ops.business.dataset.service.event.DatasetLineageRefreshRequested;
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
      LOGGER.warn(
          "Dataset lineage refresh failed after commit for dataset {}: {}",
          event.datasetId(),
          exception.getMessage(),
          exception);
    }
  }
}
