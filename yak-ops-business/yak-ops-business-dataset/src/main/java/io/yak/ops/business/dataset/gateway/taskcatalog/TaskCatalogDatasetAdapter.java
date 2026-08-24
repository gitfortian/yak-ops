package io.yak.ops.business.dataset.gateway.taskcatalog;

import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import org.springframework.stereotype.Component;

/** Converts Task Catalog domain objects into Dataset-owned immutable source snapshots. */
@Component
public class TaskCatalogDatasetAdapter implements DatasetTaskCatalogGateway {

  private final TaskCatalogService taskCatalogService;

  public TaskCatalogDatasetAdapter(TaskCatalogService taskCatalogService) {
    this.taskCatalogService = taskCatalogService;
  }

  @Override
  public DatasetTaskAssetSnapshot get(long assetId) {
    TaskAsset asset = taskCatalogService.get(assetId);
    long revisionId =
        asset.currentRevision() == null ? 0L : asset.currentRevision().taskRevisionId();
    int revisionNo = asset.currentRevision() == null ? 0 : asset.currentRevision().revisionNo();
    SourceOrigin sourceOrigin =
        asset.source() == TaskAssetSource.DATA_DEVELOPMENT
            ? SourceOrigin.DATA_DEVELOPMENT
            : SourceOrigin.OTHER;
    SourceAvailability availability =
        asset.status() == TaskAssetStatus.ONLINE
            ? SourceAvailability.ONLINE
            : SourceAvailability.NOT_ONLINE;
    return new DatasetTaskAssetSnapshot(
        asset.id(),
        asset.name(),
        asset.sourceRef(),
        sourceOrigin,
        availability,
        asset.taskType(),
        revisionId,
        revisionNo);
  }

  @Override
  public DatasetTaskRevisionSnapshot resolveRevision(long assetId, long revisionId) {
    TaskAssetRevision resolved = taskCatalogService.resolveRevision(assetId, revisionId);
    return new DatasetTaskRevisionSnapshot(
        assetId,
        resolved.revision().revisionId(),
        resolved.revision().revisionNo(),
        resolved.revision().definition().taskType(),
        resolved.revision().definition().content(),
        resolved.revision().definition().configJson());
  }
}
