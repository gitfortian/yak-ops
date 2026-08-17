package io.yak.ops.business.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lifecycle for data-development Dataset nodes that own datasource + SQL directly. */
@Service
final class DevelopmentStandaloneDatasetService {

  private final DatasetRepository repository;
  private final DatasetSchemaDiscoveryService discoveryService;
  private final ObjectMapper objectMapper;

  DevelopmentStandaloneDatasetService(
      DatasetRepository repository,
      DatasetSchemaDiscoveryService discoveryService,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.discoveryService = discoveryService;
    this.objectMapper = objectMapper;
  }

  List<DatasetService.FieldSpec> preview(String dataSourceId, String sql) {
    return discoveryService.preview(requireDataSourceId(dataSourceId), requireSql(sql));
  }

  @Transactional("yakBusinessTransactionManager")
  DatasetDetail save(
      long developmentNodeId,
      String dataSourceId,
      String sql,
      String name,
      String description,
      List<DatasetService.FieldSpec> requestedFields) {
    if (developmentNodeId <= 0L) throw new IllegalArgumentException("developmentNodeId 必须大于 0");
    String sourceId = requireDataSourceId(dataSourceId);
    String sourceSql = requireSql(sql);
    String normalizedName = normalizeName(name);
    String normalizedDescription = normalizeDescription(description);

    Dataset dataset = repository.findDatasetByDevelopmentNodeId(developmentNodeId).orElse(null);
    long datasetId;
    if (dataset == null) {
      datasetId = repository.insertDevelopmentNodeDataset(
          developmentNodeId, normalizedName, normalizedDescription);
    } else {
      datasetId = dataset.id();
      repository.updateMetadata(datasetId, normalizedName, normalizedDescription);
    }

    List<DatasetService.FieldSpec> fields = requestedFields == null || requestedFields.isEmpty()
        ? discoveryService.discover(datasetId, sourceId, sourceSql)
        : normalizeFields(datasetId, requestedFields);
    DatasetDetail current = get(datasetId);
    DatasetVersion version = current.currentVersion();
    if (version != null
        && version.sourceType() == DatasetSourceType.SQL_QUERY
        && Objects.equals(version.dataSourceId(), sourceId)
        && Objects.equals(version.sql(), sourceSql)
        && sameFields(current.fields(), fields)) {
      return current;
    }

    int versionNo = repository.nextVersionNo(datasetId);
    long versionId = repository.insertStandaloneVersion(
        datasetId, versionNo, sourceId, sourceSql, schemaSnapshot(fields));
    repository.insertFields(versionId, fields);
    repository.updateCurrentVersion(datasetId, versionId);
    return get(datasetId);
  }

  private DatasetDetail get(long datasetId) {
    Dataset dataset = repository.findDataset(datasetId)
        .orElseThrow(() -> new IllegalArgumentException("Dataset 不存在：" + datasetId));
    DatasetVersion currentVersion = dataset.currentVersionId() == null
        ? null
        : repository.findVersion(dataset.currentVersionId())
            .orElseThrow(() -> new IllegalStateException("Dataset 当前版本不存在：" + dataset.currentVersionId()));
    List<DatasetField> fields = currentVersion == null ? List.of() : repository.listFields(currentVersion.id());
    return new DatasetDetail(dataset, currentVersion, repository.listVersions(datasetId), fields);
  }

  private List<DatasetService.FieldSpec> normalizeFields(
      long datasetId,
      List<DatasetService.FieldSpec> values) {
    Map<String, String> existingIds = existingFieldIds(datasetId);
    List<DatasetService.FieldSpec> result = new ArrayList<>(values.size());
    Set<String> names = new HashSet<>();
    Set<String> ids = new HashSet<>();
    for (DatasetService.FieldSpec value : values) {
      if (value == null) throw new IllegalArgumentException("Dataset 字段不能为空");
      String physicalName = required(value.physicalName(), "physicalName", 128);
      String key = physicalName.toLowerCase(Locale.ROOT);
      if (!names.add(key)) throw new IllegalArgumentException("Dataset 字段重复：" + physicalName);
      String fieldId = value.fieldId();
      if (fieldId == null || fieldId.isBlank()) {
        fieldId = existingIds.getOrDefault(key, stableFieldId(datasetId, key));
      }
      fieldId = required(fieldId, "fieldId", 64);
      if (!ids.add(fieldId)) throw new IllegalArgumentException("fieldId 重复：" + fieldId);
      String displayName = value.displayName();
      if (displayName == null || displayName.isBlank()) displayName = physicalName;
      displayName = required(displayName, "displayName", 200);
      String fieldDescription = normalizeFieldDescription(value.description());
      result.add(new DatasetService.FieldSpec(
          fieldId,
          physicalName,
          displayName,
          value.dataType() == null ? DatasetFieldDataType.UNKNOWN : value.dataType(),
          value.nullable(),
          fieldDescription,
          value.defaultRole() == null ? DatasetFieldRole.DIMENSION : value.defaultRole()));
    }
    return List.copyOf(result);
  }

  private Map<String, String> existingFieldIds(long datasetId) {
    Map<String, String> result = new HashMap<>();
    repository.findDataset(datasetId).ifPresent(dataset -> {
      if (dataset.currentVersionId() == null) return;
      repository.listFields(dataset.currentVersionId()).forEach(field ->
          result.put(field.physicalName().toLowerCase(Locale.ROOT), field.fieldId()));
    });
    return result;
  }

  private boolean sameFields(List<DatasetField> current, List<DatasetService.FieldSpec> requested) {
    if (current.size() != requested.size()) return false;
    for (int i = 0; i < current.size(); i++) {
      DatasetField left = current.get(i);
      DatasetService.FieldSpec right = requested.get(i);
      if (!Objects.equals(left.fieldId(), right.fieldId())
          || !Objects.equals(left.physicalName(), right.physicalName())
          || !Objects.equals(left.displayName(), right.displayName())
          || left.dataType() != right.dataType()
          || left.nullable() != right.nullable()
          || !Objects.equals(left.description(), right.description())
          || left.defaultRole() != right.defaultRole()) return false;
    }
    return true;
  }

  private String requireDataSourceId(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Dataset 必须选择数据源");
    String normalized = value.trim();
    if (normalized.length() > 128) throw new IllegalArgumentException("dataSourceId 不能超过 128 个字符");
    return normalized;
  }

  private String requireSql(String value) {
    return DatasetSqlSafety.requireReadOnlyQuery(value);
  }

  private String normalizeName(String value) {
    return required(value, "Dataset 名称", 200);
  }

  private String normalizeDescription(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (normalized.length() > 2000) throw new IllegalArgumentException("Dataset 描述不能超过 2000 个字符");
    return normalized;
  }

  private String normalizeFieldDescription(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (normalized.length() > 1000) throw new IllegalArgumentException("字段描述不能超过 1000 个字符");
    return normalized;
  }

  private String required(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
    String normalized = value.trim();
    if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " 不能超过 " + maxLength + " 个字符");
    return normalized;
  }

  private String stableFieldId(long datasetId, String physicalKey) {
    return UUID.nameUUIDFromBytes(
        ("dataset:" + datasetId + ":" + physicalKey).getBytes(StandardCharsets.UTF_8)).toString();
  }

  private String schemaSnapshot(List<DatasetService.FieldSpec> fields) {
    try {
      return objectMapper.writeValueAsString(fields);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Dataset schemaSnapshot 序列化失败", exception);
    }
  }
}
