package io.yak.ops.business.dataset;

import java.util.List;
import java.util.Optional;

interface DatasetRepository {

  long insertDataset(String name, String description);

  /** Creates a Dataset identity owned by a data-development Dataset node. */
  default long insertDevelopmentNodeDataset(
      long developmentNodeId,
      String name,
      String description) {
    throw new UnsupportedOperationException("Development Dataset node binding is not supported");
  }

  long insertVersion(
      long datasetId,
      int versionNo,
      DatasetSourceType sourceType,
      long sourceTaskAssetId,
      long sourceTaskRevisionId,
      int sourceTaskRevisionNo,
      String schemaSnapshot);

  void insertFields(long versionId, List<DatasetService.FieldSpec> fields);

  void updateCurrentVersion(long datasetId, long versionId);

  void updateStatus(long datasetId, DatasetStatus status);

  /** Metadata is mutable while DatasetVersion schema/source snapshots remain immutable. */
  default void updateMetadata(long datasetId, String name, String description) {
    // Focused repository test doubles do not need to implement node-owned Dataset metadata.
  }

  Optional<Dataset> findDataset(long datasetId);

  /** Legacy SQL release publication lookup. Node-owned Datasets are deliberately excluded. */
  Optional<Dataset> findDatasetBySourceTaskAssetId(long sourceTaskAssetId);

  default Optional<Dataset> findDatasetByDevelopmentNodeId(long developmentNodeId) {
    return Optional.empty();
  }

  List<Dataset> listDatasets();

  Optional<DatasetVersion> findVersion(long versionId);

  List<DatasetVersion> listVersions(long datasetId);

  List<DatasetField> listFields(long versionId);

  int nextVersionNo(long datasetId);
}
