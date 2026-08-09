package io.yak.ops.business.workflow.dao;

import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowVersionPO;
import java.util.List;

/** 工作流定义与版本数据访问接口。 */
public interface WorkflowCatalogDao {
  List<WorkflowDefinitionPO> selectDefinitions();

  List<WorkflowVersionPO> selectPublishedVersions(String workflowId);

  WorkflowVersionPO selectVersionById(String versionId);

  int upsertDefinition(WorkflowDefinitionPO definition);

  int insertVersion(WorkflowVersionPO version);

  int deleteDefinition(String workflowId);

  int initializeEngineDefinition(String versionId, String engineDefinitionJson);

  int initializeRuntimeMetadata(String versionId, String runtimeMetadataJson);
}
