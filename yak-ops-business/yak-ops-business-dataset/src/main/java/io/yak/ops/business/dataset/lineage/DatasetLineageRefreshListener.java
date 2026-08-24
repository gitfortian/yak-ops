package io.yak.ops.business.dataset.lineage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Refreshes derived Dataset lineage only after the Dataset business transaction has committed. */
@Component
public class DatasetLineageRefreshListener {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DatasetLineageRefreshListener.class);

  private final DatasetLineageSnapshotReader snapshotReader;
  private final DatasetLineageTransactionRunner transactionRunner;

  public DatasetLineageRefreshListener(
      DatasetLineageSnapshotReader snapshotReader,
      DatasetLineageTransactionRunner transactionRunner) {
    this.snapshotReader = snapshotReader;
    this.transactionRunner = transactionRunner;
  }

  @TransactionalEventListener(
      phase = TransactionPhase.AFTER_COMMIT,
      fallbackExecution = true)
  public void refresh(DatasetLineageRefreshRequested event) {
    if (event == null || event.datasetId() <= 0L) {
      return;
    }
    try {
      transactionRunner.sync(snapshotReader.require(event.datasetId()));
    } catch (RuntimeException exception) {
      LOGGER.warn(
          "Dataset lineage refresh failed after commit for dataset {}: {}",
          event.datasetId(),
          exception.getMessage(),
          exception);
    }
  }
}
