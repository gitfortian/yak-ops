package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;

/** 工作流定义 Mapper。 */
public interface WorkflowDefinitionMapper extends BaseMapper<WorkflowDefinitionPO> {
  int upsert(WorkflowDefinitionPO definition);
}
