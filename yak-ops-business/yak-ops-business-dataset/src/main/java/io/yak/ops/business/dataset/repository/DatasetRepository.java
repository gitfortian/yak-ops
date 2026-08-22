package io.yak.ops.business.dataset.repository;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.DatasetVersionDraft;
import java.util.List;
import java.util.Optional;

/** Domain repository for the Dataset aggregate. */
public interface DatasetRepository {

  long insertDataset(String name, String description);

  long insertDevelopmentNodeDataset(long developmentNodeId, String name, String description);

  /** Appends one immutable version and its field contract atomically inside the caller transaction. */
  long appendVersion(DatasetVersionDraft draft);

  void updateCurrentVersion(long datasetId, long versionId);

  void updateStatus(long datasetId, DatasetStatus status);

  void updateMetadata(long datasetId, String name, String description);

  Optional<Dataset> findDataset(long datasetId);

  Optional<Dataset> findDatasetBySourceTaskAssetId(long sourceTaskAssetId);

  Optional<Dataset> findDatasetByDevelopmentNodeId(long developmentNodeId);

  List<Dataset> listDatasets();

  Optional<DatasetVersion> findVersion(long versionId);

  List<DatasetVersion> listVersions(long datasetId);

  List<DatasetField> listFields(long versionId);

  int nextVersionNo(long datasetId);
}
