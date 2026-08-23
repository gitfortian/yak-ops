package io.yak.ops.business.sync.offline.cursor;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineSyncCursor;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.repository.OfflineSyncCursorRepository;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Cursor 子系统内部实现；跨子系统只暴露 OfflineCursorGateway。 */
@ConditionalOnOfflineSyncEnabled
@Component
@RequiredArgsConstructor
public class OfflineCursorManager implements OfflineCursorGateway {

  private final OfflineSyncCursorRepository repository;

  @Override
  public OfflineSyncCursor initializeIfAbsent(
      long taskId, String cursorId, String sourceColumn, String initialPosition) {
    return repository.initializeIfAbsent(taskId, cursorId, sourceColumn, initialPosition);
  }

  @Override
  public Optional<OfflineSyncCursor> find(long taskId, String cursorId) {
    return repository.find(taskId, cursorId);
  }

  @Override
  public String requireSourceColumn(long taskId, String cursorId) {
    return find(taskId, cursorId)
        .orElseThrow(() -> new IllegalStateException("Cursor 尚未初始化：" + cursorId))
        .sourceColumn();
  }

  @Override
  public AdvanceResult advanceAfterSucceededBatch(BatchExecution batch) {
    Objects.requireNonNull(batch, "BatchExecution 不能为空");
    if (!(batch.batchScope() instanceof BatchScope.CursorRange range)) {
      return AdvanceResult.NOT_CURSOR_SCOPE;
    }
    if (batch.status() != BatchStatus.SUCCEEDED) return AdvanceResult.NOT_SUCCEEDED;
    if (batch.id() == null || batch.id() <= 0L) {
      throw new IllegalArgumentException("BatchExecutionId 必须大于 0");
    }

    OfflineSyncCursor current = repository.find(batch.taskId(), range.cursorId()).orElse(null);
    if (current == null) return AdvanceResult.NOT_INITIALIZED;
    if (current.position().equals(range.throughInclusive())) return AdvanceResult.ALREADY_ADVANCED;
    if (!current.position().equals(range.afterExclusive())) return AdvanceResult.STALE;

    if (repository.advance(current, range.afterExclusive(), range.throughInclusive(), batch.id())) {
      return AdvanceResult.ADVANCED;
    }
    OfflineSyncCursor reread = repository.find(batch.taskId(), range.cursorId()).orElse(null);
    if (reread != null && reread.position().equals(range.throughInclusive())) {
      return AdvanceResult.ALREADY_ADVANCED;
    }
    return AdvanceResult.STALE;
  }
}
