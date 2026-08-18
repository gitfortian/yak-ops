package io.yak.ops.business.dataset;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Application entry point used by Dashboard/Chart consumers. */
@Service
public class DatasetQueryService {

  private final DatasetRepository repository;
  private final Map<DatasetSourceType, DatasetSourceQueryAdapter> adapters;
  private final DatasetQueryPerformanceService performanceService;

  DatasetQueryService(
      DatasetRepository repository,
      List<DatasetSourceQueryAdapter> adapters,
      DatasetQueryPerformanceService performanceService) {
    this.repository = repository;
    this.performanceService = performanceService;
    Map<DatasetSourceType, DatasetSourceQueryAdapter> discovered = new LinkedHashMap<>();
    for (DatasetSourceQueryAdapter adapter : adapters) {
      DatasetSourceQueryAdapter existing = discovered.putIfAbsent(adapter.sourceType(), adapter);
      if (existing != null) {
        throw new IllegalStateException("重复的 Dataset query adapter：" + adapter.sourceType());
      }
    }
    this.adapters = Map.copyOf(discovered);
  }

  public DatasetQueryResult query(long datasetId, DatasetQueryRequest request) {
    Instant startedAt = Instant.now();
    long queryStartedAt = System.nanoTime();
    if (datasetId <= 0L) throw new IllegalArgumentException("datasetId 必须大于 0");
    Dataset dataset = repository.findDataset(datasetId)
        .orElseThrow(() -> new IllegalArgumentException("Dataset 不存在：" + datasetId));
    if (dataset.status() != DatasetStatus.ONLINE) {
      throw new IllegalArgumentException("只有 ONLINE Dataset 可以查询：" + datasetId);
    }

    DatasetVersion version = resolveVersion(dataset, request == null ? null : request.versionNo());
    List<DatasetField> fields = repository.listFields(version.id());
    DatasetSourceQueryAdapter adapter = adapters.get(version.sourceType());
    if (adapter == null) {
      throw new IllegalStateException("Dataset sourceType 尚未接入 Query Runtime：" + version.sourceType());
    }
    long servicePrepareMillis = elapsedMillis(queryStartedAt);

    DatasetQueryExecution execution = adapter.execute(dataset, version, fields, request);
    long totalMillis = elapsedMillis(queryStartedAt);
    DatasetQueryResult result = execution.result();
    performanceService.record(new DatasetQueryPerformance(
        UUID.randomUUID().toString().replace("-", ""),
        dataset.id(),
        dataset.name(),
        version.id(),
        version.versionNo(),
        version.sourceType().name(),
        execution.dataSourceId(),
        execution.sql(),
        execution.waitMillis(),
        servicePrepareMillis + execution.prepareMillis(),
        execution.executeMillis(),
        execution.transferMillis(),
        totalMillis,
        result.returnedRows(),
        result.truncated(),
        startedAt));
    return result;
  }

  public List<DatasetQueryPerformance> recentPerformance(Set<Long> datasetIds, int limit) {
    return performanceService.recent(datasetIds, limit);
  }

  private DatasetVersion resolveVersion(Dataset dataset, Integer versionNo) {
    if (versionNo == null) {
      if (dataset.currentVersionId() == null) {
        throw new IllegalStateException("Dataset 尚未建立当前版本：" + dataset.id());
      }
      return repository.findVersion(dataset.currentVersionId())
          .orElseThrow(() -> new IllegalStateException("Dataset 当前版本不存在：" + dataset.currentVersionId()));
    }
    if (versionNo <= 0) throw new IllegalArgumentException("versionNo 必须大于 0");
    return repository.listVersions(dataset.id()).stream()
        .filter(version -> version.versionNo() == versionNo)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "DatasetVersion 不存在：datasetId=" + dataset.id() + ", versionNo=" + versionNo));
  }

  private static long elapsedMillis(long startedAt) {
    return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
  }
}
