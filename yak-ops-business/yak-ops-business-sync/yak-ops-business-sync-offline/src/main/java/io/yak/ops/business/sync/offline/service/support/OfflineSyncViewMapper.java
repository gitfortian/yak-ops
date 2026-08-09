package io.yak.ops.business.sync.offline.service.support;

import io.yak.ops.business.sync.offline.domain.OfflineExecutionEvent;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.common.bean.vo.sync.offline.OfflineExecutionEventVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobDefinitionVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionVO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** 纯输出模型转换；不访问数据库、不承担业务判断。 */
@Component
public class OfflineSyncViewMapper {
  private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public OfflineJobDefinitionVO definition(OfflineJobDefinition d) {
    boolean scheduled = Boolean.TRUE.equals(d.getScheduleEnabled());
    return OfflineJobDefinitionVO.builder()
        .id(d.getId())
        .jobName(d.getJobName())
        .jobDesc(d.getJobDesc())
        .jobType("BATCH")
        .mode(d.getMode())
        .releaseState(d.getReleaseState())
        .sourceType(d.getSourceType())
        .sinkType(d.getSinkType())
        .sourceDatasourceId(d.getSourceDatasourceId())
        .sinkDatasourceId(d.getSinkDatasourceId())
        .sourceDatasourceName(d.getSourceDatasourceName())
        .sinkDatasourceName(d.getSinkDatasourceName())
        .sourceTable(d.getSourceTable())
        .sinkTable(d.getSinkTable())
        .lastJobStatus(d.getLastJobStatus())
        .lastErrorMessage(d.getLastErrorMessage())
        .instanceId(d.getLastExecutionId())
        .engineJobId(d.getLastEngineJobId())
        .runMode(scheduled ? "SCHEDULE" : "MANUAL")
        .duration(seconds(d.getLastDurationMillis()))
        .readRowCount(value(d.getLastReadRowCount()))
        .qps(value(d.getLastQps()))
        .syncSize(formatBytes(d.getLastSyncBytes()))
        .cronExpression(d.getCronExpression())
        .scheduleStatus(scheduled ? "NORMAL" : "PAUSED")
        .lastScheduleTime(format(d.getScheduleLastFireTime() == null ? d.getLastStartTime() : d.getScheduleLastFireTime()))
        .nextScheduleTime(format(d.getScheduleNextFireTime()))
        .createTime(format(d.getCreateTime()))
        .updateTime(format(d.getUpdateTime()))
        .build();
  }

  public OfflineJobExecutionVO execution(OfflineJobExecution execution) {
    return OfflineJobExecutionVO.builder()
        .id(execution.getId())
        .jobDefinitionId(execution.getJobDefinitionId())
        .definitionVersion(execution.getDefinitionVersion())
        .engineBaseUrl(execution.getEngineBaseUrl())
        .engineJobId(execution.getEngineJobId())
        .externalExecutionId(execution.getExternalExecutionId())
        .workerInstanceId(execution.getWorkerInstanceId())
        .status(execution.getStatus())
        .stateVersion(value(execution.getStateVersion()))
        .attemptNo(value(execution.getAttemptNo()))
        .triggerType(execution.getTriggerType())
        .retryFromExecutionId(execution.getRetryFromExecutionId())
        .cancellationRequested(Boolean.TRUE.equals(execution.getCancellationRequested()))
        .errorMessage(execution.getErrorMessage())
        .sourceRecordCount(value(execution.getSourceRecordCount()))
        .sinkAttemptedRecordCount(value(execution.getSinkAttemptedRecordCount()))
        .sinkSuccessRecordCount(value(execution.getSinkSuccessRecordCount()))
        .sinkCommittedRecordCount(value(execution.getSinkCommittedRecordCount()))
        .sourceReadBytes(value(execution.getSourceReadBytes()))
        .sinkWrittenBytes(value(execution.getSinkWrittenBytes()))
        .sourceAverageQps(value(execution.getSourceAverageQps()))
        .sinkAverageQps(value(execution.getSinkAverageQps()))
        .failedRecordCount(value(execution.getFailedRecordCount()))
        .skippedRecordCount(value(execution.getSkippedRecordCount()))
        .databaseCommitMillis(value(execution.getDatabaseCommitMillis()))
        .sqlExecutionMillis(value(execution.getSqlExecutionMillis()))
        .qps(value(execution.getQps()))
        .durationMillis(value(execution.getDurationMillis()))
        .createTime(format(execution.getCreateTime()))
        .startTime(format(execution.getStartTime()))
        .endTime(format(execution.getEndTime()))
        .nextRetryTime(format(execution.getNextRetryTime()))
        .lastSyncTime(format(execution.getLastSyncTime()))
        .updateTime(format(execution.getUpdateTime()))
        .build();
  }

  public OfflineExecutionEventVO event(OfflineExecutionEvent event) {
    return OfflineExecutionEventVO.builder()
        .id(event.getId())
        .executionId(event.getExecutionId())
        .stateVersion(event.getStateVersion())
        .fromStatus(event.getFromStatus())
        .toStatus(event.getToStatus())
        .eventType(event.getEventType())
        .message(event.getMessage())
        .payloadJson(event.getPayloadJson())
        .createTime(event.getCreateTime())
        .build();
  }

  private String format(LocalDateTime value) {
    return value == null ? null : value.format(FORMAT);
  }

  private long seconds(Long millis) {
    return millis == null ? 0L : Math.max(0L, millis / 1000L);
  }

  private long value(Long value) {
    return value == null ? 0L : value;
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }

  private double value(Double value) {
    return value == null ? 0D : value;
  }

  private String formatBytes(Long bytes) {
    if (bytes == null || bytes <= 0L) return "-";
    double size = bytes;
    String[] units = {"B", "KB", "MB", "GB", "TB"};
    int unit = 0;
    while (size >= 1024D && unit < units.length - 1) {
      size /= 1024D;
      unit++;
    }
    return String.format(Locale.ROOT, "%.2f %s", size, units[unit]);
  }
}
