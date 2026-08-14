package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 工作流执行实例 Mapper。 */
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecutionPO> {
  int upsert(WorkflowExecutionPO execution);

  List<String> selectExecutionIds();

  List<String> selectRecoverableExecutionIds();

  long countActiveExecutions(@Param("workflowId") String workflowId);

  String selectEffectiveRuntimeMetadata(@Param("executionId") String executionId);
}
