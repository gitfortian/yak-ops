package io.yak.ops.business.taskcatalog.service;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.repository.TaskAssetRepository;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Application service for the published task asset catalog. */
@Service
@ConditionalOnDataSourceEnabled
public class TaskCatalogService {

  private final TaskAssetRepository repository;

  public TaskCatalogService(TaskAssetRepository repository) {
    this.repository = repository;
  }

  public TaskAsset publish(
      TaskAssetSource source,
      String sourceRef,
      Long projectId,
      String name,
      String taskType,
      long revisionId,
      int revisionNo) {
    if (source == null) throw new IllegalArgumentException("任务资产来源不能为空");
    if (revisionId <= 0L) throw new IllegalArgumentException("任务版本 ID 必须大于 0");
    if (revisionNo <= 0) throw new IllegalArgumentException("任务版本号必须大于 0");
    return repository.upsertPublished(
        source,
        normalizeSourceRef(sourceRef),
        normalizeProjectId(projectId),
        normalizeName(name),
        normalizeTaskType(taskType),
        revisionId,
        revisionNo);
  }

  public List<TaskAsset> list(String source, String status, String keyword) {
    return repository.list(
        parseSource(source),
        parseStatus(status),
        normalizeKeyword(keyword));
  }

  public Optional<TaskAsset> findBySource(TaskAssetSource source, String sourceRef) {
    if (source == null) return Optional.empty();
    return repository.findBySource(source, normalizeSourceRef(sourceRef));
  }

  /** Keeps an already-published asset aligned with its source node without creating draft assets. */
  public void updateSourceMetadata(
      TaskAssetSource source,
      String sourceRef,
      Long projectId,
      String name,
      String taskType) {
    if (source == null) throw new IllegalArgumentException("任务资产来源不能为空");
    repository.updateSourceMetadata(
        source,
        normalizeSourceRef(sourceRef),
        normalizeProjectId(projectId),
        normalizeName(name),
        normalizeTaskType(taskType));
  }

  /** Offline assets disappear from new orchestration discovery while retaining their revision pointer. */
  public void offlineSource(TaskAssetSource source, String sourceRef) {
    if (source == null) throw new IllegalArgumentException("任务资产来源不能为空");
    repository.updateStatus(
        source,
        normalizeSourceRef(sourceRef),
        TaskAssetStatus.OFFLINE);
  }

  private TaskAssetSource parseSource(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return TaskAssetSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知任务资产来源：" + value, exception);
    }
  }

  private TaskAssetStatus parseStatus(String value) {
    String normalized = value == null || value.isBlank() ? "ONLINE" : value.trim();
    try {
      return TaskAssetStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知任务资产状态：" + value, exception);
    }
  }

  private String normalizeSourceRef(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("sourceRef 不能为空");
    String normalized = value.trim();
    if (normalized.length() > 128) throw new IllegalArgumentException("sourceRef 不能超过 128 个字符");
    return normalized;
  }

  private String normalizeName(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("任务资产名称不能为空");
    String normalized = value.trim();
    if (normalized.length() > 200) throw new IllegalArgumentException("任务资产名称不能超过 200 个字符");
    return normalized;
  }

  private String normalizeTaskType(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("taskType 不能为空");
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (normalized.length() > 64) throw new IllegalArgumentException("taskType 不能超过 64 个字符");
    return normalized;
  }

  private Long normalizeProjectId(Long value) {
    return value == null || value <= 0L ? null : value;
  }

  private String normalizeKeyword(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (normalized.length() > 200) throw new IllegalArgumentException("搜索关键字不能超过 200 个字符");
    return normalized;
  }
}
