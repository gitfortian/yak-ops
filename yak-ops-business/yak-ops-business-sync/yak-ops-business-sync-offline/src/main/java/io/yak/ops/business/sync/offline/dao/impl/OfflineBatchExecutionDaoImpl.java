package io.yak.ops.business.sync.offline.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineBatchExecutionDao;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineBatchExecutionMapper;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobDefinitionMapper;
import io.yak.ops.common.bean.po.sync.offline.OfflineBatchExecutionPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** 基于 MyBatis-Plus 的离线同步业务批次数据访问实现。 */
@ConditionalOnOfflineSyncEnabled
@Repository
public class OfflineBatchExecutionDaoImpl implements OfflineBatchExecutionDao {

  private final OfflineBatchExecutionMapper mapper;
  private final OfflineJobDefinitionMapper definitionMapper;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public OfflineBatchExecutionDaoImpl(
      OfflineBatchExecutionMapper mapper,
      OfflineJobDefinitionMapper definitionMapper,
      CurrentProject currentProject) {
    this.mapper = mapper;
    this.definitionMapper = definitionMapper;
    this.currentProject = currentProject;
  }

  public OfflineBatchExecutionDaoImpl(OfflineBatchExecutionMapper mapper) {
    this(mapper, null, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public OfflineBatchExecutionPO selectById(Long id) {
    Long projectId = currentProjectId();
    return mapper.selectOne(
        Wrappers.<OfflineBatchExecutionPO>lambdaQuery()
            .eq(OfflineBatchExecutionPO::getId, id)
            .eq(projectId != null, OfflineBatchExecutionPO::getProjectId, projectId));
  }

  @Override
  public OfflineBatchExecutionPO selectByTaskIdAndBatchKey(Long taskId, String batchKey) {
    if (taskId == null || taskId <= 0L || !StringUtils.hasText(batchKey)) return null;
    Long projectId = currentProjectId();
    return mapper.selectOne(
        Wrappers.<OfflineBatchExecutionPO>lambdaQuery()
            .eq(projectId != null, OfflineBatchExecutionPO::getProjectId, projectId)
            .eq(OfflineBatchExecutionPO::getJobDefinitionId, taskId)
            .eq(OfflineBatchExecutionPO::getBatchKey, batchKey.trim())
            .last("LIMIT 1"));
  }

  @Override
  public boolean existsByTaskIdAndStatuses(Long taskId, List<String> statuses) {
    if (taskId == null || taskId <= 0L || statuses == null || statuses.isEmpty()) return false;
    Long projectId = currentProjectId();
    return mapper.selectCount(
        Wrappers.<OfflineBatchExecutionPO>lambdaQuery()
            .eq(projectId != null, OfflineBatchExecutionPO::getProjectId, projectId)
            .eq(OfflineBatchExecutionPO::getJobDefinitionId, taskId)
            .in(OfflineBatchExecutionPO::getStatus, statuses)) > 0L;
  }

  @Override
  public OfflineBatchExecutionPO selectLatestByTaskIdAndStatuses(
      Long taskId, List<String> statuses) {
    if (taskId == null || taskId <= 0L || statuses == null || statuses.isEmpty()) return null;
    Long projectId = currentProjectId();
    return mapper.selectOne(
        Wrappers.<OfflineBatchExecutionPO>lambdaQuery()
            .eq(projectId != null, OfflineBatchExecutionPO::getProjectId, projectId)
            .eq(OfflineBatchExecutionPO::getJobDefinitionId, taskId)
            .in(OfflineBatchExecutionPO::getStatus, statuses)
            .orderByDesc(OfflineBatchExecutionPO::getId)
            .last("LIMIT 1"));
  }

  @Override
  public List<OfflineBatchExecutionPO> selectPendingBackfills(int limit) {
    Long projectId = currentProjectId();
    return mapper.selectList(
        Wrappers.<OfflineBatchExecutionPO>lambdaQuery()
            .eq(projectId != null, OfflineBatchExecutionPO::getProjectId, projectId)
            .eq(OfflineBatchExecutionPO::getTriggerType, "BACKFILL")
            .eq(OfflineBatchExecutionPO::getStatus, "PENDING")
            .orderByAsc(OfflineBatchExecutionPO::getId)
            .last("LIMIT " + Math.max(1, limit)));
  }

  @Override
  public boolean reservePendingBackfill(Long batchId, LocalDateTime updateTime) {
    if (batchId == null || batchId <= 0L) return false;
    Long projectId = currentProjectId();
    return mapper.update(
        null,
        Wrappers.<OfflineBatchExecutionPO>lambdaUpdate()
            .eq(OfflineBatchExecutionPO::getId, batchId)
            .eq(projectId != null, OfflineBatchExecutionPO::getProjectId, projectId)
            .eq(OfflineBatchExecutionPO::getTriggerType, "BACKFILL")
            .eq(OfflineBatchExecutionPO::getStatus, "PENDING")
            .set(OfflineBatchExecutionPO::getStatus, "RUNNING")
            .set(OfflineBatchExecutionPO::getUpdateTime, updateTime)) > 0;
  }

  @Override
  public boolean insert(OfflineBatchExecutionPO batchPO) {
    batchPO.setProjectId(resolveProjectId(batchPO.getProjectId(), batchPO.getJobDefinitionId()));
    return mapper.insert(batchPO) > 0;
  }

  @Override
  public boolean updateById(OfflineBatchExecutionPO batchPO) {
    Long projectId = currentProjectId();
    if (projectId == null) return mapper.updateById(batchPO) > 0;
    batchPO.setProjectId(projectId);
    return mapper.update(
        batchPO,
        Wrappers.<OfflineBatchExecutionPO>lambdaUpdate()
            .eq(OfflineBatchExecutionPO::getId, batchPO.getId())
            .eq(OfflineBatchExecutionPO::getProjectId, projectId)) > 0;
  }

  private Long currentProjectId() {
    return currentProject.current().map(context -> context.projectId()).orElse(null);
  }

  private Long resolveProjectId(Long storedProjectId, Long definitionId) {
    Long projectId = currentProjectId();
    if (projectId != null) {
      if (storedProjectId != null && !Objects.equals(projectId, storedProjectId)) {
        throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
      }
      return projectId;
    }
    if (storedProjectId != null || definitionMapper == null || definitionId == null) return storedProjectId;
    OfflineJobDefinitionPO definition = definitionMapper.selectById(definitionId);
    return definition == null ? null : definition.getProjectId();
  }
}
