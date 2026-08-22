package io.yak.ops.business.sync.realtime.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.sync.realtime.dao.ComputeEnvironmentDao;
import io.yak.ops.business.sync.realtime.dao.mapper.ComputeEnvironmentMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobDefinitionMapper;
import io.yak.ops.business.sync.realtime.dao.model.ComputeEnvironmentPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDefinitionPO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@DependsOn("realtimeSyncFlyway")
public class ComputeEnvironmentDaoImpl implements ComputeEnvironmentDao {

  private final ComputeEnvironmentMapper environmentMapper;
  private final RealtimeJobDefinitionMapper definitionMapper;

  public ComputeEnvironmentDaoImpl(
      ComputeEnvironmentMapper environmentMapper,
      RealtimeJobDefinitionMapper definitionMapper) {
    this.environmentMapper = environmentMapper;
    this.definitionMapper = definitionMapper;
  }

  @Override
  public List<ComputeEnvironmentPO> list() {
    return environmentMapper.selectList(
        Wrappers.<ComputeEnvironmentPO>lambdaQuery()
            .orderByDesc(ComputeEnvironmentPO::getIsDefault)
            .orderByDesc(ComputeEnvironmentPO::getEnabled)
            .orderByDesc(ComputeEnvironmentPO::getUpdateTime)
            .orderByDesc(ComputeEnvironmentPO::getId));
  }

  @Override
  public Optional<ComputeEnvironmentPO> find(long id) {
    return Optional.ofNullable(environmentMapper.selectById(id));
  }

  @Override
  public Optional<ComputeEnvironmentPO> defaultEnvironment() {
    return environmentMapper.selectList(
            Wrappers.<ComputeEnvironmentPO>lambdaQuery()
                .eq(ComputeEnvironmentPO::getIsDefault, true)
                .eq(ComputeEnvironmentPO::getEnabled, true)
                .orderByAsc(ComputeEnvironmentPO::getId)
                .last("LIMIT 1"))
        .stream()
        .findFirst();
  }

  @Override
  public long insert(ComputeEnvironmentPO environment) {
    environmentMapper.insert(environment);
    if (environment.getId() == null) throw new IllegalStateException("新增运行环境未返回主键");
    return environment.getId();
  }

  @Override
  public int update(long id, String name, String submitterType, String configJson, boolean enabled) {
    return environmentMapper.update(
        null,
        Wrappers.<ComputeEnvironmentPO>lambdaUpdate()
            .eq(ComputeEnvironmentPO::getId, id)
            .set(ComputeEnvironmentPO::getName, name)
            .set(ComputeEnvironmentPO::getSubmitterType, submitterType)
            .set(ComputeEnvironmentPO::getConfigJson, configJson)
            .set(ComputeEnvironmentPO::getEnabled, enabled)
            .setSql("version = version + 1")
            .set(ComputeEnvironmentPO::getLastCheckStatus, null)
            .set(ComputeEnvironmentPO::getLastCheckMessage, null)
            .set(ComputeEnvironmentPO::getLastCheckTime, null));
  }

  @Override
  public int setEnabled(long id, boolean enabled) {
    return environmentMapper.update(
        null,
        Wrappers.<ComputeEnvironmentPO>lambdaUpdate()
            .eq(ComputeEnvironmentPO::getId, id)
            .set(ComputeEnvironmentPO::getEnabled, enabled)
            .setSql("version = version + 1"));
  }

  @Override
  public int saveDiagnosis(long id, String status, String message, LocalDateTime checkedAt) {
    return environmentMapper.update(
        null,
        Wrappers.<ComputeEnvironmentPO>lambdaUpdate()
            .eq(ComputeEnvironmentPO::getId, id)
            .set(ComputeEnvironmentPO::getLastCheckStatus, status)
            .set(ComputeEnvironmentPO::getLastCheckMessage, message)
            .set(ComputeEnvironmentPO::getLastCheckTime, checkedAt));
  }

  @Override
  public void clearDefault() {
    environmentMapper.update(
        null,
        Wrappers.<ComputeEnvironmentPO>lambdaUpdate()
            .eq(ComputeEnvironmentPO::getIsDefault, true)
            .set(ComputeEnvironmentPO::getIsDefault, false));
  }

  @Override
  public int setDefault(long id) {
    return environmentMapper.update(
        null,
        Wrappers.<ComputeEnvironmentPO>lambdaUpdate()
            .eq(ComputeEnvironmentPO::getId, id)
            .eq(ComputeEnvironmentPO::getEnabled, true)
            .set(ComputeEnvironmentPO::getIsDefault, true)
            .setSql("version = version + 1"));
  }

  @Override
  public int delete(long id) {
    return environmentMapper.delete(
        Wrappers.<ComputeEnvironmentPO>lambdaQuery()
            .eq(ComputeEnvironmentPO::getId, id)
            .eq(ComputeEnvironmentPO::getIsDefault, false));
  }

  @Override
  public boolean hasBoundRealtimeJobs(long id) {
    return definitionMapper.selectCount(
            Wrappers.<RealtimeJobDefinitionPO>lambdaQuery()
                .eq(RealtimeJobDefinitionPO::getRuntimeEnvironmentId, id)) > 0;
  }

  @Override
  public boolean hasActiveRealtimeJobs() {
    return definitionMapper.selectCount(
            Wrappers.<RealtimeJobDefinitionPO>lambdaQuery()
                .and(q -> q.eq(RealtimeJobDefinitionPO::getDesiredState, "RUNNING")
                    .or()
                    .notIn(RealtimeJobDefinitionPO::getObservedState, List.of("STOPPED", "FAILED")))) > 0;
  }
}
