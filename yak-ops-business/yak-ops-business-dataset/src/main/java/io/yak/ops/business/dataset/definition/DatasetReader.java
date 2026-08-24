package io.yak.ops.business.dataset.definition;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side access to Dataset identity, immutable versions and current schema. */
@Component
public class DatasetReader {

  private final DatasetRepository repository;

  public DatasetReader(DatasetRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public List<Dataset> list() {
    return repository.listDatasets();
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public DatasetDetail require(long datasetId) {
    if (datasetId <= 0L) {
      throw new IllegalArgumentException("datasetId 必须大于 0");
    }
    Dataset dataset =
        repository
            .findDataset(datasetId)
            .orElseThrow(() -> new IllegalArgumentException("Dataset 不存在：" + datasetId));

    DatasetVersion currentVersion = null;
    List<DatasetField> fields = List.of();
    if (dataset.currentVersionId() != null) {
      currentVersion =
          repository
              .findVersion(dataset.currentVersionId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Dataset 当前版本不存在：datasetId="
                              + datasetId
                              + ", versionId="
                              + dataset.currentVersionId()));
      fields = repository.listFields(currentVersion.id());
    }
    return new DatasetDetail(dataset, currentVersion, repository.listVersions(datasetId), fields);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Optional<DatasetDetail> findBySourceTaskAssetId(long sourceTaskAssetId) {
    if (sourceTaskAssetId <= 0L) {
      throw new IllegalArgumentException("sourceTaskAssetId 必须大于 0");
    }
    return repository.findDatasetBySourceTaskAssetId(sourceTaskAssetId).map(value -> require(value.id()));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Optional<DatasetDetail> findByDevelopmentNodeId(long developmentNodeId) {
    if (developmentNodeId <= 0L) {
      throw new IllegalArgumentException("developmentNodeId 必须大于 0");
    }
    return repository.findDatasetByDevelopmentNodeId(developmentNodeId).map(value -> require(value.id()));
  }
}
