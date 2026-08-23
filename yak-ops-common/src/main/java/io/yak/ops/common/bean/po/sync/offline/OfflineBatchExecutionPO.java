package io.yak.ops.common.bean.po.sync.offline;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/** 离线同步业务批次持久化模型。 */
@Data
@TableName("yak_offline_batch_execution")
public class OfflineBatchExecutionPO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long jobDefinitionId;
  private String batchKey;
  private String triggerType;
  private String batchScopeType;

  @ToString.Exclude
  private String batchScopeValue;

  private String batchScopeFingerprint;

  @ToString.Exclude
  private String definitionSnapshotJson;

  private Integer definitionRevision;
  private Integer retryMaxAttempts;
  private Integer retryBackoffSeconds;
  private String configDigest;

  /** Wave 5 后由 Batch Snapshot 持有的不含凭据逻辑 JobSpec；旧 Batch 迁移期允许为空。 */
  @ToString.Exclude
  private String logicalJobSpecJson;

  private String status;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
