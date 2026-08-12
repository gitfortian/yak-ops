package io.yak.ops.business.taskcatalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.repository.TaskAssetRepository;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskCatalogServiceTest {

  @Test
  void publishCreatesOrAdvancesOneOnlineAsset() {
    TaskAssetRepository repository = mock(TaskAssetRepository.class);
    TaskCatalogService service = new TaskCatalogService(repository);
    TaskAsset expected = asset(9L, 101L, 2);
    when(repository.upsertPublished(
            TaskAssetSource.DATA_DEVELOPMENT,
            "12",
            7L,
            "今天统计",
            "SQL",
            101L,
            2))
        .thenReturn(expected);

    TaskAsset result = service.publish(
        TaskAssetSource.DATA_DEVELOPMENT,
        " 12 ",
        7L,
        " 今天统计 ",
        "sql",
        101L,
        2);

    assertEquals(expected, result);
    assertEquals(2, result.currentRevision().revisionNo());
  }

  @Test
  void listDefaultsToOnlineAndNormalizesFilters() {
    TaskAssetRepository repository = mock(TaskAssetRepository.class);
    TaskCatalogService service = new TaskCatalogService(repository);
    when(repository.list(
            TaskAssetSource.DATA_DEVELOPMENT,
            TaskAssetStatus.ONLINE,
            "统计"))
        .thenReturn(List.of(asset(9L, 101L, 2)));

    List<TaskAsset> result = service.list("data_development", null, " 统计 ");

    assertEquals(1, result.size());
    verify(repository).list(
        TaskAssetSource.DATA_DEVELOPMENT,
        TaskAssetStatus.ONLINE,
        "统计");
  }

  private TaskAsset asset(long id, long revisionId, int revisionNo) {
    Instant now = Instant.parse("2026-08-12T03:30:00Z");
    return new TaskAsset(
        id,
        TaskAssetSource.DATA_DEVELOPMENT,
        "12",
        7L,
        "今天统计",
        "SQL",
        TaskAssetStatus.ONLINE,
        new TaskRevisionRef(id, revisionId, revisionNo),
        now,
        now);
  }
}
