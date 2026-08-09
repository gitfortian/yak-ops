package io.yak.ops.business.sync.offline.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 离线同步任务定义领域模型。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineJobDefinition {
  private Long id;
  private String jobName;
  private String jobDesc;
  private String mode;
  private String definitionJson;
  private String jobSpecJson;
  private String configDigest;
  private String releaseState;
  private String sourceType;
  private String sinkType;
  private Long sourceDatasourceId;
  private Long sinkDatasourceId;
  private String sourceDatasourceName;
  private String sinkDatasourceName;
  private String sourceTable;
  private String sinkTable;
  private String scheduleJson;
  private Boolean scheduleEnabled;
  private String cronExpression;
  private Integer retryMaxAttempts;
  private Integer retryBackoffSeconds;
  private LocalDateTime scheduleLastFireTime;
  private LocalDateTime scheduleNextFireTime;
  private Integer version;
  private Long lastExecutionId;
  private String lastEngineJobId;
  private String lastJobStatus;
  private String lastErrorMessage;
  private Long lastDurationMillis;
  private Long lastReadRowCount;
  private Double lastQps;
  private Long lastSyncBytes;
  private LocalDateTime lastStartTime;
  private LocalDateTime lastEndTime;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
