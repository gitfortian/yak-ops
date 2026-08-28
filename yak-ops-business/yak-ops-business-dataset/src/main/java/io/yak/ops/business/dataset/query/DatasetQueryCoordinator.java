package io.yak.ops.business.dataset.query;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryRequest;
import io.yak.ops.business.dataset.DatasetQueryResult;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.observability.DatasetQueryPerformanceRecorder;
import io.yak.ops.business.dataset.query.DatasetSourceQueryAdapter.ExecutionResult;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolves the exact DatasetVersion and delegates execution to its source-type adapter. */
@Component
public class DatasetQueryCoordinator {

  private final DatasetRepository repository;
  private final DatasetSourceQueryRegistry sourceRegistry;
  private final DatasetQueryPerformanceRecorder performanceRecorder;

  public DatasetQueryCoordinator(
      DatasetRepository repository,
      DatasetSourceQueryRegistry sourceRegistry,
      DatasetQueryPerformanceRecorder performanceRecorder) {
    this.repository = repository;
    this.sourceRegistry = sourceRegistry;
    this.performanceRecorder = performanceRecorder;
  }

  public DatasetQueryResult query(long datasetId, DatasetQueryRequest request) {
    Instant startedAt = Instant.now();
    long queryStartedAt = System.nanoTime();
    if (datasetId <= 0L) {
      throw new IllegalArgumentException("datasetId 必须大于 0");
    }
    Dataset dataset =
        repository
            .findDataset(datasetId)
            .orElseThrow(() -> new IllegalArgumentException("Dataset 不存在：" + datasetId));
    if (dataset.status() != DatasetStatus.ONLINE) {
      throw new IllegalArgumentException("只有 ONLINE Dataset 可以查询：" + datasetId);
    }

    DatasetVersion version = resolveVersion(dataset, request == null ? null : request.versionNo());
    List<DatasetField> fields = repository.listFields(version.id());
    DatasetSourceQueryAdapter adapter = sourceRegistry.require(version.sourceType());
    long servicePrepareMillis = elapsedMillis(queryStartedAt);

    ExecutionResult execution = adapter.execute(dataset, version, fields, request);
    long totalMillis = elapsedMillis(queryStartedAt);
    String queryId = UUID.randomUUID().toString().replace("-", "");
    DatasetQueryResult result = execution.result().withQueryId(queryId);
    performanceRecorder.record(
        new DatasetQueryPerformance(
            queryId,
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

  private DatasetVersion resolveVersion(Dataset dataset, Integer versionNo) {
    if (versionNo == null) {
      if (dataset.currentVersionId() == null) {
        throw new IllegalStateException("Dataset 尚未建立当前版本：" + dataset.id());
      }
      return repository
          .findVersion(dataset.currentVersionId())
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Dataset 当前版本不存在：" + dataset.currentVersionId()));
    }
    if (versionNo <= 0) {
      throw new IllegalArgumentException("versionNo 必须大于 0");
    }
    return repository
        .findVersion(dataset.id(), versionNo)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "DatasetVersion 不存在：datasetId="
                        + dataset.id()
                        + ", versionNo="
                        + versionNo));
  }

  private static long elapsedMillis(long startedAt) {
    return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
  }
}
