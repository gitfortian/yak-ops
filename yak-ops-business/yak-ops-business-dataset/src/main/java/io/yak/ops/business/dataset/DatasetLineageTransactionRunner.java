package io.yak.ops.business.dataset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Starts an independent transaction for derived Dataset lineage after the business commit. */
@Service
public class DatasetLineageTransactionRunner {

  private final DatasetLineageService lineageService;

  public DatasetLineageTransactionRunner(DatasetLineageService lineageService) {
    this.lineageService = lineageService;
  }

  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      propagation = Propagation.REQUIRES_NEW)
  public void sync(DatasetDetail detail) {
    lineageService.syncCurrent(detail);
  }
}
