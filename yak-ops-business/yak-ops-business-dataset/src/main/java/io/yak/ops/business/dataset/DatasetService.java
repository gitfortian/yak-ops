package io.yak.ops.business.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dataset publication/lifecycle service. Dataset is a data contract, not an executable task. */
@Service
public class DatasetService {

  private final DatasetRepository repository;
  private final TaskCatalogService taskCatalogService;
  private final ObjectMapper objectMapper;
  private final DatasetSchemaDiscoveryService schemaDiscoveryService;

  @Autowired
  public DatasetService(
      DatasetRepository repository,
      TaskCatalogService taskCatalogService,
      ObjectMapper objectMapper,
      DatasetSchemaDiscoveryService schemaDiscoveryService) {
    this.repository = repository;
    this.taskCatalogService = taskCatalogService;
    this.objectMapper = objectMapper;
    this.schemaDiscoveryService = schemaDiscoveryService;
  }

  /** Backward-compatible constructor for focused unit tests that do not need live schema discovery. */
  DatasetService(
      DatasetRepository repository,
      TaskCatalogService taskCatalogService,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.taskCatalogService = taskCatalogService;
    this.objectMapper = objectMapper;
    this.schemaDiscoveryService = null;
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail publish(PublishCommand command) {
    Objects.requireNonNull(command, "command");
    TaskAsset asset = requirePublishableAsset(command.sourceTaskAssetId());
    return createDataset(asset, command);
  }

  /**
   * Data-development release shortcut.
   *
   * <p>The first publish creates a stable Dataset identity. Later SQL TaskRevision publishes append
   * a new DatasetVersion to that same Dataset. Re-publishing an already-bound TaskRevision is
   * idempotent after the first Dataset has been committed.
   */
  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail publishFromRelease(PublishCommand command) {
    Objects.requireNonNull(command, "command");
    TaskAsset asset = requirePublishableAsset(command.sourceTaskAssetId());
    Optional<Dataset> existing = repository.findDatasetBySourceTaskAssetId(asset.id());
    if (existing.isEmpty()) {
      return createDataset(asset, command);
    }

    DatasetDetail current = get(existing.get().id());
    DatasetVersion currentVersion = current.currentVersion();
    if (currentVersion == null) {
      List<FieldSpec> fields = resolveFields(existing.get().id(), asset, command.fields());
      long versionId = appendVersion(existing.get().id(), asset, fields, true);
      repository.updateCurrentVersion(existing.get().id(), versionId);
      return get(existing.get().id());
    }
    if (currentVersion.sourceTaskAssetId() != asset.id()) {
      throw new IllegalStateException(
          "Dataset 来源 TaskAsset 不一致：datasetId=" + existing.get().id()
              + ", expected=" + currentVersion.sourceTaskAssetId()
              + ", actual=" + asset.id());
    }
    if (currentVersion.sourceTaskRevisionId() == asset.currentRevision().taskRevisionId()) {
      return current;
    }

    List<FieldSpec> fields = resolveFields(existing.get().id(), asset, command.fields());
    long versionId = appendVersion(existing.get().id(), asset, fields, true);
    repository.updateCurrentVersion(existing.get().id(), versionId);
    return get(existing.get().id());
  }

  public List<FieldSpec> previewReleaseFields(long sourceTaskAssetId) {
    TaskAsset asset = requirePublishableAsset(sourceTaskAssetId);
    if (schemaDiscoveryService == null) {
      throw new IllegalStateException("Dataset schema discovery 未启用");
    }
    return schemaDiscoveryService.preview(asset);
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail createVersion(long datasetId, List<FieldSpec> fields) {
    DatasetDetail current = get(datasetId);
    DatasetVersion currentVersion = current.currentVersion();
    if (currentVersion == null) {
      throw new IllegalStateException("Dataset 尚未建立当前版本：" + datasetId);
    }

    TaskAsset asset = requirePublishableAsset(currentVersion.sourceTaskAssetId());
    if (asset.currentRevision().taskRevisionId() == currentVersion.sourceTaskRevisionId()) {
      throw new IllegalArgumentException(
          "当前 TaskRevision 已经是 Dataset 的当前版本：V" + currentVersion.versionNo());
    }

    List<FieldSpec> normalizedFields = resolveFields(datasetId, asset, fields);
    long versionId = appendVersion(datasetId, asset, normalizedFields, true);
    repository.updateCurrentVersion(datasetId, versionId);
    return get(datasetId);
  }

  public List<Dataset> list() {
    return repository.listDatasets();
  }

  public DatasetDetail get(long datasetId) {
    if (datasetId <= 0L) throw new IllegalArgumentException("datasetId 必须大于 0");
    Dataset dataset = repository.findDataset(datasetId)
        .orElseThrow(() -> new IllegalArgumentException("Dataset 不存在：" + datasetId));

    DatasetVersion currentVersion = null;
    List<DatasetField> fields = List.of();
    if (dataset.currentVersionId() != null) {
      currentVersion = repository.findVersion(dataset.currentVersionId())
          .orElseThrow(() -> new IllegalStateException(
              "Dataset 当前版本不存在：datasetId=" + datasetId
                  + ", versionId=" + dataset.currentVersionId()));
      fields = repository.listFields(currentVersion.id());
    }
    return new DatasetDetail(dataset, currentVersion, repository.listVersions(datasetId), fields);
  }

  public Optional<DatasetDetail> findBySourceTaskAssetId(long sourceTaskAssetId) {
    if (sourceTaskAssetId <= 0L) {
      throw new IllegalArgumentException("sourceTaskAssetId 必须大于 0");
    }
    return repository.findDatasetBySourceTaskAssetId(sourceTaskAssetId)
        .map(dataset -> get(dataset.id()));
  }

  /**
   * Cross-domain validation boundary used by reusable Analysis assets.
   *
   * <p>Analysis only references stable Dataset fieldIds; it does not own Dataset lifecycle or schema.
   */
  public void validateAnalysisBinding(long datasetId, Collection<String> fieldIds) {
    DatasetDetail detail = get(datasetId);
    if (detail.dataset().status() != DatasetStatus.ONLINE) {
      throw new IllegalArgumentException("Analysis 只能绑定 ONLINE Dataset：" + datasetId);
    }
    if (detail.currentVersion() == null) {
      throw new IllegalArgumentException("Analysis 绑定的 Dataset 尚无当前版本：" + datasetId);
    }
    Set<String> available = new HashSet<>();
    detail.fields().forEach(field -> available.add(field.fieldId()));
    if (fieldIds == null) return;
    for (String value : fieldIds) {
      if (value == null || value.isBlank()) throw new IllegalArgumentException("Analysis fieldId 不能为空");
      String fieldId = value.trim();
      if (!available.contains(fieldId)) {
        throw new IllegalArgumentException("Analysis 字段不属于 Dataset 当前 schema：" + fieldId);
      }
    }
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail online(long datasetId) {
    Dataset dataset = get(datasetId).dataset();
    if (dataset.status() != DatasetStatus.ONLINE) {
      repository.updateStatus(datasetId, DatasetStatus.ONLINE);
    }
    return get(datasetId);
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail offline(long datasetId) {
    Dataset dataset = get(datasetId).dataset();
    if (dataset.status() != DatasetStatus.OFFLINE) {
      repository.updateStatus(datasetId, DatasetStatus.OFFLINE);
    }
    return get(datasetId);
  }

  private DatasetDetail createDataset(TaskAsset asset, PublishCommand command) {
    String name = normalizeName(command.name(), asset.name());
    String description = normalizeDescription(command.description());

    long datasetId = repository.insertDataset(name, description);
    List<FieldSpec> fields = resolveFields(datasetId, asset, command.fields());
    long versionId = appendVersion(datasetId, asset, fields, false);
    repository.updateCurrentVersion(datasetId, versionId);
    return get(datasetId);
  }

  private List<FieldSpec> resolveFields(
      long datasetId,
      TaskAsset asset,
      List<FieldSpec> requestedFields) {
    if (requestedFields != null && !requestedFields.isEmpty()) {
      return normalizeFields(datasetId, requestedFields);
    }
    if (schemaDiscoveryService == null) return List.of();
    return normalizeFields(datasetId, schemaDiscoveryService.discover(datasetId, asset));
  }

  private long appendVersion(
      long datasetId,
      TaskAsset asset,
      List<FieldSpec> fields,
      boolean requireExistingDataset) {
    if (requireExistingDataset) {
      repository.findDataset(datasetId)
          .orElseThrow(() -> new IllegalArgumentException("Dataset 不存在：" + datasetId));
    }

    int versionNo = repository.nextVersionNo(datasetId);
    String schemaSnapshot = schemaSnapshot(fields);
    long versionId = repository.insertVersion(
        datasetId,
        versionNo,
        DatasetSourceType.QUERY_REVISION,
        asset.id(),
        asset.currentRevision().taskRevisionId(),
        asset.currentRevision().revisionNo(),
        schemaSnapshot);
    repository.insertFields(versionId, fields);
    return versionId;
  }

  private TaskAsset requirePublishableAsset(long assetId) {
    TaskAsset asset = taskCatalogService.get(assetId);
    if (asset.source() != TaskAssetSource.DATA_DEVELOPMENT) {
      throw new IllegalArgumentException("只有数据开发 TaskAsset 可以发布为 Dataset：" + assetId);
    }
    if (asset.status() != TaskAssetStatus.ONLINE) {
      throw new IllegalArgumentException("只有 ONLINE 的 TaskAsset 可以发布/更新 Dataset：" + assetId);
    }
    if (!"SQL".equalsIgnoreCase(asset.taskType())) {
      throw new IllegalArgumentException("当前仅支持 SQL TaskAsset 发布为 QUERY_REVISION Dataset");
    }
    if (asset.currentRevision() == null
        || asset.currentRevision().taskRevisionId() <= 0L
        || asset.currentRevision().revisionNo() <= 0) {
      throw new IllegalStateException("TaskAsset 缺少有效的当前不可变版本：" + assetId);
    }
    return asset;
  }

  private String normalizeName(String value, String fallback) {
    String normalized = value == null || value.isBlank() ? fallback : value.trim();
    if (normalized == null || normalized.isBlank()) throw new IllegalArgumentException("Dataset 名称不能为空");
    if (normalized.length() > 200) throw new IllegalArgumentException("Dataset 名称不能超过 200 个字符");
    return normalized;
  }

  private String normalizeDescription(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (normalized.length() > 2000) throw new IllegalArgumentException("Dataset 描述不能超过 2000 个字符");
    return normalized;
  }

  private List<FieldSpec> normalizeFields(long datasetId, List<FieldSpec> values) {
    if (values == null || values.isEmpty()) return List.of();

    Map<String, String> existingFieldIds = existingFieldIds(datasetId);
    List<FieldSpec> normalized = new ArrayList<>(values.size());
    Set<String> physicalNames = new HashSet<>();
    Set<String> fieldIds = new HashSet<>();
    for (FieldSpec value : values) {
      if (value == null) throw new IllegalArgumentException("Dataset 字段不能为空");
      String physicalName = required(value.physicalName(), "physicalName", 128);
      String physicalKey = physicalName.toLowerCase(Locale.ROOT);
      if (!physicalNames.add(physicalKey)) {
        throw new IllegalArgumentException("Dataset 字段重复：" + physicalName);
      }

      String fieldId = value.fieldId();
      if (fieldId == null || fieldId.isBlank()) {
        fieldId = existingFieldIds.getOrDefault(physicalKey, stableFieldId(datasetId, physicalKey));
      }
      fieldId = fieldId.trim();
      if (fieldId.length() > 64) throw new IllegalArgumentException("fieldId 不能超过 64 个字符");
      if (!fieldIds.add(fieldId)) throw new IllegalArgumentException("fieldId 重复：" + fieldId);

      String displayName = value.displayName();
      if (displayName == null || displayName.isBlank()) displayName = physicalName;
      displayName = displayName.trim();
      if (displayName.length() > 200) throw new IllegalArgumentException("displayName 不能超过 200 个字符");

      String description = value.description();
      if (description != null) {
        description = description.trim();
        if (description.isBlank()) description = null;
        if (description != null && description.length() > 1000) {
          throw new IllegalArgumentException("字段描述不能超过 1000 个字符");
        }
      }

      normalized.add(new FieldSpec(
          fieldId,
          physicalName,
          displayName,
          value.dataType() == null ? DatasetFieldDataType.UNKNOWN : value.dataType(),
          value.nullable(),
          description,
          value.defaultRole() == null ? DatasetFieldRole.DIMENSION : value.defaultRole()));
    }
    return List.copyOf(normalized);
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

  private String stableFieldId(long datasetId, String physicalKey) {
    return UUID.nameUUIDFromBytes(
        ("dataset:" + datasetId + ":" + physicalKey).getBytes(StandardCharsets.UTF_8)).toString();
  }

  private String schemaSnapshot(List<FieldSpec> fields) {
    try {
      return objectMapper.writeValueAsString(fields);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Dataset schemaSnapshot 序列化失败", exception);
    }
  }

  private String required(String value, String fieldName, int maxLength) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " 不能为空");
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(fieldName + " 不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }

  public record PublishCommand(
      long sourceTaskAssetId,
      String name,
      String description,
      List<FieldSpec> fields) {
  }

  public record FieldSpec(
      String fieldId,
      String physicalName,
      String displayName,
      DatasetFieldDataType dataType,
      boolean nullable,
      String description,
      DatasetFieldRole defaultRole) {
  }
}
