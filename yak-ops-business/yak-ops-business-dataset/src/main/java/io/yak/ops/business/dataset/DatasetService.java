package io.yak.ops.business.dataset;

import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.business.dataset.service.event.DatasetLineageRefreshRequested;
import io.yak.ops.business.dataset.service.support.DatasetFieldNormalizer;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dataset publication/lifecycle service. Dataset is a data contract, not an executable task. */
@Service
public class DatasetService {

  private final DatasetRepository repository;
  private final TaskCatalogService taskCatalogService;
  private final DatasetSchemaDiscoveryService schemaDiscoveryService;
  private final DatasetFieldNormalizer fieldNormalizer;
  private final ApplicationEventPublisher eventPublisher;

  @Autowired
  public DatasetService(
      DatasetRepository repository,
      TaskCatalogService taskCatalogService,
      DatasetSchemaDiscoveryService schemaDiscoveryService,
      DatasetFieldNormalizer fieldNormalizer,
      ApplicationEventPublisher eventPublisher) {
    this.repository = repository;
    this.taskCatalogService = taskCatalogService;
    this.schemaDiscoveryService = schemaDiscoveryService;
    this.fieldNormalizer = fieldNormalizer;
    this.eventPublisher = eventPublisher;
  }

  /** Focused unit-test constructor without live schema discovery or Spring events. */
  DatasetService(DatasetRepository repository, TaskCatalogService taskCatalogService) {
    this(repository, taskCatalogService, null, new DatasetFieldNormalizer(repository), null);
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail publish(PublishCommand command) {
    Objects.requireNonNull(command, "command");
    TaskAsset asset = requirePublishableAsset(command.sourceTaskAssetId());
    return createDataset(asset, command);
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail publishFromRelease(PublishCommand command) {
    Objects.requireNonNull(command, "command");
    TaskAsset asset = requirePublishableAsset(command.sourceTaskAssetId());
    Optional<Dataset> existing = repository.findDatasetBySourceTaskAssetId(asset.id());
    if (existing.isEmpty()) return createDataset(asset, command);

    DatasetDetail current = get(existing.get().id());
    DatasetVersion currentVersion = current.currentVersion();
    if (currentVersion == null) {
      List<FieldSpec> fields = resolveFields(existing.get().id(), asset, command.fields());
      long versionId = appendVersion(existing.get().id(), asset, fields, true);
      repository.updateCurrentVersion(existing.get().id(), versionId);
      requestLineageRefresh(existing.get().id());
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
    requestLineageRefresh(existing.get().id());
    return get(existing.get().id());
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail saveForDevelopmentNode(long developmentNodeId, PublishCommand command) {
    if (developmentNodeId <= 0L) throw new IllegalArgumentException("developmentNodeId 必须大于 0");
    Objects.requireNonNull(command, "command");
    TaskAsset asset = requirePublishableAsset(command.sourceTaskAssetId());
    String name = normalizeName(command.name(), asset.name());
    String description = normalizeDescription(command.description());

    Optional<Dataset> existing = repository.findDatasetByDevelopmentNodeId(developmentNodeId);
    if (existing.isEmpty()) {
      long datasetId = repository.insertDevelopmentNodeDataset(developmentNodeId, name, description);
      List<FieldSpec> fields = resolveFields(datasetId, asset, command.fields());
      long versionId = appendVersion(datasetId, asset, fields, false);
      repository.updateCurrentVersion(datasetId, versionId);
      requestLineageRefresh(datasetId);
      return get(datasetId);
    }

    long datasetId = existing.get().id();
    repository.updateMetadata(datasetId, name, description);
    DatasetDetail current = get(datasetId);
    List<FieldSpec> fields = resolveFields(datasetId, asset, command.fields());
    DatasetVersion currentVersion = current.currentVersion();
    if (currentVersion != null
        && currentVersion.sourceTaskAssetId() == asset.id()
        && currentVersion.sourceTaskRevisionId() == asset.currentRevision().taskRevisionId()
        && fieldNormalizer.sameFields(current.fields(), fields)) {
      requestLineageRefresh(datasetId);
      return get(datasetId);
    }

    long versionId = appendVersion(datasetId, asset, fields, true);
    repository.updateCurrentVersion(datasetId, versionId);
    requestLineageRefresh(datasetId);
    return get(datasetId);
  }

  public List<FieldSpec> previewReleaseFields(long sourceTaskAssetId) {
    TaskAsset asset = requirePublishableAsset(sourceTaskAssetId);
    if (schemaDiscoveryService == null) throw new IllegalStateException("Dataset schema discovery 未启用");
    return schemaDiscoveryService.preview(asset);
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail createVersion(long datasetId, List<FieldSpec> fields) {
    DatasetDetail current = get(datasetId);
    DatasetVersion currentVersion = current.currentVersion();
    if (currentVersion == null) throw new IllegalStateException("Dataset 尚未建立当前版本：" + datasetId);

    TaskAsset asset = requirePublishableAsset(currentVersion.sourceTaskAssetId());
    if (asset.currentRevision().taskRevisionId() == currentVersion.sourceTaskRevisionId()) {
      throw new IllegalArgumentException(
          "当前 TaskRevision 已经是 Dataset 的当前版本：V" + currentVersion.versionNo());
    }

    List<FieldSpec> normalizedFields = resolveFields(datasetId, asset, fields);
    long versionId = appendVersion(datasetId, asset, normalizedFields, true);
    repository.updateCurrentVersion(datasetId, versionId);
    requestLineageRefresh(datasetId);
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
    if (sourceTaskAssetId <= 0L) throw new IllegalArgumentException("sourceTaskAssetId 必须大于 0");
    return repository.findDatasetBySourceTaskAssetId(sourceTaskAssetId).map(dataset -> get(dataset.id()));
  }

  public Optional<DatasetDetail> findByDevelopmentNodeId(long developmentNodeId) {
    if (developmentNodeId <= 0L) throw new IllegalArgumentException("developmentNodeId 必须大于 0");
    return repository.findDatasetByDevelopmentNodeId(developmentNodeId).map(dataset -> get(dataset.id()));
  }

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
      requestLineageRefresh(datasetId);
    }
    return get(datasetId);
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail offline(long datasetId) {
    Dataset dataset = get(datasetId).dataset();
    if (dataset.status() != DatasetStatus.OFFLINE) {
      repository.updateStatus(datasetId, DatasetStatus.OFFLINE);
      requestLineageRefresh(datasetId);
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
    requestLineageRefresh(datasetId);
    return get(datasetId);
  }

  private List<FieldSpec> resolveFields(long datasetId, TaskAsset asset, List<FieldSpec> requestedFields) {
    if (requestedFields != null && !requestedFields.isEmpty()) {
      return fieldNormalizer.normalize(datasetId, requestedFields);
    }
    if (schemaDiscoveryService == null) return List.of();
    return fieldNormalizer.normalize(datasetId, schemaDiscoveryService.discover(datasetId, asset));
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
    return repository.appendVersion(DatasetVersionDraft.queryRevision(
        datasetId,
        versionNo,
        asset.id(),
        asset.currentRevision().taskRevisionId(),
        asset.currentRevision().revisionNo(),
        fieldNormalizer.definitions(fields)));
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

  private void requestLineageRefresh(long datasetId) {
    if (eventPublisher != null) eventPublisher.publishEvent(new DatasetLineageRefreshRequested(datasetId));
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
