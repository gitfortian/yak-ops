package io.yak.ops.business.dataset;

import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.business.dataset.service.event.DatasetLineageRefreshRequested;
import io.yak.ops.business.dataset.service.support.DatasetFieldNormalizer;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lifecycle for data-development Dataset nodes that own datasource + SQL directly. */
@Service
class DevelopmentStandaloneDatasetService {

  private final DatasetRepository repository;
  private final DatasetSchemaDiscoveryService discoveryService;
  private final DatasetFieldNormalizer fieldNormalizer;
  private final DatasetService datasetService;
  private final ApplicationEventPublisher eventPublisher;

  DevelopmentStandaloneDatasetService(
      DatasetRepository repository,
      DatasetSchemaDiscoveryService discoveryService,
      DatasetFieldNormalizer fieldNormalizer,
      DatasetService datasetService,
      ApplicationEventPublisher eventPublisher) {
    this.repository = repository;
    this.discoveryService = discoveryService;
    this.fieldNormalizer = fieldNormalizer;
    this.datasetService = datasetService;
    this.eventPublisher = eventPublisher;
  }

  List<DatasetService.FieldSpec> preview(String dataSourceId, String sql) {
    return discoveryService.preview(requireDataSourceId(dataSourceId), requireSql(sql));
  }

  DatasetSchemaDiscoveryService.QueryPreview previewQuery(String dataSourceId, String sql) {
    return discoveryService.previewQuery(requireDataSourceId(dataSourceId), requireSql(sql));
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
        ? fieldNormalizer.normalize(datasetId, discoveryService.discover(datasetId, sourceId, sourceSql))
        : fieldNormalizer.normalize(datasetId, requestedFields);
    DatasetDetail current = datasetService.get(datasetId);
    DatasetVersion version = current.currentVersion();
    if (version != null
        && version.sourceType() == DatasetSourceType.SQL_QUERY
        && Objects.equals(version.dataSourceId(), sourceId)
        && Objects.equals(version.sql(), sourceSql)
        && fieldNormalizer.sameFields(current.fields(), fields)) {
      requestLineageRefresh(datasetId);
      return current;
    }

    int versionNo = repository.nextVersionNo(datasetId);
    long versionId = repository.appendVersion(DatasetVersionDraft.sqlQuery(
        datasetId,
        versionNo,
        sourceId,
        sourceSql,
        fieldNormalizer.definitions(fields)));
    repository.updateCurrentVersion(datasetId, versionId);
    requestLineageRefresh(datasetId);
    return datasetService.get(datasetId);
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

  private String required(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(field + " 不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }

  private void requestLineageRefresh(long datasetId) {
    eventPublisher.publishEvent(new DatasetLineageRefreshRequested(datasetId));
  }
}
