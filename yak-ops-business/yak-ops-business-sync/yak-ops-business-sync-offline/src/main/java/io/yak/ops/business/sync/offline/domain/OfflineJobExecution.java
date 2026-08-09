package io.yak.ops.business.sync.offline.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 离线同步执行实例领域模型。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineJobExecution {
  private Long id;
  private Long jobDefinitionId;
  private Integer definitionVersion;
  private String engineBaseUrl;
  private String engineJobId;
  private String externalExecutionId;
  private String idempotencyKey;
  private String workerInstanceId;
  private String status;
  private Long stateVersion;
  private Integer attemptNo;
  private String triggerType;
  private Long retryFromExecutionId;
  private Boolean cancellationRequested;
  private Boolean retryCreated;
  private LocalDateTime nextRetryTime;
  private String configDigest;
  private String definitionSnapshotJson;
  private String submittedConfig;
  private String engineSnapshotJson;
  private String errorMessage;
  private Long sourceRecordCount;
  private Long sinkAttemptedRecordCount;
  private Long sinkSuccessRecordCount;
  private Long sinkCommittedRecordCount;
  private Long sourceReadBytes;
  private Long sinkWrittenBytes;
  private Double sourceAverageQps;
  private Double sinkAverageQps;
  private Long failedRecordCount;
  private Long skippedRecordCount;
  private Long databaseCommitMillis;
  private Long sqlExecutionMillis;
  private Double qps;
  private Long durationMillis;
  private LocalDateTime createTime;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private LocalDateTime lastSyncTime;
  private LocalDateTime updateTime;
}
