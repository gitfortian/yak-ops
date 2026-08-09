package io.yak.ops.common.bean.po.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 工作流节点尝试持久化对象。 */
@Data
@TableName("yak_workflow_node_attempt")
public class WorkflowNodeAttemptPO {
  @TableId(type = IdType.INPUT)
  private String id;
  private String nodeExecutionId;
  private String workflowExecutionId;
  private String nodeId;
  private Integer attemptNo;
  private LocalDateTime availableAt;
  private String status;
  private String resumeTargetStatus;
  private LocalDateTime startedAt;
  private LocalDateTime pausedAt;
  private Long pausedDurationMs;
  private LocalDateTime endedAt;
  private String errorMessage;
  private String failureReason;
  private String externalExecutionId;
}
