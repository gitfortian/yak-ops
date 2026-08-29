package io.yak.ops.business.workflow.dao;

import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeAttemptPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeExecutionPO;
import java.util.List;

/** 工作流执行聚合与运行索引数据访问接口。 */
public interface WorkflowExecutionDao {
  WorkflowExecutionPO selectExecution(String executionId);

  List<WorkflowNodeExecutionPO> selectNodeExecutions(String executionId);

  List<WorkflowNodeAttemptPO> selectNodeAttempts(String executionId);

  int upsertExecution(WorkflowExecutionPO execution);

  int upsertNodeExecution(WorkflowNodeExecutionPO nodeExecution);

  int upsertNodeAttempt(WorkflowNodeAttemptPO attempt);

  int updateExecution(WorkflowExecutionPO execution);

  List<String> selectExecutionIds();

  List<String> selectRecoverableExecutionIds();

  /** Explicit cross-Project startup dispatcher. Returned refs carry only durable identity. */
  List<ProjectExecutionRef> selectRecoverableExecutionsForDispatch();

  long countActiveExecutions(String workflowId);

  String selectEffectiveRuntimeMetadata(String executionId);

  WorkflowNodeAttemptPO selectAttempt(String attemptId);

  int bindExternalExecution(String attemptId, String externalExecutionId);

  record ProjectExecutionRef(long projectId, String executionId) {
    public ProjectExecutionRef {
      if (projectId <= 0L) throw new IllegalArgumentException("projectId must be positive");
      if (executionId == null || executionId.isBlank()) {
        throw new IllegalArgumentException("executionId must not be blank");
      }
    }
  }
}
