package io.yak.ops.business.dataset;

import java.util.List;
import java.util.Optional;

interface DatasetRepository {

  long insertDataset(String name, String description);

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

  Optional<Dataset> findDataset(long datasetId);

  List<Dataset> listDatasets();

  Optional<DatasetVersion> findVersion(long versionId);

  List<DatasetVersion> listVersions(long datasetId);

  List<DatasetField> listFields(long versionId);

  int nextVersionNo(long datasetId);
}
