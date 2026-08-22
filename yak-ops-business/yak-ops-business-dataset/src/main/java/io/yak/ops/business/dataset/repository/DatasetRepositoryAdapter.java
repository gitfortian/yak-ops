package io.yak.ops.business.dataset.repository;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetFieldDefinition;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.DatasetVersionDraft;
import io.yak.ops.business.dataset.dao.DatasetDao;
import io.yak.ops.business.dataset.dao.model.DatasetFieldPO;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import io.yak.ops.business.dataset.dao.model.DatasetVersionPO;
import io.yak.ops.business.dataset.repository.support.DatasetJsonCodec;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Dataset aggregate persistence adapter. Domain/PO conversion is isolated here. */
@Repository
@RequiredArgsConstructor
public class DatasetRepositoryAdapter implements DatasetRepository {

  private final DatasetDao datasetDao;
  private final DatasetJsonCodec jsonCodec;

  @Override
  public long insertDataset(String name, String description) {
    return insertDataset(null, name, description);
  }

  @Override
  public long insertDevelopmentNodeDataset(long developmentNodeId, String name, String description) {
    if (developmentNodeId <= 0L) throw new IllegalArgumentException("developmentNodeId 必须大于 0");
    return insertDataset(developmentNodeId, name, description);
  }

  private long insertDataset(Long developmentNodeId, String name, String description) {
    DatasetPO po = new DatasetPO();
    po.setDevelopmentNodeId(developmentNodeId);
    po.setName(name);
    po.setDescription(description);
    po.setStatus(DatasetStatus.ONLINE.name());
    if (datasetDao.insertDataset(po) != 1 || po.getId() == null) {
      throw new IllegalStateException("创建 Dataset 后未返回主键");
    }
    return po.getId();
  }

  @Override
  public long appendVersion(DatasetVersionDraft draft) {
    if (draft == null) throw new IllegalArgumentException("DatasetVersionDraft 不能为空");
    DatasetVersionPO version = new DatasetVersionPO();
    version.setDatasetId(draft.datasetId());
    version.setVersionNo(draft.versionNo());
    version.setSourceType(draft.sourceType().name());
    version.setSourceTaskAssetId(draft.sourceTaskAssetId());
    version.setSourceTaskRevisionId(draft.sourceTaskRevisionId());
    version.setSourceTaskRevisionNo(draft.sourceTaskRevisionNo());
    version.setDataSourceId(draft.dataSourceId());
    version.setSqlContent(draft.sql());
    version.setSchemaSnapshot(jsonCodec.schemaSnapshot(draft.fields()));
    if (datasetDao.insertVersion(version) != 1 || version.getId() == null) {
      throw new IllegalStateException("创建 DatasetVersion 后未返回主键");
    }

    long versionId = version.getId();
    for (int index = 0; index < draft.fields().size(); index++) {
      DatasetFieldDefinition field = draft.fields().get(index);
      DatasetFieldPO po = new DatasetFieldPO();
      po.setFieldId(field.fieldId());
      po.setVersionId(versionId);
      po.setPhysicalName(field.physicalName());
      po.setDisplayName(field.displayName());
      po.setDataType(field.dataType().name());
      po.setNullable(field.nullable());
      po.setDescription(field.description());
      po.setDefaultRole(field.defaultRole().name());
      po.setSortOrder(index + 1);
      if (datasetDao.insertField(po) != 1) {
        throw new IllegalStateException("保存 Dataset 字段失败：" + field.fieldId());
      }
    }
    return versionId;
  }

  @Override
  public void updateCurrentVersion(long datasetId, long versionId) {
    requireSingle(datasetDao.updateCurrentVersion(datasetId, versionId), datasetId);
  }

  @Override
  public void updateStatus(long datasetId, DatasetStatus status) {
    requireSingle(datasetDao.updateStatus(datasetId, status.name()), datasetId);
  }

  @Override
  public void updateMetadata(long datasetId, String name, String description) {
    requireSingle(datasetDao.updateMetadata(datasetId, name, description), datasetId);
  }

  @Override
  public Optional<Dataset> findDataset(long datasetId) {
    return Optional.ofNullable(datasetDao.selectDataset(datasetId)).map(this::toDomain);
  }

  @Override
  public Optional<Dataset> findDatasetBySourceTaskAssetId(long sourceTaskAssetId) {
    return Optional.ofNullable(datasetDao.selectDatasetBySourceTaskAssetId(sourceTaskAssetId))
        .map(this::toDomain);
  }

  @Override
  public Optional<Dataset> findDatasetByDevelopmentNodeId(long developmentNodeId) {
    return Optional.ofNullable(datasetDao.selectDatasetByDevelopmentNodeId(developmentNodeId))
        .map(this::toDomain);
  }

  @Override
  public List<Dataset> listDatasets() {
    return datasetDao.selectDatasets().stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<DatasetVersion> findVersion(long versionId) {
    return Optional.ofNullable(datasetDao.selectVersion(versionId)).map(this::toDomain);
  }

  @Override
  public List<DatasetVersion> listVersions(long datasetId) {
    return datasetDao.selectVersions(datasetId).stream().map(this::toDomain).toList();
  }

  @Override
  public List<DatasetField> listFields(long versionId) {
    return datasetDao.selectFields(versionId).stream().map(this::toDomain).toList();
  }

  @Override
  public int nextVersionNo(long datasetId) {
    return datasetDao.selectNextVersionNo(datasetId);
  }

  private Dataset toDomain(DatasetPO po) {
    return new Dataset(
        po.getId(),
        po.getName(),
        po.getDescription(),
        DatasetStatus.valueOf(po.getStatus()),
        po.getCurrentVersionId(),
        instant(po.getCreateTime()),
        instant(po.getUpdateTime()));
  }

  private DatasetVersion toDomain(DatasetVersionPO po) {
    return new DatasetVersion(
        po.getId(),
        po.getDatasetId(),
        po.getVersionNo(),
        DatasetSourceType.valueOf(po.getSourceType()),
        po.getSourceTaskAssetId(),
        po.getSourceTaskRevisionId(),
        po.getSourceTaskRevisionNo(),
        po.getDataSourceId(),
        po.getSqlContent(),
        po.getSchemaSnapshot(),
        instant(po.getCreateTime()));
  }

  private DatasetField toDomain(DatasetFieldPO po) {
    return new DatasetField(
        po.getFieldId(),
        po.getVersionId(),
        po.getPhysicalName(),
        po.getDisplayName(),
        DatasetFieldDataType.valueOf(po.getDataType()),
        Boolean.TRUE.equals(po.getNullable()),
        po.getDescription(),
        DatasetFieldRole.valueOf(po.getDefaultRole()),
        po.getSortOrder());
  }

  private void requireSingle(int affectedRows, long datasetId) {
    if (affectedRows != 1) throw new IllegalArgumentException("Dataset 不存在：" + datasetId);
  }

  private Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
