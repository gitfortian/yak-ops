package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 工作流调度 Trigger Ledger Mapper。 */
public interface WorkflowScheduleTriggerMapper extends BaseMapper<WorkflowScheduleTriggerPO> {

  @Insert("""
      INSERT IGNORE INTO yak_workflow_schedule_trigger
        (id, schedule_id, workflow_id, trigger_id, trigger_source,
         planned_fire_time, actual_fire_time, execution_strategy, misfire_strategy,
         status, workflow_execution_id, execution_status, message, error_message,
         launched_at, completed_at, create_time, update_time)
      VALUES
        (#{id}, #{scheduleId}, #{workflowId}, #{triggerId}, #{triggerSource},
         #{plannedFireTime}, #{actualFireTime}, #{executionStrategy}, #{misfireStrategy},
         #{status}, #{workflowExecutionId}, #{executionStatus}, #{message}, #{errorMessage},
         #{launchedAt}, #{completedAt}, #{createTime}, #{updateTime})
      """)
  int insertIgnore(WorkflowScheduleTriggerPO trigger);

  @Select("SELECT id FROM yak_workflow_definition WHERE id = #{workflowId} FOR UPDATE")
  String lockWorkflow(@Param("workflowId") String workflowId);

  @Select("""
      SELECT COUNT(*)
      FROM yak_workflow_execution e
      INNER JOIN yak_workflow_version v ON v.id = e.definition_id
      WHERE v.workflow_id = #{workflowId}
        AND e.status IN ('CREATED', 'RUNNING', 'PAUSING', 'PAUSED', 'RESUMING')
      """)
  long countActiveExecutions(@Param("workflowId") String workflowId);

  @Select("""
      SELECT v.workflow_id
      FROM yak_workflow_execution e
      INNER JOIN yak_workflow_version v ON v.id = e.definition_id
      WHERE e.id = #{executionId}
      LIMIT 1
      """)
  String selectWorkflowIdByExecution(@Param("executionId") String executionId);

  @Select("""
      SELECT e.id
      FROM yak_workflow_execution e
      WHERE e.runtime_metadata_json IS NOT NULL
        AND JSON_VALID(e.runtime_metadata_json)
        AND JSON_UNQUOTE(JSON_EXTRACT(e.runtime_metadata_json, '$.triggerId')) = #{triggerId}
      ORDER BY e.created_at DESC
      LIMIT 1
      """)
  String selectExecutionIdByTrigger(@Param("triggerId") String triggerId);
}
