package io.yak.ops.business.dataset.gateway.taskcatalog;

import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;

/** Dataset-owned view of the Task Catalog facts required by publication and query runtime. */
public interface DatasetTaskCatalogGateway {

  DatasetTaskAssetSnapshot get(long assetId);

  DatasetTaskRevisionSnapshot resolveRevision(long assetId, long revisionId);

  record DatasetTaskAssetSnapshot(
      long id,
      String name,
      String sourceRef,
      TaskAssetSource source,
      TaskAssetStatus status,
      String taskType,
      long currentRevisionId,
      int currentRevisionNo) {}

  record DatasetTaskRevisionSnapshot(
      long assetId,
      long revisionId,
      int revisionNo,
      String taskType,
      String content,
      String configJson) {}
}
