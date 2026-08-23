package io.yak.ops.business.sync.offline.domain.compat;

import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.AttemptMetrics;
import io.yak.ops.business.sync.offline.domain.core.AttemptReason;
import io.yak.ops.business.sync.offline.domain.core.AttemptStatus;
import io.yak.ops.business.sync.offline.domain.core.EngineExecutionRef;
import io.yak.ops.business.sync.offline.domain.core.ExecutionAttempt;
import java.util.Locale;
import java.util.Objects;

/**
 * Transitional mapper from the legacy execution persistence view to ExecutionAttempt.
 *
 * <p>Wave 6 contract：本 mapper 只允许映射 Attempt evidence。BatchExecution / BatchScope /
 * ExecutionSnapshot 必须来自 Batch persistence，禁止再从 Attempt 重建冻结运行真相。
 */
public final class LegacyOfflineExecutionCompatibilityMapper {

  private LegacyOfflineExecutionCompatibilityMapper() {}

  public static ExecutionAttempt toAttempt(OfflineJobExecution source) {
    Objects.requireNonNull(source, "legacy execution 不能为空");
    return new ExecutionAttempt(
        source.getId(),
        positive(source.getAttemptNo(), "attemptNo"),
        reason(source),
        requireText(source.getIdempotencyKey(), "idempotencyKey 不能为空"),
        requireText(source.getExternalExecutionId(), "externalExecutionId 不能为空"),
        status(source.getStatus()),
        engineRef(source),
        metrics(source),
        source.getErrorMessage(),
        Objects.requireNonNull(source.getCreateTime(), "createTime 不能为空"),
        source.getStartTime(),
        source.getEndTime());
  }

  static AttemptStatus status(String value) {
    if (value == null || value.trim().isEmpty()) return AttemptStatus.CREATED;
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "CREATED" -> AttemptStatus.CREATED;
      case "SUBMITTING" -> AttemptStatus.SUBMITTING;
      case "SUBMITTED" -> AttemptStatus.SUBMITTED;
      case "QUEUED" -> AttemptStatus.QUEUED;
      case "RUNNING" -> AttemptStatus.RUNNING;
      case "SUCCEEDED", "FINISHED", "COMPLETED" -> AttemptStatus.SUCCEEDED;
      case "FAILED" -> AttemptStatus.FAILED;
      case "CANCELED", "CANCELLED" -> AttemptStatus.CANCELED;
      case "CANCELING", "CANCELLING" -> AttemptStatus.CANCELING;
      case "UNKNOWN", "LOST" -> AttemptStatus.UNKNOWN;
      default -> AttemptStatus.UNKNOWN;
    };
  }

  private static AttemptReason reason(OfflineJobExecution source) {
    return value(source.getAttemptNo(), 1) > 1
            || source.getRetryFromExecutionId() != null
            || "RETRY".equalsIgnoreCase(source.getTriggerType())
        ? AttemptReason.RETRY
        : AttemptReason.INITIAL;
  }

  private static EngineExecutionRef engineRef(OfflineJobExecution source) {
    String jobId = trim(source.getEngineJobId());
    String workerId = trim(source.getWorkerInstanceId());
    return jobId == null && workerId == null ? null : new EngineExecutionRef(jobId, workerId);
  }

  private static AttemptMetrics metrics(OfflineJobExecution source) {
    return new AttemptMetrics(
        value(source.getSourceRecordCount(), 0L),
        value(source.getSinkAttemptedRecordCount(), 0L),
        value(source.getSinkSuccessRecordCount(), 0L),
        value(source.getSinkCommittedRecordCount(), 0L),
        value(source.getSourceReadBytes(), 0L),
        value(source.getSinkWrittenBytes(), 0L),
        value(source.getSourceAverageQps(), 0D),
        value(source.getSinkAverageQps(), 0D),
        value(source.getFailedRecordCount(), 0L),
        value(source.getSkippedRecordCount(), 0L),
        value(source.getDatabaseCommitMillis(), 0L),
        value(source.getSqlExecutionMillis(), 0L),
        value(source.getQps(), 0D),
        value(source.getDurationMillis(), 0L));
  }

  private static int positive(Integer value, String field) {
    if (value == null || value < 1) throw new IllegalArgumentException(field + " 必须大于 0");
    return value;
  }

  private static int value(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private static long value(Long value, long fallback) {
    return value == null ? fallback : value;
  }

  private static double value(Double value, double fallback) {
    return value == null ? fallback : value;
  }

  private static String trim(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }

  private static String requireText(String value, String message) {
    String normalized = trim(value);
    if (normalized == null) throw new IllegalArgumentException(message);
    return normalized;
  }
}
