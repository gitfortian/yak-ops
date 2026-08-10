package io.yak.ops.business.development.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.yak.ops.business.development.dao.mapper.SqlTaskExecutionMapper;
import io.yak.ops.business.development.dao.mapper.SqlTaskMapper;
import io.yak.ops.business.development.dao.mapper.SqlTaskVersionMapper;
import io.yak.ops.business.development.domain.SqlDevelopmentModel.Definition;
import io.yak.ops.business.development.domain.SqlDevelopmentModel.Execution;
import io.yak.ops.business.development.domain.SqlDevelopmentModel.Version;
import io.yak.ops.business.development.domain.SqlParameterDefinition;
import io.yak.ops.business.development.support.SqlDevelopmentJsonCodec;
import io.yak.ops.common.bean.po.development.SqlTaskExecutionPO;
import io.yak.ops.common.bean.po.development.SqlTaskPO;
import io.yak.ops.common.bean.po.development.SqlTaskVersionPO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for SQL development metadata and execution state. */
@Repository
public class SqlDevelopmentRepositoryAdapter implements SqlDevelopmentRepository {

  private final SqlTaskMapper taskMapper;
  private final SqlTaskVersionMapper versionMapper;
  private final SqlTaskExecutionMapper executionMapper;
  private final SqlDevelopmentJsonCodec jsonCodec;

  public SqlDevelopmentRepositoryAdapter(
      SqlTaskMapper taskMapper,
      SqlTaskVersionMapper versionMapper,
      SqlTaskExecutionMapper executionMapper,
      SqlDevelopmentJsonCodec jsonCodec) {
    this.taskMapper = taskMapper;
    this.versionMapper = versionMapper;
    this.executionMapper = executionMapper;
    this.jsonCodec = jsonCodec;
  }

  @Override
  public Definition insertDefinition(
      String name,
      String description,
      Long projectId,
      Long dataSourceId,
      String sql,
      List<SqlParameterDefinition> parameters) {
    Instant now = Instant.now();
    SqlTaskPO po = new SqlTaskPO();
    po.setName(name);
    po.setDescription(description);
    po.setProjectId(projectId);
    po.setDataSourceId(dataSourceId);
    po.setSqlText(sql);
    po.setParameterJson(jsonCodec.write(parameters));
    po.setDraftRevision(1L);
    po.setLatestVersionNo(0);
    po.setDeleted(false);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    taskMapper.insert(po);
    return toDefinition(po);
  }

  @Override
  public Optional<Definition> findDefinition(Long id) {
    return Optional.ofNullable(taskMapper.selectById(id)).map(this::toDefinition);
  }

  @Override
  public List<Definition> listDefinitions(Long projectId) {
    LambdaQueryWrapper<SqlTaskPO> query = new LambdaQueryWrapper<>();
    if (projectId != null) query.eq(SqlTaskPO::getProjectId, projectId);
    query.orderByDesc(SqlTaskPO::getUpdateTime);
    return taskMapper.selectList(query).stream().map(this::toDefinition).toList();
  }

  @Override
  public boolean updateDraft(
      Long id,
      long baseRevision,
      String name,
      String description,
      Long projectId,
      Long dataSourceId,
      String sql,
      List<SqlParameterDefinition> parameters) {
    return taskMapper.update(
            null,
            new LambdaUpdateWrapper<SqlTaskPO>()
                .eq(SqlTaskPO::getId, id)
                .eq(SqlTaskPO::getDraftRevision, baseRevision)
                .set(SqlTaskPO::getName, name)
                .set(SqlTaskPO::getDescription, description)
                .set(SqlTaskPO::getProjectId, projectId)
                .set(SqlTaskPO::getDataSourceId, dataSourceId)
                .set(SqlTaskPO::getSqlText, sql)
                .set(SqlTaskPO::getParameterJson, jsonCodec.write(parameters))
                .set(SqlTaskPO::getDraftRevision, baseRevision + 1L)
                .set(SqlTaskPO::getUpdateTime, Instant.now()))
        == 1;
  }

  @Override
  public Optional<Definition> lockDefinition(Long id) {
    return Optional.ofNullable(taskMapper.selectForUpdate(id)).map(this::toDefinition);
  }

  @Override
  public Version insertVersion(
      Long taskId,
      int versionNo,
      Long dataSourceId,
      String sql,
      List<SqlParameterDefinition> parameters,
      String contentDigest) {
    SqlTaskVersionPO po = new SqlTaskVersionPO();
    po.setTaskId(taskId);
    po.setVersionNo(versionNo);
    po.setDataSourceId(dataSourceId);
    po.setSqlSnapshot(sql);
    po.setParameterSnapshotJson(jsonCodec.write(parameters));
    po.setContentDigest(contentDigest);
    po.setPublishedAt(Instant.now());
    versionMapper.insert(po);
    return toVersion(po);
  }

  @Override
  public boolean markPublished(Long taskId, Long versionId, int versionNo) {
    return taskMapper.update(
            null,
            new LambdaUpdateWrapper<SqlTaskPO>()
                .eq(SqlTaskPO::getId, taskId)
                .set(SqlTaskPO::getPublishedVersionId, versionId)
                .set(SqlTaskPO::getLatestVersionNo, versionNo)
                .set(SqlTaskPO::getUpdateTime, Instant.now()))
        == 1;
  }

  @Override
  public Optional<Version> findVersion(Long versionId) {
    return Optional.ofNullable(versionMapper.selectById(versionId)).map(this::toVersion);
  }

  @Override
  public Optional<Version> findPublishedVersion(Long taskId) {
    SqlTaskPO task = taskMapper.selectById(taskId);
    if (task == null || task.getPublishedVersionId() == null) return Optional.empty();
    return findVersion(task.getPublishedVersionId());
  }

  @Override
  public List<Version> listVersions(Long taskId) {
    return versionMapper.selectList(
            new LambdaQueryWrapper<SqlTaskVersionPO>()
                .eq(SqlTaskVersionPO::getTaskId, taskId)
                .orderByDesc(SqlTaskVersionPO::getVersionNo))
        .stream()
        .map(this::toVersion)
        .toList();
  }

  @Override
  public Execution insertExecution(
      Long taskId,
      Long taskVersionId,
      Integer taskVersionNo,
      Long dataSourceId,
      String sql,
      Map<String, Object> input,
      String idempotencyKey) {
    SqlTaskExecutionPO po = new SqlTaskExecutionPO();
    po.setTaskId(taskId);
    po.setTaskVersionId(taskVersionId);
    po.setTaskVersionNo(taskVersionNo);
    po.setDataSourceId(dataSourceId);
    po.setSqlSnapshot(sql);
    po.setInputJson(jsonCodec.write(input == null ? Map.of() : input));
    po.setIdempotencyKey(idempotencyKey);
    po.setStatus("QUEUED");
    po.setAffectedRows(0L);
    po.setCreateTime(Instant.now());
    executionMapper.insert(po);
    return toExecution(po);
  }

  @Override
  public Optional<Execution> findExecution(Long executionId) {
    return Optional.ofNullable(executionMapper.selectById(executionId)).map(this::toExecution);
  }

  @Override
  public Optional<Execution> findExecutionByIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();
    return Optional.ofNullable(
            executionMapper.selectOne(
                new LambdaQueryWrapper<SqlTaskExecutionPO>()
                    .eq(SqlTaskExecutionPO::getIdempotencyKey, idempotencyKey)
                    .last("LIMIT 1")))
        .map(this::toExecution);
  }

  @Override
  public boolean markRunning(Long executionId) {
    return executionMapper.update(
            null,
            activeUpdate(executionId, List.of("QUEUED"))
                .set(SqlTaskExecutionPO::getStatus, "RUNNING")
                .set(SqlTaskExecutionPO::getStartTime, Instant.now()))
        == 1;
  }

  @Override
  public boolean markSucceeded(Long executionId, long affectedRows, Map<String, Object> output) {
    return executionMapper.update(
            null,
            activeUpdate(executionId, List.of("RUNNING"))
                .set(SqlTaskExecutionPO::getStatus, "SUCCEEDED")
                .set(SqlTaskExecutionPO::getAffectedRows, affectedRows)
                .set(SqlTaskExecutionPO::getOutputJson, jsonCodec.write(output))
                .set(SqlTaskExecutionPO::getFinishTime, Instant.now()))
        == 1;
  }

  @Override
  public boolean markFailed(Long executionId, String errorMessage) {
    return finish(executionId, "FAILED", errorMessage);
  }

  @Override
  public boolean markCanceled(Long executionId) {
    return finish(executionId, "CANCELED", null);
  }

  @Override
  public boolean markLost(Long executionId, String errorMessage) {
    return finish(executionId, "LOST", errorMessage);
  }

  private boolean finish(Long executionId, String status, String errorMessage) {
    LambdaUpdateWrapper<SqlTaskExecutionPO> update =
        activeUpdate(executionId, List.of("QUEUED", "RUNNING"))
            .set(SqlTaskExecutionPO::getStatus, status)
            .set(SqlTaskExecutionPO::getFinishTime, Instant.now());
    if (errorMessage != null) {
      update.set(SqlTaskExecutionPO::getErrorMessage, errorMessage);
    }
    return executionMapper.update(null, update) == 1;
  }

  private LambdaUpdateWrapper<SqlTaskExecutionPO> activeUpdate(
      Long executionId,
      List<String> statuses) {
    return new LambdaUpdateWrapper<SqlTaskExecutionPO>()
        .eq(SqlTaskExecutionPO::getId, executionId)
        .in(SqlTaskExecutionPO::getStatus, statuses);
  }

  private Definition toDefinition(SqlTaskPO po) {
    return new Definition(
        po.getId(),
        po.getName(),
        po.getDescription(),
        po.getProjectId(),
        po.getDataSourceId(),
        po.getSqlText(),
        jsonCodec.readParameters(po.getParameterJson()),
        po.getDraftRevision() == null ? 0L : po.getDraftRevision(),
        po.getPublishedVersionId(),
        po.getLatestVersionNo() == null ? 0 : po.getLatestVersionNo(),
        po.getCreateTime(),
        po.getUpdateTime());
  }

  private Version toVersion(SqlTaskVersionPO po) {
    return new Version(
        po.getId(),
        po.getTaskId(),
        po.getVersionNo() == null ? 0 : po.getVersionNo(),
        po.getDataSourceId(),
        po.getSqlSnapshot(),
        jsonCodec.readParameters(po.getParameterSnapshotJson()),
        po.getContentDigest(),
        po.getPublishedAt());
  }

  private Execution toExecution(SqlTaskExecutionPO po) {
    return new Execution(
        po.getId(),
        po.getTaskId(),
        po.getTaskVersionId(),
        po.getTaskVersionNo(),
        po.getDataSourceId(),
        po.getStatus(),
        po.getAffectedRows() == null ? 0L : po.getAffectedRows(),
        jsonCodec.readMap(po.getOutputJson()),
        po.getErrorMessage(),
        po.getCreateTime(),
        po.getStartTime(),
        po.getFinishTime());
  }
}
