package io.yak.ops.business.dataset.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.dataset.dao.DatasetDao;
import io.yak.ops.business.dataset.dao.mapper.DatasetFieldMapper;
import io.yak.ops.business.dataset.dao.mapper.DatasetMapper;
import io.yak.ops.business.dataset.dao.mapper.DatasetVersionMapper;
import io.yak.ops.business.dataset.dao.model.DatasetFieldPO;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import io.yak.ops.business.dataset.dao.model.DatasetVersionPO;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus implementation for Dataset persistence primitives. */
@Repository
@DependsOn("yakDatasetFlyway")
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DatasetDaoImpl implements DatasetDao {

  private final DatasetMapper datasetMapper;
  private final DatasetVersionMapper versionMapper;
  private final DatasetFieldMapper fieldMapper;

  @Override
  public int insertDataset(DatasetPO dataset) {
    Timestamp now = Timestamp.from(Instant.now());
    dataset.setCreateTime(now);
    dataset.setUpdateTime(now);
    return datasetMapper.insert(dataset);
  }

  @Override
  public int insertVersion(DatasetVersionPO version) {
    version.setCreateTime(Timestamp.from(Instant.now()));
    return versionMapper.insert(version);
  }

  @Override
  public int insertField(DatasetFieldPO field) {
    return fieldMapper.insert(field);
  }

  @Override
  public int updateCurrentVersion(long datasetId, long versionId) {
    return datasetMapper.update(
        null,
        Wrappers.<DatasetPO>lambdaUpdate()
            .eq(DatasetPO::getId, datasetId)
            .set(DatasetPO::getCurrentVersionId, versionId)
            .set(DatasetPO::getUpdateTime, Timestamp.from(Instant.now())));
  }

  @Override
  public int updateStatus(long datasetId, String status) {
    return datasetMapper.update(
        null,
        Wrappers.<DatasetPO>lambdaUpdate()
            .eq(DatasetPO::getId, datasetId)
            .set(DatasetPO::getStatus, status)
            .set(DatasetPO::getUpdateTime, Timestamp.from(Instant.now())));
  }

  @Override
  public int updateMetadata(long datasetId, String name, String description) {
    return datasetMapper.update(
        null,
        Wrappers.<DatasetPO>lambdaUpdate()
            .eq(DatasetPO::getId, datasetId)
            .set(DatasetPO::getName, name)
            .set(DatasetPO::getDescription, description)
            .set(DatasetPO::getUpdateTime, Timestamp.from(Instant.now())));
  }

  @Override
  public DatasetPO selectDataset(long datasetId) {
    return datasetMapper.selectById(datasetId);
  }

  @Override
  public DatasetPO selectDatasetBySourceTaskAssetId(long sourceTaskAssetId) {
    List<Object> values = versionMapper.selectObjs(
        Wrappers.<DatasetVersionPO>query()
            .select("dataset_id")
            .eq("source_task_asset_id", sourceTaskAssetId)
            .orderByDesc("id"));
    List<Long> datasetIds = values.stream()
        .filter(value -> value instanceof Number)
        .map(value -> ((Number) value).longValue())
        .distinct()
        .toList();
    if (datasetIds.isEmpty()) return null;
    return datasetMapper.selectList(
        Wrappers.<DatasetPO>lambdaQuery()
            .in(DatasetPO::getId, datasetIds)
            .isNull(DatasetPO::getDevelopmentNodeId)
            .orderByDesc(DatasetPO::getUpdateTime)
            .orderByDesc(DatasetPO::getId))
        .stream()
        .findFirst()
        .orElse(null);
  }

  @Override
  public DatasetPO selectDatasetByDevelopmentNodeId(long developmentNodeId) {
    return datasetMapper.selectOne(
        Wrappers.<DatasetPO>lambdaQuery()
            .eq(DatasetPO::getDevelopmentNodeId, developmentNodeId));
  }

  @Override
  public List<DatasetPO> selectDatasets() {
    return datasetMapper.selectList(
        Wrappers.<DatasetPO>lambdaQuery()
            .orderByDesc(DatasetPO::getUpdateTime)
            .orderByDesc(DatasetPO::getId));
  }

  @Override
  public DatasetVersionPO selectVersion(long versionId) {
    return versionMapper.selectById(versionId);
  }

  @Override
  public List<DatasetVersionPO> selectVersions(long datasetId) {
    return versionMapper.selectList(
        Wrappers.<DatasetVersionPO>lambdaQuery()
            .eq(DatasetVersionPO::getDatasetId, datasetId)
            .orderByDesc(DatasetVersionPO::getVersionNo));
  }

  @Override
  public List<DatasetFieldPO> selectFields(long versionId) {
    return fieldMapper.selectList(
        Wrappers.<DatasetFieldPO>lambdaQuery()
            .eq(DatasetFieldPO::getVersionId, versionId)
            .orderByAsc(DatasetFieldPO::getSortOrder)
            .orderByAsc(DatasetFieldPO::getPhysicalName));
  }

  @Override
  public int selectNextVersionNo(long datasetId) {
    List<Object> values = versionMapper.selectObjs(
        Wrappers.<DatasetVersionPO>query()
            .select("COALESCE(MAX(version_no), 0) + 1")
            .eq("dataset_id", datasetId));
    if (values == null || values.isEmpty() || values.get(0) == null) return 1;
    return ((Number) values.get(0)).intValue();
  }
}
