package io.yak.ops.business.dataset;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Public boundary used by data-development Dataset nodes. */
@Service
public class DevelopmentDatasetFacade {

  private final DatasetService service;
  private final DevelopmentStandaloneDatasetService standaloneService;

  public DevelopmentDatasetFacade(
      DatasetService service,
      DevelopmentStandaloneDatasetService standaloneService) {
    this.service = service;
    this.standaloneService = standaloneService;
  }

  public Optional<NodeDataset> findByDevelopmentNodeId(long developmentNodeId) {
    return service.findByDevelopmentNodeId(developmentNodeId)
        .map(detail -> toNodeDataset(developmentNodeId, detail));
  }

  /** Standalone Dataset editor preview: datasource + SQL belong to Dataset itself. */
  public List<FieldDraft> preview(String dataSourceId, String sql) {
    return standaloneService.preview(dataSourceId, sql).stream()
        .map(DevelopmentDatasetFacade::toFieldDraft)
        .toList();
  }

  public NodeDataset save(
      long developmentNodeId,
      String dataSourceId,
      String sql,
      String name,
      String description,
      List<FieldDraft> fields) {
    List<DatasetService.FieldSpec> specs = fields == null
        ? List.of()
        : fields.stream().map(DevelopmentDatasetFacade::toFieldSpec).toList();
    DatasetDetail detail = standaloneService.save(
        developmentNodeId, dataSourceId, sql, name, description, specs);
    return toNodeDataset(developmentNodeId, detail);
  }

  /** Legacy TaskAsset API retained for release flows/tests outside the Dataset node editor. */
  public List<FieldDraft> preview(long sourceTaskAssetId) {
    return service.previewReleaseFields(sourceTaskAssetId).stream()
        .map(DevelopmentDatasetFacade::toFieldDraft)
        .toList();
  }

  /** Legacy TaskAsset API retained for source compatibility. */
  public NodeDataset save(
      long developmentNodeId,
      long sourceTaskAssetId,
      String name,
      String description,
      List<FieldDraft> fields) {
    List<DatasetService.FieldSpec> specs = fields == null
        ? List.of()
        : fields.stream().map(DevelopmentDatasetFacade::toFieldSpec).toList();
    DatasetDetail detail = service.saveForDevelopmentNode(
        developmentNodeId,
        new DatasetService.PublishCommand(sourceTaskAssetId, name, description, specs));
    return toNodeDataset(developmentNodeId, detail);
  }

  private static NodeDataset toNodeDataset(long developmentNodeId, DatasetDetail detail) {
    Dataset dataset = detail.dataset();
    VersionSnapshot currentVersion = detail.currentVersion() == null
        ? null : toVersion(detail.currentVersion());
    return new NodeDataset(
        String.valueOf(developmentNodeId),
        String.valueOf(dataset.id()),
        dataset.name(),
        dataset.description(),
        dataset.status().name(),
        currentVersion,
        detail.versions().stream().map(DevelopmentDatasetFacade::toVersion).toList(),
        detail.fields().stream().map(DevelopmentDatasetFacade::toField).toList(),
        dataset.createTime(),
        dataset.updateTime());
  }

  private static VersionSnapshot toVersion(DatasetVersion version) {
    return new VersionSnapshot(
        String.valueOf(version.id()),
        version.versionNo(),
        version.sourceType().name(),
        String.valueOf(version.sourceTaskAssetId()),
        String.valueOf(version.sourceTaskRevisionId()),
        version.sourceTaskRevisionNo(),
        version.dataSourceId(),
        version.sql(),
        version.createTime());
  }

  private static FieldSnapshot toField(DatasetField field) {
    return new FieldSnapshot(
        field.fieldId(), field.physicalName(), field.displayName(), field.dataType().name(),
        field.nullable(), field.description(), field.defaultRole().name(), field.sortOrder());
  }

  private static FieldDraft toFieldDraft(DatasetService.FieldSpec field) {
    return new FieldDraft(
        field.fieldId(), field.physicalName(), field.displayName(), field.dataType().name(),
        field.nullable(), field.description(), field.defaultRole().name());
  }

  private static DatasetService.FieldSpec toFieldSpec(FieldDraft field) {
    if (field == null) throw new IllegalArgumentException("Dataset 字段不能为空");
    return new DatasetService.FieldSpec(
        field.fieldId(), field.physicalName(), field.displayName(), parseDataType(field.dataType()),
        field.nullable(), field.description(), parseRole(field.defaultRole()));
  }

  private static DatasetFieldDataType parseDataType(String value) {
    if (value == null || value.isBlank()) return DatasetFieldDataType.UNKNOWN;
    try {
      return DatasetFieldDataType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知 Dataset 字段类型：" + value, exception);
    }
  }

  private static DatasetFieldRole parseRole(String value) {
    if (value == null || value.isBlank()) return DatasetFieldRole.DIMENSION;
    try {
      return DatasetFieldRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知 Dataset 字段角色：" + value, exception);
    }
  }

  public record NodeDataset(
      String developmentNodeId,
      String datasetId,
      String name,
      String description,
      String status,
      VersionSnapshot currentVersion,
      List<VersionSnapshot> versions,
      List<FieldSnapshot> fields,
      Instant createTime,
      Instant updateTime) {
  }

  public record VersionSnapshot(
      String versionId,
      int versionNo,
      String sourceType,
      String sourceTaskAssetId,
      String sourceTaskRevisionId,
      int sourceTaskRevisionNo,
      String dataSourceId,
      String sql,
      Instant createTime) {

    public VersionSnapshot(
        String versionId,
        int versionNo,
        String sourceType,
        String sourceTaskAssetId,
        String sourceTaskRevisionId,
        int sourceTaskRevisionNo,
        Instant createTime) {
      this(versionId, versionNo, sourceType, sourceTaskAssetId, sourceTaskRevisionId,
          sourceTaskRevisionNo, null, null, createTime);
    }
  }

  public record FieldSnapshot(
      String fieldId,
      String physicalName,
      String displayName,
      String dataType,
      boolean nullable,
      String description,
      String defaultRole,
      int sortOrder) {
  }

  public record FieldDraft(
      String fieldId,
      String physicalName,
      String displayName,
      String dataType,
      boolean nullable,
      String description,
      String defaultRole) {
  }
}
