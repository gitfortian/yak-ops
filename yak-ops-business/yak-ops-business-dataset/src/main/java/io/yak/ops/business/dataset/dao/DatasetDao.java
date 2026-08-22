package io.yak.ops.business.dataset.dao;

import io.yak.ops.business.dataset.dao.model.DatasetFieldPO;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import io.yak.ops.business.dataset.dao.model.DatasetVersionPO;
import java.util.List;

/** Database access boundary. MyBatis/PO types stay below the Repository adapter. */
public interface DatasetDao {

  int insertDataset(DatasetPO dataset);

  int insertVersion(DatasetVersionPO version);

  int insertField(DatasetFieldPO field);

  int updateCurrentVersion(long datasetId, long versionId);

  int updateStatus(long datasetId, String status);

  int updateMetadata(long datasetId, String name, String description);

  DatasetPO selectDataset(long datasetId);

  DatasetPO selectDatasetBySourceTaskAssetId(long sourceTaskAssetId);

  DatasetPO selectDatasetByDevelopmentNodeId(long developmentNodeId);

  List<DatasetPO> selectDatasets();

  DatasetVersionPO selectVersion(long versionId);

  List<DatasetVersionPO> selectVersions(long datasetId);

  List<DatasetFieldPO> selectFields(long versionId);

  int selectNextVersionNo(long datasetId);
}
