package io.yak.ops.business.workflow.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.workflow.dao.WorkflowCatalogDao;
import io.yak.ops.business.workflow.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowVersionMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowVersionPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** 基于 MyBatis-Plus 的工作流定义与版本 DAO。 */
@Repository
@RequiredArgsConstructor
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowCatalogDaoImpl implements WorkflowCatalogDao {
  private final WorkflowDefinitionMapper definitionMapper;
  private final WorkflowVersionMapper versionMapper;

  @Override
  public List<WorkflowDefinitionPO> selectDefinitions() {
    return definitionMapper.selectList(
        Wrappers.<WorkflowDefinitionPO>lambdaQuery()
            .orderByDesc(WorkflowDefinitionPO::getUpdateTime));
  }

  @Override
  public List<WorkflowVersionPO> selectPublishedVersions(String workflowId) {
    return versionMapper.selectList(
        Wrappers.<WorkflowVersionPO>lambdaQuery()
            .eq(WorkflowVersionPO::getWorkflowId, workflowId)
            .eq(WorkflowVersionPO::getVersionKind, "PUBLISHED")
            .orderByAsc(WorkflowVersionPO::getVersionNo));
  }

  @Override
  public WorkflowVersionPO selectVersionById(String versionId) {
    return versionMapper.selectById(versionId);
  }

  @Override
  public int upsertDefinition(WorkflowDefinitionPO definition) {
    return definitionMapper.upsert(definition);
  }

  @Override
  public int insertVersion(WorkflowVersionPO version) {
    return versionMapper.insert(version);
  }

  @Override
  public int deleteDefinition(String workflowId) {
    return definitionMapper.deleteById(workflowId);
  }

  @Override
  public int initializeEngineDefinition(String versionId, String engineDefinitionJson) {
    return versionMapper.initializeEngineDefinition(versionId, engineDefinitionJson);
  }

  @Override
  public int initializeRuntimeMetadata(String versionId, String runtimeMetadataJson) {
    return versionMapper.initializeRuntimeMetadata(versionId, runtimeMetadataJson);
  }
}
