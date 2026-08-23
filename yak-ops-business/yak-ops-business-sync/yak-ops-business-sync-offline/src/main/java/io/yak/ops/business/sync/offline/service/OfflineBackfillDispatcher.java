package io.yak.ops.business.sync.offline.service;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.domain.OfflineSyncCursor;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Wave 5 Backfill queue dispatcher；PENDING Batch 按 V1 单 Task execution slot 串行提交。 */
@ConditionalOnOfflineSyncEnabled
@Component
@RequiredArgsConstructor
public class OfflineBackfillDispatcher {

  private static final Logger LOG = LoggerFactory.getLogger(OfflineBackfillDispatcher.class);

  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineBatchRuntimeService batchRuntimeService;
  private final OfflineCursorService cursorService;
  private final OfflineExecutionOrchestrator orchestrator;
  private final OfflineSyncProperties properties;

  @Scheduled(
      initialDelayString = "${yak.sync.offline.control.reconcile-delay-millis:5000}",
      fixedDelayString = "${yak.sync.offline.control.reconcile-delay-millis:5000}")
  public void dispatch() {
    int limit = Math.max(1, properties.getControl().getScanBatchSize());
    List<BatchExecution> pending = batchRepository.findPendingBackfills(limit);
    for (BatchExecution batch : pending) {
      try {
        if (batchRuntimeService.hasOccupyingBatch(batch.taskId())) continue;
        if (!cursorReady(batch)) continue;
        orchestrator.executePendingBackfill(batch.id());
      } catch (RuntimeException exception) {
        LOG.warn("Offline backfill dispatch failed, batchId={}", batch.id(), exception);
      }
    }
  }

  private boolean cursorReady(BatchExecution batch) {
    if (!(batch.batchScope() instanceof BatchScope.CursorRange range)) return true;
    OfflineSyncCursor cursor = cursorService.find(batch.taskId(), range.cursorId()).orElse(null);
    return cursor != null && cursor.position().equals(range.afterExclusive());
  }
}
