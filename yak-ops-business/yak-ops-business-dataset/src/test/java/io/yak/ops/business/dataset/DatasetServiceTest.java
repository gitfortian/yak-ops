package io.yak.ops.business.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DatasetServiceTest {

  @Test
  void publishSnapshotsCurrentTaskRevisionWithoutOwningTaskExecution() {
    DatasetRepository repository = mock(DatasetRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DatasetService service = new DatasetService(repository, catalog);

    TaskAsset asset = taskAsset(11L, TaskAssetStatus.ONLINE, "SQL", 71L, 3);
    when(catalog.get(11L)).thenReturn(asset);
    when(repository.insertDataset("sales", "sales dataset")).thenReturn(21L);
    when(repository.nextVersionNo(21L)).thenReturn(1);
    when(repository.appendVersion(argThat(draft ->
        draft.datasetId() == 21L
            && draft.versionNo() == 1
            && draft.sourceType() == DatasetSourceType.QUERY_REVISION
            && draft.sourceTaskAssetId() == 11L
            && draft.sourceTaskRevisionId() == 71L
            && draft.sourceTaskRevisionNo() == 3
            && draft.fields().isEmpty()))).thenReturn(31L);
    when(repository.findDataset(21L)).thenReturn(Optional.of(new Dataset(
        21L, "sales", "sales dataset", DatasetStatus.ONLINE, 31L, Instant.EPOCH, Instant.EPOCH)));
    DatasetVersion version = new DatasetVersion(
        31L, 21L, 1, DatasetSourceType.QUERY_REVISION, 11L, 71L, 3, "[]", Instant.EPOCH);
    when(repository.findVersion(31L)).thenReturn(Optional.of(version));
    when(repository.listVersions(21L)).thenReturn(List.of(version));
    when(repository.listFields(31L)).thenReturn(List.of());

    DatasetDetail result = service.publish(new DatasetService.PublishCommand(
        11L, "sales", "sales dataset", List.of()));

    assertEquals(71L, result.currentVersion().sourceTaskRevisionId());
    assertEquals(3, result.currentVersion().sourceTaskRevisionNo());
    verify(repository).updateCurrentVersion(21L, 31L);
  }

  @Test
  void publishFromReleaseIsIdempotentForCurrentTaskRevision() {
    DatasetRepository repository = mock(DatasetRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DatasetService service = new DatasetService(repository, catalog);

    TaskAsset asset = taskAsset(11L, TaskAssetStatus.ONLINE, "SQL", 71L, 3);
    Dataset dataset = new Dataset(
        21L, "sales", "sales dataset", DatasetStatus.ONLINE, 31L, Instant.EPOCH, Instant.EPOCH);
    DatasetVersion version = new DatasetVersion(
        31L, 21L, 1, DatasetSourceType.QUERY_REVISION, 11L, 71L, 3, "[]", Instant.EPOCH);
    when(catalog.get(11L)).thenReturn(asset);
    when(repository.findDatasetBySourceTaskAssetId(11L)).thenReturn(Optional.of(dataset));
    when(repository.findDataset(21L)).thenReturn(Optional.of(dataset));
    when(repository.findVersion(31L)).thenReturn(Optional.of(version));
    when(repository.listVersions(21L)).thenReturn(List.of(version));
    when(repository.listFields(31L)).thenReturn(List.of());

    DatasetDetail result = service.publishFromRelease(new DatasetService.PublishCommand(
        11L, "ignored", "ignored", List.of()));

    assertEquals(1, result.currentVersion().versionNo());
    verify(repository, never()).insertDataset("ignored", "ignored");
    verify(repository, never()).updateCurrentVersion(eq(21L), eq(31L));
  }

  @Test
  void publishFromReleaseAppendsDatasetVersionForNewTaskRevision() {
    DatasetRepository repository = mock(DatasetRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DatasetService service = new DatasetService(repository, catalog);

    TaskAsset asset = taskAsset(11L, TaskAssetStatus.ONLINE, "SQL", 72L, 4);
    Dataset before = new Dataset(
        21L, "sales", "sales dataset", DatasetStatus.ONLINE, 31L, Instant.EPOCH, Instant.EPOCH);
    Dataset after = new Dataset(
        21L, "sales", "sales dataset", DatasetStatus.ONLINE, 32L, Instant.EPOCH, Instant.EPOCH);
    DatasetVersion version1 = new DatasetVersion(
        31L, 21L, 1, DatasetSourceType.QUERY_REVISION, 11L, 71L, 3, "[]", Instant.EPOCH);
    DatasetVersion version2 = new DatasetVersion(
        32L, 21L, 2, DatasetSourceType.QUERY_REVISION, 11L, 72L, 4, "[]", Instant.EPOCH);

    when(catalog.get(11L)).thenReturn(asset);
    when(repository.findDatasetBySourceTaskAssetId(11L)).thenReturn(Optional.of(before));
    when(repository.findDataset(21L)).thenReturn(Optional.of(before), Optional.of(before), Optional.of(after));
    when(repository.findVersion(31L)).thenReturn(Optional.of(version1));
    when(repository.findVersion(32L)).thenReturn(Optional.of(version2));
    when(repository.listVersions(21L)).thenReturn(List.of(version1), List.of(version2, version1));
    when(repository.listFields(31L)).thenReturn(List.of());
    when(repository.listFields(32L)).thenReturn(List.of());
    when(repository.nextVersionNo(21L)).thenReturn(2);
    when(repository.appendVersion(argThat(draft ->
        draft.datasetId() == 21L
            && draft.versionNo() == 2
            && draft.sourceTaskRevisionId() == 72L
            && draft.sourceTaskRevisionNo() == 4))).thenReturn(32L);

    DatasetDetail result = service.publishFromRelease(new DatasetService.PublishCommand(
        11L, null, null, List.of()));

    assertEquals(2, result.currentVersion().versionNo());
    assertEquals(4, result.currentVersion().sourceTaskRevisionNo());
    verify(repository).updateCurrentVersion(21L, 32L);
  }

  @Test
  void publishFromReleasePreservesFieldIdAcrossCustomizedVersion() {
    DatasetRepository repository = mock(DatasetRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DatasetService service = new DatasetService(repository, catalog);

    TaskAsset asset = taskAsset(11L, TaskAssetStatus.ONLINE, "SQL", 72L, 4);
    Dataset before = new Dataset(
        21L, "sales", "sales dataset", DatasetStatus.ONLINE, 31L, Instant.EPOCH, Instant.EPOCH);
    Dataset after = new Dataset(
        21L, "sales", "sales dataset", DatasetStatus.ONLINE, 32L, Instant.EPOCH, Instant.EPOCH);
    DatasetVersion version1 = new DatasetVersion(
        31L, 21L, 1, DatasetSourceType.QUERY_REVISION, 11L, 71L, 3, "[]", Instant.EPOCH);
    DatasetVersion version2 = new DatasetVersion(
        32L, 21L, 2, DatasetSourceType.QUERY_REVISION, 11L, 72L, 4, "[]", Instant.EPOCH);
    DatasetField oldField = new DatasetField(
        "field-sales-amount", 31L, "sales_amount", "销售额", DatasetFieldDataType.NUMBER,
        true, null, DatasetFieldRole.MEASURE, 1);
    DatasetField newField = new DatasetField(
        "field-sales-amount", 32L, "sales_amount", "销售金额", DatasetFieldDataType.NUMBER,
        true, null, DatasetFieldRole.MEASURE, 1);

    when(catalog.get(11L)).thenReturn(asset);
    when(repository.findDatasetBySourceTaskAssetId(11L)).thenReturn(Optional.of(before));
    when(repository.findDataset(21L)).thenReturn(
        Optional.of(before), Optional.of(before), Optional.of(before), Optional.of(after));
    when(repository.findVersion(31L)).thenReturn(Optional.of(version1));
    when(repository.findVersion(32L)).thenReturn(Optional.of(version2));
    when(repository.listVersions(21L)).thenReturn(List.of(version1), List.of(version2, version1));
    when(repository.listFields(31L)).thenReturn(List.of(oldField));
    when(repository.listFields(32L)).thenReturn(List.of(newField));
    when(repository.nextVersionNo(21L)).thenReturn(2);
    when(repository.appendVersion(argThat(draft ->
        draft.fields().size() == 1
            && "field-sales-amount".equals(draft.fields().get(0).fieldId())))).thenReturn(32L);

    DatasetDetail result = service.publishFromRelease(new DatasetService.PublishCommand(
        11L,
        null,
        null,
        List.of(new DatasetService.FieldSpec(
            null,
            "sales_amount",
            "销售金额",
            DatasetFieldDataType.NUMBER,
            true,
            null,
            DatasetFieldRole.MEASURE))));

    assertEquals("field-sales-amount", result.fields().get(0).fieldId());
    verify(repository).appendVersion(argThat(draft ->
        draft.fields().size() == 1
            && "field-sales-amount".equals(draft.fields().get(0).fieldId())));
  }

  @Test
  void publishRejectsOfflineAsset() {
    DatasetRepository repository = mock(DatasetRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DatasetService service = new DatasetService(repository, catalog);
    when(catalog.get(11L)).thenReturn(taskAsset(11L, TaskAssetStatus.OFFLINE, "SQL", 71L, 3));

    IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> service.publish(new DatasetService.PublishCommand(11L, "sales", null, List.of())));

    assertEquals("只有 ONLINE 的 TaskAsset 可以发布/更新 Dataset：11", error.getMessage());
  }

  @Test
  void publishRejectsNonSqlTask() {
    DatasetRepository repository = mock(DatasetRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DatasetService service = new DatasetService(repository, catalog);
    when(catalog.get(11L)).thenReturn(taskAsset(11L, TaskAssetStatus.ONLINE, "SHELL", 71L, 3));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.publish(new DatasetService.PublishCommand(11L, "sales", null, List.of())));
  }

  private static TaskAsset taskAsset(
      long assetId,
      TaskAssetStatus status,
      String taskType,
      long revisionId,
      int revisionNo) {
    return new TaskAsset(
        assetId,
        TaskAssetSource.DATA_DEVELOPMENT,
        "101",
        null,
        "sales.sql",
        taskType,
        status,
        new TaskRevisionRef(assetId, revisionId, revisionNo),
        Instant.EPOCH,
        Instant.EPOCH);
  }
}
