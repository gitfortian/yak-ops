package io.yak.ops.business.sync.offline.backfill;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.cursor.OfflineCursorGateway;
import io.yak.ops.business.sync.offline.domain.OfflineSyncCursor;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.execution.OfflineJobExecutionService;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispatches PENDING Backfill batches only when the Task slot and Cursor range are ready. */
@ConditionalOnOfflineSyncEnabled
@Component
@RequiredArgsConstructor
public class OfflineBackfillDispatcher {

  private static final Logger LOG = LoggerFactory.getLogger(OfflineBackfillDispatcher.class);

  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineCursorGateway cursorGateway;
  private final OfflineJobExecutionService executionService;
  private final OfflineSyncProperties properties;

  @Scheduled(
      initialDelayString = "${yak.sync.offline.control.reconcile-delay-millis:5000}",
      fixedDelayString = "${yak.sync.offline.control.reconcile-delay-millis:5000}")
  public void dispatch() {
    int limit = Math.max(1, properties.getControl().getScanBatchSize());
    List<BatchExecution> pending = batchRepository.findPendingBackfills(limit);
    for (BatchExecution batch : pending) {
      try {
        if (!readyToDispatch(batch)) {
          continue;
        }
        executionService.executePendingBackfill(batch.id());
      } catch (RuntimeException exception) {
        LOG.warn("Offline backfill dispatch failed, batchId={}", batch.id(), exception);
      }
    }
  }

  private boolean readyToDispatch(BatchExecution batch) {
    return !executionService.hasOccupyingBatch(batch.taskId()) && cursorReady(batch);
  }

  private boolean cursorReady(BatchExecution batch) {
    if (!(batch.batchScope() instanceof BatchScope.CursorRange range)) {
      return true;
    }
    OfflineSyncCursor cursor =
        cursorGateway.find(batch.taskId(), range.cursorId()).orElse(null);
    return cursor != null && cursor.position().equals(range.afterExclusive());
  }
}
