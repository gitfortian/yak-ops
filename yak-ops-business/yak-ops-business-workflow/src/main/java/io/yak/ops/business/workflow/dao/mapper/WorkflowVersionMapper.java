package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowVersionPO;
import org.apache.ibatis.annotations.Param;

/** 工作流版本 Mapper。 */
public interface WorkflowVersionMapper extends BaseMapper<WorkflowVersionPO> {
  int initializeEngineDefinition(
      @Param("id") String id,
      @Param("engineDefinitionJson") String engineDefinitionJson);

  int initializeRuntimeMetadata(
      @Param("id") String id,
      @Param("runtimeMetadataJson") String runtimeMetadataJson);
}
